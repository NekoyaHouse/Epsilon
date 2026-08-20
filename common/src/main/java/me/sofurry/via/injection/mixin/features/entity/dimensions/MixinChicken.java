package me.sofurry.via.injection.mixin.features.entity.dimensions;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.animal.chicken.Chicken;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Chicken.class)
public abstract class MixinChicken {

    @Shadow
    @Final
    private static EntityDimensions BABY_DIMENSIONS;

    @Unique
    private static final EntityDimensions viaVersion$baby_dimensions_r26_1 = EntityDimensions.scalable(0.3F, 0.4F).withEyeHeight(0.28F);

    @Redirect(method = "getDefaultDimensions", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/animal/chicken/Chicken;BABY_DIMENSIONS:Lnet/minecraft/world/entity/EntityDimensions;", opcode = Opcodes.GETSTATIC))
    private EntityDimensions changeBabyDimensions() {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v26_1)) {
            return viaVersion$baby_dimensions_r26_1;
        } else {
            return BABY_DIMENSIONS;
        }
    }

}
