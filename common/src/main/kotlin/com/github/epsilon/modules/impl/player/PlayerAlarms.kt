package com.github.epsilon.modules.impl.player

import com.github.epsilon.utils.*
import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.assets.i18n.TranslateComponent
import com.github.epsilon.elements.impl.notification.NotificationMode
import com.github.epsilon.events.impl.PacketEvent
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.managers.NotificationManager
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.impl.BoolSetting
import com.github.epsilon.settings.impl.DoubleSetting
import com.github.epsilon.settings.impl.EnumSetting
import com.github.epsilon.settings.impl.IntSetting
import com.github.epsilon.settings.impl.RegistryListSetting
import com.github.epsilon.settings.impl.StringListSetting
import com.github.epsilon.settings.getValue
import com.github.epsilon.utils.safety.*
import com.github.epsilon.utils.player.ChatUtils
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.GameType
import java.util.HashMap
import java.util.HashSet
import java.util.UUID

object PlayerAlarms : Module("Player Alarms", Category.PLAYER) {

    private val sgGeneral = settingGroup("General")
    private val sgJoin = settingGroup("Join")
    private val sgLeave = settingGroup("Leave")
    private val sgEnterRD = settingGroup("Enter Render Distance")
    private val sgLeaveRD = settingGroup("Leave Render Distance")
    private val sgGamemode = settingGroup("Gamemode Change")

    private enum class AlertMode {
        Chat,
        Notification,
        Both,
    }

    // General
    private val alertMode: EnumSetting<AlertMode> = enumSetting("Alert Mode", AlertMode.Chat).apply { group(sgGeneral) }
    private val showGamemodeInChat by boolSetting("Show Gamemode In Chat", false).apply { group(sgGeneral) }

    private val names by stringListSetting("Names", listOf("ssy_", "e_2", "山水圆")).apply { group(sgGeneral) }

    // Join Settings
    private val joinRings by intSetting("Join Rings", 5, 1, 10, 1).apply { group(sgJoin) }
    private val joinRingDelay by intSetting("Join Ring Delay", 20, 1, 100, 1).apply { group(sgJoin) }
    private val joinVolume by doubleSetting("Join Volume", 1.0, 0.0, 1.0, 0.05).apply { group(sgJoin) }
    private val joinPitch by doubleSetting("Join Pitch", 1.0, 0.5, 2.0, 0.05).apply { group(sgJoin) }
    private val joinSound: RegistryListSetting<SoundEvent> = soundEventListSetting("Join Sound", listOf(SoundEvents.BELL_BLOCK)).apply { group(sgJoin) }
    private val joinChatMessage by boolSetting("Join Chat Message", true).apply { group(sgJoin) }

    // Leave Settings
    private val leaveRings by intSetting("Leave Rings", 3, 1, 10, 1).apply { group(sgLeave) }
    private val leaveRingDelay by intSetting("Leave Ring Delay", 20, 1, 100, 1).apply { group(sgLeave) }
    private val leaveVolume by doubleSetting("Leave Volume", 1.0, 0.0, 1.0, 0.05).apply { group(sgLeave) }
    private val leavePitch by doubleSetting("Leave Pitch", 1.0, 0.5, 2.0, 0.05).apply { group(sgLeave) }
    private val leaveSound: RegistryListSetting<SoundEvent> = soundEventListSetting("Leave Sound", listOf(SoundEvents.ANVIL_LAND)).apply { group(sgLeave) }
    private val leaveChatMessage by boolSetting("Leave Chat Message", true).apply { group(sgLeave) }

    // Enter RD Settings
    private val enterRDRings by intSetting("Enter Render Distance Rings", 2, 1, 10, 1).apply { group(sgEnterRD) }
    private val enterRDRingDelay by intSetting("Enter Render Distance Ring Delay", 20, 1, 100, 1).apply { group(sgEnterRD) }
    private val enterRDVolume by doubleSetting("Enter Render Distance Volume", 1.0, 0.0, 1.0, 0.05).apply { group(sgEnterRD) }
    private val enterRDPitch by doubleSetting("Enter Render Distance Pitch", 1.0, 0.5, 2.0, 0.05).apply { group(sgEnterRD) }
    private val enterRDSound: RegistryListSetting<SoundEvent> = soundEventListSetting("Enter Render Distance Sound", listOf(SoundEvents.ANVIL_DESTROY)).apply { group(sgEnterRD) }
    private val enterRDChatMessage by boolSetting("Enter Render Distance Chat Message", true).apply { group(sgEnterRD) }

