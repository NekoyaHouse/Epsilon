package com.github.epsilon.modules.impl.player

import com.github.epsilon.events.api.EventPriority
import com.github.epsilon.events.api.safeEventHandler
import com.github.epsilon.events.impl.ClientTickEvent
import com.github.epsilon.events.impl.PacketEvent
import com.github.epsilon.kernel.gameplay.inventory.NavenInvUtils
import com.github.epsilon.kernel.gameplay.inventory.NavenInvUtils.getArmorStack
import com.github.epsilon.kernel.gameplay.inventory.NavenInvUtils.getBestArmorScore
import com.github.epsilon.kernel.gameplay.inventory.NavenInvUtils.getBestAxe
import com.github.epsilon.kernel.gameplay.inventory.NavenInvUtils.getBestBlock
import com.github.epsilon.kernel.gameplay.inventory.NavenInvUtils.getBestCrossbow
import com.github.epsilon.kernel.gameplay.inventory.NavenInvUtils.getBestPickaxe
import com.github.epsilon.kernel.gameplay.inventory.NavenInvUtils.getBestPowerBow
import com.github.epsilon.kernel.gameplay.inventory.NavenInvUtils.getBestProjectile
import com.github.epsilon.kernel.gameplay.inventory.NavenInvUtils.getBestPunchBow
import com.github.epsilon.kernel.gameplay.inventory.NavenInvUtils.getBestShapeAxe
import com.github.epsilon.kernel.gameplay.inventory.NavenInvUtils.getBestShovel
import com.github.epsilon.kernel.gameplay.inventory.NavenInvUtils.getBestSword
import com.github.epsilon.kernel.gameplay.inventory.NavenInvUtils.getBlockCountInInventory
import com.github.epsilon.kernel.gameplay.inventory.NavenInvUtils.getCurrentArmorScore
import com.github.epsilon.kernel.gameplay.inventory.NavenInvUtils.getFishingRod
import com.github.epsilon.kernel.gameplay.inventory.NavenInvUtils.getInventoryStack
import com.github.epsilon.kernel.gameplay.inventory.NavenInvUtils.getItemCount
import com.github.epsilon.kernel.gameplay.inventory.NavenInvUtils.getItemSlot
import com.github.epsilon.kernel.gameplay.inventory.NavenInvUtils.getItemStackSlot
import com.github.epsilon.kernel.gameplay.inventory.NavenInvUtils.getOffhandStack
import com.github.epsilon.kernel.gameplay.inventory.NavenInvUtils.getWorstArrow
import com.github.epsilon.kernel.gameplay.inventory.NavenInvUtils.getWorstBlock
import com.github.epsilon.kernel.gameplay.inventory.NavenInvUtils.getWorstProjectile
import com.github.epsilon.kernel.gameplay.inventory.NavenInvUtils.shouldDisableFeatures
import com.github.epsilon.kernel.gameplay.player.PlayerState.isEating
import com.github.epsilon.kernel.jvm.NonNullContext
import com.github.epsilon.kernel.jvm.runSafe
import com.github.epsilon.kernel.math.MathUtils
import com.github.epsilon.kernel.time.TimerUtils
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.modules.impl.combat.KillAura
import com.github.epsilon.modules.impl.movement.NoSlowdown
import com.github.epsilon.modules.impl.movement.Scaffold
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.network.protocol.game.*
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.*
import com.github.epsilon.kernel.gameplay.inventory.ContainerClicks.click as clickSlot
import com.github.epsilon.kernel.gameplay.inventory.ContainerClicks.dropAll as dropAllInSlot
import com.github.epsilon.kernel.gameplay.inventory.ContainerClicks.shiftClick as shiftClickSlot
import com.github.epsilon.kernel.gameplay.inventory.ContainerClicks.swap as swapSlot

object InvManager : Module("Inv Manager", Category.PLAYER) {

    private enum class Mode {
        Inventory,
        Silent,
    }

    private enum class OffhandItemMode {
        None,
        GoldenApple,
        Projectile,
        FishingRod,
        Block,
    }

    private enum class BowPriorityMode {
        Crossbow,
        PowerBow,
        PunchBow,
    }

    private enum class ActionState {
        IDLE,
        WAITING_FOR_SPRINT_STOP,
        READY_TO_EXECUTE,
        WAITING_FOR_INVENTORY_CLOSE,
    }

