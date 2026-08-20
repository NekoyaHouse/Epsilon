package me.sofurry.via.injection.mixin;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.platform.ViaVersionPlatform;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Lithium is overriding the sorting with an optimized implementation
// https://github.com/CaffeineMC/lithium-fabric/blob/11892b8c0f0f14e81c3384510b0b2ac7293ca237/common/src/main/java/net/caffeinemc/mods/lithium/mixin/entity/collisions/movement/EntityMixin.java#L84
@Mixin(value = Entity.class, priority = 1001 /* Lithium has to be applied first */)
public abstract class MixinEntity_CompatLithium {

    @SuppressWarnings({"MixinAnnotationTarget", "UnresolvedMixinReference"})
    @Redirect(method = "lithium$CollideMovement", at = @At(value = "INVOKE", target = "Ljava/lang/Math;abs(D)D", ordinal = 0), remap = false, require = 0)
    private static double alwaysSortYXZ(double a) {
        if (!ViaVersionPlatform.isModLoaded("moonrise") && ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_13_2)) {
            return Double.MAX_VALUE;
        } else {
            return Math.abs(a);
        }
    }

}
