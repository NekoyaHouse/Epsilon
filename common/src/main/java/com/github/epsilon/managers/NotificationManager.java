package com.github.epsilon.managers;

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

/**
 * 管理客户端 HUD 通知及其聊天栏镜像。
 * <p>
 * 不带 {@code hash} 的通知会作为独立条目入队；带 {@code hash} 的通知具有可替换语义，
 * 相同标识的活动通知会刷新内容和显示时间，同时复用聊天栏消息位置。
 * 通知队列不是线程安全容器，调用方应在客户端主线程中访问。
 */
public class NotificationManager {

    public static final NotificationManager INSTANCE = new NotificationManager();

    private static final int MAX_NOTIFICATIONS = 5;

    private final Queue<Notification> notifications = new ArrayDeque<>();
    private final Map<Integer, Notification> hashCodeMap = new HashMap<>();

    private NotificationManager() {
    }

    /**
     * 发布一条成功通知。
     *
     * @param title    标题
     * @param subTitle 副标题
     */
    public void success(String title, String subTitle) {
        notify(title, subTitle, NotificationMode.Success, ChatFormatting.GREEN);
    }

    /**
     * 发布或刷新一条可替换的成功通知。
     *
     * @param title    标题
     * @param subTitle 副标题
     * @param hash     用于识别同一通知的稳定标识
     */
    public void success(String title, String subTitle, int hash) {
        notify(title, subTitle, NotificationMode.Success, ChatFormatting.GREEN, hash);
    }

    /**
     * 发布一条信息通知。
     *
     * @param title    标题
     * @param subTitle 副标题
     */
    public void info(String title, String subTitle) {
        notify(title, subTitle, NotificationMode.Info, ChatFormatting.WHITE);
    }

    /**
     * 发布或刷新一条可替换的信息通知。
     *
     * @param title    标题
     * @param subTitle 副标题
     * @param hash     用于识别同一通知的稳定标识
     */
    public void info(String title, String subTitle, int hash) {
        notify(title, subTitle, NotificationMode.Info, ChatFormatting.WHITE, hash);
    }

    /**
     * 发布或刷新一条可替换的信息通知，并指定聊天栏副标题颜色。
     * HUD 颜色仍由 {@link NotificationMode#Info} 决定。
     *
     * @param title    标题
     * @param subTitle 副标题
     * @param hash     用于识别同一通知的稳定标识
     * @param color    聊天栏副标题颜色
     */
    public void info(String title, String subTitle, int hash, ChatFormatting color) {
        notify(title, subTitle, NotificationMode.Info, color, hash);
    }

    /**
     * 发布一条错误通知。
     *
     * @param title    标题
     * @param subTitle 副标题
     */
    public void error(String title, String subTitle) {
        notify(title, subTitle, NotificationMode.Error, ChatFormatting.RED);
    }

    /**
     * 发布或刷新一条可替换的错误通知。
     *
     * @param title    标题
     * @param subTitle 副标题
     * @param hash     用于识别同一通知的稳定标识
     */
    public void error(String title, String subTitle, int hash) {
        notify(title, subTitle, NotificationMode.Error, ChatFormatting.RED, hash);
    }

    /**
     * 发布一条警告通知。
     *
     * @param title    标题
     * @param subTitle 副标题
     */
    public void warning(String title, String subTitle) {
        notify(title, subTitle, NotificationMode.Warning, ChatFormatting.YELLOW);
    }

    /**
     * 发布或刷新一条可替换的警告通知。
     *
     * @param title    标题
     * @param subTitle 副标题
     * @param hash     用于识别同一通知的稳定标识
     */
    public void warning(String title, String subTitle, int hash) {
        notify(title, subTitle, NotificationMode.Warning, ChatFormatting.YELLOW, hash);
    }

    /**
     * 仅向 HUD 发布或刷新一条可替换通知，不创建聊天栏镜像。
     *
     * @param title    标题
     * @param subTitle 副标题
     * @param mode     通知类型
     * @param hash     用于识别同一通知的稳定标识
     */
    public void notifyHud(String title, String subTitle, NotificationMode mode, int hash) {
        notifyReplaceable(hash, title, subTitle, mode);
    }

    /**
     * 发布模块启用状态通知，并生成对应的本地化聊天栏消息。
     *
     * @param moduleName 模块显示名称
     * @param hash       用于识别该模块状态通知的稳定标识
     * @param enabled    {@code true} 表示已启用，{@code false} 表示已禁用
     */
    public void moduleState(String moduleName, int hash, boolean enabled) {
        String stateText = (enabled
                ? EpsilonTranslations.Module.STATE_ENABLED
                : EpsilonTranslations.Module.STATE_DISABLED
        ).getTranslatedName();
        NotificationMode mode = NotificationMode.fromEnabled(enabled);

        notifyReplaceable(hash, moduleName, StringUtils.capitalize(stateText), mode);
        sendChatMessage(createModuleStateMessage(moduleName, stateText, enabled), hash);
    }

    /**
     * 为已有聊天文本应用 Epsilon 的动态前缀。
     *
     * @param original 原始格式化文本
     * @return 带动态前缀的格式化文本
     */
    public FormattedCharSequence applyAnimatedPrefix(FormattedCharSequence original) {
        return ChatUtils.applyAnimatedPrefix(original);
    }

    /**
     * 移除已完成显示及退出动画的通知，并同步清理可替换通知索引。
     */
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

    /**
     * 返回当前活动通知的内部队列。
     * <p>
     * 该对象是实时视图，HUD 渲染器只应遍历，不应直接修改；队列变更统一由本管理器负责。
     *
     * @return 当前活动通知队列
     */
    public Queue<Notification> getNotifications() {
        return notifications;
    }

    /**
     * 判断当前是否没有活动通知。
     *
     * @return 队列为空时返回 {@code true}
     */
    public boolean isEmpty() {
        return notifications.isEmpty();
    }

    /**
     * 立即清空全部通知及可替换通知索引。
     */
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

    /**
     * 创建或刷新带稳定标识的通知。
     * <p>
     * 尚未进入退出阶段的旧条目会原地刷新；已经退出的旧条目会被移除并重新入队，
     * 以保证新通知能够重新播放入场动画。
     */
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
