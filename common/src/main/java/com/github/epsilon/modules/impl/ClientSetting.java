package com.github.epsilon.modules.impl;

import com.github.epsilon.assets.i18n.EpsilonLanguage;
import com.github.epsilon.assets.i18n.EpsilonLanguageManager;
import com.github.epsilon.graphics.text.ttf.TtfFontLoader;
import com.github.epsilon.gui.dropdown.DropdownScreen;
import com.github.epsilon.gui.hudeditor.HudEditorScreen;
import com.github.epsilon.gui.panel.PanelScreen;
import com.github.epsilon.gui.screen.MainMenuScreen;
import com.github.epsilon.gui.theme.MD3Theme;
import com.github.epsilon.managers.TranslationManager;
import com.github.epsilon.managers.rotation.RotationManager;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.SettingGroup;
import com.github.epsilon.settings.impl.*;
import com.mojang.blaze3d.platform.IconSet;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersionRange;
import me.sofurry.via.features.font.FontCacheReload;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.SharedConstants;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import net.raphimc.viabedrock.protocol.data.ProtocolConstants;
import net.raphimc.vialegacy.api.LegacyProtocolVersion;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.io.IOException;
import java.util.Objects;

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
        Columbia,
        Classic
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

    public enum ViaButtonOrientation {
        None,
        LeftTop,
        RightTop,
        LeftBottom,
        RightBottom
    }

    public enum ViaCreativeTabFilter {
        VanillaAndModded,
        VanillaOnly,
        Off
    }

    public enum ViaPacketErrorAction {
        Kick,
        CancelAndNotify,
        Cancel
    }

    public enum VersionedMode {
        Auto,
        Enabled,
        Disabled
    }

    public static final class VersionedBoolSetting extends EnumSetting<VersionedMode> {

        private final ProtocolVersionRange protocolRange;

        private VersionedBoolSetting(String name, VersionedMode defaultValue, ProtocolVersionRange protocolRange) {
            super(name, defaultValue, () -> true, null);
            this.protocolRange = protocolRange;
        }

        public boolean isEnabled() {
            return isEnabled(ProtocolTranslator.getTargetVersion());
        }

        public boolean isEnabled(ProtocolVersion version) {
            return switch (getValue()) {
                case Auto -> protocolRange.contains(version);
                case Enabled -> true;
                case Disabled -> false;
            };
        }

        public ProtocolVersionRange getProtocolRange() {
            return protocolRange;
        }

    }

    private final SettingGroup sgGeneral = settingGroup("General");
    private final SettingGroup sgTeams = settingGroup("Teams");
    private final SettingGroup sgAntiCheat = settingGroup("Anti Cheat");
    private final SettingGroup sgAppearance = settingGroup("Appearance");
    private final SettingGroup sgReisa = settingGroup("Uzawa Reisa");
    private final SettingGroup sgNotification = settingGroup("Notification");
    private final SettingGroup sgViaGeneral = settingGroup("Via General");
    private final SettingGroup sgViaBedrock = settingGroup("Via Bedrock");
    private final SettingGroup sgViaDebug = settingGroup("Via Debug");
    private final SettingGroup sgViaVisual = settingGroup("Via Visual");

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

    public final BoolSetting i18nFallback = boolSetting("I18n Fallback", true, _ -> {
        TranslationManager.INSTANCE.refresh();
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

    public final EnumSetting<MainMenuStyle> mainMenuStyle = enumSetting("MainMenu Style", MainMenuStyle.Columbia, useMainMenu::getValue).group(sgAppearance);

    public final EnumSetting<MainMenuScreen.Background> mainMenuBackground = enumSetting(
            "MainMenu Background",
            MainMenuScreen.Background.PLANET,
            () -> useMainMenu.getValue() && mainMenuStyle.is(MainMenuStyle.Classic)
    ).group(sgAppearance);

    public final BoolSetting showWelcomeScreen = boolSetting("Show Welcome Screen", true).rootSetting().group(sgAppearance);

    public final BoolSetting screenBackground = boolSetting("Screen Background", true).group(sgAppearance);

    // 宇泽玲纱
    public final BoolSetting showReisaInDropdown = boolSetting("Show Reisa In Dropdown", true).group(sgReisa);

    public final BoolSetting showReisaOnStartup = boolSetting("Show Reisa On Startup", true).group(sgReisa);

    public final BoolSetting showReisaOnShutdown = boolSetting("Show Reisa On Shutdown", true).group(sgReisa);

    public final DoubleSetting reisaVolume = doubleSetting("Reisa Volume", 0.15, 0.0, 0.5, 0.05).group(sgReisa);

    // Notification
    public final BoolSetting soundNotify = boolSetting("Sound Notify", true).group(sgNotification);

    public final BoolSetting chatNotify = boolSetting("Chat Notify", true).group(sgNotification);

    public final BoolSetting animatedChatPrefix = boolSetting("Animated Chat Prefix", true).group(sgNotification);

    public final ColorSetting chatPrefixColorStart = colorSetting("Chat Prefix Color Start", new Color(255, 175, 210), animatedChatPrefix::getValue).group(sgNotification);

    public final ColorSetting chatPrefixColorEnd = colorSetting("Chat Prefix Color End", new Color(150, 220, 255), animatedChatPrefix::getValue).group(sgNotification);

    public final DoubleSetting chatPrefixGradientSpeed = doubleSetting("Chat Prefix Gradient Speed", 0.5, 0.1, 1, 0.1, animatedChatPrefix::getValue).group(sgNotification);

    // Via General
    public final EnumSetting<ViaButtonOrientation> multiplayerScreenButtonOrientation = enumSetting("Multiplayer Screen Button Orientation", ViaButtonOrientation.RightTop).group(sgViaGeneral);

    public final EnumSetting<ViaButtonOrientation> addServerScreenButtonOrientation = enumSetting("Add Server Screen Button Orientation", ViaButtonOrientation.RightTop).group(sgViaGeneral);

    public final EnumSetting<ViaButtonOrientation> directConnectScreenButtonOrientation = enumSetting("Direct Connect Screen Button Orientation", ViaButtonOrientation.RightTop).group(sgViaGeneral);

    public final EnumSetting<ViaCreativeTabFilter> removeNotAvailableItemsFromCreativeTab = enumSetting("Remove Unavailable Items From Creative Tab", ViaCreativeTabFilter.VanillaAndModded).group(sgViaGeneral);

    public final BoolSetting saveSelectedProtocolVersion = boolSetting("Save Selected Protocol Version", true).group(sgViaGeneral);

    public final BoolSetting showClassicLoadingProgressInConnectScreen = boolSetting("Show Classic Loading Progress In Connect Screen", true).group(sgViaGeneral);

    public final BoolSetting showAdvertisedServerVersion = boolSetting("Show Advertised Server Version", true).group(sgViaGeneral);

    public final EnumSetting<ViaPacketErrorAction> ignorePacketTranslationErrors = enumSetting("Packet Translation Error Action", ViaPacketErrorAction.Kick).group(sgViaGeneral);

    public final BoolSetting loadSkinsAndSkullsInLegacyVersions = boolSetting("Load Skins And Skulls In Legacy Versions", true).group(sgViaGeneral);

    public final BoolSetting emulateInventoryActionsInAlphaVersions = boolSetting("Emulate Inventory Actions In Alpha Versions", true).group(sgViaGeneral);

    public final BoolSetting saveScrollPositionInSlotScreens = boolSetting("Save Scroll Position In Slot Screens", true).group(sgViaGeneral);

    public final BoolSetting experimentalBlockConnections = boolSetting("Experimental Block Connections", false).group(sgViaGeneral);

    // Via Bedrock
    public final BoolSetting replaceDefaultPort = boolSetting("Replace Bedrock Default Port", true).group(sgViaBedrock);

    public final BoolSetting experimentalFeatures = boolSetting("Bedrock Experimental Features", true).group(sgViaBedrock);

    // Java protocol authentication
    public final BoolSetting verifySessionForOnlineModeServers = boolSetting("Verify Session For Online Mode Servers", true).group(sgViaGeneral);

    // Via Debug
    public final BoolSetting queueConfigPackets = boolSetting("Queue Config Packets", true).group(sgViaDebug);

    public final BoolSetting printNetworkingErrorsToLogs = boolSetting("Print Networking Errors To Logs", true).group(sgViaDebug);

    public final BoolSetting ignoreFabricSyncErrors = boolSetting("Ignore Fabric Sync Errors", false).group(sgViaDebug);

    public final BoolSetting hideModernJigsawScreenFeatures = boolSetting("Hide Modern Jigsaw Screen Features", true).group(sgViaDebug);

    public final BoolSetting filterNonExistingGlyphs = boolSetting("Filter Non Existing Glyphs", true, _ -> FontCacheReload.reload()).group(sgViaDebug);

    public final VersionedBoolSetting removeServerDescriptionSanitize = versionedBoolSetting("Remove Server Description Sanitize", ProtocolVersionRange.andOlder(ProtocolVersion.v1_21_11), sgViaDebug);

    public final VersionedBoolSetting dontCreatePacketErrorCrashReports = versionedBoolSetting("Do Not Create Packet Error Crash Reports", ProtocolVersionRange.andOlder(ProtocolVersion.v1_20_3), sgViaDebug);

    public final VersionedBoolSetting disableSequencing = versionedBoolSetting("Disable Sequencing", ProtocolVersionRange.andOlder(ProtocolVersion.v1_18_2), sgViaDebug);

    public final VersionedBoolSetting alwaysTickClientPlayer = versionedBoolSetting("Always Tick Client Player", ProtocolVersionRange.andOlder(ProtocolVersion.v1_8).add(ProtocolVersionRange.andNewer(ProtocolVersion.v1_17)), sgViaDebug);

    public final VersionedBoolSetting executeInputsSynchronously = versionedBoolSetting("Execute Inputs Synchronously", ProtocolVersionRange.andOlder(ProtocolVersion.v1_12_2), sgViaDebug);

    public final VersionedBoolSetting legacyTabCompletions = versionedBoolSetting("Legacy Tab Completions", ProtocolVersionRange.andOlder(ProtocolVersion.v1_12_2), sgViaDebug);

    public final VersionedBoolSetting legacyPaneOutlines = versionedBoolSetting("Legacy Pane Outlines", ProtocolVersionRange.andOlder(ProtocolVersion.v1_12_2), sgViaDebug);

    public final VersionedBoolSetting emulateArmorHud = versionedBoolSetting("Emulate Armor HUD", ProtocolVersionRange.andOlder(ProtocolVersion.v1_8), sgViaDebug);

    public final VersionedBoolSetting hideModernCommandBlockScreenFeatures = versionedBoolSetting("Hide Modern Command Block Screen Features", ProtocolVersionRange.andOlder(ProtocolVersion.v1_8), sgViaDebug);

    public final VersionedBoolSetting legacyCropOutlines = versionedBoolSetting("Legacy Crop Outlines", ProtocolVersionRange.andOlder(ProtocolVersion.v1_8), sgViaDebug);

    public final VersionedBoolSetting serversidePlaceSounds = versionedBoolSetting("Serverside Block And Item Place Sounds", ProtocolVersionRange.andOlder(ProtocolVersion.v1_8), sgViaDebug);

    public final VersionedBoolSetting disableServerPinging = versionedBoolSetting("Disable Server Pinging", ProtocolVersionRange.andOlder(LegacyProtocolVersion.b1_7tob1_7_3), sgViaDebug);

    // Via Visual
    public final BoolSetting removeBubblePopSound = boolSetting("Remove Bubble Pop Sound", false).group(sgViaVisual);

    public final BoolSetting hideEmptyBubbleIcons = boolSetting("Hide Empty Bubble Icons", false).group(sgViaVisual);

    public final BoolSetting hideVillagerProfession = boolSetting("Hide Villager Profession", false).group(sgViaVisual);

    public final VersionedBoolSetting hideDownloadTerrainScreenTransitionEffects = versionedBoolSetting("Hide Download Terrain Screen Transition Effects", VersionedMode.Disabled, ProtocolVersionRange.andOlder(ProtocolVersion.v1_20_5), sgViaVisual);

    public final VersionedBoolSetting lockBlockingArmRotation = versionedBoolSetting("Lock Blocking Arm Rotation", ProtocolVersionRange.andOlder(ProtocolVersion.v1_20_2), sgViaVisual);

    public final VersionedBoolSetting changeBodyRotationInterpolation = versionedBoolSetting("Change Body Rotation Interpolation", ProtocolVersionRange.andOlder(ProtocolVersion.v1_19_3), sgViaVisual);

    public final VersionedBoolSetting potionEnchantmentGlint = versionedBoolSetting("Potion Enchantment Glint", ProtocolVersionRange.andOlder(ProtocolVersion.v1_19_3), sgViaVisual);

    public final VersionedBoolSetting disableSecureChatWarning = versionedBoolSetting("Disable Secure Chat Warning", ProtocolVersionRange.andOlder(ProtocolVersion.v1_19), sgViaVisual);

    public final VersionedBoolSetting hideSignatureIndicator = versionedBoolSetting("Hide Signature Indicator", ProtocolVersionRange.andOlder(ProtocolVersion.v1_18_2), sgViaVisual);

    public final VersionedBoolSetting replacePetrifiedOakSlab = versionedBoolSetting("Replace Petrified Oak Slab", ProtocolVersionRange.of(LegacyProtocolVersion.r1_3_1tor1_3_2, ProtocolVersion.v1_12_2), sgViaVisual);

    public final VersionedBoolSetting hideFurnaceRecipeBook = versionedBoolSetting("Hide Furnace Recipe Book", ProtocolVersionRange.andOlder(ProtocolVersion.v1_12_2), sgViaVisual);

    public final VersionedBoolSetting forceUnicodeFontForNonAsciiLanguages = versionedBoolSetting("Force Unicode Font For Non ASCII Languages", VersionedMode.Disabled, ProtocolVersionRange.andOlder(ProtocolVersion.v1_12_2), sgViaVisual);

    public final VersionedBoolSetting sneakInstantly = versionedBoolSetting("Sneak Instantly", ProtocolVersionRange.andOlder(ProtocolVersion.v1_12_2), sgViaVisual);

    public final VersionedBoolSetting sidewaysBackwardsRunning = versionedBoolSetting("Sideways Backwards Walking", ProtocolVersionRange.andOlder(ProtocolVersion.v1_11_1), sgViaVisual);

    public final VersionedBoolSetting hideCraftingRecipeBook = versionedBoolSetting("Hide Crafting Recipe Book", ProtocolVersionRange.andOlder(ProtocolVersion.v1_11_1), sgViaVisual);

    public final VersionedBoolSetting alwaysRenderCrosshair = versionedBoolSetting("Always Render Crosshair", ProtocolVersionRange.andOlder(ProtocolVersion.v1_8), sgViaVisual);

    public final VersionedBoolSetting swingHandOnItemUse = versionedBoolSetting("Swing Hand On Item Use", ProtocolVersionRange.andOlder(ProtocolVersion.v1_7_6), sgViaVisual);

    public final VersionedBoolSetting tiltItemPositions = versionedBoolSetting("Tilt Item Positions", ProtocolVersionRange.andOlder(ProtocolVersion.v1_7_6), sgViaVisual);

    public final VersionedBoolSetting enableLegacyTablist = versionedBoolSetting("Enable Legacy Tablist", ProtocolVersionRange.andOlder(ProtocolVersion.v1_7_6), sgViaVisual);

    public final VersionedBoolSetting replaceHurtSoundWithOOFSound = versionedBoolSetting("Replace Hurt Sound With OOF Sound", ProtocolVersionRange.andOlder(LegacyProtocolVersion.b1_8tob1_8_1), sgViaVisual);

    public final VersionedBoolSetting hideModernHUDElements = versionedBoolSetting("Hide Modern HUD Elements", ProtocolVersionRange.andOlder(LegacyProtocolVersion.b1_7tob1_7_3), sgViaVisual);

    public final VersionedBoolSetting replaceCreativeInventory = versionedBoolSetting("Replace Creative Inventory", ProtocolVersionRange.andOlder(LegacyProtocolVersion.c0_28toc0_30), sgViaVisual);

    public final VersionedBoolSetting oldWalkingAnimation = versionedBoolSetting("Old Walking Animation", ProtocolVersionRange.andOlder(LegacyProtocolVersion.c0_28toc0_30), sgViaVisual);

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

    public void openViaSettingsScreen() {
        mc.execute(() -> mc.gui.setScreen(switch (guiMode.getValue()) {
            case Panel -> PanelScreen.INSTANCE.openClientSettings();
            case Dropdown -> DropdownScreen.INSTANCE;
        }));
    }

    public static void setOrientation(Position position, int orientationIndex, int width, int height) {
        switch (orientationIndex) {
            case 1 -> position.setPosition(5, 5);
            case 2 -> position.setPosition(width - 98 - 5, 5);
            case 3 -> position.setPosition(5, height - 20 - 5);
            case 4 -> position.setPosition(width - 98 - 5, height - 20 - 5);
        }
    }

    public static String replaceDefaultPort(String address, ProtocolVersion version) {
        if (INSTANCE.replaceDefaultPort.getValue()
                && Objects.equals(version, BedrockProtocolVersion.bedrockLatest)
                && !address.contains(":")) {
            return address + ":" + ProtocolConstants.BEDROCK_RAKNET_DEFAULT_PORT;
        }
        return address;
    }

    private VersionedBoolSetting versionedBoolSetting(String name, ProtocolVersionRange range, SettingGroup group) {
        return versionedBoolSetting(name, VersionedMode.Auto, range, group);
    }

    private VersionedBoolSetting versionedBoolSetting(String name, VersionedMode defaultValue, ProtocolVersionRange range, SettingGroup group) {
        return addSetting(new VersionedBoolSetting(name, defaultValue, range)).group(group);
    }

    @FunctionalInterface
    public interface Position {
        void setPosition(int x, int y);
    }

}
