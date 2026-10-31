package com.github.epsilon.utils.player

import com.github.epsilon.utils.safety.NonNullContext
import com.github.epsilon.utils.safety.player
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.*
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.level.block.*

/**
 * 背包辅助逻辑（InvManager 等模块）使用的物品评分与查找工具类。
 *
 * 纯物品评分方法（isSword / getProtection 等）不依赖上下文，可直接调用。
 */
object InvHelper {

    val blacklistedBlocks: List<Block> = listOf(
        Blocks.AIR,
        Blocks.WATER,
        Blocks.LAVA,
        Blocks.ENCHANTING_TABLE,
        Blocks.GLASS_PANE,
        Blocks.GLASS_PANE,
        Blocks.IRON_BARS,
        Blocks.SNOW,
        Blocks.COAL_ORE,
        Blocks.DIAMOND_ORE,
        Blocks.EMERALD_ORE,
        Blocks.CHEST,
        Blocks.TRAPPED_CHEST,
        Blocks.TORCH,
        Blocks.ANVIL,
        Blocks.TRAPPED_CHEST,
        Blocks.NOTE_BLOCK,
        Blocks.JUKEBOX,
        Blocks.TNT,
        Blocks.GOLD_ORE,
        Blocks.IRON_ORE,
        Blocks.LAPIS_ORE,
        Blocks.STONE_PRESSURE_PLATE,
        Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE,
        Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE,
        Blocks.STONE_BUTTON,
        Blocks.LEVER,
        Blocks.TALL_GRASS,
        Blocks.TRIPWIRE,
        Blocks.TRIPWIRE_HOOK,
        Blocks.RAIL,
        Blocks.CORNFLOWER,
        Blocks.RED_MUSHROOM,
        Blocks.BROWN_MUSHROOM,
        Blocks.VINE,
        Blocks.SUNFLOWER,
        Blocks.LADDER,
        Blocks.FURNACE,
        Blocks.SAND,
        Blocks.CACTUS,
        Blocks.DISPENSER,
        Blocks.DROPPER,
        Blocks.CRAFTING_TABLE,
        Blocks.COBWEB,
        Blocks.PUMPKIN,
        Blocks.COBBLESTONE_WALL,
        Blocks.OAK_FENCE,
        Blocks.REDSTONE_TORCH,
        Blocks.FLOWER_POT,
    )

    private val SWORDS: Set<Item> = setOf(
        Items.WOODEN_SWORD,
        Items.STONE_SWORD,
        Items.COPPER_SWORD,
        Items.IRON_SWORD,
        Items.GOLDEN_SWORD,
        Items.DIAMOND_SWORD,
        Items.NETHERITE_SWORD,
    )

    private val PICKAXES: Set<Item> = setOf(
        Items.WOODEN_PICKAXE,
        Items.STONE_PICKAXE,
        Items.COPPER_PICKAXE,
        Items.IRON_PICKAXE,
        Items.GOLDEN_PICKAXE,
        Items.DIAMOND_PICKAXE,
        Items.NETHERITE_PICKAXE,
    )

    private val AXES: Set<Item> = setOf(
        Items.WOODEN_AXE,
        Items.STONE_AXE,
        Items.COPPER_AXE,
        Items.IRON_AXE,
        Items.GOLDEN_AXE,
        Items.DIAMOND_AXE,
        Items.NETHERITE_AXE,
    )

    private val SHOVELS: Set<Item> = setOf(
        Items.WOODEN_SHOVEL,
        Items.STONE_SHOVEL,
        Items.COPPER_SHOVEL,
        Items.IRON_SHOVEL,
        Items.GOLDEN_SHOVEL,
        Items.DIAMOND_SHOVEL,
        Items.NETHERITE_SHOVEL,
    )

