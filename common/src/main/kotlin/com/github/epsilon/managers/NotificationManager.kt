package com.github.epsilon.managers

import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.elements.impl.notification.Notification
import com.github.epsilon.elements.impl.notification.NotificationMode
import com.github.epsilon.modules.impl.ClientSetting
import com.github.epsilon.utils.player.ChatUtils
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence
import org.apache.commons.lang3.StringUtils
import java.util.ArrayDeque
import java.util.HashMap
import java.util.Queue

/**
 * 管理客户端 HUD 通知及其聊天栏镜像。
 *
 * 不带 `hash` 的通知会作为独立条目入队；带 `hash` 的通知具有可替换语义，
 * 相同标识的活动通知会刷新内容和显示时间，同时复用聊天栏消息位置。
 * 通知队列不是线程安全容器，调用方应在客户端主线程中访问。
 */
object NotificationManager {

    private const val MAX_NOTIFICATIONS = 5

    private val notifications: Queue<Notification> = ArrayDeque()
    private val hashCodeMap = HashMap<Int, Notification>()

    /**
     * 发布一条成功通知。
     */
    fun success(title: String, subTitle: String) {
        notify(title, subTitle, NotificationMode.Success, ChatFormatting.GREEN)
    }

    /**
     * 发布或刷新一条可替换的成功通知。
     */
    fun success(title: String, subTitle: String, hash: Int) {
        notify(title, subTitle, NotificationMode.Success, ChatFormatting.GREEN, hash)
    }

    /**
     * 发布一条信息通知。
     */
    fun info(title: String, subTitle: String) {
        notify(title, subTitle, NotificationMode.Info, ChatFormatting.WHITE)
    }

    /**
     * 发布或刷新一条可替换的信息通知。
     */
    fun info(title: String, subTitle: String, hash: Int) {
        notify(title, subTitle, NotificationMode.Info, ChatFormatting.WHITE, hash)
    }

    /**
     * 发布或刷新一条可替换的信息通知，并指定聊天栏副标题颜色。
     * HUD 颜色仍由 [NotificationMode.Info] 决定。
     */
    fun info(title: String, subTitle: String, hash: Int, color: ChatFormatting) {
        notify(title, subTitle, NotificationMode.Info, color, hash)
    }

    /**
     * 发布一条错误通知。
     */
    fun error(title: String, subTitle: String) {
        notify(title, subTitle, NotificationMode.Error, ChatFormatting.RED)
    }

    /**
     * 发布或刷新一条可替换的错误通知。
     */
    fun error(title: String, subTitle: String, hash: Int) {
        notify(title, subTitle, NotificationMode.Error, ChatFormatting.RED, hash)
    }

    /**
     * 发布一条警告通知。
     */
    fun warning(title: String, subTitle: String) {
        notify(title, subTitle, NotificationMode.Warning, ChatFormatting.YELLOW)
    }

    /**
     * 发布或刷新一条可替换的警告通知。
     */
    fun warning(title: String, subTitle: String, hash: Int) {
        notify(title, subTitle, NotificationMode.Warning, ChatFormatting.YELLOW, hash)
    }

    /**
     * 仅向 HUD 发布或刷新一条可替换通知，不创建聊天栏镜像。
     */
    fun notifyHud(title: String, subTitle: String, mode: NotificationMode, hash: Int) {
        notifyReplaceable(hash, title, subTitle, mode)
    }

    /**
     * 发布模块启用状态通知，并生成对应的本地化聊天栏消息。
     */
    fun moduleState(moduleName: String, hash: Int, enabled: Boolean) {
        val stateText = (
            if (enabled) EpsilonTranslations.Module.STATE_ENABLED
            else EpsilonTranslations.Module.STATE_DISABLED
            ).getTranslatedName()
        val mode = NotificationMode.fromEnabled(enabled)

        notifyReplaceable(hash, moduleName, StringUtils.capitalize(stateText), mode)
        sendChatMessage(createModuleStateMessage(moduleName, stateText, enabled), hash)
    }

    /**
     * 为已有聊天文本应用 Epsilon 的动态前缀。
     */
    fun applyAnimatedPrefix(original: FormattedCharSequence): FormattedCharSequence {
        return ChatUtils.applyAnimatedPrefix(original)
    }

    /**
     * 移除已完成显示及退出动画的通知，并同步清理可替换通知索引。
     */
    fun update() {
        val iterator = notifications.iterator()
        while (iterator.hasNext()) {
            val notification = iterator.next()
            if (notification.isExpired) {
                iterator.remove()
                unregister(notification)
            }
        }
    }

    /**
     * 返回当前活动通知的内部队列。
     *
     * 该对象是实时视图，HUD 渲染器只应遍历，不应直接修改；队列变更统一由本管理器负责。
     */
    fun getNotifications(): Queue<Notification> {
        return notifications
    }

    /**
     * 判断当前是否没有活动通知。
     */
    fun isEmpty(): Boolean {
        return notifications.isEmpty()
    }

    /**
     * 立即清空全部通知及可替换通知索引。
     */
    fun clearAll() {
        notifications.clear()
        hashCodeMap.clear()
    }

    private fun notify(title: String, subTitle: String, mode: NotificationMode, chatColor: ChatFormatting) {
        enqueue(Notification(title, subTitle, mode, false))
        sendChatMessage(createNotificationMessage(title, subTitle, chatColor), null)
    }

    private fun notify(
        title: String, subTitle: String, mode: NotificationMode, chatColor: ChatFormatting, hash: Int,
    ) {
        notifyReplaceable(hash, title, subTitle, mode)
        sendChatMessage(createNotificationMessage(title, subTitle, chatColor), hash)
    }

    /**
     * 创建或刷新带稳定标识的通知。
     *
     * 尚未进入退出阶段的旧条目会原地刷新；已经退出的旧条目会被移除并重新入队，
     * 以保证新通知能够重新播放入场动画。
     */
    private fun notifyReplaceable(hash: Int, title: String, subTitle: String, mode: NotificationMode) {
        val existing = hashCodeMap[hash]
        if (existing != null) {
            if (!existing.isExiting) {
                existing.refresh(title, subTitle, mode)
                return
            }
            notifications.remove(existing)
        }

        val notification = Notification(hash, title, subTitle, mode, true)
        enqueue(notification)
        hashCodeMap[hash] = notification
    }

    private fun enqueue(notification: Notification) {
        makeRoomIfNeeded()
        notifications.add(notification)
    }

    private fun unregister(notification: Notification?) {
        if (notification != null && notification.isReplaceable) {
            hashCodeMap.remove(notification.id, notification)
        }
    }

    private fun createNotificationMessage(title: String, subTitle: String, chatColor: ChatFormatting): Component {
        return Component.literal(title)
            .append(" ")
            .append(Component.literal(subTitle).withStyle(chatColor))
    }

    private fun createModuleStateMessage(moduleName: String, stateText: String, enabled: Boolean): Component {
        return Component.literal(moduleName)
            .append(" ")
            .append(EpsilonTranslations.Module.STATE_PREFIX.getTranslatedName())
            .append(Component.literal(stateText).withStyle(if (enabled) ChatFormatting.GREEN else ChatFormatting.RED))
    }

    private fun sendChatMessage(message: Component, hash: Int?) {
        if (!ClientSetting.chatNotify) return
        if (hash == null) ChatUtils.addChatMessage(message)
        else ChatUtils.addChatMessage(message, hash)
    }

    private fun makeRoomIfNeeded() {
        if (notifications.size >= MAX_NOTIFICATIONS) {
            unregister(notifications.poll())
        }
    }

}
