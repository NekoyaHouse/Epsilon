package com.github.epsilon.graphics.text.ttf

import com.github.epsilon.Constants
import com.github.epsilon.kernel.client.platform.ClientPlatform
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * 安卓环境下 msdfgen 原生库的引导加载器。
 *
 * 背景：2026.13.0 起 TTF 字体改用 MSDF，字形轮廓由 LWJGL 绑定的 `lwjgl-msdfgen` 原生库生成。
 * LWJGL 官方与 FCL/ZL2 自带的安卓 LWJGL fork 都没有提供该模块的安卓原生库；随桌面 natives 分发的
 * `.so` 又是 glibc 链接，无法在安卓(bionic)上加载。
 *
 * 本加载器**仅在精确判定为安卓环境后**，把随 mod 分发的 bionic 版 `liblwjgl_msdfgen.so`
 * 提前解压并显式 `System.load`；其它平台完全不介入，仍走 LWJGL 默认的桌面 natives 检索流程。
 */
internal object MsdfgenNativeBootstrap {

    private const val LIB_NAME = "liblwjgl_msdfgen.so"
    private const val RESOURCE_ROOT = "assets/epsilon/natives/android"

    @Volatile
    private var loaded = false

    /**
     * 在任何字体/UI 代码触发 `org.lwjgl.util.msdfgen.MSDFGen` 类初始化之前调用一次。
     * 非安卓环境直接返回，不做任何资源提取或库加载。
     */
    fun ensureLoaded() {
        if (loaded) {
            return
        }
        // 非安卓：交给 LWJGL 默认的桌面 natives 机制，这里不介入。
        if (!ClientPlatform.isAndroid()) {
            return
        }

        val abi = ClientPlatform.androidAbi()
        if (abi == null) {
            Constants.LOGGER.warn("[msdfgen] 未识别的安卓架构，跳过原生库加载: ${ClientPlatform.architecture()}")
            return
        }

        synchronized(this) {
            if (loaded) {
                return
            }
            try {
                val libFile = extract(abi)
                // 显式按绝对路径加载，库名(soname)与 LWJGL 绑定一致：liblwjgl_msdfgen.so
                System.load(libFile.absolutePath)
                // 把解压目录补进 LWJGL librarypath，后续 loadLibrary 检索也能命中。
                appendLibraryPath(libFile.parentFile.toPath())
                loaded = true
                Constants.LOGGER.info("[msdfgen] 安卓原生库已加载: ${libFile.absolutePath}")
            } catch (t: Throwable) {
                Constants.LOGGER.error("[msdfgen] 安卓原生库加载失败 (abi=$abi)", t)
            }
        }
    }

    /** 从 mod jar 资源中按 ABI 解压原生库到可写临时目录，返回库文件。 */
    private fun extract(abi: String): File {
        val resource = "/$RESOURCE_ROOT/$abi/$LIB_NAME"
        val stream = MsdfgenNativeBootstrap::class.java.getResourceAsStream(resource)
            ?: throw IllegalStateException("未找到安卓原生库资源: $resource")

        val targetDir = Path.of(
            System.getProperty("java.io.tmpdir"),
            "epsilon-natives",
            Constants.VERSION,
            abi,
        ).toFile()
        targetDir.mkdirs()
        val target = File(targetDir, LIB_NAME)

        stream.use { input ->
            Files.copy(input, target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        if (target.length() <= 0L) {
            throw IllegalStateException("安卓原生库为空: ${target.absolutePath}")
        }
        return target
    }

    /** 将目录追加到 `org.lwjgl.librarypath`，供 LWJGL 按库名检索。 */
    private fun appendLibraryPath(dir: Path) {
        val key = "org.lwjgl.librarypath"
        val current = System.getProperty(key)
        val value = if (current.isNullOrBlank()) dir.toString() else "$dir${File.pathSeparator}$current"
        System.setProperty(key, value)
    }
}
