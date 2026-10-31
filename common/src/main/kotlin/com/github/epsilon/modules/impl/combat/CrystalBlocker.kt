package com.github.epsilon.modules.impl.combat

import com.github.epsilon.events.api.safeEventHandler
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.kernel.jvm.NonNullContext
import com.github.epsilon.kernel.jvm.gameMode
import com.github.epsilon.kernel.jvm.player
import com.github.epsilon.managers.RotationManager
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.utils.player.FindItemResult
import com.github.epsilon.utils.player.InvUtils
import com.github.epsilon.utils.player.SwingMode
import com.github.epsilon.utils.rotation.Priority
import com.github.epsilon.utils.rotation.RotationUtils
import com.github.epsilon.utils.world.BlockUtils
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.item.Items
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.abs

object CrystalBlocker : Module("Crystal Blocker", Category.COMBAT) {

    private val range by doubleSetting("Range", 4.0, 1.0..6.0, 0.1)
    private val rotate by enumSetting("Rotate", RotateMode.Silent)
    private val switchMode by enumSetting("Switch", SwitchMode.Visible)
    private val delay by intSetting("Delay", 2, 0..20, 1)
    private val visibleSwapBackDelay by intSetting(
        "Swap Back Delay", 0, 0..20, 1, { switchMode == SwitchMode.Visible })
    private val rotationSpeed by intSetting(
        "Rotation Speed", 180, 10..180, 10, { rotate == RotateMode.Silent })

    private var timer = 0
    private var waitingSwapBack = false
    private var swapBackTicks = 0
    private var savedOldSlot = -1

    enum class RotateMode {
        None,
        Normal,
        Silent,
    }

    enum class SwitchMode {
        Visible,
        Silent,
    }

    init {
        safeEventHandler<PlayerTickEvent.Pre> {
            if (!player.onGround()) return@safeEventHandler

            if (waitingSwapBack) {
                if (swapBackTicks > 0) {
                    swapBackTicks--
                    if (swapBackTicks > 0) return@safeEventHandler
                }
                if (savedOldSlot >= 0) {
                    player.inventory.selectedSlot = savedOldSlot
                }
                waitingSwapBack = false
                savedOldSlot = -1
                return@safeEventHandler
            }

            if (timer > 0) {
                timer--
            }

            // 1. Find crystals within range
            val self = player
            val playerY = self.y
            val targetCrystal = level.getEntitiesOfClass(
                EndCrystal::class.java,
                self.boundingBox.inflate(range),
                { crystal -> abs(crystal.y - playerY) < 1.0 }
            ).minByOrNull { crystal -> self.distanceTo(crystal) }

            if (targetCrystal == null) return@safeEventHandler

            // 2.1 Check if already blocked by obsidian
            val eyes: Vec3 = self.eyePosition
            val towards: Vec3 = targetCrystal.position().add(0.0, 0.5, 0.0)
            val hit = level.clip(
                ClipContext(
                    eyes,
                    towards,
                    ClipContext.Block.OUTLINE,
                    ClipContext.Fluid.NONE,
                    self
                )
            )
            if (hit.type == HitResult.Type.BLOCK) {
                val hitPos = hit.blockPos
                if (level.getBlockState(hitPos).`is`(Blocks.OBSIDIAN)) return@safeEventHandler
            }

            // 2. Calculate placement position
            val crystalPos: Vec3 = targetCrystal.position()
            val playerPos: Vec3 = self.position()

            val midPoint = playerPos.lerp(crystalPos, 0.5)
            var placePos = BlockPos.containing(midPoint.x, self.y, midPoint.z)

            if (placePos == self.blockPosition() || placePos == targetCrystal.blockPosition()) {
                val dir = crystalPos.subtract(playerPos).normalize()
                placePos = BlockPos.containing(playerPos.x + dir.x, self.y, playerPos.z + dir.z)
            }

            // 3. Check if placeable
            if (!BlockUtils.canPlaceAt(placePos)) return@safeEventHandler
            if (!level.getBlockState(placePos.below()).isSolid) return@safeEventHandler

            // 4. Find obsidian
            val obsidian: FindItemResult = InvUtils.findInHotbar(Items.OBSIDIAN)
            if (!obsidian.found()) return@safeEventHandler

            // 5. Rotation and placement
            val rot = RotationUtils.calculate(self.eyePosition, Vec3.atCenterOf(placePos))
            var readyToPlace = true

            if (rotate == RotateMode.Normal) {
                self.yRot = rot.yaw
                self.xRot = rot.pitch.coerceIn(-90.0f, 90.0f)
            } else if (rotate == RotateMode.Silent) {
                RotationManager.setRotations(rot, rotationSpeed.toDouble(), Priority.Highest)

                if (RotationManager.rotations != null) {
                    val yawDiff = abs(Mth.wrapDegrees(RotationManager.getYaw() - rot.yaw).toDouble())
                    val pitchDiff = abs((RotationManager.getPitch() - rot.pitch).toDouble())
                    if (yawDiff > 15 || pitchDiff > 15) {
                        readyToPlace = false
                    }
                }
            }

            if (readyToPlace && timer <= 0) {
                placeBlock(placePos, obsidian)
                timer = delay
            }
        }
    }

    context(nn: NonNullContext)
    private fun placeBlock(pos: BlockPos, item: FindItemResult) {
        val oldSlot = player.inventory.selectedSlot

        val side: Direction = RotationUtils.getDirection(pos)
        val bhr = BlockHitResult(Vec3.atCenterOf(pos), side, pos, false)

        if (switchMode == SwitchMode.Visible) {
            val target = item.slot
            val needSwitch = oldSlot != target
            if (needSwitch) {
                InvUtils.swap(target, true)
            }

            gameMode.useItemOn(player, InteractionHand.MAIN_HAND, bhr)
            SwingMode.Both.interactSwing(InteractionHand.MAIN_HAND)

            if (needSwitch) {
                val backDelay = visibleSwapBackDelay
                if (backDelay > 0) {
                    waitingSwapBack = true
                    swapBackTicks = backDelay
                    savedOldSlot = oldSlot
                } else {
                    InvUtils.swapBack()
                }
            }
        } else {
            InvUtils.invSwap(item.slot)
            gameMode.useItemOn(player, InteractionHand.MAIN_HAND, bhr)
            SwingMode.Both.interactSwing(InteractionHand.MAIN_HAND)
            InvUtils.invSwapBack()
        }
    }

}
