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

    private final BoolSetting pauseOnEat = boolSetting("Pause On Eat", false, () -> mode.is(Mode.Reduce));
    private final IntSetting attackCounts = intSetting("Attack Counts", 1, 1, 5, 1, () -> mode.is(Mode.Reduce));
    private final IntSetting sprintTicks = intSetting("SprintTicks", 1, 1, 5, 1, () -> mode.is(Mode.Reduce));
    private final IntSetting maxDelay = intSetting("Max Delay", 1000, 0, 1000, 50, () -> mode.is(Mode.Reduce));

    private long lag;
    private long startDelay;
    public int attackQueue;
    private int sprintQueue;
    public float yaw;
    public boolean delay;
    private boolean jump;

    private final Queue<Packet<? super ClientPacketListener>> packets = new ConcurrentLinkedQueue<>();

    @Override
    public String getInfo() {
        return switch (mode.getValue()) {
            case Cancel -> "Cancel";
            case Reduce -> "Reduce " + (delay ? System.currentTimeMillis() - startDelay + "ms" : "");
        };
    }

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

        if (pauseOnEat.getValue() && PlayerUtils.isEating()) return;

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

    @EventHandler
    private void onReduceTick(ClientTickEvent.Pre event) {
        if (nullCheck() || !mode.is(Mode.Reduce)) return;

        if (pauseOnEat.getValue() && PlayerUtils.isEating()) {
            flush();
            attackQueue = 0;
            sprintQueue = 0;
            jump = false;
            return;
        }

        if (delay && (mc.player.onGround() || System.currentTimeMillis() - lag < 100 || System.currentTimeMillis() - startDelay >= maxDelay.getValue())) {
            flush();
            if (System.currentTimeMillis() - lag >= 100) {
                attackQueue = attackCounts.getValue();
                sprintQueue = sprintTicks.getValue();
            }
        }

        KillAura killAura = KillAura.INSTANCE;
        if (sprintQueue >= 1 && killAura.target == null && !Scaffold.INSTANCE.isEnabled()) {
            Managers.ROTATION.setRotations(new Rot2f(yaw, Managers.ROTATION.getRotation().getPitch()), 180f);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    private void onAttackTick(PlayerTickEvent.Pre event) {
        if (!mode.is(Mode.Reduce)) return;
        if (pauseOnEat.getValue() && PlayerUtils.isEating()) return;

        if (attackQueue >= 1) {
            if (mc.hitResult instanceof EntityHitResult entityHitResult && entityHitResult.getEntity() instanceof Player pl && !AntiBot.INSTANCE.isBot(pl)) {
                mc.gameMode.attack(mc.player, pl);
                mc.player.swing(InteractionHand.MAIN_HAND);
            }
            attackQueue--;
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    private void onKeyboardInput(KeyboardInputEvent event) {
        if (!mode.is(Mode.Reduce)) return;
        if (pauseOnEat.getValue() && PlayerUtils.isEating()) return;

        if (delay && mc.player.fallDistance == 0 && mc.hitResult instanceof EntityHitResult entityHitResult && entityHitResult.getEntity() instanceof Player pl && !AntiBot.INSTANCE.isBot(pl)) {
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
