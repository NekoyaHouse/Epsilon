package com.github.epsilon.assets.i18n

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import java.util.*

/**
 * Epsilon 内置界面文案的集中注册表。
 *
 * UI 代码只引用这里暴露的语义常量，避免在各个实现类中散落 translation key，
 * 也让 I18NFileGenerator 可以直接从同一份注册表生成静态文案模板。
 */
object EpsilonTranslations {

    private val ALL: List<TranslateComponent> = listOf(
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
        TrollGui.CLIENT,
        TrollGui.KEYBIND,
        TrollGui.LISTENING,
        TrollGui.COLOR_PICKER,
        TrollGui.RED,
        TrollGui.GREEN,
        TrollGui.BLUE,
        TrollGui.ALPHA,
        TrollGui.OKAY,
        TrollGui.CANCEL,
        TrollGui.APPLY,
        Resources.VIDEO,
        Resources.LIGHT_TRAILS,
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
        Resources.JOB_PREPARING,
        Resources.JOB_DETAILS,
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
        AutoMine.PACKET_MINE_REQUIRED,
        AutoMine.PACKET_MINE_ENABLED,
        AutoMine.PACKET_MINE_DISABLED,
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
        Via.PACKET_ERROR,
    )

    fun all(): List<TranslateComponent> = ALL

    object Keybind {
        val NONE: TranslateComponent = create("keybind", "none")
        val TOGGLE: TranslateComponent = create("keybind", "toggle")
        val HOLD: TranslateComponent = create("keybind", "hold")
    }

    object Module {
        val VISIBLE: TranslateComponent = create("module", "visible")
        val HIDDEN: TranslateComponent = create("module", "hidden")
        val STATE_PREFIX: TranslateComponent = create("module", "state.prefix")
        val STATE_ENABLED: TranslateComponent = create("module", "state.enabled")
        val STATE_DISABLED: TranslateComponent = create("module", "state.disabled")
    }

