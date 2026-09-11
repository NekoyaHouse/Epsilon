package com.github.epsilon.modules.impl.movement;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.bus.EventPriority;
import com.github.epsilon.events.impl.KeyboardInputEvent;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.graphics.schedulers.render3d.Render3DScheduler;
import com.github.epsilon.managers.FriendManager;
import com.github.epsilon.managers.rotation.RotationManager;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.modules.impl.combat.KillAura;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.rotation.Priority;
import com.github.epsilon.utils.rotation.Rot2f;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class TargetStrafe extends Module {

    public static final TargetStrafe INSTANCE = new TargetStrafe();

    private TargetStrafe() {
        super("Target Strafe", Category.MOVEMENT);
    }

    private final DoubleSetting radius = doubleSetting("Radius", 1.0, 0.0, 6.0, 0.1);
    private final IntSetting points = intSetting("Points", 6, 3, 24, 1);
    private final BoolSetting requireJump = boolSetting("Require Jump", true);
    private final BoolSetting speedOnly = boolSetting("Speed Only", true);
    private final BoolSetting drawRadius = boolSetting("Draw Radius", true);

    private LivingEntity target;
    private float targetYaw = Float.NaN;
    private int direction = 1;

    @Override
    protected void onDisable() {
        resetTarget();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onTick(PlayerTickEvent.Pre event) {
        boolean left = mc.options.keyLeft.isDown();
        boolean right = mc.options.keyRight.isDown();
        if (left ^ right) {
            direction = left ? 1 : -1;
        }

        if (speedOnly.getValue() && !Speed.INSTANCE.isEnabled() && !Flight.INSTANCE.isEnabled()) {
            resetTarget();
            return;
        }

        if (requireJump.getValue() && (mc.gui.screen() != null || !mc.options.keyJump.isDown())) {
            resetTarget();
            return;
        }

        KillAura killAura = KillAura.INSTANCE;
        if (killAura.isEnabled() && !Scaffold.INSTANCE.isEnabled() && !mc.player.isUsingItem() && !mc.player.isBlocking()) {
            target = killAura.target;
        } else {
            resetTarget();
        }

        if (target == null) {
            targetYaw = Float.NaN;
            return;
        }

        List<OrbitOffset> offsets = createOrbitOffsets();
        int closestIndex = findClosestIndex(offsets);
        if (closestIndex < 0) {
            resetTarget();
            return;
        }

        if (mc.player.horizontalCollision) {
            direction *= -1;
        }

        int nextIndex = wrapIndex(closestIndex + direction, offsets.size());
        Vec3 nextPosition = getOrbitPosition(offsets.get(nextIndex));
        if (isOverVoid(nextPosition.x, nextPosition.z)) {
            direction *= -1;
            nextIndex = wrapIndex(closestIndex + direction, offsets.size());
            nextPosition = getOrbitPosition(offsets.get(nextIndex));
        }

        double deltaX = nextPosition.x - mc.player.getX();
        double deltaZ = nextPosition.z - mc.player.getZ();

        targetYaw = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0f);

        RotationManager.INSTANCE.setRotations(new Rot2f(targetYaw, RotationManager.INSTANCE.getPitch()), 180.0f, Priority.Low);
    }

    @EventHandler
    private void onKeyboardInput(KeyboardInputEvent event) {
        if (Float.isNaN(targetYaw) || (event.getForward() == 0.0f && event.getStrafe() == 0.0f)) return;
        event.setStrafe(0.0f);
        event.setForward(1.0f);
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!drawRadius.getValue() || target == null) return;

        Color color = getTargetColor(target);
        drawCircle(target, darken(color, 0.2f), 3.0f);
        drawCircle(target, color, 1.5f);
    }

    private List<OrbitOffset> createOrbitOffsets() {
        int pointCount = points.getValue();
        double orbitRadius = radius.getValue();
        List<OrbitOffset> offsets = new ArrayList<>(pointCount);
        for (int i = 0; i < pointCount; i++) {
            double angle = i * (Math.PI * 2.0 / pointCount);
            offsets.add(new OrbitOffset(orbitRadius * Math.cos(angle), orbitRadius * Math.sin(angle)));
        }
        return offsets;
    }

    private int findClosestIndex(List<OrbitOffset> offsets) {
        double closestDistance = Double.MAX_VALUE;
        int closestIndex = -1;
        for (int i = 0; i < offsets.size(); i++) {
            Vec3 position = getOrbitPosition(offsets.get(i));
            double distance = mc.player.distanceToSqr(position.x, mc.player.getY(), position.z);
            if (distance < closestDistance) {
                closestDistance = distance;
                closestIndex = i;
            }
        }
        return closestIndex;
    }

    private Vec3 getOrbitPosition(OrbitOffset offset) {
        return new Vec3(target.getX() + offset.x(), mc.player.getY(), target.getZ() + offset.z());
    }

    private boolean isOverVoid(double x, double z) {
        if (mc.player.isInWater() || mc.player.isInLava()) return false;

        AABB box = new AABB(
                x - 0.015,
                mc.player.getY(),
                z - 0.015,
                x + 0.015,
                mc.player.getY() + mc.player.getBbHeight(),
                z + 0.015
        );
        int minY = Mth.floor(box.minY);
        if (minY < mc.level.getMinY()) return true;

        int minX = Mth.floor(box.minX);
        int maxX = Mth.floor(box.maxX + 1.0);
        int minZ = Mth.floor(box.minZ);
        int maxZ = Mth.floor(box.maxZ + 1.0);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int blockX = minX; blockX < maxX; blockX++) {
            for (int blockZ = minZ; blockZ < maxZ; blockZ++) {
                for (int blockY = minY; blockY >= mc.level.getMinY(); blockY--) {
                    BlockState state = mc.level.getBlockState(pos.set(blockX, blockY, blockZ));
                    if (!state.canBeReplaced()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private int wrapIndex(int index, int size) {
        if (index < 0) return size - 1;
        return index >= size ? 0 : index;
    }

    private Color getTargetColor(LivingEntity entity) {
        if (entity instanceof Player player) {
            if (FriendManager.INSTANCE.isFriend(player)) {
                return new Color(85, 255, 85);
            } else {
                new Color(entity.getTeamColor());
            }
        }
        return Color.WHITE;
    }

    private Color darken(Color color, float amount) {
        float factor = 1.0f - Mth.clamp(amount, 0.0f, 1.0f);
        return new Color(
                Math.round(color.getRed() * factor),
                Math.round(color.getGreen() * factor),
                Math.round(color.getBlue() * factor),
                color.getAlpha()
        );
    }

    private void drawCircle(LivingEntity entity, Color color, float width) {
        float tickDelta = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        double x = Mth.lerp(tickDelta, entity.xOld, entity.getX());
        double y = Mth.lerp(tickDelta, entity.yOld, entity.getY());
        double z = Mth.lerp(tickDelta, entity.zOld, entity.getZ());
        int pointCount = points.getValue();
        double orbitRadius = radius.getValue();

        for (int i = 0; i < pointCount; i++) {
            double angle = i * (Math.PI * 2.0 / pointCount);
            double nextAngle = (i + 1) * (Math.PI * 2.0 / pointCount);
            Vec3 from = new Vec3(x + Math.cos(angle) * orbitRadius, y, z + Math.sin(angle) * orbitRadius);
            Vec3 to = new Vec3(x + Math.cos(nextAngle) * orbitRadius, y, z + Math.sin(nextAngle) * orbitRadius);
            Render3DScheduler.INSTANCE.addLine(from, to, color, width);
        }
    }

    private void resetTarget() {
        target = null;
        targetYaw = Float.NaN;
    }

    private record OrbitOffset(double x, double z) {
    }

}
