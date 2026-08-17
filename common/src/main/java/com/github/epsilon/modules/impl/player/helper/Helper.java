package com.github.epsilon.modules.impl.player.helper;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.managers.rotation.RotationManager;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.rotation.Priority;
import com.github.epsilon.utils.rotation.Rot2f;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.*;

public class Helper extends Module {

    public static final Helper INSTANCE = new Helper();

    private Helper() {
        super("Helper", Category.PLAYER);
    }

    public final BoolSetting selfExtinguish = boolSetting("Self Extinguish", true, enabled -> updateHelperState("Self Extinguish", enabled));
    public final BoolSetting extinguishFire = boolSetting("Extinguish Fire", true, enabled -> updateHelperState("Extinguish Fire", enabled));
    private final BoolSetting blockLava = boolSetting("Block Lava", true, enabled -> updateHelperState("Block Lava", enabled));
    private final BoolSetting blockWater = boolSetting("Block Water", true, enabled -> updateHelperState("Block Water", enabled));
    private final IntSetting rotationSpeed = intSetting("Speed", 127, 1, 180, 10);
    private final EnumSetting<Priority> rotationPriority = enumSetting("Rotation Priority", Priority.Low);

    private Rot2f targetRotation;
    private FluidTracker fluidTracker;

    private static final Map<BlockPos, Integer> waterPlacements = new HashMap<>();
    private static final Map<BlockPos, Integer> lavaPlacements = new HashMap<>();

    private final List<HelperBase> helpers = List.of(
            new SelfExtinguish(),
            new ExtinguishFire(),
            new BlockLava(),
            new BlockWater()
    );

    @Override
    protected void onEnable() {
        helpers.forEach(HelperBase::onEnable);
    }

    @Override
    protected void onDisable() {
        targetRotation = null;
        waterPlacements.clear();
        lavaPlacements.clear();
        fluidTracker = null;
        helpers.forEach(HelperBase::onDisable);
    }

    @EventHandler
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        if (fluidTracker != null) {
            Set<BlockPos> newSources = findFluidBlocks(fluidTracker.sourcePos, fluidTracker.fluidBlock);
            newSources.removeAll(fluidTracker.connectedPositions);
            for (BlockPos pos : newSources) {
                if (fluidTracker.fluidBlock == Blocks.WATER) markWaterPlaced(pos);
                else markLavaPlaced(pos);
            }
            if (!newSources.isEmpty() || --fluidTracker.tickCount <= 0) fluidTracker = null;
        } else {
            updateBucketTracker();
        }

        // Cleanup Placement Maps
        updatePlacementMap(waterPlacements, Blocks.WATER);
        updatePlacementMap(lavaPlacements, Blocks.LAVA);

        for (HelperBase helper : helpers) {
            if (!isSelected(helper)) continue;
            helper.onPreMotion();
            helper.onMotion();
            helper.onTick();
        }

        targetRotation = null;
        for (HelperBase helper : helpers) {
            if (isSelected(helper) && helper.isActive() && helper.getTargetRotation() != null) {
                targetRotation = helper.getTargetRotation();
            }
        }
        if (targetRotation != null) {
            RotationManager.INSTANCE.setRotations(targetRotation, rotationSpeed.getValue(), rotationPriority.getValue());
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        for (HelperBase helper : helpers) {
            if (isSelected(helper)) helper.onRender();
        }
    }

    private boolean isSelected(HelperBase helper) {
        return switch (helper.name) {
            case "Self Extinguish" -> selfExtinguish.getValue();
            case "Extinguish Fire" -> extinguishFire.getValue();
            case "Block Lava" -> blockLava.getValue();
            case "Block Water" -> blockWater.getValue();
            default -> false;
        };
    }

    private void updateHelperState(String name, boolean enabled) {
        if (isEnabled()) {
            for (HelperBase helper : helpers) {
                if (!helper.name.equals(name)) continue;
                if (enabled) helper.onEnable();
                else helper.onDisable();
                return;
            }
        }
    }

    public static boolean isRotationAtTarget(Rot2f target) {
        Rot2f current = RotationManager.INSTANCE.getRotation();
        float yaw = Mth.wrapDegrees(current.getYaw() - target.getYaw());
        float pitch = current.getPitch() - target.getPitch();
        return Math.hypot(yaw, pitch) <= 2.0;
    }

    public static void markWaterPlaced(BlockPos pos) {
        addPlacement(waterPlacements, pos);
    }

    public static void markLavaPlaced(BlockPos pos) {
        addPlacement(lavaPlacements, pos);
    }

    public static void removeWaterPlacement(BlockPos pos) {
        if (pos != null) waterPlacements.remove(pos);
    }

    public static void removeLavaPlacement(BlockPos pos) {
        if (pos != null) lavaPlacements.remove(pos);
    }

    public static boolean hasWaterPlacement(BlockPos pos) {
        return pos != null && waterPlacements.containsKey(pos);
    }

    public static boolean hasLavaPlacement(BlockPos pos) {
        return pos != null && lavaPlacements.containsKey(pos);
    }

    private static void addPlacement(Map<BlockPos, Integer> placements, BlockPos pos) {
        if (pos != null) placements.put(pos.immutable(), 20);
    }

    private void updateBucketTracker() {
        Item item = mc.player.getMainHandItem().getItem();
        if (item != Items.WATER_BUCKET && item != Items.LAVA_BUCKET) {
            item = mc.player.getOffhandItem().getItem();
        }
        if (item == Items.WATER_BUCKET || item == Items.LAVA_BUCKET) {
            Block fluid = item == Items.WATER_BUCKET ? Blocks.WATER : Blocks.LAVA;
            BlockPos source = mc.player.blockPosition();
            fluidTracker = new FluidTracker(fluid, source, findFluidBlocks(source, fluid), 20);
        }
    }

    private Set<BlockPos> findFluidBlocks(BlockPos center, Block fluid) {
        Set<BlockPos> positions = new HashSet<>();
        for (int dx = -6; dx <= 6; dx++) {
            for (int dy = -5; dy <= 5; dy++) {
                for (int dz = -6; dz <= 6; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (isFluidSourceAt(pos, fluid)) positions.add(pos.immutable());
                }
            }
        }
        return positions;
    }

    private void updatePlacementMap(Map<BlockPos, Integer> placements, Block fluid) {
        placements.entrySet().removeIf(entry -> {
            if (isFluidSourceAt(entry.getKey(), fluid)) {
                entry.setValue(0);
                return false;
            }
            int remaining = entry.getValue();
            if (remaining <= 0) return true;
            entry.setValue(remaining - 1);
            return false;
        });
    }

    private boolean isFluidSourceAt(BlockPos pos, Block fluid) {
        return mc.level.getBlockState(pos).is(fluid) && mc.level.getFluidState(pos).isSource();
    }

    private static class FluidTracker {
        private final Block fluidBlock;
        private final BlockPos sourcePos;
        private final Set<BlockPos> connectedPositions;
        private int tickCount;

        private FluidTracker(Block fluidBlock, BlockPos sourcePos, Set<BlockPos> connectedPositions, int tickCount) {
            this.fluidBlock = fluidBlock;
            this.sourcePos = sourcePos;
            this.connectedPositions = connectedPositions;
            this.tickCount = tickCount;
        }
    }

}
