package com.github.epsilon.utils

import com.github.epsilon.Constants
import com.github.epsilon.managers.rotation.RotationManager
import com.github.epsilon.utils.client.KeybindUtils
import com.github.epsilon.utils.combat.DamageUtils
import com.github.epsilon.utils.combat.DamageUtils.ArmorEnchantmentMode
import com.github.epsilon.utils.player.ChatUtils
import com.github.epsilon.utils.player.ClickSlotUtils
import com.github.epsilon.utils.player.EnchantmentUtils
import com.github.epsilon.utils.player.FindItemResult
import com.github.epsilon.utils.player.InvHelper
import com.github.epsilon.utils.player.InvUtils
import com.github.epsilon.utils.player.MoveUtils
import com.github.epsilon.utils.player.PlayerUtils
import com.github.epsilon.utils.rotation.Priority
import com.github.epsilon.utils.rotation.RaytraceUtils
import com.github.epsilon.utils.rotation.Rot2f
import com.github.epsilon.utils.rotation.RotationUtils
import com.github.epsilon.utils.world.BlockUtils
import com.github.epsilon.utils.world.hole.Hole
import com.github.epsilon.utils.world.hole.HoleUtils
import com.github.epsilon.utils.world.hole.HoleType
import it.unimi.dsi.fastutil.objects.Object2IntMap
import net.minecraft.client.KeyMapping
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Holder
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.world.Difficulty
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.enchantment.Enchantment
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.function.Predicate
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.client.player.LocalPlayer

/**
 * 事件处理期间可用的非空客户端上下文。
 *
 * 只有世界、玩家、交互管理器与连接同时存在时才会构造；监听器用 Kotlin 上下文参数（context parameters）
 * 声明这一依赖后，即可在 lambda 内直接使用 [player]、[world] 等非空值，省去逐个 null 检查：
 *
 * ```kotlin
 * EventBus.nonNullHandler<PlayerTickEvent.Post> {
 *     player.jumpFromGround()
 * }
 * ```
 *
 * 上下文按需计算并缓存：只要 `Minecraft` 的对应字段没有变化就复用同一实例，避免每个事件都分配新对象；
 * 世界卸载或返回主菜单后字段变为 null，[current] 随即返回 null，监听器不会执行。
 *
 * [player]、[world] 等是声明在本文件中的上下文属性；在 `com.github.epsilon.utils` 以外的包使用时
 * 需要导入对应属性，或改用 `contextOf<NonNullContext>()` 显式取值。
 */
class NonNullContext private constructor(
    val world: ClientLevel,
    val player: LocalPlayer,
    val interaction: MultiPlayerGameMode,
    val netHandler: ClientPacketListener,
) {

    val mc: Minecraft
        get() = Constants.client

    companion object {

        @Volatile
        private var cached: NonNullContext? = null

        /**
         * 当前可用的非空上下文；世界、玩家、交互管理器或连接任一缺失时为 null。
         */
        val current: NonNullContext?
            get() {
                val mc = Constants.mc ?: return clear()
                val world = mc.level ?: return clear()
                val player = mc.player ?: return clear()
                val interaction = mc.gameMode ?: return clear()
                val netHandler = mc.getConnection() ?: return clear()

                val previous = cached
                if (previous != null
                    && previous.world === world
                    && previous.player === player
                    && previous.interaction === interaction
                    && previous.netHandler === netHandler
                ) {
                    return previous
                }

                return NonNullContext(world, player, interaction, netHandler).also { cached = it }
            }

        private fun clear(): NonNullContext? {
            cached = null
            return null
        }

    }

}

/**
 * 监听器内可直接使用的玩家实例；配合 [NonNullContext] 的上下文参数解析。
 */
context(nn: NonNullContext)
val player: LocalPlayer
    get() = nn.player

/**
 * 监听器内可直接使用的客户端世界实例；配合 [NonNullContext] 的上下文参数解析。
 */
context(nn: NonNullContext)
val world: ClientLevel
    get() = nn.world

/**
 * 监听器内可直接使用的交互管理器实例；配合 [NonNullContext] 的上下文参数解析。
 */
context(nn: NonNullContext)
val interaction: MultiPlayerGameMode
    get() = nn.interaction

/**
 * 监听器内可直接使用的连接实例；配合 [NonNullContext] 的上下文参数解析。
 */
context(nn: NonNullContext)
val netHandler: ClientPacketListener
    get() = nn.netHandler

