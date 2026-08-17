package com.github.epsilon.modules.impl.player.helper;

import com.github.epsilon.managers.NotificationManager;
import com.github.epsilon.managers.rotation.RotationManager;
import com.github.epsilon.utils.player.FindItemResult;
import com.github.epsilon.utils.player.InvUtils;
import com.github.epsilon.utils.rotation.Rot2f;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import static com.github.epsilon.Constants.mc;

public class SelfExtinguish extends HelperBase {

    private static final int AIM_TIMEOUT_TICKS = 5;

    private Rot2f targetRotation;
    private int savedSlot = -1;
    private BlockPos waterBlockPos;
    private boolean aiming;
    private boolean shouldPlaceWater;
    private int aimTimeout;
    private InteractionHand waterBucketHand;

    public SelfExtinguish() {
        super("Self Extinguish");
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
    public void onTick() {
        if (mc.player.isOnFire()) {
            if (aiming && mc.level.getBlockCollisions(mc.player, mc.player.getBoundingBox().move(0.0, mc.player.getDeltaMovement().y, 0.0)).iterator().hasNext()) {
                shouldPlaceWater = true;
            } else if (!aiming && waterBlockPos == null && mc.player.onGround()) {
                beginExtinguishing();
            }
        }

        if (aiming && waterBlockPos == null && aimTimeout > 0 && --aimTimeout == 0) {
            stopAiming();
            restoreSlot();
        }
    }

    @Override
    public void onPreMotion() {
        if (shouldPlaceWater) {
            if (!Helper.isRotationAtTarget(targetRotation)) return;

            shouldPlaceWater = false;
            BlockHitResult hit = getManagedBlockHit();
            if (hit == null || hit.getDirection() != Direction.UP) {
                log("Failed to place water!");
                stopAiming();
                restoreSlot();
                return;
            }

            waterBlockPos = hit.getBlockPos().above().immutable();
            InteractionResult result = mc.gameMode.useItem(mc.player, waterBucketHand);
            if (result.consumesAction()) {
                Helper.markWaterPlaced(waterBlockPos);
            } else {
                waterBlockPos = null;
                log("Failed to place water!");
                stopAiming();
                restoreSlot();
            }
            return;
        }

        if (waterBlockPos != null) {
            recycleWater();
        }
    }

    private void beginExtinguishing() {
        FindItemResult waterBucket = InvUtils.findInHotbar(Items.WATER_BUCKET);
        if (!waterBucket.found()) return;

        waterBucketHand = waterBucket.getHand();
        if (waterBucket.isMainHand()) {
            savedSlot = mc.player.getInventory().getSelectedSlot();
            mc.player.getInventory().setSelectedSlot(waterBucket.slot());
        }
        aiming = true;
        targetRotation = new Rot2f(mc.player.getYRot(), 90.0F);
        aimTimeout = AIM_TIMEOUT_TICKS;
    }

    private void recycleWater() {
        stopAiming();

        BlockHitResult hit = getManagedBlockHit();
        if (hit != null && hit.getBlockPos().above().equals(waterBlockPos)) {
            FindItemResult bucket = InvUtils.findInHotbar(Items.BUCKET);
            if (bucket.found()) {
                InteractionHand hand = bucket.getHand();
                if (bucket.isMainHand()) {
                    mc.player.getInventory().setSelectedSlot(bucket.slot());
                }
                InteractionResult result = mc.gameMode.useItem(mc.player, hand);
                if (!result.consumesAction()) {
                    log("Failed to recycle water!");
                }
            } else {
                log("Failed to recycle water: no empty bucket found!");
            }
            Helper.removeWaterPlacement(waterBlockPos);
        } else {
            log("Failed to recycle water due to moving!");
        }

        restoreSlot();
        waterBlockPos = null;
    }

    private BlockHitResult getManagedBlockHit() {
        HitResult hit = RotationManager.INSTANCE.getHitResult();
        return hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK ? blockHit : null;
    }

    private void stopAiming() {
        aiming = false;
        shouldPlaceWater = false;
        targetRotation = null;
        aimTimeout = 0;
        waterBucketHand = null;
    }

    private void restoreSlot() {
        if (mc.player != null && savedSlot != -1) {
            mc.player.getInventory().setSelectedSlot(savedSlot);
        }
        savedSlot = -1;
    }

    private void reset() {
        restoreSlot();
        waterBlockPos = null;
        stopAiming();
    }

    private void log(String message) {
        NotificationManager.INSTANCE.error(Helper.INSTANCE.selfExtinguish.getTranslateComponent().getTranslatedName(), message);
    }

    @Override
    public boolean isActive() {
        return aiming && targetRotation != null;
    }

    @Override
    public Rot2f getTargetRotation() {
        return targetRotation;
    }

}
