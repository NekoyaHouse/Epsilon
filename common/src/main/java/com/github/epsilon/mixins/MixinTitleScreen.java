package com.github.epsilon.mixins;

import com.github.epsilon.gui.screen.MainMenuScreen;
import com.github.epsilon.gui.screen.WelcomeScreen;
import com.github.epsilon.modules.impl.ClientSetting;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.github.epsilon.Constants.mc;

@Mixin(TitleScreen.class)
public class MixinTitleScreen {

    @Unique
    private static boolean epsilon$welcomeHandled;

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void onInit(CallbackInfo ci) {
        if (!epsilon$welcomeHandled) {
            epsilon$welcomeHandled = true;
            if (ClientSetting.INSTANCE.showWelcomeScreen.getValue()) {
                ci.cancel();
                mc.setScreen(WelcomeScreen.INSTANCE);
                return;
            }
        }

        if (ClientSetting.INSTANCE.useMainMenu.getValue()) {
            ci.cancel();
            mc.setScreen(MainMenuScreen.INSTANCE);
        }
    }

}
