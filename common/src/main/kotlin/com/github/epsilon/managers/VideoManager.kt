package com.github.epsilon.managers

import com.github.epsilon.graphics.video.VideoPlayer
import java.io.IOException
import java.nio.file.Files

object VideoManager {

    /**
     * 从 `~/.epsilon/assets/video` 加载按需下载的主菜单背景视频。
     *
     * 资源缺失时直接抛错，由调用方决定是提示下载还是回退到经典背景。
     */
    fun loadBackground() {
        val background = AssetManager.videoFile()
        if (Files.notExists(background)) {
            throw IOException("Main menu video asset is missing: $background")
        }
        AssetManager.ensureFfmpegLoaded()
        VideoPlayer.init(background.toFile())
    }

}
