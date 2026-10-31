package com.github.epsilon.modules.impl.render

import com.github.epsilon.events.api.handler
import com.github.epsilon.events.api.safeEventHandler
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.events.impl.Render3DEvent
import com.github.epsilon.graphics.schedulers.render3d.Render3DScheduler
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.SettingGroup
import com.github.epsilon.settings.group
import com.github.epsilon.utils.timer.TimerUtils
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.Mth
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.awt.Color
import kotlin.math.roundToInt

object HoleESP : Module("Hole ESP", Category.RENDER) {

    private val sgGeneral: SettingGroup = settingGroup("General")
    private val sgAir: SettingGroup = settingGroup("Air")
    private val sgUnsafe: SettingGroup = settingGroup("Unsafe")
    private val sgSafe: SettingGroup = settingGroup("Safe")
    private val sgWall: SettingGroup = settingGroup("Wall")
    private val sgWallSide: SettingGroup = settingGroup("Wall Side")

    private val startFade by doubleSetting("Start Fade", 5.0, 1.0..20.0, 0.1).group(sgGeneral)
    private val distance by doubleSetting("Distance", 6.0, 1.0..20.0, 0.1).group(sgGeneral)
    private val height by doubleSetting("Height", 1.0, -3.0..3.0, 0.1).group(sgGeneral)
    private val updateDelay by intSetting("Update Delay", 50, 0..1000, 10).group(sgGeneral)

    private val airHeight by doubleSetting("Air Height", 1.0, -3.0..3.0, 0.01).group(sgAir)
    private val airYCheck by boolSetting("Air Y Check", true).group(sgAir)
    private val airFill by boolSetting("Air Fill", true).group(sgAir)
    private val airFillColor by colorSetting(
        "Air Fill Color", Color(148, 0, 0, 100),
        true,
        { airFill }
    ).group(sgAir)
    private val airFade by boolSetting("Air Fade", true, { airFill }).group(sgAir)
    private val airFadeColor by colorSetting(
        "Air Fade Color", Color(148, 0, 0, 0),
        true,
        { airFill && airFade }
    ).group(sgAir)
    private val airBox by boolSetting("Air Box", true).group(sgAir)
    private val airBoxColor by colorSetting(
        "Air Box Color", Color(148, 0, 0, 100),
        true,
        { airBox }
    ).group(sgAir)

    private val unsafeFill by boolSetting("Unsafe Fill", true).group(sgUnsafe)
    private val unsafeFillColor by colorSetting(
        "Unsafe Fill Color", Color(255, 0, 0, 50),
        true,
        { unsafeFill }
    ).group(sgUnsafe)
    private val unsafeFade by boolSetting("Unsafe Fade", true, { unsafeFill }).group(sgUnsafe)
    private val unsafeFadeColor by colorSetting(
        "Unsafe Fade Color", Color(255, 0, 0, 0),
        true,
        { unsafeFill && unsafeFade }
    ).group(sgUnsafe)
    private val unsafeBox by boolSetting("Unsafe Box", true).group(sgUnsafe)
    private val unsafeBoxColor by colorSetting(
        "Unsafe Box Color", Color(255, 0, 0, 100),
        true,
        { unsafeBox }
    ).group(sgUnsafe)

    private val safeFill by boolSetting("Safe Fill", true).group(sgSafe)
    private val safeFillColor by colorSetting(
        "Safe Fill Color", Color(8, 255, 79, 50),
        true,
        { safeFill }
    ).group(sgSafe)
    private val safeFade by boolSetting("Safe Fade", true, { safeFill }).group(sgSafe)
    private val safeFadeColor by colorSetting(
        "Safe Fade Color", Color(8, 255, 79, 100),
        true,
        { safeFill && safeFade }
    ).group(sgSafe)
    private val safeBox by boolSetting("Safe Box", true).group(sgSafe)
    private val safeBoxColor by colorSetting(
        "Safe Box Color", Color(8, 255, 79, 100),
        true,
        { safeBox }
    ).group(sgSafe)