    private val mode by enumSetting("Mode", Mode.Silent)
    private val pauseOnEat by boolSetting("Pause On Eat", true)
    private val pauseOnKillAura by boolSetting("Pause On KillAura", true)
    private val pauseOnScaffold by boolSetting("Pause On Scaffold", true)
    private val stopSprinting by boolSetting("Stop Sprinting", true, { mode == Mode.Silent })
    private val minDelay by intSetting("Min Delay", 95, 0..1000, 50)
    private val maxDelay by intSetting("Max Delay", 135, 0..1000, 50)
    private val offhandItem by enumSetting("Offhand Item", OffhandItemMode.None)
    private val autoArmor by boolSetting("Auto Armor", true)
    private val switchSword by boolSetting("Switch Sword", true)
    private val swordSlot by intSetting("Sword Slot", 1, 1..9, 1, { switchSword })
    private val switchBlock by boolSetting("Switch Block", true, { offhandItem != OffhandItemMode.Block })
    private val blockSlot by intSetting(
        "Block Slot",
        2,
        1..9,
        1, { switchBlock && offhandItem != OffhandItemMode.Block })
    val maxBlockSize by intSetting("Max Block Size", 256, 64..512, 64, { switchBlock })
    private val switchPickaxe by boolSetting("Switch Pickaxe", true)
    private val pickaxeSlot by intSetting("Pickaxe Slot", 3, 1..9, 1, { switchPickaxe })
    private val switchAxe by boolSetting("Switch Axe", true)
    private val axeSlot by intSetting("Axe Slot", 4, 1..9, 1, { switchAxe })
    private val switchBow by boolSetting("Switch Bow or Crossbow", true)
    private val bowSlot by intSetting("Bow Slot", 5, 1..9, 1, { switchBow })
    private val preferBow by enumSetting("Bow Priority", BowPriorityMode.Crossbow, { switchBow })
    val maxArrowSize by intSetting("Max Arrow Size", 256, 64..512, 64, { switchBow })
    private val switchWaterBucket by boolSetting("Switch Water Bucket", true)
    private val waterBucketSlot by intSetting("Water Bucket Slot", 6, 1..9, 1, { switchWaterBucket })
    private val switchEnderPearl by boolSetting("Switch Ender Pearl", true)
    private val enderPearlSlot by intSetting("Ender Pearl Slot", 7, 1..9, 1, { switchEnderPearl })
    private val switchFireball by boolSetting("Switch Fireball", true)
    private val fireballSlot by intSetting("Fireball Slot", 8, 1..9, 1, { switchFireball })
    private val switchGoldenApple by boolSetting(
        "Switch Golden Apple",
        true, { offhandItem != OffhandItemMode.GoldenApple })
    private val goldenAppleSlot by intSetting(
        "Golden Apple Slot",
        9,
        1..9,
        1, { switchGoldenApple && offhandItem != OffhandItemMode.GoldenApple })
    private val throwItems by boolSetting("Throw Items", true)
    val waterBucketCount by intSetting("Keep Water Buckets", 1, 0..5, 1, { throwItems })
    val lavaBucketCount by intSetting("Keep Lava Buckets", 1, 0..5, 1, { throwItems })
    val keepProjectile by boolSetting("Keep Eggs & Snowballs", true)
    private val switchProjectile by boolSetting(
        "Switch Eggs & Snowballs",
        false, { keepProjectile && offhandItem != OffhandItemMode.Projectile })
    private val projectileSlot by intSetting(
        "Eggs & Snowballs Slot",
        9,
        1..9,
        1, { switchProjectile && keepProjectile && offhandItem != OffhandItemMode.Projectile })
    val maxProjectileSize by intSetting("Max Eggs & Snowballs Size", 64, 16..256, 16, { keepProjectile })
    private val switchRod by boolSetting(
        "Switch Rod",
        false, { offhandItem != OffhandItemMode.FishingRod })
    private val rodSlot by intSetting(
        "Rod Slot",
        9,
        1..9,
        1, { switchRod && offhandItem != OffhandItemMode.FishingRod })

    private var noMoveTicks = 0
    private var clickOffHand = false
    private var inventoryOpen = false

    private var actionState = ActionState.IDLE
    private var actionConsumed = false
    private var delayMs = 0

    private val timer = TimerUtils()

