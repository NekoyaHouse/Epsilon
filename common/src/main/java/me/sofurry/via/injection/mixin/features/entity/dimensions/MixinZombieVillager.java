package me.sofurry.via.injection.mixin.features.entity.dimensions;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EntityAttachments;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ZombieVillager.class)
public abstract class MixinZombieVillager {

    @Shadow
    @Final
    private static EntityDimensions BABY_DIMENSIONS;

    @Unique
    private static final EntityDimensions viaVersion$baby_dimensions_r26_1 = EntityDimensions.scalable(0.49F, 0.99F)
            .withEyeHeight(0.67F)
            .withAttachments(EntityAttachments.builder().attach(EntityAttachment.VEHICLE, 0.0F, 0.125F, 0.0F));

    @Redirect(method = "getDefaultDimensions", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/monster/zombie/ZombieVillager;BABY_DIMENSIONS:Lnet/minecraft/world/entity/EntityDimensions;", opcode = Opcodes.GETSTATIC))
    private EntityDimensions changeBabyDimensions() {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v26_1)) {
            return viaVersion$baby_dimensions_r26_1;
        } else {
            return BABY_DIMENSIONS;
        }
    }

    @Redirect(method = "getDefaultDimensions", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/monster/zombie/ZombieVillager;isBaby()Z"))
    private boolean dontChangeScale(ZombieVillager instance) {
        return ProtocolTranslator.getTargetVersion().newerThan(ProtocolVersion.v1_21_11) && instance.isBaby();
    }

}
