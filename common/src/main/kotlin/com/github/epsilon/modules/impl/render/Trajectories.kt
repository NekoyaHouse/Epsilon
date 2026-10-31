package com.github.epsilon.modules.impl.render

import com.github.epsilon.events.api.handler
import com.github.epsilon.events.impl.Render3DEvent
import com.github.epsilon.graphics.schedulers.render3d.Render3DScheduler
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.SettingGroup
import com.github.epsilon.settings.group
import com.github.epsilon.settings.impl.RegistryListSetting
import com.github.epsilon.utils.entity.ProjectileSimulator
import com.github.epsilon.utils.math.toRadians
import com.github.epsilon.utils.player.EnchantmentUtils
import net.minecraft.core.Direction
import net.minecraft.core.component.DataComponents
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.entity.projectile.arrow.ThrownTrident
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull
import net.minecraft.world.item.CrossbowItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ChargedProjectiles
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.Vec3
import java.util.function.Predicate

object Trajectories : Module("Trajectories", Category.RENDER) {

    private val generalGroup: SettingGroup = settingGroup("General")
    private val renderGroup: SettingGroup = settingGroup("Render")

    private val items: RegistryListSetting<Item> = addSetting(
        RegistryListSetting(
            "Items", defaultItems(), RegistryListSetting.Type.ITEM,
            Predicate { item -> ProjectileSimulator.supports(item) },
            { true }
        )
    ).group(generalGroup)
    private val otherPlayers by boolSetting("Other Players", true).group(generalGroup)
    private val firedProjectiles by boolSetting("Fired Projectiles", false).group(generalGroup)
    private val ignoreWitherSkulls by boolSetting(
        "Ignore Wither Skulls",
        true, { firedProjectiles }).group(generalGroup)
    private val simulationSteps by intSetting("Simulation Steps", 500, 0..5000, 25).group(generalGroup)
    private val ignoreFirstTicks by intSetting("Ignore First Ticks", 3, 0..20, 1).group(renderGroup)
    private val lineColor by colorSetting("Line Color", java.awt.Color(255, 150, 0, 245)).group(renderGroup)
    private val lineWidth by doubleSetting("Line Width", 2.0, 0.5..5.0, 0.25).group(renderGroup)
    private val impactFill by boolSetting("Impact Fill", true).group(renderGroup)
    private val impactOutline by boolSetting("Impact Outline", true).group(renderGroup)
    private val impactFillColor by colorSetting(
        "Impact Fill Color", java.awt.Color(255, 150, 0, 45),
        true,
        { impactFill }
    ).group(renderGroup)
    private val impactOutlineColor by colorSetting(
        "Impact Outline Color", java.awt.Color(255, 180, 55, 245),
        true,
        { impactOutline }
    ).group(renderGroup)
    private val positionBoxes by boolSetting("Render Position Boxes", false).group(renderGroup)
    private val positionBoxSize by doubleSetting(
        "Position Box Size", 0.02, 0.01..0.1, 0.005,
        { positionBoxes }
    ).group(renderGroup)
    private val positionFillColor by colorSetting(
        "Position Fill Color", java.awt.Color(255, 150, 0, 28),
        true,
        { positionBoxes }
    ).group(renderGroup)
    private val positionOutlineColor by colorSetting(
        "Position Outline Color", java.awt.Color(255, 180, 55, 180),
        true,
        { positionBoxes }
    ).group(renderGroup)

    private val MULTISHOT_OFFSET: Double = (10.0).toRadians()

    init {
        handler<Render3DEvent> { onRender3D(it) }
    }

    private fun onRender3D(event: Render3DEvent) {
        val level = mc.level ?: return
        val partialTick =
            if (level.tickRateManager().isFrozen()) 1.0F else mc.deltaTracker.getGameTimeDeltaPartialTick(true)

        for (targetPlayer in level.players()) {
            if (!otherPlayers && targetPlayer !== mc.player) continue

            var stack: ItemStack = targetPlayer.mainHandItem
            if (!items.contains(stack.item)) {
                stack = targetPlayer.offhandItem
                if (!items.contains(stack.item)) continue
            }

            if (stack.item is CrossbowItem) {
                val charged: ChargedProjectiles? = stack.get(DataComponents.CHARGED_PROJECTILES)
                if (charged == null || charged.isEmpty) continue

                val firework = charged.contains(Items.FIREWORK_ROCKET)
                val piercing = if (firework) 0 else EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.PIERCING)
                renderCrossbowPath(targetPlayer, partialTick, firework, piercing, 0.0)

                if (EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.MULTISHOT) > 0) {
                    renderCrossbowPath(targetPlayer, partialTick, firework, piercing, MULTISHOT_OFFSET)
                    renderCrossbowPath(targetPlayer, partialTick, firework, piercing, -MULTISHOT_OFFSET)
                }
                continue
            }

