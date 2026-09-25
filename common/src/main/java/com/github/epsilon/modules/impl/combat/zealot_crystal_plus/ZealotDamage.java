package com.github.epsilon.modules.impl.combat.zealot_crystal_plus;

import com.github.epsilon.modules.impl.combat.PacketMine;
import com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotSnapshot.SelfSnapshot;
import com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotSnapshot.SettingsSnapshot;
import com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotSnapshot.TargetSnapshot;
import com.github.epsilon.utils.combat.DamageUtils;
import com.github.epsilon.utils.player.EnchantmentUtils;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * 水晶爆炸伤害估算。
 * <p>
 * 这些计算必须与 Minecraft 主线程读取的世界状态保持一致：{@link ResistantBlockCache} 会缓存方块抗性，
 * 因此只能在一次快照的生命周期内复用。
 */
final class ZealotDamage {

    private ZealotDamage() {
    }

    /**
     * 计算实体在指定水晶位置承受的爆炸伤害，包含护甲、抗性提升和爆炸保护减免。
     */
    static float calcDamage(Vec3 entityPos, AABB entityBox, DamageReductionData reduction, boolean playerEntity,
                            Difficulty difficulty, Vec3 crystalPos, ResistantBlockCache resistantBlocks) {
        if (playerEntity && difficulty == Difficulty.PEACEFUL) {
            return 0.0f;
        }

        float damage;
        BlockPos supportPos = BlockPos.containing(crystalPos.x, crystalPos.y - 1.0, crystalPos.z);
        if (playerEntity && crystalPos.y - entityPos.y > 1.5652173822904127 && resistantBlocks.isResistant(supportPos.asLong())) {
            damage = 1.0f;
        } else {
            damage = calcRawDamage(entityPos, entityBox, crystalPos, resistantBlocks);
        }

        if (playerEntity) {
            damage = ZealotMath.applyDifficultyDamage(difficulty, damage);
        }
        return reduction.apply(damage);
    }

    static float calcDamage(SelfSnapshot self, Vec3 crystalPos, ResistantBlockCache resistantBlocks) {
        return calcDamage(self.pos(), self.box(), self.reduction(), true, self.difficulty(), crystalPos, resistantBlocks);
    }

    static float calcDamage(TargetSnapshot target, Vec3 crystalPos, ResistantBlockCache resistantBlocks, Difficulty difficulty) {
        return calcDamage(target.pos(), target.box(), target.reduction(), target.player(), difficulty, crystalPos, resistantBlocks);
    }

    /**
     * 原版爆炸伤害公式：按距离衰减后乘以暴露度。
     */
    private static float calcRawDamage(Vec3 entityPos, AABB entityBox, Vec3 crystalPos, ResistantBlockCache resistantBlocks) {
        float scaledDist = (float) (entityPos.distanceTo(crystalPos) / ZealotMath.doubleSize());
        if (scaledDist > 1.0f) return 0.0f;

        float factor = (1.0f - scaledDist) * getExposureAmount(entityBox, crystalPos, resistantBlocks);
        return ((factor * factor + factor) * ZealotMath.DAMAGE_FACTOR + 1.0f);
    }

    /**
     * 按原版采样网格估算实体朝向爆炸点的可见比例。
     */
    private static float getExposureAmount(AABB entityBox, Vec3 explosionPos, ResistantBlockCache resistantBlocks) {
        double width = entityBox.maxX - entityBox.minX;
        double height = entityBox.maxY - entityBox.minY;
        double gridMultiplierXZ = 1.0 / (width * 2.0 + 1.0);
        double gridMultiplierY = 1.0 / (height * 2.0 + 1.0);
        double gridXZ = width * gridMultiplierXZ;
        double gridY = height * gridMultiplierY;
        int sizeXZ = Mth.floor(1.0 / gridMultiplierXZ);
        int sizeY = Mth.floor(1.0 / gridMultiplierY);
        double xzOffset = (1.0 - gridMultiplierXZ * sizeXZ) / 2.0;

        int total = 0;
        int count = 0;
        for (int yIndex = 0; yIndex <= sizeY; yIndex++) {
            for (int xIndex = 0; xIndex <= sizeXZ; xIndex++) {
                for (int zIndex = 0; zIndex <= sizeXZ; zIndex++) {
                    double x = gridXZ * xIndex + xzOffset + entityBox.minX;
                    double y = gridY * yIndex + entityBox.minY;
                    double z = gridXZ * zIndex + xzOffset + entityBox.minZ;
                    total++;
                    if (!rayTraceResistant(new Vec3(x, y, z), explosionPos, resistantBlocks)) {
                        count++;
                    }
                }
            }
        }
        return total == 0 ? 0.0f : count / (float) total;
    }