    object Gui {
        val SEARCH: TranslateComponent = create("gui", "search")
        val CLIENT_SETTINGS: TranslateComponent = create("gui", "clientsettings")
        val NO_MODULE: TranslateComponent = create("gui", "no_module")
        val MODULES: TranslateComponent = create("gui", "modules")
        val MAINMENU_SINGLEPLAYER: TranslateComponent = create("gui", "mainmenu.singleplayer")
        val MAINMENU_MULTIPLAYER: TranslateComponent = create("gui", "mainmenu.multiplayer")
        val MAINMENU_OPTIONS: TranslateComponent = create("gui", "mainmenu.options")
        val MAINMENU_QUIT: TranslateComponent = create("gui", "mainmenu.quit")
        val MAINMENU_ACCOUNTS: TranslateComponent = create("gui", "mainmenu.accounts")
        val MAINMENU_REISA_GREETING: TranslateComponent = create("gui", "mainmenu.reisa_greeting")
        val MAINMENU_REISA_FAREWELL: TranslateComponent = create("gui", "mainmenu.reisa_farewell")
        val ACCOUNTS_TITLE: TranslateComponent = create("gui", "accounts.title")
        val ACCOUNTS_EMPTY: TranslateComponent = create("gui", "accounts.empty")
        val ACCOUNTS_CURRENT: TranslateComponent = create("gui", "accounts.current")
        val ACCOUNTS_LOGIN: TranslateComponent = create("gui", "accounts.login")
        val ACCOUNTS_DELETE_CONFIRM: TranslateComponent = create("gui", "accounts.delete.confirm")
        val ACCOUNTS_CANCEL: TranslateComponent = create("gui", "accounts.cancel")
        val ACCOUNTS_ADD: TranslateComponent = create("gui", "accounts.add")
        val ACCOUNTS_ADD_CRACKED: TranslateComponent = create("gui", "accounts.add.cracked")
        val ACCOUNTS_ADD_ALTENING: TranslateComponent = create("gui", "accounts.add.altening")
        val ACCOUNTS_ADD_SESSION: TranslateComponent = create("gui", "accounts.add.session")
        val ACCOUNTS_ADD_MICROSOFT: TranslateComponent = create("gui", "accounts.add.microsoft")
        val ACCOUNTS_NAME_PLACEHOLDER: TranslateComponent = create("gui", "accounts.name.placeholder")
        val ACCOUNTS_TOKEN_PLACEHOLDER: TranslateComponent = create("gui", "accounts.token.placeholder")
        val ACCOUNTS_WAITING_MICROSOFT: TranslateComponent = create("gui", "accounts.waiting.microsoft")
        val ACCOUNTS_MICROSOFT_ERROR: TranslateComponent = create("gui", "accounts.microsoft.error")
        val ACCOUNTS_SEARCH_PLACEHOLDER: TranslateComponent = create("gui", "accounts.search.placeholder")
        val ACCOUNTS_SEARCH_EMPTY: TranslateComponent = create("gui", "accounts.search.empty")
        val ACCOUNTS_SORT_ADDED: TranslateComponent = create("gui", "accounts.sort.added")
        val ACCOUNTS_SORT_NAME: TranslateComponent = create("gui", "accounts.sort.name")
        val ACCOUNTS_SORT_TYPE: TranslateComponent = create("gui", "accounts.sort.type")
        val ACCOUNTS_EMPTY_HINT: TranslateComponent = create("gui", "accounts.empty.hint")
        val TAB_GENERAL: TranslateComponent = create("gui", "tab.general")
        val TAB_FRIEND: TranslateComponent = create("gui", "tab.friend")
        val TAB_CONFIG: TranslateComponent = create("gui", "tab.config")
        val FRIEND_EMPTY: TranslateComponent = create("gui", "friend.empty")
        val NO_SETTINGS: TranslateComponent = create("gui", "no_settings")
        val FRIEND_INPUT_PLACEHOLDER: TranslateComponent = create("gui", "friend.input.placeholder")
        val CONFIG_INPUT_PLACEHOLDER: TranslateComponent = create("gui", "config.input.placeholder")
        val CONFIG_CURRENT: TranslateComponent = create("gui", "config.current")
        val CONFIG_SWITCH_HINT: TranslateComponent = create("gui", "config.switch_hint")
        val CONFIG_EMPTY: TranslateComponent = create("gui", "config.empty")
        val CONFIG_ACTION_SAVE_AS: TranslateComponent = create("gui", "config.action.saveas")
        val CONFIG_ACTION_RELOAD: TranslateComponent = create("gui", "config.action.reload")
        val CONFIG_ACTION_EXPORT: TranslateComponent = create("gui", "config.action.export")
        val CONFIG_ACTION_IMPORT: TranslateComponent = create("gui", "config.action.import")
        val CONFIG_ACTION_NEW: TranslateComponent = create("gui", "config.action.new")
        val CONFIG_ACTION_OPEN_FOLDER: TranslateComponent = create("gui", "config.action.open_folder")
        val CONFIG_DELETE_CONFIRM_TITLE: TranslateComponent = create("gui", "config.delete.confirm.title")
        val CONFIG_DELETE_CONFIRM_MESSAGE: TranslateComponent = create("gui", "config.delete.confirm.message")
        val CONFIG_DELETE_CONFIRM_CONFIRM: TranslateComponent = create("gui", "config.delete.confirm.confirm")
        val CONFIG_DELETE_CONFIRM_CANCEL: TranslateComponent = create("gui", "config.delete.confirm.cancel")
        val CONFIG_ERROR_TITLE: TranslateComponent = create("gui", "config.error.title")
        val CONFIG_ERROR_OK: TranslateComponent = create("gui", "config.error.ok")
        val CONFIG_ERROR_SAVE: TranslateComponent = create("gui", "config.error.save")
        val CONFIG_ERROR_RELOAD: TranslateComponent = create("gui", "config.error.reload")
        val CONFIG_ERROR_EXPORT: TranslateComponent = create("gui", "config.error.export")
        val CONFIG_ERROR_IMPORT: TranslateComponent = create("gui", "config.error.import")
        val CONFIG_ERROR_OPEN_FOLDER: TranslateComponent = create("gui", "config.error.open_folder")
        val CONFIG_ERROR_SWITCH: TranslateComponent = create("gui", "config.error.switch")
        val CONFIG_ERROR_DELETE: TranslateComponent = create("gui", "config.error.delete")
        val CONFIG_ERROR_DELETE_LAST: TranslateComponent = create("gui", "config.error.delete_last")
        val CONFIG_EXPORT_SUCCESS_TITLE: TranslateComponent = create("gui", "config.export.success.title")
        val CONFIG_EXPORT_SUCCESS_MESSAGE: TranslateComponent = create("gui", "config.export.success.message")
        val DROPDOWN_COLLAPSE_ALL: TranslateComponent = create("gui", "dropdown.collapse_all")
        val DROPDOWN_STATUS_SAVED: TranslateComponent = create("gui", "dropdown.status.saved")
        val DROPDOWN_STATUS_RELOADED: TranslateComponent = create("gui", "dropdown.status.reloaded")
        val DROPDOWN_STATUS_EXPORTED: TranslateComponent = create("gui", "dropdown.status.exported")
        val DROPDOWN_STATUS_IMPORTED: TranslateComponent = create("gui", "dropdown.status.imported")
        val DROPDOWN_STATUS_DELETED: TranslateComponent = create("gui", "dropdown.status.deleted")
        val DROPDOWN_STATUS_SWITCHED: TranslateComponent = create("gui", "dropdown.status.switched")
        val DROPDOWN_STATUS_CREATED: TranslateComponent = create("gui", "dropdown.status.created")
        val DROPDOWN_HINT_SEARCH: TranslateComponent = create("gui", "dropdown.hint.search")
        val DROPDOWN_HINT_PANELS: TranslateComponent = create("gui", "dropdown.hint.panels")
        val DROPDOWN_HINT_DRAG: TranslateComponent = create("gui", "dropdown.hint.drag")
        val INSPECTOR: TranslateComponent = create("gui", "inspector")
        val INSPECTOR_SELECT: TranslateComponent = create("gui", "inspector.select")
        val LIST_ENTRIES: TranslateComponent = create("gui", "list.entries")
        val LIST_BLOCKS: TranslateComponent = create("gui", "list.blocks")
        val LIST_ITEMS: TranslateComponent = create("gui", "list.items")
        val LIST_ENTITIES: TranslateComponent = create("gui", "list.entities")
        val LIST_SOUNDS: TranslateComponent = create("gui", "list.sounds")
        val LIST_ENCHANTMENTS: TranslateComponent = create("gui", "list.enchantments")
        val LIST_SELECTED: TranslateComponent = create("gui", "list.selected")
        val LIST_TYPE_TO_ADD: TranslateComponent = create("gui", "list.type_to_add")
        val LIST_SEARCH: TranslateComponent = create("gui", "list.search")
        val LIST_ALL: TranslateComponent = create("gui", "list.all")
        val LIST_AVAILABLE: TranslateComponent = create("gui", "list.available")
        val LIST_SELECTED_HEADER: TranslateComponent = create("gui", "list.selected_header")
    }

