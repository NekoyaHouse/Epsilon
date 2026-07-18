package com.github.epsilon.modules.impl.movement;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.events.impl.SendPositionEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.utils.network.PacketUtils;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.gui.screens.RecoverWorldDataScreen;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.world.entity.Entity;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Blink extends Module {

    public static final Blink INSTANCE = new Blink();

    public Blink() {
        super("Blink", Category.MOVEMENT);
    }

    private final DoubleSetting tick = doubleSetting("Tick", 30, 5, 200, 1);
    private final BoolSetting SlowRelease = boolSetting("Slow Release", true);
    private final BoolSetting slowMove = boolSetting("Slow Move", false);
    private final DoubleSetting slowMoveTick = doubleSetting("Slow Move Tick", 5, 2, 5, 1, slowMove::getValue);
    private final BoolSetting fakePlayer = boolSetting("Fake Player", false);

    private final ConcurrentLinkedQueue<Packet<?>> packets = new ConcurrentLinkedQueue<>();
    public RemotePlayer localPlayer;

    private double serverX, serverY, serverZ;
    private double prevServerX, prevServerY, prevServerZ;
    private float serverYRot, serverXRot, serverYHeadRot;
    private float prevServerYRot, prevServerXRot, prevServerYHeadRot;
    private double lastLerpX, lastLerpY, lastLerpZ;

    @EventHandler
    public void onHigherPacketSend(PacketEvent.Send e) {
        if (nullCheck()) return;
        Packet<?> packet = e.getPacket();
        if (packet instanceof ServerboundHelloPacket || packet instanceof ClientIntentionPacket) return;
        e.cancel();
        packets.add(packet);
    }

    @Override
    public void onEnable() {
        if (nullCheck()) return;
        serverX = prevServerX = mc.player.getX();
        serverY = prevServerY = mc.player.getY();
        serverZ = prevServerZ = mc.player.getZ();
        serverYRot = prevServerYRot = mc.player.getYRot();
        serverXRot = prevServerXRot = mc.player.getXRot();
        serverYHeadRot = prevServerYHeadRot = mc.player.getYHeadRot();
        if (fakePlayer.getValue()) {
            localPlayer = new RemotePlayer(mc.level, new GameProfile(UUID.nameUUIDFromBytes(("").getBytes(StandardCharsets.UTF_8)), ""));
            localPlayer.setId(-1337);
            localPlayer.copyPosition(mc.player);
            localPlayer.setYRot(mc.player.getYRot());
            localPlayer.setXRot(mc.player.getXRot());
            localPlayer.setYHeadRot(mc.player.getYHeadRot());
            localPlayer.setHealth(mc.player.getHealth());
            localPlayer.setAbsorptionAmount(mc.player.getAbsorptionAmount());
            mc.level.addEntity(localPlayer);
        }
    }

    @Override
    public void onDisable() {
        if (nullCheck()) return;
        if (fakePlayer.getValue()) {
            mc.level.removeEntity(localPlayer.getId(), Entity.RemovalReason.DISCARDED);
        }
        releaseAll();
        packets.clear();
    }

    private void handlePlayerMove(ServerboundMovePlayerPacket packet) {
        prevServerX = serverX;
        prevServerY = serverY;
        prevServerZ = serverZ;
        serverX = packet.getX(serverX);
        serverY = packet.getY(serverY);
        serverZ = packet.getZ(serverZ);

        prevServerYRot = serverYRot;
        prevServerXRot = serverXRot;
        prevServerYHeadRot = serverYHeadRot;
        serverYRot = packet.getYRot(serverYRot);
        serverXRot = packet.getXRot(serverXRot);
        if (packet.hasRotation()) {
            serverYHeadRot = packet.getYRot(serverYHeadRot);
        }
    }

    private int getBlinkTicks() {
        return Math.toIntExact(this.packets.stream().filter(packet -> packet instanceof ServerboundMovePlayerPacket).count());
    }

    private void releaseTick() {
        while (!this.packets.isEmpty()) {
            Packet<?> poll = this.packets.poll();
            PacketUtils.sendSilently(poll);
            if (poll instanceof ServerboundMovePlayerPacket) {
                handlePlayerMove((ServerboundMovePlayerPacket) poll);
                break;
            }
        }
    }

    private void releaseAll() {
        if (!packets.isEmpty()) {
            for (Packet packet : packets) {
                PacketUtils.sendSilently(packet);
                if (packet instanceof ServerboundMovePlayerPacket serverboundMovePlayerPacket) {
                    handlePlayerMove(serverboundMovePlayerPacket);
                }
            }
        }
    }

    @EventHandler
    public void onRender(Render3DEvent event) {
        if (nullCheck()) return;
        if (localPlayer != null && fakePlayer.getValue()) {
            float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
            double lerpX = prevServerX + (serverX - prevServerX) * pt;
            double lerpY = prevServerY + (serverY - prevServerY) * pt;
            double lerpZ = prevServerZ + (serverZ - prevServerZ) * pt;
            localPlayer.xOld = lastLerpX;
            localPlayer.yOld = lastLerpY;
            localPlayer.zOld = lastLerpZ;
            localPlayer.setPos(lerpX, lerpY, lerpZ);
            lastLerpX = lerpX;
            lastLerpY = lerpY;
            lastLerpZ = lerpZ;

            float lerpYRot = prevServerYRot + (serverYRot - prevServerYRot) * pt;
            float lerpXRot = prevServerXRot + (serverXRot - prevServerXRot) * pt;
            float lerpYHeadRot = prevServerYHeadRot + (serverYHeadRot - prevServerYHeadRot) * pt;
            localPlayer.setYRot(lerpYRot);
            localPlayer.setXRot(lerpXRot);
            localPlayer.setYHeadRot(lerpYHeadRot);
            localPlayer.setYBodyRot(lerpYRot);
        }
    }


    @EventHandler
    public void onMotion(SendPositionEvent event) {
        if (mc.screen instanceof RecoverWorldDataScreen && this.isEnabled()) {
            this.setEnabled(false);
        }
        if (nullCheck()) return;

        if (SlowRelease.getValue()) {
            if (getBlinkTicks() > tick.getValue()) {
                releaseTick();
            }
        } else {
            if (getBlinkTicks() > tick.getValue()) {
                releaseAll();
            }
        }
        if (slowMove.getValue()) {
            if (mc.player.tickCount % slowMoveTick.getValue().intValue() == 0) {
                releaseTick();
            }
        }
    }

}
