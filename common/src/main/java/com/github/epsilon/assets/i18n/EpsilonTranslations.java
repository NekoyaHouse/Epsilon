package com.github.epsilon.assets.i18n;

import java.util.List;

/**
 * Epsilon 内置界面文案的集中注册表。
 * <p>
 * UI 代码只引用这里暴露的语义常量，避免在各个实现类中散落 translation key，
 * 也让 I18NFileGenerator 可以直接从同一份注册表生成静态文案模板。
 */
public final class EpsilonTranslations {

    private static final List<TranslateComponent> ALL = List.of(
            Keybind.NONE,
            Keybind.TOGGLE,
            Keybind.HOLD,
            Module.VISIBLE,
            Module.HIDDEN,
            Module.FROM,
            Module.STATE_PREFIX,
            Module.STATE_ENABLED,
            Module.STATE_DISABLED,
            Gui.SEARCH,
            Gui.CLIENT_SETTINGS,
            Gui.NO_MODULE,
            Gui.MODULES,
            Gui.MAINMENU_SINGLEPLAYER,
            Gui.MAINMENU_MULTIPLAYER,
            Gui.MAINMENU_OPTIONS,
            Gui.MAINMENU_QUIT,
            Gui.MAINMENU_REISA_GREETING,
            Gui.TAB_GENERAL,
            Gui.TAB_FRIEND,
            Gui.TAB_CONFIG,
            Gui.TAB_ADDON,
            Gui.FRIEND_EMPTY,
            Gui.FRIEND_INPUT_PLACEHOLDER,
            Gui.CONFIG_INPUT_PLACEHOLDER,
            Gui.CONFIG_CURRENT,
            Gui.CONFIG_SWITCH_HINT,
            Gui.CONFIG_EMPTY,
            Gui.CONFIG_ACTION_SAVE_AS,
            Gui.CONFIG_ACTION_RELOAD,
            Gui.CONFIG_ACTION_EXPORT,
            Gui.CONFIG_ACTION_IMPORT,
            Gui.CONFIG_ACTION_NEW,
            Gui.CONFIG_ACTION_OPEN_FOLDER,
            Gui.CONFIG_DELETE_CONFIRM_TITLE,
            Gui.CONFIG_DELETE_CONFIRM_MESSAGE,
            Gui.CONFIG_DELETE_CONFIRM_CONFIRM,
            Gui.CONFIG_DELETE_CONFIRM_CANCEL,
            Gui.CONFIG_ERROR_TITLE,
            Gui.CONFIG_ERROR_OK,
            Gui.CONFIG_ERROR_SAVE,
            Gui.CONFIG_ERROR_RELOAD,
            Gui.CONFIG_ERROR_EXPORT,
            Gui.CONFIG_ERROR_IMPORT,
            Gui.CONFIG_ERROR_OPEN_FOLDER,
            Gui.CONFIG_ERROR_SWITCH,
            Gui.CONFIG_ERROR_DELETE,
            Gui.CONFIG_ERROR_DELETE_LAST,
            Gui.CONFIG_EXPORT_SUCCESS_TITLE,
            Gui.CONFIG_EXPORT_SUCCESS_MESSAGE,
            Gui.DROPDOWN_COLLAPSE_ALL,
            Gui.DROPDOWN_STATUS_SAVED,
            Gui.DROPDOWN_STATUS_RELOADED,
            Gui.DROPDOWN_STATUS_EXPORTED,
            Gui.DROPDOWN_STATUS_IMPORTED,
            Gui.DROPDOWN_STATUS_DELETED,
            Gui.DROPDOWN_STATUS_SWITCHED,
            Gui.DROPDOWN_STATUS_CREATED,
            Gui.DROPDOWN_HINT_SEARCH,
            Gui.DROPDOWN_HINT_PANELS,
            Gui.DROPDOWN_HINT_DRAG,
            Gui.ADDON_EMPTY,
            Gui.ADDON_NO_SETTINGS,
            Gui.ADDON_INFO_ID,
            Gui.ADDON_INFO_VERSION,
            Gui.ADDON_INFO_AUTHORS,
            Gui.ADDON_INFO_MODULES,
            Gui.INSPECTOR,
            Gui.INSPECTOR_SELECT,
            Gui.LIST_ENTRIES,
            Gui.LIST_BLOCKS,
            Gui.LIST_ITEMS,
            Gui.LIST_ENTITIES,
            Gui.LIST_SOUNDS,
            Gui.LIST_ENCHANTMENTS,
            Gui.LIST_SELECTED,
            Gui.LIST_TYPE_TO_ADD,
            Gui.LIST_SEARCH,
            Gui.LIST_ALL,
            Gui.LIST_AVAILABLE,
            Gui.LIST_SELECTED_HEADER,
            ElytraFly.PITCH40_TAKEOFF_COMPLETE,
            ElytraFly.PITCH40_TOO_CLOSE_TO_LOWER_BOUNDS,
            ElytraFly.PITCH40_NO_USABLE_ELYTRA,
            PlayerAlarms.JOIN_ALERT_TEXT,
            PlayerAlarms.LEAVE_ALERT_TEXT,
            PlayerAlarms.ENTER_RD_ALERT_TEXT,
            PlayerAlarms.LEAVE_RD_ALERT_TEXT,
            PlayerAlarms.GAMEMODE_ALERT_TEXT,
            PlayerAlarms.GAMEMODE_SURVIVAL,
            PlayerAlarms.GAMEMODE_CREATIVE,
            PlayerAlarms.GAMEMODE_ADVENTURE,
            PlayerAlarms.GAMEMODE_SPECTATOR,
            PlayerAlarms.UNKNOWN_GAMEMODE
    );