    init {
        onEnable {
            clickOffHand = false
            inventoryOpen = false
            actionState = ActionState.IDLE
            actionConsumed = false
            delayMs = MathUtils.getRandom(minDelay, maxDelay)
            timer.reset()
        }

        onDisable {
            val closeSilentInventory = inventoryOpen && mode == Mode.Silent
            clickOffHand = false
            inventoryOpen = false
            actionState = ActionState.IDLE
            actionConsumed = false
            delayMs = 0
            if (closeSilentInventory) {
                runSafe {
                    connection.send(ServerboundContainerClosePacket(player.inventoryMenu.containerId))
                }
            }
        }

        safeEventHandler<PacketEvent.Send> { event ->
            val packet = event.packet
            if (packet is ServerboundContainerClosePacket) {
                this@InvManager.inventoryOpen = false
                if (actionState == ActionState.WAITING_FOR_INVENTORY_CLOSE) {
                    actionState = ActionState.IDLE
                }
            }
            if (this@InvManager.inventoryOpen && mode == Mode.Silent) {
                if (
                    packet is ServerboundMovePlayerPacket ||
                    packet is ServerboundUseItemOnPacket ||
                    packet is ServerboundUseItemPacket ||
                    packet is ServerboundInteractPacket ||
                    packet is ServerboundAttackPacket ||
                    packet is ServerboundPlayerActionPacket
                ) {
                    this@InvManager.inventoryOpen = false
                    connection.send(ServerboundContainerClosePacket(player.inventoryMenu.containerId))
                }
            }
        }

        safeEventHandler<ClientTickEvent.Pre> {
            actionConsumed = false

            if (shouldDisableFeatures()) {
                cancelPendingAction()
                return@safeEventHandler
            }
            if (NoSlowdown.isWorking()) {
                cancelPendingAction()
                return@safeEventHandler
            }

            if (player.isMoving) {
                this@InvManager.noMoveTicks = 0
            } else {
                this@InvManager.noMoveTicks++
            }

            if (pauseOnEat && isEating()) {
                cancelPendingAction()
                return@safeEventHandler
            }
            if (pauseOnKillAura && KillAura.isEnabled && KillAura.target != null) {
                cancelPendingAction()
                return@safeEventHandler
            }
            if (pauseOnScaffold && Scaffold.isEnabled) {
                cancelPendingAction()
                return@safeEventHandler
            }

            val allowMove = mode == Mode.Silent
            if (Stealer.isWorking() || (if (mode == Mode.Inventory) mc.gui.screen() !is InventoryScreen else !allowMove && this@InvManager.noMoveTicks <= 1)) {
                this@InvManager.clickOffHand = false
                cancelPendingAction()
                return@safeEventHandler
            }
            val screen = mc.gui.screen()
            if (screen is AbstractContainerScreen<*> && screen.menu.containerId != player.inventoryMenu.containerId) {
                cancelPendingAction()
                return@safeEventHandler
            }

            if (this@InvManager.autoArmor) {
                val armorSlots =
                    arrayOf(EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD)
                for (i in armorSlots.indices) {
                    val stack = getArmorStack(armorSlots[i])
                    if (NavenInvUtils.isArmor(stack)) {
                        if (!stack.isEmpty && canExecuteInventoryAction() && getBestArmorScore(armorSlots[i]) > NavenInvUtils.getProtection(
                                stack
                            )
                        ) {
                            dropAll(4 + (4 - i))
                        }
                    }
                }
                for (ix in 0 until player.inventory.nonEquipmentItems.size) {
                    val stack = getInventoryStack(ix)
                    if (!stack.isEmpty && NavenInvUtils.isArmor(stack)) {
                        val currentItemScore = NavenInvUtils.getProtection(stack)
                        val isBestItem =
                            getBestArmorScore(NavenInvUtils.getArmorSlot(stack)!!) == currentItemScore
                        val isBetterItem =
                            getCurrentArmorScore(NavenInvUtils.getArmorSlot(stack)!!) < currentItemScore
                        if (isBestItem && isBetterItem && canExecuteInventoryAction()) {
                            if (ix < 9) {
                                shiftClick(ix + 36)
                            } else {
                                shiftClick(ix)
                            }
                        }
                    }
                }
            }

            if (this@InvManager.clickOffHand && canExecuteInventoryAction()) {
                if (click(45)) {
                    this@InvManager.clickOffHand = false
                }
            }

            if (this@InvManager.offhandItem == OffhandItemMode.GoldenApple) {
                val offHand = getOffhandStack()
                val offHandItem = offHand.item

                val egapSlot = getItemSlot(Items.ENCHANTED_GOLDEN_APPLE)
                val gapSlot = getItemSlot(Items.GOLDEN_APPLE)

                if (offHandItem == Items.ENCHANTED_GOLDEN_APPLE) {
                    if (offHand.count < offHand.maxStackSize && egapSlot != -1) {
                        if (canExecuteInventoryAction()) {
                            val clicked = if (egapSlot < 9) {
                                click(egapSlot + 36)
                            } else {
                                click(egapSlot)
                            }
                            if (clicked) {
                                this@InvManager.clickOffHand = true
                            }
                        }
                    }
                } else if (offHandItem == Items.GOLDEN_APPLE) {
                    if (egapSlot != -1) {
                        if (canExecuteInventoryAction()) {
                            swapOffHand(egapSlot)
                        }
                    } else if (offHand.count < offHand.maxStackSize && gapSlot != -1) {
                        if (canExecuteInventoryAction()) {
                            val targetSlot = gapSlot
                            val clicked = if (targetSlot < 9) {
                                click(targetSlot + 36)
                            } else {
                                click(targetSlot)
                            }
                            if (clicked) {
                                this@InvManager.clickOffHand = true
                            }
                        }
                    }
                } else {
                    if (egapSlot != -1) {
                        if (canExecuteInventoryAction()) {
                            swapOffHand(egapSlot)
                        }
                    } else if (gapSlot != -1) {
                        if (canExecuteInventoryAction()) {
                            swapOffHand(gapSlot)
                        }
                    }
                }
            } else if (this@InvManager.offhandItem == OffhandItemMode.Projectile) {
                val offHand = getOffhandStack()
                val bestProjectile = getBestProjectile()
                if (bestProjectile != null) {
                    val slot = getItemStackSlot(bestProjectile)
                    val shouldSwap =
                        offHand.item != Items.EGG && offHand.item != Items.SNOWBALL || offHand.count < bestProjectile.count
                    if (shouldSwap && slot != -1 && canExecuteInventoryAction()) swapOffHand(slot)
                }
            } else if (this@InvManager.offhandItem == OffhandItemMode.FishingRod) {
                val offHand = getOffhandStack()
                val slot = getItemSlot(Items.FISHING_ROD)
                if (slot != -1 && canExecuteInventoryAction() && offHand.item != Items.FISHING_ROD) {
                    swapOffHand(slot)
                }
            } else if (this@InvManager.offhandItem == OffhandItemMode.Block) {
                val offHand = getOffhandStack()
                val bestBlock = getBestBlock()
                if (bestBlock != null) {
                    val slot = getItemStackSlot(bestBlock)
                    val shouldSwap = !NavenInvUtils.isValidStack(offHand) || offHand.count < bestBlock.count
                    if (shouldSwap && slot != -1 && canExecuteInventoryAction()) swapOffHand(slot)
                }
            }

            if (this@InvManager.switchGoldenApple && this@InvManager.offhandItem != OffhandItemMode.GoldenApple) {
                val targetSlotIdx = this@InvManager.goldenAppleSlot - 1
                val egapSlot = getItemSlot(Items.ENCHANTED_GOLDEN_APPLE)
                val gapSlot = getItemSlot(Items.GOLDEN_APPLE)
                val bestGapSlot = if (egapSlot != -1) egapSlot else gapSlot
                if (bestGapSlot != -1) {
                    val currentInSlot = getInventoryStack(targetSlotIdx)
                    val bestGapItem = getInventoryStack(bestGapSlot)
                    if (currentInSlot.item != bestGapItem.item || (currentInSlot.count < bestGapItem.count && currentInSlot.item == bestGapItem.item)) {
                        swapItem(targetSlotIdx, bestGapItem)
                    }
                }
            }

            if (this@InvManager.switchBlock) {
                val blockSlotIndex = this@InvManager.blockSlot - 1
                val currentBlock = getInventoryStack(blockSlotIndex)
                val bestBlock = getBestBlock()
                if (bestBlock != null && (bestBlock.count > currentBlock.count || !NavenInvUtils.isValidStack(
                        currentBlock
                    )) && this@InvManager.offhandItem != OffhandItemMode.Block
                ) {
                    swapItem(blockSlotIndex, bestBlock)
                }
                if (getBlockCountInInventory().toFloat() > this@InvManager.maxBlockSize) {
                    throwItem(getWorstBlock())
                }
            }

            if (this@InvManager.switchSword) {
                val slotIndex = this@InvManager.swordSlot - 1
                val currentSword = getInventoryStack(slotIndex)
                var bestSword = getBestSword()
                val bestShapeAxe = getBestShapeAxe()
                if (NavenInvUtils.getAxeDamage(bestShapeAxe) > NavenInvUtils.getSwordDamage(bestSword)) {
                    bestSword = bestShapeAxe
                }
                if (bestSword != null) {
                    val currentDamage =
                        if (NavenInvUtils.isSword(currentSword)) NavenInvUtils.getSwordDamage(currentSword) else NavenInvUtils.getAxeDamage(
                            currentSword
                        )
                    val bestWeaponDamage =
                        if (NavenInvUtils.isSword(bestSword)) NavenInvUtils.getSwordDamage(bestSword) else NavenInvUtils.getAxeDamage(
                            bestSword
                        )
                    if (bestWeaponDamage > currentDamage) swapItem(slotIndex, bestSword)
                }
            }

            if (this@InvManager.switchPickaxe) {
                val slotIndex = this@InvManager.pickaxeSlot - 1
                val bestPickaxe = getBestPickaxe()
                val currentPickaxe = getInventoryStack(slotIndex)
                if (bestPickaxe != null && NavenInvUtils.isPickaxe(bestPickaxe) && (NavenInvUtils.getToolScore(
                        bestPickaxe
                    ) > NavenInvUtils.getToolScore(
                        currentPickaxe
                    ) || !NavenInvUtils.isPickaxe(currentPickaxe))
                ) {
                    swapItem(slotIndex, bestPickaxe)
                }
            }

            if (this@InvManager.switchAxe) {
                val slotIndex = this@InvManager.axeSlot - 1
                val bestAxe = getBestAxe()
                val currentAxe = getInventoryStack(slotIndex)
                if (bestAxe != null && NavenInvUtils.isAxe(bestAxe) && (NavenInvUtils.getToolScore(bestAxe) > NavenInvUtils.getToolScore(
                        currentAxe
                    ) || !NavenInvUtils.isAxe(currentAxe))
                ) {
                    swapItem(slotIndex, bestAxe)
                }
            }

            if (this@InvManager.switchRod && this@InvManager.offhandItem != OffhandItemMode.FishingRod) {
                val slotIndex = this@InvManager.rodSlot - 1
                val bestRod = getFishingRod()
                val currentRod = getInventoryStack(slotIndex)
                if (currentRod.item !is FishingRodItem) swapItem(slotIndex, bestRod)
            }

            if (this@InvManager.switchBow) {
                val slotIndex = this@InvManager.bowSlot - 1
                val currentBow = getInventoryStack(slotIndex)
                var bestBow: ItemStack? = null
                var bestScore = 0.0f
                var currentScore = 0.0f
                when (this@InvManager.preferBow) {
                    BowPriorityMode.Crossbow -> {
                        bestBow = getBestCrossbow()
                        bestScore = NavenInvUtils.getCrossbowScore(bestBow)
                        currentScore = NavenInvUtils.getCrossbowScore(currentBow)
                    }

                    BowPriorityMode.PowerBow -> {
                        bestBow = getBestPowerBow()
                        bestScore = NavenInvUtils.getPowerBowScore(bestBow)
                        currentScore = NavenInvUtils.getPowerBowScore(currentBow)
                    }

                    BowPriorityMode.PunchBow -> {
                        bestBow = getBestPunchBow()
                        bestScore = NavenInvUtils.getPunchBowScore(bestBow)
                        currentScore = NavenInvUtils.getPunchBowScore(currentBow)
                    }
                }
                if (bestBow == null) {
                    bestBow = getBestCrossbow()
                    bestScore = NavenInvUtils.getCrossbowScore(bestBow)
                    currentScore = NavenInvUtils.getCrossbowScore(currentBow)
                }
                if (bestBow == null) {
                    bestBow = getBestPowerBow()
                    bestScore = NavenInvUtils.getPowerBowScore(bestBow)
                    currentScore = NavenInvUtils.getPowerBowScore(currentBow)
                }
                if (bestBow == null) {
                    bestBow = getBestPunchBow()
                    bestScore = NavenInvUtils.getPunchBowScore(bestBow)
                    currentScore = NavenInvUtils.getPunchBowScore(currentBow)
                }
                val finalBow = bestBow
                if (finalBow != null && bestScore > currentScore) swapItem(slotIndex, finalBow)
                if (getItemCount(Items.ARROW).toFloat() > this@InvManager.maxArrowSize) {
                    throwItem(getWorstArrow())
                }
            }

            if (this@InvManager.switchEnderPearl) {
                swapItem(this@InvManager.enderPearlSlot - 1, Items.ENDER_PEARL)
            }
            if (this@InvManager.switchWaterBucket) {
                swapItem(this@InvManager.waterBucketSlot - 1, Items.WATER_BUCKET)
            }
            if (this@InvManager.switchFireball) {
                swapItem(this@InvManager.fireballSlot - 1, Items.FIRE_CHARGE)
            }

            if (this@InvManager.keepProjectile) {
                if ((getItemCount(Items.EGG) + getItemCount(Items.SNOWBALL)).toFloat() > this@InvManager.maxProjectileSize) {
                    throwItem(getWorstProjectile())
                }
                if (this@InvManager.switchProjectile && this@InvManager.offhandItem != OffhandItemMode.Projectile) {
                    val pSlot = this@InvManager.projectileSlot - 1
                    if (getItemCount(Items.EGG) > 0) {
                        swapItem(pSlot, Items.EGG)
                    } else if (getItemCount(Items.SNOWBALL) > 0) {
                        swapItem(pSlot, Items.SNOWBALL)
                    }
                }
            }

            if (this@InvManager.throwItems) {
                val slots = (0 until player.inventory.nonEquipmentItems.size).toMutableList()
                slots.shuffle()
                for (slotIdx in slots) {
                    val stack = getInventoryStack(slotIdx)
                    if (!stack.isEmpty && !isItemUseful(stack) && canExecuteInventoryAction()) {
                        if (throwItem(stack)) {
                            return@safeEventHandler
                        }
                    }
                }
            }

            if (actionState == ActionState.READY_TO_EXECUTE && !actionConsumed) {
                actionState = ActionState.IDLE
            }
        }

        safeEventHandler<ClientTickEvent.Pre>(EventPriority.HIGHEST) {
            if (shouldStopSprinting() && actionState == ActionState.WAITING_FOR_SPRINT_STOP) {
                player.isSprinting = false
                if (!player.wasSprinting) {
                    actionState = ActionState.READY_TO_EXECUTE
                }
            }
        }
    }