    private val wallHeight by doubleSetting("Wall Height", 3.0, -3.0..3.0, 0.1).group(sgWall)
    private val sideCheck by boolSetting("Side Check", true).group(sgWall)
    private val wallFill by boolSetting("Wall Fill", true).group(sgWall)
    private val wallFillColor by colorSetting(
        "Wall Fill Color", Color(0, 255, 255, 128),
        true,
        { wallFill }
    ).group(sgWall)
    private val wallFade by boolSetting("Wall Fade", true, { wallFill }).group(sgWall)
    private val wallFadeColor by colorSetting(
        "Wall Fade Color", Color(0, 255, 255, 64),
        true,
        { wallFill && wallFade }
    ).group(sgWall)
    private val wallBox by boolSetting("Wall Box", true).group(sgWall)
    private val wallBoxColor by colorSetting(
        "Wall Box Color", Color(0, 225, 255, 255),
        true,
        { wallBox }
    ).group(sgWall)

    private val wallSideFill by boolSetting("Wall Side Fill", true).group(sgWallSide)
    private val wallSideFillColor by colorSetting(
        "Wall Side Fill Color", Color(0, 255, 255, 128),
        true,
        { wallSideFill }
    ).group(sgWallSide)
    private val wallSideFade by boolSetting(
        "Wall Side Fade", true,
        { wallSideFill }
    ).group(sgWallSide)
    private val wallSideFadeColor by colorSetting(
        "Wall Side Fade Color", Color(0, 255, 255, 64),
        true,
        { wallSideFill && wallSideFade }
    ).group(sgWallSide)
    private val wallSideBox by boolSetting("Wall Side Box", true).group(sgWallSide)
    private val wallSideBoxColor by colorSetting(
        "Wall Side Box Color", Color(0, 225, 255, 255),
        true,
        { wallSideBox }
    ).group(sgWallSide)

    private var unsafeHoles: List<Hole> = emptyList()
    private var safeHoles: List<Hole> = emptyList()
    private var airBlocks: List<BlockPos> = emptyList()
    private var walls: List<BlockPos> = emptyList()
    private var wallSides: List<BlockPos> = emptyList()
    private var scannedLevel: ClientLevel? = null

    private val updateTimer = TimerUtils()

    private val HORIZONTAL_DIRECTIONS = arrayOf(
        Direction.NORTH,
        Direction.SOUTH,
        Direction.WEST,
        Direction.EAST
    )

    init {
        safeEventHandler<PlayerTickEvent.Pre> {
            if (scannedLevel !== level) updateTimer.setMs(updateDelay.toLong())
            if (!updateTimer.every(updateDelay.toLong())) return@safeEventHandler

            val result = scan()
            unsafeHoles = result.unsafeHoles
            safeHoles = result.safeHoles
            airBlocks = result.airBlocks
            walls = result.walls
            wallSides = result.wallSides
            scannedLevel = level
        }

        handler<Render3DEvent> {
            if (scannedLevel !== mc.level) return@handler

            drawHoles(safeHoles, safeFill, safeFillColor, safeFade, safeFadeColor, safeBox, safeBoxColor, height)
            draw(airBlocks, airFill, airFillColor, airFade, airFadeColor, airBox, airBoxColor, airHeight)
            drawHoles(
                unsafeHoles,
                unsafeFill,
                unsafeFillColor,
                unsafeFade,
                unsafeFadeColor,
                unsafeBox,
                unsafeBoxColor,
                height
            )
            draw(walls, wallFill, wallFillColor, wallFade, wallFadeColor, wallBox, wallBoxColor, wallHeight)
            draw(
                wallSides,
                wallSideFill,
                wallSideFillColor,
                wallSideFade,
                wallSideFadeColor,
                wallSideBox,
                wallSideBoxColor,
                height
            )
        }
    }

    override fun onEnable() {
        clearResults()
        updateTimer.setMs(updateDelay.toLong())
    }

    override fun onDisable() {
        clearResults()
    }

