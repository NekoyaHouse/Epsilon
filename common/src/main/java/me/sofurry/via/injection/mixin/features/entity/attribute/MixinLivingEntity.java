package me.sofurry.via.injection.mixin.features.entity.attribute;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.features.entity.attribute.EnchantmentAttributesEmulation1_20_6;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity {

    @Inject(method = "getBlockSpeedFactor", at = @At("HEAD"))
    private void setGenericMovementEfficiencyAttribute(CallbackInfoReturnable<Float> cir) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_20_5)) {
            EnchantmentAttributesEmulation1_20_6.setGenericMovementEfficiencyAttribute((LivingEntity) (Object) this);
        }
    }

}