    object TrollGui {
        val CLIENT: TranslateComponent = create("gui", "troll.client")
        val KEYBIND: TranslateComponent = create("gui", "troll.keybind")
        val LISTENING: TranslateComponent = create("gui", "troll.listening")
        val COLOR_PICKER: TranslateComponent = create("gui", "troll.color_picker")
        val RED: TranslateComponent = create("gui", "troll.red")
        val GREEN: TranslateComponent = create("gui", "troll.green")
        val BLUE: TranslateComponent = create("gui", "troll.blue")
        val ALPHA: TranslateComponent = create("gui", "troll.alpha")
        val OKAY: TranslateComponent = create("gui", "troll.okay")
        val CANCEL: TranslateComponent = create("gui", "troll.cancel")
        val APPLY: TranslateComponent = create("gui", "troll.apply")
    }

    object Resources {
        val VIDEO: TranslateComponent = create("gui", "resource.video")
        val LIGHT_TRAILS: TranslateComponent = create("gui", "resource.light_trails")
        val REISA: TranslateComponent = create("gui", "resource.reisa")
        val FFMPEG: TranslateComponent = create("gui", "resource.ffmpeg")
        val TITLE: TranslateComponent = create("gui", "resource.title")
        val STATUS_READY: TranslateComponent = create("gui", "resource.status.ready")
        val STATUS_MISSING: TranslateComponent = create("gui", "resource.status.missing")
        val STATUS_DOWNLOADING: TranslateComponent = create("gui", "resource.status.downloading")
        val BUTTON_DOWNLOAD: TranslateComponent = create("gui", "resource.button.download")
        val BUTTON_RETRY: TranslateComponent = create("gui", "resource.button.retry")
        val BUTTON_CANCEL: TranslateComponent = create("gui", "resource.button.cancel")
        val BUTTON_CLOSE: TranslateComponent = create("gui", "resource.button.close")
        val BUTTON_DONE: TranslateComponent = create("gui", "resource.button.done")
        val BUTTON_BACKGROUND: TranslateComponent = create("gui", "resource.button.background")
        val BUTTON_STOP: TranslateComponent = create("gui", "resource.button.stop")
        val HINT: TranslateComponent = create("gui", "resource.hint")
        val DOWNLOADING: TranslateComponent = create("gui", "resource.downloading")
        val JOB_PREPARING: TranslateComponent = create("gui", "resource.job.preparing")
        val JOB_DETAILS: TranslateComponent = create("gui", "resource.job.details")
        val FAILED: TranslateComponent = create("gui", "resource.failed")
        val DOWNLOAD_SUCCESS_TITLE: TranslateComponent = create("gui", "resource.success.title")
        val DOWNLOAD_SUCCESS_MESSAGE: TranslateComponent = create("gui", "resource.success.message")
        val DOWNLOAD_FAILED_TITLE: TranslateComponent = create("gui", "resource.failed.title")
        val CLEAR_CONFIRM_TITLE: TranslateComponent = create("gui", "resource.clear.title")
        val CLEAR_CONFIRM_MESSAGE: TranslateComponent = create("gui", "resource.clear.message")
        val CLEAR_CONFIRM_YES: TranslateComponent = create("gui", "resource.clear.yes")
        val CLEAR_CONFIRM_NO: TranslateComponent = create("gui", "resource.clear.no")
        val CLEAR_SUCCESS_TITLE: TranslateComponent = create("gui", "resource.cleared.title")
        val CLEAR_SUCCESS_MESSAGE: TranslateComponent = create("gui", "resource.cleared.message")
        val OPEN_FOLDER_FAILED: TranslateComponent = create("gui", "resource.open_folder_failed")
    }

