package com.github.epsilon.modules.impl.movement;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.bus.EventPriority;
import com.github.epsilon.events.impl.MoveEvent;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class SafeWalk extends Module {

    public static final SafeWalk INSTANCE = new SafeWalk();

    private SafeWalk() {
        super("Safe Walk", Category.MOVEMENT);
    }

    private final DoubleSetting motion = doubleSetting("Motion", 1.0, 0.5, 1.0, 0.05);
    private final DoubleSetting speedMotion = doubleSetting("Speed Motion", 1.0, 0.5, 1.5, 0.05);
    private final BoolSetting air = boolSetting("Air", false);
    private final BoolSetting directionCheck = boolSetting("Direction Check", true);
    private final BoolSetting pitchCheck = boolSetting("Pitch Check", true);
    private final BoolSetting requirePress = boolSetting("Require Press", false);
    private final BoolSetting blocksOnly = boolSetting("Blocks Only", true);

    @EventHandler
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        if (!mc.player.onGround() || !isMovementInputPressed()) return;

        double motionX = mc.player.getDeltaMovement().x;
        double motionZ = mc.player.getDeltaMovement().z;
        if (!canSafeWalk(motionX, motionZ)) return;

        double multiplier = mc.player.hasEffect(MobEffects.SPEED) ? speedMotion.getValue() : motion.getValue();
        if (multiplier == 1.0) return;

        mc.player.setDeltaMovement(
                motionX * multiplier,
                mc.player.getDeltaMovement().y,
                motionZ * multiplier
        );
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onMove(MoveEvent event) {
        if (!canSafeWalk(event.getX(), event.getZ())) return;

        double movementX = event.getX();
        double movementZ = event.getZ();
        double stepX = Math.signum(movementX) * 0.05;
        double stepZ = Math.signum(movementZ) * 0.05;
        float maxDownStep = mc.player.maxUpStep();

        while (movementX != 0.0 && canFallAtLeast(movementX, 0.0, maxDownStep)) {
            if (Math.abs(movementX) <= 0.05) {
                movementX = 0.0;
                break;
            }
            movementX -= stepX;
        }

        while (movementZ != 0.0 && canFallAtLeast(0.0, movementZ, maxDownStep)) {
            if (Math.abs(movementZ) <= 0.05) {
                movementZ = 0.0;
                break;
            }
            movementZ -= stepZ;
        }

        while (movementX != 0.0 && movementZ != 0.0 && canFallAtLeast(movementX, movementZ, maxDownStep)) {
            if (Math.abs(movementX) <= 0.05) {
                movementX = 0.0;
            } else {
                movementX -= stepX;
            }

            if (Math.abs(movementZ) <= 0.05) {
                movementZ = 0.0;
            } else {
                movementZ -= stepZ;
            }
        }

        event.setX(movementX);
        event.setZ(movementZ);
        event.cancel();
    }

    private boolean canSafeWalk(double movementX, double movementZ) {
        if (Scaffold.INSTANCE.isEnabled()) return false;
        if (directionCheck.getValue() && mc.options.keyUp.isDown()) return false;
        if (pitchCheck.getValue() && mc.player.getXRot() < 69.0F) return false;
        if (blocksOnly.getValue() && !isHoldingBlock()) return false;
        if (requirePress.getValue() && !mc.options.keyUse.isDown()) return false;

        return mc.player.onGround() && canMove(movementX, movementZ, -1.0) || air.getValue() && canMove(movementX, movementZ, -2.0);
    }

    private boolean canMove(double movementX, double movementZ, double movementY) {
        return mc.level.noCollision(
                mc.player,
                mc.player.getBoundingBox().move(movementX, movementY, movementZ)
        );
    }

    private boolean canFallAtLeast(double movementX, double movementZ, double minHeight) {
        AABB boundingBox = mc.player.getBoundingBox();
        return mc.level.noCollision(
                mc.player,
                new AABB(
                        boundingBox.minX + 1.0E-7 + movementX,
                        boundingBox.minY - minHeight - 1.0E-7,
                        boundingBox.minZ + 1.0E-7 + movementZ,
                        boundingBox.maxX - 1.0E-7 + movementX,
                        boundingBox.minY,
                        boundingBox.maxZ - 1.0E-7 + movementZ
                )
        );
    }

    private boolean isMovementInputPressed() {
        if (mc.options.keyUp.isDown() != mc.options.keyDown.isDown()) return true;
        return mc.options.keyLeft.isDown() != mc.options.keyRight.isDown();
    }

    private boolean isHoldingBlock() {
        ItemStack stack = mc.player.getMainHandItem();
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) return false;

        Block block = blockItem.getBlock();
        BlockState state = block.defaultBlockState();
        if (block instanceof EntityBlock || state.getMenuProvider(mc.level, BlockPos.ZERO) != null) return false;
        if (!state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) return false;

        return !(block instanceof FallingBlock)
                && !(block instanceof PumpkinBlock)
                && !(block instanceof CarvedPumpkinBlock)
                && !(block instanceof SlimeBlock)
                && !(block instanceof TntBlock);
    }

}
