package com.github.epsilon.smtc

/**
 * 某一时刻的 Windows SMTC 媒体会话快照。
 *
 * @param thumbnail 仅在封面版本变化时携带字节；空数组表示清除封面，null 表示沿用上一帧
 */
data class SmtcSnapshot(
    val available: Boolean,
    val title: String,
    val artist: String,
    val albumTitle: String,
    val sourceAppId: String,
    val playbackStatus: SmtcPlaybackStatus,
    val thumbnailRevision: Long,
    val thumbnail: ByteArray?,
) {

    val isPlaying: Boolean
        get() = playbackStatus == SmtcPlaybackStatus.Playing

    companion object {

        @JvmField
        val UNAVAILABLE: SmtcSnapshot = SmtcSnapshot(
            false, "", "", "", "", SmtcPlaybackStatus.Closed, 0L, null,
        )

        /**
         * 合并一次探测结果：探测不可用时整体回到 [UNAVAILABLE]，封面为 null 时沿用上一帧。
         */
        internal fun merge(previous: SmtcSnapshot, result: SmtcResult): SmtcSnapshot {
            if (!result.available) return UNAVAILABLE
            return SmtcSnapshot(
                true,
                result.title,
                result.artist,
                result.albumTitle,
                result.sourceAppId,
                SmtcPlaybackStatus.fromNative(result.playbackStatus),
                result.thumbnailRevision,
                result.thumbnail ?: previous.thumbnail,
            )
        }

    }

}