    private EpsilonTranslations() {
    }

    public static List<TranslateComponent> all() {
        return ALL;
    }

    public static final class Keybind {
        public static final TranslateComponent NONE = create("keybind", "none");
        public static final TranslateComponent TOGGLE = create("keybind", "toggle");
        public static final TranslateComponent HOLD = create("keybind", "hold");

        private Keybind() {
        }
    }

    public static final class Module {
        public static final TranslateComponent VISIBLE = create("module", "visible");
        public static final TranslateComponent HIDDEN = create("module", "hidden");
        public static final TranslateComponent FROM = create("module", "from");
        public static final TranslateComponent STATE_PREFIX = create("module", "state.prefix");
        public static final TranslateComponent STATE_ENABLED = create("module", "state.enabled");
        public static final TranslateComponent STATE_DISABLED = create("module", "state.disabled");

        private Module() {
        }
    }

    public static final class Gui {
        public static final TranslateComponent SEARCH = create("gui", "search");
        public static final TranslateComponent CLIENT_SETTINGS = create("gui", "clientsettings");
        public static final TranslateComponent NO_MODULE = create("gui", "no_module");
        public static final TranslateComponent MODULES = create("gui", "modules");

        public static final TranslateComponent MAINMENU_SINGLEPLAYER = create("gui", "mainmenu.singleplayer");
        public static final TranslateComponent MAINMENU_MULTIPLAYER = create("gui", "mainmenu.multiplayer");
        public static final TranslateComponent MAINMENU_OPTIONS = create("gui", "mainmenu.options");
        public static final TranslateComponent MAINMENU_QUIT = create("gui", "mainmenu.quit");
        public static final TranslateComponent MAINMENU_REISA_GREETING = create("gui", "mainmenu.reisa_greeting");
        public static final TranslateComponent MAINMENU_REISA_FAREWELL = create("gui", "mainmenu.reisa_farewell");

        public static final TranslateComponent TAB_GENERAL = create("gui", "tab.general");
        public static final TranslateComponent TAB_FRIEND = create("gui", "tab.friend");
        public static final TranslateComponent TAB_CONFIG = create("gui", "tab.config");
        public static final TranslateComponent TAB_ADDON = create("gui", "tab.addon");

        public static final TranslateComponent FRIEND_EMPTY = create("gui", "friend.empty");
        public static final TranslateComponent FRIEND_INPUT_PLACEHOLDER = create("gui", "friend.input.placeholder");