    // Leave RD Settings
    private val leaveRDRings by intSetting("Leave Render Distance Rings", 2, 1, 10, 1).apply { group(sgLeaveRD) }
    private val leaveRDRingDelay by intSetting("Leave Render Distance Ring Delay", 20, 1, 100, 1).apply { group(sgLeaveRD) }
    private val leaveRDVolume by doubleSetting("Leave Render Distance Volume", 1.0, 0.0, 1.0, 0.05).apply { group(sgLeaveRD) }
    private val leaveRDPitch by doubleSetting("Leave Render Distance Pitch", 1.0, 0.5, 2.0, 0.05).apply { group(sgLeaveRD) }
    private val leaveRDSound: RegistryListSetting<SoundEvent> = soundEventListSetting("Leave Render Distance Sound", listOf(SoundEvents.BELL_BLOCK)).apply { group(sgLeaveRD) }
    private val leaveRDChatMessage by boolSetting("Leave Render Distance Chat Message", true).apply { group(sgLeaveRD) }

    // Gamemode Change Settings
    private val gamemodeRings by intSetting("Gamemode Rings", 3, 1, 10, 1).apply { group(sgGamemode) }
    private val gamemodeRingDelay by intSetting("Gamemode Ring Delay", 20, 1, 100, 1).apply { group(sgGamemode) }
    private val gamemodeVolume by doubleSetting("Gamemode Volume", 1.0, 0.0, 1.0, 0.05).apply { group(sgGamemode) }
    private val gamemodePitch by doubleSetting("Gamemode Pitch", 1.0, 0.5, 2.0, 0.05).apply { group(sgGamemode) }
    private val gamemodeSound: RegistryListSetting<SoundEvent> = soundEventListSetting("Gamemode Sound", listOf(SoundEvents.ARROW_HIT_PLAYER)).apply { group(sgGamemode) }
    private val gamemodeChatMessage by boolSetting("Gamemode Chat Message", true).apply { group(sgGamemode) }

    private val playersInRender: MutableSet<UUID> = HashSet()
    private val gamemodeCache: MutableMap<UUID, GameType> = HashMap()
    private val alarmedJoinPlayers: MutableSet<UUID> = HashSet()
    private var lastConnection: Any? = null
    private var lastLevel: Any? = null

    private class RingState {
        var ticks = 0
        var ringsLeft = 0
        var active = false
    }

    private val joinRing = RingState()
    private val leaveRing = RingState()
    private val enterRDRing = RingState()
    private val leaveRDRing = RingState()
    private val gamemodeRing = RingState()

