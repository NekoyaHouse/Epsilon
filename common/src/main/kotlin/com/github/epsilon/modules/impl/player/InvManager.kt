package com.github.epsilon.modules.impl.player

import com.github.epsilon.utils.*
import com.github.epsilon.events.bus.EventPriority
import com.github.epsilon.events.impl.ClientTickEvent
import com.github.epsilon.events.impl.PacketEvent
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.modules.impl.combat.KillAura
import com.github.epsilon.modules.impl.movement.NoSlowdown
import com.github.epsilon.modules.impl.movement.Scaffold
import com.github.epsilon.settings.Setting
import com.github.epsilon.settings.getValue
import com.github.epsilon.utils.math.MathUtils
import com.github.epsilon.utils.netHandler
import com.github.epsilon.utils.player
import com.github.epsilon.utils.player.ClickSlotUtils
import com.github.epsilon.utils.player.InvHelper
import com.github.epsilon.utils.player.PlayerUtils
import com.github.epsilon.utils.timer.TimerUtils
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.network.protocol.game.ServerboundAttackPacket
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket
import net.minecraft.network.protocol.game.ServerboundInteractPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.BowItem
import net.minecraft.world.item.CrossbowItem
import net.minecraft.world.item.FishingRodItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.StandingAndWallBlockItem

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
    private val stopSprinting by boolSetting("Stop Sprinting", true, Setting.Dependency { mode = = Mode.Silent })
    private val minDelay by intSetting("Min Delay", 95, 0, 1000, 50)
    private val maxDelay by intSetting("Max Delay", 135, 0, 1000, 50)
    private val offhandItem by enumSetting("Offhand Item", OffhandItemMode.None)
    private val autoArmor by boolSetting("Auto Armor", true)
    private val switchSword by boolSetting("Switch Sword", true)
    private val swordSlot by intSetting("Sword Slot", 1, 1, 9, 1, Setting.Dependency { switchSword })
    private val switchBlock by boolSetting("Switch Block", true, Setting.Dependency { offhandItem != OffhandItemMode.Block })
    private val blockSlot by intSetting("Block Slot", 2, 1, 9, 1, Setting.Dependency { switchBlock && offhandItem != OffhandItemMode.Block })
    val maxBlockSize by intSetting("Max Block Size", 256, 64, 512, 64, Setting.Dependency { switchBlock })
    private val switchPickaxe by boolSetting("Switch Pickaxe", true)
    private val pickaxeSlot by intSetting("Pickaxe Slot", 3, 1, 9, 1, Setting.Dependency { switchPickaxe })
    private val switchAxe by boolSetting("Switch Axe", true)
    private val axeSlot by intSetting("Axe Slot", 4, 1, 9, 1, Setting.Dependency { switchAxe })
    private val switchBow by boolSetting("Switch Bow or Crossbow", true)
    private val bowSlot by intSetting("Bow Slot", 5, 1, 9, 1, Setting.Dependency { switchBow })
    private val preferBow by enumSetting("Bow Priority", BowPriorityMode.Crossbow, Setting.Dependency { switchBow })
    val maxArrowSize by intSetting("Max Arrow Size", 256, 64, 512, 64, Setting.Dependency { switchBow })
    private val switchWaterBucket by boolSetting("Switch Water Bucket", true)
    private val waterBucketSlot by intSetting("Water Bucket Slot", 6, 1, 9, 1, Setting.Dependency { switchWaterBucket })
    private val switchEnderPearl by boolSetting("Switch Ender Pearl", true)
    private val enderPearlSlot by intSetting("Ender Pearl Slot", 7, 1, 9, 1, Setting.Dependency { switchEnderPearl })
    private val switchFireball by boolSetting("Switch Fireball", true)
    private val fireballSlot by intSetting("Fireball Slot", 8, 1, 9, 1, Setting.Dependency { switchFireball })
    private val switchGoldenApple by boolSetting("Switch Golden Apple", true, Setting.Dependency { offhandItem != OffhandItemMode.GoldenApple })
    private val goldenAppleSlot by intSetting("Golden Apple Slot", 9, 1, 9, 1, Setting.Dependency { switchGoldenApple && offhandItem != OffhandItemMode.GoldenApple })
    private val throwItems by boolSetting("Throw Items", true)
    val waterBucketCount by intSetting("Keep Water Buckets", 1, 0, 5, 1, Setting.Dependency { throwItems })
    val lavaBucketCount by intSetting("Keep Lava Buckets", 1, 0, 5, 1, Setting.Dependency { throwItems })
    val keepProjectile by boolSetting("Keep Eggs & Snowballs", true)
    private val switchProjectile by boolSetting("Switch Eggs & Snowballs", false, Setting.Dependency { keepProjectile && offhandItem != OffhandItemMode.Projectile })
    private val projectileSlot by intSetting("Eggs & Snowballs Slot", 9, 1, 9, 1, Setting.Dependency { switchProjectile && keepProjectile && offhandItem != OffhandItemMode.Projectile })
    val maxProjectileSize by intSetting("Max Eggs & Snowballs Size", 64, 16, 256, 16, Setting.Dependency { keepProjectile })
    private val switchRod by boolSetting("Switch Rod", false, Setting.Dependency { offhandItem != OffhandItemMode.FishingRod })
    private val rodSlot by intSetting("Rod Slot", 9, 1, 9, 1, Setting.Dependency { switchRod && offhandItem != OffhandItemMode.FishingRod })

    private var noMoveTicks = 0
    private var clickOffHand = false
    private var inventoryOpen = false

    private var actionState = ActionState.IDLE
    private var actionConsumed = false
    private var delayMs = 0

    private val timer = TimerUtils()

    init {
        nonNullListener<PacketEvent.Send> { event ->
            val packet = event.packet
            if (packet is ServerboundContainerClosePacket) {
                this.inventoryOpen = false
                if (actionState == ActionState.WAITING_FOR_INVENTORY_CLOSE) {
                    actionState = ActionState.IDLE
                }
            }
            if (this.inventoryOpen && mode = = Mode.Silent) {
                if (
                    packet is ServerboundMovePlayerPacket ||
                    packet is ServerboundUseItemOnPacket ||
                    packet is ServerboundUseItemPacket ||
                    packet is ServerboundInteractPacket ||
                    packet is ServerboundAttackPacket ||
                    packet is ServerboundPlayerActionPacket
                ) {
                    this.inventoryOpen = false
                    netHandler.send(ServerboundContainerClosePacket(player.inventoryMenu.containerId))
                }
            }
        }

        nonNullListener<ClientTickEvent.Pre> {
            actionConsumed = false

            if (shouldDisableFeatures()) {
                cancelPendingAction()
                return@nonNullListener
            }
            if (NoSlowdown.isWorking()) {
                cancelPendingAction()
                return@nonNullListener
            }

            if (player.isMoving()) {
                this.noMoveTicks = 0
            } else {
                this.noMoveTicks++
            }

            if (pauseOnEat && isEating()) {
                cancelPendingAction()
                return@nonNullListener
            }
            if (pauseOnKillAura && KillAura.isEnabled && KillAura.target != null) {
                cancelPendingAction()
                return@nonNullListener
            }
            if (pauseOnScaffold && Scaffold.isEnabled) {
                cancelPendingAction()
                return@nonNullListener
            }

            val allowMove = mode = = Mode.Silent
            if (Stealer.isWorking() || (if (mode = = Mode.Inventory) mc.gui.screen() !is InventoryScreen else !allowMove && this.noMoveTicks <= 1)) {
                this.clickOffHand = false
                cancelPendingAction()
                return@nonNullListener
            }
            val screen = mc.gui.screen()
            if (screen is AbstractContainerScreen<*> && screen.menu.containerId != player.inventoryMenu.containerId) {
                cancelPendingAction()
                return@nonNullListener
            }

            if (this.autoArmor.value) {
                val armorSlots = arrayOf(EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD)
                for (i in armorSlots.indices) {
                    val stack = getArmorStack(armorSlots[i])
                    if (isArmor(stack)) {
                        if (!stack.isEmpty && canExecuteInventoryAction() && getBestArmorScore(armorSlots[i]) > getProtection(stack)) {
                            dropAll(4 + (4 - i))
                        }
                    }
                }
                for (ix in 0 until player.inventory.nonEquipmentItems.size) {
                    val stack = getInventoryStack(ix)
                    if (!stack.isEmpty && isArmor(stack)) {
                        val currentItemScore = getProtection(stack)
                        val isBestItem = getBestArmorScore(getArmorSlot(stack)!!) == currentItemScore
                        val isBetterItem = getCurrentArmorScore(getArmorSlot(stack)!!) < currentItemScore
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

            if (this.clickOffHand && canExecuteInventoryAction()) {
                if (click(45)) {
                    this.clickOffHand = false
                }
            }

            if (this.offhandItem.value == OffhandItemMode.GoldenApple) {
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
                                this.clickOffHand = true
                            }
                        }
                    }
                } else if (offHandItem == Items.GOLDEN_APPLE) {
                    if (egapSlot != -1) {
                        if (canExecuteInventoryAction()) {
                            this.swapOffHand(egapSlot)
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
                                this.clickOffHand = true
                            }
                        }
                    }
                } else {
                    if (egapSlot != -1) {
                        if (canExecuteInventoryAction()) {
                            this.swapOffHand(egapSlot)
                        }
                    } else if (gapSlot != -1) {
                        if (canExecuteInventoryAction()) {
                            this.swapOffHand(gapSlot)
                        }
                    }
                }
            } else if (this.offhandItem.value == OffhandItemMode.Projectile) {
                val offHand = getOffhandStack()
                val bestProjectile = getBestProjectile()
                if (bestProjectile != null) {
                    val slot = getItemStackSlot(bestProjectile)
                    val shouldSwap = offHand.item != Items.EGG && offHand.item != Items.SNOWBALL || offHand.count < bestProjectile.count
                    if (shouldSwap && slot != -1 && canExecuteInventoryAction()) this.swapOffHand(slot)
                }
            } else if (this.offhandItem.value == OffhandItemMode.FishingRod) {
                val offHand = getOffhandStack()
                val slot = getItemSlot(Items.FISHING_ROD)
                if (slot != -1 && canExecuteInventoryAction() && offHand.item != Items.FISHING_ROD) {
                    this.swapOffHand(slot)
                }
            } else if (this.offhandItem.value == OffhandItemMode.Block) {
                val offHand = getOffhandStack()
                val bestBlock = getBestBlock()
                if (bestBlock != null) {
                    val slot = getItemStackSlot(bestBlock)
                    val shouldSwap = !isValidStack(offHand) || offHand.count < bestBlock.count
                    if (shouldSwap && slot != -1 && canExecuteInventoryAction()) this.swapOffHand(slot)
                }
            }

            if (this.switchGoldenApple.value && this.offhandItem.value != OffhandItemMode.GoldenApple) {
                val targetSlotIdx = this.goldenAppleSlot.value - 1
                val egapSlot = getItemSlot(Items.ENCHANTED_GOLDEN_APPLE)
                val gapSlot = getItemSlot(Items.GOLDEN_APPLE)
                val bestGapSlot = if (egapSlot != -1) egapSlot else gapSlot
                if (bestGapSlot != -1) {
                    val currentInSlot = getInventoryStack(targetSlotIdx)
                    val bestGapItem = getInventoryStack(bestGapSlot)
                    if (currentInSlot.item != bestGapItem.item || (currentInSlot.count < bestGapItem.count && currentInSlot.item == bestGapItem.item)) {
                        this.swapItem(targetSlotIdx, bestGapItem)
                    }
                }
            }

            if (this.switchBlock.value) {
                val blockSlotIndex = this.blockSlot.value - 1
                val currentBlock = getInventoryStack(blockSlotIndex)
                val bestBlock = getBestBlock()
                if (bestBlock != null && (bestBlock.count > currentBlock.count || !isValidStack(currentBlock)) && this.offhandItem.value != OffhandItemMode.Block) {
                    this.swapItem(blockSlotIndex, bestBlock)
                }
                if (getBlockCountInInventory().toFloat() > this.maxBlockSize.value) {
                    this.throwItem(getWorstBlock())
                }
            }

            if (this.switchSword.value) {
                val slotIndex = this.swordSlot.value - 1
                val currentSword = getInventoryStack(slotIndex)
                var bestSword = getBestSword()
                val bestShapeAxe = getBestShapeAxe()
                if (getAxeDamage(bestShapeAxe) > getSwordDamage(bestSword)) {
                    bestSword = bestShapeAxe
                }
                if (bestSword != null) {
                    val currentDamage = if (isSword(currentSword)) getSwordDamage(currentSword) else getAxeDamage(currentSword)
                    val bestWeaponDamage = if (isSword(bestSword)) getSwordDamage(bestSword) else getAxeDamage(bestSword)
                    if (bestWeaponDamage > currentDamage) this.swapItem(slotIndex, bestSword)
                }
            }

            if (this.switchPickaxe.value) {
                val slotIndex = this.pickaxeSlot.value - 1
                val bestPickaxe = getBestPickaxe()
                val currentPickaxe = getInventoryStack(slotIndex)
                if (bestPickaxe != null && isPickaxe(bestPickaxe) && (getToolScore(bestPickaxe) > InvHelper.getToolScore(currentPickaxe) || !InvHelper.isPickaxe(currentPickaxe))) {
                    this.swapItem(slotIndex, bestPickaxe)
                }
            }

            if (this.switchAxe.value) {
                val slotIndex = this.axeSlot.value - 1
                val bestAxe = getBestAxe()
                val currentAxe = getInventoryStack(slotIndex)
                if (bestAxe != null && isAxe(bestAxe) && (getToolScore(bestAxe) > InvHelper.getToolScore(currentAxe) || !InvHelper.isAxe(currentAxe))) {
                    this.swapItem(slotIndex, bestAxe)
                }
            }

            if (this.switchRod.value && this.offhandItem.value != OffhandItemMode.FishingRod) {
                val slotIndex = this.rodSlot.value - 1
                val bestRod = getFishingRod()
                val currentRod = getInventoryStack(slotIndex)
                if (currentRod.item !is FishingRodItem) this.swapItem(slotIndex, bestRod)
            }

            if (this.switchBow.value) {
                val slotIndex = this.bowSlot.value - 1
                val currentBow = getInventoryStack(slotIndex)
                var bestBow: ItemStack? = null
                var bestScore = 0.0f
                var currentScore = 0.0f
                when (this.preferBow.value) {
                    BowPriorityMode.Crossbow -> {
                        bestBow = getBestCrossbow()
                        bestScore = getCrossbowScore(bestBow)
                        currentScore = getCrossbowScore(currentBow)
                    }

                    BowPriorityMode.PowerBow -> {
                        bestBow = getBestPowerBow()
                        bestScore = getPowerBowScore(bestBow)
                        currentScore = getPowerBowScore(currentBow)
                    }

                    BowPriorityMode.PunchBow -> {
                        bestBow = getBestPunchBow()
                        bestScore = getPunchBowScore(bestBow)
                        currentScore = getPunchBowScore(currentBow)
                    }
                }
                if (bestBow == null) {
                    bestBow = getBestCrossbow()
                    bestScore = getCrossbowScore(bestBow)
                    currentScore = getCrossbowScore(currentBow)
                }
                if (bestBow == null) {
                    bestBow = getBestPowerBow()
                    bestScore = getPowerBowScore(bestBow)
                    currentScore = getPowerBowScore(currentBow)
                }
                if (bestBow == null) {
                    bestBow = getBestPunchBow()
                    bestScore = getPunchBowScore(bestBow)
                    currentScore = getPunchBowScore(currentBow)
                }
                val finalBow = bestBow
                if (finalBow != null && bestScore > currentScore) this.swapItem(slotIndex, finalBow)
                if (getItemCount(Items.ARROW).toFloat() > this.maxArrowSize.value) {
                    this.throwItem(getWorstArrow())
                }
            }

            if (this.switchEnderPearl.value) {
                this.swapItem(this.enderPearlSlot.value - 1, Items.ENDER_PEARL)
            }
            if (this.switchWaterBucket.value) {
                this.swapItem(this.waterBucketSlot.value - 1, Items.WATER_BUCKET)
            }
            if (this.switchFireball.value) {
                this.swapItem(this.fireballSlot.value - 1, Items.FIRE_CHARGE)
            }

            if (this.keepProjectile.value) {
                if ((getItemCount(Items.EGG) + InvHelper.getItemCount(Items.SNOWBALL)).toFloat() > this.maxProjectileSize.value) {
                    this.throwItem(getWorstProjectile())
                }
                if (this.switchProjectile.value && this.offhandItem.value != OffhandItemMode.Projectile) {
                    val pSlot = this.projectileSlot.value - 1
                    if (getItemCount(Items.EGG) > 0) {
                        this.swapItem(pSlot, Items.EGG)
                    } else if (getItemCount(Items.SNOWBALL) > 0) {
                        this.swapItem(pSlot, Items.SNOWBALL)
                    }
                }
            }

            if (this.throwItems.value) {
                val slots = (0 until player.inventory.nonEquipmentItems.size).toMutableList()
                slots.shuffle()
                for (slotIdx in slots) {
                    val stack = getInventoryStack(slotIdx)
                    if (!stack.isEmpty && !this.isItemUseful(stack) && canExecuteInventoryAction()) {
                        if (this.throwItem(stack)) {
                            return@nonNullListener
                        }
                    }
                }
            }

            if (actionState == ActionState.READY_TO_EXECUTE && !actionConsumed) {
                actionState = ActionState.IDLE
            }
        }

        nonNullListener<ClientTickEvent.Pre>(EventPriority.HIGHEST) {
            if (shouldStopSprinting() && actionState == ActionState.WAITING_FOR_SPRINT_STOP) {
                player.setSprinting(false)
                if (!player.wasSprinting) {
                    actionState = ActionState.READY_TO_EXECUTE
                }
            }
        }
    }

    override fun onEnable() {
        clickOffHand = false
        inventoryOpen = false
        actionState = ActionState.IDLE
        actionConsumed = false
        delayMs = MathUtils.getRandom(minDelay, maxDelay)
        timer.reset()
    }

    override fun onDisable() {
        val connection = mc.getConnection()
        val player = mc.player
        val closeSilentInventory = inventoryOpen && mode = = Mode.Silent && connection != null && player != null
        clickOffHand = false
        inventoryOpen = false
        actionState = ActionState.IDLE
        actionConsumed = false
        delayMs = 0
        if (closeSilentInventory) {
            connection!!.send(ServerboundContainerClosePacket(player!!.inventoryMenu.containerId))
        }
    }

    private fun isItemUseful(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        if (InvHelper.isGodItem(stack)) return true
        if (stack.displayName.string.contains("点击使用")) return true
        if (InvHelper.isArmor(stack)) {
            val protection = InvHelper.getProtection(stack)
            if (InvHelper.getCurrentArmorScore(InvHelper.getArmorSlot(stack)!!) >= protection) return false
            val bestArmor = InvHelper.getBestArmorScore(InvHelper.getArmorSlot(stack)!!)
            return !(protection < bestArmor)
        }
        if (InvHelper.isSword(stack)) return InvHelper.getBestSword() === stack
        if (InvHelper.isPickaxe(stack)) return InvHelper.getBestPickaxe() === stack
        if (InvHelper.isAxe(stack) && !InvHelper.isSharpnessAxe(stack)) {
            return InvHelper.getBestAxe() === stack
        }
        if (InvHelper.isShovel(stack)) return InvHelper.getBestShovel() === stack
        if (stack.item is CrossbowItem) return InvHelper.getBestCrossbow() === stack
        if (stack.item is BowItem && InvHelper.isPunchBow(stack)) {
            return InvHelper.getBestPunchBow() === stack
        }
        if (stack.item is BowItem && InvHelper.isPowerBow(stack)) {
            return InvHelper.getBestPowerBow() === stack
        }
        if (stack.item is BowItem && InvHelper.getItemCount(Items.BOW) > 1) return false
        if (stack.item == Items.WATER_BUCKET && InvHelper.getItemCount(Items.WATER_BUCKET) > waterBucketCount) {
            return false
        }
        if (stack.item == Items.LAVA_BUCKET && InvHelper.getItemCount(Items.LAVA_BUCKET) > lavaBucketCount) {
            return false
        }
        if (stack.item is FishingRodItem && InvHelper.getItemCount(Items.FISHING_ROD) > 1) {
            return false
        }
        if ((stack.item == Items.SNOWBALL || stack.item == Items.EGG) && !keepProjectile) {
            return false
        }
        if (stack.item == Items.GOLDEN_APPLE || stack.item == Items.ENCHANTED_GOLDEN_APPLE) return true
        return stack.item !is StandingAndWallBlockItem && InvHelper.isCommonItemUseful(stack)
    }

    private fun swapOffHand(slot: Int) {
        if (slot < 9) {
            swap(slot + 36, 40)
        } else {
            swap(slot, 40)
        }
    }

    private fun throwItem(item: ItemStack?): Boolean {
        if (item != null && InvHelper.isItemValid(item) && canExecuteInventoryAction()) {
            val itemSlot = InvHelper.getItemStackSlot(item)
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

    private fun swapItem(targetSlot: Int, bestItem: ItemStack?) {
        val currentSlot = InvHelper.getInventoryStack(targetSlot)
        if (InvHelper.isItemValid(currentSlot) && bestItem !== currentSlot && canExecuteInventoryAction()) {
            val bestItemSlot = InvHelper.getItemStackSlot(bestItem)
            if (bestItemSlot != -1) {
                if (bestItemSlot < 9) {
                    swap(bestItemSlot + 36, targetSlot)
                } else {
                    swap(bestItemSlot, targetSlot)
                }
            }
        }
    }

    private fun swapItem(targetSlot: Int, item: Item) {
        val currentSlot = InvHelper.getInventoryStack(targetSlot)
        if (InvHelper.isItemValid(currentSlot) && canExecuteInventoryAction()) {
            val bestItemSlot = InvHelper.getItemSlot(item)
            if (bestItemSlot != -1) {
                val bestItemStack = InvHelper.getInventoryStack(bestItemSlot)
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
        return mode = = Mode.Silent && stopSprinting
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
            player.setSprinting(false)
            return false
        }

        return true
    }

    private fun completeInventoryAction() {
        timer.reset()
        delayMs = MathUtils.getRandom(minDelay, maxDelay)
        this.inventoryOpen = true
        actionConsumed = true
        actionState = if (mode = = Mode.Silent) ActionState.WAITING_FOR_INVENTORY_CLOSE else ActionState.IDLE
    }

    private fun canExecuteInventoryAction(): Boolean {
        return !actionConsumed && timer.passedMillise(delayMs.toLong())
    }

    private fun cancelPendingAction() {
        if (actionState != ActionState.IDLE && actionState != ActionState.WAITING_FOR_INVENTORY_CLOSE) {
            actionState = ActionState.IDLE
        }
    }

    private fun click(slot: Int): Boolean {
        if (!prepareInventoryAction()) return false
        ClickSlotUtils.click(slot)
        completeInventoryAction()
        return true
    }

    private fun swap(slot: Int, hotbarSlot: Int): Boolean {
        if (!prepareInventoryAction()) return false
        ClickSlotUtils.swap(slot, hotbarSlot)
        completeInventoryAction()
        return true
    }

    private fun shiftClick(slot: Int): Boolean {
        if (!prepareInventoryAction()) return false
        ClickSlotUtils.shiftClick(slot)
        completeInventoryAction()
        return true
    }

    private fun dropAll(slot: Int): Boolean {
        if (!prepareInventoryAction()) return false
        ClickSlotUtils.dropAll(slot)
        completeInventoryAction()
        return true
    }

}
