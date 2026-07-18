package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.player.FindItemResult;
import com.github.epsilon.utils.player.InvUtils;
import com.github.epsilon.utils.rotation.Priority;
import com.github.epsilon.utils.rotation.Rot2f;
import com.github.epsilon.utils.rotation.RotationUtils;
import com.github.epsilon.utils.world.BlockUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;

public class CrystalBlocker extends Module {

    public static final CrystalBlocker INSTANCE = new CrystalBlocker();

    private CrystalBlocker() {
        super("Crystal Blocker", Category.COMBAT);
    }

    private final DoubleSetting range = doubleSetting("Range", 4.0, 1.0, 6.0, 0.1);
    private final EnumSetting<RotateMode> rotate = enumSetting("Rotate", RotateMode.Silent);
    private final EnumSetting<SwitchMode> switchMode = enumSetting("Switch", SwitchMode.Visible);
    private final IntSetting delay = intSetting("Delay", 2, 0, 20, 1);
    private final IntSetting visibleSwapBackDelay = intSetting("Swap Back Delay", 0, 0, 20, 1, () -> switchMode.is(SwitchMode.Visible));
    private final IntSetting rotationSpeed = intSetting("Rotation Speed", 180, 10, 180, 10, () -> rotate.is(RotateMode.Silent));

    private int timer = 0;
    private boolean waitingSwapBack = false;
    private int swapBackTicks = 0;
    private int savedOldSlot = -1;

    public enum RotateMode {
        None, Normal, Silent
    }

    public enum SwitchMode {
        Visible, Silent
    }

    @EventHandler
    private void onTick(PlayerTickEvent.Pre event) {
        if (!mc.player.onGround()) return;

        if (waitingSwapBack) {
            if (swapBackTicks > 0) {
                swapBackTicks--;
                if (swapBackTicks > 0) return;
            }
            if (savedOldSlot >= 0) {
                mc.player.getInventory().setSelectedSlot(savedOldSlot);
            }
            waitingSwapBack = false;
            savedOldSlot = -1;
            return;
        }

        if (timer > 0) {
            timer--;
        }

        // 1. Find crystals within range
        EndCrystal targetCrystal = mc.level.getEntitiesOfClass(EndCrystal.class,
                        mc.player.getBoundingBox().inflate(range.getValue()),
                        crystal -> Math.abs(crystal.getY() - mc.player.getY()) < 1.0)
                .stream()
                .min(Comparator.comparingDouble(c -> mc.player.distanceTo(c)))
                .orElse(null);

        if (targetCrystal == null) return;

        // 2.1 Check if already blocked by obsidian
        Vec3 eyes = mc.player.getEyePosition();
        Vec3 towards = targetCrystal.position().add(0, 0.5, 0);
        HitResult hit = mc.level.clip(new ClipContext(
                eyes,
                towards,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                mc.player
        ));
        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockPos hitPos = ((BlockHitResult) hit).getBlockPos();
            if (mc.level.getBlockState(hitPos).is(Blocks.OBSIDIAN)) return;
        }

        // 2. Calculate placement position
        Vec3 crystalPos = targetCrystal.position();
        Vec3 playerPos = mc.player.position();

        Vec3 midPoint = playerPos.lerp(crystalPos, 0.5);
        BlockPos placePos = BlockPos.containing(midPoint.x, mc.player.getY(), midPoint.z);

        if (placePos.equals(mc.player.blockPosition()) || placePos.equals(targetCrystal.blockPosition())) {
            Vec3 dir = crystalPos.subtract(playerPos).normalize();
            placePos = BlockPos.containing(playerPos.x + dir.x, mc.player.getY(), playerPos.z + dir.z);
        }

        // 3. Check if placeable
        if (!BlockUtils.canPlaceAt(placePos)) return;
        if (!mc.level.getBlockState(placePos.below()).isSolid()) return;

        // 4. Find obsidian
        FindItemResult obsidian = InvUtils.findInHotbar(Items.OBSIDIAN);
        if (!obsidian.found()) return;

        // 5. Rotation and placement
        Rot2f rot = RotationUtils.calculate(mc.player.getEyePosition(), Vec3.atCenterOf(placePos));
        boolean readyToPlace = true;

        if (rotate.is(RotateMode.Normal)) {
            mc.player.setYRot(rot.getYaw());
            mc.player.setXRot(Mth.clamp(rot.getPitch(), -90.0f, 90.0f));
        } else if (rotate.is(RotateMode.Silent)) {
            Managers.ROTATION.setRotations(rot, rotationSpeed.getValue(), Priority.Highest);

            if (Managers.ROTATION.rotations != null) {
                double yawDiff = Math.abs(Mth.wrapDegrees(Managers.ROTATION.getYaw() - rot.getYaw()));
                double pitchDiff = Math.abs(Managers.ROTATION.getPitch() - rot.getPitch());
                if (yawDiff > 15 || pitchDiff > 15) {
                    readyToPlace = false;
                }
            }
        }

        if (readyToPlace && timer <= 0) {
            placeBlock(placePos, obsidian);
            timer = delay.getValue();
        }
    }

    private void placeBlock(BlockPos pos, FindItemResult item) {
        int oldSlot = mc.player.getInventory().getSelectedSlot();

        Direction side = RotationUtils.getDirection(pos);
        BlockHitResult bhr = new BlockHitResult(Vec3.atCenterOf(pos), side, pos, false);

        if (switchMode.is(SwitchMode.Visible)) {
            int target = item.slot();
            boolean needSwitch = oldSlot != target;
            if (needSwitch) {
                InvUtils.swap(target, true);
            }

            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, bhr);
            mc.player.swing(InteractionHand.MAIN_HAND);

            if (needSwitch) {
                int backDelay = visibleSwapBackDelay.getValue();
                if (backDelay > 0) {
                    waitingSwapBack = true;
                    swapBackTicks = backDelay;
                    savedOldSlot = oldSlot;
                } else {
                    InvUtils.swapBack();
                }
            }
        } else {
            InvUtils.invSwap(item.slot());
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, bhr);
            mc.player.swing(InteractionHand.MAIN_HAND);
            InvUtils.invSwapBack();
        }
    }
}
