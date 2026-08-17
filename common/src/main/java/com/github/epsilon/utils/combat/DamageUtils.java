package com.github.epsilon.utils.combat;

import com.github.epsilon.utils.player.EnchantmentUtils;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import static com.github.epsilon.Constants.mc;

/**
 * 用于计算实体所受爆炸伤害的工具类。
 * 客户端计算流程与原版 {@code ServerExplosion}、{@code ExplosionDamageCalculator}、
 * {@code CombatRules} 以及
 * {@code LivingEntity.getDamageAfterArmorAbsorb/getDamageAfterMagicAbsorb} 保持一致。
 */
public class DamageUtils {

    /**
     * {@code EndCrystal.hurtServer} 定义的末影水晶爆炸半径。
     */
    public static final float CRYSTAL_EXPLOSION_RADIUS = 6.0f;

    /**
     * {@code RespawnAnchorBlock.explode} 定义的重生锚爆炸半径。
     */
    public static final float ANCHOR_EXPLOSION_RADIUS = 5.0f;

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    // ── public API ──────────────────────────────────────────────────────────

    /**
     * 估算末影水晶爆炸对目标造成的最终伤害。
     *
     * @param target     目标实体
     * @param crystalPos 末影水晶爆炸中心
     * @param targetPos  目标预测位置；为 null 时使用实体当前位置
     * @param mode       护甲附魔计算模式
     * @return 应用全部减免后的估算伤害，最小为 0
     */
    public static float crystalDamage(LivingEntity target, Vec3 crystalPos, Vec3 targetPos, ArmorEnchantmentMode mode) {
        return explosionDamage(target, crystalPos, CRYSTAL_EXPLOSION_RADIUS, targetPos, mode);
    }

    /**
     * 估算重生锚爆炸对目标造成的最终伤害。
     *
     * @param target    目标实体
     * @param anchorPos 重生锚爆炸中心
     * @param mode      护甲附魔计算模式
     * @return 应用全部减免后的估算伤害，最小为 0
     */
    public static float anchorDamage(LivingEntity target, Vec3 anchorPos, ArmorEnchantmentMode mode) {
        return explosionDamage(target, anchorPos, ANCHOR_EXPLOSION_RADIUS, null, mode);
    }

    /**
     * 估算指定爆炸对目标造成的最终伤害。
     *
     * @param target       目标实体
     * @param explosionPos 爆炸中心
     * @param radius       爆炸或特效半径
     * @param targetPos    目标预测位置；为 null 时使用实体当前位置
     * @param mode         护甲附魔计算模式
     * @return 应用全部减免后的估算伤害，最小为 0
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
     * 计算尚未应用护甲等减免的原始爆炸伤害。
     *
     * @param target       目标实体
     * @param explosionPos 爆炸中心
     * @param radius       爆炸或特效半径
     * @return 未应用减免的估算伤害，最小为 0
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
     * 计算爆炸中心对目标包围盒的无遮挡采样比例。
     *
     * @param center 爆炸中心
     * @param entity 实体
     * @return 范围为 0 到 1 的无遮挡比例
     */
    public static float getSeenPercent(Vec3 center, LivingEntity entity) {
        return getSeenPercent(center, entity.getBoundingBox(), entity);
    }

    /**
     * 计算爆炸中心对目标包围盒的无遮挡采样比例。
     *
     * @param center 爆炸中心
     * @param bb     用于采样的实体包围盒
     * @param entity 实体
     * @return 范围为 0 到 1 的无遮挡比例
     */
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

    // ── 难度缩放 ───────────────────────────────────────────────────────────

