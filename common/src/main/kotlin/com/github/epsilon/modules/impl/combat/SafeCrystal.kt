package com.github.epsilon.modules.impl.combat

import com.github.epsilon.events.api.nonNullHandler
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.utils.player.SwingMode
import com.github.epsilon.utils.safety.NonNullContext
import com.github.epsilon.utils.safety.interaction
import com.github.epsilon.utils.safety.level
import com.github.epsilon.utils.safety.player
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.item.Items
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.*
import kotlin.random.Random

object SafeCrystal : Module("Safe Crystal", Category.COMBAT) {

    private val autoBreak by boolSetting("Auto Break", true)
    private val autoPlace by boolSetting("Auto Place", true)
    private val antiSuicide by doubleSetting("Anti Suicide", 2.0, 1.0..20.0, 0.5)

    private var attackCooldown = 0
    private var placeCooldown = 0

    private const val MAX_ATTACK_DISTANCE = 3.6

    init {
        nonNullHandler<PlayerTickEvent.Pre> {
            if (autoBreak) {
                handleAutoCrystalBreak()
            }

            if (autoPlace) {
                handleAutoCrystalPlace()
            }
        }
    }

    override fun onEnable() {
        attackCooldown = 0
        placeCooldown = 0
    }

    override fun onDisable() {
        attackCooldown = 0
        placeCooldown = 0
    }

    override fun getInfo(): String {
        if (autoBreak && autoPlace) return "Break+Place"
        if (autoBreak) return "Break"
        if (autoPlace) return "Place"
        return "None"
    }

    context(nn: NonNullContext)
    private fun handleAutoCrystalBreak() {
        if (player.health < antiSuicide.toFloat()) return
        if (attackCooldown > 0) {
            attackCooldown--
            return
        }

        val hit = mc.hitResult
        if (hit !is EntityHitResult) return

        val crystal = hit.entity as? EndCrystal ?: return

        if (player.distanceTo(crystal) > MAX_ATTACK_DISTANCE) return

        val playerHigherOrEqual = player.y >= crystal.y

        if (playerHigherOrEqual) {
            val start: Vec3 = player.eyePosition
            val end: Vec3 = crystal.position()

            val blockHit = level.clip(
                ClipContext(
                    start,
                    end,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    player
                )
            )

            if (blockHit.type == HitResult.Type.MISS) {
                return
            }
        }

        interaction.attack(player, crystal)
        SwingMode.Both.attackSwing(InteractionHand.MAIN_HAND)

        attackCooldown = 1 + (Random.nextDouble() * 2).toInt()
    }

    context(nn: NonNullContext)
    private fun handleAutoCrystalPlace() {
        if (placeCooldown > 0) {
            placeCooldown--
            return
        }

        val hit = mc.hitResult
        if (hit !is BlockHitResult) return

        val base: BlockPos = hit.blockPos

        if (!level.getBlockState(base).`is`(Blocks.OBSIDIAN) &&
            !level.getBlockState(base).`is`(Blocks.BEDROCK)
        ) {
            return
        }

        val above = base.above()

        if (!level.getBlockState(above).isAir) return

        val box = AABB(
            above.x.toDouble(), above.y.toDouble(), above.z.toDouble(),
            above.x + 1.0, above.y + 2.0, above.z + 1.0
        )

        if (!level.getEntities(null, box).isEmpty()) {
            return
        }

        val crystalHand = getCrystalHand() ?: return

        interaction.useItemOn(
            player,
            crystalHand,
            hit
        )

        SwingMode.Both.interactSwing(crystalHand)

        placeCooldown = 1 + (Random.nextDouble() * 2).toInt()
    }

    context(nn: NonNullContext)
    private fun getCrystalHand(): InteractionHand? {
        if (player.mainHandItem.`is`(Items.END_CRYSTAL)) {
            return InteractionHand.MAIN_HAND
        }

        if (player.offhandItem.`is`(Items.END_CRYSTAL)) {
            return InteractionHand.OFF_HAND
        }

        return null
    }

}
