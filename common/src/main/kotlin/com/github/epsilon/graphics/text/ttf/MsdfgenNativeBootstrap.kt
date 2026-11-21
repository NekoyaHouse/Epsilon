package com.github.epsilon.graphics.text.ttf

import com.github.epsilon.Constants
import com.github.epsilon.kernel.client.platform.ClientPlatform
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import org.lwjgl.system.Configuration

/**
 * 安卓环境下 msdfgen 原生库的引导加载器。
 *
 * 背景：2026.13.0 起 TTF 字体改用 MSDF，字形轮廓由 LWJGL 绑定的 `lwjgl-msdfgen` 原生库生成。
 * LWJGL 官方与 FCL/ZL2 自带的安卓 LWJGL fork 都没有提供该模块的安卓原生库；随桌面 natives 分发的
 * `.so` 又是 glibc 链接，无法在安卓(bionic)上加载。
 *
 * 本加载器**仅在精确判定为安卓环境后**，把随 mod 分发的 bionic 版 `liblwjgl_msdfgen.so`
 * 提前解压并显式 `System.load`；其它平台完全不介入，仍走 LWJGL 默认的桌面 natives 检索流程。
 *
 * 注意：LWJGL 的 `Configuration.LIBRARY_PATH` 在类加载时就把 `org.lwjgl.librarypath` 缓存进了
 * 静态字段，运行期只改系统属性不会被重新读取，必须同步调用 `Configuration.LIBRARY_PATH.set(...)`
 * 写入其配置缓存，`Library.loadSystem` 的 librarypath 回退才能扫到本目录。
 */
internal object MsdfgenNativeBootstrap {

    private const val LIB_NAME = "liblwjgl_msdfgen.so"
    private const val RESOURCE_ROOT = "assets/epsilon/natives/android"
    private const val LIBRARY_PATH_KEY = "org.lwjgl.librarypath"
    private const val MODULE_PATH = "org/lwjgl/msdfgen"

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
                // 把解压目录补进 LWJGL librarypath（含其配置缓存），后续 loadSystem 检索也能命中。
                registerLibraryPath(libFile.parentFile.toPath())
                loaded = true
                Constants.LOGGER.info("[msdfgen] 安卓原生库已加载: ${libFile.absolutePath}")
            } catch (t: Throwable) {
                Constants.LOGGER.error("[msdfgen] 安卓原生库加载失败 (abi=$abi)", t)
            }
        }
    }

    /** 从 mod jar 资源中按 ABI 解压原生库到可写缓存目录，返回库文件。 */
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

        // 同时放一份到 LWJGL bundled 子路径（linux/<arch>/org/lwjgl/msdfgen/），
        // 兼容 Library#findFile 按模块子目录结构的优先查找；根目录的扁平文件作为回退。
        val bundledDir = targetDir.resolve("linux").resolve(toLwjglArch(abi)).resolve(MODULE_PATH)
        bundledDir.mkdirs()
        Files.copy(
            target.toPath(),
            bundledDir.resolve(LIB_NAME).toPath(),
            StandardCopyOption.REPLACE_EXISTING,
        )
        return target
    }

    /**
     * 将目录追加到 `org.lwjgl.librarypath`。
     *
     * 除系统属性外，必须同步写入 LWJGL 的 `Configuration.LIBRARY_PATH` 缓存；该缓存在
     * LWJGL core 类加载时初始化，之后 `get()` 不再回读系统属性。
     */
    private fun registerLibraryPath(dir: Path) {
        val existing = System.getProperty(LIBRARY_PATH_KEY)
        val combined = if (existing.isNullOrBlank()) {
            dir.toString()
        } else {
            "$dir${File.pathSeparator}$existing"
        }
        System.setProperty(LIBRARY_PATH_KEY, combined)
        try {
            Configuration.LIBRARY_PATH.set(combined)
            Constants.LOGGER.info("[msdfgen] LWJGL librarypath=${Configuration.LIBRARY_PATH.get()}")
        } catch (t: Throwable) {
            Constants.LOGGER.warn("[msdfgen] 写入 LWJGL LIBRARY_PATH 缓存失败，仅使用系统属性", t)
        }
    }

    /** 安卓 ABI 目录名 → LWJGL bundled 路径中的架构段名。 */
    private fun toLwjglArch(abi: String): String = when (abi) {
        "arm64-v8a" -> "arm64"
        "armeabi-v7a" -> "arm32"
        "x86_64" -> "x64"
        "x86" -> "x86"
        else -> abi
    }
}
