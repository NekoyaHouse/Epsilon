package com.github.epsilon.utils.combat;

import com.github.epsilon.utils.player.EnchantmentUtils;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import static com.github.epsilon.Constants.mc;

/**
 * Utility class for calculating explosion damage dealt to entities.
 * Mirrors the vanilla {@code ServerExplosion} + {@code ExplosionDamageCalculator} +
 * {@code CombatRules} + {@code LivingEntity.getDamageAfterArmorAbsorb/getDamageAfterMagicAbsorb}
 * pipeline on the client side.
 */
public class DamageUtils {

    /**
     * End Crystal explosion radius as defined in {@code EndCrystal.hurtServer}.
     */
    public static final float CRYSTAL_EXPLOSION_RADIUS = 6.0f;

    /**
     * Respawn Anchor explosion radius as defined in {@code RespawnAnchorBlock.explode}.
     */
    public static final float ANCHOR_EXPLOSION_RADIUS = 5.0f;

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    // ── public API ──────────────────────────────────────────────────────────

    /**
     * Calculates the damage an End Crystal exploding at {@code crystalPos} would
     * deal to {@code target}, taking into account exposure, difficulty, armor,
     * enchantments, the Resistance effect and absorption.
     *
     * @param target     the entity that would be damaged
     * @param crystalPos the position of the End Crystal (explosion center)
     * @param mode       the armor enchantment mode to apply for calculate
     * @return estimated damage after all reductions (≥ 0)
     */
    public static float crystalDamage(LivingEntity target, Vec3 crystalPos, Vec3 targetPos, ArmorEnchantmentMode mode) {
        return explosionDamage(target, crystalPos, CRYSTAL_EXPLOSION_RADIUS, targetPos, mode);
    }

    /**
     * Calculates the damage a Respawn Anchor exploding at {@code anchorPos} would
     * deal to {@code target}, taking into account exposure, difficulty, armor,
     * enchantments, the Resistance effect and absorption.
     *
     * @param target    the entity that would be damaged
     * @param anchorPos the position of the Respawn Anchor (explosion center)
     * @param mode      the armor enchantment mode to apply for calculate
     * @return estimated damage after all reductions (≥ 0)
     */
    public static float anchorDamage(LivingEntity target, Vec3 anchorPos, ArmorEnchantmentMode mode) {
        return explosionDamage(target, anchorPos, ANCHOR_EXPLOSION_RADIUS, null, mode);
    }

    /**
     * Calculates the damage an explosion with a given {@code radius} at
     * {@code explosionPos} would deal to {@code target}.
     *
     * @param target       the entity that would be damaged
     * @param explosionPos the center of the explosion
     * @param radius       the explosion radius (e.g. 6.0 for End Crystals)
     * @param mode         the armor enchantment mode to apply for calculate
     * @return estimated damage after all reductions (≥ 0)
     */
    public static float explosionDamage(LivingEntity target, Vec3 explosionPos, float radius, Vec3 targetPos, ArmorEnchantmentMode mode) {
        if (target.isInvulnerable()) return 0f;

        float doubleRadius = radius * 2.0f;
        Vec3 entityPos = targetPos != null ? targetPos : target.position();
        double dist = entityPos.distanceTo(explosionPos) / doubleRadius;
        if (dist > 1.0) return 0f;

        AABB box = targetPos != null ? getPredictedBoundingBox(target, targetPos) : target.getBoundingBox();
        float exposure = getSeenPercent(explosionPos, box, target);
        if (exposure <= 0f) return 0f;

        double impact = (1.0 - dist) * exposure;
        float baseDamage = (float) ((impact * impact + impact) / 2.0 * 7.0 * doubleRadius + 1.0);

        if (target instanceof Player player) {
            baseDamage = applyDifficultyScaling(baseDamage, player);
        }

        float afterArmor = applyArmorReduction(target, baseDamage);
        float afterResistance = applyResistanceReduction(target, afterArmor);
        float afterEnchants = applyEnchantmentReduction(target, afterResistance, mode);

        return Math.max(0f, afterEnchants);
    }

