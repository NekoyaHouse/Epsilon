package me.sofurry.via.visuals.injection.mixin.oof_sound;

import com.github.epsilon.modules.impl.ClientSetting;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class MixinPlayer {

    @Unique
    private static final SoundEvent viaVersionVisuals$oof_hurt = SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("viafabricplus-visuals", "oof.hurt"));

    @Inject(method = "getHurtSound", at = @At("HEAD"), cancellable = true)
    private void replaceSound(DamageSource source, CallbackInfoReturnable<SoundEvent> cir) {
        if (ClientSetting.INSTANCE.replaceHurtSoundWithOOFSound.isEnabled()) {
            cir.setReturnValue(viaVersionVisuals$oof_hurt);
        }
    }

}
