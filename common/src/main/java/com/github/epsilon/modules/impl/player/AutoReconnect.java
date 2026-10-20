package com.github.epsilon.modules.impl.player;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.*;
import com.github.epsilon.features.*;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.IntSetting;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

public final class AutoReconnect extends Module {
    public static final AutoReconnect INSTANCE = new AutoReconnect();
    private final IntSetting delay = intSetting("Delay Seconds", 15, 5, 300, 1);
    private final IntSetting maxAttempts = intSetting("Maximum Attempts", 3, 1, 20, 1);
    private ServerData lastServer;
    private Screen pendingScreen;
    private long due;
    private int attempts;
    private AutoReconnect() { super("Auto Reconnect", Category.PLAYER); buttonSetting("Cancel Reconnect", this::cancel); }
    public void cancel() { pendingScreen = null; due = 0; }
    @EventHandler private void joined(GameJoinedEvent e) {
        DisconnectGuard.joined(); attempts = 0; cancel();
        lastServer = mc.getSingleplayerServer() == null ? mc.getCurrentServer() : null;
    }
    @EventHandler private void screen(OpenScreenEvent e) {
        if (!(e.getScreen() instanceof DisconnectedScreen)) { cancel(); return; }
        if (!DisconnectGuard.unexpected() || lastServer == null || attempts >= maxAttempts.getValue()) return;
        pendingScreen = e.getScreen(); due = System.currentTimeMillis() + Math.max(5, delay.getValue()) * 1000L;
    }
    @EventHandler private void tick(ClientTickEvent.Post e) {
        if (!nullCheck()) lastServer = mc.getSingleplayerServer() == null ? mc.getCurrentServer() : null;
        if (pendingScreen == null) return;
        if (mc.gui.screen() != pendingScreen || mc.level != null) { cancel(); return; }
        if (System.currentTimeMillis() < due) return;
        Screen parent = pendingScreen; cancel(); attempts++;
        ConnectScreen.startConnecting(parent, mc, ServerAddress.parseString(lastServer.ip), lastServer, false, null);
    }
    @Override protected void onDisable() { cancel(); lastServer = null; attempts = 0; }
    @Override public String getInfo() { return pendingScreen == null ? null : Math.max(0, (due - System.currentTimeMillis()) / 1000) + "s"; }
}
