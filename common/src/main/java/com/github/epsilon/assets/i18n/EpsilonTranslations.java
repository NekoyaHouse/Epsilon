package com.github.epsilon.assets.i18n;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;
import java.util.Locale;

/**
 * Epsilon 内置界面文案的集中注册表。
 * <p>
 * UI 代码只引用这里暴露的语义常量，避免在各个实现类中散落 translation key，
 * 也让 I18NFileGenerator 可以直接从同一份注册表生成静态文案模板。
 */
public class EpsilonTranslations {

    private static final List<TranslateComponent> ALL = List.of(
            Keybind.NONE,
            Keybind.TOGGLE,
            Keybind.HOLD,
            Module.VISIBLE,
            Module.HIDDEN,
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
            Gui.MAINMENU_ACCOUNTS,
            Gui.MAINMENU_REISA_GREETING,
            Gui.MAINMENU_REISA_FAREWELL,
            Gui.ACCOUNTS_TITLE,
            Gui.ACCOUNTS_EMPTY,
            Gui.ACCOUNTS_CURRENT,
            Gui.ACCOUNTS_LOGIN,
            Gui.ACCOUNTS_DELETE_CONFIRM,
            Gui.ACCOUNTS_CANCEL,
            Gui.ACCOUNTS_ADD,
            Gui.ACCOUNTS_ADD_CRACKED,
            Gui.ACCOUNTS_ADD_ALTENING,
            Gui.ACCOUNTS_ADD_SESSION,
            Gui.ACCOUNTS_ADD_MICROSOFT,
            Gui.ACCOUNTS_NAME_PLACEHOLDER,
            Gui.ACCOUNTS_TOKEN_PLACEHOLDER,
            Gui.ACCOUNTS_WAITING_MICROSOFT,
            Gui.ACCOUNTS_MICROSOFT_ERROR,
            Gui.ACCOUNTS_SEARCH_PLACEHOLDER,
            Gui.ACCOUNTS_SEARCH_EMPTY,
            Gui.ACCOUNTS_SORT_ADDED,
            Gui.ACCOUNTS_SORT_NAME,
            Gui.ACCOUNTS_SORT_TYPE,
            Gui.ACCOUNTS_EMPTY_HINT,
            Gui.TAB_GENERAL,
            Gui.TAB_FRIEND,
            Gui.TAB_CONFIG,
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
            Gui.NO_SETTINGS,
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
            Resources.VIDEO,
            Resources.REISA,
            Resources.FFMPEG,
            Resources.TITLE,
            Resources.STATUS_READY,
            Resources.STATUS_MISSING,
            Resources.STATUS_DOWNLOADING,
            Resources.BUTTON_DOWNLOAD,
            Resources.BUTTON_RETRY,
            Resources.BUTTON_CANCEL,
            Resources.BUTTON_CLOSE,
            Resources.BUTTON_DONE,
            Resources.BUTTON_BACKGROUND,
            Resources.BUTTON_STOP,
            Resources.HINT,
            Resources.DOWNLOADING,
            Resources.FAILED,
            Resources.DOWNLOAD_SUCCESS_TITLE,
            Resources.DOWNLOAD_SUCCESS_MESSAGE,
            Resources.DOWNLOAD_FAILED_TITLE,
            Resources.CLEAR_CONFIRM_TITLE,
            Resources.CLEAR_CONFIRM_MESSAGE,
            Resources.CLEAR_CONFIRM_YES,
            Resources.CLEAR_CONFIRM_NO,
            Resources.CLEAR_SUCCESS_TITLE,
            Resources.CLEAR_SUCCESS_MESSAGE,
            Resources.OPEN_FOLDER_FAILED,
            PlatformOnly.BADGE,
            PlatformOnly.TITLE,
            PlatformOnly.FEATURE,
            PlatformOnly.REQUIREMENT,
            PlatformOnly.CURRENT,
            PlatformOnly.HINT,
            PlatformOnly.CONFIRM,
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
            PlayerAlarms.UNKNOWN_GAMEMODE,
            PlayerInfo.PLAYERS,
            PlayerInfo.DISTANCE,
            PlayerInfo.HEALTH,
            PlayerInfo.POPS,
            PlayerInfo.DIRECTION,
            PlayerInfo.EMPTY,
            Notifications.NO_SLOWDOWN_DISABLED_WATER,
            Notifications.NO_SLOWDOWN_DISABLED_FALLING,
            Notifications.NO_SLOWDOWN_DISABLED_PEARL,
            Notifications.TRANSACTION_COUNT_TOO_HIGH,
            Notifications.SCAFFOLD_FLYING_WARNING,
            Notifications.SCAFFOLD_TOGGLE_ON_TELEPORT,
            Notifications.SERVERBOUND_PACKET_FLUSH_FAILED,
            Notifications.CLIENTBOUND_PACKET_FLUSH_FAILED,
            Notifications.KEY_FRIEND_ADDED,
            Notifications.KEY_FRIEND_REMOVED,
            Notifications.PREVIEW_TITLE,
            Notifications.PREVIEW_MESSAGE,
            Via.BASE_CANCEL,
            Via.BASE_REFRESH,
            Via.BASE_SOMETHING_WENT_WRONG,
            Via.BASE_SET_VERSION,
            Via.BASE_CANCEL_AND_RESET,
            Via.BASE_DETECTING_SERVER_VERSION,
            Via.BASE_TARGET_VERSION,
            Via.BASE_SERVER_VERSION,
            Via.BASE_THIS_WILL_REQUIRE_A_RESTART,
            Via.SCREEN_FORCE_VERSION,
            Via.FORCE_VERSION_TITLE,
            Via.JAVA_FAILED_TO_VERIFY_SESSION,
            Via.PACKET_ERROR
    );