    /**
     * 判断当前界面是否应禁用背包辅助功能。
     */
    context(nn: NonNullContext)
    fun shouldDisableFeatures(): Boolean {
        return getAllItems().any { item ->
            if (item.isEmpty) {
                return@any false
            }

            val name = item.displayName.string
            name.contains("Click") ||
                    name.contains("点击") ||
                    name.contains("Right") ||
                    name.contains("Teleport") ||
                    name.contains("离开游戏") ||
                    name.contains("选择") ||
                    name.contains("再来")
        }
    }

    /**
     * 判断物品是否为带自定义名称的金头。
     */
    fun isGoldenHead(stack: ItemStack): Boolean {
        if (stack.isEmpty) {
            return false
        }

        val item = stack.item
        return item is BlockItem && item.block is SkullBlock
    }

    /**
     * 判断物品是否可装备到护甲槽位。
     */
    fun isArmor(stack: ItemStack?): Boolean {
        return getArmorSlot(stack) != null
    }

    /**
     * 获取物品对应的护甲装备槽位。
     */
    fun getArmorSlot(stack: ItemStack?): EquipmentSlot? {
        if (stack == null || stack.isEmpty) {
            return null
        }

        val equippable = stack.get(DataComponents.EQUIPPABLE) ?: return null
        val slot = equippable.slot()
        return if (slot.isArmor && slot != EquipmentSlot.BODY) slot else null
    }

    /**
     * 判断物品是否为剑。
     */
    fun isSword(stack: ItemStack?): Boolean {
        return stack != null && !stack.isEmpty && SWORDS.contains(stack.item)
    }

    /**
     * 判断物品是否为镐。
     */
    fun isPickaxe(stack: ItemStack?): Boolean {
        return stack != null && !stack.isEmpty && PICKAXES.contains(stack.item)
    }

    /**
     * 判断物品是否为斧。
     *
     * 26.3 移除了 `AxeItem`，工具类型改为由 `Item.Properties#axe` 写入数据组件，
     * 因此这里与剑、镐保持一致，使用固定物品集合判定。
     */
    fun isAxe(stack: ItemStack?): Boolean {
        return stack != null && !stack.isEmpty && AXES.contains(stack.item)
    }

    /**
     * 判断物品是否为锹。
     *
     * 26.3 与斧一样移除了 `ShovelItem`，因此使用固定物品集合判定。
     */
    fun isShovel(stack: ItemStack?): Boolean {
        return stack != null && !stack.isEmpty && SHOVELS.contains(stack.item)
    }

    /**
     * 获取指定普通背包槽位中的物品堆。
     */
    context(nn: NonNullContext)
    fun getInventoryStack(slot: Int): ItemStack {
        val items = player.inventory.nonEquipmentItems

        if (slot < 0 || slot >= items.size) {
            return ItemStack.EMPTY
        }

        return items[slot]
    }

    /**
     * 获取指定护甲槽位中的物品堆。
     */
    context(nn: NonNullContext)
    fun getArmorStack(slot: EquipmentSlot): ItemStack {
        return player.inventory.getItem(slot.getIndex(36))
    }

    /**
     * 获取副手槽位中的物品堆。
     */
    context(nn: NonNullContext)
    fun getOffhandStack(): ItemStack {
        return player.inventory.getItem(Inventory.SLOT_OFFHAND)
    }

