package com.github.epsilon.modules.impl.player;

import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.assets.i18n.TranslateComponent;
import com.github.epsilon.elements.impl.notification.NotificationMode;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.SettingGroup;
import com.github.epsilon.settings.impl.*;
import com.github.epsilon.utils.player.ChatUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Entry;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;

import java.util.*;

public class PlayerAlarms extends Module {

    public static final PlayerAlarms INSTANCE = new PlayerAlarms();

    private PlayerAlarms() {
        super("Player Alarms", Category.PLAYER);
    }

    private final SettingGroup sgGeneral = settingGroup("General");
    private final SettingGroup sgJoin = settingGroup("Join");
    private final SettingGroup sgLeave = settingGroup("Leave");
    private final SettingGroup sgEnterRD = settingGroup("Enter Render Distance");
    private final SettingGroup sgLeaveRD = settingGroup("Leave Render Distance");
    private final SettingGroup sgGamemode = settingGroup("Gamemode Change");

    private enum AlertMode {
        Chat,
        Notification,
        Both
    }

    // General
    private final EnumSetting<AlertMode> alertMode = enumSetting("Alert Mode", AlertMode.Chat).group(sgGeneral);
    private final BoolSetting showGamemodeInChat = boolSetting("Show Gamemode In Chat", false).group(sgGeneral);

    private final StringListSetting names = stringListSetting("Names",
            List.of("ssy_", "e_2", "山水圆")).group(sgGeneral);

    // Join Settings
    private final IntSetting joinRings = intSetting("Join Rings", 5, 1, 10, 1).group(sgJoin);
    private final IntSetting joinRingDelay = intSetting("Join Ring Delay", 20, 1, 100, 1).group(sgJoin);
    private final DoubleSetting joinVolume = doubleSetting("Join Volume", 1.0, 0.0, 1.0, 0.05).group(sgJoin);
    private final DoubleSetting joinPitch = doubleSetting("Join Pitch", 1.0, 0.5, 2.0, 0.05).group(sgJoin);
    private final RegistryListSetting<SoundEvent> joinSound = soundEventListSetting("Join Sound",
            List.of(SoundEvents.BELL_BLOCK)).group(sgJoin);
    private final BoolSetting joinChatMessage = boolSetting("Join Chat Message", true).group(sgJoin);

    // Leave Settings
    private final IntSetting leaveRings = intSetting("Leave Rings", 3, 1, 10, 1).group(sgLeave);
    private final IntSetting leaveRingDelay = intSetting("Leave Ring Delay", 20, 1, 100, 1).group(sgLeave);
    private final DoubleSetting leaveVolume = doubleSetting("Leave Volume", 1.0, 0.0, 1.0, 0.05).group(sgLeave);
    private final DoubleSetting leavePitch = doubleSetting("Leave Pitch", 1.0, 0.5, 2.0, 0.05).group(sgLeave);
    private final RegistryListSetting<SoundEvent> leaveSound = soundEventListSetting("Leave Sound",
            List.of(SoundEvents.ANVIL_LAND)).group(sgLeave);
    private final BoolSetting leaveChatMessage = boolSetting("Leave Chat Message", true).group(sgLeave);

    // Enter RD Settings
    private final IntSetting enterRDRings = intSetting("Enter Render Distance Rings", 2, 1, 10, 1).group(sgEnterRD);
    private final IntSetting enterRDRingDelay = intSetting("Enter Render Distance Ring Delay", 20, 1, 100, 1).group(sgEnterRD);
    private final DoubleSetting enterRDVolume = doubleSetting("Enter Render Distance Volume", 1.0, 0.0, 1.0, 0.05).group(sgEnterRD);
    private final DoubleSetting enterRDPitch = doubleSetting("Enter Render Distance Pitch", 1.0, 0.5, 2.0, 0.05).group(sgEnterRD);
    private final RegistryListSetting<SoundEvent> enterRDSound = soundEventListSetting("Enter Render Distance Sound",
            List.of(SoundEvents.ANVIL_DESTROY)).group(sgEnterRD);
    private final BoolSetting enterRDChatMessage = boolSetting("Enter Render Distance Chat Message", true).group(sgEnterRD);