        public static final TranslateComponent CONFIG_INPUT_PLACEHOLDER = create("gui", "config.input.placeholder");
        public static final TranslateComponent CONFIG_CURRENT = create("gui", "config.current");
        public static final TranslateComponent CONFIG_SWITCH_HINT = create("gui", "config.switch_hint");
        public static final TranslateComponent CONFIG_EMPTY = create("gui", "config.empty");
        public static final TranslateComponent CONFIG_ACTION_SAVE_AS = create("gui", "config.action.saveas");
        public static final TranslateComponent CONFIG_ACTION_RELOAD = create("gui", "config.action.reload");
        public static final TranslateComponent CONFIG_ACTION_EXPORT = create("gui", "config.action.export");
        public static final TranslateComponent CONFIG_ACTION_IMPORT = create("gui", "config.action.import");
        public static final TranslateComponent CONFIG_ACTION_NEW = create("gui", "config.action.new");
        public static final TranslateComponent CONFIG_ACTION_OPEN_FOLDER = create("gui", "config.action.open_folder");
        public static final TranslateComponent CONFIG_DELETE_CONFIRM_TITLE = create("gui", "config.delete.confirm.title");
        public static final TranslateComponent CONFIG_DELETE_CONFIRM_MESSAGE = create("gui", "config.delete.confirm.message");
        public static final TranslateComponent CONFIG_DELETE_CONFIRM_CONFIRM = create("gui", "config.delete.confirm.confirm");
        public static final TranslateComponent CONFIG_DELETE_CONFIRM_CANCEL = create("gui", "config.delete.confirm.cancel");
        public static final TranslateComponent CONFIG_ERROR_TITLE = create("gui", "config.error.title");
        public static final TranslateComponent CONFIG_ERROR_OK = create("gui", "config.error.ok");
        public static final TranslateComponent CONFIG_ERROR_SAVE = create("gui", "config.error.save");
        public static final TranslateComponent CONFIG_ERROR_RELOAD = create("gui", "config.error.reload");
        public static final TranslateComponent CONFIG_ERROR_EXPORT = create("gui", "config.error.export");
        public static final TranslateComponent CONFIG_ERROR_IMPORT = create("gui", "config.error.import");
        public static final TranslateComponent CONFIG_ERROR_OPEN_FOLDER = create("gui", "config.error.open_folder");
        public static final TranslateComponent CONFIG_ERROR_SWITCH = create("gui", "config.error.switch");
        public static final TranslateComponent CONFIG_ERROR_DELETE = create("gui", "config.error.delete");
        public static final TranslateComponent CONFIG_ERROR_DELETE_LAST = create("gui", "config.error.delete_last");
        public static final TranslateComponent CONFIG_EXPORT_SUCCESS_TITLE = create("gui", "config.export.success.title");
        public static final TranslateComponent CONFIG_EXPORT_SUCCESS_MESSAGE = create("gui", "config.export.success.message");

        public static final TranslateComponent DROPDOWN_COLLAPSE_ALL = create("gui", "dropdown.collapse_all");
        public static final TranslateComponent DROPDOWN_STATUS_SAVED = create("gui", "dropdown.status.saved");
        public static final TranslateComponent DROPDOWN_STATUS_RELOADED = create("gui", "dropdown.status.reloaded");
        public static final TranslateComponent DROPDOWN_STATUS_EXPORTED = create("gui", "dropdown.status.exported");
        public static final TranslateComponent DROPDOWN_STATUS_IMPORTED = create("gui", "dropdown.status.imported");
        public static final TranslateComponent DROPDOWN_STATUS_DELETED = create("gui", "dropdown.status.deleted");
        public static final TranslateComponent DROPDOWN_STATUS_SWITCHED = create("gui", "dropdown.status.switched");
        public static final TranslateComponent DROPDOWN_STATUS_CREATED = create("gui", "dropdown.status.created");
        public static final TranslateComponent DROPDOWN_HINT_SEARCH = create("gui", "dropdown.hint.search");
        public static final TranslateComponent DROPDOWN_HINT_PANELS = create("gui", "dropdown.hint.panels");
        public static final TranslateComponent DROPDOWN_HINT_DRAG = create("gui", "dropdown.hint.drag");

