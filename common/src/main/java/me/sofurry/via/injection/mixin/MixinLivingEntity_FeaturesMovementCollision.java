package me.sofurry.via.injection.mixin;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.raphimc.vialegacy.api.LegacyProtocolVersion;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity_FeaturesMovementCollision extends Entity {

    @Shadow
    private Optional<BlockPos> lastClimbablePos;

    public MixinLivingEntity_FeaturesMovementCollision(final EntityType<?> type, final Level world) {
        super(type, world);
    }

    @Shadow
    protected abstract boolean trapdoorUsableAsLadder(final BlockPos pos, final BlockState state);

    @Redirect(method = "handleRelativeFrictionAndCalculateMovement", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/LivingEntity;wasInPowderSnow:Z", opcode = Opcodes.GETFIELD))
    private boolean dontCheckLastTick(LivingEntity instance) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21_4)) {
            return this.getInBlockState().is(Blocks.POWDER_SNOW);
        } else {
            return instance.wasInPowderSnow;
        }
    }

    @Inject(method = "pushEntities", at = @At("HEAD"), cancellable = true)
    private void preventEntityPush(CallbackInfo ci) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_8)) {
            ci.cancel();
        }
    }

    @Inject(method = "trapdoorUsableAsLadder", at = @At("HEAD"), cancellable = true)
    private void disableCrawling(CallbackInfoReturnable<Boolean> ci) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_8)) {
            ci.setReturnValue(false);
        }
    }

    @Inject(method = "onClimbable", at = @At("RETURN"), cancellable = true)
    private void allowGappedLadderClimb(CallbackInfoReturnable<Boolean> cir) {
        if (ProtocolTranslator.getTargetVersion().olderThan(LegacyProtocolVersion.b1_5tob1_5_2) && !cir.getReturnValueZ() && !this.isSpectator()) {
            final BlockPos blockPos = this.blockPosition().above();
            final BlockState blockState = this.level().getBlockState(blockPos);
            if (blockState.is(BlockTags.CLIMBABLE)) {
                this.lastClimbablePos = Optional.of(blockPos);
                cir.setReturnValue(true);
            } else if (blockState.getBlock() instanceof TrapDoorBlock && this.trapdoorUsableAsLadder(blockPos, blockState)) {
                this.lastClimbablePos = Optional.of(blockPos);
                cir.setReturnValue(true);
            }
        }
    }

}
