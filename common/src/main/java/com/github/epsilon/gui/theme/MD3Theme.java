package com.github.epsilon.gui.theme;

import com.github.epsilon.modules.impl.ClientSetting;
import net.minecraft.util.Mth;

import java.awt.*;

public class MD3Theme {

    public static Color SHADOW = new Color(0, 0, 0, 96);

    public static Color SURFACE = new Color(20, 18, 24, 238);
    public static Color SURFACE_DIM = new Color(15, 13, 19, 232);
    public static Color SURFACE_CONTAINER_LOW = new Color(29, 27, 32, 240);
    public static Color SURFACE_CONTAINER = new Color(33, 31, 38, 244);
    public static Color SURFACE_CONTAINER_HIGH = new Color(43, 41, 48, 248);
    public static Color SURFACE_CONTAINER_HIGHEST = new Color(54, 52, 59, 252);

    public static Color OUTLINE = new Color(147, 143, 153, 180);
    public static Color OUTLINE_SOFT = new Color(147, 143, 153, 96);

    public static Color PRIMARY = new Color(208, 188, 255);
    public static Color ON_PRIMARY = new Color(56, 30, 114);
    public static Color PRIMARY_CONTAINER = new Color(79, 55, 139, 236);
    public static Color ON_PRIMARY_CONTAINER = new Color(234, 221, 255);

    public static Color SECONDARY = new Color(204, 194, 220);
    public static Color ON_SECONDARY = new Color(51, 45, 65);
    public static Color SECONDARY_CONTAINER = new Color(74, 68, 88, 236);
    public static Color ON_SECONDARY_CONTAINER = new Color(232, 222, 248);

    public static Color TERTIARY = new Color(239, 184, 200);
    public static Color ON_TERTIARY = new Color(73, 37, 50);
    public static Color TERTIARY_CONTAINER = new Color(99, 59, 72, 236);
    public static Color ON_TERTIARY_CONTAINER = new Color(255, 216, 228);
    public static Color INVERSE_SURFACE = new Color(230, 224, 233);
    public static Color INVERSE_ON_SURFACE = new Color(49, 48, 51);

    public static Color TEXT_PRIMARY = new Color(230, 224, 233);
    public static Color TEXT_SECONDARY = new Color(202, 196, 208);
    public static Color TEXT_MUTED = new Color(147, 143, 153);
    public static Color SUCCESS = new Color(204, 194, 220);
    public static Color ERROR = new Color(242, 184, 181);

    private static ClientSetting.ThemePreset appliedPreset = null;
    private static ClientSetting.ThemeMode appliedMode = null;

    public static final int PANEL_RADIUS = 17;
    public static final int SECTION_RADIUS = 13;
    public static final int CARD_RADIUS = 9;
    public static final int CHIP_RADIUS = 999;
    public static final float PANEL_SHADOW_BLUR = 24.0f;
    public static final int PANEL_SHADOW_ALPHA = 96;
    public static final float POPUP_SHADOW_BLUR = 14.0f;
    public static final int POPUP_SHADOW_ALPHA = 112;
    public static final float FLOATING_LABEL_SHADOW_BLUR = 12.0f;
    public static final int FLOATING_LABEL_SHADOW_ALPHA = 96;

    public static final float OUTER_PADDING = 5.0f;
    public static final float SECTION_GAP = 3.0f;
    public static final float INNER_PADDING = 5.0f;
    public static final float ROW_GAP = 3.0f;
    public static final float PANEL_TITLE_INSET = 6.0f;
    public static final float PANEL_VIEWPORT_INSET = 3.0f;
    public static final float ROW_CONTENT_INSET = 5.0f;
    public static final float ROW_TRAILING_INSET = 5.0f;
    public static final float RAIL_COLLAPSED_WIDTH = 42.0f;
    public static final float RAIL_EXPANDED_WIDTH = 120.0f;
    public static final float CONTROL_HEIGHT = 18.0f;
    public static final float CONTROL_RADIUS = 7.0f;
    public static final float COMPACT_CHIP_HEIGHT = 16.0f;
    public static final float SWITCH_WIDTH = 26.0f;
    public static final float SWITCH_HEIGHT = 16.0f;
    public static final float SWITCH_HANDLE_SIZE_OFF = 8.0f;
    public static final float SWITCH_HANDLE_SIZE_ON = 12.0f;
    public static final float SWITCH_HANDLE_INSET_OFF = 4.0f;
    public static final float SWITCH_HANDLE_INSET_ON = 2.0f;
    public static final float SWITCH_STATE_LAYER_SIZE = 20.0f;

