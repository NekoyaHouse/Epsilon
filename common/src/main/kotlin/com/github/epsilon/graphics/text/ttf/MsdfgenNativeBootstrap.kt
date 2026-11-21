package com.github.epsilon.graphics.text.ttf

import com.github.epsilon.Constants
import com.github.epsilon.kernel.client.platform.ClientPlatform
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import org.lwjgl.system.Configuration

/**
 * 安卓环境（FCL / ZL2 / Pojav 等手机启动器）下 msdfgen 原生库的引导加载器。
 *
 * LWJGL 官方与启动器自带的安卓 LWJGL fork 都未提供 msdfgen 模块的安卓原生库，桌面 natives 为
 * glibc 链接、无法在 bionic 上加载。这里在判定为安卓后，把随 mod 分发的 bionic 版
 * `liblwjgl_msdfgen.so` 解压并显式加载，同时铺进 LWJGL 的 librarypath 与 java.library.path
 * 检索目录；非安卓环境完全不介入，仍走 LWJGL 默认流程。
 *
 * 注意：`Configuration.LIBRARY_PATH` 在 LWJGL core 类加载时即缓存，运行期只改系统属性不会被
 * 重读，需同步调用 [Configuration.LIBRARY_PATH.set] 写入其配置缓存。
 */
internal object MsdfgenNativeBootstrap {

    private const val LIB_NAME = "liblwjgl_msdfgen.so"
    private const val RESOURCE_ROOT = "assets/epsilon/natives/android"
    private const val LIBRARY_PATH_KEY = "org.lwjgl.librarypath"
    private const val JAVA_LIBRARY_PATH_KEY = "java.library.path"
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
        if (!ClientPlatform.isAndroid()) {
            return
        }

        val abi = ClientPlatform.androidAbi() ?: return

        synchronized(this) {
            if (loaded) {
                return
            }
            try {
                val libFile = extract(abi)
                // 显式按绝对路径加载，库名(soname)与 LWJGL 绑定一致：liblwjgl_msdfgen.so
                System.load(libFile.absolutePath)
                registerLibraryPath(libFile.parentFile.toPath())
                deployToRuntimeLibraryDirs(libFile, abi)
                loaded = true
            } catch (_: Throwable) {
                // 加载失败不阻断启动，保持静默。
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
        // 兼容 Library#findFile 按模块子目录结构的优先查找；根目录扁平文件作为回退。
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
     * 将目录追加到 `org.lwjgl.librarypath`，并同步写入 LWJGL 的 `Configuration.LIBRARY_PATH`
     * 缓存（该缓存在 core 类加载时初始化，之后不再回读系统属性）。
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
        } catch (_: Throwable) {
            // 缓存写入失败时退化为仅系统属性，保持静默。
        }
    }

    /**
     * 把 `.so` 复制到 `java.library.path` / `org.lwjgl.librarypath` 中已存在且可写的目录，
     * 扁平文件与 bundled 子路径各一份，供 LWJGL 其余回退路径命中。
     */
    private fun deployToRuntimeLibraryDirs(libFile: File, abi: String) {
        val dirs = linkedSetOf<String>()
        listOf(JAVA_LIBRARY_PATH_KEY, LIBRARY_PATH_KEY).forEach { key ->
            System.getProperty(key)?.let { value ->
                value.split(File.pathSeparator).forEach { p ->
                    if (p.isNotBlank()) {
                        dirs.add(p)
                    }
                }
            }
        }

        val arch = toLwjglArch(abi)
        for (dirStr in dirs) {
            val dir = File(dirStr)
            if (!dir.isDirectory || !dir.canWrite()) {
                continue
            }
            runCatching {
                Files.copy(
                    libFile.toPath(),
                    File(dir, LIB_NAME).toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
                val bundled = File(dir, "linux/$arch/$MODULE_PATH")
                bundled.mkdirs()
                Files.copy(
                    libFile.toPath(),
                    File(bundled, LIB_NAME).toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
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