    /**
     * 体素 DDA：判断采样点到爆炸点之间是否存在抗性方块。
     */
    private static boolean rayTraceResistant(Vec3 from, Vec3 to, ResistantBlockCache resistantBlocks) {
        int x = Mth.floor(from.x);
        int y = Mth.floor(from.y);
        int z = Mth.floor(from.z);
        int endX = Mth.floor(to.x);
        int endY = Mth.floor(to.y);
        int endZ = Mth.floor(to.z);

        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;

        int stepX = dx > 0.0 ? 1 : (dx < 0.0 ? -1 : 0);
        int stepY = dy > 0.0 ? 1 : (dy < 0.0 ? -1 : 0);
        int stepZ = dz > 0.0 ? 1 : (dz < 0.0 ? -1 : 0);

        double tMaxX = ZealotMath.intBound(from.x, dx);
        double tMaxY = ZealotMath.intBound(from.y, dy);
        double tMaxZ = ZealotMath.intBound(from.z, dz);
        double tDeltaX = stepX == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dx);
        double tDeltaY = stepY == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dy);
        double tDeltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dz);

        for (int i = 0; i < 200; i++) {
            if (resistantBlocks.isResistant(BlockPos.asLong(x, y, z))) {
                return true;
            }
            if (x == endX && y == endY && z == endZ) {
                break;
            }

            if (tMaxX < tMaxY) {
                if (tMaxX < tMaxZ) {
                    x += stepX;
                    tMaxX += tDeltaX;
                } else {
                    z += stepZ;
                    tMaxZ += tDeltaZ;
                }
            } else {
                if (tMaxY < tMaxZ) {
                    y += stepY;
                    tMaxY += tDeltaY;
                } else {
                    z += stepZ;
                    tMaxZ += tDeltaZ;
                }
            }
        }

        return false;
    }

    static boolean isResistantState(BlockState state) {
        return state.is(Blocks.BEDROCK)
                || state.is(Blocks.OBSIDIAN)
                || state.is(Blocks.CRYING_OBSIDIAN)
                || state.is(Blocks.ENDER_CHEST)
                || state.is(Blocks.RESPAWN_ANCHOR)
                || state.is(Blocks.ENCHANTING_TABLE)
                || state.is(Blocks.ANVIL)
                || state.is(Blocks.CHIPPED_ANVIL)
                || state.is(Blocks.DAMAGED_ANVIL)
                || state.is(Blocks.NETHERITE_BLOCK);
    }

    /**
     * 是否应当切换到“强攻”阈值：目标残血、移动过快或护甲即将破碎。
     */
    static boolean shouldForcePlace(SelfSnapshot self, TargetSnapshot target, SettingsSnapshot settings) {
        return (!settings.forcePlaceWhileSwording() || !self.swording())
                && (target.totalHealth() <= settings.forcePlaceHealth()
                || target.speed() >= settings.forcePlaceMotion()
                || target.minArmorRate() <= settings.forcePlaceArmorRate());
    }

    /**
     * 目标/自身的伤害减免数据，来自护甲、盔甲韧性和保护类附魔。
     */
    static final class DamageReductionData {
        private final float armorValue;
        private final float toughness;
        private final float resistanceMultiplier;
        private final float blastReduction;

        private DamageReductionData(float armorValue, float toughness, float resistanceMultiplier, float blastReduction) {
            this.armorValue = armorValue;
            this.toughness = toughness;
            this.resistanceMultiplier = resistanceMultiplier;
            this.blastReduction = blastReduction;
        }

        static DamageReductionData fromEntity(LivingEntity entity, DamageUtils.ArmorEnchantmentMode armorMode) {
            float armorValue = (float) entity.getAttributeValue(Attributes.ARMOR);
            float toughness = (float) entity.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
            float resistanceMultiplier = 1.0f;
            if (entity.hasEffect(MobEffects.RESISTANCE) && entity.getEffect(MobEffects.RESISTANCE) != null) {
                resistanceMultiplier = Math.max(1.0f - (entity.getEffect(MobEffects.RESISTANCE).getAmplifier() + 1) * 0.2f, 0.0f);
            }

            int epf = switch (armorMode) {
                case PPPP -> 16;
                case PPBP -> 20;
                case None -> {
                    int actualEpf = 0;
                    for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                        ItemStack stack = entity.getItemBySlot(slot);
                        actualEpf += EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.PROTECTION);
                        actualEpf += EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.BLAST_PROTECTION) * 2;
                    }
                    yield actualEpf;
                }
            };

            float blastReduction = 1.0f - Math.min(epf, 20) / 25.0f;
            return new DamageReductionData(armorValue, toughness, resistanceMultiplier, blastReduction);
        }

        private float apply(float damage) {
            float toughnessFactor = 2.0f + toughness / 4.0f;
            float effectiveArmor = Mth.clamp(armorValue - damage / toughnessFactor, armorValue * 0.2f, 20.0f);
            float afterArmor = damage * (1.0f - effectiveArmor / 25.0f);
            return afterArmor * resistanceMultiplier * blastReduction;
        }
    }

    /**
     * 抗性方块缓存，避免在伤害估算的密集采样中重复读取世界方块状态。
     * <p>
     * 只有 {@code level == null} 的 {@link #EMPTY} 可以跨帧复用；其余实例绑定单次快照。
     */
    static final class ResistantBlockCache {
        static final ResistantBlockCache EMPTY = new ResistantBlockCache(null, false);

        private final Level level;
        private final boolean assumeInstantMine;
        private final Map<Long, Boolean> cache = new HashMap<>();

        ResistantBlockCache(Level level, boolean assumeInstantMine) {
            this.level = level;
            this.assumeInstantMine = assumeInstantMine;
        }

        boolean isResistant(long posLong) {
            Boolean cached = cache.get(posLong);
            if (cached != null) return cached;
            boolean resistant = false;
            if (level != null) {
                BlockPos pos = BlockPos.of(posLong);
                BlockState state = level.getBlockState(pos);
                resistant = isResistantState(state)
                        && (!assumeInstantMine || !PacketMine.INSTANCE.isInstantMining(pos));
            }
            cache.put(posLong, resistant);
            return resistant;
        }
    }
}
