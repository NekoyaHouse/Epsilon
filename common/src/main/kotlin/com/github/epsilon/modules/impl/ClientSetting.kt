package com.github.epsilon.modules.impl

import com.github.epsilon.assets.ffmpeg.FFmpegNativePlatform
import com.github.epsilon.assets.i18n.EpsilonLanguage
import com.github.epsilon.assets.i18n.EpsilonLanguageManager
import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.graphics.text.ttf.TtfFontLoader
import com.github.epsilon.gui.dropdown.DropdownScreen
import com.github.epsilon.gui.hudeditor.HudEditorScreen
import com.github.epsilon.gui.panel.PanelScreen
import com.github.epsilon.gui.screen.ConfirmDialogScreen
import com.github.epsilon.gui.screen.MainMenuScreen
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.gui.troll.TrollGuiScreen
import com.github.epsilon.managers.AssetManager
import com.github.epsilon.managers.NotificationManager
import com.github.epsilon.managers.TranslationManager
import com.github.epsilon.managers.rotation.RotationManager
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.SettingGroup
import com.github.epsilon.settings.applyWhenRelease
import com.github.epsilon.settings.group
import com.github.epsilon.settings.impl.EnumSetting
import com.github.epsilon.settings.globalSetting
import com.github.epsilon.utils.client.PlatformRequirement
import com.mojang.blaze3d.Blaze3D
import com.mojang.blaze3d.platform.IconSet
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.SharedConstants
import net.minecraft.client.gui.screens.Screen
import java.awt.Color
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

object ClientSetting : Module("Client Setting", null) {

    enum class Teams {
        None,
        Color,
        Scoreboard,
    }

    enum class GuiMode {
        Dropdown,
        Panel,
        TrollHack,
    }

    enum class MainMenuStyle {
        Columbina,
        Classic,
    }

    enum class ModuleSort {
        Name,
        EnabledFirst,
    }

    enum class ThemePreset {
        TonalSpot,
        Neutral,
        Vibrant,
        Expressive,
        Fidelity,
        Content,
        Rainbow,
        FruitSalad,
        Monochrome,
    }

    enum class ThemeMode {
        Dark,
        Light,
    }

    enum class IconMode {
        Vanilla,
        Minecraft_1_8_9,
        Epsilon,
        Sakura,
        Endfield,
    }

    enum class TitleMode {
        Vanilla,
        Minecraft_1_8_9,
        Epsilon,
        Sakura,
        Endfield,
    }

    enum class HideMode {
        None,
        Hide,
        Vanilla,
    }

    enum class FontMode {
        Default,
        Custom,
    }

    private val sgGeneral: SettingGroup = settingGroup("General")
    private val sgTeams: SettingGroup = settingGroup("Teams")
    private val sgAntiCheat: SettingGroup = settingGroup("Anti Cheat")
    private val sgAppearance: SettingGroup = settingGroup("Appearance")
    private val sgReisa: SettingGroup = sgAppearance.child("Uzawa Reisa")
    private val sgNotification: SettingGroup = settingGroup("Notification")
    private val sgResources: SettingGroup = settingGroup("Resources")

    @Suppress("unused")
    private val openHUDEditor by buttonSetting(
        "Open HUD Editor",
        Runnable { mc.gui.setScreen(HudEditorScreen) }
    )

    // General
    val guiKeybind by keybindSetting("Gui Keybind", InputConstants.KEY_RCONTROL)
        .group(sgGeneral)

    val guiMode: EnumSetting<GuiMode> = enumSetting(
        "Gui Mode",
        GuiMode.Dropdown,
        onChanged = { _ -> mc.gui.setScreen(getGuiScreen()) }
    ).group(sgGeneral)

    val moduleSort: EnumSetting<ModuleSort> = enumSetting("Module Sort", ModuleSort.Name)
        .group(sgGeneral)

    val language: EnumSetting<EpsilonLanguage> = enumSetting(
        "Language",
        EpsilonLanguage.English,
        onChanged = { EpsilonLanguageManager.selectLanguage(it) }
    ).group(sgGeneral)

    val customLanguage by stringSetting(
        "Custom Language",
        "", { language.`is`(EpsilonLanguage.Custom) },
        onChanged = { EpsilonLanguageManager.refreshCustomLanguage() }
    ).group(sgGeneral).applyWhenRelease()

