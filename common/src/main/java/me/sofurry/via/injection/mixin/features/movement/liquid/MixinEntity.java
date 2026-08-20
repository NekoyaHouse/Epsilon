package me.sofurry.via.injection.mixin.features.movement.liquid;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class MixinEntity {

    @Shadow
    private Level level;

    @Shadow
    public abstract AABB getBoundingBox();

    @Shadow
    protected abstract AABB modifyPassengerFluidInteractionBox(final AABB passengerBox);

    @Redirect(method = "getFluidInteractionBox", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;modifyPassengerFluidInteractionBox(Lnet/minecraft/world/phys/AABB;)Lnet/minecraft/world/phys/AABB;"))
    private AABB skipPassengerChanges(Entity instance, AABB passengerBox) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21_11) || ProtocolTranslator.getTargetVersion().equals(BedrockProtocolVersion.bedrockLatest)) {
            return passengerBox;
        } else {
            return modifyPassengerFluidInteractionBox(passengerBox);
        }
    }

    @Inject(method = "isInLava", at = @At("RETURN"), cancellable = true)
    private void replaceLavaCheck1_13_2(CallbackInfoReturnable<Boolean> cir) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_13_2)) {
            final AABB aabb = this.getBoundingBox().deflate(0.1F, 0.4F, 0.1F);
            cir.setReturnValue(this.level.getBlockStatesIfLoaded(aabb).anyMatch(key -> key.getFluidState().is(FluidTags.LAVA)));
        }
    }

    @Redirect(method = "getFluidInteractionBox", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/AABB;deflate(D)Lnet/minecraft/world/phys/AABB;"))
    private AABB inflate(AABB instance, double amount) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2) || ProtocolTranslator.getTargetVersion().equals(BedrockProtocolVersion.bedrockLatest)) {
            instance = instance.inflate(0, -0.4, 0);
        }

        return instance.deflate(amount);
    }

    @Inject(method = "setSwimming", at = @At("HEAD"), cancellable = true)
    private void cancelSwimming(boolean swimming, CallbackInfo ci) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2) && swimming) {
            ci.cancel();
        }
    }

}
