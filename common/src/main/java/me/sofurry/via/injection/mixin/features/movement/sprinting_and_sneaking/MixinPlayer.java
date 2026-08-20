package me.sofurry.via.injection.mixin.features.movement.sprinting_and_sneaking;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class MixinPlayer extends LivingEntity {

    @Unique
    public boolean viaVersion$isSprinting;

    protected MixinPlayer(EntityType<? extends LivingEntity> entityType, Level world) {
        super(entityType, world);
    }

    @Inject(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;setSpeed(F)V"))
    private void storeSprintingState(CallbackInfo ci) {
        viaVersion$isSprinting = this.isSprinting();
    }

    @Redirect(method = "getFlyingSpeed", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;isSprinting()Z"))
    private boolean useLastSprintingState(Player instance) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_19_3)) {
            return viaVersion$isSprinting;
        } else {
            return instance.isSprinting();
        }
    }

}
