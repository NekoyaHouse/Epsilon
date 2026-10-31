package com.github.epsilon.modules.impl.player

import com.github.epsilon.events.api.nonNullHandler
import com.github.epsilon.events.impl.ClientTickEvent
import com.github.epsilon.gui.dropdown.DropdownScreen
import com.github.epsilon.gui.hudeditor.HudEditorScreen
import com.github.epsilon.gui.panel.PanelScreen
import com.github.epsilon.gui.troll.TrollGuiScreen
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.modules.impl.movement.elytrafly.ElytraFly
import com.github.epsilon.utils.player.ClickSlotUtils
import com.github.epsilon.utils.player.EnchantmentUtils
import com.github.epsilon.utils.player.InvUtils
import com.github.epsilon.kernel.jvm.NonNullContext
import com.github.epsilon.utils.safety.connection
import com.github.epsilon.utils.safety.interaction
import com.github.epsilon.utils.safety.player
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.item.equipment.Equippable
import kotlin.math.ceil

object AutoArmor : Module("Auto Armor", Category.PLAYER) {

    private enum class ElytraPriority {
        None,
        Always,
        ElytraPlus,
        Ignore,
    }

    private enum class EnchantPriority {
        Blast,
        Protection,
    }

    private val head by enumSetting("Head", EnchantPriority.Protection)
    private val body by enumSetting("Body", EnchantPriority.Protection)
    private val tights by enumSetting("Tights", EnchantPriority.Protection)
    private val feet by enumSetting("Feet", EnchantPriority.Protection)
    private val elytraPriority by enumSetting("Elytra Priority", ElytraPriority.Ignore)
    private val delay by intSetting("Delay", 5, 0..10, 1)
    private val oldVersion by boolSetting("Old Version", false)
    private val pauseInventory by boolSetting("Pause Inventory", false)
    private val noMove by boolSetting("No Move", false)
    private val ignoreCurse by boolSetting("Ignore Curse", true)
    private val strict by boolSetting("Strict", false)

    private var tickDelay = 0

    private val armorList: List<ArmorData> = listOf(
        ArmorData(EquipmentSlot.FEET, 36),
        ArmorData(EquipmentSlot.LEGS, 37),
        ArmorData(EquipmentSlot.CHEST, 38),
        ArmorData(EquipmentSlot.HEAD, 39),
    )

    init {
        nonNullHandler<ClientTickEvent.Pre> {
            val screen = mc.gui.screen()
            if (screen != null &&
                pauseInventory &&
                screen !is ChatScreen &&
                screen !is PanelScreen &&
                screen !is DropdownScreen &&
                screen !is TrollGuiScreen &&
                screen !is HudEditorScreen
            ) {
                return@nonNullHandler
            }

            if (tickDelay-- > 0) return@nonNullHandler

            armorList.forEach { it.reset() }

            for (slot in 0 until 36) {
                val stack = player.inventory.getItem(slot)
                val protection = getProtection(stack)
                if (protection <= 0) continue

                val equipmentSlot = getEquipmentSlot(stack) ?: continue

                for (armorData in armorList) {
                    if (armorData.equipmentSlot == equipmentSlot &&
                        protection > armorData.prevProtection &&
                        protection > armorData.newProtection
                    ) {
                        armorData.newSlot = slot
                        armorData.newProtection = protection
                    }
                }
            }

            for (armorPiece in armorList) {
                val slot = armorPiece.newSlot
                if (slot == -1) continue

                if ((armorPiece.prevProtection == -1 || !oldVersion) && slot < 9) {
                    InvUtils.swap(slot, true)
                    try {
                        interaction.useItem(player, InteractionHand.MAIN_HAND)
                    } finally {
                        InvUtils.swapBack()
                    }
                } else {
                    if (player.isMoving && noMove) return@nonNullHandler

                    val inventorySlot = if (slot < 9) 36 + slot else slot
                    val armorSlot = 8 - armorPiece.equipmentSlot.index

                    if (strict) {
                        connection.send(
                            ServerboundPlayerCommandPacket(
                                player,
                                ServerboundPlayerCommandPacket.Action.STOP_SPRINTING
                            )
                        )
                    }

                    ClickSlotUtils.click(inventorySlot)
                    ClickSlotUtils.click(armorSlot)
                    if (armorPiece.prevProtection != -1) {
                        ClickSlotUtils.click(inventorySlot)
                    }

                    connection.send(ServerboundContainerClosePacket(player.inventoryMenu.containerId))
                }

                tickDelay = delay
                return@nonNullHandler
            }
        }
    }

