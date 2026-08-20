package me.sofurry.smtc;

public enum SmtcPlaybackStatus {

    Closed,
    Opened,
    Changing,
    Stopped,
    Playing,
    Paused,
    Unknown;

    public static SmtcPlaybackStatus fromNative(int value) {
        SmtcPlaybackStatus[] values = values();
        if (value < 0 || value >= Unknown.ordinal()) return Unknown;
        return values[value];
    }

}
