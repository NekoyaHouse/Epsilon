package com.github.epsilon.graphics.text;

import com.github.epsilon.Constants;
import com.github.epsilon.assets.resources.ResourceLocationUtils;
import com.github.epsilon.graphics.text.ttf.TtfFontLoader;
import com.github.epsilon.modules.impl.ClientSetting;
import net.minecraft.resources.Identifier;

import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public class StaticFontLoader {

    private static final String[] FONT_FILE_EXTENSIONS = {".ttf", ".otf", ".ttc"};
    private static final String SIZE_REFERENCE_SAMPLE = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789简体中文字体设置默认自定义战斗移动玩家渲染";
    private static final Identifier DEFAULT_FONT_ID = ResourceLocationUtils.getIdentifier("fonts/font.ttf");
    private static TtfFontLoader builtinDefault = new TtfFontLoader(DEFAULT_FONT_ID);
    private static final float DEFAULT_VISUAL_HEIGHT = builtinDefault.fontFile.getVisualHeight(SIZE_REFERENCE_SAMPLE);

    public static volatile TtfFontLoader DEFAULT = builtinDefault;

    public static final TtfFontLoader ICONS = new TtfFontLoader(ResourceLocationUtils.getIdentifier("fonts/icons.ttf"));

    public static final TtfFontLoader JURA_LIGHT = new TtfFontLoader(ResourceLocationUtils.getIdentifier("fonts/jura-light.ttf"));

    public static final TtfFontLoader OSAKA_CHIPS = new TtfFontLoader(ResourceLocationUtils.getIdentifier("fonts/osakachips.ttf"));

    private static TtfFontLoader customDefault;
    private static Path customDefaultPath;
    private static ClientSetting.FontMode appliedMode;
    private static String appliedCustomFont;
    private static boolean destroyed;
    private static volatile Map<String, Path> systemFontLookup;

    public static TtfFontLoader defaultFont() {
        if (destroyed) {
            return DEFAULT;
        }

        ClientSetting settings = ClientSetting.INSTANCE;
        ClientSetting.FontMode mode = settings.font.getValue();
        String fontPath = settings.customFont.getValue();
        if (isApplied(mode, fontPath)) {
            return DEFAULT;
        }
        return applyDefaultFont(mode, fontPath);
    }

    private static synchronized TtfFontLoader applyDefaultFont(ClientSetting.FontMode mode, String fontPath) {
        if (destroyed) {
            return DEFAULT;
        }

        if (isApplied(mode, fontPath)) {
            return DEFAULT;
        }

        if (appliedMode == null && mode == ClientSetting.FontMode.Default && DEFAULT == builtinDefault && customDefault == null) {
            appliedMode = ClientSetting.FontMode.Default;
            appliedCustomFont = null;
            return DEFAULT;
        }

        if (mode == ClientSetting.FontMode.Custom) {
            applyCustomDefault(fontPath);
        } else {
            applyBuiltinDefault();
        }
        appliedMode = mode;
        appliedCustomFont = fontPath;
        return DEFAULT;
    }

    private static boolean isApplied(ClientSetting.FontMode mode, String fontPath) {
        return mode == appliedMode
                && (mode != ClientSetting.FontMode.Custom || Objects.equals(fontPath, appliedCustomFont));
    }

    public static synchronized void destroyDefault() {
        if (destroyed) {
            return;
        }

        TtfFontLoader current = DEFAULT;
        TtfFontLoader builtin = builtinDefault;
        TtfFontLoader custom = customDefault;

        customDefault = null;
        customDefaultPath = null;
        builtinDefault = null;
        appliedMode = null;
        appliedCustomFont = null;

        destroyLoader(current);
        destroyLoaderIfDifferent(builtin, current);
        destroyLoaderIfDifferent(custom, current, builtin);
        destroyed = true;
    }

    private static void applyBuiltinDefault() {
        TtfFontLoader previous = DEFAULT;
        TtfFontLoader previousBuiltin = builtinDefault;
        TtfFontLoader previousCustom = customDefault;
        TtfFontLoader next = new TtfFontLoader(DEFAULT_FONT_ID);

        builtinDefault = next;
        customDefault = null;
        customDefaultPath = null;
        DEFAULT = next;

        destroyLoader(previous);
        destroyLoaderIfDifferent(previousBuiltin, previous);
        destroyLoaderIfDifferent(previousCustom, previous, previousBuiltin);
    }

    private static void applyCustomDefault(String fontPath) {
        Path path = resolveCustomFont(fontPath);
        if (path == null || !Files.isRegularFile(path)) {
            applyBuiltinDefault();
            return;
        }

        if (customDefault != null && path.equals(customDefaultPath)) {
            DEFAULT = customDefault;
            return;
        }

        TtfFontLoader previous = DEFAULT;
        TtfFontLoader previousBuiltin = builtinDefault;
        TtfFontLoader previousCustom = customDefault;
        TtfFontLoader next;
        try {
            next = new TtfFontLoader(path);
            next.setRenderScale(customRenderScale(next));
        } catch (RuntimeException e) {
            Constants.LOGGER.warn("Failed to load custom default font: {}", path, e);
            applyBuiltinDefault();
            return;
        }

        builtinDefault = null;
        customDefault = next;
        customDefaultPath = path;
        DEFAULT = next;

        destroyLoader(previous);
        destroyLoaderIfDifferent(previousBuiltin, previous);
        destroyLoaderIfDifferent(previousCustom, previous, previousBuiltin);
    }

    private static Path resolveCustomFont(String fontPath) {
        if (fontPath == null || fontPath.isBlank()) {
            return null;
        }

        String normalized = stripQuotes(fontPath.trim());
        try {
            Path path = Path.of(normalized);
            Path directPath = resolveExistingFontPath(path);
            if (directPath != null) {
                return directPath;
            }

            if (path.isAbsolute()) {
                return resolveSystemFont(normalized);
            }

            directPath = resolveExistingFontPath(path.toAbsolutePath().normalize());
            if (directPath != null) {
                return directPath;
            }

            directPath = resolveExistingFontPath(Path.of(System.getProperty("user.home"), ".epsilon", "fonts")
                    .resolve(path)
                    .toAbsolutePath()
                    .normalize());
            if (directPath != null) {
                return directPath;
            }
        } catch (InvalidPathException ignored) {
        }

        return resolveSystemFont(normalized);
    }

    private static String stripQuotes(String value) {
        boolean doubleQuoted = value.startsWith("\"") && value.endsWith("\"");
        boolean singleQuoted = value.startsWith("'") && value.endsWith("'");
        if (value.length() >= 2 && (doubleQuoted || singleQuoted)) {
            return value.substring(1, value.length() - 1).trim();
        }
        return value;
    }

    private static Path resolveExistingFontPath(Path path) {
        Path normalized = path.normalize();
        if (Files.isRegularFile(normalized)) {
            return normalized.toAbsolutePath().normalize();
        }

        if (hasSupportedFontExtension(normalized)) {
            return null;
        }

        Path fileName = normalized.getFileName();
        if (fileName == null) {
            return null;
        }

        Path parent = normalized.getParent();
        for (String extension : FONT_FILE_EXTENSIONS) {
            Path candidate = parent == null
                    ? Path.of(fileName + extension)
                    : parent.resolve(fileName + extension);
            if (Files.isRegularFile(candidate)) {
                return candidate.toAbsolutePath().normalize();
            }
        }
        return null;
    }

    private static Path resolveSystemFont(String fontName) {
        String key = normalizeFontLookupKey(fontName);
        if (key.isEmpty()) {
            return null;
        }
        return getSystemFontLookup().get(key);
    }

    private static Map<String, Path> getSystemFontLookup() {
        Map<String, Path> lookup = systemFontLookup;
        if (lookup != null) {
            return lookup;
        }

        synchronized (StaticFontLoader.class) {
            lookup = systemFontLookup;
            if (lookup == null) {
                lookup = buildSystemFontLookup();
                systemFontLookup = lookup;
            }
            return lookup;
        }
    }

    private static Map<String, Path> buildSystemFontLookup() {
        Map<String, Path> lookup = new LinkedHashMap<>();
        for (Path directory : systemFontDirectories()) {
            if (!Files.isDirectory(directory)) {
                continue;
            }

            try (Stream<Path> paths = Files.walk(directory)) {
                paths.filter(Files::isRegularFile)
                        .filter(StaticFontLoader::hasSupportedFontExtension)
                        .sorted(Comparator.comparing(path -> path.toString().toLowerCase(Locale.ROOT)))
                        .forEach(path -> registerSystemFont(lookup, path.toAbsolutePath().normalize()));
            } catch (IOException | SecurityException ignored) {
            }
        }
        return lookup;
    }

    private static Set<Path> systemFontDirectories() {
        Set<Path> directories = new LinkedHashSet<>();
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String userHome = System.getProperty("user.home");

        if (osName.contains("win")) {
            addDirectory(directories, System.getenv("WINDIR"), "Fonts");
            addDirectory(directories, System.getenv("SystemRoot"), "Fonts");
            addDirectory(directories, System.getenv("LOCALAPPDATA"), "Microsoft", "Windows", "Fonts");
        } else if (osName.contains("mac")) {
            addDirectory(directories, "/System/Library/Fonts");
            addDirectory(directories, "/Library/Fonts");
            addDirectory(directories, userHome, "Library", "Fonts");
        } else {
            addDirectory(directories, "/usr/share/fonts");
            addDirectory(directories, "/usr/local/share/fonts");
            addDirectory(directories, userHome, ".fonts");
            addDirectory(directories, userHome, ".local", "share", "fonts");
        }

        return directories;
    }

    private static void addDirectory(Set<Path> directories, String first, String... more) {
        if (first == null || first.isBlank()) {
            return;
        }

        try {
            directories.add(Path.of(first, more).toAbsolutePath().normalize());
        } catch (InvalidPathException ignored) {
        }
    }

    private static void registerSystemFont(Map<String, Path> lookup, Path path) {
        String fileName = path.getFileName().toString();
        putFontLookupKey(lookup, fileName, path);
        putFontLookupKey(lookup, stripFontExtension(fileName), path);

        try {
            for (Font font : Font.createFonts(path.toFile())) {
                putFontLookupKey(lookup, font.getFontName(Locale.ROOT), path);
                putFontLookupKey(lookup, font.getFamily(Locale.ROOT), path);
                putFontLookupKey(lookup, font.getPSName(), path);
            }
        } catch (FontFormatException | IOException | RuntimeException ignored) {
        }
    }

    private static void putFontLookupKey(Map<String, Path> lookup, String name, Path path) {
        String key = normalizeFontLookupKey(name);
        if (!key.isEmpty()) {
            lookup.putIfAbsent(key, path);
        }
    }

    private static String normalizeFontLookupKey(String name) {
        if (name == null) {
            return "";
        }

        return stripFontExtension(stripQuotes(name.trim()))
                .replace("-", "")
                .replace("_", "")
                .replace(" ", "")
                .toLowerCase(Locale.ROOT);
    }

    private static String stripFontExtension(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        for (String extension : FONT_FILE_EXTENSIONS) {
            if (lower.endsWith(extension)) {
                return value.substring(0, value.length() - extension.length());
            }
        }
        return value;
    }

    private static boolean hasSupportedFontExtension(Path path) {
        Path fileName = path.getFileName();
        return fileName != null && hasSupportedFontExtension(fileName.toString());
    }

    private static boolean hasSupportedFontExtension(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        for (String extension : FONT_FILE_EXTENSIONS) {
            if (lower.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    private static float customRenderScale(TtfFontLoader fontLoader) {
        float customHeight = fontLoader.fontFile.getVisualHeight(SIZE_REFERENCE_SAMPLE);
        if (!Float.isFinite(customHeight) || customHeight <= 0.0f) {
            return 1.0f;
        }
        return DEFAULT_VISUAL_HEIGHT / customHeight;
    }

    private static void destroyLoader(TtfFontLoader fontLoader) {
        if (fontLoader != null) {
            fontLoader.destroy();
        }
    }

    private static void destroyLoaderIfDifferent(TtfFontLoader fontLoader, TtfFontLoader... existing) {
        if (fontLoader == null) {
            return;
        }

        for (TtfFontLoader other : existing) {
            if (fontLoader == other) {
                return;
            }
        }
        fontLoader.destroy();
    }

}
