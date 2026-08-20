package me.sofurry.via.injection.mixin.features.movement.limitation;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class MixinPlayer extends LivingEntity {

    protected MixinPlayer(EntityType<? extends LivingEntity> entityType, Level world) {
        super(entityType, world);
    }

    @Inject(method = "onClimbable", at = @At("HEAD"), cancellable = true)
    private void allowClimbingWhileFlying(CallbackInfoReturnable<Boolean> cir) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21_2)) {
            cir.setReturnValue(super.onClimbable());
        }
    }

    @Redirect(method = "maybeBackOffFromEdge", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/player/Abilities;flying:Z", opcode = Opcodes.GETFIELD))
    private boolean disableFlyingCheck(final Abilities instance) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_16_1)) {
            return false;
        } else {
            return instance.flying;
        }
    }

    @Inject(method = "isAboveGround", at = @At("HEAD"), cancellable = true)
    private void isOnGround(final float maxDownStep, final CallbackInfoReturnable<Boolean> cir) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_16_1)) {
            cir.setReturnValue(this.onGround());
        }
    }

    @Redirect(method = "canPlayerFitWithinBlocksAndEntitiesWhen", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/AABB;deflate(D)Lnet/minecraft/world/phys/AABB;"))
    private AABB removeContractionOfCollisionBox(AABB instance, double amount) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_15_2)) {
            return instance;
        } else {
            return instance.deflate(amount);
        }
    }

}
