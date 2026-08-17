package com.github.epsilon.modules.impl.player.helper;

import com.github.epsilon.graphics.schedulers.render3d.Render3DScheduler;
import com.github.epsilon.managers.rotation.RotationManager;
import com.github.epsilon.utils.rotation.Rot2f;
import com.github.epsilon.utils.rotation.RotationUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.awt.*;

import static com.github.epsilon.Constants.mc;

public class ExtinguishFire extends HelperBase {

    private BlockPos firePos;
    private Rot2f targetRotation;
    private boolean aiming;
    private int aimDelay;

    public ExtinguishFire() {
        super("Extinguish Fire");
    }

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        reset();
    }

    @Override
    public void onMotion() {
        if (mc.player.isUsingItem()) return;

        if (firePos == null) {
            findFirePos();
            if (firePos != null) {
                aiming = true;
                aimDelay = 2;
            }
        }

        if (firePos == null || !mc.level.getBlockState(firePos).is(Blocks.FIRE) || mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(firePos)) > 25.0) {
            reset();
            return;
        }

        Vec3 fireAimPos = findAimPoint(firePos);
        if (fireAimPos == null) {
            reset();
            return;
        }
        targetRotation = RotationUtils.calculate(fireAimPos);

        if (!aiming) {
            aiming = true;
            aimDelay = 2;
        }

        if (aimDelay > 0) {
            aimDelay--;
            return;
        }

        HitResult hitResult = RotationManager.INSTANCE.getHitResult();
        if (hitResult instanceof BlockHitResult blockHit && blockHit.getBlockPos().equals(firePos)) {
            mc.gameMode.startDestroyBlock(firePos, RotationUtils.getDirection(firePos));
            mc.player.swing(InteractionHand.MAIN_HAND);
            aiming = false;
            targetRotation = null;
        } else if (Helper.isRotationAtTarget(targetRotation)) {
            reset();
        }
    }

    @Override
    public void onRender() {
        if (firePos == null || !aiming) return;
        Render3DScheduler.INSTANCE.addFilledBox(new AABB(firePos), new Color(255, 0, 0, 64));
        Render3DScheduler.INSTANCE.addOutlineBox(new AABB(firePos), new Color(255, 0, 0, 190));
    }

    private void findFirePos() {
        BlockPos base = mc.player.blockPosition();
        Vec3 eye = mc.player.getEyePosition();
        firePos = null;

        double closest = Double.MAX_VALUE;
        for (int dx = -8; dx <= 8; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -8; dz <= 8; dz++) {
                    BlockPos candidate = base.offset(dx, dy, dz);
                    if (!mc.level.getBlockState(candidate).is(Blocks.FIRE) || Vec3.atCenterOf(candidate).distanceToSqr(eye) > 25.0)
                        continue;
                    Vec3 aimPoint = findAimPoint(candidate);
                    if (aimPoint == null) continue;
                    double distance = Vec3.atCenterOf(candidate).distanceToSqr(eye);
                    if (distance < closest) {
                        closest = distance;
                        firePos = candidate;
                    }
                }
            }
        }
    }

    private Vec3 findAimPoint(BlockPos pos) {
        Vec3 eyePos = mc.player.getEyePosition();
        Vec3 closestPoint = null;
        double closestDistance = Double.MAX_VALUE;
        for (AABB box : mc.level.getBlockState(pos).getShape(mc.level, pos).toAabbs()) {
            Vec3 point = box.move(pos).getCenter();
            BlockHitResult hit = mc.level.clip(new ClipContext(eyePos, point, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
            if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(pos)) continue;
            double distance = point.distanceToSqr(eyePos);
            if (distance < closestDistance) {
                closestDistance = distance;
                closestPoint = point;
            }
        }
        return closestPoint;
    }

    private void reset() {
        firePos = null;
        targetRotation = null;
        aiming = false;
        aimDelay = 0;
    }

    @Override
    public boolean isActive() {
        return targetRotation != null;
    }

    @Override
    public Rot2f getTargetRotation() {
        return targetRotation;
    }

}