    private fun scan(): ScanResult {
        val unsafe = ArrayList<Hole>()
        val safe = ArrayList<Hole>()
        val air = ArrayList<BlockPos>()
        val wall = ArrayList<BlockPos>()
        val wallSide = ArrayList<BlockPos>()
        val processedHoleCells = HashSet<BlockPos>()

        val player = mc.player ?: return ScanResult(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        val level = mc.level ?: return ScanResult(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())

        val center = player.position()
        val radius = distance
        val radiusSquared = radius * radius
        val minX = Mth.floor(center.x - radius)
        val maxX = Mth.floor(center.x + radius)
        val minY = maxOf(level.minY + 1, Mth.floor(center.y - radius))
        val maxY = minOf(level.maxY - 2, Mth.floor(center.y + radius))
        val minZ = Mth.floor(center.z - radius)
        val maxZ = Mth.floor(center.z + radius)

        for (y in maxY downTo minY) {
            for (x in minX..maxX) {
                for (z in minZ..maxZ) {
                    val pos = BlockPos(x, y, z)
                    if (pos.distToCenterSqr(center) > radiusSquared || !level.isLoaded(pos)) continue

                    if (isBedrock(pos) && isBedrock(pos.above(2)) && isBedrock(pos.below())) {
                        val side = getWallSide(pos)
                        if (side != null || !sideCheck) wall.add(pos)
                        if (side != null) wallSide.add(pos.relative(side))
                    }

                    if (isAirMarker(pos)) air.add(pos)

                    if (!processedHoleCells.contains(pos)) {
                        val hole = findHole(pos)
                        if (hole != null) {
                            processedHoleCells.addAll(hole.positions)
                            if (hole.safe) {
                                safe.add(hole)
                            } else {
                                unsafe.add(hole)
                            }
                        }
                    }
                }
            }
        }

        return ScanResult(unsafe, safe, air, wall, wallSide)
    }

    private fun isAirMarker(pos: BlockPos): Boolean {
        val player = mc.player ?: return false
        return isAir(pos) &&
                (!airYCheck || pos.y == player.blockY - 1 || pos.y == player.blockY) &&
                isHard(pos.above())
    }

    private fun findHole(origin: BlockPos): Hole? {
        if (!isHoleCell(origin)) return null

        return createHole(origin, HoleShape.Quad)
            ?: createHole(origin, HoleShape.DoubleX)
            ?: createHole(origin, HoleShape.DoubleZ)
            ?: createHole(origin, HoleShape.Single)
    }

    private fun createHole(origin: BlockPos, shape: HoleShape): Hole? {
        val positions = when (shape) {
            HoleShape.Single -> listOf(origin)
            HoleShape.DoubleX -> listOf(origin, origin.east())
            HoleShape.DoubleZ -> listOf(origin, origin.south())
            HoleShape.Quad -> listOf(origin, origin.east(), origin.south(), origin.south().east())
        }

        for (i in 1 until positions.size) {
            if (!isHoleCell(positions[i])) return null
        }

        for (pos in positions) {
            for (direction in HORIZONTAL_DIRECTIONS) {
                val side = pos.relative(direction)
                if (!positions.contains(side) && !isHard(side)) return null
            }
        }

        val oppositeCorner = when (shape) {
            HoleShape.Single -> origin
            HoleShape.DoubleX -> origin.east()
            HoleShape.DoubleZ -> origin.south()
            HoleShape.Quad -> origin.south().east()
        }
        val bounds = AABB.encapsulatingFullBlocks(origin, oppositeCorner)
        return Hole(shape, positions, bounds, isSafeHole(positions))
    }

    private fun isHoleCell(pos: BlockPos): Boolean = isHard(pos.below()) &&
            hasCollision(pos.below()) &&
            isAir(pos) &&
            isAir(pos.above()) &&
            isAir(pos.above(2))

    private fun isSafeHole(positions: List<BlockPos>): Boolean {
        for (pos in positions) {
            if (!isBedrock(pos.below())) return false

            for (direction in HORIZONTAL_DIRECTIONS) {
                val side = pos.relative(direction)
                if (!positions.contains(side) && !isBedrock(side)) return false
            }
        }
        return true
    }

    private fun getWallSide(pos: BlockPos): Direction? {
        var closestSide: Direction? = null
        var closestDistanceSquared = Double.MAX_VALUE
        val player = mc.player ?: return null
        val eyePosition = player.eyePosition

        for (direction in HORIZONTAL_DIRECTIONS) {
            val side = pos.relative(direction)
            if (!hasCollision(side.below()) || hasCollision(side) || hasCollision(side.above())) continue

            val distanceSquared = eyePosition.distanceToSqr(Vec3.atCenterOf(side))
            if (distanceSquared < closestDistanceSquared) {
                closestDistanceSquared = distanceSquared
                closestSide = direction
            }
        }
        return closestSide
    }

    private fun isAir(pos: BlockPos): Boolean {
        val level = mc.level ?: return false
        return level.isLoaded(pos) && level.getBlockState(pos).isAir
    }

    private fun isBedrock(pos: BlockPos): Boolean {
        val level = mc.level ?: return false
        return level.isLoaded(pos) && level.getBlockState(pos).`is`(Blocks.BEDROCK)
    }

    private fun isHard(pos: BlockPos): Boolean {
        val level = mc.level ?: return false
        if (!level.isLoaded(pos)) return false

        val state = level.getBlockState(pos)
        return state.`is`(Blocks.BEDROCK) ||
                state.`is`(Blocks.OBSIDIAN) ||
                state.`is`(Blocks.ENDER_CHEST) ||
                state.`is`(Blocks.NETHERITE_BLOCK) ||
                state.`is`(Blocks.CRYING_OBSIDIAN) ||
                state.`is`(Blocks.RESPAWN_ANCHOR) ||
                state.`is`(Blocks.ANCIENT_DEBRIS) ||
                state.`is`(Blocks.ANVIL)
    }

    private fun hasCollision(pos: BlockPos): Boolean {
        val level = mc.level ?: return false
        return level.isLoaded(pos) && !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty
    }

    private fun draw(
        positions: List<BlockPos>,
        fill: Boolean, fillColor: Color,
        fade: Boolean, fadeColor: Color,
        box: Boolean, boxColor: Color,
        boxHeight: Double,
    ) {
        if (!fill && !box) return

        for (pos in positions) {
            val bounds = AABB(
                pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(),
                pos.x + 1.0, pos.y + boxHeight, pos.z + 1.0
            )
            drawBox(bounds, Vec3.atCenterOf(pos), fill, fillColor, fade, fadeColor, box, boxColor)
        }
    }

    private fun drawHoles(
        holes: List<Hole>,
        fill: Boolean, fillColor: Color,
        fade: Boolean, fadeColor: Color,
        box: Boolean, boxColor: Color,
        boxHeight: Double,
    ) {
        if (!fill && !box) return

        for (hole in holes) {
            val baseBounds = hole.bounds
            val bounds = AABB(
                baseBounds.minX, baseBounds.minY, baseBounds.minZ,
                baseBounds.maxX, baseBounds.minY + boxHeight, baseBounds.maxZ
            )
            drawBox(bounds, baseBounds.center, fill, fillColor, fade, fadeColor, box, boxColor)
        }
    }

    private fun drawBox(
        bounds: AABB, fadePosition: Vec3,
        fill: Boolean, fillColor: Color,
        fade: Boolean, fadeColor: Color,
        box: Boolean, boxColor: Color,
    ) {
        val alpha = getDistanceAlpha(fadePosition)
        if (alpha <= 0.0) return

        if (fill) {
            val bottomColor = withAlpha(fillColor, alpha)
            val topColor = if (fade) withAlpha(fadeColor, alpha) else bottomColor
            Render3DScheduler.addFilledFadeBox(bounds, bottomColor, topColor)
        }
        if (box) {
            Render3DScheduler.addOutlineBox(bounds, withAlpha(boxColor, alpha))
        }
    }

    private fun getDistanceAlpha(position: Vec3): Double {
        val player = mc.player ?: return 1.0
        val renderDistance = distance
        val fadeStart = minOf(startFade, renderDistance)
        val blockDistance = player.position().distanceTo(position)
        if (blockDistance <= fadeStart || fadeStart >= renderDistance) return 1.0
        return ((renderDistance - blockDistance) / (renderDistance - fadeStart)).coerceIn(0.0, 1.0)
    }

    private fun withAlpha(color: Color, multiplier: Double): Int {
        val alpha = (color.alpha * multiplier).roundToInt().coerceIn(0, 255)
        return Color(color.red, color.green, color.blue, alpha).rgb
    }

    private fun clearResults() {
        unsafeHoles = emptyList()
        safeHoles = emptyList()
        airBlocks = emptyList()
        walls = emptyList()
        wallSides = emptyList()
        scannedLevel = null
    }

    private enum class HoleShape {
        Single,
        DoubleX,
        DoubleZ,
        Quad
    }

    private data class Hole(
        val shape: HoleShape,
        val positions: List<BlockPos>,
        val bounds: AABB,
        val safe: Boolean,
    )

    private data class ScanResult(
        val unsafeHoles: List<Hole>,
        val safeHoles: List<Hole>,
        val airBlocks: List<BlockPos>,
        val walls: List<BlockPos>,
        val wallSides: List<BlockPos>,
    )

}