/**
 * 上下文存在时执行 [block]，否则跳过并返回 null。
 */
inline fun <R> runSafe(crossinline block: context(NonNullContext) () -> R): R? {
    val ctx = NonNullContext.current ?: return null
    return context(ctx) { block() }
}

/**
 * 上下文存在时执行 [block] 并返回其布尔结果，否则返回 false。
 */
inline fun runSafeOrFalse(crossinline block: context(NonNullContext) () -> Boolean): Boolean =
    runSafe(block) ?: false

/**
 * 上下文存在时执行 [block]，否则返回 [defaultValue]。
 */
inline fun <R> runSafeOrElse(defaultValue: R, crossinline block: context(NonNullContext) () -> R): R =
    runSafe(block) ?: defaultValue

// ---- 上下文作用域内的 utils 直接调用入口 ----
// 在 runSafe / nonNullListener / context(nn) 等 NonNullContext 上下文作用域内可直接调用，例如 findInHotbar(...)。

context(nn: NonNullContext)
fun canBeReplacedWith(state: BlockState, item: ItemStack) = BlockUtils.canBeReplacedWith(state, item)

context(nn: NonNullContext)
fun canPlaceAt(pos: BlockPos) = BlockUtils.canPlaceAt(pos)

context(nn: NonNullContext)
fun getVisibleFace(pos: BlockPos) = BlockUtils.getVisibleFace(pos)

context(nn: NonNullContext)
fun hasBlockingEntity(box: AABB) = BlockUtils.hasBlockingEntity(box)

context(nn: NonNullContext)
fun isFaceVisible(pos: BlockPos, face: Direction) = BlockUtils.isFaceVisible(pos, face)

context(nn: NonNullContext)
fun isSolidBlock(pos: BlockPos) = BlockUtils.isSolidBlock(pos)

context(nn: NonNullContext)
fun isWithinRange(point: Vec3) = BlockUtils.isWithinRange(point)

context(nn: NonNullContext)
fun addChatMessage(message: String) = ChatUtils.addChatMessage(message)

context(nn: NonNullContext)
fun addChatMessage(message: Component) = ChatUtils.addChatMessage(message)

context(nn: NonNullContext)
fun addChatMessage(prefix: Boolean, message: String) = ChatUtils.addChatMessage(prefix, message)

context(nn: NonNullContext)
fun addChatMessage(prefix: Boolean, message: Component) = ChatUtils.addChatMessage(prefix, message)

context(nn: NonNullContext)
fun addChatMessage(message: String, hash: Int) = ChatUtils.addChatMessage(message, hash)

context(nn: NonNullContext)
fun addChatMessage(message: Component, hash: Int) = ChatUtils.addChatMessage(message, hash)

context(nn: NonNullContext)
fun addChatMessage(prefix: Boolean, message: String, hash: Int) = ChatUtils.addChatMessage(prefix, message, hash)

context(nn: NonNullContext)
fun addChatMessage(prefix: Boolean, message: Component, hash: Int) = ChatUtils.addChatMessage(prefix, message, hash)

context(nn: NonNullContext)
fun click(containerId: Int, slot: Int) = ClickSlotUtils.click(containerId, slot)

context(nn: NonNullContext)
fun click(slot: Int) = ClickSlotUtils.click(slot)

context(nn: NonNullContext)
fun dropAll(containerId: Int, slot: Int) = ClickSlotUtils.dropAll(containerId, slot)

context(nn: NonNullContext)
fun dropAll(slot: Int) = ClickSlotUtils.dropAll(slot)

context(nn: NonNullContext)
fun shiftClick(containerId: Int, slot: Int) = ClickSlotUtils.shiftClick(containerId, slot)

context(nn: NonNullContext)
fun shiftClick(slot: Int) = ClickSlotUtils.shiftClick(slot)

context(nn: NonNullContext)
fun swap(containerId: Int, slot: Int, hotbarSlot: Int) = ClickSlotUtils.swap(containerId, slot, hotbarSlot)

context(nn: NonNullContext)
fun swap(slot: Int, hotbarSlot: Int) = ClickSlotUtils.swap(slot, hotbarSlot)

context(nn: NonNullContext)
fun anchorDamage(target: LivingEntity, anchorPos: Vec3, mode: ArmorEnchantmentMode) = DamageUtils.anchorDamage(target, anchorPos, mode)

