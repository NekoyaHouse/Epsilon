package me.sofurry.via.injection.mixin;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EntityAttachments;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.equine.AbstractChestedHorse;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.level.Level;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractChestedHorse.class)
public abstract class MixinAbstractChestedHorse extends AbstractHorse {

    @Shadow
    @Final
    private EntityDimensions babyDimensions;

    @Unique
    private EntityDimensions viaVersion$baby_dimensions_r1_21_11;

    public MixinAbstractChestedHorse(final EntityType<? extends AbstractHorse> type, final Level level) {
        super(type, level);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void initializeDimensions(EntityType<? extends AbstractChestedHorse> type, Level level, CallbackInfo ci) {
        this.viaVersion$baby_dimensions_r1_21_11 = type.getDimensions()
                .withAttachments(EntityAttachments.builder().attach(EntityAttachment.PASSENGER, 0.0F, type.getHeight() - 0.15625F, 0.0F))
                .scale(0.5F);
    }

    @Redirect(method = "getDefaultDimensions", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/animal/equine/AbstractChestedHorse;babyDimensions:Lnet/minecraft/world/entity/EntityDimensions;", opcode = Opcodes.GETFIELD))
    private EntityDimensions changeBabyDimensions(AbstractChestedHorse instance) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21_11)) {
            return this.viaVersion$baby_dimensions_r1_21_11;
        } else {
            return this.babyDimensions;
        }
    }

}
