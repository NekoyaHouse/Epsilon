package com.github.epsilon.gui.theme

import com.github.epsilon.modules.impl.ClientSetting
import net.minecraft.util.Mth
import java.awt.Color

object MD3Theme {

    var SHADOW: Color = Color(0, 0, 0, 96)

    var SURFACE: Color = Color(20, 18, 24, 238)
    var SURFACE_DIM: Color = Color(15, 13, 19, 232)
    var SURFACE_CONTAINER_LOW: Color = Color(29, 27, 32, 240)
    var SURFACE_CONTAINER: Color = Color(33, 31, 38, 244)
    var SURFACE_CONTAINER_HIGH: Color = Color(43, 41, 48, 248)
    var SURFACE_CONTAINER_HIGHEST: Color = Color(54, 52, 59, 252)

    var OUTLINE: Color = Color(147, 143, 153, 180)
    var OUTLINE_SOFT: Color = Color(147, 143, 153, 96)

    var PRIMARY: Color = Color(208, 188, 255)
    var ON_PRIMARY: Color = Color(56, 30, 114)
    var PRIMARY_CONTAINER: Color = Color(79, 55, 139, 236)
    var ON_PRIMARY_CONTAINER: Color = Color(234, 221, 255)

    var SECONDARY: Color = Color(204, 194, 220)
    var ON_SECONDARY: Color = Color(51, 45, 65)
    var SECONDARY_CONTAINER: Color = Color(74, 68, 88, 236)
    var ON_SECONDARY_CONTAINER: Color = Color(232, 222, 248)

    var TERTIARY: Color = Color(239, 184, 200)
    var ON_TERTIARY: Color = Color(73, 37, 50)
    var TERTIARY_CONTAINER: Color = Color(99, 59, 72, 236)
    var ON_TERTIARY_CONTAINER: Color = Color(255, 216, 228)
    var INVERSE_SURFACE: Color = Color(230, 224, 233)
    var INVERSE_ON_SURFACE: Color = Color(49, 48, 51)

    var TEXT_PRIMARY: Color = Color(230, 224, 233)
    var TEXT_SECONDARY: Color = Color(202, 196, 208)
    var TEXT_MUTED: Color = Color(147, 143, 153)
    var SUCCESS: Color = Color(204, 194, 220)
    var ERROR: Color = Color(242, 184, 181)

    private var appliedPreset: ClientSetting.ThemePreset? = null
    private var appliedMode: ClientSetting.ThemeMode? = null

    const val PANEL_RADIUS = 17
    const val SECTION_RADIUS = 13
    const val CARD_RADIUS = 9
    const val CHIP_RADIUS = 999
    const val PANEL_SHADOW_BLUR = 24.0f
    const val PANEL_SHADOW_ALPHA = 96
    const val POPUP_SHADOW_BLUR = 14.0f
    const val POPUP_SHADOW_ALPHA = 112
    const val FLOATING_LABEL_SHADOW_BLUR = 12.0f
    const val FLOATING_LABEL_SHADOW_ALPHA = 96

    const val OUTER_PADDING = 5.0f
    const val SECTION_GAP = 3.0f
    const val INNER_PADDING = 5.0f
    const val ROW_GAP = 3.0f
    const val PANEL_TITLE_INSET = 6.0f
    const val PANEL_VIEWPORT_INSET = 3.0f
    const val ROW_CONTENT_INSET = 5.0f
    const val ROW_TRAILING_INSET = 5.0f
    const val RAIL_COLLAPSED_WIDTH = 42.0f
    const val RAIL_EXPANDED_WIDTH = 120.0f
    const val CONTROL_HEIGHT = 18.0f
    const val CONTROL_RADIUS = 7.0f
    const val COMPACT_CHIP_HEIGHT = 16.0f
    const val SWITCH_WIDTH = 26.0f
    const val SWITCH_HEIGHT = 16.0f
    const val SWITCH_HANDLE_SIZE_OFF = 8.0f
    const val SWITCH_HANDLE_SIZE_ON = 12.0f
    const val SWITCH_HANDLE_INSET_OFF = 4.0f
    const val SWITCH_HANDLE_INSET_ON = 2.0f
    const val SWITCH_STATE_LAYER_SIZE = 20.0f