            val simulator = ProjectileSimulator(level)
            if (!simulator.configureHeld(targetPlayer, stack, 0.0, partialTick, 0)) continue
            renderPath(
                calculate(
                    simulator,
                    if (targetPlayer === mc.player) ignoreFirstTicks else 0,
                    null
                )
            )
        }

        if (firedProjectiles) {
            for (entity in level.entitiesForRendering()) {
                if (entity !is Projectile) continue
                if (ignoreWitherSkulls && entity is WitherSkull) continue
                if (entity is ThrownTrident && entity.noPhysics) continue
                val simulator = ProjectileSimulator(level)
                if (!simulator.configureFired(entity)) continue
                renderPath(calculate(simulator, 0, entity.getPosition(partialTick)))
            }
        }
    }

    private fun renderCrossbowPath(
        player: Player,
        partialTick: Float,
        firework: Boolean,
        piercing: Int,
        angle: Double,
    ) {
        val level = mc.level ?: return
        val simulator = ProjectileSimulator(level)
        simulator.configureCrossbow(player, firework, angle, partialTick, piercing)
        renderPath(calculate(simulator, if (player === mc.player) ignoreFirstTicks else 0, null))
    }

    private fun calculate(simulator: ProjectileSimulator, ignoredTicks: Int, renderedStart: Vec3?): Path {
        val points = ArrayList<Vec3>()
        points.add(renderedStart ?: simulator.position())
        var blockHit: BlockHitResult? = null
        val entityHits = ArrayList<Entity>()
        val limit = if (simulationSteps == 0) Int.MAX_VALUE else simulationSteps

        for (i in 0 until limit) {
            val step = simulator.tick()
            points.add(step.position)
            when (val hit = step.hit) {
                is BlockHitResult -> blockHit = hit
                is EntityHitResult -> if (!entityHits.contains(hit.entity)) {
                    entityHits.add(hit.entity)
                }

                else -> {
                }
            }
            if (step.stop) break
        }

        val start = Mth.clamp(points.size - 2, 0, ignoredTicks)
        return Path(points, start, blockHit, entityHits)
    }

    private fun renderPath(path: Path) {
        val width = lineWidth.toFloat()

        for (i in path.start + 1 until path.points.size) {
            val from = path.points[i - 1]
            val to = path.points[i]
            Render3DScheduler.addLine(from, to, lineColor, width)
            if (positionBoxes) {
                val size = positionBoxSize
                val box = AABB(to.x - size, to.y - size, to.z - size, to.x + size, to.y + size, to.z + size)
                Render3DScheduler.addFilledBox(box, positionFillColor)
                Render3DScheduler.addOutlineBox(box, positionOutlineColor, minOf(width, 1.5F))
            }
        }

        val hit = path.blockHit
        if (hit != null) {
            val point = hit.location
            val direction = hit.direction
            val radius = 0.25
            val depth = 0.002
            val box: AABB

            if (direction.axis == Direction.Axis.Y) {
                box = AABB(
                    point.x - radius, point.y - depth, point.z - radius,
                    point.x + radius, point.y + depth, point.z + radius
                )
            } else if (direction.axis == Direction.Axis.Z) {
                box = AABB(
                    point.x - radius, point.y - radius, point.z - depth,
                    point.x + radius, point.y + radius, point.z + depth
                )
            } else {
                box = AABB(
                    point.x - depth, point.y - radius, point.z - radius,
                    point.x + depth, point.y + radius, point.z + radius
                )
            }

            if (impactFill) {
                Render3DScheduler.addFilledSide(box, impactFillColor.rgb, direction)
            }
            if (impactOutline) {
                Render3DScheduler.addSideOutline(box, impactOutlineColor.rgb, width, direction)
            }
        }

        for (entity in path.entityHits) {
            val partialTick = mc.deltaTracker.getGameTimeDeltaPartialTick(true)
            val offset = entity.getPosition(partialTick).subtract(entity.position())
            val box = entity.boundingBox.move(offset)
            if (impactFill) Render3DScheduler.addFilledBox(box, impactFillColor)
            if (impactOutline) Render3DScheduler.addOutlineBox(box, impactOutlineColor, width)
        }
    }

    private fun defaultItems(): List<Item> = listOf(
        Items.BOW, Items.CROSSBOW, Items.TRIDENT, Items.FISHING_ROD,
        Items.SNOWBALL, Items.EGG, Items.ENDER_PEARL, Items.EXPERIENCE_BOTTLE,
        Items.SPLASH_POTION, Items.LINGERING_POTION, Items.WIND_CHARGE
    )

    private data class Path(
        val points: MutableList<Vec3>,
        val start: Int,
        val blockHit: BlockHitResult?,
        val entityHits: MutableList<Entity>,
    )

}