    object PlatformOnly {
        val BADGE: TranslateComponent = create("gui", "platform.badge")
        val TITLE: TranslateComponent = create("gui", "platform.title")
        val FEATURE: TranslateComponent = create("gui", "platform.feature")
        val REQUIREMENT: TranslateComponent = create("gui", "platform.requirement")
        val CURRENT: TranslateComponent = create("gui", "platform.current")
        val HINT: TranslateComponent = create("gui", "platform.hint")
        val CONFIRM: TranslateComponent = create("gui", "platform.confirm")
    }

    object ElytraFly {
        val PITCH40_TAKEOFF_COMPLETE: TranslateComponent = create("modules.elytra fly", "pitch40_takeoff_complete")
        val PITCH40_TOO_CLOSE_TO_LOWER_BOUNDS: TranslateComponent =
            create("modules.elytra fly", "pitch40_too_close_to_lower_bounds")
        val PITCH40_NO_USABLE_ELYTRA: TranslateComponent = create("modules.elytra fly", "pitch40_no_usable_elytra")
    }

    object PlayerAlarms {
        val JOIN_ALERT_TEXT: TranslateComponent = create("modules.player alarms", "join_alert_text")
        val LEAVE_ALERT_TEXT: TranslateComponent = create("modules.player alarms", "leave_alert_text")
        val ENTER_RD_ALERT_TEXT: TranslateComponent = create("modules.player alarms", "enter_rd_alert_text")
        val LEAVE_RD_ALERT_TEXT: TranslateComponent = create("modules.player alarms", "leave_rd_alert_text")
        val GAMEMODE_ALERT_TEXT: TranslateComponent = create("modules.player alarms", "gamemode_alert_text")
        val GAMEMODE_SURVIVAL: TranslateComponent = create("modules.player alarms", "gamemode.survival")
        val GAMEMODE_CREATIVE: TranslateComponent = create("modules.player alarms", "gamemode.creative")
        val GAMEMODE_ADVENTURE: TranslateComponent = create("modules.player alarms", "gamemode.adventure")
        val GAMEMODE_SPECTATOR: TranslateComponent = create("modules.player alarms", "gamemode.spectator")
        val UNKNOWN_GAMEMODE: TranslateComponent = create("modules.player alarms", "unknown_gamemode")
    }

