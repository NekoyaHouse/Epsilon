package com.github.epsilon.graphics.text

import com.github.epsilon.Constants
import com.github.epsilon.assets.resources.ResourceLocationUtils
import com.github.epsilon.graphics.text.ttf.TtfFontLoader
import com.github.epsilon.modules.impl.ClientSetting
import net.minecraft.resources.Identifier
import java.awt.Font
import java.awt.FontFormatException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.util.*

object StaticFontLoader {

    private val FONT_FILE_EXTENSIONS = arrayOf(".ttf", ".otf", ".ttc")
    private const val SIZE_REFERENCE_SAMPLE =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789简体中文字体设置默认自定义战斗移动玩家渲染"
    private val DEFAULT_FONT_ID: Identifier = ResourceLocationUtils.getIdentifier("fonts/font.ttf")
    private var builtinDefault: TtfFontLoader? = TtfFontLoader(DEFAULT_FONT_ID)
    private val DEFAULT_VISUAL_HEIGHT: Float = builtinDefault!!.fontFile.getVisualHeight(SIZE_REFERENCE_SAMPLE)

    @Volatile
    private var DEFAULT: TtfFontLoader = builtinDefault!!

    val ICONS: TtfFontLoader = TtfFontLoader(ResourceLocationUtils.getIdentifier("fonts/icons.ttf"))

    val JURA_LIGHT: TtfFontLoader = TtfFontLoader(ResourceLocationUtils.getIdentifier("fonts/jura-light.ttf"))

    val CINZEL_DECORATIVE: TtfFontLoader =
        TtfFontLoader(ResourceLocationUtils.getIdentifier("fonts/cinzeldecorative.ttf"))

    val OSAKA_CHIPS: TtfFontLoader = TtfFontLoader(ResourceLocationUtils.getIdentifier("fonts/osakachips.ttf"))

    private var customDefault: TtfFontLoader? = null
    private var customDefaultPath: Path? = null
    private var appliedMode: ClientSetting.FontMode? = null
    private var appliedCustomFont: String? = null
    private var destroyed = false

    @Volatile
    private var systemFontLookup: Map<String, Path>? = null

    fun defaultFont(): TtfFontLoader {
        if (destroyed) return DEFAULT
        val settings = ClientSetting
        val mode = settings.font.value
        val fontPath = settings.customFont
        if (isApplied(mode, fontPath)) return DEFAULT
        return applyDefaultFont(mode, fontPath)
    }

    // 原 Java 的 static synchronized 锁在 StaticFontLoader.class 上，这里显式用同一个类锁，
    // 不要换成 @Synchronized（在 object 中会锁 INSTANCE，与 getSystemFontLookup 的类锁不是同一把）。
    private fun applyDefaultFont(mode: ClientSetting.FontMode, fontPath: String?): TtfFontLoader =
        synchronized(StaticFontLoader::class.java) {
            if (destroyed) {
                return@synchronized DEFAULT
            }

            if (isApplied(mode, fontPath)) {
                return@synchronized DEFAULT
            }

            if (appliedMode == null && mode == ClientSetting.FontMode.Default && DEFAULT === builtinDefault && customDefault == null) {
                appliedMode = ClientSetting.FontMode.Default
                appliedCustomFont = null
                return@synchronized DEFAULT
            }

            if (mode == ClientSetting.FontMode.Custom) {
                applyCustomDefault(fontPath)
            } else {
                applyBuiltinDefault()
            }
            appliedMode = mode
            appliedCustomFont = fontPath
            DEFAULT
        }

    private fun isApplied(mode: ClientSetting.FontMode, fontPath: String?): Boolean {
        return mode == appliedMode &&
                (mode != ClientSetting.FontMode.Custom || fontPath == appliedCustomFont)
    }

    fun destroyDefault() {
        synchronized(StaticFontLoader::class.java) {
            if (destroyed) {
                return@synchronized
            }

            val current = DEFAULT
            val builtin = builtinDefault
            val custom = customDefault

            customDefault = null
            customDefaultPath = null
            builtinDefault = null
            appliedMode = null
            appliedCustomFont = null

            destroyLoader(current)
            destroyLoaderIfDifferent(builtin, current)
            destroyLoaderIfDifferent(custom, current, builtin)
            destroyed = true
        }
    }

