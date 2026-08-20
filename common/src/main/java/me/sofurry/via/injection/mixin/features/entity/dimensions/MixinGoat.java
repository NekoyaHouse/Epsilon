package me.sofurry.via.injection.mixin.features.entity.dimensions;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Goat.class)
public abstract class MixinGoat extends Animal {

    @Shadow
    public abstract float getAgeScale();

    @Unique
    private static final EntityDimensions viaVersion$dimensions_r26_1 = EntityDimensions.scalable(0.9F, 1.3F).scale(0.7F);

    public MixinGoat(final EntityType<? extends Animal> type, final Level level) {
        super(type, level);
    }

    @Inject(method = "getDefaultDimensions", at = @At("HEAD"), cancellable = true)
    private void replaceDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v26_1)) {
            cir.setReturnValue(pose == Pose.LONG_JUMPING ? viaVersion$dimensions_r26_1.scale(this.getAgeScale()) : super.getDefaultDimensions(pose));
        }
    }

    @Redirect(method = "getAgeScale", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/goat/Goat;isBaby()Z"))
    private boolean dontChangeScale(Goat instance) {
        return ProtocolTranslator.getTargetVersion().newerThan(ProtocolVersion.v1_21_11) && instance.isBaby();
    }

}
