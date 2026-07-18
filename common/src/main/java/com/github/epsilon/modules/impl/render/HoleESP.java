package com.github.epsilon.modules.impl.render;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.graphics.schedulers.render3d.Render3DScheduler;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.SettingGroup;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.timer.TimerUtils;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HoleESP extends Module {

    public static final HoleESP INSTANCE = new HoleESP();

    private final SettingGroup sgGeneral = settingGroup("General");
    private final SettingGroup sgAir = settingGroup("Air");
    private final SettingGroup sgUnsafe = settingGroup("Unsafe");
    private final SettingGroup sgSafe = settingGroup("Safe");
    private final SettingGroup sgWall = settingGroup("Wall");
    private final SettingGroup sgWallSide = settingGroup("Wall Side");

    private final DoubleSetting startFade = doubleSetting("Start Fade", 5.0, 1.0, 20.0, 0.1).group(sgGeneral);
    private final DoubleSetting distance = doubleSetting("Distance", 6.0, 1.0, 20.0, 0.1).group(sgGeneral);
    private final DoubleSetting height = doubleSetting("Height", 1.0, -3.0, 3.0, 0.1).group(sgGeneral);
    private final IntSetting updateDelay = intSetting("Update Delay", 50, 0, 1000, 10).group(sgGeneral);

    private final DoubleSetting airHeight = doubleSetting("Air Height", 1.0, -3.0, 3.0, 0.01).group(sgAir);
    private final BoolSetting airYCheck = boolSetting("Air Y Check", true).group(sgAir);
    private final BoolSetting airFill = boolSetting("Air Fill", true).group(sgAir);
    private final ColorSetting airFillColor = colorSetting("Air Fill Color", new Color(148, 0, 0, 100), airFill::getValue).group(sgAir);
    private final BoolSetting airFade = boolSetting("Air Fade", true, airFill::getValue).group(sgAir);
    private final ColorSetting airFadeColor = colorSetting("Air Fade Color", new Color(148, 0, 0, 0), () -> airFill.getValue() && airFade.getValue()).group(sgAir);
    private final BoolSetting airBox = boolSetting("Air Box", true).group(sgAir);
    private final ColorSetting airBoxColor = colorSetting("Air Box Color", new Color(148, 0, 0, 100), airBox::getValue).group(sgAir);

    private final BoolSetting unsafeFill = boolSetting("Unsafe Fill", true).group(sgUnsafe);
    private final ColorSetting unsafeFillColor = colorSetting("Unsafe Fill Color", new Color(255, 0, 0, 50), unsafeFill::getValue).group(sgUnsafe);
    private final BoolSetting unsafeFade = boolSetting("Unsafe Fade", true, unsafeFill::getValue).group(sgUnsafe);
    private final ColorSetting unsafeFadeColor = colorSetting("Unsafe Fade Color", new Color(255, 0, 0, 0), () -> unsafeFill.getValue() && unsafeFade.getValue()).group(sgUnsafe);
    private final BoolSetting unsafeBox = boolSetting("Unsafe Box", true).group(sgUnsafe);
    private final ColorSetting unsafeBoxColor = colorSetting("Unsafe Box Color", new Color(255, 0, 0, 100), unsafeBox::getValue).group(sgUnsafe);

    private final BoolSetting safeFill = boolSetting("Safe Fill", true).group(sgSafe);
    private final ColorSetting safeFillColor = colorSetting("Safe Fill Color", new Color(8, 255, 79, 50), safeFill::getValue).group(sgSafe);
    private final BoolSetting safeFade = boolSetting("Safe Fade", true, safeFill::getValue).group(sgSafe);
    private final ColorSetting safeFadeColor = colorSetting("Safe Fade Color", new Color(8, 255, 79, 100), () -> safeFill.getValue() && safeFade.getValue()).group(sgSafe);
    private final BoolSetting safeBox = boolSetting("Safe Box", true).group(sgSafe);
    private final ColorSetting safeBoxColor = colorSetting("Safe Box Color", new Color(8, 255, 79, 100), safeBox::getValue).group(sgSafe);

    private final DoubleSetting wallHeight = doubleSetting("Wall Height", 3.0, -3.0, 3.0, 0.1).group(sgWall);
    private final BoolSetting sideCheck = boolSetting("Side Check", true).group(sgWall);
    private final BoolSetting wallFill = boolSetting("Wall Fill", true).group(sgWall);
    private final ColorSetting wallFillColor = colorSetting("Wall Fill Color", new Color(0, 255, 255, 128), wallFill::getValue).group(sgWall);
    private final BoolSetting wallFade = boolSetting("Wall Fade", true, wallFill::getValue).group(sgWall);
    private final ColorSetting wallFadeColor = colorSetting("Wall Fade Color", new Color(0, 255, 255, 64), () -> wallFill.getValue() && wallFade.getValue()).group(sgWall);
    private final BoolSetting wallBox = boolSetting("Wall Box", true).group(sgWall);
    private final ColorSetting wallBoxColor = colorSetting("Wall Box Color", new Color(0, 225, 255, 255), wallBox::getValue).group(sgWall);

    private final BoolSetting wallSideFill = boolSetting("Wall Side Fill", true).group(sgWallSide);
    private final ColorSetting wallSideFillColor = colorSetting("Wall Side Fill Color", new Color(0, 255, 255, 128), wallSideFill::getValue).group(sgWallSide);
    private final BoolSetting wallSideFade = boolSetting("Wall Side Fade", true, wallSideFill::getValue).group(sgWallSide);
    private final ColorSetting wallSideFadeColor = colorSetting("Wall Side Fade Color", new Color(0, 255, 255, 64), () -> wallSideFill.getValue() && wallSideFade.getValue()).group(sgWallSide);
    private final BoolSetting wallSideBox = boolSetting("Wall Side Box", true).group(sgWallSide);
    private final ColorSetting wallSideBoxColor = colorSetting("Wall Side Box Color", new Color(0, 225, 255, 255), wallSideBox::getValue).group(sgWallSide);

    private List<Hole> unsafeHoles = List.of();
    private List<Hole> safeHoles = List.of();
    private List<BlockPos> airBlocks = List.of();
    private List<BlockPos> walls = List.of();
    private List<BlockPos> wallSides = List.of();
    private ClientLevel scannedLevel;

    private final TimerUtils updateTimer = new TimerUtils();

    private static final Direction[] HORIZONTAL_DIRECTIONS = {
            Direction.NORTH,
            Direction.SOUTH,
            Direction.WEST,
            Direction.EAST
    };

    private HoleESP() {
        super("Hole ESP", Category.RENDER);
    }

    @Override
    protected void onEnable() {
        clearResults();
        updateTimer.setMs(updateDelay.getValue());
    }

    @Override
    protected void onDisable() {
        clearResults();
    }

    @EventHandler
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        if (scannedLevel != mc.level) updateTimer.setMs(updateDelay.getValue());
        if (!updateTimer.every(updateDelay.getValue())) return;

        ScanResult result = scan();
        unsafeHoles = result.unsafeHoles();
        safeHoles = result.safeHoles();
        airBlocks = result.airBlocks();
        walls = result.walls();
        wallSides = result.wallSides();
        scannedLevel = mc.level;
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (scannedLevel != mc.level) return;

        drawHoles(safeHoles, safeFill, safeFillColor, safeFade, safeFadeColor, safeBox, safeBoxColor, height.getValue());
        draw(airBlocks, airFill, airFillColor, airFade, airFadeColor, airBox, airBoxColor, airHeight.getValue());
        drawHoles(unsafeHoles, unsafeFill, unsafeFillColor, unsafeFade, unsafeFadeColor, unsafeBox, unsafeBoxColor, height.getValue());
        draw(walls, wallFill, wallFillColor, wallFade, wallFadeColor, wallBox, wallBoxColor, wallHeight.getValue());
        draw(wallSides, wallSideFill, wallSideFillColor, wallSideFade, wallSideFadeColor, wallSideBox, wallSideBoxColor, height.getValue());
    }

    private ScanResult scan() {
        List<Hole> unsafe = new ArrayList<>();
        List<Hole> safe = new ArrayList<>();
        List<BlockPos> air = new ArrayList<>();
        List<BlockPos> wall = new ArrayList<>();
        List<BlockPos> wallSide = new ArrayList<>();
        Set<BlockPos> processedHoleCells = new HashSet<>();

        Vec3 center = mc.player.position();
        double radius = distance.getValue();
        double radiusSquared = radius * radius;
        int minX = Mth.floor(center.x - radius);
        int maxX = Mth.floor(center.x + radius);
        int minY = Math.max(mc.level.getMinY() + 1, Mth.floor(center.y - radius));
        int maxY = Math.min(mc.level.getMaxY() - 2, Mth.floor(center.y + radius));
        int minZ = Mth.floor(center.z - radius);
        int maxZ = Mth.floor(center.z + radius);

        for (int y = maxY; y >= minY; y--) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (pos.distToCenterSqr(center) > radiusSquared || !mc.level.isLoaded(pos)) continue;

                    if (isBedrock(pos) && isBedrock(pos.above(2)) && isBedrock(pos.below())) {
                        Direction side = getWallSide(pos);
                        if (side != null || !sideCheck.getValue()) wall.add(pos);
                        if (side != null) wallSide.add(pos.relative(side));
                    }

                    if (isAirMarker(pos)) air.add(pos);

                    if (!processedHoleCells.contains(pos)) {
                        Hole hole = findHole(pos);
                        if (hole != null) {
                            processedHoleCells.addAll(hole.positions());
                            if (hole.safe()) {
                                safe.add(hole);
                            } else {
                                unsafe.add(hole);
                            }
                        }
                    }
                }
            }
        }

        return new ScanResult(List.copyOf(unsafe), List.copyOf(safe), List.copyOf(air), List.copyOf(wall), List.copyOf(wallSide));
    }

    private boolean isAirMarker(BlockPos pos) {
        return isAir(pos)
                && (!airYCheck.getValue() || pos.getY() == mc.player.getBlockY() - 1 || pos.getY() == mc.player.getBlockY())
                && isHard(pos.above());
    }

    private Hole findHole(BlockPos origin) {
        if (!isHoleCell(origin)) return null;

        Hole hole = createHole(origin, HoleShape.Quad);
        if (hole != null) return hole;

        hole = createHole(origin, HoleShape.DoubleX);
        if (hole != null) return hole;

        hole = createHole(origin, HoleShape.DoubleZ);
        if (hole != null) return hole;

        return createHole(origin, HoleShape.Single);
    }

    private Hole createHole(BlockPos origin, HoleShape shape) {
        List<BlockPos> positions = switch (shape) {
            case Single -> List.of(origin);
            case DoubleX -> List.of(origin, origin.east());
            case DoubleZ -> List.of(origin, origin.south());
            case Quad -> List.of(origin, origin.east(), origin.south(), origin.south().east());
        };

        for (int i = 1; i < positions.size(); i++) {
            if (!isHoleCell(positions.get(i))) return null;
        }

        for (BlockPos pos : positions) {
            for (Direction direction : HORIZONTAL_DIRECTIONS) {
                BlockPos side = pos.relative(direction);
                if (!positions.contains(side) && !isHard(side)) return null;
            }
        }

        BlockPos oppositeCorner = switch (shape) {
            case Single -> origin;
            case DoubleX -> origin.east();
            case DoubleZ -> origin.south();
            case Quad -> origin.south().east();
        };
        AABB bounds = AABB.encapsulatingFullBlocks(origin, oppositeCorner);
        return new Hole(shape, positions, bounds, isSafeHole(positions));
    }

    private boolean isHoleCell(BlockPos pos) {
        return isHard(pos.below())
                && hasCollision(pos.below())
                && isAir(pos)
                && isAir(pos.above())
                && isAir(pos.above(2));
    }

    private boolean isSafeHole(List<BlockPos> positions) {
        for (BlockPos pos : positions) {
            if (!isBedrock(pos.below())) return false;

            for (Direction direction : HORIZONTAL_DIRECTIONS) {
                BlockPos side = pos.relative(direction);
                if (!positions.contains(side) && !isBedrock(side)) return false;
            }
        }
        return true;
    }

    private Direction getWallSide(BlockPos pos) {
        Direction closestSide = null;
        double closestDistanceSquared = Double.MAX_VALUE;
        Vec3 eyePosition = mc.player.getEyePosition();

        for (Direction direction : HORIZONTAL_DIRECTIONS) {
            BlockPos side = pos.relative(direction);
            if (!hasCollision(side.below()) || hasCollision(side) || hasCollision(side.above())) continue;

            double distanceSquared = eyePosition.distanceToSqr(side.getCenter());
            if (distanceSquared < closestDistanceSquared) {
                closestDistanceSquared = distanceSquared;
                closestSide = direction;
            }
        }
        return closestSide;
    }

    private boolean isAir(BlockPos pos) {
        return mc.level.isLoaded(pos) && mc.level.getBlockState(pos).isAir();
    }

    private boolean isBedrock(BlockPos pos) {
        return mc.level.isLoaded(pos) && mc.level.getBlockState(pos).is(Blocks.BEDROCK);
    }

    private boolean isHard(BlockPos pos) {
        if (!mc.level.isLoaded(pos)) return false;

        BlockState state = mc.level.getBlockState(pos);
        return state.is(Blocks.BEDROCK)
                || state.is(Blocks.OBSIDIAN)
                || state.is(Blocks.ENDER_CHEST)
                || state.is(Blocks.NETHERITE_BLOCK)
                || state.is(Blocks.CRYING_OBSIDIAN)
                || state.is(Blocks.RESPAWN_ANCHOR)
                || state.is(Blocks.ANCIENT_DEBRIS)
                || state.is(Blocks.ANVIL);
    }

    private boolean hasCollision(BlockPos pos) {
        return mc.level.isLoaded(pos) && !mc.level.getBlockState(pos).getCollisionShape(mc.level, pos).isEmpty();
    }

    private void draw(
            List<BlockPos> positions,
            BoolSetting fill, ColorSetting fillColor,
            BoolSetting fade, ColorSetting fadeColor,
            BoolSetting box, ColorSetting boxColor,
            double boxHeight
    ) {
        if (!fill.getValue() && !box.getValue()) return;

        for (BlockPos pos : positions) {
            AABB bounds = new AABB(
                    pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX() + 1.0, pos.getY() + boxHeight, pos.getZ() + 1.0
            );
            drawBox(bounds, pos.getCenter(), fill, fillColor, fade, fadeColor, box, boxColor);
        }
    }

    private void drawHoles(
            List<Hole> holes,
            BoolSetting fill, ColorSetting fillColor,
            BoolSetting fade, ColorSetting fadeColor,
            BoolSetting box, ColorSetting boxColor,
            double boxHeight
    ) {
        if (!fill.getValue() && !box.getValue()) return;

        for (Hole hole : holes) {
            AABB baseBounds = hole.bounds();
            AABB bounds = new AABB(
                    baseBounds.minX, baseBounds.minY, baseBounds.minZ,
                    baseBounds.maxX, baseBounds.minY + boxHeight, baseBounds.maxZ
            );
            drawBox(bounds, baseBounds.getCenter(), fill, fillColor, fade, fadeColor, box, boxColor);
        }
    }

    private void drawBox(
            AABB bounds, Vec3 fadePosition,
            BoolSetting fill, ColorSetting fillColor,
            BoolSetting fade, ColorSetting fadeColor,
            BoolSetting box, ColorSetting boxColor
    ) {
        double alpha = getDistanceAlpha(fadePosition);
        if (alpha <= 0.0) return;

        if (fill.getValue()) {
            int bottomColor = withAlpha(fillColor.getValue(), alpha);
            int topColor = fade.getValue() ? withAlpha(fadeColor.getValue(), alpha) : bottomColor;
            Render3DScheduler.INSTANCE.addFilledFadeBox(bounds, bottomColor, topColor);
        }
        if (box.getValue()) {
            Render3DScheduler.INSTANCE.addOutlineBox(bounds, withAlpha(boxColor.getValue(), alpha));
        }
    }

    private double getDistanceAlpha(Vec3 position) {
        double renderDistance = distance.getValue();
        double fadeStart = Math.min(startFade.getValue(), renderDistance);
        double blockDistance = mc.player.position().distanceTo(position);
        if (blockDistance <= fadeStart || fadeStart >= renderDistance) return 1.0;
        return Mth.clamp((renderDistance - blockDistance) / (renderDistance - fadeStart), 0.0, 1.0);
    }

    private int withAlpha(Color color, double multiplier) {
        int alpha = Mth.clamp((int) Math.round(color.getAlpha() * multiplier), 0, 255);
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha).getRGB();
    }

    private void clearResults() {
        unsafeHoles = List.of();
        safeHoles = List.of();
        airBlocks = List.of();
        walls = List.of();
        wallSides = List.of();
        scannedLevel = null;
    }

    private enum HoleShape {
        Single,
        DoubleX,
        DoubleZ,
        Quad
    }

    private record Hole(
            HoleShape shape,
            List<BlockPos> positions,
            AABB bounds,
            boolean safe
    ) {
    }

    private record ScanResult(
            List<Hole> unsafeHoles,
            List<Hole> safeHoles,
            List<BlockPos> airBlocks,
            List<BlockPos> walls,
            List<BlockPos> wallSides
    ) {
    }

}
