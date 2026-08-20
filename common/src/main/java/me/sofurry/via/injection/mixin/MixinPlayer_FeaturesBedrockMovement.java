package me.sofurry.via.injection.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class MixinPlayer_FeaturesBedrockMovement extends MixinLivingEntity_FeaturesMovementLiquid {

    @Unique
    private int viaVersion$ticksSinceSwimming;

    public MixinPlayer_FeaturesBedrockMovement(final EntityType<?> type, final Level world) {
        super(type, world);
    }

    @Redirect(method = "travel", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;containing(DDD)Lnet/minecraft/core/BlockPos;"))
    private BlockPos modifyWaterAbovePosition(double x, double y, double z) {
        if (ProtocolTranslator.getTargetVersion().equals(BedrockProtocolVersion.bedrockLatest)) {
            return BlockPos.containing(x, y - 0.9, z);
        } else {
            return BlockPos.containing(x, y, z);
        }
    }

    @WrapWithCondition(method = "travel", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V", ordinal = 0))
    private boolean preventSwimmingMotionWhenJumping(Player instance, Vec3 vec3d) {
        return !ProtocolTranslator.getTargetVersion().equals(BedrockProtocolVersion.bedrockLatest) || !instance.isJumping();
    }

    @Inject(method = "travel", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getAbilities()Lnet/minecraft/world/entity/player/Abilities;"))
    private void preventJumpingWhenStartedSwimming(Vec3 input, CallbackInfo ci) {
        if (!ProtocolTranslator.getTargetVersion().equals(BedrockProtocolVersion.bedrockLatest)) {
            return;
        }

        if (this.isSwimming()) {
            this.viaVersion$ticksSinceSwimming++;
        } else {
            this.viaVersion$ticksSinceSwimming = 0;
        }

        if (this.viaVersion$ticksSinceSwimming > 0 && this.viaVersion$ticksSinceSwimming < 10 && this.isJumping()) {
            this.setDeltaMovement(this.getDeltaMovement().x(), 0, this.getDeltaMovement().z());
        }
    }

    @Redirect(method = "travel", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;isSwimming()Z"))
    private boolean preventSwimmingResurface(Player instance) {
        if (!ProtocolTranslator.getTargetVersion().equals(BedrockProtocolVersion.bedrockLatest) || !instance.isSwimming()) {
            return instance.isSwimming();
        }

        double d = this.getLookAngle().y;
        // TODO: The value used here (0.55) isn't entirely correct, however in most cases it should be fine.
        if (this.level().getFluidState(BlockPos.containing(this.getX(), this.getY() + 0.4, this.getZ())).isEmpty() && d > 0 && d < 0.55) {
            instance.setDeltaMovement(instance.getDeltaMovement().x(), 0, instance.getDeltaMovement().z());
            return false;
        }

        return true;
    }

    @Redirect(method = "travel", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V", ordinal = 1))
    private void removeFlySlipperiness(Player instance, Vec3 vec3d, @Local(argsOnly = true) Vec3 input) {
        if (ProtocolTranslator.getTargetVersion().equals(BedrockProtocolVersion.bedrockLatest) && input.horizontalDistanceSqr() == 0) {
            instance.setDeltaMovement(new Vec3(0, vec3d.y, 0));
        } else {
            instance.setDeltaMovement(vec3d);
        }
    }

}