    private fun applyBuiltinDefault() {
        val previous = DEFAULT
        val previousBuiltin = builtinDefault
        val previousCustom = customDefault
        val next = TtfFontLoader(DEFAULT_FONT_ID)

        builtinDefault = next
        customDefault = null
        customDefaultPath = null
        DEFAULT = next

        destroyLoader(previous)
        destroyLoaderIfDifferent(previousBuiltin, previous)
        destroyLoaderIfDifferent(previousCustom, previous, previousBuiltin)
    }

    private fun applyCustomDefault(fontPath: String?) {
        val path = resolveCustomFont(fontPath)
        if (path == null || !Files.isRegularFile(path)) {
            applyBuiltinDefault()
            return
        }

        if (customDefault != null && path == customDefaultPath) {
            DEFAULT = customDefault!!
            return
        }

        val previous = DEFAULT
        val previousBuiltin = builtinDefault
        val previousCustom = customDefault
        val next = try {
            TtfFontLoader(path).also { it.setRenderScale(customRenderScale(it)) }
        } catch (e: RuntimeException) {
            Constants.LOGGER.warn("Failed to load custom default font: {}", path, e)
            applyBuiltinDefault()
            return
        }

        builtinDefault = null
        customDefault = next
        customDefaultPath = path
        DEFAULT = next

        destroyLoader(previous)
        destroyLoaderIfDifferent(previousBuiltin, previous)
        destroyLoaderIfDifferent(previousCustom, previous, previousBuiltin)
    }

    private fun resolveCustomFont(fontPath: String?): Path? {
        if (fontPath == null || fontPath.isBlank()) {
            return null
        }

        val normalized = stripQuotes(fontPath.trim())
        try {
            val path = Path.of(normalized)
            var directPath = resolveExistingFontPath(path)
            if (directPath != null) {
                return directPath
            }

            if (path.isAbsolute) {
                return resolveSystemFont(normalized)
            }

            directPath = resolveExistingFontPath(path.toAbsolutePath().normalize())
            if (directPath != null) {
                return directPath
            }

            directPath = resolveExistingFontPath(
                Path.of(System.getProperty("user.home"), ".epsilon", "fonts")
                    .resolve(path)
                    .toAbsolutePath()
                    .normalize()
            )
            if (directPath != null) {
                return directPath
            }
        } catch (ignored: InvalidPathException) {
        }

        return resolveSystemFont(normalized)
    }

    private fun stripQuotes(value: String): String {
        val doubleQuoted = value.startsWith("\"") && value.endsWith("\"")
        val singleQuoted = value.startsWith("'") && value.endsWith("'")
        if (value.length >= 2 && (doubleQuoted || singleQuoted)) {
            return value.substring(1, value.length - 1).trim()
        }
        return value
    }

    private fun resolveExistingFontPath(path: Path): Path? {
        val normalized = path.normalize()
        if (Files.isRegularFile(normalized)) {
            return normalized.toAbsolutePath().normalize()
        }

        if (hasSupportedFontExtension(normalized)) {
            return null
        }

        val fileName = normalized.fileName
        if (fileName == null) {
            return null
        }

        val parent = normalized.parent
        for (extension in FONT_FILE_EXTENSIONS) {
            val candidate = if (parent == null) {
                Path.of(fileName.toString() + extension)
            } else {
                parent.resolve(fileName.toString() + extension)
            }
            if (Files.isRegularFile(candidate)) {
                return candidate.toAbsolutePath().normalize()
            }
        }
        return null
    }

    private fun resolveSystemFont(fontName: String): Path? {
        val key = normalizeFontLookupKey(fontName)
        if (key.isEmpty()) {
            return null
        }
        return getSystemFontLookup()[key]
    }

    private fun getSystemFontLookup(): Map<String, Path> {
        val cached = systemFontLookup
        if (cached != null) {
            return cached
        }

        return synchronized(StaticFontLoader::class.java) {
            val current = systemFontLookup
            if (current != null) {
                current
            } else {
                val built = buildSystemFontLookup()
                systemFontLookup = built
                built
            }
        }
    }

    private fun buildSystemFontLookup(): Map<String, Path> {
        val lookup = LinkedHashMap<String, Path>()
        for (directory in systemFontDirectories()) {
            if (!Files.isDirectory(directory)) {
                continue
            }

            try {
                Files.walk(directory).use { paths ->
                    paths.filter { Files.isRegularFile(it) }
                        .filter { hasSupportedFontExtension(it) }
                        .sorted(Comparator.comparing<Path, String> { path -> path.toString().lowercase(Locale.ROOT) })
                        .forEach { path -> registerSystemFont(lookup, path.toAbsolutePath().normalize()) }
                }
            } catch (ignored: IOException) {
            } catch (ignored: SecurityException) {
            }
        }
        return lookup
    }

