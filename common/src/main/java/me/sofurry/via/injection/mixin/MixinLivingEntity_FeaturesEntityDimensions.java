package me.sofurry.via.injection.mixin;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.features.entity.dimensions.EntityRidingOffsetsPre1_20_2;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity_FeaturesEntityDimensions extends Entity {

    public MixinLivingEntity_FeaturesEntityDimensions(EntityType<?> type, Level world) {
        super(type, world);
    }

    @Redirect(method = "getPassengerRidingPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getPassengerAttachmentPoint(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/EntityDimensions;F)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 getPassengerRidingPos1_20_1(LivingEntity instance, Entity entity, EntityDimensions entityDimensions, float v) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_20)) {
            return EntityRidingOffsetsPre1_20_2.getMountedHeightOffset(instance, entity).yRot(-instance.getYRot() * (float) (Math.PI / 180));
        } else {
            return getPassengerAttachmentPoint(entity, entityDimensions, v);
        }
    }

}