    fun syncFromSettings() {
        val preset = ClientSetting.themePreset.value
        val mode = ClientSetting.themeMode.value
        if (preset == appliedPreset && mode == appliedMode) {
            return
        }
        val palette = forPreset(preset, mode)
        SHADOW = palette.shadow
        SURFACE = palette.surface
        SURFACE_DIM = palette.surfaceDim
        SURFACE_CONTAINER_LOW = palette.surfaceContainerLow
        SURFACE_CONTAINER = palette.surfaceContainer
        SURFACE_CONTAINER_HIGH = palette.surfaceContainerHigh
        SURFACE_CONTAINER_HIGHEST = palette.surfaceContainerHighest
        OUTLINE = palette.outline
        OUTLINE_SOFT = withAlpha(palette.outline, 96)
        PRIMARY = palette.primary
        ON_PRIMARY = palette.onPrimary
        PRIMARY_CONTAINER = palette.primaryContainer
        ON_PRIMARY_CONTAINER = palette.onPrimaryContainer
        SECONDARY = palette.secondary
        ON_SECONDARY = palette.onSecondary
        SECONDARY_CONTAINER = palette.secondaryContainer
        ON_SECONDARY_CONTAINER = palette.onSecondaryContainer
        TERTIARY = palette.tertiary
        ON_TERTIARY = palette.onTertiary
        TERTIARY_CONTAINER = palette.tertiaryContainer
        ON_TERTIARY_CONTAINER = palette.onTertiaryContainer
        INVERSE_SURFACE = palette.inverseSurface
        INVERSE_ON_SURFACE = palette.inverseOnSurface
        TEXT_PRIMARY = palette.textPrimary
        TEXT_SECONDARY = palette.textSecondary
        TEXT_MUTED = palette.textMuted
        SUCCESS = palette.secondary
        ERROR = palette.error
        appliedPreset = preset
        appliedMode = mode
    }

    fun withAlpha(color: Color, alpha: Int): Color {
        val clampedAlpha = Mth.clamp(alpha, 0, 255)
        return Color(color.getRed(), color.getGreen(), color.getBlue(), clampedAlpha)
    }

    fun lerp(start: Color, end: Color, delta: Float): Color {
        val t = Mth.clamp(delta, 0.0f, 1.0f)
        val r = (start.getRed() + (end.getRed() - start.getRed()) * t).toInt()
        val g = (start.getGreen() + (end.getGreen() - start.getGreen()) * t).toInt()
        val b = (start.getBlue() + (end.getBlue() - start.getBlue()) * t).toInt()
        val a = (start.getAlpha() + (end.getAlpha() - start.getAlpha()) * t).toInt()
        return Color(r, g, b, a)
    }

    fun isLightTheme(): Boolean {
        return ClientSetting.themeMode.`is`(ClientSetting.ThemeMode.Light)
    }

    fun stateLayer(color: Color, progress: Float, maxAlpha: Int): Color {
        return withAlpha(color, (Mth.clamp(progress, 0.0f, 1.0f) * Mth.clamp(maxAlpha, 0, 255)).toInt())
    }

    fun rowSurface(hoverProgress: Float): Color {
        return lerp(SURFACE_CONTAINER, SURFACE_CONTAINER_HIGH, hoverProgress)
    }

    fun filledFieldSurface(focused: Boolean, hoverProgress: Float): Color {
        if (focused) {
            val focusMix = if (isLightTheme()) 0.58f else 0.42f
            return lerp(SURFACE_CONTAINER_HIGH, PRIMARY_CONTAINER, focusMix)
        }
        val base = if (isLightTheme()) SURFACE_CONTAINER else SURFACE_CONTAINER_LOW
        return lerp(base, SURFACE_CONTAINER_HIGHEST, Mth.clamp(hoverProgress * 0.85f, 0.0f, 1.0f))
    }

    fun filledFieldContent(focused: Boolean): Color {
        return TEXT_PRIMARY
    }

    fun filledFieldCaret(focused: Boolean): Color {
        return if (focused) PRIMARY else TEXT_PRIMARY
    }

    fun filledFieldIndicator(focused: Boolean, hoverProgress: Float): Color {
        if (focused) {
            return PRIMARY
        }
        return lerp(
            withAlpha(OUTLINE, 96), withAlpha(TEXT_PRIMARY, 136),
            Mth.clamp(hoverProgress * 0.55f, 0.0f, 1.0f)
        )
    }

