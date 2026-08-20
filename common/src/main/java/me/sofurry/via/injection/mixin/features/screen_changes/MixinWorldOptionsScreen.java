package me.sofurry.via.injection.mixin.features.screen_changes;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.options.WorldOptionsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldOptionsScreen.class)
public abstract class MixinWorldOptionsScreen {

    @Inject(method = "createGameRulesButton", at = @At("RETURN"))
    private void disableGameRules(CallbackInfoReturnable<Button> cir) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21_11)) {
            cir.getReturnValue().active = false;
        }
    }

}
