package com.github.epsilon.modules.impl.player

import com.github.epsilon.utils.*
import com.github.epsilon.events.impl.PacketEvent
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.managers.rotation.RotationManager
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.modules.impl.combat.KillAura
import com.github.epsilon.modules.impl.movement.Scaffold
import com.github.epsilon.settings.getValue
import com.github.epsilon.utils.safety.*
import com.github.epsilon.utils.player.PlayerUtils
import com.github.epsilon.utils.rotation.Priority
import com.github.epsilon.utils.rotation.Rot2f
import com.github.epsilon.utils.rotation.RotationUtils
import com.github.epsilon.utils.timer.TimerUtils
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.minecraft.world.level.block.entity.FurnaceBlockEntity
import net.minecraft.world.level.block.entity.SmokerBlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import java.util.HashSet

object ChestAura : Module("Chest Aura", Category.PLAYER) {

    private val chests by boolSetting("Chests", true)
    private val furnaces by boolSetting("Furnaces", false)
    private val blastFurnaces by boolSetting("Blast Furnaces", false)
    private val smokers by boolSetting("Smokers", false)
    private val brewingStands by boolSetting("Brewing Stands", false)
    private val swing by boolSetting("Swing", true)
    private val pauseOnEat by boolSetting("Pause On Eat", true)
    private val requireStealer by boolSetting("Require Stealer", true)
    private val ignoreOtherOpened by boolSetting("Ignore Other Opened", false)
    private val range by doubleSetting("Range", 4.5, 0.0, 7.0, 0.1)
    private val throughWallRange by doubleSetting("Through Wall Range", 4.5, 0.0, 7.0, 0.1)
    private val cancelRange by doubleSetting("Cancel Range", 0.0, 0.0, 20.0, 0.1)
    private val delay by intSetting("Delay", 400, 0, 1000, 1)
    private val turnSpeed by doubleSetting("Turn Speed", 180.0, 0.0, 180.0, 0.1)
    private val disableInHypixelLobby by boolSetting("Disable In Hypixel Lobby", false)

    private val timer = TimerUtils()
    private val clickedContainers: MutableSet<BlockPos> = HashSet()
    private val playerClickedContainers: MutableSet<BlockPos> = HashSet()

    private var trackedLevel: ClientLevel? = null
    private var opening = false
    private var openingStartedAt = 0L

    init {
        nonNullListener<PlayerTickEvent.Pre> {
            updateTrackedLevel()

            if (opening) {
                if (mc.gui.screen() is AbstractContainerScreen<*>) return@nonNullListener
                if (System.currentTimeMillis() - openingStartedAt < 1000L) return@nonNullListener
                opening = false
                timer.reset()
            }

            if (shouldPause() || !timer.passedMillise(delay.toLong())) return@nonNullListener

            if (hasNearbyPlayer()) {
                if (mc.gui.screen() is AbstractContainerScreen<*>) {
                    player.closeContainer()
                }
                return@nonNullListener
            }

            if (mc.gui.screen() is AbstractContainerScreen<*>) return@nonNullListener

            val target = findTarget() ?: return@nonNullListener

            val targetRotation = calculate(player.getEyePosition(), target.hit.location)
            RotationManager.INSTANCE.setRotations(
                targetRotation,
                turnSpeed,
                { rotation -> hitsTarget(rotation, target) },
                Priority.Lowest,
            )

            val hit = raycastTarget(RotationManager.INSTANCE.getRotation(), target.pos, target.throughWalls)
            if (hit == null || hit.blockPos != target.pos) return@nonNullListener

            val result = interaction.useItemOn(player, InteractionHand.MAIN_HAND, hit)
            if (!result.consumesAction()) return@nonNullListener

            playerClickedContainers.add(target.pos)
            clickedContainers.add(target.pos)
            opening = true
            openingStartedAt = System.currentTimeMillis()
            if (swing) {
                swingHand(InteractionHand.MAIN_HAND)
            }
        }

        nonNullListener<PacketEvent.Send> { event ->
            val packet = event.packet
            if (packet is ServerboundUseItemOnPacket) {
                val pos = packet.hitResult().blockPos
                if (isSelectedContainer(level.getBlockEntity(pos))) {
                    clickedContainers.add(pos.immutable())
                    playerClickedContainers.add(pos.immutable())
                }
            } else if (packet is ServerboundContainerClosePacket) {
                finishOpening()
            }
        }

        nonNullListener<PacketEvent.Receive> { event ->
            val packet = event.packet
            if (packet is ClientboundBlockEventPacket) {
                markContainerOpened(packet.pos)
            } else if (packet is ClientboundSoundPacket) {
                markContainerOpened(BlockPos.containing(packet.x, packet.y, packet.z))
            } else if (packet is ClientboundContainerClosePacket) {
                finishOpening()
            }
        }
    }

    override fun onEnable() {
        opening = false
        openingStartedAt = 0L
        timer.reset()
        updateTrackedLevel()
    }

    override fun onDisable() {
        opening = false
        openingStartedAt = 0L
    }

    context(nn: NonNullContext)
    private fun shouldPause(): Boolean {
        if (Scaffold.isEnabled) return true
        if (KillAura.isEnabled && KillAura.target != null) return true
        if (pauseOnEat && PlayerUtils.isEating()) return true
        if (requireStealer && !Stealer.isEnabled) return true
        return disableInHypixelLobby && isInLobby()
    }

