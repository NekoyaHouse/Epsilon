package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.bus.EventPriority;
import com.github.epsilon.events.impl.*;
import com.github.epsilon.graphics.schedulers.render3d.Render3DScheduler;
import com.github.epsilon.managers.FriendManager;
import com.github.epsilon.managers.target.TargetManager;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.modules.impl.movement.Velocity;
import com.github.epsilon.settings.impl.*;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.game.*;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.awt.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class Backtrack extends Module {

    public static final Backtrack INSTANCE = new Backtrack();

    private Backtrack() {
        super("Backtrack", Category.COMBAT);
    }

    private enum TargetMode {
        Attack,
        Range
    }

    private final DoubleSetting minRange = doubleSetting("Min Range", 2.5, 0.0, 10.0, 0.1);
    private final DoubleSetting maxRange = doubleSetting("Max Range", 5.0, 0.0, 10.0, 0.1);
    private final IntSetting minDelay = intSetting("Min Delay", 100, 0, 1000, 10);
    private final IntSetting maxDelay = intSetting("Max Delay", 150, 0, 1000, 10);
    private final IntSetting nextBacktrackMin = intSetting("Next Backtrack Min", 0, 0, 2000, 10);
    private final IntSetting nextBacktrackMax = intSetting("Next Backtrack Max", 10, 0, 2000, 10);
    private final IntSetting trackingBuffer = intSetting("Tracking Buffer", 500, 0, 2000, 10);
    private final IntSetting chance = intSetting("Chance", 50, 0, 100, 1);
    private final BoolSetting pauseOnHurtTime = boolSetting("Pause On Hurt Time", false);
    private final IntSetting hurtTime = intSetting("Hurt Time", 3, 0, 10, 1, pauseOnHurtTime::getValue);
    private final EnumSetting<TargetMode> targetMode = enumSetting("Target Mode", TargetMode.Attack);
    private final IntSetting lastAttackTime = intSetting("Last Attack Time", 1000, 0, 5000, 50);
    private final BoolSetting players = boolSetting("Players", true, () -> targetMode.is(TargetMode.Range));
    private final BoolSetting mobs = boolSetting("Mobs", false, () -> targetMode.is(TargetMode.Range));
    private final BoolSetting animals = boolSetting("Animals", false, () -> targetMode.is(TargetMode.Range));
    private final BoolSetting villagers = boolSetting("Villagers", false, () -> targetMode.is(TargetMode.Range));
    private final BoolSetting ambient = boolSetting("Ambient", false, () -> targetMode.is(TargetMode.Range));
    private final BoolSetting water = boolSetting("Water", false, () -> targetMode.is(TargetMode.Range));
    private final BoolSetting others = boolSetting("Others", false, () -> targetMode.is(TargetMode.Range));
    private final BoolSetting invisible = boolSetting("Invisible", true, () -> targetMode.is(TargetMode.Range));
    private final BoolSetting esp = boolSetting("ESP", true);
    private final ColorSetting sideColor = colorSetting("Side Color", new Color(255, 255, 255, 35), esp::getValue);
    private final ColorSetting outlineColor = colorSetting("Outline Color", new Color(255, 255, 255, 190), esp::getValue);

    private final Object packetLock = new Object();
    private final Deque<QueuedPacket> packetQueue = new ArrayDeque<>();

    private LivingEntity target;
    private Vec3 trackedPosition = Vec3.ZERO;
    private volatile boolean backtracking;
    private boolean chancePassed;
    private long lastAttackAt = Long.MIN_VALUE;
    private long lastInRangeAt = Long.MIN_VALUE;
    private long nextAllowedAt;
    private int currentDelay;

    @Override
    public String getInfo() {
        synchronized (packetLock) {
            return backtracking && !packetQueue.isEmpty() ? currentDelay + "ms" : targetMode.getTranslatedValue();
        }
    }

    @Override
    protected void onEnable() {
        synchronized (packetLock) {
            resetStateLocked(true);
            chancePassed = rollChance();
            currentDelay = randomBetween(minDelay.getValue(), maxDelay.getValue());
        }
    }

    @Override
    protected void onDisable() {
        List<Packet<? super ClientPacketListener>> pending;
        synchronized (packetLock) {
            pending = drainAllLocked();
            resetStateLocked(false);
        }
        handlePackets(pending);
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        long now = System.currentTimeMillis();
        synchronized (packetLock) {
            lastAttackAt = now;
            chancePassed = rollChance();
        }

        if (targetMode.is(TargetMode.Attack) && event.getEntity() instanceof LivingEntity living) {
            processTarget(living, now);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onTick(ClientTickEvent.Pre event) {
        if (nullCheck()) {
            clearWithoutReplay();
            return;
        }

        long now = System.currentTimeMillis();
        if (targetMode.is(TargetMode.Range)) {
            LivingEntity rangeTarget = findRangeTarget();
            if (rangeTarget == null) {
                clearAndReplay(true);
            } else {
                processTarget(rangeTarget, now);
            }
        }

        List<Packet<? super ClientPacketListener>> pending;
        synchronized (packetLock) {
            if (Velocity.INSTANCE.ownsIncomingDelayQueue()) {
                backtracking = false;
                pending = drainAllLocked();
            } else {
                backtracking = target != null && shouldBacktrackLocked(target, now);
                if (backtracking) {
                    pending = drainExpiredLocked(now - currentDelay);
                } else if (!packetQueue.isEmpty()) {
                    pending = drainAllLocked();
                    clearTargetLocked(true);
                } else {
                    pending = List.of();
                }
            }

            if (packetQueue.isEmpty()) {
                currentDelay = randomBetween(minDelay.getValue(), maxDelay.getValue());
            }
        }
        handlePackets(pending);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onPacketReceive(PacketEvent.Receive event) {
        Packet<?> packet = event.getPacket();
        List<Packet<? super ClientPacketListener>> pending = List.of();

        synchronized (packetLock) {
            if (packet instanceof ClientboundDisconnectPacket) {
                packetQueue.clear();
                resetStateLocked(false);
                return;
            }

            if (nullCheck()) {
                packetQueue.clear();
                resetStateLocked(false);
                return;
            }

            if (Velocity.INSTANCE.ownsIncomingDelayQueue()) {
                return;
            }

            if (packet instanceof ClientboundPlayerPositionPacket || packet instanceof ClientboundSetHealthPacket health && health.getHealth() <= 0.0f) {
                pending = drainAllLocked();
                clearTargetLocked(true);
            } else if (isPassThrough(packet)) {
                return;
            } else if (backtracking || !packetQueue.isEmpty()) {
                Vec3 newTrackedPosition = trackTargetPosition(packet);
                if (newTrackedPosition != null && isServerPositionCloser(newTrackedPosition)) {
                    pending = drainAllLocked();
                } else {
                    event.cancel();
                    packetQueue.addLast(new QueuedPacket((Packet<? super ClientPacketListener>) packet, System.currentTimeMillis()));
                    return;
                }
            }
        }

        schedulePackets(pending);
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        AABB box;
        synchronized (packetLock) {
            if (!esp.getValue() || target == null || !target.isAlive()) return;
            box = target.getBoundingBox().move(trackedPosition.subtract(target.position()));
        }
        Render3DScheduler.INSTANCE.addFilledBox(box, sideColor.getValue());
        Render3DScheduler.INSTANCE.addOutlineBox(box, outlineColor.getValue(), 1.5f);
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        clearWithoutReplay();
    }

    @EventHandler
    private void onLevelUpdate(LevelUpdateEvent event) {
        clearWithoutReplay();
    }

    public boolean isLagging() {
        synchronized (packetLock) {
            return isEnabled() && !packetQueue.isEmpty();
        }
    }

    private void processTarget(LivingEntity enemy, long now) {
        List<Packet<? super ClientPacketListener>> pending = List.of();
        synchronized (packetLock) {
            if (!shouldBacktrackLocked(enemy, now)) return;

            if (enemy != target) {
                pending = drainAllLocked();
                target = enemy;
                trackedPosition = enemy.getPositionCodec().getBase();
            }
            backtracking = true;
        }
        handlePackets(pending);
    }

    private boolean shouldBacktrackLocked(LivingEntity candidate, long now) {
        if (!isValidTarget(candidate)) return false;

        double distance = candidate.getBoundingBox().distanceToSqr(mc.player.getEyePosition());
        double min = Math.min(minRange.getValue(), maxRange.getValue());
        double max = Math.max(minRange.getValue(), maxRange.getValue());
        boolean inRange = distance >= min * min && distance <= max * max;
        if (inRange) {
            lastInRangeAt = now;
        }

        boolean withinTrackingBuffer = lastInRangeAt != Long.MIN_VALUE && now - lastInRangeAt <= trackingBuffer.getValue();
        boolean attackedRecently = lastAttackAt != Long.MIN_VALUE && now - lastAttackAt <= lastAttackTime.getValue();

        return (inRange || withinTrackingBuffer)
                && mc.player.tickCount > 10
                && chancePassed
                && now >= nextAllowedAt
                && attackedRecently
                && !Velocity.INSTANCE.blocksBacktrack()
                && (!pauseOnHurtTime.getValue() || candidate.hurtTime < hurtTime.getValue());
    }

    private boolean isValidTarget(LivingEntity candidate) {
        if (candidate == mc.player || !candidate.isAlive() || candidate.isDeadOrDying()) return false;
        if (candidate.level() != mc.level || AntiBot.INSTANCE.isBot(candidate)) return false;
        if (TargetManager.INSTANCE.isSameTeam(candidate)) return false;
        return !(candidate instanceof Player player) || !FriendManager.INSTANCE.isFriend(player);
    }

    private LivingEntity findRangeTarget() {
        double min = Math.min(minRange.getValue(), maxRange.getValue());
        double max = Math.max(minRange.getValue(), maxRange.getValue());
        double minSquared = min * min;
        double maxSquared = max * max;
        Vec3 eyePosition = mc.player.getEyePosition();
        LivingEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || entity instanceof ArmorStand) continue;
            if (!isValidTarget(living) || !isEnabledRangeTarget(living)) continue;

            double distance = living.getBoundingBox().distanceToSqr(eyePosition);
            if (distance < minSquared || distance > maxSquared || distance >= nearestDistance) continue;
            nearest = living;
            nearestDistance = distance;
        }
        return nearest;
    }

    private boolean isEnabledRangeTarget(LivingEntity entity) {
        if (entity instanceof Player) {
            return players.getValue() && (invisible.getValue() || !entity.isInvisible());
        }
        if (entity instanceof Villager) return villagers.getValue();
        if (entity instanceof Monster) return mobs.getValue();
        if (entity instanceof Animal) return animals.getValue();

        MobCategory category = entity.getType().getCategory();
        return switch (category) {
            case AMBIENT, WATER_AMBIENT -> ambient.getValue();
            case WATER_CREATURE, AXOLOTLS, UNDERGROUND_WATER_CREATURE -> water.getValue();
            default -> others.getValue();
        };
    }

    private boolean isPassThrough(Packet<?> packet) {
        if (packet instanceof ClientboundSystemChatPacket || packet instanceof ClientboundPlayerChatPacket) {
            return true;
        }
        return packet instanceof ClientboundSoundPacket sound && sound.getSound().value() == SoundEvents.PLAYER_HURT;
    }

    private Vec3 trackTargetPosition(Packet<?> packet) {
        if (target == null) return null;
        Vec3 position = trackSinglePacket(packet);

        if (packet instanceof BundlePacket<?> bundle) {
            for (Packet<?> subPacket : bundle.subPackets()) {
                Vec3 subPosition = trackSinglePacket(subPacket);
                if (subPosition != null) position = subPosition;
            }
        }
        return position;
    }

    private Vec3 trackSinglePacket(Packet<?> packet) {
        Vec3 position;
        if (packet instanceof ClientboundMoveEntityPacket movement && movement.getEntity(mc.level) == target) {
            position = decodeRelative(trackedPosition, movement.getXa(), movement.getYa(), movement.getZa());
        } else if (packet instanceof ClientboundTeleportEntityPacket teleport && teleport.id() == target.getId()) {
            position = teleport.change().position();
        } else if (packet instanceof ClientboundEntityPositionSyncPacket sync && sync.id() == target.getId()) {
            position = sync.values().position();
        } else {
            return null;
        }

        trackedPosition = position;
        return position;
    }

    private boolean isServerPositionCloser(Vec3 serverPosition) {
        if (target == null) return false;
        Vec3 eyePosition = mc.player.getEyePosition();
        AABB currentBox = target.getBoundingBox();
        AABB serverBox = currentBox.move(serverPosition.subtract(target.position()));
        return serverBox.distanceToSqr(eyePosition) < currentBox.distanceToSqr(eyePosition);
    }

    private void clearAndReplay(boolean applyCooldown) {
        List<Packet<? super ClientPacketListener>> pending;
        synchronized (packetLock) {
            pending = drainAllLocked();
            clearTargetLocked(applyCooldown);
        }
        handlePackets(pending);
    }

    private void clearWithoutReplay() {
        synchronized (packetLock) {
            packetQueue.clear();
            resetStateLocked(false);
        }
    }

    private void resetStateLocked(boolean resetTimers) {
        target = null;
        trackedPosition = Vec3.ZERO;
        backtracking = false;
        lastInRangeAt = Long.MIN_VALUE;
        if (resetTimers) {
            lastAttackAt = Long.MIN_VALUE;
            nextAllowedAt = 0L;
        }
    }

    private void clearTargetLocked(boolean applyCooldown) {
        if (target != null && applyCooldown) {
            nextAllowedAt = System.currentTimeMillis() + randomBetween(nextBacktrackMin.getValue(), nextBacktrackMax.getValue());
        }
        target = null;
        trackedPosition = Vec3.ZERO;
        backtracking = false;
        lastInRangeAt = Long.MIN_VALUE;
    }

    private List<Packet<? super ClientPacketListener>> drainExpiredLocked(long timestamp) {
        if (packetQueue.isEmpty() || packetQueue.peekFirst().timestamp() > timestamp) return List.of();

        List<Packet<? super ClientPacketListener>> pending = new ArrayList<>();
        while (!packetQueue.isEmpty() && packetQueue.peekFirst().timestamp() <= timestamp) {
            pending.add(packetQueue.removeFirst().packet());
        }
        return pending;
    }

    private List<Packet<? super ClientPacketListener>> drainAllLocked() {
        if (packetQueue.isEmpty()) return List.of();

        List<Packet<? super ClientPacketListener>> pending = new ArrayList<>(packetQueue.size());
        while (!packetQueue.isEmpty()) {
            pending.add(packetQueue.removeFirst().packet());
        }
        return pending;
    }

    private void schedulePackets(List<Packet<? super ClientPacketListener>> packets) {
        if (!packets.isEmpty()) mc.execute(() -> handlePackets(packets));
    }

    private void handlePackets(List<Packet<? super ClientPacketListener>> packets) {
        ClientPacketListener listener = mc.getConnection();
        if (listener == null) return;
        for (Packet<? super ClientPacketListener> packet : packets) {
            packet.handle(listener);
        }
    }

    private boolean rollChance() {
        return ThreadLocalRandom.current().nextInt(100) < chance.getValue();
    }

    private static int randomBetween(int first, int second) {
        int min = Math.min(first, second);
        int max = Math.max(first, second);
        return min == max ? min : ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    private static Vec3 decodeRelative(Vec3 base, long x, long y, long z) {
        double decodedX = x == 0L ? base.x : (Math.round(base.x * 4096.0) + x) / 4096.0;
        double decodedY = y == 0L ? base.y : (Math.round(base.y * 4096.0) + y) / 4096.0;
        double decodedZ = z == 0L ? base.z : (Math.round(base.z * 4096.0) + z) / 4096.0;
        return new Vec3(decodedX, decodedY, decodedZ);
    }

    private record QueuedPacket(Packet<? super ClientPacketListener> packet, long timestamp) {
    }

}