    fun segmentedControlSurface(): Color {
        return if (isLightTheme()) SURFACE else SURFACE_CONTAINER_HIGH
    }

    fun segmentedControlIndicator(): Color {
        return SECONDARY_CONTAINER
    }

    fun segmentedControlActiveLabel(): Color {
        return ON_SECONDARY_CONTAINER
    }

    fun segmentedControlInactiveLabel(): Color {
        return if (isLightTheme()) TEXT_SECONDARY else TEXT_MUTED
    }

    fun switchTrack(toggleProgress: Float): Color {
        return lerp(SURFACE_CONTAINER_HIGHEST, PRIMARY, toggleProgress)
    }

    fun switchKnob(toggleProgress: Float): Color {
        return lerp(OUTLINE, ON_PRIMARY, toggleProgress)
    }

    fun switchTrackOutline(toggleProgress: Float, hoverProgress: Float): Color {
        val inactive = 1.0f - Mth.clamp(toggleProgress, 0.0f, 1.0f)
        val hoverMix = Mth.clamp(hoverProgress * 0.35f, 0.0f, 1.0f)
        val base = lerp(OUTLINE, TEXT_PRIMARY, hoverMix)
        return withAlpha(base, (inactive * (if (isLightTheme()) 188 else 168)).toInt())
    }

    fun switchTrackOutlineWidth(toggleProgress: Float): Float {
        return 1.0f + (1.0f - Mth.clamp(toggleProgress, 0.0f, 1.0f)) * 0.1f
    }

