package me.sofurry.via.injection.mixin.features.entity.dimensions;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Rabbit.class)
public abstract class MixinRabbit {

    @Redirect(method = "getDefaultDimensions", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/rabbit/Rabbit;isBaby()Z"))
    private boolean dontChangeScale(Rabbit instance) {
        return ProtocolTranslator.getTargetVersion().newerThan(ProtocolVersion.v1_21_11) && instance.isBaby();
    }

}
