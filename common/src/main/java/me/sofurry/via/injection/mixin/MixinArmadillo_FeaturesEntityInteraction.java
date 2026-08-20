package me.sofurry.via.injection.mixin;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Armadillo.class)
public abstract class MixinArmadillo_FeaturesEntityInteraction {

    @Redirect(method = "mobInteract", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/armadillo/Armadillo;isScared()Z"))
    private boolean changeCondition(Armadillo instance) {
        return ProtocolTranslator.getTargetVersion().newerThan(ProtocolVersion.v1_20_5) && instance.isScared();
    }

}
