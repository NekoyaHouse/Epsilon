package me.sofurry.via.injection.mixin.features.block.mining_calculation;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.raphimc.vialegacy.api.LegacyProtocolVersion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class MixinPlayer extends LivingEntity {

    @Shadow
    public abstract boolean hasCorrectToolForDrops(BlockState state);

    @Shadow
    @Final
    private Inventory inventory;

    protected MixinPlayer(EntityType<? extends LivingEntity> entityType, Level world) {
        super(entityType, world);
    }

    @Redirect(method = {"getDestroySpeed(Lnet/minecraft/world/level/block/state/BlockState;)F", "getDestroySpeed(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)F"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;hasEffect(Lnet/minecraft/core/Holder;)Z"))
    private boolean changeSpeedCalculation(Player instance, Holder<MobEffect> statusEffect, @Local(name = "speed") LocalFloatRef f) {
        final boolean hasMiningFatigue = instance.hasEffect(statusEffect);
        if (hasMiningFatigue && ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_7_6)) {
            f.set(f.get() * (1.0F - (this.getEffect(MobEffects.MINING_FATIGUE).getAmplifier() + 1) * 0.2F));
            if (f.get() < 0) {
                f.set(0);
            }
            return false; // disable original code
        }
        return hasMiningFatigue;
    }

    @Inject(method = {"getDestroySpeed(Lnet/minecraft/world/level/block/state/BlockState;)F", "getDestroySpeed(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)F"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/effect/MobEffectUtil;hasDigSpeed(Lnet/minecraft/world/entity/LivingEntity;)Z"))
    private void changeSpeedCalculation(CallbackInfoReturnable<Float> cir, @Local(argsOnly = true) BlockState block, @Local(name = "speed") LocalFloatRef f) {
        final float efficiency = (float) this.getAttributeValue(Attributes.MINING_EFFICIENCY);
        if (efficiency <= 0) {
            return;
        }

        final float speed = this.inventory.getSelectedItem().getDestroySpeed(block);
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(LegacyProtocolVersion.r1_4_4tor1_4_5) && this.hasCorrectToolForDrops(block)) {
            f.set(speed + efficiency);
        } else if (speed > 1F || ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(LegacyProtocolVersion.r1_4_6tor1_4_7)) {
            if (!this.getMainHandItem().isEmpty()) {
                if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_7_6)) {
                    if (speed <= 1.0 && !this.hasCorrectToolForDrops(block)) {
                        f.set(speed + efficiency * 0.08F);
                    } else {
                        f.set(speed + efficiency);
                    }
                }
            }
        }
    }

}