    private val renderScale by doubleSetting("Render Scale", 2.0, 1.0..6.0, 0.5)
        .group(sgGeneral)
        .applyWhenRelease()

    val i18nFallback by boolSetting(
        "I18n Fallback",
        true,
        onChanged = { TranslationManager.refresh() }
    ).group(sgGeneral)

    val fontAntiAliasing by boolSetting("Font Anti Aliasing", true).group(sgGeneral)

    val font: EnumSetting<FontMode> = enumSetting("Font", FontMode.Default).group(sgGeneral)

    val customFont by stringSetting(
        "Custom Font",
        "",
        { font.`is`(FontMode.Custom) }
    ).group(sgGeneral).applyWhenRelease()

    val fontGlyphsPerFrame by intSetting(
        "Font Glyphs Per Frame",
        8,
        1..64,
        1,
        onChanged = { applyFontGlyphUploadBudget(it) }
    ).group(sgGeneral)

    val replaceMinecraftFont by boolSetting("Replace Minecraft Font", true).group(sgGeneral)

    val closeOnOutside by boolSetting(
        "Close Gui On Outside",
        false,
        { guiMode.`is`(GuiMode.Panel) }
    ).group(sgGeneral)

    val dropdownHints by boolSetting(
        "Dropdown Hints",
        true,
        { guiMode.`is`(GuiMode.Dropdown) }
    ).group(sgGeneral)

    // Teams
    val teams: EnumSetting<Teams> = enumSetting("Teams", Teams.None).group(sgTeams)

    // Anti Cheat
    val rotationMode: EnumSetting<RotationManager.RotationMode> = enumSetting(
        "Rotation Mode",
        RotationManager.RotationMode.SILENT,
        onChanged = { RotationManager.switchRotationManager(it) }
    ).group(sgAntiCheat)

    val modifyCrosshair by boolSetting("Modify Crosshair", true).group(sgAntiCheat)

    val hideMode: EnumSetting<HideMode> = enumSetting("Hide Mode", HideMode.None).group(sgAntiCheat)

    // Appearance
    val themeMode: EnumSetting<ThemeMode> = enumSetting(
        "Theme Mode",
        ThemeMode.Dark,
        onChanged = { MD3Theme.syncFromSettings() }
    ).group(sgAppearance)

    val themePreset: EnumSetting<ThemePreset> = enumSetting(
        "Theme Preset",
        ThemePreset.TonalSpot,
        onChanged = { MD3Theme.syncFromSettings() }
    ).group(sgAppearance)

    val customIcon: EnumSetting<IconMode> = enumSetting(
        "Custom Icon",
        IconMode.Epsilon,
        onChanged = {
            try {
                mc.window.setIcon(
                    mc.vanillaPackResources.fullResources(),
                    if (SharedConstants.getCurrentVersion().stable()) IconSet.RELEASE else IconSet.SNAPSHOT
                )
            } catch (ignored: IOException) {
            }
        }
    ).group(sgAppearance)

    val customTitle: EnumSetting<TitleMode> = enumSetting(
        "Custom Title",
        TitleMode.Epsilon,
        onChanged = { mc.updateTitle() }
    ).group(sgAppearance)

    val useMainMenu by boolSetting("Use MainMenu", true).group(sgAppearance)

    val mainMenuStyle: EnumSetting<MainMenuStyle> = enumSetting(
        "MainMenu Style",
        MainMenuStyle.Columbina, { useMainMenu },
        onChanged = { style ->
            if (style == MainMenuStyle.Columbina) {
                // 重新启用时允许下次进入主菜单再次提示下载缺失资源
                AssetManager.clearDeclined(
                    listOf(
                        AssetManager.Asset.VIDEO,
                        AssetManager.Asset.LIGHT_TRAILS,
                        AssetManager.Asset.FFMPEG
                    )
                )
            }
        }
    )
        .apply { restrictMode(MainMenuStyle.Columbina, PlatformRequirement.WINDOWS_X64_OR_MACOS_ARM64) }
        .group(sgAppearance)

    val mainMenuBackground: EnumSetting<MainMenuScreen.Background> = enumSetting(
        "MainMenu Background",
        MainMenuScreen.Background.PLANET,
        { useMainMenu && mainMenuStyle.`is`(MainMenuStyle.Classic) }
    ).group(sgAppearance)

    val showWelcomeScreen by boolSetting("Show Welcome Screen", true)
        .globalSetting()
        .group(sgAppearance)