    private fun NonNullContext.isItemUseful(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        if (NavenInvUtils.isGodItem(stack)) return true
        if (stack.displayName.string.contains("点击使用")) return true
        if (NavenInvUtils.isArmor(stack)) {
            val protection = NavenInvUtils.getProtection(stack)
            if (getCurrentArmorScore(NavenInvUtils.getArmorSlot(stack)!!) >= protection) return false
            val bestArmor = getBestArmorScore(NavenInvUtils.getArmorSlot(stack)!!)
            return !(protection < bestArmor)
        }
        if (NavenInvUtils.isSword(stack)) return getBestSword() === stack
        if (NavenInvUtils.isPickaxe(stack)) return getBestPickaxe() === stack
        if (NavenInvUtils.isAxe(stack) && !NavenInvUtils.isSharpnessAxe(stack)) {
            return getBestAxe() === stack
        }
        if (NavenInvUtils.isShovel(stack)) return getBestShovel() === stack
        if (stack.item is CrossbowItem) return getBestCrossbow() === stack
        if (stack.item is BowItem && NavenInvUtils.isPunchBow(stack)) {
            return getBestPunchBow() === stack
        }
        if (stack.item is BowItem && NavenInvUtils.isPowerBow(stack)) {
            return getBestPowerBow() === stack
        }
        if (stack.item is BowItem && getItemCount(Items.BOW) > 1) return false
        if (stack.item == Items.WATER_BUCKET && getItemCount(Items.WATER_BUCKET) > waterBucketCount) {
            return false
        }
        if (stack.item == Items.LAVA_BUCKET && getItemCount(Items.LAVA_BUCKET) > lavaBucketCount) {
            return false
        }
        if (stack.item is FishingRodItem && getItemCount(Items.FISHING_ROD) > 1) {
            return false
        }
        if ((stack.item == Items.SNOWBALL || stack.item == Items.EGG) && !keepProjectile) {
            return false
        }
        if (stack.item == Items.GOLDEN_APPLE || stack.item == Items.ENCHANTED_GOLDEN_APPLE) return true
        return stack.item !is StandingAndWallBlockItem && NavenInvUtils.isCommonItemUseful(stack)
    }

