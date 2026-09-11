package com.github.epsilon.modules.impl.render;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.graphics.schedulers.render3d.Render3DScheduler;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.RegistryListSetting;
import com.github.epsilon.utils.player.ChatUtils;
import com.github.epsilon.utils.timer.TimerUtils;
import com.google.common.collect.Lists;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BlockESP extends Module {

    public static final BlockESP INSTANCE = new BlockESP();

    private BlockESP() {
        super("Block ESP", Category.RENDER);
    }

    private final RegistryListSetting<Block> blockListValue = blockListSetting("Block List", defaultBlocks());
    private final BoolSetting illegals = boolSetting("Illegals", false);
    private final DoubleSetting range = doubleSetting("Range", 64.0, 1.0, 128.0, 1.0);
    private final ColorSetting sideColor = colorSetting("Side Color", new Color(160, 210, 255, 30));
    private final ColorSetting lineColor = colorSetting("Line Color", new Color(160, 210, 255, 180));
    private final BoolSetting blur = boolSetting("Blur", true);
    private final DoubleSetting blurStrength = doubleSetting("Blur Strength", 8.0, 0.0, 16.0, 0.5, blur::getValue);

    private final ExecutorService searchThread = Executors.newSingleThreadExecutor();
    private final TimerUtils searchTimer = new TimerUtils();
    private boolean canContinue;

    public static List<AABB> boxes = new ArrayList<>();

    private static List<Block> defaultBlocks() {
        List<Block> blocks = new ArrayList<>();
        blocks.add(Blocks.CHEST);
        blocks.add(Blocks.TRAPPED_CHEST);
        blocks.addAll(Blocks.COPPER_CHEST.asList());
        blocks.add(Blocks.ENDER_CHEST);
        blocks.add(Blocks.BARREL);
        blocks.add(Blocks.SHULKER_BOX);
        blocks.addAll(Blocks.DYED_SHULKER_BOX.asList());
        return blocks;
    }

    @Override
    protected void onEnable() {
        boxes.clear();
        canContinue = true;
    }

    @EventHandler
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        if (searchTimer.every(1000) && canContinue) {
            CompletableFuture.supplyAsync(this::scan, searchThread).thenAcceptAsync(newAABBList -> {
                boxes = newAABBList;
                canContinue = true;
            }, Util.backgroundExecutor());
            canContinue = false;
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (boxes.isEmpty()) return;

        if (mc.getFps() < 8 && mc.player.tickCount > 150) {
            ChatUtils.addChatMessage("你的 FPS 太低了，我帮你给 BlockESP 关掉了。");
            toggle();
            return;
        }

        for (AABB aabb : Lists.newArrayList(boxes)) {
            if (blur.getValue()) Render3DScheduler.INSTANCE.addBlurredBox(aabb, blurStrength.getValue());
            Render3DScheduler.INSTANCE.addFilledBox(aabb, sideColor.getValue());
            Render3DScheduler.INSTANCE.addOutlineBox(aabb, lineColor.getValue());
        }
    }

    private List<AABB> scan() {
        List<AABB> boxes = new ArrayList<>();
        Set<BlockPos> processed = new HashSet<>();

        int startX = Mth.floor(mc.player.getX() - range.getValue());
        int endX = Mth.ceil(mc.player.getX() + range.getValue());
        int startY = mc.level.getMinY() + 1;
        int endY = mc.level.getMaxY();
        int startZ = Mth.floor(mc.player.getZ() - range.getValue());
        int endZ = Mth.ceil(mc.player.getZ() + range.getValue());

        for (int x = startX; x <= endX; x++) {
            for (int y = startY; y <= endY; y++) {
                for (int z = startZ; z <= endZ; z++) {
                    BlockPos blockPos = new BlockPos(x, y, z);
                    if (!processed.contains(blockPos)) {
                        BlockState blockState = mc.level.getBlockState(blockPos);
                        if (shouldAdd(blockState.getBlock(), blockPos)) {
                            boxes.add(getConnectedShapeAABB(blockPos, blockState, processed));
                        }
                    }
                }
            }
        }
        return boxes;
    }

    private boolean shouldAdd(Block block, BlockPos pos) {
        if (block instanceof AirBlock) return false;
        if (blockListValue.getValue().contains(block)) return true;
        if (illegals.getValue()) return isIllegal(block, pos);
        return false;
    }

    private boolean isIllegal(Block block, BlockPos pos) {
        if (block instanceof CommandBlock || block instanceof BarrierBlock) return true;

        if (block == Blocks.BEDROCK) {
            if (!Level.NETHER.equals(mc.level.dimension())) {
                return pos.getY() > 4;
            } else {
                return pos.getY() > 127 || (pos.getY() < 123 && pos.getY() > 4);
            }
        }
        return false;
    }

    private AABB getConnectedShapeAABB(BlockPos blockPos, BlockState state, Set<BlockPos> processed) {
        processed.add(blockPos);
        AABB box = getShapeAABB(blockPos, state);

        if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockPos connectedPos = ChestBlock.getConnectedBlockPos(blockPos, state);
            BlockState connectedState = mc.level.getBlockState(connectedPos);
            if (isConnectedChestPart(blockPos, state, connectedState, connectedPos)) {
                processed.add(connectedPos);
                box = box.minmax(getShapeAABB(connectedPos, connectedState));
            }
        }

        return box;
    }

    private boolean isConnectedChestPart(BlockPos blockPos, BlockState state, BlockState connectedState, BlockPos connectedPos) {
        if (!(state.getBlock() instanceof ChestBlock chestBlock)) return false;
        if (!chestBlock.chestCanConnectTo(connectedState)) return false;
        if (!connectedState.hasProperty(ChestBlock.TYPE) || !connectedState.hasProperty(ChestBlock.FACING))
            return false;
        if (connectedState.getValue(ChestBlock.TYPE) == ChestType.SINGLE) return false;
        if (connectedState.getValue(ChestBlock.FACING) != state.getValue(ChestBlock.FACING)) return false;
        if (!ChestBlock.getConnectedBlockPos(connectedPos, connectedState).equals(blockPos)) return false;

        return shouldAdd(connectedState.getBlock(), connectedPos);
    }

    private AABB getShapeAABB(BlockPos blockPos, BlockState state) {
        return state.getShape(mc.level, blockPos).bounds().move(blockPos);
    }

}