context(nn: NonNullContext)
fun breakCrosshairCrystal() = DamageUtils.breakCrosshairCrystal()

context(nn: NonNullContext)
fun getEnchantmentLevel(itemStack: ItemStack, enchantment: ResourceKey<Enchantment>) = EnchantmentUtils.getEnchantmentLevel(itemStack, enchantment)

context(nn: NonNullContext)
fun getEnchantmentLevel(itemEnchantments: Object2IntMap<Holder<Enchantment>>, enchantment: ResourceKey<Enchantment>) = EnchantmentUtils.getEnchantmentLevel(itemEnchantments, enchantment)

context(nn: NonNullContext)
fun hasEnchantment(itemStack: ItemStack, enchantmentKey: ResourceKey<Enchantment>) = EnchantmentUtils.hasEnchantment(itemStack, enchantmentKey)

context(nn: NonNullContext)
fun getHole(pos: BlockPos) = HoleUtils.getHole(pos)

context(nn: NonNullContext)
fun getHole(pos: BlockPos, depth: Int) = HoleUtils.getHole(pos, depth)

context(nn: NonNullContext)
fun getHole(pos: BlockPos, depth: Int, floor: Boolean) = HoleUtils.getHole(pos, depth, floor)

context(nn: NonNullContext)
fun getHole(pos: BlockPos, single: Boolean, doubles: Boolean, quad: Boolean, depth: Int, floor: Boolean) = HoleUtils.getHole(pos, single, doubles, quad, depth, floor)

context(nn: NonNullContext)
fun inHole(player: Player) = HoleUtils.inHole(player)

context(nn: NonNullContext)
fun isBlock(pos: BlockPos) = HoleUtils.isBlock(pos)

context(nn: NonNullContext)
fun getArmorSlot(stack: ItemStack?) = InvHelper.getArmorSlot(stack)

context(nn: NonNullContext)
fun getArmorStack(slot: EquipmentSlot) = InvHelper.getArmorStack(slot)

context(nn: NonNullContext)
fun getAxeDamage(stack: ItemStack?) = InvHelper.getAxeDamage(stack)

context(nn: NonNullContext)
fun getBestArmorScore(slot: EquipmentSlot) = InvHelper.getBestArmorScore(slot)

context(nn: NonNullContext)
fun getBestAxe() = InvHelper.getBestAxe()

context(nn: NonNullContext)
fun getBestAxeScore() = InvHelper.getBestAxeScore()

context(nn: NonNullContext)
fun getBestBlock() = InvHelper.getBestBlock()

context(nn: NonNullContext)
fun getBestCrossbow() = InvHelper.getBestCrossbow()

context(nn: NonNullContext)
fun getBestCrossbowScore() = InvHelper.getBestCrossbowScore()

context(nn: NonNullContext)
fun getBestPickaxe() = InvHelper.getBestPickaxe()

context(nn: NonNullContext)
fun getBestPickaxeScore() = InvHelper.getBestPickaxeScore()

context(nn: NonNullContext)
fun getBestPowerBow() = InvHelper.getBestPowerBow()

context(nn: NonNullContext)
fun getBestPowerBowScore() = InvHelper.getBestPowerBowScore()

context(nn: NonNullContext)
fun getBestProjectile() = InvHelper.getBestProjectile()

context(nn: NonNullContext)
fun getBestPunchBow() = InvHelper.getBestPunchBow()

context(nn: NonNullContext)
fun getBestPunchBowScore() = InvHelper.getBestPunchBowScore()

context(nn: NonNullContext)
fun getBestShapeAxe() = InvHelper.getBestShapeAxe()

context(nn: NonNullContext)
fun getBestShovel() = InvHelper.getBestShovel()

context(nn: NonNullContext)
fun getBestShovelScore() = InvHelper.getBestShovelScore()

context(nn: NonNullContext)
fun getBestSword() = InvHelper.getBestSword()

context(nn: NonNullContext)
fun getBestSwordDamage() = InvHelper.getBestSwordDamage()

context(nn: NonNullContext)
fun getBlockCountInInventory() = InvHelper.getBlockCountInInventory()

context(nn: NonNullContext)
fun getCrossbowScore(stack: ItemStack?) = InvHelper.getCrossbowScore(stack)

context(nn: NonNullContext)
fun getCurrentArmorScore(slot: EquipmentSlot) = InvHelper.getCurrentArmorScore(slot)

