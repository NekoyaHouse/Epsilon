package me.sofurry.via.injection.mixin.features.entity.dimensions;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.world.entity.animal.dolphin.Dolphin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Dolphin.class)
public abstract class MixinDolphin {

    @Redirect(method = "getDefaultDimensions", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/dolphin/Dolphin;isBaby()Z"))
    private boolean dontChangeScale(Dolphin instance) {
        return ProtocolTranslator.getTargetVersion().newerThan(ProtocolVersion.v26_1) && instance.isBaby();
    }

}
