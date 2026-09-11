package com.github.epsilon.modules.impl.movement;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.BedBlock;

public class ReverseStep extends Module {

    public static final ReverseStep INSTANCE = new ReverseStep();

    private ReverseStep() {
        super("Reverse Step", Category.MOVEMENT);
    }

    private final DoubleSetting fallSpeed = doubleSetting("Fall Speed", 3, 0, 10, 1);
    private final DoubleSetting fallDistance = doubleSetting("Fall Distance", 3, 0, 10, 1);
    private final BoolSetting vehicles = boolSetting("Vehicles", false);

    @EventHandler
    private void onTick(PlayerTickEvent.Post event) {
        if (Scaffold.INSTANCE.isEnabled()) return;

        Entity vehicle = mc.player.getVehicle();
        if (vehicle != null && vehicles.getValue()) {
            if (canSnap(vehicle)) {
                vehicle.setDeltaMovement(vehicle.getDeltaMovement().x, -fallSpeed.getValue(), vehicle.getDeltaMovement().z);
            }
        } else {
            if (mc.player.isSuppressingSlidingDownLadder() || mc.player.zza == 0 && mc.player.xxa == 0) return;
            if (!isOnBed() && canSnap(mc.player)) {
                mc.player.setDeltaMovement(mc.player.getDeltaMovement().x, -fallSpeed.getValue(), mc.player.getDeltaMovement().z);
            }
        }
    }

    private boolean canSnap(Entity entity) {
        if (!entity.onGround() || entity.isUnderWater() || entity.isInLava() || mc.options.keyJump.isDown() || entity.noPhysics) {
            return false;
        }
        return !mc.level.noCollision(entity.getBoundingBox().move(0.0, (float) -(fallDistance.getValue() + 0.01), 0.0));
    }

    private boolean isOnBed() {
        BlockPos.MutableBlockPos blockPos = mc.player.blockPosition().mutable();

        if (check(blockPos, 0, 0)) return true;

        double xa = mc.player.getX() - blockPos.getX();
        double za = mc.player.getZ() - blockPos.getZ();

        if (xa >= 0 && xa <= 0.3 && check(blockPos, -1, 0)) return true;
        if (xa >= 0.7 && check(blockPos, 1, 0)) return true;
        if (za >= 0 && za <= 0.3 && check(blockPos, 0, -1)) return true;
        if (za >= 0.7 && check(blockPos, 0, 1)) return true;

        if (xa >= 0 && xa <= 0.3 && za >= 0 && za <= 0.3 && check(blockPos, -1, -1)) return true;
        if (xa >= 0 && xa <= 0.3 && za >= 0.7 && check(blockPos, -1, 1)) return true;
        if (xa >= 0.7 && za >= 0 && za <= 0.3 && check(blockPos, 1, -1)) return true;
        return xa >= 0.7 && za >= 0.7 && check(blockPos, 1, 1);
    }

    private boolean check(BlockPos.MutableBlockPos blockPos, int x, int z) {
        blockPos.move(x, 0, z);
        boolean is = mc.level.getBlockState(blockPos).getBlock() instanceof BedBlock;
        blockPos.move(-x, 0, -z);
        return is;
    }

}
