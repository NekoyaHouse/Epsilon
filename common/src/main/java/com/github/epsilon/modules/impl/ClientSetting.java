package com.github.epsilon.modules.impl;

import com.github.epsilon.assets.i18n.EpsilonLanguage;
import com.github.epsilon.assets.i18n.EpsilonLanguageManager;
import com.github.epsilon.graphics.text.ttf.TtfFontLoader;
import com.github.epsilon.gui.dropdown.DropdownScreen;
import com.github.epsilon.gui.hudeditor.HudEditorScreen;
import com.github.epsilon.gui.panel.PanelScreen;
import com.github.epsilon.gui.screen.MainMenuScreen;
import com.github.epsilon.gui.theme.MD3Theme;
import com.github.epsilon.holders.TextureCacheHolder;
import com.github.epsilon.holders.TranslateHolder;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.managers.impl.rotations.RotationManager;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.SettingGroup;
import com.github.epsilon.settings.impl.*;
import com.mojang.blaze3d.platform.IconSet;
import net.minecraft.SharedConstants;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.io.IOException;

public class ClientSetting extends Module {

    public static final ClientSetting INSTANCE = new ClientSetting();

    private ClientSetting() {
        super("Client Setting", null);
    }

    public enum GuiMode {
        Dropdown,
        Panel
    }

    public enum ModuleSort {
        Name,
        EnabledFirst,
        Addon
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
        Epsilon
    }

    public enum TitleMode {
        Vanilla,
        Minecraft_1_8_9,
        Epsilon
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
    private final SettingGroup sgAntiCheat = settingGroup("Anti Cheat");
    private final SettingGroup sgAppearance = settingGroup("Appearance");
    private final SettingGroup sgReisa = settingGroup("Uzawa Reisa");
    private final SettingGroup sgNotification = settingGroup("Notification");

    @SuppressWarnings("unused")
    private final ButtonSetting openHUDEditor = buttonSetting("Open HUD Editor", () -> mc.setScreen(HudEditorScreen.INSTANCE));

    // General
    public final KeybindSetting guiKeybind = keybindSetting("Gui Keybind", GLFW.GLFW_KEY_RIGHT_SHIFT).group(sgGeneral);

    public final EnumSetting<GuiMode> guiMode = enumSetting("Gui Mode", GuiMode.Dropdown, _ -> mc.setScreen(switch (ClientSetting.INSTANCE.guiMode.getValue()) {
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

    public final BoolSetting i18nFallback = boolSetting("I18n Fallback", true, _ -> {
        TranslateHolder.INSTANCE.refresh();
        TextureCacheHolder.INSTANCE.clearCache();
    }).group(sgGeneral);

    public final BoolSetting fontAntiAliasing = boolSetting("Font Anti Aliasing", true).group(sgGeneral);

    public final EnumSetting<FontMode> font = enumSetting("Font", FontMode.Default).group(sgGeneral);

    public final StringSetting customFont = stringSetting("Custom Font", "", () -> font.is(FontMode.Custom))
            .group(sgGeneral)
            .applyWhenRelease();

    public final IntSetting fontGlyphsPerFrame = intSetting("Font Glyphs Per Frame", 8, 1, 64, 1, this::applyFontGlyphUploadBudget).group(sgGeneral);

    public final BoolSetting replaceMinecraftFont = boolSetting("Replace Minecraft Font", true).group(sgGeneral);

    public final BoolSetting closeOnOutside = boolSetting("Close Gui On Outside", false, () -> guiMode.is(GuiMode.Panel)).group(sgGeneral);

    public final BoolSetting dropdownHints = boolSetting("Dropdown Hints", true, () -> guiMode.is(GuiMode.Dropdown)).group(sgGeneral);

    // Anti Cheat
    public final EnumSetting<RotationManager.RotationMode> rotationMode =
            enumSetting("Rotation Mode", RotationManager.RotationMode.SILENT, mode -> {
                if (Managers.ROTATION != null) {
                    Managers.switchRotationManager(mode);
                }
            }).group(sgAntiCheat);

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

    public final EnumSetting<MainMenuScreen.Background> mainMenuBackground = enumSetting("MainMenu Background", MainMenuScreen.Background.PLANET, useMainMenu::getValue).group(sgAppearance);

    public final BoolSetting showWelcomeScreen = boolSetting("Show Welcome Screen", true).rootSetting().group(sgAppearance);

    // 宇泽玲纱
    public final BoolSetting showReisaInDropdown = boolSetting("Show Reisa In Dropdown", true).group(sgReisa);

    public final BoolSetting showReisaOnStartup = boolSetting("Show Reisa On Startup", true).group(sgReisa);

    public final BoolSetting showReisaOnShutdown = boolSetting("Show Reisa On Shutdown", true).group(sgReisa);

    public final DoubleSetting reisaVolume = doubleSetting("Reisa Volume", 1.0, 0.0, 1.0, 0.05).group(sgReisa);

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

    public int getFontGlyphsPerFrame() {
        return fontGlyphsPerFrame.getValue();
    }

    public void syncFontGlyphUploadBudget() {
        applyFontGlyphUploadBudget(fontGlyphsPerFrame.getValue());
    }

    private void applyFontGlyphUploadBudget(int maxGlyphsPerFrame) {
        int budget = Math.max(1, maxGlyphsPerFrame);
        TtfFontLoader.setMaxGlyphUploadsPerFrame(budget);
    }

}
