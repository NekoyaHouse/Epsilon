package me.sofurry.via.injection.mixin.features.world.entity_distance;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(ClientLevel.class)
public abstract class MixinClientLevel {

    @ModifyConstant(method = "doAddParticle", constant = @Constant(doubleValue = 1024.0))
    private double lowerParticleRenderDistance(double constant) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_8)) {
            return 256.0;
        } else {
            return constant;
        }
    }

}