    /**
     * 判断物品是否为高等级锋利斧。
     */
    fun isSharpnessAxe(stack: ItemStack): Boolean {
        return !stack.isEmpty &&
                isAxe(stack) &&
                EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.SHARPNESS) >= 8 &&
                EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.SHARPNESS) < 50
    }

    /**
     * 判断物品是否为特殊高伤害金斧。
     */
    fun isGodAxe(stack: ItemStack): Boolean {
        return !stack.isEmpty &&
                stack.item === Items.GOLDEN_AXE &&
                EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.SHARPNESS) > 100
    }

    /**
     * 判断物品是否为附魔金苹果。
     */
    fun isEnchantedGApple(stack: ItemStack): Boolean {
        return !stack.isEmpty && stack.item === Items.ENCHANTED_GOLDEN_APPLE
    }

    /**
     * 判断物品是否为末影水晶。
     */
    fun isEndCrystal(stack: ItemStack): Boolean {
        return !stack.isEmpty && stack.item === Items.END_CRYSTAL
    }

    /**
     * 判断物品是否为高击退黏液球。
     */
    fun isKBBall(stack: ItemStack): Boolean {
        return !stack.isEmpty &&
                stack.item === Items.SLIME_BALL &&
                EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.KNOCKBACK) > 1
    }

    /**
     * 判断物品是否为高击退木棍。
     */
    fun isKBStick(stack: ItemStack): Boolean {
        return !stack.isEmpty &&
                stack.item === Items.STICK &&
                EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.KNOCKBACK) > 1
    }

    /**
     * 查找普通背包区域中的空槽位。
     */
    context(nn: NonNullContext)
    fun findEmptyInventory(): Int {
        val size = player.inventory.nonEquipmentItems.size

        for (i in 9 until size) {
            if (getInventoryStack(i).isEmpty) {
                return i
            }
        }

        return -1
    }

    /**
     * 查找快捷栏中的空槽位。
     */
    context(nn: NonNullContext)
    fun findEmptySlot(): Int {
        for (i in 0 until 9) {
            if (getInventoryStack(i).isEmpty) {
                return i
            }
        }

        return -1
    }

    /**
     * 在快捷栏中查找指定物品。
     *
     * @return 匹配槽位；未找到时返回 null
     */
    context(nn: NonNullContext)
    fun findItemHotbar(item: Item): Int? {
        for (i in 0 until 9) {
            if (getInventoryStack(i).item === item) {
                return i
            }
        }

        return null
    }

    /**
     * 获取弓的冲击附魔等级。
     */
    fun getPunchLevel(stack: ItemStack): Int {
        return EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.PUNCH)
    }

    /**
     * 获取弓的力量附魔等级。
     */
    fun getPowerLevel(stack: ItemStack): Int {
        return EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.POWER)
    }

    /**
     * 获取玩家背包中的全部物品堆。
     */
    context(nn: NonNullContext)
    fun getAllItems(): List<ItemStack> {
        val list = ArrayList<ItemStack>()
        val inventory = player.inventory

        for (i in 0 until inventory.containerSize) {
            list.add(inventory.getItem(i))
        }

        return list
    }

    /**
     * 获取指定护甲槽位可用物品的最高评分。
     */
    context(nn: NonNullContext)
    fun getBestArmorScore(slot: EquipmentSlot): Float {
        return getAllItems()
            .filter { item -> !item.isEmpty && getArmorSlot(item) == slot }
            .map { item -> getProtection(item) }
            .maxOrNull() ?: 0.0F
    }

    /**
     * 获取指定护甲槽位当前装备的评分。
     */
    context(nn: NonNullContext)
    fun getCurrentArmorScore(slot: EquipmentSlot): Float {
        return getProtection(getArmorStack(slot))
    }

    /**
     * 获取背包内剑的最高伤害评分。
     */
    context(nn: NonNullContext)
    fun getBestSwordDamage(): Float {
        return getAllItems()
            .filter { item -> isSword(item) }
            .map { item -> getSwordDamage(item) }
            .maxOrNull() ?: 0.0F
    }

    /**
     * 获取背包内伤害评分最高的剑。
     *
     * @return 评分最高的剑；不存在时返回 null
     */
    context(nn: NonNullContext)
    fun getBestSword(): ItemStack? {
        return getAllItems()
            .filter { item -> isSword(item) }
            .maxByOrNull { item -> (getSwordDamage(item) * 100.0F).toInt() }
    }

    /**
     * 按对象引用查找物品堆所在的背包槽位。
     */
    context(nn: NonNullContext)
    fun getItemStackSlot(stack: ItemStack?): Int {
        if (stack == null) {
            return -1
        }
        val inventory = player.inventory

        for (i in 0 until inventory.containerSize) {
            if (inventory.getItem(i) === stack) {
                return i
            }
        }

        return -1
    }

    /**
     * 判断物品是否适合背包辅助逻辑处理。
     */
    fun isItemValid(stack: ItemStack): Boolean {
        if (stack.isEmpty) {
            return true
        }

        if (stack.item is PlayerHeadItem) {
            return false
        }

        val name = stack.displayName.string
        return !name.contains("Click") &&
                !name.contains("Right") &&
                !name.contains("点击") &&
                !name.contains("Teleport") &&
                !name.contains("使用") &&
                !name.contains("传送") &&
                !name.contains("再来")
    }

    /**
     * 在普通背包槽位中查找指定物品。
     */
    context(nn: NonNullContext)
    fun getItemSlot(item: Item): Int {
        val size = player.inventory.nonEquipmentItems.size

        for (i in 0 until size) {
            if (getInventoryStack(i).item === item) {
                return i
            }
        }

        return -1
    }

    /**
     * 获取数量最多的可用投掷物。
     */
    context(nn: NonNullContext)
    fun getBestProjectile(): ItemStack? {
        return getAllItems()
            .filter { item ->
                !item.isEmpty && (item.item === Items.EGG || item.item === Items.SNOWBALL) && isItemValid(
                    item
                )
            }
            .maxByOrNull { item -> item.count }
    }

    /**
     * 获取任意可用钓鱼竿。
     */
    context(nn: NonNullContext)
    fun getFishingRod(): ItemStack? {
        return getAllItems()
            .firstOrNull { item -> !item.isEmpty && item.item is FishingRodItem && isItemValid(item) }
    }

    /**
     * 统计背包内可放置方块的总数。
     */
    context(nn: NonNullContext)
    fun getBlockCountInInventory(): Int {
        return getAllItems()
            .filter { item -> !item.isEmpty && item.item is BlockItem && isValidStack(item) && isItemValid(item) }
            .sumOf { item -> item.count }
    }

    /**
     * 获取数量最少的投掷物堆。
     */
    context(nn: NonNullContext)
    fun getWorstProjectile(): ItemStack? {
        return getAllItems()
            .filter { item -> !item.isEmpty && (item.item === Items.EGG || item.item === Items.SNOWBALL) }
            .minByOrNull { item -> item.count }
    }

    /**
     * 获取数量最少的可用箭矢堆。
     */
    context(nn: NonNullContext)
    fun getWorstArrow(): ItemStack? {
        return getAllItems()
            .filter { item -> !item.isEmpty && item.item is ArrowItem && isItemValid(item) }
            .minByOrNull { item -> item.count }
    }

    /**
     * 获取数量最少的可用方块堆。
     */
    context(nn: NonNullContext)
    fun getWorstBlock(): ItemStack? {
        return getAllItems()
            .filter { item -> !item.isEmpty && item.item is BlockItem && isValidStack(item) && isItemValid(item) }
            .minByOrNull { item -> item.count }
    }

    /**
     * 获取数量最多的可用方块堆。
     */
    context(nn: NonNullContext)
    fun getBestBlock(): ItemStack? {
        return getAllItems()
            .filter { item -> !item.isEmpty && item.item is BlockItem && isValidStack(item) && isItemValid(item) }
            .maxByOrNull { item -> item.count }
    }

    /**
     * 获取背包内镐的最高工具评分。
     */
    context(nn: NonNullContext)
    fun getBestPickaxeScore(): Float {
        return getAllItems()
            .filter { item -> isPickaxe(item) && isItemValid(item) }
            .map { item -> getToolScore(item) }
            .maxOrNull() ?: 0.0F
    }

    /**
     * 获取背包内评分最高的镐。
     */
    context(nn: NonNullContext)
    fun getBestPickaxe(): ItemStack? {
        return getAllItems()
            .filter { item -> isPickaxe(item) && isItemValid(item) }
            .maxByOrNull { item -> (getToolScore(item) * 100.0F).toInt() }
    }

    /**
     * 获取背包内普通斧的最高工具评分。
     */
    context(nn: NonNullContext)
    fun getBestAxeScore(): Float {
        return getAllItems()
            .filter { item -> !item.isEmpty && isAxe(item) && !isSharpnessAxe(item) && isItemValid(item) }
            .map { item -> getToolScore(item) }
            .maxOrNull() ?: 0.0F
    }

    /**
     * 获取背包内评分最高的普通斧。
     */
    context(nn: NonNullContext)
    fun getBestAxe(): ItemStack? {
        return getAllItems()
            .filter { item -> !item.isEmpty && isAxe(item) && !isSharpnessAxe(item) && isItemValid(item) }
            .maxByOrNull { item -> (getToolScore(item) * 100.0F).toInt() }
    }

    /**
     * 获取背包内伤害评分最高的锋利斧。
     */
    context(nn: NonNullContext)
    fun getBestShapeAxe(): ItemStack? {
        return getAllItems()
            .filter { item ->
                !item.isEmpty && isAxe(item) && isSharpnessAxe(item) && isItemValid(item) && !isGodAxe(
                    item
                )
            }
            .maxByOrNull { item -> (getAxeDamage(item) * 100.0F).toInt() }
    }

    /**
     * 获取背包内锹的最高工具评分。
     */
    context(nn: NonNullContext)
    fun getBestShovelScore(): Float {
        return getAllItems()
            .filter { item -> !item.isEmpty && isShovel(item) && isItemValid(item) }
            .map { item -> getToolScore(item) }
            .maxOrNull() ?: 0.0F
    }

    /**
     * 获取背包内评分最高的锹。
     */
    context(nn: NonNullContext)
    fun getBestShovel(): ItemStack? {
        return getAllItems()
            .filter { item -> !item.isEmpty && isShovel(item) && isItemValid(item) }
            .maxByOrNull { item -> (getToolScore(item) * 100.0F).toInt() }
    }

    /**
     * 获取背包内弩的最高附魔评分。
     */
    context(nn: NonNullContext)
    fun getBestCrossbowScore(): Float {
        return getAllItems()
            .filter { item -> !item.isEmpty && item.item is CrossbowItem && isItemValid(item) }
            .map { item -> getCrossbowScore(item) }
            .maxOrNull() ?: 0.0F
    }

    /**
     * 获取背包内评分最高的弩。
     */
    context(nn: NonNullContext)
    fun getBestCrossbow(): ItemStack? {
        return getAllItems()
            .filter { item -> !item.isEmpty && item.item is CrossbowItem && isItemValid(item) }
            .maxByOrNull { item -> (getCrossbowScore(item) * 100.0F).toInt() }
    }

    /**
     * 获取背包内冲击弓的最高评分。
     */
    context(nn: NonNullContext)
    fun getBestPunchBowScore(): Float {
        return getAllItems()
            .filter { item -> !item.isEmpty && item.item is BowItem && isItemValid(item) }
            .map { item -> getPunchBowScore(item) }
            .maxOrNull() ?: 0.0F
    }

    /**
     * 获取背包内评分最高的冲击弓。
     */
    context(nn: NonNullContext)
    fun getBestPunchBow(): ItemStack? {
        return getAllItems()
            .filter { item -> !item.isEmpty && item.item is BowItem && isItemValid(item) }
            .maxByOrNull { item -> (getPunchBowScore(item) * 100.0F).toInt() }
    }

    /**
     * 获取背包内力量弓的最高评分。
     */
    context(nn: NonNullContext)
    fun getBestPowerBowScore(): Float {
        return getAllItems()
            .filter { item -> !item.isEmpty && item.item is BowItem && isItemValid(item) }
            .map { item -> getPowerBowScore(item) }
            .maxOrNull() ?: 0.0F
    }

    /**
     * 获取背包内评分最高的力量弓。
     */
    context(nn: NonNullContext)
    fun getBestPowerBow(): ItemStack? {
        return getAllItems()
            .filter { item -> !item.isEmpty && item.item is BowItem && isItemValid(item) }
            .maxByOrNull { item -> (getPowerBowScore(item) * 100.0F).toInt() }
    }

    /**
     * 判断物品是否为可用的冲击弓。
     */
    fun isPunchBow(stack: ItemStack): Boolean {
        return getPunchBowScore(stack) > 10.0F && isItemValid(stack)
    }

    /**
     * 判断物品是否为可用的力量弓。
     */
    fun isPowerBow(stack: ItemStack): Boolean {
        return getPowerBowScore(stack) > 10.0F && isItemValid(stack)
    }

    /**
     * 判断背包中是否含有指定物品。
     */
    context(nn: NonNullContext)
    fun hasItem(item: Item): Boolean {
        return getAllItems().any { stack -> !stack.isEmpty && stack.item === item }
    }

    /**
     * 统计背包中指定物品的总数。
     */
    context(nn: NonNullContext)
    fun getItemCount(item: Item): Int {
        return getAllItems()
            .filter { stack -> !stack.isEmpty && stack.item === item }
            .sumOf { stack -> stack.count }
    }

    /**
     * 计算弓偏向冲击用途的评分。
     */
    fun getPunchBowScore(stack: ItemStack?): Float {
        if (stack == null || stack.isEmpty || stack.item !is BowItem) {
            return 0.0F
        }

        var valence = 10.0F
        valence += EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.PUNCH)
        valence += EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.INFINITY)
        valence += EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.FLAME)
        valence += EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.POWER) / 10.0F
        return valence + stack.damageValue.toFloat() / stack.maxDamage.toFloat()
    }

    /**
     * 计算弓偏向伤害用途的评分。
     */
    fun getPowerBowScore(stack: ItemStack?): Float {
        if (stack == null || stack.isEmpty || stack.item !is BowItem) {
            return 0.0F
        }

        var valence = 10.0F
        valence += EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.PUNCH) / 10.0F
        valence += EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.INFINITY)
        valence += EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.FLAME)
        valence += EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.POWER)
        return valence + stack.damageValue.toFloat() / stack.maxDamage.toFloat()
    }

    /**
     * 根据挖掘速度和附魔计算工具评分。
     */
    fun getToolScore(stack: ItemStack?): Float {
        if (stack == null || stack.isEmpty || isGodItem(stack) || isSharpnessAxe(stack)) {
            return 0.0F
        }

        val valence: Float
        if (isPickaxe(stack)) {
            valence = stack.getDestroySpeed(Blocks.STONE.defaultBlockState())
        } else if (isAxe(stack)) {
            valence = stack.getDestroySpeed(Blocks.OAK_LOG.defaultBlockState())
        } else if (isShovel(stack)) {
            valence = stack.getDestroySpeed(Blocks.DIRT.defaultBlockState())
        } else {
            return 0.0F
        }

        val efficiency = EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.EFFICIENCY)
        if (efficiency > 0) {
            return valence + efficiency * 0.0075F
        }

        return valence
    }

    private fun getMainhandAttackDamage(stack: ItemStack?): Float {
        if (stack == null || stack.isEmpty) {
            return 0.0F
        }

        var damage = 0.0F
        stack.forEachModifier(EquipmentSlot.MAINHAND) { attribute, modifier ->
            if (attribute === Attributes.ATTACK_DAMAGE) {
                damage += getAttributeModifierAmount(modifier)
            }
        }
        return damage
    }

    private fun getSharpnessBonus(stack: ItemStack): Float {
        val level = EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.SHARPNESS)
        return if (level > 0) 0.5F * level + 0.5F else 0.0F
    }

    private fun getAttributeModifierAmount(modifier: AttributeModifier): Float {
        return modifier.amount().toFloat()
    }

    /**
     * 计算斧的主手攻击伤害评分。
     */
    fun getAxeDamage(stack: ItemStack?): Float {
        if (stack == null || stack.isEmpty || !isAxe(stack)) {
            return 0.0F
        }

        return getMainhandAttackDamage(stack) + getSharpnessBonus(stack)
    }

    /**
     * 计算剑的主手攻击伤害评分。
     */
    fun getSwordDamage(stack: ItemStack?): Float {
        if (!isSword(stack)) {
            return 0.0F
        }

        val item = stack ?: return 0.0F
        return getMainhandAttackDamage(item) + getSharpnessBonus(item)
    }

    /**
     * 根据护甲、韧性和附魔计算防具评分。
     */
    fun getProtection(stack: ItemStack?): Float {
        if (stack == null || stack.isEmpty) {
            return 0.0F
        }

        val slot = getArmorSlot(stack) ?: return 0.0F

        var armor = 0.0F
        var toughness = 0.0F
        stack.forEachModifier(slot) { attribute, modifier ->
            if (attribute === Attributes.ARMOR) {
                armor += getAttributeModifierAmount(modifier)
            } else if (attribute === Attributes.ARMOR_TOUGHNESS) {
                toughness += getAttributeModifierAmount(modifier)
            }
        }

        return armor * 100.0F +
                toughness * 10.0F +
                EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.PROTECTION)
    }

    /**
     * 根据附魔计算弩的评分。
     */
    fun getCrossbowScore(stack: ItemStack?): Float {
        if (stack == null || stack.isEmpty || stack.item !is CrossbowItem) {
            return 0.0F
        }

        var valence = 0
        valence += EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.QUICK_CHARGE)
        valence += EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.MULTISHOT)
        valence += EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.PIERCING)
        return valence.toFloat()
    }

    /**
     * 判断物品是否属于需要保留的特殊强力物品。
     */
    fun isGodItem(stack: ItemStack): Boolean {
        if (stack.isEmpty) {
            return false
        }

        if (isAxe(stack) &&
            stack.item === Items.GOLDEN_AXE &&
            EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.SHARPNESS) > 100
        ) {
            return true
        }

        if (stack.item === Items.SLIME_BALL && EnchantmentUtils.getEnchantmentLevel(
                stack,
                Enchantments.KNOCKBACK
            ) > 1
        ) {
            return true
        }

        return stack.item === Items.TOTEM_OF_UNDYING || stack.item === Items.END_CRYSTAL
    }

    /**
     * 判断普通物品是否适合保留。
     */
    fun isCommonItemUseful(stack: ItemStack): Boolean {
        if (stack.isEmpty) {
            return true
        }

        val item = stack.item
        if (item is BlockItem) {
            return item.block !== Blocks.ENCHANTING_TABLE && item.block !== Blocks.COBWEB
        }

        if (item is WritableBookItem || item is WrittenBookItem || item is KnowledgeBookItem) {
            return false
        }

        if (item is ExperienceBottleItem || item is FireworkRocketItem) {
            return false
        }

        if (item === Items.WHEAT_SEEDS || item === Items.BEETROOT_SEEDS || item === Items.MELON_SEEDS || item === Items.PUMPKIN_SEEDS) {
            return false
        }

        return item !== Items.FLINT_AND_STEEL
    }

    /**
     * 判断物品堆是否为可用于放置的有效方块堆。
     */
    fun isValidStack(stack: ItemStack?): Boolean {
        if (stack == null || stack.isEmpty || stack.item !is BlockItem) {
            return false
        }

        if (!isItemValid(stack)) {
            return false
        }

        val name = stack.displayName.string
        if (name.contains("Click") || name.contains("点击")) {
            return false
        }

        if (stack.item is StandingAndWallBlockItem) {
            return false
        }

        val block = (stack.item as BlockItem).block
        return !(block is FlowerBlock || block is BushBlock || block is NetherFungusBlock || block is CropBlock) && block !is SlabBlock && !blacklistedBlocks.contains(
            block
        )
    }

}