    private fun forPreset(preset: ClientSetting.ThemePreset, mode: ClientSetting.ThemeMode): ThemePalette {
        return when (preset) {
            ClientSetting.ThemePreset.TonalSpot -> if (mode == ClientSetting.ThemeMode.Dark)
                paletteDark(
                    "#141218", "#1B1820", "#211F26", "#2B2930", "#35333B", "#D0BCFF", "#381E72",
                    "#4F378B", "#EADDFF", "#CCC2DC", "#332D41", "#4A4458", "#E8DEF8", "#EFB8C8",
                    "#492532", "#633B48", "#FFD8E4"
                )
            else
                paletteLight(
                    "#FFFBFE", "#F7F2FA", "#F3EDF7", "#ECE6F0", "#E6E0E9", "#6750A4", "#FFFFFF",
                    "#EADDFF", "#21005D", "#625B71", "#FFFFFF", "#E8DEF8", "#1D192B", "#7D5260",
                    "#FFFFFF", "#FFD8E4", "#31111D"
                )

            ClientSetting.ThemePreset.Neutral -> if (mode == ClientSetting.ThemeMode.Dark)
                paletteDark(
                    "#141314", "#1C1B1C", "#211F21", "#2C2A2C", "#363436", "#CFC3D9", "#362B3E",
                    "#4B4153", "#E9DDEC", "#CCC2CF", "#342F38", "#4B4450", "#E8DDEB", "#D8C2C7",
                    "#3C2B2F", "#544247", "#F4DCE1"
                )
            else
                paletteLight(
                    "#FEF7FF", "#F7EEF8", "#F1E8F2", "#EBE1EB", "#E4DBE5", "#6C4F75", "#FFFFFF",
                    "#F2DAFF", "#261430", "#665A69", "#FFFFFF", "#EBDDDF", "#201A21", "#81525D",
                    "#FFFFFF", "#FFD9E0", "#33111A"
                )

            ClientSetting.ThemePreset.Vibrant -> if (mode == ClientSetting.ThemeMode.Dark)
                paletteDark(
                    "#16111C", "#1D1725", "#241D2D", "#2F2639", "#3A3044", "#E3B7FF", "#4A1F63",
                    "#663282", "#F5D9FF", "#D7BEE4", "#3D2D48", "#564260", "#F2DAFF", "#FFB4AB",
                    "#690005", "#93000A", "#FFDAD6"
                )
            else
                paletteLight(
                    "#FFF7FD", "#F8EDF8", "#F3E7F4", "#EDE0EE", "#E6D9E7", "#7A2F9A", "#FFFFFF",
                    "#FFD7F6", "#320046", "#6A586F", "#FFFFFF", "#EEDCF4", "#231727", "#80535E",
                    "#FFFFFF", "#FFD9E0", "#33111A"
                )

            ClientSetting.ThemePreset.Expressive -> if (mode == ClientSetting.ThemeMode.Dark)
                paletteDark(
                    "#14141B", "#1C1C24", "#22222A", "#2D2D36", "#373740", "#FFB1C8", "#561D33",
                    "#73324B", "#FFD9E2", "#D9C2CB", "#3F2A33", "#574049", "#F4DDE6", "#C4D7FF",
                    "#1E3A6B", "#35528A", "#DBE1FF"
                )
            else
                paletteLight(
                    "#FFF8F8", "#F8EFEF", "#F3E8E8", "#EDE1E1", "#E7DADB", "#904A61", "#FFFFFF",
                    "#FFD9E2", "#3B071D", "#6F5862", "#FFFFFF", "#F7D9E3", "#29141D", "#48648F",
                    "#FFFFFF", "#DBE1FF", "#001D36"
                )

            ClientSetting.ThemePreset.Fidelity -> if (mode == ClientSetting.ThemeMode.Dark)
                paletteDark(
                    "#10141A", "#161B22", "#1C2128", "#252B33", "#2E353E", "#7CC6FF", "#00344F",
                    "#0E4A69", "#CEE5FF", "#B8CADB", "#203845", "#374E5B", "#D4E5F8", "#9AD0B8",
                    "#103826", "#28503B", "#B6EFD0"
                )
            else
                paletteLight(
                    "#F6FAFF", "#EEF3F9", "#E8EDF3", "#E1E8EF", "#DAE2EA", "#00658A", "#FFFFFF",
                    "#CDEFFD", "#001E2C", "#50606E", "#FFFFFF", "#D3E5F5", "#0C1D28", "#3C6651",
                    "#FFFFFF", "#BEECD1", "#072012"
                )

            ClientSetting.ThemePreset.Content -> if (mode == ClientSetting.ThemeMode.Dark)
                paletteDark(
                    "#11151A", "#181D23", "#1E232A", "#282E36", "#313841", "#91C9FF", "#11314B",
                    "#294964", "#D1E5FF", "#C1C9D6", "#28333D", "#3F4B56", "#DEE4F2", "#D7C29F",
                    "#41311A", "#59472E", "#F5DEB8"
                )
            else
                paletteLight(
                    "#F8FAFC", "#F0F3F7", "#E9EDF2", "#E2E7EC", "#DBE1E7", "#355F8D", "#FFFFFF",
                    "#D1E4FF", "#001D36", "#5A616C", "#FFFFFF", "#DEE3F2", "#171C25", "#735B3E",
                    "#FFFFFF", "#F5DEB8", "#291805"
                )

            ClientSetting.ThemePreset.Rainbow -> if (mode == ClientSetting.ThemeMode.Dark)
                paletteDark(
                    "#141318", "#1B1A20", "#211F26", "#2B2831", "#35323B", "#C8C1FF", "#2E2A67",
                    "#454287", "#E4DFFF", "#D7C2E6", "#3A3047", "#53485F", "#F2DBFF", "#FFB59D",
                    "#5E2F1C", "#7D4732", "#FFDCCF"
                )
            else
                paletteLight(
                    "#FCF8FF", "#F4EEF8", "#EEE8F3", "#E7E1EC", "#E0DAE6", "#5B5BD6", "#FFFFFF",
                    "#E0DFFF", "#191962", "#675A70", "#FFFFFF", "#ECDCF5", "#21182A", "#8A4F3A",
                    "#FFFFFF", "#FFDCCF", "#351100"
                )

            ClientSetting.ThemePreset.FruitSalad -> if (mode == ClientSetting.ThemeMode.Dark)
                paletteDark(
                    "#121415", "#181B1C", "#1E2122", "#282C2D", "#313637", "#8FD665", "#173807",
                    "#2D5218", "#C2F19A", "#C8D0C0", "#2F372B", "#475041", "#E4F0D9", "#FFB77B",
                    "#5A2E00", "#7D4300", "#FFDCC2"
                )
            else
                paletteLight(
                    "#FAFFF4", "#F2F8EB", "#EAF0E3", "#E3EAD9", "#DCE3D2", "#466A1F", "#FFFFFF",
                    "#C7F089", "#102000", "#5E6657", "#FFFFFF", "#E0E9D5", "#1B1F17", "#965100",
                    "#FFFFFF", "#FFDCC2", "#301400"
                )

            ClientSetting.ThemePreset.Monochrome -> if (mode == ClientSetting.ThemeMode.Dark)
                paletteDark(
                    "#121212", "#1A1A1A", "#202020", "#2A2A2A", "#333333", "#E6E1E5", "#1B1B1B",
                    "#383838", "#F3EEF2", "#D1CCD0", "#2C2C2C", "#434343", "#EFE9ED", "#CFC8CD",
                    "#2B2B2B", "#444444", "#F0E9EE"
                )
            else
                paletteLight(
                    "#FCFCFC", "#F3F3F3", "#ECECEC", "#E5E5E5", "#DEDEDE", "#5F5E61", "#FFFFFF",
                    "#E4E1E4", "#1C1B1E", "#605D62", "#FFFFFF", "#E5E1E6", "#1C1B1F", "#625D61",
                    "#FFFFFF", "#E7E0E5", "#201A1E"
                )
        }
    }