    private fun NonNullContext.swapOffHand(slot: Int) {
        if (slot < 9) {
            swap(slot + 36, 40)
        } else {
            swap(slot, 40)
        }
    }

    private fun NonNullContext.throwItem(item: ItemStack?): Boolean {
        if (item != null && NavenInvUtils.isItemValid(item) && canExecuteInventoryAction()) {
            val itemSlot = getItemStackSlot(item)
            if (itemSlot != -1) {
                return if (itemSlot < 9) {
                    dropAll(itemSlot + 36)
                } else {
                    dropAll(itemSlot)
                }
            }
        }
        return false
    }

    private fun NonNullContext.swapItem(targetSlot: Int, bestItem: ItemStack?) {
        val currentSlot = getInventoryStack(targetSlot)
        if (NavenInvUtils.isItemValid(currentSlot) && bestItem !== currentSlot && canExecuteInventoryAction()) {
            val bestItemSlot = getItemStackSlot(bestItem)
            if (bestItemSlot != -1) {
                if (bestItemSlot < 9) {
                    swap(bestItemSlot + 36, targetSlot)
                } else {
                    swap(bestItemSlot, targetSlot)
                }
            }
        }
    }

    private fun NonNullContext.swapItem(targetSlot: Int, item: Item) {
        val currentSlot = getInventoryStack(targetSlot)
        if (NavenInvUtils.isItemValid(currentSlot) && canExecuteInventoryAction()) {
            val bestItemSlot = getItemSlot(item)
            if (bestItemSlot != -1) {
                val bestItemStack = getInventoryStack(bestItemSlot)
                if (currentSlot.item != item || currentSlot.item == item && currentSlot.count < bestItemStack.count) {
                    if (bestItemSlot < 9) {
                        swap(bestItemSlot + 36, targetSlot)
                    } else {
                        swap(bestItemSlot, targetSlot)
                    }
                }
            }
        }
    }