    /**
     * 复现原版对玩家应用的难度伤害缩放。
     * 爆炸伤害类型使用 {@code DamageScaling.ALWAYS}，因此始终按难度缩放：
     * <ul>
     *   <li>和平：0</li>
     *   <li>简单：min(伤害 / 2 + 1, 伤害)</li>
     *   <li>普通：伤害不变</li>
     *   <li>困难：伤害乘以 1.5</li>
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

    // ── 护甲减免 ───────────────────────────────────────────────────────────

    /**
     * 在客户端复现 {@code CombatRules.getDamageAfterAbsorb}。
     * 爆炸伤害没有 {@code BYPASSES_ARMOR} 标签，因此需要应用护甲减免。
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

    // ── 抗性提升效果 ───────────────────────────────────────────────────────

    /**
     * 复现 {@code LivingEntity.getDamageAfterMagicAbsorb} 中抗性提升效果的减伤。
     * 每级抗性提升减少 20% 伤害。
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

    // ── 附魔保护 ───────────────────────────────────────────────────────────

    /**
     * 通过直接读取护甲附魔，在客户端估算针对爆炸的附魔保护值。
     * <p>
     * 原版数据包中 {@code Enchantments} 定义的数值：
     * <ul>
     *   <li>{@code Protection}：每级增加 1，适用于全部伤害</li>
     *   <li>{@code Blast Protection}：每级增加 2，适用于爆炸伤害</li>
     * </ul>
     * 总保护值限制在 [0, 20]，再按
     * {@code CombatRules.getDamageAfterMagicAbsorb} 应用。
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
     * 估算末影水晶爆炸对本地玩家造成的最终伤害。
     *
     * @param crystalPos 末影水晶爆炸中心
     * @param mode       护甲附魔计算模式
     * @return 本地玩家预计受到的伤害，最小为 0
     */
    public static float selfCrystalDamage(Vec3 crystalPos, ArmorEnchantmentMode mode) {
        return selfCrystalDamage(crystalPos, null, mode);
    }

    /**
     * 估算末影水晶爆炸对本地玩家造成的最终伤害。
     *
     * @param crystalPos 末影水晶爆炸中心
     * @param selfPos    本地玩家预测位置；为 null 时使用当前位置
     * @param mode       护甲附魔计算模式
     * @return 本地玩家预计受到的伤害，最小为 0
     */
    public static float selfCrystalDamage(Vec3 crystalPos, Vec3 selfPos, ArmorEnchantmentMode mode) {
        if (mc.player == null) return 0f;
        return crystalDamage(mc.player, crystalPos, selfPos, mode);
    }

    /**
     * 根据预测位置构造实体的受限包围盒。
     *
     * @param entity 实体
     * @param pos    目标位置
     * @return 以预测位置为中心构造的实体包围盒
     */
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

    public static boolean breakCrosshairCrystal() {
        if (mc.level == null || mc.player == null || mc.getCameraEntity() == null) return false;

        Vec3 eye = mc.player.getEyePosition();
        Vec3 look = mc.player.getLookAngle();
        Vec3 end = eye.add(look.scale(4.0));

        BlockPos placePos = null;
        for (double d = 0.1; d <= 4.0; d += 0.1) {
            Vec3 point = eye.add(look.scale(d));
            BlockPos pos = BlockPos.containing(point);
            BlockState state = mc.level.getBlockState(pos);
            if (state.isAir() || state.canBeReplaced()) {
                placePos = pos;
                break;
            }
        }

        if (placePos == null) return false;

        net.minecraft.world.entity.boss.enderdragon.EndCrystal target = null;
        double bestDistSq = Double.MAX_VALUE;
        AABB searchBox = new AABB(eye, end).inflate(0.5);
        for (net.minecraft.world.entity.boss.enderdragon.EndCrystal crystal : mc.level.getEntitiesOfClass(net.minecraft.world.entity.boss.enderdragon.EndCrystal.class, searchBox)) {
            if (!crystal.isAlive()) continue;
            Vec3 toCrystal = crystal.position().subtract(eye);
            double projection = toCrystal.dot(look);
            if (projection < 0 || projection > 4.0) continue;
            Vec3 closest = eye.add(look.scale(projection));
            if (closest.distanceToSqr(crystal.position()) > 1.5 * 1.5) continue;
            double distSq = eye.distanceToSqr(crystal.position());
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                target = crystal;
            }
        }

        if (target != null) {
            mc.gameMode.attack(mc.player, target);
            mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            return true;
        }
        return false;
    }

    public enum ArmorEnchantmentMode {
        None,
        PPPP,
        PPBP,
    }

}