    private EpsilonTranslations() {
    }

    public static List<TranslateComponent> all() {
        return ALL;
    }

    public static class Keybind {
        public static final TranslateComponent NONE = create("keybind", "none");
        public static final TranslateComponent TOGGLE = create("keybind", "toggle");
        public static final TranslateComponent HOLD = create("keybind", "hold");

        private Keybind() {
        }
    }

    public static class Module {
        public static final TranslateComponent VISIBLE = create("module", "visible");
        public static final TranslateComponent HIDDEN = create("module", "hidden");
        public static final TranslateComponent STATE_PREFIX = create("module", "state.prefix");
        public static final TranslateComponent STATE_ENABLED = create("module", "state.enabled");
        public static final TranslateComponent STATE_DISABLED = create("module", "state.disabled");

        private Module() {
        }
    }

    public static class Gui {
        public static final TranslateComponent SEARCH = create("gui", "search");
        public static final TranslateComponent CLIENT_SETTINGS = create("gui", "clientsettings");
        public static final TranslateComponent NO_MODULE = create("gui", "no_module");
        public static final TranslateComponent MODULES = create("gui", "modules");

        public static final TranslateComponent MAINMENU_SINGLEPLAYER = create("gui", "mainmenu.singleplayer");
        public static final TranslateComponent MAINMENU_MULTIPLAYER = create("gui", "mainmenu.multiplayer");
        public static final TranslateComponent MAINMENU_OPTIONS = create("gui", "mainmenu.options");
        public static final TranslateComponent MAINMENU_QUIT = create("gui", "mainmenu.quit");
        public static final TranslateComponent MAINMENU_ACCOUNTS = create("gui", "mainmenu.accounts");
        public static final TranslateComponent MAINMENU_REISA_GREETING = create("gui", "mainmenu.reisa_greeting");
        public static final TranslateComponent MAINMENU_REISA_FAREWELL = create("gui", "mainmenu.reisa_farewell");

        public static final TranslateComponent ACCOUNTS_TITLE = create("gui", "accounts.title");
        public static final TranslateComponent ACCOUNTS_EMPTY = create("gui", "accounts.empty");
        public static final TranslateComponent ACCOUNTS_CURRENT = create("gui", "accounts.current");
        public static final TranslateComponent ACCOUNTS_LOGIN = create("gui", "accounts.login");
        public static final TranslateComponent ACCOUNTS_DELETE_CONFIRM = create("gui", "accounts.delete.confirm");
        public static final TranslateComponent ACCOUNTS_CANCEL = create("gui", "accounts.cancel");
        public static final TranslateComponent ACCOUNTS_ADD = create("gui", "accounts.add");
        public static final TranslateComponent ACCOUNTS_ADD_CRACKED = create("gui", "accounts.add.cracked");
        public static final TranslateComponent ACCOUNTS_ADD_ALTENING = create("gui", "accounts.add.altening");
        public static final TranslateComponent ACCOUNTS_ADD_SESSION = create("gui", "accounts.add.session");
        public static final TranslateComponent ACCOUNTS_ADD_MICROSOFT = create("gui", "accounts.add.microsoft");
        public static final TranslateComponent ACCOUNTS_NAME_PLACEHOLDER = create("gui", "accounts.name.placeholder");
        public static final TranslateComponent ACCOUNTS_TOKEN_PLACEHOLDER = create("gui", "accounts.token.placeholder");
        public static final TranslateComponent ACCOUNTS_WAITING_MICROSOFT = create("gui", "accounts.waiting.microsoft");
        public static final TranslateComponent ACCOUNTS_MICROSOFT_ERROR = create("gui", "accounts.microsoft.error");
        public static final TranslateComponent ACCOUNTS_SEARCH_PLACEHOLDER = create("gui", "accounts.search.placeholder");
        public static final TranslateComponent ACCOUNTS_SEARCH_EMPTY = create("gui", "accounts.search.empty");
        public static final TranslateComponent ACCOUNTS_SORT_ADDED = create("gui", "accounts.sort.added");
        public static final TranslateComponent ACCOUNTS_SORT_NAME = create("gui", "accounts.sort.name");
        public static final TranslateComponent ACCOUNTS_SORT_TYPE = create("gui", "accounts.sort.type");
        public static final TranslateComponent ACCOUNTS_EMPTY_HINT = create("gui", "accounts.empty.hint");

