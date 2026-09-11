package com.github.epsilon.modules.impl.movement;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.bus.EventPriority;
import com.github.epsilon.events.impl.ClientTickEvent;
import com.github.epsilon.events.impl.KeyboardInputEvent;
import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.managers.rotation.RotationManager;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.modules.impl.combat.AntiBot;
import com.github.epsilon.modules.impl.combat.KillAura;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.player.PlayerUtils;
import com.github.epsilon.utils.rotation.Priority;
import com.github.epsilon.utils.rotation.Rot2f;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.game.*;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class Velocity extends Module {

    public static final Velocity INSTANCE = new Velocity();

    private Velocity() {
        super("Velocity", Category.MOVEMENT);
    }

    // Packet receive events run on Netty while delayed packets are flushed on the client thread.
    private final Object packetLock = new Object();

    public enum Mode {
        Cancel,
        Reduce,
        Delay
    }

    public final EnumSetting<Mode> mode = enumSetting("Mode", Mode.Cancel, newMode -> {
        if (newMode != Mode.Reduce) resetReduceState();
        if (newMode != Mode.Delay) resetDelayState();
    });
    private final BoolSetting serverMotion = boolSetting("Server Motion", true, () -> mode.is(Mode.Cancel));
    private final BoolSetting explosion = boolSetting("Explosion", true, () -> mode.is(Mode.Cancel));
    private final BoolSetting explosionOnlyBlock = boolSetting("Explosion Only Block", false, () -> mode.is(Mode.Cancel) && explosion.getValue());
    public final BoolSetting waterPush = boolSetting("No Water Push", true, () -> mode.is(Mode.Cancel));
    public final BoolSetting entityPush = boolSetting("No Entity Push", true, () -> mode.is(Mode.Cancel));
    public final BoolSetting blockPush = boolSetting("No Block Push", true, () -> mode.is(Mode.Cancel));
    private final IntSetting attackCounts = intSetting("Attack Counts", 1, 1, 5, 1, () -> mode.is(Mode.Reduce));
    private final IntSetting sprintTicks = intSetting("Sprint Ticks", 3, 1, 5, 1, () -> mode.is(Mode.Reduce));
    private final IntSetting maxDelay = intSetting("Max Delay", 1000, 0, 1000, 50, () -> mode.is(Mode.Reduce));
    private final BoolSetting swingHand = boolSetting("Swing Hand", false, () -> mode.is(Mode.Reduce));
    private final IntSetting delayTicks = intSetting("Delay Ticks", 3, 1, 5, 1, () -> mode.is(Mode.Delay));
    private final BoolSetting jumpReset = boolSetting("Jump Reset", false, () -> mode.is(Mode.Delay));

    private volatile long lag;
    private volatile long delayLag;
    private volatile long startDelay;
    public volatile int attackQueue;
    private volatile int sprintQueue;
    public volatile float yaw;
    public volatile boolean delay;
    private volatile boolean jump;
    private volatile int delayTicksRemaining;

    public boolean ownsIncomingDelayQueue() {
        return isEnabled() && (mode.is(Mode.Reduce) && delay || mode.is(Mode.Delay) && delayTicksRemaining > 0);
    }

    public boolean blocksBacktrack() {
        return isEnabled() && (mode.is(Mode.Reduce) && (delay || attackQueue > 0) || mode.is(Mode.Delay) && delayTicksRemaining > 0);
    }

    private final Queue<Packet<? super ClientPacketListener>> packets = new ArrayDeque<>();
    private final Queue<Packet<? super ClientPacketListener>> delayPackets = new ArrayDeque<>();

    @Override
    public String getInfo() {
        return mode.getTranslatedValue() + switch (mode.getValue()) {
            case Cancel -> "";
            case Reduce -> " " + (delay ? System.currentTimeMillis() - startDelay + "ms" : "");
            case Delay -> " " + delayTicksRemaining + "t";
        };
    }

    @Override
    protected void onDisable() {
        flush();
        flushDelay();
        resetReduceState();
        resetDelayState();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        synchronized (packetLock) {
            if (!isEnabled() || nullCheck()) return;

            switch (mode.getValue()) {
                case Cancel -> cancelPacket(event);
                case Reduce -> reducePacket(event);
                case Delay -> delayPacket(event);
            }
        }
    }

    private void cancelPacket(PacketEvent.Receive event) {
        if (serverMotion.getValue() && event.getPacket() instanceof ClientboundSetEntityMotionPacket packet && packet.id() == mc.player.getId()) {
            event.cancel();
            return;
        }

        if (explosion.getValue()
                && event.getPacket() instanceof ClientboundExplodePacket packet
                && (!explosionOnlyBlock.getValue() || PlayerUtils.isInBlock())) {
            event.setPacket(new ClientboundExplodePacket(
                    packet.center(),
                    packet.radius(),
                    packet.blockCount(),
                    Optional.empty(),
                    packet.explosionParticle(),
                    packet.explosionSound(),
                    packet.blockParticles()
            ));
        }
    }

    private void reducePacket(PacketEvent.Receive event) {
        Packet<?> packet = event.getPacket();

        if (packet instanceof ClientboundDisconnectPacket) {
            resetReduceState();
            return;
        }

        if (delay && (packet instanceof ClientboundSetEntityMotionPacket
                || packet instanceof ClientboundMoveEntityPacket
                || packet instanceof ClientboundTeleportEntityPacket
                || packet instanceof ClientboundPingPacket
                || packet instanceof ClientboundPlayerLookAtPacket
                || packet instanceof ClientboundPlayerPositionPacket)
        ) {
            event.cancel();
            packets.add((Packet<? super ClientPacketListener>) packet);
        }

        if (packet instanceof ClientboundPlayerPositionPacket || packet instanceof ClientboundExplodePacket) {
            lag = System.currentTimeMillis();
        }

        if (packet instanceof ClientboundSetEntityMotionPacket(int id, Vec3 movement) && id == mc.player.getId()) {
            if (System.currentTimeMillis() - lag >= 100L) {
                boolean knockback = movement.y > 0.0 && (movement.x != 0.0 || movement.z != 0.0);
                if (knockback && !delay) {
                    delay = true;
                    event.cancel();
                    packets.add((Packet<? super ClientPacketListener>) packet);
                    startDelay = System.currentTimeMillis();
                }
            }
            yaw = Mth.wrapDegrees((float) (Math.toDegrees(Math.atan2(movement.z, movement.x)) + 90.0));
        }
    }

    private void delayPacket(PacketEvent.Receive event) {
        Packet<?> packet = event.getPacket();

        if (packet instanceof ClientboundDisconnectPacket) {
            resetDelayState();
            return;
        }

        if (packet instanceof ClientboundPlayerPositionPacket || packet instanceof ClientboundExplodePacket) {
            delayLag = System.currentTimeMillis();
        }

        if (delayTicksRemaining > 0) {
            event.cancel();
            delayPackets.add((Packet<? super ClientPacketListener>) packet);
            return;
        }

        if (packet instanceof ClientboundSetEntityMotionPacket(
                int id, Vec3 movement
        ) && id == mc.player.getId() && System.currentTimeMillis() - delayLag >= 100L) {
            boolean knockback = movement.y > 0.0 && (movement.x != 0.0 || movement.z != 0.0);
            if (knockback) {
                delayTicksRemaining = delayTicks.getValue();
            }
        }
    }

    @EventHandler
    private void onReduceTick(ClientTickEvent.Pre event) {
        if (nullCheck() || !mode.is(Mode.Reduce)) return;

        if (delay && (mc.player.onGround() || System.currentTimeMillis() - lag < 100 || System.currentTimeMillis() - startDelay >= maxDelay.getValue()) && flush()) {
            if (System.currentTimeMillis() - lag >= 100L) {
                attackQueue = attackCounts.getValue();
                sprintQueue = sprintTicks.getValue();
            }
        }

        KillAura killAura = KillAura.INSTANCE;
        if (sprintQueue >= 1 && killAura.target == null && !Scaffold.INSTANCE.isEnabled()) {
            RotationManager.INSTANCE.setRotations(new Rot2f(yaw, RotationManager.INSTANCE.getRotation().getPitch()), 180f, Priority.Highest);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    private void onDelayTick(PlayerTickEvent.Pre event) {
        List<Packet<? super ClientPacketListener>> pending = List.of();
        synchronized (packetLock) {
            if (!mode.is(Mode.Delay) || delayTicksRemaining <= 0) return;
            if (--delayTicksRemaining == 0) {
                pending = drain(delayPackets);
            }
        }
        handlePackets(pending);
    }

    @EventHandler(priority = EventPriority.LOW)
    private void onAttackTick(PlayerTickEvent.Pre event) {
        if (!mode.is(Mode.Reduce)) return;

        if (attackQueue >= 1) {
            if (RotationManager.INSTANCE.getHitResult() instanceof EntityHitResult entityHitResult && entityHitResult.getEntity() instanceof Player pl && pl.isAlive() && !AntiBot.INSTANCE.isBot(pl)) {
                mc.gameMode.attack(mc.player, pl);
                if (swingHand.getValue()) {
                    mc.player.swing(InteractionHand.MAIN_HAND);
                } else {
                    mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
                }
            }
            attackQueue--;
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    private void onKeyboardInput(KeyboardInputEvent event) {
        if (mode.is(Mode.Delay) && jumpReset.getValue() && mc.player.onGround() && mc.player.hurtTime == 9) {
            event.setJump(true);
        }

        if (!mode.is(Mode.Reduce)) return;

        if (delay && mc.player.fallDistance == 0 && RotationManager.INSTANCE.getHitResult() instanceof EntityHitResult entityHitResult && entityHitResult.getEntity() instanceof Player pl && !AntiBot.INSTANCE.isBot(pl)) {
            if (System.currentTimeMillis() - lag >= 100) {
                event.setForward(1.0f);
                event.setStrafe(0.0f);
            }
        }

        if (jump && mc.player.onGround()) {
            if (System.currentTimeMillis() - lag >= 100) {
                event.setJump(true);
                event.setForward(1.0f);
                event.setStrafe(0.0f);
            }
            jump = false;
        }

        KillAura killAura = KillAura.INSTANCE;
        if (sprintQueue-- >= 1 && killAura.target == null && !Scaffold.INSTANCE.isEnabled() && System.currentTimeMillis() - lag >= 100) {
            event.setForward(1.0f);
            event.setStrafe(0.0f);
        }
    }

    private boolean flush() {
        List<Packet<? super ClientPacketListener>> pending;
        synchronized (packetLock) {
            if (!delay) return false;

            delay = false;
            pending = drain(packets);
        }

        handlePackets(pending);
        jump = true;
        return true;
    }

    private void flushDelay() {
        List<Packet<? super ClientPacketListener>> pending;
        synchronized (packetLock) {
            delayTicksRemaining = 0;
            pending = drain(delayPackets);
        }
        handlePackets(pending);
    }

    private void resetReduceState() {
        synchronized (packetLock) {
            delay = false;
            packets.clear();
            lag = 0L;
            startDelay = 0L;
            attackQueue = 0;
            sprintQueue = 0;
            jump = false;
            yaw = 0.0f;
        }
    }

    private void resetDelayState() {
        synchronized (packetLock) {
            delayTicksRemaining = 0;
            delayPackets.clear();
            delayLag = 0L;
        }
    }

    private static <T> List<T> drain(Queue<T> queue) {
        List<T> packets = new ArrayList<>(queue.size());
        T packet;
        while ((packet = queue.poll()) != null) {
            packets.add(packet);
        }
        return packets;
    }

    private void handlePackets(List<Packet<? super ClientPacketListener>> packets) {
        ClientPacketListener listener = mc.getConnection();
        if (listener == null) return;

        for (Packet<? super ClientPacketListener> packet : packets) {
            packet.handle(listener);
        }
    }

}
