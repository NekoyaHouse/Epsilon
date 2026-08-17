package love.slmpc.smtc;

public record SmtcSnapshot(
        boolean available,
        String title,
        String artist,
        String albumTitle,
        String sourceAppId,
        SmtcPlaybackStatus playbackStatus,
        long thumbnailRevision,
        byte[] thumbnail
) {

    public static final SmtcSnapshot UNAVAILABLE = new SmtcSnapshot(
            false, "", "", "", "", SmtcPlaybackStatus.Closed, 0L, null
    );

    static SmtcSnapshot merge(SmtcSnapshot previous, SmtcNativeResult result) {
        if (!result.available()) return UNAVAILABLE;

        byte[] nextThumbnail = result.thumbnail();
        if (nextThumbnail == null && previous.available() && previous.thumbnailRevision() == result.thumbnailRevision()) {
            nextThumbnail = previous.thumbnail();
        }
        return new SmtcSnapshot(
                true,
                safe(result.title()),
                safe(result.artist()),
                safe(result.albumTitle()),
                safe(result.sourceAppId()),
                SmtcPlaybackStatus.fromNative(result.playbackStatus()),
                result.thumbnailRevision(),
                nextThumbnail
        );
    }

    public boolean isPlaying() {
        return playbackStatus == SmtcPlaybackStatus.Playing;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

}
