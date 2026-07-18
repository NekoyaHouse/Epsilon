package com.github.epsilon.modules.impl.movement;

import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.bus.listeners.ConsumerListener;
import com.github.epsilon.events.impl.KeyboardInputEvent;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.graphics.schedulers.render3d.Render3DScheduler;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.*;
import com.github.epsilon.utils.math.MathUtils;
import com.github.epsilon.utils.player.FallingPlayer;
import com.github.epsilon.utils.player.FindItemResult;
import com.github.epsilon.utils.player.InvUtils;
import com.github.epsilon.utils.render.animation.Easing;
import com.github.epsilon.utils.rotation.RaytraceUtils;
import com.github.epsilon.utils.rotation.Rot2f;
import com.github.epsilon.utils.rotation.RotationUtils;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Scaffold extends Module {

    public static final Scaffold INSTANCE = new Scaffold();

    private Scaffold() {
        super("Scaffold", Category.MOVEMENT);
        EventBus.INSTANCE.subscribe(new ConsumerListener<>(Render3DEvent.class,
                event -> {
                    if (!render.getValue() || renderBoxes.isEmpty()) return;

                    long time = System.currentTimeMillis();
                    long fadeTime = this.fadeTime.getValue().longValue();

                    renderBoxes.removeIf(box -> time - box.startTime() > fadeTime);

                    for (RenderInfo box : renderBoxes) {
                        float progress = Mth.clamp((float) (time - box.startTime()) / fadeTime, 0.0f, 1.0f);

                        double scale = 1.0;
                        if (box.shrink()) {
                            scale = 1.0 - Easing.EASE_IN_OUT_EXPO.getFunction().apply(progress);
                            if (scale < 0) scale = 0;
                        }

                        float alphaFactor = box.fade() ? Mth.clamp(1.0f - progress, 0.0f, 1.0f) : 1.0f;

                        Color sideColor = box.sideColor();
                        Color lineColor = box.lineColor();

                        Color side = new Color(sideColor.getRed(), sideColor.getGreen(), sideColor.getBlue(), (int) (sideColor.getAlpha() * alphaFactor));
                        Color line = new Color(lineColor.getRed(), lineColor.getGreen(), lineColor.getBlue(), (int) (lineColor.getAlpha() * alphaFactor));

                        AABB renderBox = box.aabb;
                        if (box.shrink()) {
                            renderBox = AABB.ofSize(renderBox.getCenter(), renderBox.getXsize() * scale, renderBox.getYsize() * scale, renderBox.getZsize() * scale);
                        }

                        Render3DScheduler.INSTANCE.addFilledBox(renderBox, side);
                        Render3DScheduler.INSTANCE.addOutlineBox(renderBox, line);
                    }
                }
        ));
    }

    private enum Mode {
        TellyBridge,
        GodBridge
    }

    private enum RotationMode {
        Rise,
        Hypixel
    }

    private enum RaytraceMode {
        Normal,
        Strict
    }

    private enum SwapMode {
        None,
        Normal,
        Silent,
        InvSwitch
    }

    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.TellyBridge);
    private final EnumSetting<SwapMode> swapMode = enumSetting("Swap Mode", SwapMode.Normal);
    private final BoolSetting swapBack = boolSetting("Swap Back", true, () -> swapMode.is(SwapMode.Normal));
    private final BoolSetting skipTicks = boolSetting("Skip Ticks", false);
    private final BoolSetting snap = boolSetting("Snap", false, () -> mode.is(Mode.GodBridge));
    private final EnumSetting<RotationMode> rotationMode = enumSetting("Rotation Mode", RotationMode.Rise);
    private final EnumSetting<RaytraceMode> raytrace = enumSetting("Raytrace Mode", RaytraceMode.Normal);
    private final IntSetting rotateSpeed = intSetting("Rotation Speed", 180, 10, 180, 10, () -> rotationMode.is(RotationMode.Rise));
    private final IntSetting rotateBackSpeed = intSetting("Rotation Back Speed", 180, 10, 180, 10, () -> mode.is(Mode.TellyBridge));
    private final IntSetting tellyTicks = intSetting("Telly Ticks", 1, 0, 6, 1, () -> mode.is(Mode.TellyBridge));

    private final BoolSetting swingHand = boolSetting("Swing Hand", true);
    private final BoolSetting render = boolSetting("Render", true);
    private final BoolSetting fade = boolSetting("Fade", true, render::getValue);
    private final IntSetting fadeTime = intSetting("Fade Time", 500, 0, 3000, 50, () -> render.getValue() && fade.getValue());
    private final BoolSetting shrink = boolSetting("Shrink", false, render::getValue);
    private final ColorSetting sideColor = colorSetting("Side Color", new Color(255, 183, 197, 100), render::getValue);
    private final ColorSetting lineColor = colorSetting("Line Color", new Color(255, 105, 180), render::getValue);

    private int airTicks;
    private int yLevel;
    private BlockPos blockPos;
    private Direction direction;
    private Rot2f rotation;
    private int rotateCount = 0;

    private FindItemResult blockResult;
    private boolean shouldSwapBack;

    private final List<RenderInfo> renderBoxes = new ArrayList<>();

    private final RegistryListSetting<Block> blacklistedBlocks = blockListSetting("Blacklisted Blocks", List.of(
            Blocks.AIR,
            Blocks.WATER,
            Blocks.LAVA,
            Blocks.ENCHANTING_TABLE,
            Blocks.GLASS_PANE,
            Blocks.IRON_BARS,
            Blocks.SNOW,
            Blocks.COAL_ORE,
            Blocks.DIAMOND_ORE,
            Blocks.EMERALD_ORE,
            Blocks.CHEST,
            Blocks.TRAPPED_CHEST,
            Blocks.TORCH,
            Blocks.ANVIL,
            Blocks.NOTE_BLOCK,
            Blocks.JUKEBOX,
            Blocks.TNT,
            Blocks.GOLD_ORE,
            Blocks.IRON_ORE,
            Blocks.LAPIS_ORE,
            Blocks.STONE_PRESSURE_PLATE,
            Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE,
            Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE,
            Blocks.STONE_BUTTON,
            Blocks.LEVER,
            Blocks.TALL_GRASS,
            Blocks.TRIPWIRE,
            Blocks.TRIPWIRE_HOOK,
            Blocks.RAIL,
            Blocks.CORNFLOWER,
            Blocks.RED_MUSHROOM,
            Blocks.BROWN_MUSHROOM,
            Blocks.VINE,
            Blocks.SUNFLOWER,
            Blocks.LADDER,
            Blocks.FURNACE,
            Blocks.SAND,
            Blocks.CACTUS,
            Blocks.DISPENSER,
            Blocks.DROPPER,
            Blocks.CRAFTING_TABLE,
            Blocks.COBWEB,
            Blocks.PUMPKIN,
            Blocks.COBBLESTONE_WALL,
            Blocks.OAK_FENCE,
            Blocks.REDSTONE_TORCH,
            Blocks.FLOWER_POT
    ));

    @Override
    protected void onEnable() {
        airTicks = 0;
        blockPos = null;
        direction = null;
        rotation = null;
        rotateCount = 0;
        blockResult = null;
        shouldSwapBack = false;
    }

    @Override
    protected void onDisable() {
        yLevel = 0;
        if (!nullCheck()) {
            if (shouldSwapBack) {
                InvUtils.swapBack();
                shouldSwapBack = false;
            }
            boolean isHoldingShift = InputConstants.isKeyDown(mc.getWindow(), mc.options.keyShift.getDefaultKey().getValue());
            mc.options.keyShift.setDown(isHoldingShift);
        }
    }

    @EventHandler
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        blockResult = findBlockResult();
        if (!blockResult.found()) return;

        if (mc.player.onGround()) {
            airTicks = 0;
            yLevel = Mth.floor(mc.player.getY()) - 1;
        } else {
            airTicks++;
        }

        getBlockInfo();

        if (skipTicks.getValue() && blockPos != null) {
            boolean reachable = true;

            if (mc.player.getDeltaMovement().y < -0.1) {
                FallingPlayer fallingPlayer = new FallingPlayer(mc.player);
                fallingPlayer.calculate(2);
                if (blockPos.getY() > fallingPlayer.getY()) {
                    reachable = false;
                }
            }

            if ((!reachable || mc.player.getDeltaMovement().horizontal().length() >= 1.5) && rotateCount <= 8 && getBlockCount() >= 1) {
                Rot2f rotation = getRotation(blockPos, direction);
                event.cancel();

                rotateCount++;
                Managers.ROTATION.rotations = rotation;
                Managers.ROTATION.setActive(true);
                mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(rotation.getYaw(), rotation.getPitch(), mc.player.onGround(), mc.player.horizontalCollision));

                swap();

                InteractionHand hand = blockResult.getHand();
                InteractionResult result = mc.gameMode.useItemOn(mc.player, hand, new BlockHitResult(getVec3(blockPos, direction), direction, blockPos, false));
                if (result.consumesAction()) {
                    if (swingHand.getValue()) mc.player.swing(hand);
                    else mc.getConnection().send(new ServerboundSwingPacket(hand));

                    if (render.getValue()) {
                        renderBoxes.add(new RenderInfo(new AABB(blockPos.relative(direction)), lineColor.getValue(), sideColor.getValue(), System.currentTimeMillis(), fade.getValue(), shrink.getValue()));
                    }
                }

                swapBack();
                return;
            } else {
                rotateCount = 0;
            }
        }

        switch (mode.getValue()) {
            case TellyBridge -> handleTelly();
            case GodBridge -> handleNormal();
        }
    }

    @EventHandler
    private void onMoveInput(KeyboardInputEvent event) {
        if (mc.player.onGround() && !mc.options.keyJump.isDown() && mc.player.isMoving() && mode.is(Mode.TellyBridge)) {
            event.setJump(true);
        }
    }

    public int getBlockCount() {
        if (nullCheck()) return 91;

        int total = 0;

        if (isValidStack(mc.player.getOffhandItem())) {
            total += mc.player.getOffhandItem().getCount();
        }

        int maxSlot = swapMode.is(SwapMode.InvSwitch) ? mc.player.getInventory().getContainerSize() : 9;
        for (int i = 0; i < maxSlot; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (isValidStack(stack)) {
                total += stack.getCount();
            }
        }

        return total;
    }

    private void handleTelly() {
        if (mc.player.onGround()) {
            Managers.ROTATION.setRotations(new Rot2f(mc.player.getYRot(), rotation == null ? mc.player.getXRot() : rotation.getPitch()), rotateBackSpeed.getValue());
            return;
        }

        rotation = getRotation(blockPos, direction);
        double speed = rotateSpeed.getValue();

        if (rotationMode.is(RotationMode.Hypixel)) {
            speed = airTicks <= 1 ? 127 : 35;
        }

        Managers.ROTATION.setRotations(rotation, speed);

        if (airTicks > tellyTicks.getValue()) {
            place();
        }
    }

    private void handleNormal() {
        if (onAir() || !snap.getValue()) {
            rotation = getRotation(blockPos, direction);
            Managers.ROTATION.setRotations(rotation, rotateSpeed.getValue());
        }
        place();
    }

    private void place() {
        if (!onAir() || blockPos == null || direction == null) {
            return;
        }

        if (switch (raytrace.getValue()) {
            case Normal -> !RaytraceUtils.overBlock(Managers.ROTATION.getRotation(), blockPos);
            case Strict -> !RaytraceUtils.overBlock(Managers.ROTATION.getRotation(), blockPos, direction);
        }) {
            return;
        }

        swap();

        InteractionHand hand = blockResult.getHand();
        if (!isValidStack(mc.player.getItemInHand(hand))) {
            swapBack();
            return;
        }

        InteractionResult result = mc.gameMode.useItemOn(mc.player, hand, new BlockHitResult(getVec3(blockPos, direction), direction, blockPos, false));
        if (result.consumesAction()) {
            if (swingHand.getValue()) {
                mc.player.swing(hand);
            } else {
                mc.getConnection().send(new ServerboundSwingPacket(hand));
            }

            if (render.getValue()) {
                renderBoxes.add(new RenderInfo(new AABB(blockPos.relative(direction)), lineColor.getValue(), sideColor.getValue(), System.currentTimeMillis(), fade.getValue(), shrink.getValue()));
            }
        }

        swapBack();
    }

    private int getYLevel() {
        if (!mc.options.keyJump.isDown() && mc.player.isMoving() && mc.player.fallDistance <= 0.25f && mode.is(Mode.TellyBridge)) {
            return yLevel;
        }
        return Mth.floor(mc.player.getY()) - 1;
    }

    private void getBlockInfo() {
        blockPos = null;
        direction = null;

        Vec3 baseVec = mc.player.getEyePosition();
        BlockPos base = BlockPos.containing(baseVec.x, getYLevel(), baseVec.z);
        int baseX = base.getX();
        int baseZ = base.getZ();

        if (!onAir()) {
            return;
        }

        if (checkBlock(baseVec, base)) {
            return;
        }

        for (int d = 1; d <= 6; d++) {
            if (checkBlock(baseVec, new BlockPos(baseX, getYLevel() - d, baseZ))) {
                return;
            }

            for (int x = 0; x <= d; x++) {
                for (int z = 0; z <= d - x; z++) {
                    int y = d - x - z;
                    for (int rev1 = 0; rev1 <= 1; rev1++) {
                        for (int rev2 = 0; rev2 <= 1; rev2++) {
                            BlockPos pos = new BlockPos(baseX + (rev1 == 0 ? x : -x), getYLevel() - y, baseZ + (rev2 == 0 ? z : -z));
                            if (checkBlock(baseVec, pos)) {
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean checkBlock(Vec3 baseVec, BlockPos pos) {
        if (!onAir() || pos.getY() > getYLevel()) {
            return false;
        }

        Vec3 center = pos.getBottomCenter();
        for (Direction dir : Direction.values()) {
            Vec3 normal = dir.getUnitVec3();
            Vec3 hit = center.add(normal.scale(0.5));
            BlockPos baseBlockPos = pos.relative(dir);

            BlockState state = mc.level.getBlockState(baseBlockPos);
            if (state.getCollisionShape(mc.level, baseBlockPos).isEmpty() || state.getMenuProvider(mc.level, baseBlockPos) != null) {
                continue;
            }

            Direction face = dir.getOpposite();
            if (hit.distanceToSqr(baseVec) > 4.5 * 4.5 || hit.subtract(baseVec).dot(normal) < 0.0) {
                continue;
            }

            if (face == Direction.UP && mc.player.isMoving() && !mc.options.keyJump.isDown()) {
                continue;
            }

            blockPos = baseBlockPos;
            direction = face;
            return true;
        }

        return false;
    }

    private Rot2f getRotation(BlockPos pos, Direction direction) {
        if (rotation == null) {
            return new Rot2f(Mth.wrapDegrees(mc.player.getYRot() - 135.0F), 82.0F);
        }

        if (!onAir() || pos == null || direction == null) {
            return rotation;
        }

        Rot2f calculated = RotationUtils.calculate(pos, direction);
        Float[] yawArray = {
                -135F,
                -90F,
                -45F,
                0F,
                45F,
                90F,
                135F,
                180F,
                calculated.getYaw()
        };
        Arrays.sort(yawArray, (a, b) ->
                Float.compare(
                        Math.abs(Mth.wrapDegrees(mc.player.getYRot() - 180 - a)),
                        Math.abs(Mth.wrapDegrees(mc.player.getYRot() - 180 - b))
                )
        );

        float[] pitchArray = {75.0F, 82.0F, 87.0F};

        for (float yaw : yawArray) {
            for (float pitch : pitchArray) {
                Rot2f candidate = new Rot2f(yaw + MathUtils.getRandom(-0.3F, 0.3F), pitch + MathUtils.getRandom(-0.3F, 0.3F));
                boolean matches = raytrace.is(RaytraceMode.Normal) ? RaytraceUtils.overBlock(candidate, pos) : RaytraceUtils.overBlock(candidate, pos, direction);
                if (matches) {
                    return candidate;
                }
            }

            for (int pitch = -90; pitch < 90; pitch++) {
                Rot2f candidate = new Rot2f(yaw, pitch);
                boolean matches = raytrace.is(RaytraceMode.Normal) ? RaytraceUtils.overBlock(candidate, pos) : RaytraceUtils.overBlock(candidate, pos, direction);
                if (matches) {
                    return candidate;
                }
            }
        }

        return calculated;
    }

    private boolean onAir() {
        Vec3 baseVec = mc.player.getEyePosition();
        BlockPos base = BlockPos.containing(baseVec.x, getYLevel(), baseVec.z);
        return mc.level.getBlockState(base).canBeReplaced();
    }

    private Vec3 getVec3(BlockPos pos, Direction face) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;

        if (face != Direction.UP && face != Direction.DOWN) {
            y += 0.08;
        } else {
            x += MathUtils.getRandom(-0.3, 0.3);
            z += MathUtils.getRandom(-0.3, 0.3);
        }

        if (face == Direction.WEST || face == Direction.EAST) {
            z += MathUtils.getRandom(-0.3, 0.3);
        }

        if (face == Direction.SOUTH || face == Direction.NORTH) {
            x += MathUtils.getRandom(-0.3, 0.3);
        }

        return new Vec3(x, y, z);
    }

    private FindItemResult findBlockResult() {
        ItemStack offhandStack = mc.player.getOffhandItem();
        if (isValidStack(offhandStack)) {
            return new FindItemResult(40, offhandStack.getCount(), offhandStack.getMaxStackSize());
        }
        return swapMode.is(SwapMode.InvSwitch) ? InvUtils.find(this::isValidStack) : InvUtils.findInHotbar(this::isValidStack);
    }

    private void swap() {
        if (blockResult.isOffhand()) {
            return;
        }

        switch (swapMode.getValue()) {
            case Normal -> {
                int selectedSlot = mc.player.getInventory().getSelectedSlot();
                InvUtils.swap(blockResult.slot(), true);
                if (swapBack.getValue() && blockResult.slot() != selectedSlot) {
                    shouldSwapBack = true;
                }
            }
            case Silent -> InvUtils.swap(blockResult.slot(), true);
            case InvSwitch -> InvUtils.invSwap(blockResult.slot());
        }
    }

    private void swapBack() {
        if (blockResult.isOffhand()) {
            return;
        }

        switch (swapMode.getValue()) {
            case Silent -> InvUtils.swapBack();
            case InvSwitch -> InvUtils.invSwapBack();
        }
    }

    private boolean isValidStack(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BlockItem)) {
            return false;
        }

        String name = stack.getDisplayName().getString();
        if (name.contains("Click") || name.contains("点击")) {
            return false;
        }

        if (stack.getItem() instanceof StandingAndWallBlockItem) {
            return false;
        }

        Block block = ((BlockItem) stack.getItem()).getBlock();
        if (block instanceof FlowerBlock || block instanceof BushBlock || block instanceof NetherFungusBlock || block instanceof CropBlock) {
            return false;
        }

        return !(block instanceof SlabBlock) && !blacklistedBlocks.contains(block);
    }


    private record RenderInfo(AABB aabb, Color lineColor, Color sideColor, long startTime, boolean fade,
                              boolean shrink) {
    }

}