    val isSprintTransitionPending: Boolean
        get() = shouldStopSprinting() && actionState != ActionState.IDLE

    private fun shouldStopSprinting(): Boolean {
        return mode == Mode.Silent && stopSprinting
    }

    fun prepareInventoryAction(): Boolean {
        if (!shouldStopSprinting()) {
            if (actionState == ActionState.WAITING_FOR_SPRINT_STOP) {
                actionState = ActionState.IDLE
            }
            return true
        }
        if (actionState == ActionState.WAITING_FOR_SPRINT_STOP) {
            return false
        }
        if (actionState == ActionState.READY_TO_EXECUTE) {
            return true
        }

        val player = mc.player ?: return true
        if (player.isSprinting || player.wasSprinting) {
            actionState = ActionState.WAITING_FOR_SPRINT_STOP
            player.isSprinting = false
            return false
        }

        return true
    }

    private fun completeInventoryAction() {
        timer.reset()
        delayMs = MathUtils.getRandom(minDelay, maxDelay)
        this.inventoryOpen = true
        actionConsumed = true
        actionState = if (mode == Mode.Silent) ActionState.WAITING_FOR_INVENTORY_CLOSE else ActionState.IDLE
    }

    private fun canExecuteInventoryAction(): Boolean {
        return !actionConsumed && timer.passedMillise(delayMs.toLong())
    }

    private fun cancelPendingAction() {
        if (actionState != ActionState.IDLE && actionState != ActionState.WAITING_FOR_INVENTORY_CLOSE) {
            actionState = ActionState.IDLE
        }
    }

    private fun NonNullContext.click(slot: Int): Boolean {
        if (!prepareInventoryAction()) return false
        clickSlot(slot)
        completeInventoryAction()
        return true
    }

    private fun NonNullContext.swap(slot: Int, hotbarSlot: Int): Boolean {
        if (!prepareInventoryAction()) return false
        swapSlot(slot, hotbarSlot)
        completeInventoryAction()
        return true
    }

    private fun NonNullContext.shiftClick(slot: Int): Boolean {
        if (!prepareInventoryAction()) return false
        shiftClickSlot(slot)
        completeInventoryAction()
        return true
    }

    private fun NonNullContext.dropAll(slot: Int): Boolean {
        if (!prepareInventoryAction()) return false
        dropAllInSlot(slot)
        completeInventoryAction()
        return true
    }

}
