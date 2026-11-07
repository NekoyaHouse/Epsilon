package com.github.epsilon.native.winrt

import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment

/**
 * Windows.Media.Control（GlobalSystemMediaTransportControls）读取。
 *
 * 只读取当前会话的标题、艺术家、专辑、来源应用、播放状态与封面字节，
 * 不计算也不上报播放进度；接口槽位与 Windows SDK `winrt/windows.media.control.h`、
 * `winrt/windows.storage.streams.h` 中的 MIDL 声明顺序一致。
 */
internal object SmtcSession {

    private const val CLASS_SESSION_MANAGER =
        "Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager"
    private const val CLASS_DATA_READER = "Windows.Storage.Streams.DataReader"

    private const val MAXIMUM_THUMBNAIL_BYTES = 16L * 1024L * 1024L

    private val EMPTY_BYTES = ByteArray(0)

    /**
     * 获取会话管理器。
     *
     * 管理器是进程级单例，调用方应缓存并在 reset 时释放；`RequestAsync` 首次调用较慢。
     */
    fun requestManager(arena: Arena): MemorySegment {
        val factory = WinRtNative.activationFactory(CLASS_SESSION_MANAGER, WinRtNative.IID_MANAGER_STATICS, arena)
        try {
            val operation = WinRtNative.callOutPtr(factory, 6, arena)
                ?: throw WinRtException(0, "IGlobalSystemMediaTransportControlsSessionManagerStatics.RequestAsync")
            try {
                return WinRtNative.asyncInterface(operation, arena)
                    ?: throw WinRtException(0, "RequestAsync returned no manager")
            } finally {
                WinRtNative.release(operation)
            }
        } finally {
            WinRtNative.release(factory)
        }
    }

    /** 当前会话；没有媒体会话时返回 null。 */
    fun currentSession(manager: MemorySegment, arena: Arena): MemorySegment? {
        return WinRtNative.callOutPtr(manager, 6, arena)
    }

    /**
     * 读取会话元数据。
     *
     * 返回值的 [SmtcSessionMetadata.thumbnail] 是单独持有引用的封面流引用，
     * 调用方必须通过 [SmtcSessionMetadata.close] 释放。
     */
    fun readMetadata(session: MemorySegment, arena: Arena): SmtcSessionMetadata {
        val sourceAppId = WinRtNative.callOutHString(session, 6, arena)
        val properties = acquireAsyncInterface(session, 7, arena, "TryGetMediaPropertiesAsync")
        try {
            val playbackInfo = WinRtNative.callOutPtr(session, 9, arena)
                ?: throw WinRtException(0, "GetPlaybackInfo returned no result")
            val playbackStatus = try {
                WinRtNative.callOutInt(playbackInfo, 7, arena)
            } finally {
                WinRtNative.release(playbackInfo)
            }
            val title = WinRtNative.callOutHString(properties, 6, arena)
            val artist = WinRtNative.callOutHString(properties, 9, arena)
            val albumArtist = WinRtNative.callOutHString(properties, 8, arena)
            val albumTitle = WinRtNative.callOutHString(properties, 10, arena)
            val thumbnail = WinRtNative.callOutPtr(properties, 15, arena)
            return SmtcSessionMetadata(title, artist, albumArtist, albumTitle, sourceAppId, playbackStatus, thumbnail)
        } finally {
            WinRtNative.release(properties)
        }
    }

    /** 读取封面字节；引用为空、流过大或读取失败时返回空数组。 */
    fun readThumbnail(reference: MemorySegment, arena: Arena): ByteArray {
        val operation = WinRtNative.callOutPtr(reference, 6, arena) ?: return EMPTY_BYTES
        val stream = try {
            WinRtNative.asyncInterface(operation, arena) ?: return EMPTY_BYTES
        } finally {
            WinRtNative.release(operation)
        }
        try {
            val randomAccess = WinRtNative.queryInterface(stream, WinRtNative.IID_RANDOM_ACCESS_STREAM, arena)
                ?: return EMPTY_BYTES
            try {
                val size = WinRtNative.callOutLong(randomAccess, 6, arena)
                if (size <= 0L || size > MAXIMUM_THUMBNAIL_BYTES) return EMPTY_BYTES
                val input = WinRtNative.callOutPtrWithLongArg(randomAccess, 8, 0L, arena) ?: return EMPTY_BYTES
                try {
                    return readStream(input, size, arena)
                } finally {
                    WinRtNative.tryClose(input, arena)
                    WinRtNative.release(input)
                }
            } finally {
                WinRtNative.release(randomAccess)
            }
        } finally {
            WinRtNative.tryClose(stream, arena)
            WinRtNative.release(stream)
        }
    }

    private fun readStream(input: MemorySegment, size: Long, arena: Arena): ByteArray {
        val factory = WinRtNative.activationFactory(CLASS_DATA_READER, WinRtNative.IID_DATA_READER_FACTORY, arena)
        try {
            val reader = WinRtNative.callOutPtrWithPtrArg(factory, 6, input, arena) ?: return EMPTY_BYTES
            try {
                val loaded = loadAsync(reader, size, arena)
                if (loaded <= 0) return EMPTY_BYTES
                return WinRtNative.readBytes(reader, loaded, arena)
            } finally {
                WinRtNative.tryClose(reader, arena)
                WinRtNative.release(reader)
            }
        } finally {
            WinRtNative.release(factory)
        }
    }

    private fun loadAsync(reader: MemorySegment, size: Long, arena: Arena): Int {
        val operation = WinRtNative.callOutPtrWithIntArg(reader, 29, size.toInt(), arena) ?: return 0
        try {
            return WinRtNative.asyncUInt(operation, arena)
        } finally {
            WinRtNative.release(operation)
        }
    }

    private fun acquireAsyncInterface(owner: MemorySegment, slot: Int, arena: Arena, what: String): MemorySegment {
        val operation = WinRtNative.callOutPtr(owner, slot, arena) ?: throw WinRtException(0, "$what returned no operation")
        try {
            return WinRtNative.asyncInterface(operation, arena) ?: throw WinRtException(0, "$what returned no result")
        } finally {
            WinRtNative.release(operation)
        }
    }
}

/**
 * 一次媒体会话探测的原始结果。
 *
 * [thumbnail] 是封面流引用（IRandomAccessStreamReference），由本对象持有，
 * 使用完必须调用 [close] 释放。
 */
internal class SmtcSessionMetadata(
    val title: String,
    val artist: String,
    val albumArtist: String,
    val albumTitle: String,
    val sourceAppId: String,
    val playbackStatus: Int,
    val thumbnail: MemorySegment?,
) {

    fun close() {
        WinRtNative.release(thumbnail)
    }
}
