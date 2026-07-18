package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.ClientTickEvent;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.player.ChatUtils;
import com.github.epsilon.utils.player.FindItemResult;
import com.github.epsilon.utils.player.InvUtils;
import com.github.epsilon.utils.player.PlayerUtils;
import com.github.epsilon.utils.rotation.RaytraceUtils;
import com.github.epsilon.utils.rotation.Rot2f;
import com.github.epsilon.utils.rotation.RotationUtils;
import com.github.epsilon.utils.timer.TimerUtils;
import com.github.epsilon.utils.world.BlockUtils;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class FeetTrap extends Module {

    public static final FeetTrap INSTANCE = new FeetTrap();

    private FeetTrap() {
        super("Feet Trap", Category.COMBAT);
    }

    private final BoolSetting toggleOnMove = boolSetting("Toggle On Move", true);
    private final BoolSetting toggleOnJump = boolSetting("Toggle On Jump", true);
    private final BoolSetting inAir = boolSetting("In Air", true);
    private final BoolSetting pauseOnEat = boolSetting("Pause On Eat", true);
    private final IntSetting placeDelay = intSetting("Place Delay", 50, 0, 500, 50);
    private final BoolSetting extend = boolSetting("Extend", true);
    private final IntSetting blocksPerTick = intSetting("Blocks Per Tick", 1, 1, 8, 1);
    private final BoolSetting rotate = boolSetting("Rotate", true);
    private final IntSetting rotationSpeed = intSetting("Rotation Speed", 180, 18, 180, 18, rotate::getValue);
    private final BoolSetting enderChest = boolSetting("Ender Chest", true);
    private final BoolSetting inventorySwap = boolSetting("Inventory Swap", true);

    private double startX = 0, startY = 0, startZ = 0;
    private int progress = 0;
    private Rot2f rotation = null;

    private final TimerUtils timer = new TimerUtils();

    private static final Direction[] DIRECTIONS = {Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    @Override
    public void onEnable() {
        if (nullCheck()) {
            toggle();
            return;
        }
        startX = mc.player.getX();
        startY = mc.player.getY();
        startZ = mc.player.getZ();
    }

    @EventHandler
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        if (rotation != null && rotate.getValue()) {
            Managers.ROTATION.setRotations(rotation, rotationSpeed.getValue().doubleValue());
        }
    }

    @EventHandler
    private void onClientTick(ClientTickEvent.Pre event) {
        if (nullCheck()) return;
        if (!timer.passedMillise(placeDelay.getValue())) return;

        progress = 0;

        if (!mc.player.isMoving() && !mc.options.keyJump.isDown()) {
            startX = mc.player.getX();
            startY = mc.player.getY();
            startZ = mc.player.getZ();
        }

        FindItemResult result = findBlocks();
        if (!result.found()) {
            ChatUtils.addChatMessage("[FeetTrap] No block found");
            toggle();
            return;
        }

        if (toggleOnMove.getValue() && mc.player.distanceToSqr(startX, startY, startZ) > 1.0 || toggleOnJump.getValue() && mc.player.input.keyPresses.jump()) {
            toggle();
            return;
        }

        if (pauseOnEat.getValue() && PlayerUtils.isEating()) {
            return;
        }

        if (!inAir.getValue() && !mc.player.onGround()) {
            return;
        }

        doSurround(BlockPos.containing(mc.player.getX(), mc.player.getY(), mc.player.getZ()), result.slot());
        doSurround(BlockPos.containing(mc.player.getX(), mc.player.getY() + 0.8, mc.player.getZ()), result.slot());
    }

    private void doSurround(BlockPos pos, int slot) {
        for (Direction i : DIRECTIONS) {
            BlockPos offsetPos = pos.relative(i);
            tryPlaceBlockOrHelper(offsetPos, slot);
            if ((selfIntersectPos(offsetPos) || otherIntersectPos(offsetPos)) && extend.getValue()) {
                for (Direction i2 : DIRECTIONS) {
                    BlockPos offsetPos2 = offsetPos.relative(i2);
                    if (selfIntersectPos(offsetPos2) || otherIntersectPos(offsetPos2)) {
                        for (Direction i3 : DIRECTIONS) {
                            tryPlaceBlock(offsetPos2, slot);
                            BlockPos offsetPos3 = offsetPos2.relative(i3);
                            tryPlaceBlockOrHelper(offsetPos3, slot);
                        }
                    }
                    tryPlaceBlockOrHelper(offsetPos2, slot);
                }
            }
        }
    }

    private void tryPlaceBlockOrHelper(BlockPos pos, int slot) {
        if (getPlaceSide(pos) != null || !mc.level.getBlockState(pos).canBeReplaced()) {
            tryPlaceBlock(pos, slot);
        } else {
            tryPlaceBlock(getHelperPos(pos), slot);
        }
    }

    private Direction getPlaceSide(BlockPos pos) {
        double minDistance = Double.MAX_VALUE;
        Direction side = null;
        for (Direction i : Direction.values()) {
            BlockPos neighbourPos = pos.relative(i);
            BlockState neighbourState = mc.level.getBlockState(neighbourPos);
            if (neighbourState.canBeReplaced() || !isClickable(neighbourState, neighbourPos)) continue;
            if (!RotationUtils.canSee(neighbourPos, i.getOpposite())) continue;
            double vecDis = mc.player.getEyePosition().distanceToSqr(hitVec(pos, i));
            if (vecDis > minDistance) {
                continue;
            }
            side = i;
            minDistance = vecDis;
        }
        return side;
    }

    private boolean isClickable(BlockState state, BlockPos pos) {
        return mc.player.isSecondaryUseActive() || !(
                state.getMenuProvider(mc.level, pos) != null
                        || state.getBlock() instanceof EntityBlock
                        || state.is(BlockTags.BUTTONS)
                        || state.is(BlockTags.PRESSURE_PLATES)
                        || state.is(BlockTags.BEDS)
                        || state.is(BlockTags.FENCE_GATES)
                        || state.is(BlockTags.DOORS)
                        || state.is(BlockTags.TRAPDOORS)
                        || state.getBlock() instanceof NoteBlock
        );
    }

    private void tryPlaceBlock(BlockPos pos, int slot) {
        if (pos == null) return;
        if (!(progress < blocksPerTick.getValue())) return;
        Direction side = getPlaceSide(pos);
        if (side == null) return;
        if (!BlockUtils.canPlaceAt(pos)) return;

        if (rotate.getValue()) {
            this.rotation = RotationUtils.calculate(hitVec(pos, side));
            if (RaytraceUtils.overBlock(Managers.ROTATION.getRotation(), pos.relative(side))) {
                // 在这个 tick 转头已经到达，无需继续设置转头
                this.rotation = null;
            } else {
                // 没到，给劳资转！
                return;
            }
        }

        if (inventorySwap.getValue()) InvUtils.invSwap(slot);
        else InvUtils.swap(slot, true);

        placeBlock(pos.relative(side), side.getOpposite(), InteractionHand.MAIN_HAND);

        timer.reset();

        if (inventorySwap.getValue()) InvUtils.invSwapBack();
        else InvUtils.swapBack();

        progress++;
    }

    private void placeBlock(BlockPos pos, Direction side, InteractionHand hand) {
        BlockHitResult result = new BlockHitResult(hitVec(pos, side), side, pos, false);
        mc.gameMode.useItemOn(mc.player, hand, result);
    }

    private Vec3 hitVec(BlockPos pos, Direction side) {
        return pos.getCenter().relative(side, 0.5);
    }

    private BlockPos getHelperPos(BlockPos pos) {
        for (Direction i : Direction.values()) {
            if (!RotationUtils.canSee(pos.relative(i), i.getOpposite())) continue;
            if (BlockUtils.canPlaceAt(pos.relative(i))) return pos.relative(i);
        }
        return null;
    }

    private boolean selfIntersectPos(BlockPos pos) {
        return mc.player.getBoundingBox().intersects(new AABB(pos));
    }

    private boolean otherIntersectPos(BlockPos pos) {
        for (AbstractClientPlayer player : mc.level.players()) {
            if (player.getBoundingBox().intersects(new AABB(pos))) {
                return true;
            }
        }
        return false;
    }

    private FindItemResult findBlocks() {
        if (inventorySwap.getValue()) {
            FindItemResult result = InvUtils.find(Items.OBSIDIAN);
            if (result.found() || !enderChest.getValue()) {
                return result;
            }
            return InvUtils.find(Items.ENDER_CHEST);
        } else {
            FindItemResult result = InvUtils.findInHotbar(Items.OBSIDIAN);
            if (result.found() || !enderChest.getValue()) {
                return result;
            }
            return InvUtils.findInHotbar(Items.ENDER_CHEST);
        }
    }

}