context(nn: NonNullContext)
fun getFishingRod() = InvHelper.getFishingRod()

context(nn: NonNullContext)
fun getInventoryStack(slot: Int) = InvHelper.getInventoryStack(slot)

context(nn: NonNullContext)
fun getItemCount(item: Item) = InvHelper.getItemCount(item)

context(nn: NonNullContext)
fun getItemSlot(item: Item) = InvHelper.getItemSlot(item)

context(nn: NonNullContext)
fun getItemStackSlot(stack: ItemStack?) = InvHelper.getItemStackSlot(stack)

context(nn: NonNullContext)
fun getOffhandStack() = InvHelper.getOffhandStack()

context(nn: NonNullContext)
fun getPowerBowScore(stack: ItemStack?) = InvHelper.getPowerBowScore(stack)

context(nn: NonNullContext)
fun getProtection(stack: ItemStack?) = InvHelper.getProtection(stack)

context(nn: NonNullContext)
fun getPunchBowScore(stack: ItemStack?) = InvHelper.getPunchBowScore(stack)

context(nn: NonNullContext)
fun getSwordDamage(stack: ItemStack?) = InvHelper.getSwordDamage(stack)

context(nn: NonNullContext)
fun getToolScore(stack: ItemStack?) = InvHelper.getToolScore(stack)

context(nn: NonNullContext)
fun getWorstArrow() = InvHelper.getWorstArrow()

context(nn: NonNullContext)
fun getWorstBlock() = InvHelper.getWorstBlock()

context(nn: NonNullContext)
fun getWorstProjectile() = InvHelper.getWorstProjectile()

context(nn: NonNullContext)
fun hasItem(item: Item) = InvHelper.hasItem(item)

context(nn: NonNullContext)
fun isArmor(stack: ItemStack?) = InvHelper.isArmor(stack)

context(nn: NonNullContext)
fun isAxe(stack: ItemStack?) = InvHelper.isAxe(stack)

context(nn: NonNullContext)
fun isCommonItemUseful(stack: ItemStack) = InvHelper.isCommonItemUseful(stack)

context(nn: NonNullContext)
fun isGodItem(stack: ItemStack) = InvHelper.isGodItem(stack)

context(nn: NonNullContext)
fun isItemValid(stack: ItemStack) = InvHelper.isItemValid(stack)

context(nn: NonNullContext)
fun isPickaxe(stack: ItemStack?) = InvHelper.isPickaxe(stack)

context(nn: NonNullContext)
fun isPowerBow(stack: ItemStack) = InvHelper.isPowerBow(stack)

context(nn: NonNullContext)
fun isPunchBow(stack: ItemStack) = InvHelper.isPunchBow(stack)

context(nn: NonNullContext)
fun isSharpnessAxe(stack: ItemStack) = InvHelper.isSharpnessAxe(stack)

context(nn: NonNullContext)
fun isShovel(stack: ItemStack?) = InvHelper.isShovel(stack)

context(nn: NonNullContext)
fun isSword(stack: ItemStack?) = InvHelper.isSword(stack)

context(nn: NonNullContext)
fun isValidStack(stack: ItemStack?) = InvHelper.isValidStack(stack)

context(nn: NonNullContext)
fun shouldDisableFeatures() = InvHelper.shouldDisableFeatures()

context(nn: NonNullContext)
fun find(vararg items: Item) = InvUtils.find(*items)

context(nn: NonNullContext)
fun find(isGood: Predicate<ItemStack>) = InvUtils.find(isGood)

context(nn: NonNullContext)
fun find(isGood: Predicate<ItemStack>, start: Int, end: Int) = InvUtils.find(isGood, start, end)

context(nn: NonNullContext)
fun findInHotbar(vararg items: Item) = InvUtils.findInHotbar(*items)

context(nn: NonNullContext)
fun findInHotbar(isGood: Predicate<ItemStack>) = InvUtils.findInHotbar(isGood)

context(nn: NonNullContext)
fun invSwap(slot: Int) = InvUtils.invSwap(slot)

context(nn: NonNullContext)
fun invSwapBack() = InvUtils.invSwapBack()

context(nn: NonNullContext)
fun swap(slot: Int, saveSwap: Boolean) = InvUtils.swap(slot, saveSwap)

context(nn: NonNullContext)
fun swapBack() = InvUtils.swapBack()