        public static final TranslateComponent TAB_GENERAL = create("gui", "tab.general");
        public static final TranslateComponent TAB_FRIEND = create("gui", "tab.friend");
        public static final TranslateComponent TAB_CONFIG = create("gui", "tab.config");

        public static final TranslateComponent FRIEND_EMPTY = create("gui", "friend.empty");
        public static final TranslateComponent NO_SETTINGS = create("gui", "no_settings");
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

    /**
     * 大体积运行时资源下载相关的界面文案。
     */
    public static class Resources {
        public static final TranslateComponent VIDEO = create("gui", "resource.video");
        public static final TranslateComponent REISA = create("gui", "resource.reisa");
        public static final TranslateComponent FFMPEG = create("gui", "resource.ffmpeg");
        public static final TranslateComponent TITLE = create("gui", "resource.title");
        public static final TranslateComponent STATUS_READY = create("gui", "resource.status.ready");
        public static final TranslateComponent STATUS_MISSING = create("gui", "resource.status.missing");
        public static final TranslateComponent STATUS_DOWNLOADING = create("gui", "resource.status.downloading");
        public static final TranslateComponent BUTTON_DOWNLOAD = create("gui", "resource.button.download");
        public static final TranslateComponent BUTTON_RETRY = create("gui", "resource.button.retry");
        public static final TranslateComponent BUTTON_CANCEL = create("gui", "resource.button.cancel");
        public static final TranslateComponent BUTTON_CLOSE = create("gui", "resource.button.close");
        public static final TranslateComponent BUTTON_DONE = create("gui", "resource.button.done");
        public static final TranslateComponent BUTTON_BACKGROUND = create("gui", "resource.button.background");
        public static final TranslateComponent BUTTON_STOP = create("gui", "resource.button.stop");
        public static final TranslateComponent HINT = create("gui", "resource.hint");
        public static final TranslateComponent DOWNLOADING = create("gui", "resource.downloading");
        public static final TranslateComponent FAILED = create("gui", "resource.failed");
        public static final TranslateComponent DOWNLOAD_SUCCESS_TITLE = create("gui", "resource.success.title");
        public static final TranslateComponent DOWNLOAD_SUCCESS_MESSAGE = create("gui", "resource.success.message");
        public static final TranslateComponent DOWNLOAD_FAILED_TITLE = create("gui", "resource.failed.title");
        public static final TranslateComponent CLEAR_CONFIRM_TITLE = create("gui", "resource.clear.title");
        public static final TranslateComponent CLEAR_CONFIRM_MESSAGE = create("gui", "resource.clear.message");
        public static final TranslateComponent CLEAR_CONFIRM_YES = create("gui", "resource.clear.yes");
        public static final TranslateComponent CLEAR_CONFIRM_NO = create("gui", "resource.clear.no");
        public static final TranslateComponent CLEAR_SUCCESS_TITLE = create("gui", "resource.cleared.title");
        public static final TranslateComponent CLEAR_SUCCESS_MESSAGE = create("gui", "resource.cleared.message");
        public static final TranslateComponent OPEN_FOLDER_FAILED = create("gui", "resource.open_folder_failed");

        private Resources() {
        }
    }

    /**
     * Platform Only 提示相关文案。
     */
    public static class PlatformOnly {
        public static final TranslateComponent BADGE = create("gui", "platform.badge");
        public static final TranslateComponent TITLE = create("gui", "platform.title");
        public static final TranslateComponent FEATURE = create("gui", "platform.feature");
        public static final TranslateComponent REQUIREMENT = create("gui", "platform.requirement");
        public static final TranslateComponent CURRENT = create("gui", "platform.current");
        public static final TranslateComponent HINT = create("gui", "platform.hint");
        public static final TranslateComponent CONFIRM = create("gui", "platform.confirm");