    context(nn: NonNullContext)
    private fun hasNearbyPlayer(): Boolean {
        val distance = cancelRange
        if (distance <= 0.0) return false

        val distanceSq = distance * distance
        for (player in nn.level.players()) {
            if (player !== nn.player && player.distanceToSqr(nn.player) <= distanceSq) {
                return true
            }
        }
        return false
    }

    context(nn: NonNullContext)
    private fun findTarget(): Target? {
        val radius = Math.ceil(range).toInt()
        val origin = nn.player.blockPosition()
        val eyes = nn.player.getEyePosition()

        var best: Target? = null
        var bestDistance = Double.MAX_VALUE

        for (pos in BlockPos.betweenClosed(origin.offset(-radius, -radius, -radius), origin.offset(radius, radius, radius))) {
            val immutable = pos.immutable()
            if (!isSelectedContainer(nn.level.getBlockEntity(immutable))) continue
            if (isBlockedChest(immutable)) continue
            if (wasAlreadyOpened(immutable)) continue

            val target = createTarget(immutable, eyes) ?: continue
            val distance = eyes.distanceToSqr(Vec3.atCenterOf(target.pos))
            if (distance < bestDistance) {
                bestDistance = distance
                best = target
            }
        }

        return best
    }

    context(nn: NonNullContext)
    private fun createTarget(pos: BlockPos, eyes: Vec3): Target? {
        val distanceSq = eyes.distanceToSqr(Vec3.atCenterOf(pos))
        val maxRange = range
        if (distanceSq > maxRange * maxRange) return null

        val visibleHit = nn.level.clip(
            ClipContext(
                eyes,
                Vec3.atCenterOf(pos),
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                nn.player,
            )
        )
        if (visibleHit.type == HitResult.Type.BLOCK && visibleHit.blockPos == pos) {
            return Target(pos, visibleHit, false)
        }

        val wallRange = throughWallRange
        if (distanceSq > wallRange * wallRange) return null

        val rotation = RotationUtils.calculate(eyes, Vec3.atCenterOf(pos))
        val throughWallHit = raycastTarget(rotation, pos, true) ?: return null
        return Target(pos, throughWallHit, true)
    }

    context(nn: NonNullContext)
    private fun hitsTarget(rotation: Rot2f, target: Target): Boolean {
        val hit = raycastTarget(rotation, target.pos, target.throughWalls)
        return hit != null && hit.blockPos == target.pos
    }

    context(nn: NonNullContext)
    private fun raycastTarget(rotation: Rot2f, pos: BlockPos, throughWalls: Boolean): BlockHitResult? {
        val start = nn.player.getEyePosition(1.0F)
        val direction = Vec3.directionFromRotation(rotation.pitch, rotation.yaw)
        val end = start.add(direction.scale(range))

        if (!throughWalls) {
            val hit = nn.level.clip(
                ClipContext(
                    start,
                    end,
                    ClipContext.Block.OUTLINE,
                    ClipContext.Fluid.NONE,
                    nn.player,
                )
            )
            return if (hit.type == HitResult.Type.BLOCK) hit else null
        }

        val state: BlockState = nn.level.getBlockState(pos)
        val shape = state.getShape(nn.level, pos)
        return if (shape.isEmpty) null else shape.clip(start, end, pos)
    }

    private fun isSelectedContainer(blockEntity: BlockEntity?): Boolean {
        if (blockEntity is ChestBlockEntity) return chests
        if (blockEntity is BlastFurnaceBlockEntity) return blastFurnaces
        if (blockEntity is SmokerBlockEntity) return smokers
        if (blockEntity is FurnaceBlockEntity) return furnaces
        return blockEntity is BrewingStandBlockEntity && brewingStands
    }

    context(nn: NonNullContext)
    private fun isBlockedChest(pos: BlockPos): Boolean {
        val above = pos.above()
        return nn.level.getBlockEntity(pos) is ChestBlockEntity && nn.level.getBlockState(above).isRedstoneConductor(nn.level, above)
    }

    private fun wasAlreadyOpened(pos: BlockPos): Boolean {
        return if (ignoreOtherOpened) {
            playerClickedContainers.contains(pos)
        } else {
            clickedContainers.contains(pos)
        }
    }

    context(nn: NonNullContext)
    private fun markContainerOpened(pos: BlockPos) {
        if (isSelectedContainer(nn.level.getBlockEntity(pos))) {
            clickedContainers.add(pos.immutable())
        }
    }

    private fun finishOpening() {
        if (!opening) return
        opening = false
        openingStartedAt = 0L
        timer.reset()
    }

    private fun updateTrackedLevel() {
        if (trackedLevel === mc.level) return
        trackedLevel = mc.level
        clickedContainers.clear()
        playerClickedContainers.clear()
        opening = false
        openingStartedAt = 0L
    }

    context(nn: NonNullContext)
    private fun isInLobby(): Boolean {
        for (entity in nn.level.entitiesForRendering()) {
            if (entity.name.string.contains("\u00A7e\u00A7lCLICK TO PLAY")) {
                return true
            }
        }
        return nn.player.inventory.getItem(8).`is`(Items.NETHER_STAR) && nn.player.inventory.getItem(0).`is`(Items.COMPASS)
    }

    private data class Target(
        val pos: BlockPos,
        val hit: BlockHitResult,
        val throughWalls: Boolean,
    )

}
