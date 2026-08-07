package com.github.epsilon.utils.player;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.block.*;

import java.util.*;

import static com.github.epsilon.Constants.mc;

public class InvHelper {

    public static final List<Block> blacklistedBlocks = Arrays.asList(
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
            Blocks.FLOWER_POT
    );

    private static final Set<Item> SWORDS = Set.of(
            Items.WOODEN_SWORD,
            Items.STONE_SWORD,
            Items.COPPER_SWORD,
            Items.IRON_SWORD,
            Items.GOLDEN_SWORD,
            Items.DIAMOND_SWORD,
            Items.NETHERITE_SWORD
    );
    private static final Set<Item> PICKAXES = Set.of(
            Items.WOODEN_PICKAXE,
            Items.STONE_PICKAXE,
            Items.COPPER_PICKAXE,
            Items.IRON_PICKAXE,
            Items.GOLDEN_PICKAXE,
            Items.DIAMOND_PICKAXE,
            Items.NETHERITE_PICKAXE
    );

    /**
     * 判断当前界面是否应禁用背包辅助功能。
     *
     * @return 判断结果
     */
    public static boolean shouldDisableFeatures() {
        return getAllItems().stream().anyMatch(item -> {
            if (item.isEmpty()) {
                return false;
            }

            String name = item.getDisplayName().getString();
            return name.contains("Click")
                    || name.contains("点击")
                    || name.contains("Right")
                    || name.contains("Teleport")
                    || name.contains("离开游戏")
                    || name.contains("选择")
                    || name.contains("再来");
        });
    }

    /**
     * 判断物品是否为带自定义名称的金头。
     *
     * @param stack 物品堆
     * @return 判断结果
     */
    public static boolean isGoldenHead(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        if (stack.getItem() instanceof BlockItem item) {
            return item.getBlock() instanceof SkullBlock;
        }

        return false;
    }

    /**
     * 判断物品是否可装备到护甲槽位。
     *
     * @param stack 物品堆
     * @return 判断结果
     */
    public static boolean isArmor(ItemStack stack) {
        return getArmorSlot(stack) != null;
    }

    /**
     * 获取物品对应的护甲装备槽位。
     *
     * @param stack 物品堆
     * @return 获取或计算得到的结果
     */
    public static EquipmentSlot getArmorSlot(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable == null) {
            return null;
        }

