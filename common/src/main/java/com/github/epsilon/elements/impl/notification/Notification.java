package com.github.epsilon.elements.impl.notification;

public class Notification {

    private static final long EXIT_ANIMATION_DURATION = 500L;

    private final int id;
    private String title;
    private String subTitle;
    private NotificationMode mode;
    private long createTime;
    private final boolean replaceable;
    private boolean skipIntroAnim = false;

    public Notification(int id, String title, String subTitle, NotificationMode mode, boolean replaceable) {
        this.id = id;
        this.title = title;
        this.subTitle = subTitle;
        this.mode = mode;
        this.createTime = System.currentTimeMillis();
        this.replaceable = replaceable;
    }

    public Notification(String title, String subTitle, NotificationMode mode, boolean replaceable) {
        this(0, title, subTitle, mode, replaceable);
    }

    public void refresh(String newTitle, String newSubTitle, NotificationMode newMode) {
        this.title = newTitle;
        this.subTitle = newSubTitle;
        this.mode = newMode;
        this.createTime = System.currentTimeMillis();
        this.skipIntroAnim = true;
    }

    public boolean isExpired() {
        return getElapsedTime() > getDisplayTime() + EXIT_ANIMATION_DURATION;
    }

    public boolean isExiting() {
        return getExitTime() >= 0L;
    }

    @Override
    public int hashCode() {
        return replaceable ? id : System.identityHashCode(this);
    }

    public String getTitle() {
        return title;
    }

    public String getSubTitle() {
        return subTitle;
    }

    public NotificationMode getMode() {
        return mode;
    }

    public boolean isReplaceable() {
        return replaceable;
    }

    public long getCreateTime() {
        return createTime;
    }

    public long getElapsedTime() {
        return System.currentTimeMillis() - createTime;
    }

    public long getExitTime() {
        return getElapsedTime() - getDisplayTime();
    }

    public int getDisplayTime() {
        return Notifications.INSTANCE.displayTime.getValue();
    }

    public int getId() {
        return id;
    }

    public boolean shouldSkipIntroAnim() {
        return skipIntroAnim;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Notification that = (Notification) obj;
        return replaceable && that.replaceable && id == that.id;
    }

}