    init {
        nonNullListener<PlayerTickEvent.Pre> {
            // Handle all rings
            handleRing(joinRing) { playSound(joinVolume, joinPitch, joinSound.value) }
            handleRing(leaveRing) { playSound(leaveVolume, leavePitch, leaveSound.value) }
            handleRing(enterRDRing) { playSound(enterRDVolume, enterRDPitch, enterRDSound.value) }
            handleRing(leaveRDRing) { playSound(leaveRDVolume, leaveRDPitch, leaveRDSound.value) }
            handleRing(gamemodeRing) { playSound(gamemodeVolume, gamemodePitch, gamemodeSound.value) }

            // Detect world/server changes and re-scan online target players
            val currentConnection: Any? = connection
            val currentLevel: Any? = level
            val reconnected = currentConnection !== lastConnection
            val worldChanged = currentLevel !== lastLevel
            if (reconnected || worldChanged) {
                if (reconnected) lastConnection = currentConnection
                if (worldChanged) lastLevel = currentLevel
                alarmedJoinPlayers.clear()
                for (entry in connection.listedOnlinePlayers) {
                    val id = entry.profile.id()
                    val playerName = entry.profile.name()
                    if (shouldAlarm(playerName)) {
                        startRing(joinRing, joinRings, joinRingDelay)
                        sendAlert(joinChatMessage, EpsilonTranslations.PlayerAlarms.JOIN_ALERT_TEXT, playerName, id, ChatFormatting.RED)
                        alarmedJoinPlayers.add(id)
                    }
                }
            }

            // Render distance detection
            val currentInRender: MutableSet<UUID> = HashSet()
            for (entity in level.entitiesForRendering()) {
                if (entity is Player && entity !== player) {
                    currentInRender.add(entity.uuid)
                }
            }

            // Enter RD
            for (id in currentInRender) {
                if (!playersInRender.contains(id)) {
                    val playerName = getPlayerName(id)
                    if (playerName != null && shouldAlarm(playerName)) {
                        startRing(enterRDRing, enterRDRings, enterRDRingDelay)
                        sendAlert(enterRDChatMessage, EpsilonTranslations.PlayerAlarms.ENTER_RD_ALERT_TEXT, playerName, id, ChatFormatting.DARK_RED)
                    }
                }
            }

            // Leave RD
            for (id in playersInRender) {
                if (!currentInRender.contains(id)) {
                    val playerName = getPlayerName(id)
                    if (playerName != null && shouldAlarm(playerName)) {
                        startRing(leaveRDRing, leaveRDRings, leaveRDRingDelay)
                        sendAlert(leaveRDChatMessage, EpsilonTranslations.PlayerAlarms.LEAVE_RD_ALERT_TEXT, playerName, id, ChatFormatting.DARK_GREEN)
                    }
                }
            }

            playersInRender.clear()
            playersInRender.addAll(currentInRender)
        }

        nonNullListener<PacketEvent.Receive> { event ->
            val packet = event.packet

            // Player Join
            if (packet is ClientboundPlayerInfoUpdatePacket) {
                if (packet.actions().contains(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER)) {
                    for (entry in packet.entries()) {
                        val profile = entry.profile() ?: continue
                        val playerName = profile.name()
                        gamemodeCache[entry.profileId()] = entry.gameMode()
                        if (!alarmedJoinPlayers.contains(entry.profileId()) && shouldAlarm(playerName)) {
                            startRing(joinRing, joinRings, joinRingDelay)
                            sendAlert(joinChatMessage, EpsilonTranslations.PlayerAlarms.JOIN_ALERT_TEXT, playerName, entry.profileId(), ChatFormatting.RED)
                            alarmedJoinPlayers.add(entry.profileId())
                        }
                    }
                }

                // Gamemode Change
                if (packet.actions().contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE)) {
                    for (entry in packet.entries()) {
                        val id = entry.profileId()
                        val newMode = entry.gameMode()

                        val playerInfo = connection.getPlayerInfo(id) ?: continue

                        val oldMode = playerInfo.gameMode
                        if (oldMode != newMode) {
                            val playerName = getPlayerName(id)
                            if (playerName != null && shouldAlarm(playerName)) {
                                startRing(gamemodeRing, gamemodeRings, gamemodeRingDelay)
                                if (gamemodeChatMessage) {
                                    val msg = EpsilonTranslations.PlayerAlarms.GAMEMODE_ALERT_TEXT.getTranslatedName()
                                        .replace("{name}", playerName)
                                        .replace("{old_gamemode}", translateGamemode(oldMode))
                                        .replace("{new_gamemode}", translateGamemode(newMode))
                                    val mode = alertMode.value
                                    if (mode == AlertMode.Chat || mode == AlertMode.Both) {
                                        addChatMessage(Component.literal(msg).withStyle(ChatFormatting.YELLOW))
                                    }
                                    if (mode == AlertMode.Notification || mode == AlertMode.Both) {
                                        val hash = java.util.Objects.hash(playerName, "gamemode")
                                        NotificationManager.notifyHud(msg, "", NotificationMode.Info, hash)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Player Leave
            if (packet is ClientboundPlayerInfoRemovePacket) {
                for (id in packet.profileIds()) {
                    val playerName = getPlayerName(id) ?: id.toString()
                    if (shouldAlarm(playerName)) {
                        startRing(leaveRing, leaveRings, leaveRingDelay)
                        sendAlert(leaveChatMessage, EpsilonTranslations.PlayerAlarms.LEAVE_ALERT_TEXT, playerName, id, ChatFormatting.GREEN)
                    }
                    gamemodeCache.remove(id)
                    playersInRender.remove(id)
                    alarmedJoinPlayers.remove(id)
                }
            }
        }
    }

    override fun onEnable() {
        playersInRender.clear()
        gamemodeCache.clear()
        alarmedJoinPlayers.clear()
        lastConnection = null
        lastLevel = null
        resetRingState(joinRing)
        resetRingState(leaveRing)
        resetRingState(enterRDRing)
        resetRingState(leaveRDRing)
        resetRingState(gamemodeRing)
    }

    private fun resetRingState(rs: RingState) {
        rs.ticks = 0
        rs.ringsLeft = 0
        rs.active = false
    }

    private fun shouldAlarm(playerName: String): Boolean {
        val nameList = names
        if (nameList.isEmpty()) return false
        return nameList.any { it.equals(playerName, ignoreCase = true) }
    }

    private fun startRing(rs: RingState, rings: Int, delay: Int) {
        rs.ringsLeft = rings
        rs.ticks = 0
        rs.active = true
    }

    private fun handleRing(rs: RingState, play: Runnable) {
        if (!rs.active || rs.ringsLeft <= 0) {
            rs.active = false
            return
        }
        if (rs.ticks <= 0) {
            play.run()
            rs.ticks = if (rs.ringsLeft == 1) 0 else getRingDelay(rs)
            rs.ringsLeft--
            if (rs.ringsLeft <= 0) rs.active = false
        } else {
            rs.ticks--
        }
    }

    private fun getRingDelay(rs: RingState): Int {
        if (rs === joinRing) return joinRingDelay
        if (rs === leaveRing) return leaveRingDelay
        if (rs === enterRDRing) return enterRDRingDelay
        if (rs === leaveRDRing) return leaveRDRingDelay
        if (rs === gamemodeRing) return gamemodeRingDelay
        return 20
    }

    private fun sendAlert(enabled: Boolean, template: TranslateComponent, playerName: String, playerId: UUID?, color: ChatFormatting) {
        if (!enabled) return
        val mode = alertMode.value
        // Build full message
        var msg = template.getTranslatedName().replace("{name}", playerName)
        if (showGamemodeInChat && playerId != null) {
            msg = msg.replace("{gamemode}", getGamemodeName(playerId))
        } else {
            msg = msg.replace("{gamemode}", "")
        }
        msg = msg.replace("{old_gamemode}", "").replace("{new_gamemode}", "").trim()
        // Chat
        if (mode == AlertMode.Chat || mode == AlertMode.Both) {
            ChatUtils.addChatMessage(Component.literal(msg).withStyle(color))
        }
        // HUD Notification (no chat side-effect)
        if ((mode == AlertMode.Notification || mode == AlertMode.Both) && playerId != null) {
            val hash = java.util.Objects.hash(playerName, template.getTranslatedName())
            val notifMode = if (color == ChatFormatting.RED || color == ChatFormatting.DARK_RED) {
                NotificationMode.Error
            } else {
                NotificationMode.Success
            }
            NotificationManager.notifyHud(msg, "", notifMode, hash)
        }
    }

    private fun playSound(vol: Double, pitch: Double, sounds: List<SoundEvent>) {
        val nn = NonNullContext.current ?: return
        if (sounds.isEmpty()) return
        val sound = sounds[0]
        nn.level.playLocalSound(nn.player.blockPosition(), sound, SoundSource.PLAYERS, vol.toFloat(), pitch.toFloat(), false)
    }

    private fun getPlayerName(id: UUID): String? {
        val nn = NonNullContext.current ?: return null
        val player = nn.level.getPlayerByUUID(id)
        if (player != null) return player.gameProfile.name()
        // fallback: try to get from network player list
        val entry = nn.connection.getPlayerInfo(id)
        if (entry != null) {
            val profile = entry.profile ?: return null
            return profile.name()
        }
        return null
    }

    private fun getGamemodeName(id: UUID): String {
        val cached = gamemodeCache[id]
        if (cached != null) return translateGamemode(cached)
        // fallback: try to get from network info
        val nn = NonNullContext.current
        if (nn != null) {
            val info = nn.connection.getPlayerInfo(id)
            if (info != null) {
                val mode = info.gameMode
                if (mode != null) return translateGamemode(mode)
            }
        }
        return EpsilonTranslations.PlayerAlarms.UNKNOWN_GAMEMODE.getTranslatedName()
    }

    private fun translateGamemode(gameType: GameType): String {
        if (gameType == GameType.SURVIVAL) {
            return EpsilonTranslations.PlayerAlarms.GAMEMODE_SURVIVAL.getTranslatedName()
        }
        if (gameType == GameType.CREATIVE) {
            return EpsilonTranslations.PlayerAlarms.GAMEMODE_CREATIVE.getTranslatedName()
        }
        if (gameType == GameType.ADVENTURE) {
            return EpsilonTranslations.PlayerAlarms.GAMEMODE_ADVENTURE.getTranslatedName()
        }
        if (gameType == GameType.SPECTATOR) {
            return EpsilonTranslations.PlayerAlarms.GAMEMODE_SPECTATOR.getTranslatedName()
        }
        return gameType.getName()
    }

}
