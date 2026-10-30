package com.github.epsilon.elements.impl.notification

class Notification(
    val id: Int,
    var title: String,
    var subTitle: String,
    var mode: NotificationMode,
    val isReplaceable: Boolean,
) {

    var createTime: Long = System.currentTimeMillis()

    private var skipIntroAnim: Boolean = false

    constructor(title: String, subTitle: String, mode: NotificationMode, replaceable: Boolean) : this(0, title, subTitle, mode, replaceable)

    fun refresh(newTitle: String, newSubTitle: String, newMode: NotificationMode) {
        this.title = newTitle
        this.subTitle = newSubTitle
        this.mode = newMode
        this.createTime = System.currentTimeMillis()
        this.skipIntroAnim = true
    }

    val isExpired: Boolean
        get() = elapsedTime > displayTime + EXIT_ANIMATION_DURATION

    val isExiting: Boolean
        get() = exitTime >= 0L

    override fun hashCode(): Int {
        return if (isReplaceable) id else System.identityHashCode(this)
    }

    val elapsedTime: Long
        get() = System.currentTimeMillis() - createTime

    val exitTime: Long
        get() = elapsedTime - displayTime

    val displayTime: Int
        get() = Notifications.getDisplayTime()

    fun shouldSkipIntroAnim(): Boolean {
        return skipIntroAnim
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as Notification
        return isReplaceable && that.isReplaceable && id == that.id
    }

    companion object {

        private const val EXIT_ANIMATION_DURATION = 500L

        fun preview(title: String, subTitle: String, mode: NotificationMode): Notification {
            val notification = Notification(title, subTitle, mode, false)
            notification.skipIntroAnim = true
            return notification
        }

    }

}
