package com.github.epsilon.ffi.smtc

import com.github.epsilon.ffi.winrt.SmtcSession
import com.github.epsilon.ffi.winrt.WinRtException
import com.github.epsilon.ffi.winrt.WinRtNative
import com.github.epsilon.utils.client.ClientPlatform
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment

/**
 * FFM 边界返回的原始 SMTC 数据。
 *
 * @param available         当前是否存在媒体会话
 * @param title             标题
 * @param artist            艺术家（艺术家为空时已回退为专辑艺术家）
 * @param albumTitle        专辑标题
 * @param sourceAppId       Windows 媒体源应用标识
 * @param playbackStatus    Windows SMTC 播放状态枚举值
 * @param thumbnailRevision 封面内容版本
 * @param thumbnail         仅在封面版本变化时返回；空数组表示清除封面，null 表示沿用上一帧
 * @param error             WinRT 查询错误
 */
internal data class SmtcResult(
    val available: Boolean,
    val title: String = "",
    val artist: String = "",
    val albumTitle: String = "",
    val sourceAppId: String = "",
    val playbackStatus: Int = 0,
    val thumbnailRevision: Long = 0L,
    val thumbnail: ByteArray? = null,
    val error: String = "",
)

/**
 * Windows SMTC 读取引擎：用 Panama FFM 直接调用 WinRT COM 接口替换原有的 C++/WinRT + JNI 实现。
 *
 * 线程模型：所有 WinRT 调用都必须发生在同一个已初始化 MTA 公寓的线程上（见 [SmtcService]），
 * 这里用 [lock] 串行化 [poll] 与 [reset]，并缓存会话管理器以减少 `RequestAsync` 开销。
 * 状态语义与旧 C++ 实现保持一致：track 变化或封面缺失超过 2 秒才重新读取封面，
 * 封面版本用 FNV-1a 64 计算，只有版本变化时才把字节交给上层。
 */
internal object SmtcEngine {

    private const val THUMBNAIL_RETRY_NANOS = 2_000_000_000L
    private const val FNV_OFFSET_BASIS = -3750763034362895579L
    private const val FNV_PRIME = 1099511628211L

    private val EMPTY_THUMBNAIL = ByteArray(0)

    private val lock = Any()

    private var resetRequested = false
    private var manager: MemorySegment? = null

    private var trackKey = ""
    private var thumbnail: ByteArray = EMPTY_THUMBNAIL
    private var thumbnailRevision = 0L
    private var deliveredRevision = 0L
    private var thumbnailAttemptNanos = 0L

    /** SMTC 读取是否可用；非 Windows x86_64 平台不加载任何 WinRT 符号。 */
    val available: Boolean
        get() = ClientPlatform.isWindowsX64()

    fun poll(): SmtcResult {
        if (!available) return SmtcResult(available = false)
        synchronized(lock) {
            return try {
                pollLocked()
            } catch (error: WinRtException) {
                SmtcResult(available = false, error = error.message ?: "SMTC WinRT failure")
            } catch (error: Throwable) {
                SmtcResult(available = false, error = error.javaClass.name + ": " + (error.message ?: "no message"))
            }
        }
    }

    /** 请求下一次 [poll] 重置会话管理器与封面缓存（用于启停或切换会话后重新读取）。 */
    fun reset() {
        synchronized(lock) {
            resetRequested = true
        }
    }

    private fun pollLocked(): SmtcResult {
        if (resetRequested) {
            resetRequested = false
            WinRtNative.release(manager)
            manager = null
            clearCachedState()
        }
        WinRtNative.initializeApartment()
        val sessionManager = manager ?: requestManager()
        val arena = Arena.ofConfined()
        try {
            val session = SmtcSession.currentSession(sessionManager, arena)
            if (session == null) {
                clearCachedState()
                return SmtcResult(available = false)
            }
            try {
                return readSnapshot(session, arena)
            } finally {
                WinRtNative.release(session)
            }
        } finally {
            arena.close()
        }
    }

    private fun requestManager(): MemorySegment {
        val arena = Arena.ofConfined()
        try {
            val created = SmtcSession.requestManager(arena)
            manager = created
            return created
        } finally {
            arena.close()
        }
    }

    private fun readSnapshot(session: MemorySegment, arena: Arena): SmtcResult {
        val metadata = SmtcSession.readMetadata(session, arena)
        try {
            val artist = if (metadata.artist.isEmpty()) metadata.albumArtist else metadata.artist
            val nextTrackKey = buildTrackKey(metadata.sourceAppId, metadata.title, artist, metadata.albumTitle)
            val now = System.nanoTime()
            val changedTrack = nextTrackKey != trackKey
            val retryMissingThumbnail = thumbnail.isEmpty() && now - thumbnailAttemptNanos >= THUMBNAIL_RETRY_NANOS
            if (changedTrack || retryMissingThumbnail) {
                trackKey = nextTrackKey
                thumbnailAttemptNanos = now
                val reference = metadata.thumbnail
                thumbnail = if (reference == null) EMPTY_THUMBNAIL else SmtcSession.readThumbnail(reference, arena)
                thumbnailRevision = computeRevision(nextTrackKey, thumbnail)
            }
            var deliveredThumbnail: ByteArray? = null
            if (thumbnailRevision != deliveredRevision) {
                deliveredThumbnail = thumbnail
                deliveredRevision = thumbnailRevision
            }
            return SmtcResult(
                available = true,
                title = metadata.title,
                artist = artist,
                albumTitle = metadata.albumTitle,
                sourceAppId = metadata.sourceAppId,
                playbackStatus = metadata.playbackStatus,
                thumbnailRevision = thumbnailRevision,
                thumbnail = deliveredThumbnail,
            )
        } finally {
            metadata.close()
        }
    }

    private fun clearCachedState() {
        trackKey = ""
        thumbnail = EMPTY_THUMBNAIL
        thumbnailRevision = 0L
        deliveredRevision = 0L
        thumbnailAttemptNanos = 0L
    }

    private fun buildTrackKey(sourceAppId: String, title: String, artist: String, albumTitle: String): String {
        return sourceAppId + '\n' + title + '\n' + artist + '\n' + albumTitle
    }

    /** 封面版本：FNV-1a 64（先 track_key 的 UTF-16LE 字节，再封面字节），0 用 1 代替。 */
    private fun computeRevision(key: String, bytes: ByteArray): Long {
        var value = hashBytes(FNV_OFFSET_BASIS, key.toByteArray(Charsets.UTF_16LE))
        value = hashBytes(value, bytes)
        value = value xor bytes.size.toLong()
        value *= FNV_PRIME
        return if (value == 0L) 1L else value
    }

    private fun hashBytes(initial: Long, bytes: ByteArray): Long {
        var value = initial
        for (byte in bytes) {
            value = value xor (byte.toLong() and 0xFFL)
            value *= FNV_PRIME
        }
        return value
    }
}
