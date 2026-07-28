package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.graphics.schedulers.render3d.Render3DScheduler;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.managers.impl.target.TargetRequest;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.modules.impl.movement.Scaffold;
import com.github.epsilon.modules.impl.movement.Stuck;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.player.FindItemResult;
import com.github.epsilon.utils.player.InvUtils;
import com.github.epsilon.utils.rotation.Priority;
import com.github.epsilon.utils.rotation.Rot2f;
import com.github.epsilon.utils.timer.TimerUtils;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class AutoThrow extends Module {

    public static final AutoThrow INSTANCE = new AutoThrow();

    private static final double PROJECTILE_SPEED = 1.5;
    private static final double PROJECTILE_GRAVITY = 0.03;
    private static final double PROJECTILE_DRAG = 0.99;
    private static final int MAX_FLIGHT_TICKS = 60;

    private AutoThrow() {
        super("Auto Throw", Category.COMBAT);
    }

    private final DoubleSetting minDistance = doubleSetting("Min Distance", 2.0, 0.0, 12.0, 0.5);
    private final DoubleSetting maxDistance = doubleSetting("Max Distance", 10.0, 1.0, 30.0, 0.5);
    private final DoubleSetting fov = doubleSetting("FOV", 360.0, 0.0, 360.0, 1.0);
    private final DoubleSetting delay = doubleSetting("Delay", 400.0, 0.0, 2000.0, 10.0);
    private final DoubleSetting prediction = doubleSetting("Prediction", 1.0, 0.0, 2.0, 0.05);
    private final BoolSetting swingHand = boolSetting("Swing Hand", true);
    private final IntSetting extrapolation = intSetting("Target Extrapolation", 2, 0, 20, 1);
    private final IntSetting extrapolationSmoothing = intSetting("Target Extrapolation Smoothening", 2, 1, 20, 1);
    private final IntSetting selfExtrapolation = intSetting("Self Extrapolation", 2, 0, 20, 1);
    private final IntSetting selfExtrapolationSmoothing = intSetting("Self Extrapolation Smoothening", 2, 1, 20, 1);
    private final BoolSetting renderExtrapolation = boolSetting("Render Extrapolation", true);
    private final BoolSetting renderSelfExtrapolation = boolSetting("Render Self Extrapolation", true);

    private boolean pendingUse;
    private boolean pendingSwitchBack;
    private Rot2f pendingRotation;
    private Player extrapolatedTarget;
    private AABB previousExtrapolatedTargetBox;
    private AABB extrapolatedTargetBox;
    private AABB previousExtrapolatedSelfBox;
    private AABB extrapolatedSelfBox;

    private final TimerUtils timer = new TimerUtils();

    @Override
    protected void onEnable() {
        timer.reset();
        resetState();
    }

    @Override
    protected void onDisable() {
        InvUtils.swapBack();
        resetState();
    }

    @EventHandler
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        if (Scaffold.INSTANCE.isEnabled()) return;

        if (pendingUse) {
            if (pendingRotation != null) {
                Managers.ROTATION.setRotations(pendingRotation, 180.0, Priority.Low);
            }
            // 操你妈逼这用不了 MultiPlayerGameMode 的 useItem()
            mc.getConnection().send(new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, mc.level.getBlockStatePredictionHandler().startPredicting().currentSequence(), Managers.ROTATION.getYaw(), Managers.ROTATION.getPitch()));
            if (swingHand.getValue()) {
                mc.player.swing(InteractionHand.MAIN_HAND);
            } else {
                mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
            }
            pendingUse = false;
            pendingSwitchBack = true;
            return;
        }

        if (pendingSwitchBack) {
            InvUtils.swapBack();
            pendingSwitchBack = false;
            pendingRotation = null;
            clearExtrapolation();
            timer.reset();
            return;
        }

        Player target = findTarget();
        updateExtrapolation(target);

        if (!timer.passedMillise(delay.getValue())) return;
        if (target == null) return;

        FindItemResult result = InvUtils.findInHotbar(stack -> stack.is(Items.SNOWBALL) || stack.is(Items.EGG));
        if (!result.found()) return;

        Rot2f rotation = calculateThrowRotation(target);
        if (rotation == null) return;

        if (Stuck.INSTANCE.isEnabled()) return;

        Managers.ROTATION.setRotations(rotation, 180.0, Priority.Low);
        InvUtils.swap(result.slot(), true);
        pendingRotation = rotation;
        pendingUse = true;
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        AABB targetRenderBox = interpolateBox(previousExtrapolatedTargetBox, extrapolatedTargetBox, partialTick);
        AABB selfRenderBox = interpolateBox(previousExtrapolatedSelfBox, extrapolatedSelfBox, partialTick);

        if (renderExtrapolation.getValue() && targetRenderBox != null) {
            Render3DScheduler.INSTANCE.addFilledBox(targetRenderBox, 0x40FF5555);
            Render3DScheduler.INSTANCE.addOutlineBox(targetRenderBox, 0xD0FF5555);
        }
        if (renderSelfExtrapolation.getValue() && selfRenderBox != null) {
            Render3DScheduler.INSTANCE.addFilledBox(selfRenderBox, 0x4055AAFF);
            Render3DScheduler.INSTANCE.addOutlineBox(selfRenderBox, 0xD055AAFF);
        }
    }

    private Player findTarget() {
        double range = maxDistance.getValue();
        double rangeSq = range * range;

        List<LivingEntity> candidates = Managers.TARGET.acquireTargets(TargetRequest.of(
                range,
                fov.getValue().floatValue(),
                true,
                false,
                false,
                false,
                false,
                false,
                false,
                true,
                false,
                64
        ));

        Player best = null;
        double minDstSq = Double.MAX_VALUE;
        for (Entity entity : candidates) {
            double distSq = mc.player.distanceToSqr(entity);
            if (distSq > rangeSq) continue;

            double dx = entity.getX() - mc.player.getX();
            double dz = entity.getZ() - mc.player.getZ();
            if (Math.sqrt(dx * dx + dz * dz) < minDistance.getValue()) continue;

            if (distSq < minDstSq) {
                minDstSq = distSq;
                best = (Player) entity;
            }
        }
        return best;
    }

    private Rot2f calculateThrowRotation(Player target) {
        if (target != extrapolatedTarget) {
            updateExtrapolation(target);
        }
        if (extrapolatedTargetBox == null || extrapolatedSelfBox == null) return null;

        Vec3 selfCenter = extrapolatedSelfBox.getCenter();
        Vec3 source = new Vec3(
                selfCenter.x,
                extrapolatedSelfBox.minY + mc.player.getEyeHeight() - 0.1,
                selfCenter.z
        );
        Vec3 destination = extrapolatedTargetBox.getCenter();
        Vec3 targetDelta = destination.subtract(source);

        Vec3 sourceMovement = mc.player.getKnownMovement();
        if (mc.player.onGround()) {
            sourceMovement = new Vec3(sourceMovement.x, 0.0, sourceMovement.z);
        }

        Rot2f bestRotation = null;
        double bestError = Double.MAX_VALUE;
        double dragPower = 1.0;
        double displacementFactor = 0.0;
        double gravityVelocity = 0.0;
        double gravityDisplacement = 0.0;

        // ThrowableProjectile 每 tick 先施加重力，再乘空气阻力，最后移动。
        for (int tick = 1; tick <= MAX_FLIGHT_TICKS; tick++) {
            dragPower *= PROJECTILE_DRAG;
            displacementFactor += dragPower;
            gravityVelocity = (gravityVelocity - PROJECTILE_GRAVITY) * PROJECTILE_DRAG;
            gravityDisplacement += gravityVelocity;

            Vec3 requiredInitialMovement = new Vec3(
                    targetDelta.x / displacementFactor,
                    (targetDelta.y - gravityDisplacement) / displacementFactor,
                    targetDelta.z / displacementFactor
            );
            Vec3 requiredProjectileMovement = requiredInitialMovement.subtract(sourceMovement);
            if (requiredProjectileMovement.lengthSqr() < 1.0E-8) continue;

            Vec3 projectileMovement = requiredProjectileMovement.normalize().scale(PROJECTILE_SPEED);
            Vec3 actualInitialMovement = projectileMovement.add(sourceMovement);
            Vec3 actualDisplacement = new Vec3(
                    actualInitialMovement.x * displacementFactor,
                    actualInitialMovement.y * displacementFactor + gravityDisplacement,
                    actualInitialMovement.z * displacementFactor
            );
            double error = actualDisplacement.distanceToSqr(targetDelta);
            if (error >= bestError) continue;

            double horizontalMovement = projectileMovement.horizontalDistance();
            float yaw = (float) Math.toDegrees(Math.atan2(-projectileMovement.x, projectileMovement.z));
            float pitch = (float) Math.toDegrees(Math.atan2(-projectileMovement.y, horizontalMovement));
            bestRotation = new Rot2f(yaw, pitch);
            bestError = error;
        }

        return bestRotation;
    }

    private void updateExtrapolation(Player target) {
        AABB nextSelfBox = Managers.EXTRAPOLATION.extrapolate(
                mc.player,
                selfExtrapolation.getValue(),
                selfExtrapolationSmoothing.getValue()
        );
        if (nextSelfBox == null) {
            nextSelfBox = mc.player.getBoundingBox();
        }
        previousExtrapolatedSelfBox = extrapolatedSelfBox == null ? nextSelfBox : extrapolatedSelfBox;
        extrapolatedSelfBox = nextSelfBox;

        if (!(target instanceof AbstractClientPlayer clientTarget)) {
            extrapolatedTarget = null;
            previousExtrapolatedTargetBox = null;
            extrapolatedTargetBox = null;
            return;
        }

        AABB currentTargetBox = target.getBoundingBox();
        AABB targetBox = Managers.EXTRAPOLATION.extrapolate(
                clientTarget,
                extrapolation.getValue(),
                extrapolationSmoothing.getValue()
        );
        if (targetBox == null) {
            targetBox = currentTargetBox;
        }

        // Prediction 是目标外推位移倍率：0 为当前位置，1 为完整外推结果。
        Vec3 targetOffset = targetBox.getCenter()
                .subtract(currentTargetBox.getCenter())
                .scale(prediction.getValue());
        Vec3 predictedTargetCenter = currentTargetBox.getCenter().add(targetOffset);
        AABB nextTargetBox = targetBox.move(predictedTargetCenter.subtract(targetBox.getCenter()));

        previousExtrapolatedTargetBox = extrapolatedTarget == target && extrapolatedTargetBox != null
                ? extrapolatedTargetBox
                : nextTargetBox;
        extrapolatedTargetBox = nextTargetBox;
        extrapolatedTarget = target;
    }

    private AABB interpolateBox(AABB previous, AABB current, float partialTick) {
        if (current == null) return null;
        if (previous == null) return current;

        return new AABB(
                Mth.lerp(partialTick, previous.minX, current.minX),
                Mth.lerp(partialTick, previous.minY, current.minY),
                Mth.lerp(partialTick, previous.minZ, current.minZ),
                Mth.lerp(partialTick, previous.maxX, current.maxX),
                Mth.lerp(partialTick, previous.maxY, current.maxY),
                Mth.lerp(partialTick, previous.maxZ, current.maxZ)
        );
    }

    private void clearExtrapolation() {
        extrapolatedTarget = null;
        previousExtrapolatedTargetBox = null;
        extrapolatedTargetBox = null;
        previousExtrapolatedSelfBox = null;
        extrapolatedSelfBox = null;
    }

    private void resetState() {
        pendingUse = false;
        pendingSwitchBack = false;
        pendingRotation = null;
        clearExtrapolation();
    }

}
