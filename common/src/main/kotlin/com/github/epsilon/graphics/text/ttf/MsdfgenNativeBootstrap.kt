package com.github.epsilon.graphics.text.ttf

import com.github.epsilon.kernel.client.platform.ClientPlatform
import com.github.epsilon.kernel.network.Http
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import org.lwjgl.system.Configuration

internal object MsdfgenNativeBootstrap {

    private const val LIB_NAME = "liblwjgl_msdfgen.so"
    private const val DOWNLOAD_BASE_URL = "https://github.com/lovelisy/Epsilon/releases/download/msdfgen-v1/"
    private const val LIBRARY_PATH_KEY = "org.lwjgl.librarypath"
    private const val JAVA_LIBRARY_PATH_KEY = "java.library.path"
    private const val MODULE_PATH = "org/lwjgl/msdfgen"
    private const val DOWNLOAD_ATTEMPTS = 3

    @Volatile
    private var loaded = false

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
                val libFile = resolveLibrary(abi) ?: return
                System.load(libFile.absolutePath)
                registerLibraryPath(libFile.parentFile.toPath())
                deployToRuntimeLibraryDirs(libFile, abi)
                loaded = true
            } catch (_: Throwable) {
            }
        }
    }

    private fun resolveLibrary(abi: String): File? {
        val dir = cacheDir(abi)
        val target = File(dir, LIB_NAME)
        if (isUsable(target)) {
            return target
        }
        return download(abi, target)
    }

    private fun cacheDir(abi: String): Path {
        return Paths.get(System.getProperty("user.home"), ".epsilon", "assets", "natives", "msdfgen", abi)
    }

    private fun download(abi: String, target: File): File? {
        val url = DOWNLOAD_BASE_URL + "msdfgen-$abi.so"
        val part = File(target.parentFile, target.name + ".part")

        repeat(DOWNLOAD_ATTEMPTS) {
            try {
                Files.createDirectories(target.parentFile.toPath())
                Files.deleteIfExists(part.toPath())
                Http.download(url, part.toPath(), null, null)
                if (!isUsable(part)) {
                    Files.deleteIfExists(part.toPath())
                    return@repeat
                }
                Files.move(part.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                return target
            } catch (_: Throwable) {
                Files.deleteIfExists(part.toPath())
            }
        }
        return null
    }

    private fun isUsable(file: File): Boolean = file.isFile && file.length() > 0L

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
        }
    }

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

    private fun toLwjglArch(abi: String): String = when (abi) {
        "arm64-v8a" -> "arm64"
        "armeabi-v7a" -> "arm32"
        "x86_64" -> "x64"
        "x86" -> "x86"
        else -> abi
    }
}
