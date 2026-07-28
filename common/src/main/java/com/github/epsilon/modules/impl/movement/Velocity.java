package com.github.epsilon.modules.impl.movement;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.bus.EventPriority;
import com.github.epsilon.events.impl.ClientTickEvent;
import com.github.epsilon.events.impl.KeyboardInputEvent;
import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.modules.impl.combat.AntiBot;
import com.github.epsilon.modules.impl.combat.KillAura;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.player.PlayerUtils;
import com.github.epsilon.utils.rotation.Rot2f;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.game.*;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Velocity extends Module {

    public static final Velocity INSTANCE = new Velocity();

    private Velocity() {
        super("Velocity", Category.MOVEMENT);
    }

    private enum Mode {
        Cancel,
        Reduce
    }

    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.Cancel, newMode -> {
        if (newMode != Mode.Reduce) resetReduceState();
    });
    private final BoolSetting serverMotion = boolSetting("Server Motion", true, () -> mode.is(Mode.Cancel));
    private final BoolSetting explosion = boolSetting("Explosion", true, () -> mode.is(Mode.Cancel));
    private final BoolSetting explosionOnlyBlock = boolSetting("Explosion Only Block", false, () -> mode.is(Mode.Cancel) && explosion.getValue());
    public final BoolSetting waterPush = boolSetting("No Water Push", true, () -> mode.is(Mode.Cancel));
    public final BoolSetting entityPush = boolSetting("No Entity Push", true, () -> mode.is(Mode.Cancel));
    public final BoolSetting blockPush = boolSetting("No Block Push", true, () -> mode.is(Mode.Cancel));

    private final BoolSetting pauseOnEat = boolSetting("Pause On Eat", true, () -> mode.is(Mode.Reduce));
    private final IntSetting attackCounts = intSetting("Attack Counts", 3, 1, 5, 1, () -> mode.is(Mode.Reduce));
    private final IntSetting sprintTicks = intSetting("SprintTicks", 3, 1, 5, 1, () -> mode.is(Mode.Reduce));
    private final IntSetting maxDelay = intSetting("Max Delay", 1000, 0, 1000, 50, () -> mode.is(Mode.Reduce));

    private long lag;
    private long startDelay;
    private int attackQueue;
    private int sprintQueue;
    public float yaw;
    public boolean delay;
    private boolean jump;

    private final Queue<Packet<? super ClientPacketListener>> packets = new ConcurrentLinkedQueue<>();

    @Override
    protected void onEnable() {
        resetReduceState();
    }

    @Override
    protected void onDisable() {
        resetReduceState();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (nullCheck()) return;

        switch (mode.getValue()) {
            case Cancel -> {
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
            case Reduce -> reducePacket(event);
        }
    }

    private void reducePacket(PacketEvent.Receive event) {
        Packet<?> packet = event.getPacket();

        if (delay && isDelayedPacket(packet)) {
            event.cancel();
            packets.add((Packet<? super ClientPacketListener>) packet);
        }

        if (shouldPauseReduce()) return;

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

    private boolean isDelayedPacket(Packet<?> packet) {
        return packet instanceof ClientboundSetEntityMotionPacket
                || packet instanceof ClientboundMoveEntityPacket
                || packet instanceof ClientboundTeleportEntityPacket
                || packet instanceof ClientboundPingPacket
                || packet instanceof ClientboundPlayerLookAtPacket
                || packet instanceof ClientboundPlayerPositionPacket;
    }

    @EventHandler
    private void onReduceTick(ClientTickEvent.Pre event) {
        if (nullCheck() || !mode.is(Mode.Reduce)) return;

        if (shouldPauseReduce()) {
            flush();
            attackQueue = 0;
            sprintQueue = 0;
            jump = false;
            return;
        }

        long now = System.currentTimeMillis();
        if (delay && (mc.player.onGround() || now - lag < 100L || now - startDelay >= maxDelay.getValue())) {
            flush();
            if (now - lag >= 100L) {
                attackQueue = attackCounts.getValue();
                sprintQueue = sprintTicks.getValue();
            }
        }

        if (sprintQueue > 0 && canForceMovementInput() && now - lag >= 100L) {
            Managers.ROTATION.setRotations(
                    new Rot2f(yaw, Managers.ROTATION.getRotation().getPitch()),
                    180.0
            );
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    private void onAttackTick(PlayerTickEvent.Pre event) {
        if (!mode.is(Mode.Reduce) || shouldPauseReduce() || attackQueue <= 0) return;

        if (mc.hitResult instanceof EntityHitResult hit
                && hit.getEntity() instanceof Player player
                && !AntiBot.INSTANCE.isBot(player)) {
            mc.gameMode.attack(mc.player, player);
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
        attackQueue--;
    }

    @EventHandler(priority = EventPriority.LOW)
    private void onKeyboardInput(KeyboardInputEvent event) {
        if (!mode.is(Mode.Reduce) || shouldPauseReduce()) return;

        long elapsed = System.currentTimeMillis() - lag;
        if (delay && mc.player.fallDistance == 0.0f
                && mc.hitResult instanceof EntityHitResult hit
                && hit.getEntity() instanceof Player player
                && !AntiBot.INSTANCE.isBot(player)
                && elapsed >= 100L) {
            event.setForward(1.0f);
            event.setStrafe(0.0f);
        }

        if (jump && mc.player.onGround()) {
            if (elapsed >= 100L) {
                event.setJump(true);
                event.setForward(1.0f);
                event.setStrafe(0.0f);
            }
            jump = false;
        }

        if (sprintQueue > 0 && canForceMovementInput() && elapsed >= 100L) {
            event.setForward(1.0f);
            event.setStrafe(0.0f);
            sprintQueue--;
        }
    }

    private boolean canForceMovementInput() {
        return !KillAura.INSTANCE.isEnabled() && !Scaffold.INSTANCE.isEnabled();
    }

    private boolean shouldPauseReduce() {
        return pauseOnEat.getValue() && PlayerUtils.isEating();
    }

    private void flush() {
        if (!delay) return;

        Packet<? super ClientPacketListener> packet;
        while ((packet = packets.poll()) != null) {
            packet.handle(mc.getConnection());
        }
        delay = false;
        jump = true;
    }

    private void resetReduceState() {
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