context(nn: NonNullContext)
fun encodeMouseButton(button: Int) = KeybindUtils.encodeMouseButton(button)

context(nn: NonNullContext)
fun getKey(keyMapping: KeyMapping) = KeybindUtils.getKey(keyMapping)

context(nn: NonNullContext)
fun isPressed(keyMapping: KeyMapping) = KeybindUtils.isPressed(keyMapping)

context(nn: NonNullContext)
fun isPressed(keyBind: Int) = KeybindUtils.isPressed(keyBind)

context(nn: NonNullContext)
fun forward(speed: Double) = MoveUtils.forward(speed)

context(nn: NonNullContext)
fun forwardWithoutStrafe(speed: Double) = MoveUtils.forwardWithoutStrafe(speed)

context(nn: NonNullContext)
fun isEating() = PlayerUtils.isEating()

context(nn: NonNullContext)
fun isInBlock() = PlayerUtils.isInBlock()

context(nn: NonNullContext)
fun isInWeb() = PlayerUtils.isInWeb()

context(nn: NonNullContext)
fun swingHand(hand: InteractionHand) = PlayerUtils.swingHand(hand)

context(nn: NonNullContext)
fun canSeePointFrom(eyes: Vec3, vec3: Vec3) = RaytraceUtils.canSeePointFrom(eyes, vec3)

context(nn: NonNullContext)
fun overBlock(rotation: Rot2f, dir: Direction, pos: BlockPos, strict: Boolean) = RaytraceUtils.overBlock(rotation, dir, pos, strict)

context(nn: NonNullContext)
fun overBlock(rotation: Rot2f, pos: BlockPos, strict: Boolean) = RaytraceUtils.overBlock(rotation, pos, strict)

context(nn: NonNullContext)
fun overBlock(rotation: Rot2f, pos: BlockPos) = RaytraceUtils.overBlock(rotation, pos)

context(nn: NonNullContext)
fun overBlock(rotation: Rot2f, pos: BlockPos, enumFacing: Direction) = RaytraceUtils.overBlock(rotation, pos, enumFacing)

context(nn: NonNullContext)
fun raytrace(rotation: Rot2f, range: Double) = RaytraceUtils.raytrace(rotation, range)

context(nn: NonNullContext)
fun raytrace(rotation: Rot2f, range: Double, expand: Float) = RaytraceUtils.raytrace(rotation, range, expand)

context(nn: NonNullContext)
fun raytrace(rotation: Rot2f, range: Double, expand: Float, entity: Entity?) = RaytraceUtils.raytrace(rotation, range, expand, entity)

context(nn: NonNullContext)
fun applySensitivityPatch(rotation: Rot2f) = RotationUtils.applySensitivityPatch(rotation)

context(nn: NonNullContext)
fun applySensitivityPatch(rotation: Rot2f, previousRotation: Rot2f) = RotationUtils.applySensitivityPatch(rotation, previousRotation)

context(nn: NonNullContext)
fun calculate(from: Vec3, to: Vec3) = RotationUtils.calculate(from, to)

context(nn: NonNullContext)
fun calculate(entity: Entity) = RotationUtils.calculate(entity)

context(nn: NonNullContext)
fun calculate(entity: Entity, adaptive: Boolean, range: Double) = RotationUtils.calculate(entity, adaptive, range)

context(nn: NonNullContext)
fun calculate(to: BlockPos) = RotationUtils.calculate(to)

context(nn: NonNullContext)
fun calculate(to: Vec3) = RotationUtils.calculate(to)

context(nn: NonNullContext)
fun calculate(position: Vec3, direction: Direction) = RotationUtils.calculate(position, direction)

context(nn: NonNullContext)
fun calculate(position: BlockPos, direction: Direction) = RotationUtils.calculate(position, direction)

context(nn: NonNullContext)
fun calculate(vec: Vec3, predict: Boolean) = RotationUtils.calculate(vec, predict)

context(nn: NonNullContext)
fun canSee(pos: BlockPos, side: Direction) = RotationUtils.canSee(pos, side)

context(nn: NonNullContext)
fun getDirection(pos: BlockPos) = RotationUtils.getDirection(pos)

context(nn: NonNullContext)
fun getEyeDistanceToEntity(entity: LivingEntity) = RotationUtils.getEyeDistanceToEntity(entity)

context(nn: NonNullContext)
fun getRotationsToEntity(entity: Entity) = RotationUtils.getRotationsToEntity(entity)

