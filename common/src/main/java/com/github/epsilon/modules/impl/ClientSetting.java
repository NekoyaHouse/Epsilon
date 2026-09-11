package com.github.epsilon.modules.impl;

import com.github.epsilon.assets.i18n.EpsilonLanguage;
import com.github.epsilon.assets.i18n.EpsilonLanguageManager;
import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.graphics.text.ttf.TtfFontLoader;
import com.github.epsilon.gui.dropdown.DropdownScreen;
import com.github.epsilon.gui.hudeditor.HudEditorScreen;
import com.github.epsilon.gui.panel.PanelScreen;
import com.github.epsilon.gui.screen.ConfirmDialogScreen;
import com.github.epsilon.gui.screen.MainMenuScreen;
import com.github.epsilon.gui.theme.MD3Theme;
import com.github.epsilon.managers.AssetManager;
import com.github.epsilon.managers.NotificationManager;
import com.github.epsilon.managers.TranslationManager;
import com.github.epsilon.managers.rotation.RotationManager;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.SettingGroup;
import com.github.epsilon.settings.impl.*;
import com.github.epsilon.utils.client.PlatformRequirement;
import com.mojang.blaze3d.platform.IconSet;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ClientSetting extends Module {

    public static final ClientSetting INSTANCE = new ClientSetting();

    private ClientSetting() {
        super("Client Setting", null);
    }

    public enum Teams {
        None,
        Color,
        Scoreboard
    }

    public enum GuiMode {
        Dropdown,
        Panel
    }

    public enum MainMenuStyle {
        Columbina,
        Classic
    }

    public enum ModuleSort {
        Name,
        EnabledFirst
    }

    public enum ThemePreset {
        TonalSpot,
        Neutral,
        Vibrant,
        Expressive,
        Fidelity,
        Content,
        Rainbow,
        FruitSalad,
        Monochrome
    }

    public enum ThemeMode {
        Dark,
        Light
    }

    public enum IconMode {
        Vanilla,
        Minecraft_1_8_9,
        Epsilon,
        Endfield
    }

    public enum TitleMode {
        Vanilla,
        Minecraft_1_8_9,
        Epsilon,
        Endfield
    }

    public enum HideMode {
        None,
        Hide,
        Vanilla
    }

    public enum FontMode {
        Default,
        Custom
    }

    private final SettingGroup sgGeneral = settingGroup("General");
    private final SettingGroup sgTeams = settingGroup("Teams");
    private final SettingGroup sgAntiCheat = settingGroup("Anti Cheat");
    private final SettingGroup sgAppearance = settingGroup("Appearance");
    private final SettingGroup sgReisa = settingGroup("Uzawa Reisa");
    private final SettingGroup sgNotification = settingGroup("Notification");
    private final SettingGroup sgResources = settingGroup("Resources");

    @SuppressWarnings("unused")
    private final ButtonSetting openHUDEditor = buttonSetting("Open HUD Editor", () -> mc.gui.setScreen(HudEditorScreen.INSTANCE));

    // General
    public final KeybindSetting guiKeybind = keybindSetting("Gui Keybind", GLFW.GLFW_KEY_RIGHT_SHIFT).group(sgGeneral);

    public final EnumSetting<GuiMode> guiMode = enumSetting("Gui Mode", GuiMode.Dropdown, _ -> mc.gui.setScreen(switch (ClientSetting.INSTANCE.guiMode.getValue()) {
        case Panel -> PanelScreen.INSTANCE;
        case Dropdown -> DropdownScreen.INSTANCE;
    })).group(sgGeneral);

    public final EnumSetting<ModuleSort> moduleSort = enumSetting("Module Sort", ModuleSort.Name).group(sgGeneral);

    public final EnumSetting<EpsilonLanguage> language = enumSetting("Language", EpsilonLanguage.English, EpsilonLanguageManager.INSTANCE::selectLanguage).group(sgGeneral);

    public final StringSetting customLanguage = stringSetting("Custom Language", "", () -> language.is(EpsilonLanguage.Custom), _ -> EpsilonLanguageManager.INSTANCE.refreshCustomLanguage())
            .group(sgGeneral)
            .applyWhenRelease();

    private final DoubleSetting renderScale = doubleSetting("Render Scale", 2.0, 1.0, 6.0, 0.5)
            .group(sgGeneral)
            .applyWhenRelease();

    public final BoolSetting i18nFallback = boolSetting("I18n Fallback", true, _ -> TranslationManager.INSTANCE.refresh()).group(sgGeneral);

    public final BoolSetting fontAntiAliasing = boolSetting("Font Anti Aliasing", true).group(sgGeneral);

    public final EnumSetting<FontMode> font = enumSetting("Font", FontMode.Default).group(sgGeneral);

    public final StringSetting customFont = stringSetting("Custom Font", "", () -> font.is(FontMode.Custom)).group(sgGeneral).applyWhenRelease();

    public final IntSetting fontGlyphsPerFrame = intSetting("Font Glyphs Per Frame", 8, 1, 64, 1, this::applyFontGlyphUploadBudget).group(sgGeneral);

    public final BoolSetting replaceMinecraftFont = boolSetting("Replace Minecraft Font", true).group(sgGeneral);

    public final BoolSetting closeOnOutside = boolSetting("Close Gui On Outside", false, () -> guiMode.is(GuiMode.Panel)).group(sgGeneral);

    public final BoolSetting dropdownHints = boolSetting("Dropdown Hints", true, () -> guiMode.is(GuiMode.Dropdown)).group(sgGeneral);

    // Teams
    public final EnumSetting<Teams> teams = enumSetting("Teams", Teams.None).group(sgTeams);

    // Anti Cheat
    public final EnumSetting<RotationManager.RotationMode> rotationMode = enumSetting("Rotation Mode", RotationManager.RotationMode.SILENT, RotationManager::switchRotationManager).group(sgAntiCheat);

    public final BoolSetting modifyCrosshair = boolSetting("Modify Crosshair", true).group(sgAntiCheat);

    public final EnumSetting<HideMode> hideMode = enumSetting("Hide Mode", HideMode.None).group(sgAntiCheat);

    // Appearance
    public final EnumSetting<ThemeMode> themeMode = enumSetting("Theme Mode", ThemeMode.Dark, _ -> MD3Theme.syncFromSettings()).group(sgAppearance);

    public final EnumSetting<ThemePreset> themePreset = enumSetting("Theme Preset", ThemePreset.TonalSpot, _ -> MD3Theme.syncFromSettings()).group(sgAppearance);

    public final EnumSetting<IconMode> customIcon = enumSetting("Custom Icon", IconMode.Epsilon, _ -> {
        try {
            mc.getWindow().setIcon(mc.getVanillaPackResources(), SharedConstants.getCurrentVersion().stable() ? IconSet.RELEASE : IconSet.SNAPSHOT);
        } catch (IOException ignored) {
        }
    }).group(sgAppearance);

    public final EnumSetting<TitleMode> customTitle = enumSetting("Custom Title", TitleMode.Epsilon, _ -> mc.updateTitle()).group(sgAppearance);

    public final BoolSetting useMainMenu = boolSetting("Use MainMenu", true).group(sgAppearance);

    public final EnumSetting<MainMenuStyle> mainMenuStyle = enumSetting("MainMenu Style", MainMenuStyle.Columbina, useMainMenu::getValue)
            .restrictMode(MainMenuStyle.Columbina, PlatformRequirement.WINDOWS_X64)
            .group(sgAppearance);

    public final EnumSetting<MainMenuScreen.Background> mainMenuBackground = enumSetting(
            "MainMenu Background",
            MainMenuScreen.Background.PLANET,
            () -> useMainMenu.getValue() && mainMenuStyle.is(MainMenuStyle.Classic)
    ).group(sgAppearance);

    public final BoolSetting showWelcomeScreen = boolSetting("Show Welcome Screen", true).rootSetting().group(sgAppearance);

    // 宇泽玲纱
    public final BoolSetting showReisaInDropdown = boolSetting("Show Reisa In Dropdown", true).group(sgReisa);

    public final BoolSetting showReisaOnStartup = boolSetting("Show Reisa On Startup", true).group(sgReisa);

    public final BoolSetting showReisaOnShutdown = boolSetting("Show Reisa On Shutdown", true).group(sgReisa);

    public final DoubleSetting reisaVolume = doubleSetting("Reisa Volume", 0.15, 0.0, 0.5, 0.05).group(sgReisa);

    // Resources
    public final StringSetting resourceBaseUrl = stringSetting("Resource Base URL", AssetManager.DEFAULT_RESOURCE_BASE_URL).group(sgResources);

    public final StringSetting ffmpegDownloadUrl = stringSetting("FFmpeg Download URL", AssetManager.DEFAULT_FFMPEG_URL).group(sgResources);

    @SuppressWarnings("unused")
    private final ButtonSetting downloadAssets = buttonSetting("Download Assets", () -> AssetManager.INSTANCE.openDownloadScreen()).group(sgResources);

    @SuppressWarnings("unused")
    private final ButtonSetting clearAssetCache = buttonSetting("Clear Asset Cache", () -> {
        Screen parent = mc.gui.screen();
        List<String> lines = List.of(
                EpsilonTranslations.Resources.CLEAR_CONFIRM_MESSAGE.getTranslatedName(),
                AssetManager.INSTANCE.rootDirectory().toString()
        );
        mc.gui.setScreen(new ConfirmDialogScreen(parent,
                EpsilonTranslations.Resources.CLEAR_CONFIRM_TITLE.getTranslatedName(),
                lines,
                EpsilonTranslations.Resources.CLEAR_CONFIRM_YES.getTranslatedName(),
                EpsilonTranslations.Resources.CLEAR_CONFIRM_NO.getTranslatedName(),
                () -> {
                    AssetManager.INSTANCE.clearCache();
                    NotificationManager.INSTANCE.success(
                            EpsilonTranslations.Resources.CLEAR_SUCCESS_TITLE.getTranslatedName(),
                            EpsilonTranslations.Resources.CLEAR_SUCCESS_MESSAGE.getTranslatedName());
                }));
    }).group(sgResources);

    @SuppressWarnings("unused")
    private final ButtonSetting openAssetFolder = buttonSetting("Open Asset Folder", () -> {
        try {
            Path directory = AssetManager.INSTANCE.rootDirectory();
            Files.createDirectories(directory);
            Util.getPlatform().openPath(directory);
        } catch (IOException e) {
            NotificationManager.INSTANCE.error(
                    EpsilonTranslations.Resources.OPEN_FOLDER_FAILED.getTranslatedName(),
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }).group(sgResources);

    // Notification
    public final BoolSetting soundNotify = boolSetting("Sound Notify", true).group(sgNotification);

    public final BoolSetting chatNotify = boolSetting("Chat Notify", true).group(sgNotification);

    public final BoolSetting animatedChatPrefix = boolSetting("Animated Chat Prefix", true).group(sgNotification);

    public final ColorSetting chatPrefixColorStart = colorSetting("Chat Prefix Color Start", new Color(255, 175, 210), animatedChatPrefix::getValue).group(sgNotification);

    public final ColorSetting chatPrefixColorEnd = colorSetting("Chat Prefix Color End", new Color(150, 220, 255), animatedChatPrefix::getValue).group(sgNotification);

    public final DoubleSetting chatPrefixGradientSpeed = doubleSetting("Chat Prefix Gradient Speed", 0.5, 0.1, 1, 0.1, animatedChatPrefix::getValue).group(sgNotification);

    public double getScale() {
        return renderScale.getValue();
    }

    public void syncFontGlyphUploadBudget() {
        applyFontGlyphUploadBudget(fontGlyphsPerFrame.getValue());
    }

    private void applyFontGlyphUploadBudget(int maxGlyphsPerFrame) {
        int budget = Math.max(1, maxGlyphsPerFrame);
        TtfFontLoader.setMaxGlyphUploadsPerFrame(budget);
    }

}