    /**
     * Returns the raw (pre-reduction) explosion damage that would be dealt, useful
     * for comparing crystal placements without the cost of reading entity equipment.
     */
    public static float rawExplosionDamage(LivingEntity target, Vec3 explosionPos, float radius) {
        float doubleRadius = radius * 2.0f;
        double dist = Math.sqrt(target.distanceToSqr(explosionPos)) / doubleRadius;
        if (dist > 1.0) return 0f;

        float exposure = getSeenPercent(explosionPos, target);
        if (exposure <= 0f) return 0f;

        double impact = (1.0 - dist) * exposure;
        return (float) ((impact * impact + impact) / 2.0 * 7.0 * doubleRadius + 1.0);
    }

    // ── exposure (seen percent) ─────────────────────────────────────────────

    /**
     * Re-implementation of {@code ServerExplosion.getSeenPercent} that works on the
     * client level. Traces rays from sub-samples of the entity bounding box to the
     * explosion center and returns the fraction that are unobstructed.
     */
    public static float getSeenPercent(Vec3 center, LivingEntity entity) {
        return getSeenPercent(center, entity.getBoundingBox(), entity);
    }

    public static float getSeenPercent(Vec3 center, AABB bb, LivingEntity entity) {
        double xs = 1.0 / ((bb.maxX - bb.minX) * 2.0 + 1.0);
        double ys = 1.0 / ((bb.maxY - bb.minY) * 2.0 + 1.0);
        double zs = 1.0 / ((bb.maxZ - bb.minZ) * 2.0 + 1.0);
        double xOffset = (1.0 - Math.floor(1.0 / xs) * xs) / 2.0;
        double zOffset = (1.0 - Math.floor(1.0 / zs) * zs) / 2.0;

        if (xs < 0.0 || ys < 0.0 || zs < 0.0) return 0.0f;

        int hits = 0;
        int total = 0;

        for (double xx = 0.0; xx <= 1.0; xx += xs) {
            for (double yy = 0.0; yy <= 1.0; yy += ys) {
                for (double zz = 0.0; zz <= 1.0; zz += zs) {
                    double x = Mth.lerp(xx, bb.minX, bb.maxX);
                    double y = Mth.lerp(yy, bb.minY, bb.maxY);
                    double z = Mth.lerp(zz, bb.minZ, bb.maxZ);
                    Vec3 from = new Vec3(x + xOffset, y, z + zOffset);

                    if (mc.level.clip(new ClipContext(
                            from, center,
                            ClipContext.Block.COLLIDER,
                            ClipContext.Fluid.NONE,
                            entity
                    )).getType() == HitResult.Type.MISS) {
                        hits++;
                    }
                    total++;
                }
            }
        }

        return (float) hits / total;
    }

    // ── difficulty scaling ──────────────────────────────────────────────────

    /**
     * Mirrors the vanilla difficulty-based damage scaling applied to players.
     * Explosion damage type uses {@code DamageScaling.ALWAYS}, meaning it always
     * scales with difficulty:
     * <ul>
     *   <li>Peaceful → 0</li>
     *   <li>Easy → min(damage / 2 + 1, damage)</li>
     *   <li>Normal → damage (unchanged)</li>
     *   <li>Hard → damage × 1.5</li>
     * </ul>
     */
    private static float applyDifficultyScaling(float damage, Player player) {
        Difficulty difficulty = player.level().getDifficulty();
        return switch (difficulty) {
            case PEACEFUL -> 0f;
            case EASY -> Math.min(damage / 2.0f + 1.0f, damage);
            case NORMAL -> damage;
            case HARD -> damage * 1.5f;
        };
    }

    // ── armor reduction ─────────────────────────────────────────────────────

    /**
     * Client-side mirror of {@code CombatRules.getDamageAfterAbsorb}.
     * Explosion damage is NOT tagged {@code BYPASSES_ARMOR}, so armor applies.
     */
    private static float applyArmorReduction(LivingEntity target, float damage) {
        float totalArmor = (float) target.getAttributeValue(Attributes.ARMOR);
        float armorToughness = (float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS);

        // CombatRules.getDamageAfterAbsorb (without weapon-based armor piercing)
        float toughness = 2.0f + armorToughness / 4.0f;
        float effectiveArmor = Mth.clamp(totalArmor - damage / toughness, totalArmor * 0.2f, 20.0f);
        float armorFraction = effectiveArmor / 25.0f;
        return damage * (1.0f - armorFraction);
    }

