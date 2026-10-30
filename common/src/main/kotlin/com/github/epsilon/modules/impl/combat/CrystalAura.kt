package com.github.epsilon.modules.impl.combat

import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.events.impl.RightClickEvent
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.getValue
import com.github.epsilon.utils.NonNullContext
import com.github.epsilon.utils.client.KeybindUtils
import com.github.epsilon.utils.interaction
import com.github.epsilon.utils.math.MathUtils
import com.github.epsilon.utils.player
import com.github.epsilon.utils.player.FindItemResult
import com.github.epsilon.utils.player.InvUtils
import com.github.epsilon.utils.player.PlayerUtils
import com.github.epsilon.utils.world
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.KeyMapping
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.tags.ItemTags
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.entity.monster.cubemob.Slime
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.Vec3

object CrystalAura : Module("Crystal Aura", Category.COMBAT) {

    private val activateKey by keybindSetting("Activate Key", InputConstants.UNKNOWN.value)
    private val placeDelay by doubleSetting("Place Delay", 0.0, 0.0, 20.0, 1.0)
    private val breakDelay by doubleSetting("Break Delay", 0.0, 0.0, 20.0, 1.0)
    private val placeChance by doubleSetting("Place Chance", 100.0, 0.0, 100.0, 1.0)
    private val breakChance by doubleSetting("Break Chance", 100.0, 0.0, 100.0, 1.0)
    private val stopOnKill by boolSetting("Stop on Kill", false)
    private val fakePunch by boolSetting("Fake Punch", false)
    private val clickSimulation by boolSetting("Click Simulation", false)
    private val damageTick by boolSetting("Damage Tick", false)
    private val antiWeakness by boolSetting("Anti-Weakness", false)
    private val strictAirCheck by boolSetting("Strict Air Check", false)
    private val particleChance by doubleSetting("Particle Chance", 20.0, 0.0, 100.0, 1.0)
    private val swingHand by boolSetting("Swing Hand", true)

    private var placeClock = 0
    private var breakClock = 0
    var crystalling = false

    init {
        nonNullListener<PlayerTickEvent.Pre> {
            onTick(it)
        }

        nonNullListener<RightClickEvent> { event ->
            if (!mc.mouseHandler.isRightPressed()) return@nonNullListener
            if (!player.mainHandItem.`is`(Items.END_CRYSTAL)) return@nonNullListener

            val hit = mc.hitResult
            if (hit is BlockHitResult) {
                if ((world.getBlockState(hit.blockPos).`is`(Blocks.OBSIDIAN) ||
                        world.getBlockState(hit.blockPos).`is`(Blocks.BEDROCK)) &&
                    canPlaceCrystalAssumeObsidian(hit.blockPos)
                ) {
                    event.cancel()
                }
            }
        }
    }

    fun resetClocks() {
        placeClock = 0
        breakClock = 0
    }

    override fun onEnable() {
        resetClocks()
        crystalling = false
    }

    override fun onDisable() {
        resetClocks()
        crystalling = false
    }