    // Leave RD Settings
    private final IntSetting leaveRDRings = intSetting("Leave Render Distance Rings", 2, 1, 10, 1).group(sgLeaveRD);
    private final IntSetting leaveRDRingDelay = intSetting("Leave Render Distance Ring Delay", 20, 1, 100, 1).group(sgLeaveRD);
    private final DoubleSetting leaveRDVolume = doubleSetting("Leave Render Distance Volume", 1.0, 0.0, 1.0, 0.05).group(sgLeaveRD);
    private final DoubleSetting leaveRDPitch = doubleSetting("Leave Render Distance Pitch", 1.0, 0.5, 2.0, 0.05).group(sgLeaveRD);
    private final RegistryListSetting<SoundEvent> leaveRDSound = soundEventListSetting("Leave Render Distance Sound",
            List.of(SoundEvents.BELL_BLOCK)).group(sgLeaveRD);
    private final BoolSetting leaveRDChatMessage = boolSetting("Leave Render Distance Chat Message", true).group(sgLeaveRD);

    // Gamemode Change Settings
    private final IntSetting gamemodeRings = intSetting("Gamemode Rings", 3, 1, 10, 1).group(sgGamemode);
    private final IntSetting gamemodeRingDelay = intSetting("Gamemode Ring Delay", 20, 1, 100, 1).group(sgGamemode);
    private final DoubleSetting gamemodeVolume = doubleSetting("Gamemode Volume", 1.0, 0.0, 1.0, 0.05).group(sgGamemode);
    private final DoubleSetting gamemodePitch = doubleSetting("Gamemode Pitch", 1.0, 0.5, 2.0, 0.05).group(sgGamemode);
    private final RegistryListSetting<SoundEvent> gamemodeSound = soundEventListSetting("Gamemode Sound",
            List.of(SoundEvents.ARROW_HIT_PLAYER)).group(sgGamemode);
    private final BoolSetting gamemodeChatMessage = boolSetting("Gamemode Chat Message", true).group(sgGamemode);

    private final Set<UUID> playersInRender = new HashSet<>();
    private final Map<UUID, GameType> gamemodeCache = new HashMap<>();
    private final Set<UUID> alarmedJoinPlayers = new HashSet<>();
    private Object lastConnection = null;
    private Object lastLevel = null;

    private static class RingState {
        int ticks;
        int ringsLeft;
        boolean active;
    }

    private final RingState joinRing = new RingState();
    private final RingState leaveRing = new RingState();
    private final RingState enterRDRing = new RingState();
    private final RingState leaveRDRing = new RingState();
    private final RingState gamemodeRing = new RingState();

    @Override
    protected void onEnable() {
        playersInRender.clear();
        gamemodeCache.clear();
        alarmedJoinPlayers.clear();
        lastConnection = null;
        lastLevel = null;
        resetRingState(joinRing);
        resetRingState(leaveRing);
        resetRingState(enterRDRing);
        resetRingState(leaveRDRing);
        resetRingState(gamemodeRing);
    }

    private void resetRingState(RingState rs) {
        rs.ticks = 0;
        rs.ringsLeft = 0;
        rs.active = false;
    }