    object PlayerInfo {
        val PLAYERS: TranslateComponent = create("elements.player info", "players")
        val DISTANCE: TranslateComponent = create("elements.player info", "distance")
        val HEALTH: TranslateComponent = create("elements.player info", "health")
        val POPS: TranslateComponent = create("elements.player info", "pops")
        val DIRECTION: TranslateComponent = create("elements.player info", "direction")
        val EMPTY: TranslateComponent = create("elements.player info", "empty")
    }

    object Notifications {
        val PREVIEW_TITLE: TranslateComponent = create("elements.notifications", "preview.title")
        val PREVIEW_MESSAGE: TranslateComponent = create("elements.notifications", "preview.message")
        val NO_SLOWDOWN_DISABLED_WATER: TranslateComponent = create("notification", "no_slowdown_disabled_water")
        val NO_SLOWDOWN_DISABLED_FALLING: TranslateComponent = create("notification", "no_slowdown_disabled_falling")
        val NO_SLOWDOWN_DISABLED_PEARL: TranslateComponent = create("notification", "no_slowdown_disabled_pearl")
        val TRANSACTION_COUNT_TOO_HIGH: TranslateComponent = create("notification", "transaction_count_too_high")
        val SCAFFOLD_FLYING_WARNING: TranslateComponent = create("notification", "scaffold_flying_warning")
        val SCAFFOLD_TOGGLE_ON_TELEPORT: TranslateComponent = create("notification", "scaffold_toggle_on_teleport")
        val SERVERBOUND_PACKET_FLUSH_FAILED: TranslateComponent =
            create("notification", "serverbound_packet_flush_failed")
        val CLIENTBOUND_PACKET_FLUSH_FAILED: TranslateComponent =
            create("notification", "clientbound_packet_flush_failed")
        val KEY_FRIEND_ADDED: TranslateComponent = create("notification", "key_friend_added")
        val KEY_FRIEND_REMOVED: TranslateComponent = create("notification", "key_friend_removed")
    }

    object AutoMine {
        val PACKET_MINE_REQUIRED: TranslateComponent = create("modules.auto mine", "packet_mine_required")
        val PACKET_MINE_ENABLED: TranslateComponent = create("modules.auto mine", "packet_mine_enabled")
        val PACKET_MINE_DISABLED: TranslateComponent = create("modules.auto mine", "packet_mine_disabled")
    }

    object Via {
        val BASE_CANCEL: TranslateComponent = create("via", "base.cancel")
        val BASE_REFRESH: TranslateComponent = create("via", "base.refresh")
        val BASE_SOMETHING_WENT_WRONG: TranslateComponent = create("via", "base.something_went_wrong")
        val BASE_SET_VERSION: TranslateComponent = create("via", "base.set_version")
        val BASE_CANCEL_AND_RESET: TranslateComponent = create("via", "base.cancel_and_reset")
        val BASE_DETECTING_SERVER_VERSION: TranslateComponent = create("via", "base.detecting_server_version")
        val BASE_TARGET_VERSION: TranslateComponent = create("via", "base.target_version")
        val BASE_SERVER_VERSION: TranslateComponent = create("via", "base.server_version")
        val BASE_THIS_WILL_REQUIRE_A_RESTART: TranslateComponent = create("via", "base.this_will_require_a_restart")
        val SCREEN_FORCE_VERSION: TranslateComponent = create("via", "screen.force_version")
        val FORCE_VERSION_TITLE: TranslateComponent = create("via", "force_version.title")
        val JAVA_FAILED_TO_VERIFY_SESSION: TranslateComponent = create("via", "java.failed_to_verify_session")
        val PACKET_ERROR: TranslateComponent = create("via", "translation.packet_error")
    }

    fun component(translation: TranslateComponent, vararg args: Any?): MutableComponent {
        var text = translation.getTranslatedName()
        if (args.isNotEmpty()) {
            text = String.format(Locale.ROOT, text, *args)
        }
        return Component.literal(text)
    }

}

private fun create(prefix: String, suffix: String): TranslateComponent {
    return EpsilonTranslateComponent.create(prefix, suffix)
}
