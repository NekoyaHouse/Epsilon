package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.AttackEntityEvent;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.player.EnchantmentUtils;
import com.github.epsilon.utils.player.InvUtils;
import com.github.epsilon.utils.timer.TimerUtils;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * @author Nykrop
 */

public class AutoWeapon extends Module {

    public static final AutoWeapon INSTANCE = new AutoWeapon();

    private AutoWeapon() {
        super("Auto Weapon", Category.COMBAT);
    }

    private enum Mode {
        Simple,
        Smart
    }

    private enum Page {
        General,
        Swapping,
        SwordEnchants,
        MaceEnchants,
        OtherEnchants,
        Weapon
    }

    private final EnumSetting<Page> page = enumSetting("Page", Page.General);

    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.Simple, () -> page.is(Page.General));
    private final IntSetting targetSlot = intSetting("Target Slot", 1, 1, 9, 1, () -> page.is(Page.General) && mode.is(Mode.Simple));
    private final BoolSetting swapBack = boolSetting("Swap Back", true, () -> page.is(Page.General));
    private final IntSetting swapBackDelay = intSetting("Swap Back Delay", 200, 0, 500, 10, () -> page.is(Page.General));

    private final BoolSetting smartDurability = boolSetting("Smart Durability Saver", false, () -> page.is(Page.Swapping) && mode.is(Mode.Smart));
    private final BoolSetting smartShieldBreak = boolSetting("Smart Shield Breaker", true, () -> page.is(Page.Swapping) && mode.is(Mode.Smart));
    private final BoolSetting swordSwapping = boolSetting("Sword Swapping", true, () -> page.is(Page.Swapping) && mode.is(Mode.Smart));
    private final BoolSetting maceSwapping = boolSetting("Mace Swapping", true, () -> page.is(Page.Swapping) && mode.is(Mode.Smart));
    private final BoolSetting otherSwapping = boolSetting("Other Swapping", true, () -> page.is(Page.Swapping) && mode.is(Mode.Smart));

    private final BoolSetting enchantFireAspect = boolSetting("Fire Aspect", true, () -> page.is(Page.SwordEnchants) && mode.is(Mode.Smart) && swordSwapping.getValue());
    private final BoolSetting enchantLooting = boolSetting("Looting", true, () -> page.is(Page.SwordEnchants) && mode.is(Mode.Smart) && swordSwapping.getValue());
    private final BoolSetting enchantSharpness = boolSetting("Sharpness", true, () -> page.is(Page.SwordEnchants) && mode.is(Mode.Smart) && swordSwapping.getValue());
    private final BoolSetting enchantSmite = boolSetting("Smite", true, () -> page.is(Page.SwordEnchants) && mode.is(Mode.Smart) && swordSwapping.getValue());
    private final BoolSetting enchantBaneOfArthropods = boolSetting("Bane of Arthropods", true, () -> page.is(Page.SwordEnchants) && mode.is(Mode.Smart) && swordSwapping.getValue());

    private final BoolSetting enchantSweepingEdge = boolSetting("Sweeping Edge", true, () -> page.is(Page.SwordEnchants) && mode.is(Mode.Smart) && swordSwapping.getValue());
    private final BoolSetting regularMace = boolSetting("Regular Mace", true, () -> page.is(Page.MaceEnchants) && mode.is(Mode.Smart) && maceSwapping.getValue());
    private final BoolSetting enchantDensity = boolSetting("Density", true, () -> page.is(Page.MaceEnchants) && mode.is(Mode.Smart) && maceSwapping.getValue());
    private final BoolSetting enchantBreach = boolSetting("Breach", true, () -> page.is(Page.MaceEnchants) && mode.is(Mode.Smart) && maceSwapping.getValue());
    private final BoolSetting enchantWindBurst = boolSetting("Wind Burst", true, () -> page.is(Page.MaceEnchants) && mode.is(Mode.Smart) && maceSwapping.getValue());

    private final BoolSetting enchantImpaling = boolSetting("Impaling", true, () -> page.is(Page.OtherEnchants) && mode.is(Mode.Smart) && otherSwapping.getValue());

    private final BoolSetting onlyOnWeapon = boolSetting("Only On Weapon", false, () -> page.is(Page.Weapon) && mode.is(Mode.Smart));
    private final BoolSetting sword = boolSetting("Sword", true, () -> page.is(Page.Weapon) && mode.is(Mode.Smart) && onlyOnWeapon.getValue());
    private final BoolSetting axe = boolSetting("Axe", true, () -> page.is(Page.Weapon) && mode.is(Mode.Smart) && onlyOnWeapon.getValue());
    private final BoolSetting pickaxe = boolSetting("Pickaxe", true, () -> page.is(Page.Weapon) && mode.is(Mode.Smart) && onlyOnWeapon.getValue());
    private final BoolSetting shovel = boolSetting("Shovel", true, () -> page.is(Page.Weapon) && mode.is(Mode.Smart) && onlyOnWeapon.getValue());
    private final BoolSetting hoe = boolSetting("Hoe", true, () -> page.is(Page.Weapon) && mode.is(Mode.Smart) && onlyOnWeapon.getValue());
    private final BoolSetting mace = boolSetting("Mace", true, () -> page.is(Page.Weapon) && mode.is(Mode.Smart) && onlyOnWeapon.getValue());
    private final BoolSetting trident = boolSetting("Trident", true, () -> page.is(Page.Weapon) && mode.is(Mode.Smart) && onlyOnWeapon.getValue());

    private final TimerUtils backTimer = new TimerUtils();
    private boolean awaitingBack;

    @Override
    protected void onDisable() {
        backTimer.reset();
        awaitingBack = false;
    }

    @EventHandler
    private void onAttackEntity(AttackEntityEvent event) {
        if (!canSwapByWeapon()) return;
        performSwap(event.getEntity());
    }

    private void performSwap(Entity target) {
        if (awaitingBack) return;

        int slotIndex;

        if (mode.is(Mode.Simple)) {
            slotIndex = targetSlot.getValue() - 1;
        } else {
            slotIndex = getSmartSlot(target);
        }

        if (slotIndex < 0 || slotIndex > 8) return;
        if (slotIndex == mc.player.getInventory().getSelectedSlot()) return;

        InvUtils.swap(slotIndex, swapBack.getValue());

        awaitingBack = swapBack.getValue();
    }

    @EventHandler
    private void onTick(PlayerTickEvent.Pre event) {
        if (!awaitingBack) return;
        if (backTimer.passedMillise(swapBackDelay.getValue())) {
            InvUtils.swapBack();
            backTimer.reset();
            awaitingBack = false;
        }
    }

    private boolean canSwapByWeapon() {
        if (!onlyOnWeapon.getValue()) return true;
        return InvUtils.testInMainHand(item ->
                (sword.getValue() && item.is(ItemTags.SWORDS)) ||
                        (axe.getValue() && item.is(ItemTags.AXES)) ||
                        (pickaxe.getValue() && item.is(ItemTags.PICKAXES)) ||
                        (shovel.getValue() && item.is(ItemTags.SHOVELS)) ||
                        (hoe.getValue() && item.is(ItemTags.HOES)) ||
                        (mace.getValue() && item.getItem() instanceof MaceItem) ||
                        (trident.getValue() && item.getItem() instanceof TridentItem)
        );
    }

    private int getSmartSlot(Entity target) {
        ItemStack currentStack = mc.player.getMainHandItem();

        if (target != null && smartShieldBreak.getValue() && target instanceof LivingEntity living && living.isBlocking()) {
            if (currentStack.getItem() instanceof AxeItem) return -1;
            int axeSlot = InvUtils.findInHotbar(item -> item.getItem() instanceof AxeItem).slot();
            if (axeSlot != -1) return axeSlot;
        }

        boolean isFalling = mc.player.fallDistance > 1.5;
        boolean durability = smartDurability.getValue();

        boolean isLiving = target instanceof LivingEntity;
        boolean isPlayer = target instanceof Player;
        boolean isOnFire = target != null && target.isOnFire();
        boolean isUndead = target != null && target.is(EntityTypeTags.SENSITIVE_TO_SMITE);
        boolean isArthropod = target != null && target.is(EntityTypeTags.SENSITIVE_TO_BANE_OF_ARTHROPODS);
        boolean isAquatic = target != null && target.is(EntityTypeTags.SENSITIVE_TO_IMPALING);
        boolean hasFireResistance = isLiving && (((LivingEntity) target).hasEffect(MobEffects.FIRE_RESISTANCE) || hasFireProtectionArmor((LivingEntity) target));
        double armor = isLiving ? ((LivingEntity) target).getAttributeValue(Attributes.ARMOR) : 0;
        float health = isLiving ? ((LivingEntity) target).getHealth() : 0;

        int bestSlot = -1;
        double bestScore = getItemScore(currentStack, isFalling, durability, isLiving, isPlayer, isOnFire, hasFireResistance, isUndead, isArthropod, isAquatic, armor, health);

        for (int i = 0; i < 9; i++) {
            if (i == mc.player.getInventory().getSelectedSlot()) continue;

            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty() && !durability) continue;

            double score = getItemScore(stack, isFalling, durability, isLiving, isPlayer, isOnFire, hasFireResistance, isUndead, isArthropod, isAquatic, armor, health);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = i;
            }
        }

        return bestSlot;
    }

    private double getItemScore(ItemStack stack, boolean isFalling, boolean durability, boolean isLiving, boolean isPlayer, boolean isOnFire, boolean hasFireResistance, boolean isUndead, boolean isArthropod, boolean isAquatic, double armor, float health) {
        double score = 0;

        if (durability) {
            score += getDurabilityScore(stack);
        }

        if (stack.isEmpty()) return score;

        score += getCombatScore(stack, isFalling, isLiving, isPlayer, isOnFire, hasFireResistance, isUndead, isArthropod, isAquatic, armor, health);

        return score;
    }

    private double getDurabilityScore(ItemStack stack) {
        if (!stack.isDamageableItem()) return 4;

        int unbreaking = EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.UNBREAKING);
        if (unbreaking > 0) return unbreaking * 0.05;

        return 0;
    }

    private double getCombatScore(ItemStack stack, boolean isFalling, boolean isLiving, boolean isPlayer, boolean isOnFire, boolean hasFireResistance, boolean isUndead, boolean isArthropod, boolean isAquatic, double armor, float health) {
        double score = 0;

        if (swordSwapping.getValue()) {
            score += getFireAspectScore(stack, isOnFire, hasFireResistance);
            score += getLootingScore(stack, isPlayer, isLiving, isOnFire, health);
            score += getSharpnessScore(stack, isOnFire);
            score += getSmiteScore(stack, isUndead, isOnFire);
            score += getBaneOfArthropodsScore(stack, isArthropod, isOnFire);
            score += getSweepingEdgeScore(stack);
        }
        if (maceSwapping.getValue()) {
            score += getBreachScore(stack, isLiving, armor);
            score += getDensityScore(stack, isFalling);
            score += getWindBurstScore(stack, isFalling);
            score += getMaceScore(stack, isFalling);
        }
        if (otherSwapping.getValue()) {
            score += getImpalingScore(stack, isAquatic);
        }

        return score;
    }

    private double getFireAspectScore(ItemStack stack, boolean isOnFire, boolean hasFireResistance) {
        if (!enchantFireAspect.getValue() || isOnFire || hasFireResistance) return 0;
        int level = EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.FIRE_ASPECT);
        return (level > 0) ? 30 : 0;
    }

    private double getLootingScore(ItemStack stack, boolean isPlayer, boolean isLiving, boolean isOnFire, float health) {
        if (!enchantLooting.getValue() || isPlayer) return 0;
        int level = EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.LOOTING);
        if (level > 0) {
            boolean execute = (isLiving && health < 20) || isOnFire;
            return level * (execute ? 10 : 5);
        }
        return 0;
    }

    private double getSharpnessScore(ItemStack stack, boolean isOnFire) {
        if (!enchantSharpness.getValue()) return 0;
        int level = EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.SHARPNESS);
        if (level > 0) {
            double baseScore = (1 + 0.5 * (level - 1)) * 3;
            return isOnFire ? baseScore * 1.5 : baseScore;
        }
        return 0;
    }

    private double getSmiteScore(ItemStack stack, boolean isUndead, boolean isOnFire) {
        if (!enchantSmite.getValue() || !isUndead) return 0;
        int level = EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.SMITE);
        if (level > 0) {
            double baseScore = level * 5;
            return isOnFire ? baseScore * 1.5 : baseScore;
        }
        return 0;
    }

    private double getBaneOfArthropodsScore(ItemStack stack, boolean isArthropod, boolean isOnFire) {
        if (!enchantBaneOfArthropods.getValue() || !isArthropod) return 0;
        int level = EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.BANE_OF_ARTHROPODS);
        if (level > 0) {
            double baseScore = level * 5;
            return isOnFire ? baseScore * 1.5 : baseScore;
        }
        return 0;
    }

    private double getSweepingEdgeScore(ItemStack stack) {
        if (!enchantSweepingEdge.getValue()) return 0;
        int level = EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.SWEEPING_EDGE);
        if (level > 0) {
            return level * 3;
        }
        return 0;
    }

    private double getImpalingScore(ItemStack stack, boolean isAquatic) {
        if (!enchantImpaling.getValue() || !isAquatic) return 0;

        int level = EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.IMPALING);
        if (level > 0) {
            return level * 5;
        }
        return 0;
    }

    private double getBreachScore(ItemStack stack, boolean isLiving, double armor) {
        if (!enchantBreach.getValue() || !isLiving || armor <= 0) return 0;
        int level = EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.BREACH);
        if (level > 0) {
            return level * armor * 0.3;
        }
        return 0;
    }

    private double getDensityScore(ItemStack stack, boolean isFalling) {
        if (!enchantDensity.getValue() || !isFalling) return 0;
        int level = EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.DENSITY);
        if (level > 0) return 50 + (level * mc.player.fallDistance * 2);
        return 0;
    }

    private double getWindBurstScore(ItemStack stack, boolean isFalling) {
        if (!enchantWindBurst.getValue() || !isFalling) return 0;
        int level = EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.WIND_BURST);
        if (level > 0) return level * 20;
        return 0;
    }

    private double getMaceScore(ItemStack stack, boolean isFalling) {
        if (!regularMace.getValue() || !isFalling) return 0;
        if (stack.getItem() instanceof MaceItem) return 40;
        return 0;
    }

    private boolean hasFireProtectionArmor(LivingEntity entity) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (stack.isEmpty()) continue;

            int fireProtection = EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.FIRE_PROTECTION);
            if (fireProtection > 0) return true;
        }
        return false;
    }

}