        public static final TranslateComponent ADDON_EMPTY = create("gui", "addon.empty");
        public static final TranslateComponent ADDON_NO_SETTINGS = create("gui", "addon.no_settings");
        public static final TranslateComponent ADDON_INFO_ID = create("gui", "addon.info.id");
        public static final TranslateComponent ADDON_INFO_VERSION = create("gui", "addon.info.version");
        public static final TranslateComponent ADDON_INFO_AUTHORS = create("gui", "addon.info.authors");
        public static final TranslateComponent ADDON_INFO_MODULES = create("gui", "addon.info.modules");

        public static final TranslateComponent INSPECTOR = create("gui", "inspector");
        public static final TranslateComponent INSPECTOR_SELECT = create("gui", "inspector.select");

        public static final TranslateComponent LIST_ENTRIES = create("gui", "list.entries");
        public static final TranslateComponent LIST_BLOCKS = create("gui", "list.blocks");
        public static final TranslateComponent LIST_ITEMS = create("gui", "list.items");
        public static final TranslateComponent LIST_ENTITIES = create("gui", "list.entities");
        public static final TranslateComponent LIST_SOUNDS = create("gui", "list.sounds");
        public static final TranslateComponent LIST_ENCHANTMENTS = create("gui", "list.enchantments");
        public static final TranslateComponent LIST_SELECTED = create("gui", "list.selected");
        public static final TranslateComponent LIST_TYPE_TO_ADD = create("gui", "list.type_to_add");
        public static final TranslateComponent LIST_SEARCH = create("gui", "list.search");
        public static final TranslateComponent LIST_ALL = create("gui", "list.all");
        public static final TranslateComponent LIST_AVAILABLE = create("gui", "list.available");
        public static final TranslateComponent LIST_SELECTED_HEADER = create("gui", "list.selected_header");

        private Gui() {
        }
    }

    public static final class ElytraFly {
        public static final TranslateComponent PITCH40_TAKEOFF_COMPLETE = create("modules.elytra fly", "pitch40_takeoff_complete");
        public static final TranslateComponent PITCH40_TOO_CLOSE_TO_LOWER_BOUNDS = create("modules.elytra fly", "pitch40_too_close_to_lower_bounds");
        public static final TranslateComponent PITCH40_NO_USABLE_ELYTRA = create("modules.elytra fly", "pitch40_no_usable_elytra");

        private ElytraFly() {
        }
    }

    public static final class PlayerAlarms {
        public static final TranslateComponent JOIN_ALERT_TEXT = create("modules.player alarms", "join_alert_text");
        public static final TranslateComponent LEAVE_ALERT_TEXT = create("modules.player alarms", "leave_alert_text");
        public static final TranslateComponent ENTER_RD_ALERT_TEXT = create("modules.player alarms", "enter_rd_alert_text");
        public static final TranslateComponent LEAVE_RD_ALERT_TEXT = create("modules.player alarms", "leave_rd_alert_text");
        public static final TranslateComponent GAMEMODE_ALERT_TEXT = create("modules.player alarms", "gamemode_alert_text");
        public static final TranslateComponent GAMEMODE_SURVIVAL = create("modules.player alarms", "gamemode.survival");
        public static final TranslateComponent GAMEMODE_CREATIVE = create("modules.player alarms", "gamemode.creative");
        public static final TranslateComponent GAMEMODE_ADVENTURE = create("modules.player alarms", "gamemode.adventure");
        public static final TranslateComponent GAMEMODE_SPECTATOR = create("modules.player alarms", "gamemode.spectator");
        public static final TranslateComponent UNKNOWN_GAMEMODE = create("modules.player alarms", "unknown_gamemode");

        private PlayerAlarms() {
        }
    }

    private static TranslateComponent create(String prefix, String suffix) {
        return EpsilonTranslateComponent.create(prefix, suffix);
    }

}
