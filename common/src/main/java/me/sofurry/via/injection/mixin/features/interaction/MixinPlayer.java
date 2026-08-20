package me.sofurry.via.injection.mixin.features.interaction;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class MixinPlayer {

    @Shadow
    @Final
    private Abilities abilities;

    @Inject(method = "canEat", at = @At("HEAD"), cancellable = true)
    private void preventEatingFoodInCreative(boolean canAlwaysEat, CallbackInfoReturnable<Boolean> cir) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_14_4) && this.abilities.invulnerable) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isCreative", at = @At("HEAD"), cancellable = true)
    private void fixCreativeCheck(CallbackInfoReturnable<Boolean> cir) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_8)) {
            cir.setReturnValue(this.abilities.instabuild);
        }
    }

}
