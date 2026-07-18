package com.github.epsilon.managers.impl;

import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.elements.impl.notification.Notification;
import com.github.epsilon.elements.impl.notification.NotificationMode;
import com.github.epsilon.modules.impl.ClientSetting;
import com.github.epsilon.utils.player.ChatUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

public class NotificationManager {

    private static final int MAX_NOTIFICATIONS = 5;

    private final Queue<Notification> notifications = new ArrayDeque<>();
    private final Map<Integer, Notification> hashCodeMap = new HashMap<>();

    public void success(String title, String subTitle) {
        notify(title, subTitle, NotificationMode.Success, ChatFormatting.GREEN);
    }

    public void success(String title, String subTitle, int hash) {
        notify(title, subTitle, NotificationMode.Success, ChatFormatting.GREEN, hash);
    }

    public void info(String title, String subTitle) {
        notify(title, subTitle, NotificationMode.Info, ChatFormatting.WHITE);
    }

    public void info(String title, String subTitle, int hash) {
        notify(title, subTitle, NotificationMode.Info, ChatFormatting.WHITE, hash);
    }

    public void info(String title, String subTitle, int hash, ChatFormatting color) {
        notify(title, subTitle, NotificationMode.Info, color, hash);
    }

    public void error(String title, String subTitle) {
        notify(title, subTitle, NotificationMode.Error, ChatFormatting.RED);
    }

    public void error(String title, String subTitle, int hash) {
        notify(title, subTitle, NotificationMode.Error, ChatFormatting.RED, hash);
    }

    public void notifyHud(String title, String subTitle, NotificationMode mode, int hash) {
        notifyReplaceable(hash, title, subTitle, mode);
    }

    public void moduleState(String moduleName, int hash, boolean enabled) {
        String stateText = (enabled
                ? EpsilonTranslations.Module.STATE_ENABLED
                : EpsilonTranslations.Module.STATE_DISABLED
        ).getTranslatedName();
        NotificationMode mode = NotificationMode.fromEnabled(enabled);

        notifyReplaceable(hash, moduleName, StringUtils.capitalize(stateText), mode);
        sendChatMessage(createModuleStateMessage(moduleName, stateText, enabled), hash);
    }

    public FormattedCharSequence applyAnimatedPrefix(FormattedCharSequence original) {
        return ChatUtils.applyAnimatedPrefix(original);
    }

    public void update() {
        Iterator<Notification> iterator = notifications.iterator();
        while (iterator.hasNext()) {
            Notification notification = iterator.next();
            if (notification.isExpired()) {
                iterator.remove();
                unregister(notification);
            }
        }
    }

    public Queue<Notification> getNotifications() {
        return notifications;
    }

    public boolean isEmpty() {
        return notifications.isEmpty();
    }

    public void clearAll() {
        notifications.clear();
        hashCodeMap.clear();
    }

    private void notify(String title, String subTitle, NotificationMode mode, ChatFormatting chatColor) {
        enqueue(new Notification(title, subTitle, mode, false));
        sendChatMessage(createNotificationMessage(title, subTitle, chatColor), null);
    }

    private void notify(String title, String subTitle, NotificationMode mode, ChatFormatting chatColor, int hash) {
        notifyReplaceable(hash, title, subTitle, mode);
        sendChatMessage(createNotificationMessage(title, subTitle, chatColor), hash);
    }

    private void notifyReplaceable(int hash, String title, String subTitle, NotificationMode mode) {
        Notification existing = hashCodeMap.get(hash);
        if (existing != null) {
            if (!existing.isExiting()) {
                existing.refresh(title, subTitle, mode);
                return;
            }
            notifications.remove(existing);
        }

        Notification notification = new Notification(hash, title, subTitle, mode, true);
        enqueue(notification);
        hashCodeMap.put(hash, notification);
    }

    private void enqueue(Notification notification) {
        makeRoomIfNeeded();
        notifications.add(notification);
    }

    private void unregister(Notification notification) {
        if (notification != null && notification.isReplaceable()) {
            hashCodeMap.remove(notification.getId(), notification);
        }
    }

    private Component createNotificationMessage(String title, String subTitle, ChatFormatting chatColor) {
        return Component.literal(title)
                .append(" ")
                .append(Component.literal(subTitle).withStyle(chatColor));
    }

    private Component createModuleStateMessage(String moduleName, String stateText, boolean enabled) {
        return Component.literal(moduleName)
                .append(" ")
                .append(EpsilonTranslations.Module.STATE_PREFIX.getTranslatedName())
                .append(Component.literal(stateText).withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    private void sendChatMessage(Component message, Integer hash) {
        if (!ClientSetting.INSTANCE.chatNotify.getValue()) return;
        if (hash == null) ChatUtils.addChatMessage(message);
        else ChatUtils.addChatMessage(message, hash);
    }

    private void makeRoomIfNeeded() {
        if (notifications.size() >= MAX_NOTIFICATIONS) {
            unregister(notifications.poll());
        }
    }

}