    private fun getEquipmentSlot(stack: ItemStack): EquipmentSlot? {
        if (stack.isEmpty) return null

        val equippable: Equippable? = stack.get(DataComponents.EQUIPPABLE)
        if (equippable == null || equippable.slot().type != EquipmentSlot.Type.HUMANOID_ARMOR) {
            return null
        }

        return equippable.slot()
    }

    private fun getProtection(stack: ItemStack): Int {
        if (stack.isEmpty) return -1

        val slot = getEquipmentSlot(stack) ?: return 0
        val player = NonNullContext.current?.player ?: return 0

        val elytra = stack.`is`(Items.ELYTRA)
        var enchantmentScore = 0
        var blastMultiplier = 1
        var protectionMultiplier = 1

        if (elytra) {
            if (!LivingEntity.canGlideUsing(stack, slot)) return 0

            val elytraFlyActive = elytraPriority == ElytraPriority.ElytraPlus &&
                    ElytraFly.isEnabled &&
                    !ElytraFly.isArmorMode
            val preserveEquippedElytra = elytraPriority == ElytraPriority.Ignore &&
                    player.getItemBySlot(EquipmentSlot.CHEST).`is`(Items.ELYTRA)

            if (elytraFlyActive || preserveEquippedElytra || elytraPriority == ElytraPriority.Always) {
                enchantmentScore = 999
            }
        }

        when (slot) {
            EquipmentSlot.HEAD -> {
                if (head == EnchantPriority.Protection) {
                    protectionMultiplier = 2
                } else {
                    blastMultiplier = 2
                }
            }

            EquipmentSlot.CHEST -> {
                if (body == EnchantPriority.Protection) {
                    protectionMultiplier = 2
                } else {
                    blastMultiplier = 2
                }
            }

            EquipmentSlot.LEGS -> {
                if (tights == EnchantPriority.Protection) {
                    protectionMultiplier = 2
                } else {
                    blastMultiplier = 2
                }
            }

            EquipmentSlot.FEET -> {
                if (feet == EnchantPriority.Protection) {
                    protectionMultiplier = 2
                } else {
                    blastMultiplier = 2
                }
            }

            else -> {
                return 0
            }
        }

        enchantmentScore += EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.PROTECTION) * protectionMultiplier
        enchantmentScore += EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.BLAST_PROTECTION) * blastMultiplier

        if (ignoreCurse && EnchantmentUtils.hasEnchantment(stack, Enchantments.BINDING_CURSE)) {
            return -999
        }

        var armor = 0.0
        var toughness = 0.0
        stack.forEachModifier(slot) { attribute, modifier ->
            if (attribute == Attributes.ARMOR) {
                armor += modifier.amount()
            } else if (attribute == Attributes.ARMOR_TOUGHNESS) {
                toughness += modifier.amount()
            }
        }

        if (!elytra && armor <= 0.0 && toughness <= 0.0) return 0

        return ((armor + ceil(toughness)) * 10.0).toInt() + enchantmentScore
    }

    private class ArmorData(
        val equipmentSlot: EquipmentSlot,
        val inventorySlot: Int,
    ) {

        var prevProtection = 0
        var newSlot = -1
        var newProtection = -1

        fun reset() {
            val player = NonNullContext.current?.player
            prevProtection = if (player == null) -1 else getProtection(player.inventory.getItem(inventorySlot))
            newSlot = -1
            newProtection = -1
        }

    }

}