    private MD3Theme() {
    }

    public static void syncFromSettings() {
        ClientSetting.ThemePreset preset = ClientSetting.INSTANCE.themePreset.getValue();
        ClientSetting.ThemeMode mode = ClientSetting.INSTANCE.themeMode.getValue();
        if (preset == appliedPreset && mode == appliedMode) {
            return;
        }
        ThemePalette palette = ThemePalette.forPreset(preset, mode);
        SHADOW = palette.shadow();
        SURFACE = palette.surface();
        SURFACE_DIM = palette.surfaceDim();
        SURFACE_CONTAINER_LOW = palette.surfaceContainerLow();
        SURFACE_CONTAINER = palette.surfaceContainer();
        SURFACE_CONTAINER_HIGH = palette.surfaceContainerHigh();
        SURFACE_CONTAINER_HIGHEST = palette.surfaceContainerHighest();
        OUTLINE = palette.outline();
        OUTLINE_SOFT = withAlpha(palette.outline(), 96);
        PRIMARY = palette.primary();
        ON_PRIMARY = palette.onPrimary();
        PRIMARY_CONTAINER = palette.primaryContainer();
        ON_PRIMARY_CONTAINER = palette.onPrimaryContainer();
        SECONDARY = palette.secondary();
        ON_SECONDARY = palette.onSecondary();
        SECONDARY_CONTAINER = palette.secondaryContainer();
        ON_SECONDARY_CONTAINER = palette.onSecondaryContainer();
        TERTIARY = palette.tertiary();
        ON_TERTIARY = palette.onTertiary();
        TERTIARY_CONTAINER = palette.tertiaryContainer();
        ON_TERTIARY_CONTAINER = palette.onTertiaryContainer();
        INVERSE_SURFACE = palette.inverseSurface();
        INVERSE_ON_SURFACE = palette.inverseOnSurface();
        TEXT_PRIMARY = palette.textPrimary();
        TEXT_SECONDARY = palette.textSecondary();
        TEXT_MUTED = palette.textMuted();
        SUCCESS = palette.secondary();
        ERROR = palette.error();
        appliedPreset = preset;
        appliedMode = mode;
    }