    private fun systemFontDirectories(): Set<Path> {
        val directories = LinkedHashSet<Path>()
        val osName = System.getProperty("os.name", "").lowercase(Locale.ROOT)
        val userHome = System.getProperty("user.home")

        if (osName.contains("win")) {
            addDirectory(directories, System.getenv("WINDIR"), "Fonts")
            addDirectory(directories, System.getenv("SystemRoot"), "Fonts")
            addDirectory(directories, System.getenv("LOCALAPPDATA"), "Microsoft", "Windows", "Fonts")
        } else if (osName.contains("mac")) {
            addDirectory(directories, "/System/Library/Fonts")
            addDirectory(directories, "/Library/Fonts")
            addDirectory(directories, userHome, "Library", "Fonts")
        } else {
            addDirectory(directories, "/usr/share/fonts")
            addDirectory(directories, "/usr/local/share/fonts")
            addDirectory(directories, userHome, ".fonts")
            addDirectory(directories, userHome, ".local", "share", "fonts")
        }

        return directories
    }

    private fun addDirectory(directories: MutableSet<Path>, first: String?, vararg more: String) {
        if (first == null || first.isBlank()) {
            return
        }

        try {
            directories.add(Path.of(first, *more).toAbsolutePath().normalize())
        } catch (ignored: InvalidPathException) {
        }
    }

    private fun registerSystemFont(lookup: MutableMap<String, Path>, path: Path) {
        val fileName = path.fileName.toString()
        putFontLookupKey(lookup, fileName, path)
        putFontLookupKey(lookup, stripFontExtension(fileName), path)

        try {
            for (font in Font.createFonts(path.toFile())) {
                putFontLookupKey(lookup, font.getFontName(Locale.ROOT), path)
                putFontLookupKey(lookup, font.getFamily(Locale.ROOT), path)
                putFontLookupKey(lookup, font.psName, path)
            }
        } catch (ignored: FontFormatException) {
        } catch (ignored: IOException) {
        } catch (ignored: RuntimeException) {
        }
    }

    private fun putFontLookupKey(lookup: MutableMap<String, Path>, name: String?, path: Path) {
        val key = normalizeFontLookupKey(name)
        if (key.isNotEmpty()) {
            lookup.putIfAbsent(key, path)
        }
    }

    private fun normalizeFontLookupKey(name: String?): String {
        if (name == null) {
            return ""
        }

        return stripFontExtension(stripQuotes(name.trim()))
            .replace("-", "")
            .replace("_", "")
            .replace(" ", "")
            .lowercase(Locale.ROOT)
    }

    private fun stripFontExtension(value: String): String {
        val lower = value.lowercase(Locale.ROOT)
        for (extension in FONT_FILE_EXTENSIONS) {
            if (lower.endsWith(extension)) {
                return value.substring(0, value.length - extension.length)
            }
        }
        return value
    }

    private fun hasSupportedFontExtension(path: Path): Boolean {
        val fileName = path.fileName
        return fileName != null && hasSupportedFontExtension(fileName.toString())
    }

    private fun hasSupportedFontExtension(fileName: String): Boolean {
        val lower = fileName.lowercase(Locale.ROOT)
        for (extension in FONT_FILE_EXTENSIONS) {
            if (lower.endsWith(extension)) {
                return true
            }
        }
        return false
    }

    private fun customRenderScale(fontLoader: TtfFontLoader): Float {
        val customHeight = fontLoader.fontFile.getVisualHeight(SIZE_REFERENCE_SAMPLE)
        if (!customHeight.isFinite() || customHeight <= 0.0f) {
            return 1.0f
        }
        return DEFAULT_VISUAL_HEIGHT / customHeight
    }

    private fun destroyLoader(fontLoader: TtfFontLoader?) {
        if (fontLoader != null) {
            fontLoader.destroy()
        }
    }

    private fun destroyLoaderIfDifferent(fontLoader: TtfFontLoader?, vararg existing: TtfFontLoader?) {
        if (fontLoader == null) {
            return
        }

        for (other in existing) {
            if (fontLoader === other) {
                return
            }
        }
        fontLoader.destroy()
    }

}
