package com.github.epsilon.modules.impl.movement;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.bus.EventPriority;
import com.github.epsilon.events.impl.MoveEvent;
import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.graphics.schedulers.render3d.Render3DScheduler;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.SettingGroup;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.world.hole.Hole;
import com.github.epsilon.utils.world.hole.HoleType;
import com.github.epsilon.utils.world.hole.HoleUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.awt.*;

public class HoleSnap extends Module {

    public static final HoleSnap INSTANCE = new HoleSnap();

    private HoleSnap() {
        super("Hole Snap", Category.MOVEMENT);
    }

    private final SettingGroup sgGeneral = settingGroup("General");
    private final SettingGroup sgSpeed = settingGroup("Speed");
    private final SettingGroup sgHole = settingGroup("Hole");
    private final SettingGroup sgRender = settingGroup("Render");

    private final BoolSetting step = boolSetting("Use Step", true).group(sgGeneral);
    private final BoolSetting disableStep = boolSetting("Toggle Step", true).group(sgGeneral);
    private final BoolSetting jump = boolSetting("Jump", false).group(sgGeneral);
    private final IntSetting jumpCooldown = intSetting("Jump Cooldown", 5, 0, 100, 1, jump::getValue).group(sgGeneral);
    private final IntSetting range = intSetting("Range", 3, 0, 10, 1).group(sgGeneral);
    private final IntSetting downRange = intSetting("Down Range", 3, 0, 5, 1).group(sgGeneral);
    private final IntSetting collisionLimit = intSetting("Collisions To Disable", 15, 0, 100, 1).group(sgGeneral);
    private final IntSetting rubberbandLimit = intSetting("Rubberbands To Disable", 1, 0, 100, 1).group(sgGeneral);

    private final DoubleSetting speed = doubleSetting("Speed", 0.2873, 0.0, 1.0, 0.0001).group(sgSpeed);
    private final BoolSetting boost = boolSetting("Speed Boost", false).group(sgSpeed);
    private final DoubleSetting boostedSpeed = doubleSetting("Boosted Speed", 0.5, 0.0, 1.0, 0.01, boost::getValue).group(sgSpeed);
    private final IntSetting boostTicks = intSetting("Boost Ticks", 3, 1, 10, 1, boost::getValue).group(sgSpeed);
    private final DoubleSetting timer = doubleSetting("Timer", 1.5, 0.1, 100.0, 0.1).group(sgSpeed);

    private final BoolSetting singleTarget = boolSetting("Single Target", true).group(sgHole);
    private final IntSetting depth = intSetting("Hole Depth", 3, 1, 5, 1).group(sgHole);
    private final BoolSetting singleHoles = boolSetting("Single Holes", true).group(sgHole);
    private final BoolSetting doubleHoles = boolSetting("Double Holes", true).group(sgHole);
    private final BoolSetting quadHoles = boolSetting("Quad Holes", true).group(sgHole);

    private final BoolSetting render = boolSetting("Render", true).group(sgRender);
    private final DoubleSetting lineWidth = doubleSetting("Line Width", 2.0, 1.0, 4.0, 0.1, render::getValue).group(sgRender);
    private final ColorSetting color = colorSetting("Color", Color.WHITE, render::getValue).group(sgRender);

    private Hole singleHole;
    private Hole targetHole;
    private int collisions;
    private int rubberbands;
    private int jumpTicks;
    private int boostLeft;

    @Override
    protected void onEnable() {
        singleHole = nullCheck() ? null : findHole();
        targetHole = singleHole;
        collisions = 0;
        rubberbands = 0;
        jumpTicks = 0;
        boostLeft = boost.getValue() ? boostTicks.getValue() : 0;
    }