    context(nn: NonNullContext)
    fun onTick(event: PlayerTickEvent.Pre?) {
        if (mc.gui.screen() != null) return

        val dontPlace = placeClock != 0
        val dontBreak = breakClock != 0

        if (stopOnKill && isDeadBodyNearby()) return

        var randomInt = MathUtils.getRandom(1, 100)

        if (dontPlace) placeClock--
        if (dontBreak) breakClock--

        if (player.isUsingItem) return
        if (damageTick && damageTickCheck()) return

        if (activateKey != InputConstants.UNKNOWN.value && !KeybindUtils.isPressed(activateKey)) {
            resetClocks()
            crystalling = false
            return
        }

        crystalling = true

        if (!player.mainHandItem.`is`(Items.END_CRYSTAL)) return

        val blockHit = mc.hitResult as? BlockHitResult
        if (blockHit != null) {
            if (!dontPlace &&
                randomInt <= placeChance.toInt() &&
                (world.getBlockState(blockHit.blockPos).`is`(Blocks.OBSIDIAN) ||
                    world.getBlockState(blockHit.blockPos).`is`(Blocks.BEDROCK)) &&
                canPlaceCrystalAssumeObsidian(blockHit.blockPos)
            ) {

                if (clickSimulation) {
                    KeyMapping.click(mc.options.keyUse.defaultKey)
                }

                var placeHit = blockHit
                if (blockHit.direction != Direction.UP) {
                    placeHit = BlockHitResult(
                        Vec3.atCenterOf(blockHit.blockPos).add(0.0, 0.5, 0.0),
                        Direction.UP,
                        blockHit.blockPos,
                        false
                    )
                }

                val result: InteractionResult =
                    interaction.useItemOn(player, InteractionHand.MAIN_HAND, placeHit)
                if (result.consumesAction()) {
                    if (swingHand) {
                        PlayerUtils.swingHand(InteractionHand.MAIN_HAND)
                    }
                }

                if (fakePunch && randomInt <= particleChance.toInt() &&
                    blockHit.direction == Direction.UP
                ) {
                    // Placeholder for old block break particles behavior.
                    PlayerUtils.swingHand(InteractionHand.MAIN_HAND)
                }

                placeClock = placeDelay.toInt()
            }
        }

        randomInt = MathUtils.getRandom(1, 100)

        val entityHit = mc.hitResult as? EntityHitResult
        if (entityHit != null) {
            if (!dontBreak && randomInt <= breakChance.toInt()) {
                val entity: Entity = entityHit.entity

                if (!(fakePunch || entity is EndCrystal || entity is Slime)) {
                    return
                }

                val prevSlot = player.inventory.getSelectedSlot()
                var swappedForWeakness = false

                if ((entity is EndCrystal || entity is Slime) && antiWeakness && cantBreakCrystal()) {
                    swappedForWeakness = selectSword()
                }

                if (clickSimulation) {
                    KeyMapping.click(mc.options.keyAttack.defaultKey)
                }

                interaction.attack(player, entity)

                if (swingHand) {
                    PlayerUtils.swingHand(InteractionHand.MAIN_HAND)
                }

                breakClock = breakDelay.toInt()

                if (antiWeakness && swappedForWeakness) {
                    InvUtils.swap(prevSlot, false)
                }
            }
        }
    }

    private fun selectSword(): Boolean {
        val sword: FindItemResult = InvUtils.findInHotbar { stack -> stack.`is`(ItemTags.SWORDS) }

        if (!sword.found()) return false
        InvUtils.swap(sword.slot, false)
        return true
    }

    context(nn: NonNullContext)
    private fun canPlaceCrystalAssumeObsidian(supportPos: BlockPos): Boolean {
        val crystalPos = supportPos.above()
        if (!world.getBlockState(crystalPos).isAir) return false
        if (strictAirCheck && !world.getBlockState(crystalPos.above()).isAir) return false
        val box = AABB(
            crystalPos.x.toDouble(), crystalPos.y.toDouble(), crystalPos.z.toDouble(),
            crystalPos.x + 1.0, crystalPos.y + 2.0, crystalPos.z + 1.0
        )
        return world.getEntities(null, box).isEmpty()
    }

    context(nn: NonNullContext)
    private fun cantBreakCrystal(): Boolean {
        val weakness: MobEffectInstance? = player.getEffect(MobEffects.WEAKNESS)
        val strength: MobEffectInstance? = player.getEffect(MobEffects.STRENGTH)

        return weakness != null &&
            (strength == null || strength.amplifier <= weakness.amplifier) &&
            !isToolLike(player.mainHandItem)
    }

    private fun isToolLike(stack: ItemStack): Boolean {
        return stack.`is`(ItemTags.SWORDS) ||
            stack.`is`(ItemTags.AXES) ||
            stack.`is`(ItemTags.PICKAXES) ||
            stack.`is`(ItemTags.SHOVELS) ||
            stack.`is`(ItemTags.HOES)
    }

    context(nn: NonNullContext)
    private fun isDeadBodyNearby(): Boolean {
        for (otherPlayer in world.players()) {
            if (otherPlayer === player) continue
            if (otherPlayer.distanceToSqr(player) > 36.0) continue
            if (!otherPlayer.isAlive || otherPlayer.deathTime > 0) {
                return true
            }
        }
        return false
    }

    context(nn: NonNullContext)
    private fun damageTickCheck(): Boolean {
        val found = world.players().stream()
            .filter { p -> p !== nn.player }
            .filter { p -> p.distanceToSqr(nn.player) < 36.0 }
            .filter { p -> !p.onGround() }
            .anyMatch { p -> p.hurtTime >= 2 }

        if (!found) return false

        val entityHit = mc.hitResult as? EntityHitResult
        return !(entityHit != null && entityHit.entity is net.minecraft.world.entity.player.Player)
    }

}