    public static Color withAlpha(Color color, int alpha) {
        int clampedAlpha = Mth.clamp(alpha, 0, 255);
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), clampedAlpha);
    }

    public static Color lerp(Color start, Color end, float delta) {
        float t = Mth.clamp(delta, 0.0f, 1.0f);
        int r = (int) (start.getRed() + (end.getRed() - start.getRed()) * t);
        int g = (int) (start.getGreen() + (end.getGreen() - start.getGreen()) * t);
        int b = (int) (start.getBlue() + (end.getBlue() - start.getBlue()) * t);
        int a = (int) (start.getAlpha() + (end.getAlpha() - start.getAlpha()) * t);
        return new Color(r, g, b, a);
    }

    public static boolean isLightTheme() {
        return ClientSetting.INSTANCE.themeMode.is(ClientSetting.ThemeMode.Light);
    }

    public static Color stateLayer(Color color, float progress, int maxAlpha) {
        return withAlpha(color, (int) (Mth.clamp(progress, 0.0f, 1.0f) * Mth.clamp(maxAlpha, 0, 255)));
    }

    public static Color rowSurface(float hoverProgress) {
        return lerp(SURFACE_CONTAINER, SURFACE_CONTAINER_HIGH, hoverProgress);
    }

    public static Color filledFieldSurface(boolean focused, float hoverProgress) {
        if (focused) {
            float focusMix = isLightTheme() ? 0.58f : 0.42f;
            return lerp(SURFACE_CONTAINER_HIGH, PRIMARY_CONTAINER, focusMix);
        }
        Color base = isLightTheme() ? SURFACE_CONTAINER : SURFACE_CONTAINER_LOW;
        return lerp(base, SURFACE_CONTAINER_HIGHEST, Mth.clamp(hoverProgress * 0.85f, 0.0f, 1.0f));
    }

    public static Color filledFieldContent(boolean focused) {
        return TEXT_PRIMARY;
    }

    public static Color filledFieldCaret(boolean focused) {
        return focused ? PRIMARY : TEXT_PRIMARY;
    }

    public static Color filledFieldIndicator(boolean focused, float hoverProgress) {
        if (focused) {
            return PRIMARY;
        }
        return lerp(withAlpha(OUTLINE, 96), withAlpha(TEXT_PRIMARY, 136), Mth.clamp(hoverProgress * 0.55f, 0.0f, 1.0f));
    }

    public static Color segmentedControlSurface() {
        return isLightTheme() ? SURFACE : SURFACE_CONTAINER_HIGH;
    }

    public static Color segmentedControlIndicator() {
        return SECONDARY_CONTAINER;
    }

    public static Color segmentedControlActiveLabel() {
        return ON_SECONDARY_CONTAINER;
    }

    public static Color segmentedControlInactiveLabel() {
        return isLightTheme() ? TEXT_SECONDARY : TEXT_MUTED;
    }

    public static Color switchTrack(float toggleProgress) {
        return lerp(SURFACE_CONTAINER_HIGHEST, PRIMARY, toggleProgress);
    }

    public static Color switchKnob(float toggleProgress) {
        return lerp(OUTLINE, ON_PRIMARY, toggleProgress);
    }

    public static Color switchTrackOutline(float toggleProgress, float hoverProgress) {
        float inactive = 1.0f - Mth.clamp(toggleProgress, 0.0f, 1.0f);
        float hoverMix = Mth.clamp(hoverProgress * 0.35f, 0.0f, 1.0f);
        Color base = lerp(OUTLINE, TEXT_PRIMARY, hoverMix);
        return withAlpha(base, (int) (inactive * (isLightTheme() ? 188 : 168)));
    }

    public static float switchTrackOutlineWidth(float toggleProgress) {
        return 1.0f + (1.0f - Mth.clamp(toggleProgress, 0.0f, 1.0f)) * 0.1f;
    }

    private record ThemePalette(
            Color shadow,
            Color surface,
            Color surfaceDim,
            Color surfaceContainerLow,
            Color surfaceContainer,
            Color surfaceContainerHigh,
            Color surfaceContainerHighest,
            Color outline,
            Color primary,
            Color onPrimary,
            Color primaryContainer,
            Color onPrimaryContainer,
            Color secondary,
            Color onSecondary,
            Color secondaryContainer,
            Color onSecondaryContainer,
            Color tertiary,
            Color onTertiary,
            Color tertiaryContainer,
            Color onTertiaryContainer,
            Color inverseSurface,
            Color inverseOnSurface,
            Color textPrimary,
            Color textSecondary,
            Color textMuted,
            Color error
    ) {
        private static ThemePalette forPreset(ClientSetting.ThemePreset preset, ClientSetting.ThemeMode mode) {
            return switch (preset) {
                case TonalSpot -> mode == ClientSetting.ThemeMode.Dark
                        ? paletteDark("#141218", "#1B1820", "#211F26", "#2B2930", "#35333B", "#D0BCFF", "#381E72", "#4F378B", "#EADDFF", "#CCC2DC", "#332D41", "#4A4458", "#E8DEF8", "#EFB8C8", "#492532", "#633B48", "#FFD8E4")
                        : paletteLight("#FFFBFE", "#F7F2FA", "#F3EDF7", "#ECE6F0", "#E6E0E9", "#6750A4", "#FFFFFF", "#EADDFF", "#21005D", "#625B71", "#FFFFFF", "#E8DEF8", "#1D192B", "#7D5260", "#FFFFFF", "#FFD8E4", "#31111D");
                case Neutral -> mode == ClientSetting.ThemeMode.Dark
                        ? paletteDark("#141314", "#1C1B1C", "#211F21", "#2C2A2C", "#363436", "#CFC3D9", "#362B3E", "#4B4153", "#E9DDEC", "#CCC2CF", "#342F38", "#4B4450", "#E8DDEB", "#D8C2C7", "#3C2B2F", "#544247", "#F4DCE1")
                        : paletteLight("#FEF7FF", "#F7EEF8", "#F1E8F2", "#EBE1EB", "#E4DBE5", "#6C4F75", "#FFFFFF", "#F2DAFF", "#261430", "#665A69", "#FFFFFF", "#EBDDDF", "#201A21", "#81525D", "#FFFFFF", "#FFD9E0", "#33111A");
                case Vibrant -> mode == ClientSetting.ThemeMode.Dark
                        ? paletteDark("#16111C", "#1D1725", "#241D2D", "#2F2639", "#3A3044", "#E3B7FF", "#4A1F63", "#663282", "#F5D9FF", "#D7BEE4", "#3D2D48", "#564260", "#F2DAFF", "#FFB4AB", "#690005", "#93000A", "#FFDAD6")
                        : paletteLight("#FFF7FD", "#F8EDF8", "#F3E7F4", "#EDE0EE", "#E6D9E7", "#7A2F9A", "#FFFFFF", "#FFD7F6", "#320046", "#6A586F", "#FFFFFF", "#EEDCF4", "#231727", "#80535E", "#FFFFFF", "#FFD9E0", "#33111A");
                case Expressive -> mode == ClientSetting.ThemeMode.Dark
                        ? paletteDark("#14141B", "#1C1C24", "#22222A", "#2D2D36", "#373740", "#FFB1C8", "#561D33", "#73324B", "#FFD9E2", "#D9C2CB", "#3F2A33", "#574049", "#F4DDE6", "#C4D7FF", "#1E3A6B", "#35528A", "#DBE1FF")
                        : paletteLight("#FFF8F8", "#F8EFEF", "#F3E8E8", "#EDE1E1", "#E7DADB", "#904A61", "#FFFFFF", "#FFD9E2", "#3B071D", "#6F5862", "#FFFFFF", "#F7D9E3", "#29141D", "#48648F", "#FFFFFF", "#DBE1FF", "#001D36");
                case Fidelity -> mode == ClientSetting.ThemeMode.Dark
                        ? paletteDark("#10141A", "#161B22", "#1C2128", "#252B33", "#2E353E", "#7CC6FF", "#00344F", "#0E4A69", "#CEE5FF", "#B8CADB", "#203845", "#374E5B", "#D4E5F8", "#9AD0B8", "#103826", "#28503B", "#B6EFD0")
                        : paletteLight("#F6FAFF", "#EEF3F9", "#E8EDF3", "#E1E8EF", "#DAE2EA", "#00658A", "#FFFFFF", "#CDEFFD", "#001E2C", "#50606E", "#FFFFFF", "#D3E5F5", "#0C1D28", "#3C6651", "#FFFFFF", "#BEECD1", "#072012");
                case Content -> mode == ClientSetting.ThemeMode.Dark
                        ? paletteDark("#11151A", "#181D23", "#1E232A", "#282E36", "#313841", "#91C9FF", "#11314B", "#294964", "#D1E5FF", "#C1C9D6", "#28333D", "#3F4B56", "#DEE4F2", "#D7C29F", "#41311A", "#59472E", "#F5DEB8")
                        : paletteLight("#F8FAFC", "#F0F3F7", "#E9EDF2", "#E2E7EC", "#DBE1E7", "#355F8D", "#FFFFFF", "#D1E4FF", "#001D36", "#5A616C", "#FFFFFF", "#DEE3F2", "#171C25", "#735B3E", "#FFFFFF", "#F5DEB8", "#291805");
                case Rainbow -> mode == ClientSetting.ThemeMode.Dark
                        ? paletteDark("#141318", "#1B1A20", "#211F26", "#2B2831", "#35323B", "#C8C1FF", "#2E2A67", "#454287", "#E4DFFF", "#D7C2E6", "#3A3047", "#53485F", "#F2DBFF", "#FFB59D", "#5E2F1C", "#7D4732", "#FFDCCF")
                        : paletteLight("#FCF8FF", "#F4EEF8", "#EEE8F3", "#E7E1EC", "#E0DAE6", "#5B5BD6", "#FFFFFF", "#E0DFFF", "#191962", "#675A70", "#FFFFFF", "#ECDCF5", "#21182A", "#8A4F3A", "#FFFFFF", "#FFDCCF", "#351100");
                case FruitSalad -> mode == ClientSetting.ThemeMode.Dark
                        ? paletteDark("#121415", "#181B1C", "#1E2122", "#282C2D", "#313637", "#8FD665", "#173807", "#2D5218", "#C2F19A", "#C8D0C0", "#2F372B", "#475041", "#E4F0D9", "#FFB77B", "#5A2E00", "#7D4300", "#FFDCC2")
                        : paletteLight("#FAFFF4", "#F2F8EB", "#EAF0E3", "#E3EAD9", "#DCE3D2", "#466A1F", "#FFFFFF", "#C7F089", "#102000", "#5E6657", "#FFFFFF", "#E0E9D5", "#1B1F17", "#965100", "#FFFFFF", "#FFDCC2", "#301400");
                case Monochrome -> mode == ClientSetting.ThemeMode.Dark
                        ? paletteDark("#121212", "#1A1A1A", "#202020", "#2A2A2A", "#333333", "#E6E1E5", "#1B1B1B", "#383838", "#F3EEF2", "#D1CCD0", "#2C2C2C", "#434343", "#EFE9ED", "#CFC8CD", "#2B2B2B", "#444444", "#F0E9EE")
                        : paletteLight("#FCFCFC", "#F3F3F3", "#ECECEC", "#E5E5E5", "#DEDEDE", "#5F5E61", "#FFFFFF", "#E4E1E4", "#1C1B1E", "#605D62", "#FFFFFF", "#E5E1E6", "#1C1B1F", "#625D61", "#FFFFFF", "#E7E0E5", "#201A1E");
            };
        }

        private static ThemePalette paletteDark(String surface, String low, String container, String high, String highest,
                                                String primary, String onPrimary, String primaryContainer, String onPrimaryContainer,
                                                String secondary, String onSecondary, String secondaryContainer, String onSecondaryContainer,
                                                String tertiary, String onTertiary, String tertiaryContainer, String onTertiaryContainer) {
            Color surfaceColor = color(surface, 238);
            Color surfaceDimColor = color(low, 232);
            Color lowColor = color(low, 240);
            Color containerColor = color(container, 244);
            Color highColor = color(high, 248);
            Color highestColor = color(highest, 252);
            Color outlineColor = color("#938F99", 180);
            Color textPrimaryColor = color("#ECE6F0", 255);
            Color textSecondaryColor = color("#CAC4D0", 255);
            Color textMutedColor = color("#938F99", 255);
            return new ThemePalette(
                    new Color(0, 0, 0, 96),
                    surfaceColor,
                    surfaceDimColor,
                    lowColor,
                    containerColor,
                    highColor,
                    highestColor,
                    outlineColor,
                    color(primary, 255),
                    color(onPrimary, 255),
                    color(primaryContainer, 236),
                    color(onPrimaryContainer, 255),
                    color(secondary, 255),
                    color(onSecondary, 255),
                    color(secondaryContainer, 236),
                    color(onSecondaryContainer, 255),
                    color(tertiary, 255),
                    color(onTertiary, 255),
                    color(tertiaryContainer, 236),
                    color(onTertiaryContainer, 255),
                    color("#E6E0E9", 255),
                    color("#313033", 255),
                    textPrimaryColor,
                    textSecondaryColor,
                    textMutedColor,
                    color("#F2B8B5", 255)
            );
        }

        private static ThemePalette paletteLight(String surface, String low, String container, String high, String highest,
                                                 String primary, String onPrimary, String primaryContainer, String onPrimaryContainer,
                                                 String secondary, String onSecondary, String secondaryContainer, String onSecondaryContainer,
                                                 String tertiary, String onTertiary, String tertiaryContainer, String onTertiaryContainer) {
            Color surfaceColor = color(surface, 242);
            Color surfaceDimColor = color(low, 238);
            Color lowColor = color(low, 242);
            Color containerColor = color(container, 246);
            Color highColor = color(high, 250);
            Color highestColor = color(highest, 252);
            Color outlineColor = color("#79747E", 180);
            Color textPrimaryColor = color("#1C1B1F", 255);
            Color textSecondaryColor = color("#49454F", 255);
            Color textMutedColor = color("#79747E", 255);
            return new ThemePalette(
                    new Color(0, 0, 0, 80),
                    surfaceColor,
                    surfaceDimColor,
                    lowColor,
                    containerColor,
                    highColor,
                    highestColor,
                    outlineColor,
                    color(primary, 255),
                    color(onPrimary, 255),
                    color(primaryContainer, 236),
                    color(onPrimaryContainer, 255),
                    color(secondary, 255),
                    color(onSecondary, 255),
                    color(secondaryContainer, 236),
                    color(onSecondaryContainer, 255),
                    color(tertiary, 255),
                    color(onTertiary, 255),
                    color(tertiaryContainer, 236),
                    color(onTertiaryContainer, 255),
                    color("#313033", 255),
                    color("#F4EFF4", 255),
                    textPrimaryColor,
                    textSecondaryColor,
                    textMutedColor,
                    color("#BA1A1A", 255)
            );
        }

        private static Color color(String hex, int alpha) {
            Color base = Color.decode(hex);
            return new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha);
        }
    }

}