        private PlatformOnly() {
        }
    }

    public static class ElytraFly {
        public static final TranslateComponent PITCH40_TAKEOFF_COMPLETE = create("modules.elytra fly", "pitch40_takeoff_complete");
        public static final TranslateComponent PITCH40_TOO_CLOSE_TO_LOWER_BOUNDS = create("modules.elytra fly", "pitch40_too_close_to_lower_bounds");
        public static final TranslateComponent PITCH40_NO_USABLE_ELYTRA = create("modules.elytra fly", "pitch40_no_usable_elytra");

        private ElytraFly() {
        }
    }

    public static class PlayerAlarms {
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

    public static class PlayerInfo {
        public static final TranslateComponent PLAYERS = create("elements.player info", "players");
        public static final TranslateComponent DISTANCE = create("elements.player info", "distance");
        public static final TranslateComponent HEALTH = create("elements.player info", "health");
        public static final TranslateComponent POPS = create("elements.player info", "pops");
        public static final TranslateComponent DIRECTION = create("elements.player info", "direction");
        public static final TranslateComponent EMPTY = create("elements.player info", "empty");

        private PlayerInfo() {
        }
    }

    public static class Notifications {
        public static final TranslateComponent PREVIEW_TITLE = create("elements.notifications", "preview.title");
        public static final TranslateComponent PREVIEW_MESSAGE = create("elements.notifications", "preview.message");
        public static final TranslateComponent NO_SLOWDOWN_DISABLED_WATER = create("notification", "no_slowdown_disabled_water");
        public static final TranslateComponent NO_SLOWDOWN_DISABLED_FALLING = create("notification", "no_slowdown_disabled_falling");
        public static final TranslateComponent NO_SLOWDOWN_DISABLED_PEARL = create("notification", "no_slowdown_disabled_pearl");
        public static final TranslateComponent TRANSACTION_COUNT_TOO_HIGH = create("notification", "transaction_count_too_high");
        public static final TranslateComponent SCAFFOLD_FLYING_WARNING = create("notification", "scaffold_flying_warning");
        public static final TranslateComponent SCAFFOLD_TOGGLE_ON_TELEPORT = create("notification", "scaffold_toggle_on_teleport");
        public static final TranslateComponent SERVERBOUND_PACKET_FLUSH_FAILED = create("notification", "serverbound_packet_flush_failed");
        public static final TranslateComponent CLIENTBOUND_PACKET_FLUSH_FAILED = create("notification", "clientbound_packet_flush_failed");
        public static final TranslateComponent KEY_FRIEND_ADDED = create("notification", "key_friend_added");
        public static final TranslateComponent KEY_FRIEND_REMOVED = create("notification", "key_friend_removed");

        private Notifications() {
        }
    }

    public static class Via {
        public static final TranslateComponent BASE_CANCEL = create("via", "base.cancel");
        public static final TranslateComponent BASE_REFRESH = create("via", "base.refresh");
        public static final TranslateComponent BASE_SOMETHING_WENT_WRONG = create("via", "base.something_went_wrong");
        public static final TranslateComponent BASE_SET_VERSION = create("via", "base.set_version");
        public static final TranslateComponent BASE_CANCEL_AND_RESET = create("via", "base.cancel_and_reset");
        public static final TranslateComponent BASE_DETECTING_SERVER_VERSION = create("via", "base.detecting_server_version");
        public static final TranslateComponent BASE_TARGET_VERSION = create("via", "base.target_version");
        public static final TranslateComponent BASE_SERVER_VERSION = create("via", "base.server_version");
        public static final TranslateComponent BASE_THIS_WILL_REQUIRE_A_RESTART = create("via", "base.this_will_require_a_restart");

        public static final TranslateComponent SCREEN_FORCE_VERSION = create("via", "screen.force_version");
        public static final TranslateComponent FORCE_VERSION_TITLE = create("via", "force_version.title");
        public static final TranslateComponent JAVA_FAILED_TO_VERIFY_SESSION = create("via", "java.failed_to_verify_session");

        public static final TranslateComponent PACKET_ERROR = create("via", "translation.packet_error");

        private Via() {
        }
    }

    public static MutableComponent component(TranslateComponent translation, Object... args) {
        String text = translation.getTranslatedName();
        if (args.length > 0) {
            text = String.format(Locale.ROOT, text, args);
        }
        return Component.literal(text);
    }

    private static TranslateComponent create(String prefix, String suffix) {
        return EpsilonTranslateComponent.create(prefix, suffix);
    }

}