    @EventHandler
    private void onTick(PlayerTickEvent.Pre event) {
        if (nullCheck()) return;

        // Handle all rings
        handleRing(joinRing, () -> playSound(joinVolume.getValue(), joinPitch.getValue(), joinSound.getValue()));
        handleRing(leaveRing, () -> playSound(leaveVolume.getValue(), leavePitch.getValue(), leaveSound.getValue()));
        handleRing(enterRDRing, () -> playSound(enterRDVolume.getValue(), enterRDPitch.getValue(), enterRDSound.getValue()));
        handleRing(leaveRDRing, () -> playSound(leaveRDVolume.getValue(), leaveRDPitch.getValue(), leaveRDSound.getValue()));
        handleRing(gamemodeRing, () -> playSound(gamemodeVolume.getValue(), gamemodePitch.getValue(), gamemodeSound.getValue()));

        // Detect world/server changes and re-scan online target players
        Object currentConnection = mc.getConnection();
        Object currentLevel = mc.level;
        boolean reconnected = currentConnection != null && currentConnection != lastConnection;
        boolean worldChanged = currentLevel != null && currentLevel != lastLevel;
        if (currentConnection == null) {
            lastConnection = null;
            lastLevel = null;
        } else if (reconnected || worldChanged) {
            if (reconnected) lastConnection = currentConnection;
            if (worldChanged) lastLevel = currentLevel;
            alarmedJoinPlayers.clear();
            for (var entry : mc.getConnection().getListedOnlinePlayers()) {
                UUID id = entry.getProfile().id();
                String playerName = entry.getProfile().name();
                if (shouldAlarm(playerName)) {
                    startRing(joinRing, joinRings.getValue(), joinRingDelay.getValue());
                    sendAlert(joinChatMessage.getValue(), EpsilonTranslations.PlayerAlarms.JOIN_ALERT_TEXT, playerName, id, ChatFormatting.RED);
                    alarmedJoinPlayers.add(id);
                }
            }
        }

        // Render distance detection
        Set<UUID> currentInRender = new HashSet<>();
        for (var entity : mc.level.entitiesForRendering()) {
            if (entity instanceof Player && entity != mc.player) {
                currentInRender.add(entity.getUUID());
            }
        }

        // Enter RD
        for (UUID id : currentInRender) {
            if (!playersInRender.contains(id)) {
                String playerName = getPlayerName(id);
                if (playerName != null && shouldAlarm(playerName)) {
                    startRing(enterRDRing, enterRDRings.getValue(), enterRDRingDelay.getValue());
                    sendAlert(enterRDChatMessage.getValue(), EpsilonTranslations.PlayerAlarms.ENTER_RD_ALERT_TEXT, playerName, id, ChatFormatting.DARK_RED);
                }
            }
        }

        // Leave RD
        for (UUID id : playersInRender) {
            if (!currentInRender.contains(id)) {
                String playerName = getPlayerName(id);
                if (playerName != null && shouldAlarm(playerName)) {
                    startRing(leaveRDRing, leaveRDRings.getValue(), leaveRDRingDelay.getValue());
                    sendAlert(leaveRDChatMessage.getValue(), EpsilonTranslations.PlayerAlarms.LEAVE_RD_ALERT_TEXT, playerName, id, ChatFormatting.DARK_GREEN);
                }
            }
        }

        playersInRender.clear();
        playersInRender.addAll(currentInRender);
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (nullCheck()) return;

        // Player Join
        if (event.getPacket() instanceof ClientboundPlayerInfoUpdatePacket packet) {
            if (packet.actions().contains(Action.ADD_PLAYER)) {
                for (Entry entry : packet.entries()) {
                    String playerName = entry.profile().name();
                    gamemodeCache.put(entry.profileId(), entry.gameMode());
                    if (!alarmedJoinPlayers.contains(entry.profileId()) && shouldAlarm(playerName)) {
                        startRing(joinRing, joinRings.getValue(), joinRingDelay.getValue());
                        sendAlert(joinChatMessage.getValue(), EpsilonTranslations.PlayerAlarms.JOIN_ALERT_TEXT, playerName, entry.profileId(), ChatFormatting.RED);
                        alarmedJoinPlayers.add(entry.profileId());
                    }
                }
            }

            // Gamemode Change
            if (packet.actions().contains(Action.UPDATE_GAME_MODE)) {
                for (Entry entry : packet.entries()) {
                    UUID id = entry.profileId();
                    GameType newMode = entry.gameMode();

                    var playerInfo = mc.getConnection().getPlayerInfo(id);
                    if (playerInfo == null) continue;

                    GameType oldMode = playerInfo.getGameMode();
                    if (oldMode != newMode) {
                        String playerName = getPlayerName(id);
                        if (playerName != null && shouldAlarm(playerName)) {
                            startRing(gamemodeRing, gamemodeRings.getValue(), gamemodeRingDelay.getValue());
                            if (gamemodeChatMessage.getValue()) {
                                String msg = EpsilonTranslations.PlayerAlarms.GAMEMODE_ALERT_TEXT.getTranslatedName()
                                        .replace("{name}", playerName)
                                        .replace("{old_gamemode}", translateGamemode(oldMode))
                                        .replace("{new_gamemode}", translateGamemode(newMode));
                                AlertMode mode = alertMode.getValue();
                                if (mode == AlertMode.Chat || mode == AlertMode.Both) {
                                    ChatUtils.addChatMessage(Component.literal(msg).withStyle(ChatFormatting.YELLOW));
                                }
                                if (mode == AlertMode.Notification || mode == AlertMode.Both) {
                                    int hash = java.util.Objects.hash(playerName, "gamemode");
                                    Managers.NOTIFICATION.notifyHud(msg, "", NotificationMode.Info, hash);
                                }
                            }
                        }
                    }
                }
            }
        }

        // Player Leave
        if (event.getPacket() instanceof ClientboundPlayerInfoRemovePacket removePacket) {
            for (UUID id : removePacket.profileIds()) {
                String playerName = getPlayerName(id);
                if (playerName == null) playerName = id.toString();
                if (shouldAlarm(playerName)) {
                    startRing(leaveRing, leaveRings.getValue(), leaveRingDelay.getValue());
                    sendAlert(leaveChatMessage.getValue(), EpsilonTranslations.PlayerAlarms.LEAVE_ALERT_TEXT, playerName, id, ChatFormatting.GREEN);
                }
                gamemodeCache.remove(id);
                playersInRender.remove(id);
                alarmedJoinPlayers.remove(id);
            }
        }
    }

