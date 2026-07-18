package com.github.epsilon.utils.player;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.block.*;

import java.util.*;

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
    private static final Minecraft mc = Minecraft.getInstance();

    private static int getEnchantmentLevel(ItemStack stack, ResourceKey<Enchantment> enchantmentKey) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }

        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (it.unimi.dsi.fastutil.objects.Object2IntMap.Entry<net.minecraft.core.Holder<Enchantment>> entry : enchantments.entrySet()) {
            if (entry.getKey().is(enchantmentKey)) {
                return entry.getIntValue();
            }
        }

        return 0;
    }

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

    public static boolean isGoldenHead(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        if (stack.getItem() instanceof BlockItem item) {
            return item.getBlock() instanceof SkullBlock;
        }

        return false;
    }

    public static boolean isArmor(ItemStack stack) {
        return getArmorSlot(stack) != null;
    }

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

    public static boolean isSword(ItemStack stack) {
        return stack != null && !stack.isEmpty() && SWORDS.contains(stack.getItem());
    }

    public static boolean isPickaxe(ItemStack stack) {
        return stack != null && !stack.isEmpty() && PICKAXES.contains(stack.getItem());
    }

    public static ItemStack getInventoryStack(int slot) {
        if (mc.player == null || slot < 0 || slot >= mc.player.getInventory().getNonEquipmentItems().size()) {
            return ItemStack.EMPTY;
        }

        return mc.player.getInventory().getNonEquipmentItems().get(slot);
    }

    public static ItemStack getArmorStack(EquipmentSlot slot) {
        if (mc.player == null) {
            return ItemStack.EMPTY;
        }

        return mc.player.getInventory().getItem(slot.getIndex(36));
    }

    public static ItemStack getOffhandStack() {
        if (mc.player == null) {
            return ItemStack.EMPTY;
        }

        return mc.player.getInventory().getItem(Inventory.SLOT_OFFHAND);
    }

    public static boolean isSharpnessAxe(ItemStack stack) {
        return !stack.isEmpty()
                && stack.getItem() instanceof AxeItem
                && getEnchantmentLevel(stack, Enchantments.SHARPNESS) >= 8
                && getEnchantmentLevel(stack, Enchantments.SHARPNESS) < 50;
    }

    public static boolean isGodAxe(ItemStack stack) {
        return !stack.isEmpty()
                && stack.getItem() == Items.GOLDEN_AXE
                && getEnchantmentLevel(stack, Enchantments.SHARPNESS) > 100;
    }

    public static boolean isEnchantedGApple(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == Items.ENCHANTED_GOLDEN_APPLE;
    }

    public static boolean isEndCrystal(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == Items.END_CRYSTAL;
    }

    public static boolean isKBBall(ItemStack stack) {
        return !stack.isEmpty()
                && stack.getItem() == Items.SLIME_BALL
                && getEnchantmentLevel(stack, Enchantments.KNOCKBACK) > 1;
    }

    public static boolean isKBStick(ItemStack stack) {
        return !stack.isEmpty()
                && stack.getItem() == Items.STICK
                && getEnchantmentLevel(stack, Enchantments.KNOCKBACK) > 1;
    }

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

    public static int getPunchLevel(ItemStack stack) {
        return getEnchantmentLevel(stack, Enchantments.PUNCH);
    }

    public static int getPowerLevel(ItemStack stack) {
        return getEnchantmentLevel(stack, Enchantments.POWER);
    }

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

    public static float getBestArmorScore(EquipmentSlot slot) {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && getArmorSlot(item) == slot)
                .map(InvHelper::getProtection)
                .max(Float::compareTo)
                .orElse(0.0F);
    }

    public static float getCurrentArmorScore(EquipmentSlot slot) {
        return getProtection(getArmorStack(slot));
    }

    public static float getBestSwordDamage() {
        return getAllItems().stream()
                .filter(InvHelper::isSword)
                .map(InvHelper::getSwordDamage)
                .max(Float::compareTo)
                .orElse(0.0F);
    }

    public static ItemStack getBestSword() {
        return getAllItems().stream()
                .filter(InvHelper::isSword)
                .max(Comparator.comparingInt(s -> (int) (getSwordDamage(s) * 100.0F)))
                .orElse(null);
    }

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

    public static ItemStack getBestProjectile() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && (item.getItem() == Items.EGG || item.getItem() == Items.SNOWBALL) && isItemValid(item))
                .max(Comparator.comparingInt(ItemStack::getCount))
                .orElse(null);
    }

    public static ItemStack getFishingRod() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof FishingRodItem && isItemValid(item))
                .findAny()
                .orElse(null);
    }

    public static int getBlockCountInInventory() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof BlockItem && isValidStack(item) && isItemValid(item))
                .mapToInt(ItemStack::getCount)
                .sum();
    }

    public static ItemStack getWorstProjectile() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && (item.getItem() == Items.EGG || item.getItem() == Items.SNOWBALL))
                .min(Comparator.comparingInt(ItemStack::getCount))
                .orElse(null);
    }

    public static ItemStack getWorstArrow() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof ArrowItem && isItemValid(item))
                .min(Comparator.comparingInt(ItemStack::getCount))
                .orElse(null);
    }

    public static ItemStack getWorstBlock() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof BlockItem && isValidStack(item) && isItemValid(item))
                .min(Comparator.comparingInt(ItemStack::getCount))
                .orElse(null);
    }

    public static ItemStack getBestBlock() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof BlockItem && isValidStack(item) && isItemValid(item))
                .max(Comparator.comparingInt(ItemStack::getCount))
                .orElse(null);
    }

    public static float getBestPickaxeScore() {
        return getAllItems().stream()
                .filter(item -> isPickaxe(item) && isItemValid(item))
                .map(InvHelper::getToolScore)
                .max(Float::compareTo)
                .orElse(0.0F);
    }

    public static ItemStack getBestPickaxe() {
        return getAllItems().stream()
                .filter(item -> isPickaxe(item) && isItemValid(item))
                .max(Comparator.comparingInt(s -> (int) (getToolScore(s) * 100.0F)))
                .orElse(null);
    }

    public static float getBestAxeScore() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof AxeItem && !isSharpnessAxe(item) && isItemValid(item))
                .map(InvHelper::getToolScore)
                .max(Float::compareTo)
                .orElse(0.0F);
    }

    public static ItemStack getBestAxe() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof AxeItem && !isSharpnessAxe(item) && isItemValid(item))
                .max(Comparator.comparingInt(s -> (int) (getToolScore(s) * 100.0F)))
                .orElse(null);
    }

    public static ItemStack getBestShapeAxe() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof AxeItem && isSharpnessAxe(item) && isItemValid(item) && !isGodAxe(item))
                .max(Comparator.comparingInt(s -> (int) (getAxeDamage(s) * 100.0F)))
                .orElse(null);
    }

    public static float getBestShovelScore() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof ShovelItem && isItemValid(item))
                .map(InvHelper::getToolScore)
                .max(Float::compareTo)
                .orElse(0.0F);
    }

    public static ItemStack getBestShovel() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof ShovelItem && isItemValid(item))
                .max(Comparator.comparingInt(s -> (int) (getToolScore(s) * 100.0F)))
                .orElse(null);
    }

    public static float getBestCrossbowScore() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof CrossbowItem && isItemValid(item))
                .map(InvHelper::getCrossbowScore)
                .max(Float::compareTo)
                .orElse(0.0F);
    }

    public static ItemStack getBestCrossbow() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof CrossbowItem && isItemValid(item))
                .max(Comparator.comparingInt(s -> (int) (getCrossbowScore(s) * 100.0F)))
                .orElse(null);
    }

    public static float getBestPunchBowScore() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof BowItem && isItemValid(item))
                .map(InvHelper::getPunchBowScore)
                .max(Float::compareTo)
                .orElse(0.0F);
    }

    public static ItemStack getBestPunchBow() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof BowItem && isItemValid(item))
                .max(Comparator.comparingInt(s -> (int) (getPunchBowScore(s) * 100.0F)))
                .orElse(null);
    }

    public static float getBestPowerBowScore() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof BowItem && isItemValid(item))
                .map(InvHelper::getPowerBowScore)
                .max(Float::compareTo)
                .orElse(0.0F);
    }

    public static ItemStack getBestPowerBow() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof BowItem && isItemValid(item))
                .max(Comparator.comparingInt(s -> (int) (getPowerBowScore(s) * 100.0F)))
                .orElse(null);
    }

    public static boolean isPunchBow(ItemStack stack) {
        return getPunchBowScore(stack) > 10.0F && isItemValid(stack);
    }

    public static boolean isPowerBow(ItemStack stack) {
        return getPowerBowScore(stack) > 10.0F && isItemValid(stack);
    }

    public static boolean hasItem(Item item) {
        return getAllItems().stream().anyMatch(stack -> !stack.isEmpty() && stack.getItem() == item);
    }

    public static int getItemCount(Item item) {
        return getAllItems().stream()
                .filter(stack -> !stack.isEmpty() && stack.getItem() == item)
                .mapToInt(ItemStack::getCount)
                .sum();
    }

    public static float getPunchBowScore(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BowItem)) {
            return 0.0F;
        }

        float valence = 10.0F;
        valence += getEnchantmentLevel(stack, Enchantments.PUNCH);
        valence += getEnchantmentLevel(stack, Enchantments.INFINITY);
        valence += getEnchantmentLevel(stack, Enchantments.FLAME);
        valence += getEnchantmentLevel(stack, Enchantments.POWER) / 10.0F;
        return valence + (float) stack.getDamageValue() / (float) stack.getMaxDamage();
    }

    public static float getPowerBowScore(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BowItem)) {
            return 0.0F;
        }

        float valence = 10.0F;
        valence += getEnchantmentLevel(stack, Enchantments.PUNCH) / 10.0F;
        valence += getEnchantmentLevel(stack, Enchantments.INFINITY);
        valence += getEnchantmentLevel(stack, Enchantments.FLAME);
        valence += getEnchantmentLevel(stack, Enchantments.POWER);
        return valence + (float) stack.getDamageValue() / (float) stack.getMaxDamage();
    }

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

        int efficiency = getEnchantmentLevel(stack, Enchantments.EFFICIENCY);
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
            if (attribute.equals(Attributes.ATTACK_DAMAGE)) {
                damage[0] += getAttributeModifierAmount(modifier);
            }
        });
        return damage[0];
    }

    private static float getSharpnessBonus(ItemStack stack) {
        int level = getEnchantmentLevel(stack, Enchantments.SHARPNESS);
        return level > 0 ? 0.5F * level + 0.5F : 0.0F;
    }

    private static float getAttributeModifierAmount(AttributeModifier modifier) {
        return (float) modifier.amount();
    }

    public static float getAxeDamage(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof AxeItem)) {
            return 0.0F;
        }

        return getMainhandAttackDamage(stack) + getSharpnessBonus(stack);
    }

    public static float getSwordDamage(ItemStack stack) {
        if (!isSword(stack)) {
            return 0.0F;
        }

        return getMainhandAttackDamage(stack) + getSharpnessBonus(stack);
    }

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
            if (attribute.equals(Attributes.ARMOR)) {
                armor[0] += getAttributeModifierAmount(modifier);
            } else if (attribute.equals(Attributes.ARMOR_TOUGHNESS)) {
                toughness[0] += getAttributeModifierAmount(modifier);
            }
        });

        return armor[0] * 100.0F
                + toughness[0] * 10.0F
                + getEnchantmentLevel(stack, Enchantments.PROTECTION);
    }

    public static float getCrossbowScore(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof CrossbowItem)) {
            return 0.0F;
        }

        int valence = 0;
        valence += getEnchantmentLevel(stack, Enchantments.QUICK_CHARGE);
        valence += getEnchantmentLevel(stack, Enchantments.MULTISHOT);
        valence += getEnchantmentLevel(stack, Enchantments.PIERCING);
        return valence;
    }

    public static boolean isGodItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        if (stack.getItem() instanceof AxeItem
                && stack.getItem() == Items.GOLDEN_AXE
                && getEnchantmentLevel(stack, Enchantments.SHARPNESS) > 100) {
            return true;
        }

        if (stack.getItem() == Items.SLIME_BALL && getEnchantmentLevel(stack, Enchantments.KNOCKBACK) > 1) {
            return true;
        }

        return stack.getItem() == Items.TOTEM_OF_UNDYING || stack.getItem() == Items.END_CRYSTAL;
    }

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

    public static boolean isValidStack(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BlockItem) || stack.getCount() <= 1) {
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
