package com.github.epsilon.modules.impl.movement;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.BlockCollisionEvent;
import com.github.epsilon.events.impl.DestroyBlockEvent;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.modules.impl.player.AutoTool;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.player.FindItemResult;
import com.github.epsilon.utils.player.InvUtils;
import com.github.epsilon.utils.player.MoveUtils;
import com.github.epsilon.utils.rotation.Rot2f;
import com.github.epsilon.utils.rotation.RotationUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;

public class Phase extends Module {

    public static final Phase INSTANCE = new Phase();

    private Phase() {
        super("Phase", Category.MOVEMENT);
    }

    private enum Mode {
        Vanilla,
        Pearl,
        Sunrise,
        ForceMine,
        CCClip
    }

    private enum SwapMode {
        Silent,
        InvSwitch
    }

    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.Vanilla);
    private final EnumSetting<SwapMode> swapMode = enumSetting("Swap Mode", SwapMode.InvSwitch);
    private final BoolSetting pauseOnPhase = boolSetting("Pause On Phase", false, () -> mode.is(Mode.Pearl));
    private final BoolSetting swingHand = boolSetting("Swing Hand", true, () -> mode.is(Mode.Pearl));
    private final BoolSetting silent = boolSetting("Silent", false, () -> mode.is(Mode.Sunrise));
    private final BoolSetting waitBreak = boolSetting("Wait Break", true, () -> mode.is(Mode.Sunrise));
    private final BoolSetting onlyOnGround = boolSetting("Only On Ground", false, () -> mode.is(Mode.Pearl));
    private final BoolSetting autoDisable = boolSetting("Auto Disable", false, () -> mode.is(Mode.Pearl));
    private final IntSetting afterBreak = intSetting("Break Timeout", 4, 1, 20, 1, () -> mode.is(Mode.Sunrise) && waitBreak.getValue());
    private final IntSetting afterPearl = intSetting("Pearl Timeout", 0, 0, 60, 1, () -> mode.is(Mode.Pearl));
    private final DoubleSetting pitch = doubleSetting("Pitch", 80.0, 0.0, 90.0, 1.0, () -> mode.is(Mode.Pearl));
    private final BoolSetting strict = boolSetting("Strict", false, () -> mode.is(Mode.ForceMine));

    public int clipTimer;
    public int afterPearlTime;

    @Override
    protected void onEnable() {
        afterPearlTime = 0;
        clipTimer = 0;

        if (!nullCheck() && mc.player.onGround() && mode.is(Mode.CCClip)) {
            double[] diagonalOffset = MoveUtils.forwardWithoutStrafe(0.44);
            boolean diagonal = mc.player.getYRot() % 90 > 35 && mc.player.getYRot() % 90 < 55;

            mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_SPRINTING));

            if (diagonal) {
                double[] directionVec = MoveUtils.forwardWithoutStrafe(0.51);

                int height = mc.level.clip(
                        new ClipContext(mc.player.getEyePosition(), mc.player.getEyePosition().add(diagonalOffset[0], 0, diagonalOffset[1]), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player)
                ).getType().equals(HitResult.Type.MISS) ? 1 : 2;

                mc.player.setPos(mc.player.getX() + directionVec[0], mc.player.getY() + height, mc.player.getZ() + directionVec[1]);
                mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY(), mc.player.getZ(), true, false));

                height = mc.level.getBlockState(BlockPos.containing(mc.player.position().add(diagonalOffset[0], -2, diagonalOffset[1]))).isAir() ? 2 : 1;

                mc.player.setPos(mc.player.getX() + directionVec[0], mc.player.getY() - height, mc.player.getZ() + directionVec[1]);
                mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY(), mc.player.getZ(), true, false));
                toggle();

            } else {
                double[] directionVec = MoveUtils.forwardWithoutStrafe(0.57);

                int height = mc.level.clip(
                        new ClipContext(mc.player.getEyePosition(), mc.player.getEyePosition().add(diagonalOffset[0], 0, diagonalOffset[1]), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player)
                ).getType().equals(HitResult.Type.MISS) ? 1 : 2;

                mc.player.setPos(mc.player.getX() + directionVec[0], mc.player.getY() + height, mc.player.getZ() + directionVec[1]);
                mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY(), mc.player.getZ(), true, false));

                mc.player.setPos(mc.player.getX() + directionVec[0], mc.player.getY(), mc.player.getZ() + directionVec[1]);
                mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY(), mc.player.getZ(), true, false));

                height = mc.level.getBlockState(BlockPos.containing(mc.player.position().add(diagonalOffset[0], -2, diagonalOffset[1]))).isAir() ? 2 : 1;

                mc.player.setPos(mc.player.getX() + directionVec[0], mc.player.getY() - height, mc.player.getZ() + directionVec[1]);
                mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY(), mc.player.getZ(), true, false));
                toggle();
            }
        }
    }

    @EventHandler
    private void onDestroyBlock(DestroyBlockEvent event) {
        clipTimer = afterBreak.getValue();
    }

    @EventHandler
    private void onCollide(BlockCollisionEvent event) {
        if (nullCheck()) return;

        BlockPos playerPos = BlockPos.containing(mc.player.position());

        if (!mode.is(Mode.CCClip) && !mode.is(Mode.Pearl) && !mode.is(Mode.ForceMine) && canNoClip() || afterPearlTime > 0) {
            if (!event.getPos().equals(playerPos.below()) || mc.options.keyShift.isDown()) {
                event.setState(Blocks.AIR.defaultBlockState());
            }
        }

        if (mode.is(Mode.ForceMine)) {
            float xDelta = Math.abs(playerPos.getX() - event.getPos().getX());
            float zDelta = Math.abs(playerPos.getZ() - event.getPos().getZ());

            if (xDelta != 0 && zDelta != 0 && strict.getValue()) {
                return;
            }

            if (!event.getPos().equals(playerPos.below()) || mc.options.keyShift.isDown()) {
                event.setState(Blocks.AIR.defaultBlockState());
            }
        }
    }

    @EventHandler
    public void onClientTickPre(PlayerTickEvent.Pre event) {
        if (clipTimer > 0) clipTimer--;
        if (afterPearlTime > 0) afterPearlTime--;

        if (mode.getValue() == Mode.Sunrise && (mc.player.horizontalCollision || isPlayerInBlock()) && !mc.player.isInWater() && !mc.player.isInLava() && clipTimer <= 0) {
            double[] dir = MoveUtils.forward(0.5);

            BlockPos blockToBreak = null;

            if (mc.options.keyJump.isDown()) {
                blockToBreak = BlockPos.containing(mc.player.getX() + dir[0], mc.player.getY() + 2, mc.player.getZ() + dir[1]);
            } else if (mc.options.keyShift.isDown()) {
                blockToBreak = BlockPos.containing(mc.player.getX() + dir[0], mc.player.getY() - 1, mc.player.getZ() + dir[1]);
            } else if (mc.player.isMoving()) {
                blockToBreak = BlockPos.containing(mc.player.getX() + dir[0], mc.player.getY(), mc.player.getZ() + dir[1]);
            }

            if (blockToBreak == null) return;
            int bestTool = AutoTool.INSTANCE.getTool(blockToBreak);
            if (bestTool == -1) return;

            InvUtils.swap(bestTool, true);
            mc.gameMode.continueDestroyBlock(blockToBreak, mc.player.getDirection());
            mc.player.swing(InteractionHand.MAIN_HAND);
            if (silent.getValue()) {
                InvUtils.swapBack();
            }
        }

        if (mode.getValue() == Mode.ForceMine && (mc.player.horizontalCollision || isPlayerInBlock()) && !mc.player.isInWater() && !mc.player.isInLava())
            for (int x = -2; x < 2; x++) {
                for (int y = -1; y < 3; y++) {
                    for (int z = -2; z < 2; z++) {
                        if (((x == 0 && y == 0 && z == 0) || (x == 0 && y == 1 && z == 0)) && !mc.options.keyShift.isDown()) {
                            continue;
                        }
                        BlockPos bp = BlockPos.containing(mc.player.position()).offset(x, y, z);
                        if (mc.player.getBoundingBox().intersects(new AABB(bp)) && !mc.level.getBlockState(bp).isAir()) {
                            mc.getConnection().send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, bp, Direction.UP));
                        }
                    }
                }
            }
        if (mode.getValue() == Mode.Pearl && (mc.player.onGround() || !onlyOnGround.getValue())) {
            if (mc.player.horizontalCollision && clipTimer <= 0 && mc.player.tickCount > 60) {
                if (pauseOnPhase.getValue() && isPlayerInBlock()) {
                    return;
                }

                double[] dir = MoveUtils.forward(0.5);
                BlockPos block = BlockPos.containing(mc.player.getX() + dir[0], mc.player.getY(), mc.player.getZ() + dir[1]);

                if (mc.options.keyShift.isDown()) {
                    return;
                }

                Rot2f angle = RotationUtils.calculate(block.getCenter());
                FindItemResult result = swapMode.is(SwapMode.Silent) ? InvUtils.findInHotbar(Items.ENDER_PEARL) : InvUtils.find(Items.ENDER_PEARL);
                if (result.found()) {
                    float prevYaw = mc.player.getYRot();
                    float prevPitch = mc.player.getXRot();

                    mc.player.setYRot(angle.getYaw());
                    mc.player.setXRot(pitch.getValue().floatValue());

                    doUsePearl(result.slot());

                    mc.player.setYRot(prevYaw);
                    mc.player.setXRot(prevPitch);
                }
            }
        }
    }

    private void doUsePearl(int slot) {
        if (swapMode.is(SwapMode.Silent)) {
            InvUtils.swap(slot, true);
        } else {
            InvUtils.invSwap(slot);
        }

        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);

        if (swingHand.getValue()) {
            mc.player.swing(InteractionHand.MAIN_HAND);
        } else {
            mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
        }


        if (swapMode.is(SwapMode.Silent)) {
            InvUtils.swapBack();
        } else {
            InvUtils.invSwapBack();
        }

        if (autoDisable.getValue()) {
            toggle();
        }

        clipTimer = 20;
        afterPearlTime = afterPearl.getValue();
    }

    private boolean canNoClip() {
        if (mode.is(Mode.Vanilla)) return true;
        if (!waitBreak.getValue()) return true;
        return clipTimer != 0;
    }

    private boolean isPlayerInBlock() {
        return !mc.level.getBlockState(BlockPos.containing(mc.player.position())).isAir();
    }

}
