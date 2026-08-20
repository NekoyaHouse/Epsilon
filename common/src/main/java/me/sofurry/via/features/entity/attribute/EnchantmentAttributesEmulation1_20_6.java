package me.sofurry.via.features.entity.attribute;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import me.sofurry.via.util.ViaVersionClientEvents;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

public final class EnchantmentAttributesEmulation1_20_6 {

    public static void init() {
        ViaVersionClientEvents.START_LEVEL_TICK.register(world -> {
            if (ProtocolTranslator.getTargetVersion().newerThan(ProtocolVersion.v1_20_5)) {
                return;
            }

            // Update generic attributes for all entities
            for (Entity entity : world.entitiesForRendering()) {
                if (entity.isLocalInstanceAuthoritative() && entity instanceof LivingEntity livingEntity) {
                    setAttribute(livingEntity, Attributes.WATER_MOVEMENT_EFFICIENCY, getEquipmentLevel(Enchantments.DEPTH_STRIDER, livingEntity) / 3D);
                    setGenericMovementEfficiencyAttribute(livingEntity);
                }
            }

            // Update player-specific attributes for all players
            for (Player player : world.players()) {
                if (!player.isLocalInstanceAuthoritative()) {
                    continue;
                }

                final int efficiencyLevel = getEquipmentLevel(Enchantments.EFFICIENCY, player);
                setAttribute(player, Attributes.MINING_EFFICIENCY, efficiencyLevel > 0 ? efficiencyLevel * efficiencyLevel + 1D : 0D);
                setAttribute(player, Attributes.SNEAKING_SPEED, 0.3D + getEquipmentLevel(Enchantments.SWIFT_SNEAK, player) * 0.15D);
                setAttribute(player, Attributes.SUBMERGED_MINING_SPEED, getEquipmentLevel(Enchantments.AQUA_AFFINITY, player) <= 0 ? 0.2D : 1D);
                setAttribute(player, Attributes.ATTACK_KNOCKBACK, getEquipmentLevel(Enchantments.KNOCKBACK, player));
            }
        });
    }

    /**
     * Called from MixinLivingEntity as well to ensure the attribute value is set at the correct place in the entity tick logic.
     * Called above just as a fallback if a mod accesses the raw attribute value directly.
     */
    public static void setGenericMovementEfficiencyAttribute(final LivingEntity entity) {
        final boolean isOnSoulSpeedBlock = entity.level().getBlockState(entity.getBlockPosBelowThatAffectsMyMovement()).is(BlockTags.SOUL_SPEED_BLOCKS);
        setAttribute(entity, Attributes.MOVEMENT_EFFICIENCY, isOnSoulSpeedBlock && getEquipmentLevel(Enchantments.SOUL_SPEED, entity) > 0 ? 1 : 0);
    }

    private static int getEquipmentLevel(final ResourceKey<Enchantment> enchantment, final LivingEntity livingEntity) {
        return EnchantmentHelper.getEnchantmentLevel(livingEntity.level().registryAccess().getOrThrow(enchantment), livingEntity);
    }

    private static void setAttribute(final LivingEntity entity, final Holder<Attribute> attribute, final double value) {
        final AttributeInstance attributeInstance = entity.getAttribute(attribute);
        attributeInstance.removeModifiers(); // Minecraft is applying attribute modifiers in some situations, remove them before we set the base value
        attributeInstance.setBaseValue(value);
    }

}