    @Override
    protected void onDisable() {
        if (disableStep.getValue() && step.getValue() && Step.INSTANCE.isEnabled()) {
            Step.INSTANCE.setEnabled(false);
        }

        Managers.TIMER.reset();
        singleHole = null;
        targetHole = null;
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (rubberbandLimit.getValue() <= 0) return;

        if (event.getPacket() instanceof ClientboundPlayerPositionPacket || event.getPacket() instanceof ClientboundPlayerRotationPacket) {
            rubberbands++;
            if (rubberbands >= rubberbandLimit.getValue()) {
                setEnabled(false);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onMove(MoveEvent event) {
        Hole hole = singleTarget.getValue() ? singleHole : findHole();
        targetHole = hole;

        if (hole == null || singleBlocked()) {
            toggle();
            return;
        }

        Managers.TIMER.set(timer.getValue().floatValue());

        double yaw = Math.cos(Math.toRadians(getAngle(hole.middle) + 90.0f));
        double pitch = Math.sin(Math.toRadians(getAngle(hole.middle) + 90.0f));

        if (mc.player.getX() == hole.middle.x && mc.player.getZ() == hole.middle.z) {
            if (mc.player.getY() == hole.middle.y) {
                event.setX(0.0);
                event.setZ(0.0);
                event.cancel();
                toggle();
            } else if (hasBlockCollision(mc.player.getBoundingBox().move(0.0, -0.05, 0.0))) {
                toggle();
            } else {
                event.setX(0.0);
                event.setZ(0.0);
                event.cancel();
            }
            return;
        }

        if (step.getValue() && !Step.INSTANCE.isEnabled()) {
            Step.INSTANCE.setEnabled(true);
        }

        double motionX = getSpeed() * yaw;
        double distanceX = hole.middle.x - mc.player.getX();
        double motionZ = getSpeed() * pitch;
        double distanceZ = hole.middle.z - mc.player.getZ();

        if (hasBlockCollision(mc.player.getBoundingBox().move(motionX, 0.0, motionZ))) {
            collisions++;
            if (collisionLimit.getValue() > 0 && collisions >= collisionLimit.getValue()) {
                toggle();
                return;
            }
        } else {
            collisions = 0;
        }

        if (jumpTicks > 0) {
            jumpTicks--;
        } else if (jump.getValue() && hasBlockCollision(mc.player.getBoundingBox().move(0.0, -0.05, 0.0))) {
            jumpTicks = jumpCooldown.getValue();
            event.setY(0.42);
        }

        boostLeft--;
        event.setX(Math.abs(motionX) < Math.abs(distanceX) ? motionX : distanceX);
        event.setZ(Math.abs(motionZ) < Math.abs(distanceZ) ? motionZ : distanceZ);
        event.cancel();
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!render.getValue() || targetHole == null) return;

        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        Vec3 playerPos = mc.player.getPosition(partialTick);
        Vec3 topEdge = getTopEdge(targetHole, playerPos);
        float width = lineWidth.getValue().floatValue();

        Render3DScheduler.INSTANCE.addLine(playerPos, topEdge, color.getValue(), width);
        Render3DScheduler.INSTANCE.addLine(topEdge, targetHole.middle, color.getValue(), width);
    }

    private Vec3 getTopEdge(Hole hole, Vec3 playerPos) {
        double minX = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        for (BlockPos pos : hole.positions) {
            minX = Math.min(minX, pos.getX());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX() + 1.0);
            maxZ = Math.max(maxZ, pos.getZ() + 1.0);
        }

        double deltaX = playerPos.x - hole.middle.x;
        double deltaZ = playerPos.z - hole.middle.z;
        double xRatio = Math.abs(deltaX) / ((maxX - minX) * 0.5);
        double zRatio = Math.abs(deltaZ) / ((maxZ - minZ) * 0.5);
        double ratio = Math.max(xRatio, zRatio);
        double scale = ratio > 1.0 ? 1.0 / ratio : 1.0;

        return new Vec3(
                hole.middle.x + deltaX * scale,
                hole.middle.y + 1.0,
                hole.middle.z + deltaZ * scale
        );
    }

    private boolean singleBlocked() {
        if (!singleTarget.getValue() || singleHole == null) return false;

        for (BlockPos pos : singleHole.positions) {
            if (HoleUtils.isBlock(pos)) return true;
        }
        return false;
    }

    private Hole findHole() {
        Hole closest = null;
        Vec3 playerPos = mc.player.position();
        BlockPos playerBlockPos = mc.player.blockPosition();

        for (int x = -range.getValue(); x <= range.getValue(); x++) {
            for (int y = -downRange.getValue(); y <= 0; y++) {
                for (int z = -range.getValue(); z <= range.getValue(); z++) {
                    BlockPos pos = playerBlockPos.offset(x, y, z);
                    Hole hole = HoleUtils.getHole(pos, singleHoles.getValue(), doubleHoles.getValue(), quadHoles.getValue(), depth.getValue(), true);

                    if (hole.type == HoleType.NotHole) continue;
                    if (y == 0 && inHole(hole)) return hole;

                    if (closest == null || hole.middle.distanceTo(playerPos) < closest.middle.distanceTo(playerPos)) {
                        closest = hole;
                    }
                }
            }
        }

        return closest;
    }

    private boolean inHole(Hole hole) {
        BlockPos playerPos = mc.player.blockPosition();
        for (BlockPos pos : hole.positions) {
            if (playerPos.equals(pos)) return true;
        }
        return false;
    }

    private boolean hasBlockCollision(AABB box) {
        return !mc.level.noBlockCollision(mc.player, box);
    }

    private float getAngle(Vec3 pos) {
        double deltaX = pos.x - mc.player.getX();
        double deltaZ = pos.z - mc.player.getZ();
        float targetYaw = (float) Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0f;
        return mc.player.getYRot() + Mth.wrapDegrees(targetYaw - mc.player.getYRot());
    }

    private double getSpeed() {
        return boostLeft > 0 ? boostedSpeed.getValue() : speed.getValue();
    }

}