    // ── Resistance effect ───────────────────────────────────────────────────

    /**
     * Mirrors the Resistance potion effect reduction from
     * {@code LivingEntity.getDamageAfterMagicAbsorb}.
     * Each level of Resistance reduces damage by 20%.
     */
    private static float applyResistanceReduction(LivingEntity target, float damage) {
        if (target.hasEffect(MobEffects.RESISTANCE)) {
            int amplifier = target.getEffect(MobEffects.RESISTANCE).getAmplifier();
            int reduction = (amplifier + 1) * 5; // 5 per level
            int remaining = 25 - reduction;
            float reduced = damage * remaining;
            damage = Math.max(reduced / 25.0f, 0.0f);
        }
        return damage;
    }

    // ── enchantment protection ──────────────────────────────────────────────

    /**
     * Estimates enchantment-based explosion protection on the client side by
     * reading armor item enchantments directly.
     * <p>
     * Vanilla values (from {@code Enchantments} data-pack definitions):
     * <ul>
     *   <li>{@code Protection}: +1 per level (applies to all damage)</li>
     *   <li>{@code Blast Protection}: +2 per level (applies to explosion damage)</li>
     * </ul>
     * The total is clamped to [0, 20] and applied via
     * {@code CombatRules.getDamageAfterMagicAbsorb}.
     */
    private static float applyEnchantmentReduction(LivingEntity target, float damage, ArmorEnchantmentMode mode) {
        float totalProtection = 0f;

        switch (mode) {
            case None -> {
                for (EquipmentSlot slot : ARMOR_SLOTS) {
                    ItemStack stack = target.getItemBySlot(slot);
                    if (stack.isEmpty()) continue;

                    int protLevel = EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.PROTECTION);
                    int blastLevel = EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.BLAST_PROTECTION);

                    totalProtection += protLevel * 1.0f;   // Protection: 1 per level
                    totalProtection += blastLevel * 2.0f;   // Blast Protection: 2 per level
                }
            }
            case PPPP -> {
                totalProtection += 4 * 1.0f * 4;
            }
            case PPBP -> {
                totalProtection += 4 * 1.0f * 3;
                totalProtection += 4 * 2.0f * 1;
            }
        }

        // CombatRules.getDamageAfterMagicAbsorb
        float clamped = Mth.clamp(totalProtection, 0.0f, 20.0f);
        return damage * (1.0f - clamped / 25.0f);
    }

    // ── helper: self-damage shortcut ────────────────────────────────────────

    /**
     * Shortcut: calculates how much crystal damage the local player would take.
     */
    public static float selfCrystalDamage(Vec3 crystalPos, ArmorEnchantmentMode mode) {
        return selfCrystalDamage(crystalPos, null, mode);
    }

    public static float selfCrystalDamage(Vec3 crystalPos, Vec3 selfPos, ArmorEnchantmentMode mode) {
        if (mc.player == null) return 0f;
        return crystalDamage(mc.player, crystalPos, selfPos, mode);
    }

    public static AABB getPredictedBoundingBox(LivingEntity entity, Vec3 pos) {
        float width = entity.getBbWidth();
        float height = entity.getBbHeight();
        double halfWidth = Math.min(width, 2.0f) / 2.0;
        double clampedHeight = Math.min(height, 3.0);

        return new AABB(
                pos.x - halfWidth, pos.y, pos.z - halfWidth,
                pos.x + halfWidth, pos.y + clampedHeight, pos.z + halfWidth
        );
    }

    private DamageUtils() {
    }

    public enum ArmorEnchantmentMode {
        None,
        PPPP,   // Protection 4 x4
        PPBP,   // Protection 4 x2 + Blast Protection 4 x1 + Protection 4 x1
    }
}

