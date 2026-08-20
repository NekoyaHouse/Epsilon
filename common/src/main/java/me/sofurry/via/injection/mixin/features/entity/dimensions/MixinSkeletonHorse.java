package me.sofurry.via.injection.mixin.features.entity.dimensions;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.equine.SkeletonHorse;
import net.minecraft.world.level.Level;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SkeletonHorse.class)
public abstract class MixinSkeletonHorse extends AbstractHorse {

    @Shadow
    @Final
    private static EntityDimensions BABY_DIMENSIONS;

    @Unique
    private static final EntityDimensions viaVersion$baby_dimensions_r1_21_11 = EntityTypes.SKELETON_HORSE
            .getDimensions()
            .withAttachments(EntityAttachments.builder().attach(EntityAttachment.PASSENGER, 0.0F, EntityTypes.SKELETON_HORSE.getHeight() - 0.03125F, 0.0F))
            .scale(0.5F);

    public MixinSkeletonHorse(final EntityType<? extends AbstractHorse> type, final Level level) {
        super(type, level);
    }

    @Redirect(method = "getDefaultDimensions", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/animal/equine/SkeletonHorse;BABY_DIMENSIONS:Lnet/minecraft/world/entity/EntityDimensions;", opcode = Opcodes.GETSTATIC))
    private EntityDimensions changeBabyDimensions() {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21_11)) {
            return viaVersion$baby_dimensions_r1_21_11;
        } else {
            return BABY_DIMENSIONS;
        }
    }

}