    private boolean shouldAlarm(String playerName) {
        if (playerName == null) return false;
        List<String> nameList = names.getValue();
        if (nameList.isEmpty()) return false;
        return nameList.stream().anyMatch(n -> n.equalsIgnoreCase(playerName));
    }

    private void startRing(RingState rs, int rings, int delay) {
        rs.ringsLeft = rings;
        rs.ticks = 0;
        rs.active = true;
    }

    private void handleRing(RingState rs, Runnable play) {
        if (!rs.active || rs.ringsLeft <= 0) {
            rs.active = false;
            return;
        }
        if (rs.ticks <= 0) {
            play.run();
            rs.ticks = (rs.ringsLeft == 1) ? 0 : getRingDelay(rs);
            rs.ringsLeft--;
            if (rs.ringsLeft <= 0) rs.active = false;
        } else {
            rs.ticks--;
        }
    }

    private int getRingDelay(RingState rs) {
        if (rs == joinRing) return joinRingDelay.getValue();
        if (rs == leaveRing) return leaveRingDelay.getValue();
        if (rs == enterRDRing) return enterRDRingDelay.getValue();
        if (rs == leaveRDRing) return leaveRDRingDelay.getValue();
        if (rs == gamemodeRing) return gamemodeRingDelay.getValue();
        return 20;
    }

    private void sendAlert(boolean enabled, TranslateComponent template, String playerName, UUID playerId, ChatFormatting color) {
        if (!enabled) return;
        AlertMode mode = alertMode.getValue();
        // Build full message
        String msg = template.getTranslatedName().replace("{name}", playerName);
        if (showGamemodeInChat.getValue() && playerId != null) {
            msg = msg.replace("{gamemode}", getGamemodeName(playerId));
        } else {
            msg = msg.replace("{gamemode}", "");
        }
        msg = msg.replace("{old_gamemode}", "").replace("{new_gamemode}", "").trim();
        // Chat
        if (mode == AlertMode.Chat || mode == AlertMode.Both) {
            ChatUtils.addChatMessage(Component.literal(msg).withStyle(color));
        }
        // HUD Notification (no chat side-effect)
        if ((mode == AlertMode.Notification || mode == AlertMode.Both) && playerId != null) {
            int hash = java.util.Objects.hash(playerName, template.getTranslatedName());
            NotificationMode notifMode = color == ChatFormatting.RED || color == ChatFormatting.DARK_RED
                    ? NotificationMode.Error : NotificationMode.Success;
            Managers.NOTIFICATION.notifyHud(msg, "", notifMode, hash);
        }
    }

    private void playSound(double vol, double pitch, List<SoundEvent> sounds) {
        if (mc.player == null || mc.level == null || sounds.isEmpty()) return;
        SoundEvent sound = sounds.get(0);
        mc.level.playLocalSound(mc.player.blockPosition(), sound, SoundSource.PLAYERS, (float) vol, (float) pitch, false);
    }

    private String getPlayerName(UUID id) {
        if (mc.level == null) return null;
        Player player = mc.level.getPlayerByUUID(id);
        if (player != null) return player.getGameProfile().name();
        // fallback: try to get from network player list
        if (mc.getConnection() != null) {
            var entry = mc.getConnection().getPlayerInfo(id);
            if (entry != null && entry.getProfile() != null) return entry.getProfile().name();
        }
        return null;
    }

    private String getGamemodeName(UUID id) {
        GameType mode = gamemodeCache.get(id);
        if (mode != null) return translateGamemode(mode);
        // fallback: try to get from network info
        if (mc.getConnection() != null) {
            var info = mc.getConnection().getPlayerInfo(id);
            if (info != null) {
                mode = info.getGameMode();
                if (mode != null) return translateGamemode(mode);
            }
        }
        return EpsilonTranslations.PlayerAlarms.UNKNOWN_GAMEMODE.getTranslatedName();
    }

    private static String translateGamemode(GameType gameType) {
        if (gameType == GameType.SURVIVAL)
            return EpsilonTranslations.PlayerAlarms.GAMEMODE_SURVIVAL.getTranslatedName();
        if (gameType == GameType.CREATIVE)
            return EpsilonTranslations.PlayerAlarms.GAMEMODE_CREATIVE.getTranslatedName();
        if (gameType == GameType.ADVENTURE)
            return EpsilonTranslations.PlayerAlarms.GAMEMODE_ADVENTURE.getTranslatedName();
        if (gameType == GameType.SPECTATOR)
            return EpsilonTranslations.PlayerAlarms.GAMEMODE_SPECTATOR.getTranslatedName();
        return gameType.getName();
    }

}