        EquipmentSlot slot = equippable.slot();
        return slot.isArmor() && slot != EquipmentSlot.BODY ? slot : null;
    }

    /**
     * 判断物品是否为剑。
     *
     * @param stack 物品堆
     * @return 判断结果
     */
    public static boolean isSword(ItemStack stack) {
        return stack != null && !stack.isEmpty() && SWORDS.contains(stack.getItem());
    }

    /**
     * 判断物品是否为镐。
     *
     * @param stack 物品堆
     * @return 判断结果
     */
    public static boolean isPickaxe(ItemStack stack) {
        return stack != null && !stack.isEmpty() && PICKAXES.contains(stack.getItem());
    }

    /**
     * 获取指定普通背包槽位中的物品堆。
     *
     * @param slot 背包、容器或装备槽位
     * @return 获取或计算得到的结果
     */
    public static ItemStack getInventoryStack(int slot) {
        if (mc.player == null || slot < 0 || slot >= mc.player.getInventory().getNonEquipmentItems().size()) {
            return ItemStack.EMPTY;
        }

        return mc.player.getInventory().getNonEquipmentItems().get(slot);
    }

    /**
     * 获取指定护甲槽位中的物品堆。
     *
     * @param slot 背包、容器或装备槽位
     * @return 获取或计算得到的结果
     */
    public static ItemStack getArmorStack(EquipmentSlot slot) {
        if (mc.player == null) {
            return ItemStack.EMPTY;
        }

        return mc.player.getInventory().getItem(slot.getIndex(36));
    }

    /**
     * 获取副手槽位中的物品堆。
     *
     * @return 获取或计算得到的结果
     */
    public static ItemStack getOffhandStack() {
        if (mc.player == null) {
            return ItemStack.EMPTY;
        }

        return mc.player.getInventory().getItem(Inventory.SLOT_OFFHAND);
    }

    /**
     * 判断物品是否为高等级锋利斧。
     *
     * @param stack 物品堆
     * @return 判断结果
     */
    public static boolean isSharpnessAxe(ItemStack stack) {
        return !stack.isEmpty()
                && stack.getItem() instanceof AxeItem
                && EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.SHARPNESS) >= 8
                && EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.SHARPNESS) < 50;
    }

    /**
     * 判断物品是否为特殊高伤害金斧。
     *
     * @param stack 物品堆
     * @return 判断结果
     */
    public static boolean isGodAxe(ItemStack stack) {
        return !stack.isEmpty()
                && stack.getItem() == Items.GOLDEN_AXE
                && EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.SHARPNESS) > 100;
    }

    /**
     * 判断物品是否为附魔金苹果。
     *
     * @param stack 物品堆
     * @return 判断结果
     */
    public static boolean isEnchantedGApple(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == Items.ENCHANTED_GOLDEN_APPLE;
    }

    /**
     * 判断物品是否为末影水晶。
     *
     * @param stack 物品堆
     * @return 判断结果
     */
    public static boolean isEndCrystal(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == Items.END_CRYSTAL;
    }

    /**
     * 判断物品是否为高击退黏液球。
     *
     * @param stack 物品堆
     * @return 判断结果
     */
    public static boolean isKBBall(ItemStack stack) {
        return !stack.isEmpty()
                && stack.getItem() == Items.SLIME_BALL
                && EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.KNOCKBACK) > 1;
    }

    /**
     * 判断物品是否为高击退木棍。
     *
     * @param stack 物品堆
     * @return 判断结果
     */
    public static boolean isKBStick(ItemStack stack) {
        return !stack.isEmpty()
                && stack.getItem() == Items.STICK
                && EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.KNOCKBACK) > 1;
    }

    /**
     * 查找普通背包区域中的空槽位。
     *
     * @return 操作结果
     */
    public static int findEmptyInventory() {
        if (mc.player == null) {
            return -1;
        }

        for (int i = 9; i < mc.player.getInventory().getNonEquipmentItems().size(); i++) {
            if (getInventoryStack(i).isEmpty()) {
                return i;
            }
        }

        return -1;
    }

    /**
     * 查找快捷栏中的空槽位。
     *
     * @return 操作结果
     */
    public static int findEmptySlot() {
        if (mc.player == null) {
            return -1;
        }

        for (int i = 0; i < 9; i++) {
            if (getInventoryStack(i).isEmpty()) {
                return i;
            }
        }

        return -1;
    }

    /**
     * 在快捷栏中查找指定物品。
     *
     * @param item 物品
     * @return 匹配槽位；未找到或玩家不存在时返回 null
     */
    public static Integer findItemHotbar(Item item) {
        if (mc.player == null) {
            return null;
        }

        for (int i = 0; i < 9; i++) {
            if (getInventoryStack(i).getItem() == item) {
                return i;
            }
        }

        return null;
    }

    /**
     * 获取弓的冲击附魔等级。
     *
     * @param stack 物品堆
     * @return 获取或计算得到的结果
     */
    public static int getPunchLevel(ItemStack stack) {
        return EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.PUNCH);
    }

    /**
     * 获取弓的力量附魔等级。
     *
     * @param stack 物品堆
     * @return 获取或计算得到的结果
     */
    public static int getPowerLevel(ItemStack stack) {
        return EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.POWER);
    }

    /**
     * 获取玩家背包中的全部物品堆。
     *
     * @return 获取或计算得到的结果
     */
    public static List<ItemStack> getAllItems() {
        ArrayList<ItemStack> list = new ArrayList<>();
        if (mc.player == null) {
            return list;
        }

        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            list.add(mc.player.getInventory().getItem(i));
        }

        return list;
    }

    /**
     * 获取指定护甲槽位可用物品的最高评分。
     *
     * @param slot 背包、容器或装备槽位
     * @return 获取或计算得到的结果
     */
    public static float getBestArmorScore(EquipmentSlot slot) {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && getArmorSlot(item) == slot)
                .map(InvHelper::getProtection)
                .max(Float::compareTo)
                .orElse(0.0F);
    }

    /**
     * 获取指定护甲槽位当前装备的评分。
     *
     * @param slot 背包、容器或装备槽位
     * @return 获取或计算得到的结果
     */
    public static float getCurrentArmorScore(EquipmentSlot slot) {
        return getProtection(getArmorStack(slot));
    }

    /**
     * 获取背包内剑的最高伤害评分。
     *
     * @return 获取或计算得到的结果
     */
    public static float getBestSwordDamage() {
        return getAllItems().stream()
                .filter(InvHelper::isSword)
                .map(InvHelper::getSwordDamage)
                .max(Float::compareTo)
                .orElse(0.0F);
    }

    /**
     * 获取背包内伤害评分最高的剑。
     *
     * @return 评分最高的剑；不存在时返回 null
     */
    public static ItemStack getBestSword() {
        return getAllItems().stream()
                .filter(InvHelper::isSword)
                .max(Comparator.comparingInt(s -> (int) (getSwordDamage(s) * 100.0F)))
                .orElse(null);
    }

    /**
     * 按对象引用查找物品堆所在的背包槽位。
     *
     * @param stack 物品堆
     * @return 操作结果
     */
    public static int getItemStackSlot(ItemStack stack) {
        if (stack == null || mc.player == null) {
            return -1;
        }

        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            if (mc.player.getInventory().getItem(i) == stack) {
                return i;
            }
        }

        return -1;
    }

    /**
     * 判断物品是否适合背包辅助逻辑处理。
     *
     * @param stack 物品堆
     * @return 判断结果
     */
    public static boolean isItemValid(ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }

        if (stack.getItem() instanceof PlayerHeadItem) {
            return false;
        }

        String name = stack.getDisplayName().getString();
        return !name.contains("Click")
                && !name.contains("Right")
                && !name.contains("点击")
                && !name.contains("Teleport")
                && !name.contains("使用")
                && !name.contains("传送")
                && !name.contains("再来");
    }

    /**
     * 在普通背包槽位中查找指定物品。
     *
     * @param item 物品
     * @return 操作结果
     */
    public static int getItemSlot(Item item) {
        if (mc.player == null) {
            return -1;
        }

        for (int i = 0; i < mc.player.getInventory().getNonEquipmentItems().size(); i++) {
            if (getInventoryStack(i).getItem() == item) {
                return i;
            }
        }

        return -1;
    }

    /**
     * 获取数量最多的可用投掷物。
     *
     * @return 获取或计算得到的结果
     */
    public static ItemStack getBestProjectile() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && (item.getItem() == Items.EGG || item.getItem() == Items.SNOWBALL) && isItemValid(item))
                .max(Comparator.comparingInt(ItemStack::getCount))
                .orElse(null);
    }

    /**
     * 获取任意可用钓鱼竿。
     *
     * @return 获取或计算得到的结果
     */
    public static ItemStack getFishingRod() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof FishingRodItem && isItemValid(item))
                .findAny()
                .orElse(null);
    }

    /**
     * 统计背包内可放置方块的总数。
     *
     * @return 操作结果
     */
    public static int getBlockCountInInventory() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof BlockItem && isValidStack(item) && isItemValid(item))
                .mapToInt(ItemStack::getCount)
                .sum();
    }

    /**
     * 获取数量最少的投掷物堆。
     *
     * @return 获取或计算得到的结果
     */
    public static ItemStack getWorstProjectile() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && (item.getItem() == Items.EGG || item.getItem() == Items.SNOWBALL))
                .min(Comparator.comparingInt(ItemStack::getCount))
                .orElse(null);
    }

    /**
     * 获取数量最少的可用箭矢堆。
     *
     * @return 获取或计算得到的结果
     */
    public static ItemStack getWorstArrow() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof ArrowItem && isItemValid(item))
                .min(Comparator.comparingInt(ItemStack::getCount))
                .orElse(null);
    }

    /**
     * 获取数量最少的可用方块堆。
     *
     * @return 获取或计算得到的结果
     */
    public static ItemStack getWorstBlock() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof BlockItem && isValidStack(item) && isItemValid(item))
                .min(Comparator.comparingInt(ItemStack::getCount))
                .orElse(null);
    }

    /**
     * 获取数量最多的可用方块堆。
     *
     * @return 获取或计算得到的结果
     */
    public static ItemStack getBestBlock() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof BlockItem && isValidStack(item) && isItemValid(item))
                .max(Comparator.comparingInt(ItemStack::getCount))
                .orElse(null);
    }

    /**
     * 获取背包内镐的最高工具评分。
     *
     * @return 获取或计算得到的结果
     */
    public static float getBestPickaxeScore() {
        return getAllItems().stream()
                .filter(item -> isPickaxe(item) && isItemValid(item))
                .map(InvHelper::getToolScore)
                .max(Float::compareTo)
                .orElse(0.0F);
    }

    /**
     * 获取背包内评分最高的镐。
     *
     * @return 获取或计算得到的结果
     */
    public static ItemStack getBestPickaxe() {
        return getAllItems().stream()
                .filter(item -> isPickaxe(item) && isItemValid(item))
                .max(Comparator.comparingInt(s -> (int) (getToolScore(s) * 100.0F)))
                .orElse(null);
    }

    /**
     * 获取背包内普通斧的最高工具评分。
     *
     * @return 获取或计算得到的结果
     */
    public static float getBestAxeScore() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof AxeItem && !isSharpnessAxe(item) && isItemValid(item))
                .map(InvHelper::getToolScore)
                .max(Float::compareTo)
                .orElse(0.0F);
    }

    /**
     * 获取背包内评分最高的普通斧。
     *
     * @return 获取或计算得到的结果
     */
    public static ItemStack getBestAxe() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof AxeItem && !isSharpnessAxe(item) && isItemValid(item))
                .max(Comparator.comparingInt(s -> (int) (getToolScore(s) * 100.0F)))
                .orElse(null);
    }

    /**
     * 获取背包内伤害评分最高的锋利斧。
     *
     * @return 获取或计算得到的结果
     */
    public static ItemStack getBestShapeAxe() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof AxeItem && isSharpnessAxe(item) && isItemValid(item) && !isGodAxe(item))
                .max(Comparator.comparingInt(s -> (int) (getAxeDamage(s) * 100.0F)))
                .orElse(null);
    }

    /**
     * 获取背包内锹的最高工具评分。
     *
     * @return 获取或计算得到的结果
     */
    public static float getBestShovelScore() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof ShovelItem && isItemValid(item))
                .map(InvHelper::getToolScore)
                .max(Float::compareTo)
                .orElse(0.0F);
    }

    /**
     * 获取背包内评分最高的锹。
     *
     * @return 获取或计算得到的结果
     */
    public static ItemStack getBestShovel() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof ShovelItem && isItemValid(item))
                .max(Comparator.comparingInt(s -> (int) (getToolScore(s) * 100.0F)))
                .orElse(null);
    }

    /**
     * 获取背包内弩的最高附魔评分。
     *
     * @return 获取或计算得到的结果
     */
    public static float getBestCrossbowScore() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof CrossbowItem && isItemValid(item))
                .map(InvHelper::getCrossbowScore)
                .max(Float::compareTo)
                .orElse(0.0F);
    }

    /**
     * 获取背包内评分最高的弩。
     *
     * @return 获取或计算得到的结果
     */
    public static ItemStack getBestCrossbow() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof CrossbowItem && isItemValid(item))
                .max(Comparator.comparingInt(s -> (int) (getCrossbowScore(s) * 100.0F)))
                .orElse(null);
    }

    /**
     * 获取背包内冲击弓的最高评分。
     *
     * @return 获取或计算得到的结果
     */
    public static float getBestPunchBowScore() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof BowItem && isItemValid(item))
                .map(InvHelper::getPunchBowScore)
                .max(Float::compareTo)
                .orElse(0.0F);
    }

    /**
     * 获取背包内评分最高的冲击弓。
     *
     * @return 获取或计算得到的结果
     */
    public static ItemStack getBestPunchBow() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof BowItem && isItemValid(item))
                .max(Comparator.comparingInt(s -> (int) (getPunchBowScore(s) * 100.0F)))
                .orElse(null);
    }

    /**
     * 获取背包内力量弓的最高评分。
     *
     * @return 获取或计算得到的结果
     */
    public static float getBestPowerBowScore() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof BowItem && isItemValid(item))
                .map(InvHelper::getPowerBowScore)
                .max(Float::compareTo)
                .orElse(0.0F);
    }

    /**
     * 获取背包内评分最高的力量弓。
     *
     * @return 获取或计算得到的结果
     */
    public static ItemStack getBestPowerBow() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof BowItem && isItemValid(item))
                .max(Comparator.comparingInt(s -> (int) (getPowerBowScore(s) * 100.0F)))
                .orElse(null);
    }

    /**
     * 判断物品是否为可用的冲击弓。
     *
     * @param stack 物品堆
     * @return 判断结果
     */
    public static boolean isPunchBow(ItemStack stack) {
        return getPunchBowScore(stack) > 10.0F && isItemValid(stack);
    }

    /**
     * 判断物品是否为可用的力量弓。
     *
     * @param stack 物品堆
     * @return 判断结果
     */
    public static boolean isPowerBow(ItemStack stack) {
        return getPowerBowScore(stack) > 10.0F && isItemValid(stack);
    }

    /**
     * 判断背包中是否含有指定物品。
     *
     * @param item 物品
     * @return 判断结果
     */
    public static boolean hasItem(Item item) {
        return getAllItems().stream().anyMatch(stack -> !stack.isEmpty() && stack.getItem() == item);
    }

    /**
     * 统计背包中指定物品的总数。
     *
     * @param item 物品
     * @return 操作结果
     */
    public static int getItemCount(Item item) {
        return getAllItems().stream()
                .filter(stack -> !stack.isEmpty() && stack.getItem() == item)
                .mapToInt(ItemStack::getCount)
                .sum();
    }

    /**
     * 计算弓偏向冲击用途的评分。
     *
     * @param stack 物品堆
     * @return 获取或计算得到的结果
     */
    public static float getPunchBowScore(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BowItem)) {
            return 0.0F;
        }

        float valence = 10.0F;
        valence += EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.PUNCH);
        valence += EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.INFINITY);
        valence += EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.FLAME);
        valence += EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.POWER) / 10.0F;
        return valence + (float) stack.getDamageValue() / (float) stack.getMaxDamage();
    }

    /**
     * 计算弓偏向伤害用途的评分。
     *
     * @param stack 物品堆
     * @return 获取或计算得到的结果
     */
    public static float getPowerBowScore(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BowItem)) {
            return 0.0F;
        }

        float valence = 10.0F;
        valence += EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.PUNCH) / 10.0F;
        valence += EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.INFINITY);
        valence += EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.FLAME);
        valence += EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.POWER);
        return valence + (float) stack.getDamageValue() / (float) stack.getMaxDamage();
    }

    /**
     * 根据挖掘速度和附魔计算工具评分。
     *
     * @param stack 物品堆
     * @return 操作结果
     */
    public static float getToolScore(ItemStack stack) {
        if (stack == null || stack.isEmpty() || isGodItem(stack) || isSharpnessAxe(stack)) {
            return 0.0F;
        }

        float valence;
        if (isPickaxe(stack)) {
            valence = stack.getDestroySpeed(Blocks.STONE.defaultBlockState());
        } else if (stack.getItem() instanceof AxeItem) {
            valence = stack.getDestroySpeed(Blocks.OAK_LOG.defaultBlockState());
        } else if (stack.getItem() instanceof ShovelItem) {
            valence = stack.getDestroySpeed(Blocks.DIRT.defaultBlockState());
        } else {
            return 0.0F;
        }

        int efficiency = EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.EFFICIENCY);
        if (efficiency > 0) {
            valence += efficiency * 0.0075F;
        }

        return valence;
    }

    private static float getMainhandAttackDamage(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0.0F;
        }

        final float[] damage = {0.0F};
        stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute == Attributes.ATTACK_DAMAGE) {
                damage[0] += getAttributeModifierAmount(modifier);
            }
        });
        return damage[0];
    }

    private static float getSharpnessBonus(ItemStack stack) {
        int level = EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.SHARPNESS);
        return level > 0 ? 0.5F * level + 0.5F : 0.0F;
    }

    private static float getAttributeModifierAmount(AttributeModifier modifier) {
        return (float) modifier.amount();
    }

    /**
     * 计算斧的主手攻击伤害评分。
     *
     * @param stack 物品堆
     * @return 获取或计算得到的结果
     */
    public static float getAxeDamage(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof AxeItem)) {
            return 0.0F;
        }

        return getMainhandAttackDamage(stack) + getSharpnessBonus(stack);
    }

    /**
     * 计算剑的主手攻击伤害评分。
     *
     * @param stack 物品堆
     * @return 获取或计算得到的结果
     */
    public static float getSwordDamage(ItemStack stack) {
        if (!isSword(stack)) {
            return 0.0F;
        }

        return getMainhandAttackDamage(stack) + getSharpnessBonus(stack);
    }

    /**
     * 根据护甲、韧性和附魔计算防具评分。
     *
     * @param stack 物品堆
     * @return 操作结果
     */
    public static float getProtection(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0.0F;
        }

        EquipmentSlot slot = getArmorSlot(stack);
        if (slot == null) {
            return 0.0F;
        }

        final float[] armor = {0.0F};
        final float[] toughness = {0.0F};
        stack.forEachModifier(slot, (attribute, modifier) -> {
            if (attribute == Attributes.ARMOR) {
                armor[0] += getAttributeModifierAmount(modifier);
            } else if (attribute == Attributes.ARMOR_TOUGHNESS) {
                toughness[0] += getAttributeModifierAmount(modifier);
            }
        });

        return armor[0] * 100.0F
                + toughness[0] * 10.0F
                + EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.PROTECTION);
    }

    /**
     * 根据附魔计算弩的评分。
     *
     * @param stack 物品堆
     * @return 操作结果
     */
    public static float getCrossbowScore(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof CrossbowItem)) {
            return 0.0F;
        }

        int valence = 0;
        valence += EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.QUICK_CHARGE);
        valence += EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.MULTISHOT);
        valence += EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.PIERCING);
        return valence;
    }

    /**
     * 判断物品是否属于需要保留的特殊强力物品。
     *
     * @param stack 物品堆
     * @return 判断结果
     */
    public static boolean isGodItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        if (stack.getItem() instanceof AxeItem
                && stack.getItem() == Items.GOLDEN_AXE
                && EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.SHARPNESS) > 100) {
            return true;
        }

        if (stack.getItem() == Items.SLIME_BALL && EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.KNOCKBACK) > 1) {
            return true;
        }

        return stack.getItem() == Items.TOTEM_OF_UNDYING || stack.getItem() == Items.END_CRYSTAL;
    }

    /**
     * 判断普通物品是否适合保留。
     *
     * @param stack 物品堆
     * @return 判断结果
     */
    public static boolean isCommonItemUseful(ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }

        Item item = stack.getItem();
        if (item instanceof BlockItem block) {
            return block.getBlock() != Blocks.ENCHANTING_TABLE && block.getBlock() != Blocks.COBWEB;
        }

        if (item instanceof WritableBookItem || item instanceof WrittenBookItem || item instanceof KnowledgeBookItem) {
            return false;
        }

        if (item instanceof ExperienceBottleItem || item instanceof FireworkRocketItem) {
            return false;
        }

        if (item == Items.WHEAT_SEEDS || item == Items.BEETROOT_SEEDS || item == Items.MELON_SEEDS || item == Items.PUMPKIN_SEEDS) {
            return false;
        }

        return item != Items.FLINT_AND_STEEL;
    }

    /**
     * 判断物品堆是否为可用于放置的有效方块堆。
     *
     * @param stack 物品堆
     * @return 判断结果
     */
    public static boolean isValidStack(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BlockItem)) {
            return false;
        }

        if (!isItemValid(stack)) {
            return false;
        }

        String name = stack.getDisplayName().getString();
        if (name.contains("Click") || name.contains("点击")) {
            return false;
        }

        if (stack.getItem() instanceof StandingAndWallBlockItem) {
            return false;
        }

        Block block = ((BlockItem) stack.getItem()).getBlock();
        if (block instanceof FlowerBlock || block instanceof BushBlock || block instanceof NetherFungusBlock || block instanceof CropBlock) {
            return false;
        }

        return !(block instanceof SlabBlock) && !blacklistedBlocks.contains(block);
    }

}