    // 宇泽玲纱
    val showReisaInDropdown by boolSetting("Show Reisa In Dropdown", true).group(sgReisa)

    val showReisaOnStartup by boolSetting("Show Reisa On Startup", true).group(sgReisa)

    val showReisaOnShutdown by boolSetting("Show Reisa On Shutdown", true).group(sgReisa)

    val reisaVolume by doubleSetting("Reisa Volume", 0.15, 0.0..0.5, 0.05).group(sgReisa)

    // Resources
    val resourceBaseUrl by stringSetting(
        "Resource Base URL",
        AssetManager.DEFAULT_RESOURCE_BASE_URL
    ).group(sgResources)

    var ffmpegDownloadUrl by stringSetting(
        "FFmpeg Download URL",
        FFmpegNativePlatform.currentOrDefault().defaultUrl()
    ).group(sgResources)

    @Suppress("unused")
    private val downloadAssets by buttonSetting(
        "Download Assets",
        Runnable { AssetManager.openDownloadScreen() }
    ).group(sgResources)

    @Suppress("unused")
    private val clearAssetCache by buttonSetting(
        "Clear Asset Cache",
        Runnable {
            val parent = mc.gui.screen()
            val lines = listOf(
                EpsilonTranslations.Resources.CLEAR_CONFIRM_MESSAGE.getTranslatedName(),
                AssetManager.rootDirectory().toString()
            )
            mc.gui.setScreen(
                ConfirmDialogScreen(
                    parent,
                    EpsilonTranslations.Resources.CLEAR_CONFIRM_TITLE.getTranslatedName(),
                    lines,
                    EpsilonTranslations.Resources.CLEAR_CONFIRM_YES.getTranslatedName(),
                    EpsilonTranslations.Resources.CLEAR_CONFIRM_NO.getTranslatedName(),
                    Runnable {
                        AssetManager.clearCache()
                        NotificationManager.success(
                            EpsilonTranslations.Resources.CLEAR_SUCCESS_TITLE.getTranslatedName(),
                            EpsilonTranslations.Resources.CLEAR_SUCCESS_MESSAGE.getTranslatedName()
                        )
                    }
                )
            )
        }
    ).group(sgResources)

    @Suppress("unused")
    private val openAssetFolder by buttonSetting(
        "Open Asset Folder",
        Runnable {
            try {
                val directory: Path = AssetManager.rootDirectory()
                Files.createDirectories(directory)
                Blaze3D.openPath(directory)
            } catch (e: IOException) {
                NotificationManager.error(
                    EpsilonTranslations.Resources.OPEN_FOLDER_FAILED.getTranslatedName(),
                    e.message ?: e.javaClass.simpleName
                )
            }
        }
    ).group(sgResources)

    // Notification
    val soundNotify by boolSetting("Sound Notify", true).group(sgNotification)

    val chatNotify by boolSetting("Chat Notify", true).group(sgNotification)

    val animatedChatPrefix by boolSetting("Animated Chat Prefix", true).group(sgNotification)

    val chatPrefixColorStart by colorSetting(
        "Chat Prefix Color Start",
        Color(255, 175, 210),
        true,
        { animatedChatPrefix }
    ).group(sgNotification)

    val chatPrefixColorEnd by colorSetting(
        "Chat Prefix Color End",
        Color(150, 220, 255),
        true,
        { animatedChatPrefix }
    ).group(sgNotification)

    val chatPrefixGradientSpeed by doubleSetting(
        "Chat Prefix Gradient Speed",
        0.5,
        0.1..1.0,
        0.1,
        { animatedChatPrefix }
    ).group(sgNotification)

    fun getGuiScreen(): Screen {
        return when (guiMode.value) {
            GuiMode.Dropdown -> DropdownScreen.INSTANCE
            GuiMode.Panel -> PanelScreen
            GuiMode.TrollHack -> TrollGuiScreen.INSTANCE
        }
    }

    fun getScale(): Double {
        return renderScale
    }

    fun syncFontGlyphUploadBudget() {
        applyFontGlyphUploadBudget(fontGlyphsPerFrame)
    }

    private fun applyFontGlyphUploadBudget(maxGlyphsPerFrame: Int) {
        val budget = maxOf(1, maxGlyphsPerFrame)
        TtfFontLoader.setMaxGlyphUploadsPerFrame(budget)
    }

}