    private fun paletteDark(
        surface: String, low: String, container: String, high: String, highest: String,
        primary: String, onPrimary: String, primaryContainer: String, onPrimaryContainer: String,
        secondary: String, onSecondary: String, secondaryContainer: String, onSecondaryContainer: String,
        tertiary: String, onTertiary: String, tertiaryContainer: String, onTertiaryContainer: String,
    ): ThemePalette {
        val surfaceColor = color(surface, 238)
        val surfaceDimColor = color(low, 232)
        val lowColor = color(low, 240)
        val containerColor = color(container, 244)
        val highColor = color(high, 248)
        val highestColor = color(highest, 252)
        val outlineColor = color("#938F99", 180)
        val textPrimaryColor = color("#ECE6F0", 255)
        val textSecondaryColor = color("#CAC4D0", 255)
        val textMutedColor = color("#938F99", 255)
        return ThemePalette(
            Color(0, 0, 0, 96),
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
        )
    }

    private fun paletteLight(
        surface: String, low: String, container: String, high: String, highest: String,
        primary: String, onPrimary: String, primaryContainer: String, onPrimaryContainer: String,
        secondary: String, onSecondary: String, secondaryContainer: String, onSecondaryContainer: String,
        tertiary: String, onTertiary: String, tertiaryContainer: String, onTertiaryContainer: String,
    ): ThemePalette {
        val surfaceColor = color(surface, 242)
        val surfaceDimColor = color(low, 238)
        val lowColor = color(low, 242)
        val containerColor = color(container, 246)
        val highColor = color(high, 250)
        val highestColor = color(highest, 252)
        val outlineColor = color("#79747E", 180)
        val textPrimaryColor = color("#1C1B1F", 255)
        val textSecondaryColor = color("#49454F", 255)
        val textMutedColor = color("#79747E", 255)
        return ThemePalette(
            Color(0, 0, 0, 80),
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
        )
    }

    private fun color(hex: String, alpha: Int): Color {
        val base = Color.decode(hex)
        return Color(base.getRed(), base.getGreen(), base.getBlue(), alpha)
    }

    private data class ThemePalette(
        val shadow: Color,
        val surface: Color,
        val surfaceDim: Color,
        val surfaceContainerLow: Color,
        val surfaceContainer: Color,
        val surfaceContainerHigh: Color,
        val surfaceContainerHighest: Color,
        val outline: Color,
        val primary: Color,
        val onPrimary: Color,
        val primaryContainer: Color,
        val onPrimaryContainer: Color,
        val secondary: Color,
        val onSecondary: Color,
        val secondaryContainer: Color,
        val onSecondaryContainer: Color,
        val tertiary: Color,
        val onTertiary: Color,
        val tertiaryContainer: Color,
        val onTertiaryContainer: Color,
        val inverseSurface: Color,
        val inverseOnSurface: Color,
        val textPrimary: Color,
        val textSecondary: Color,
        val textMuted: Color,
        val error: Color,
    )

}
