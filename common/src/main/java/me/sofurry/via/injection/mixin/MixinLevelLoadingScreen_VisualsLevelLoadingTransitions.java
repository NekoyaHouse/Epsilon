package me.sofurry.via.injection.mixin;

import com.github.epsilon.modules.impl.ClientSetting;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LevelLoadingScreen.class)
public abstract class MixinLevelLoadingScreen_VisualsLevelLoadingTransitions {

    @Shadow
    private LevelLoadingScreen.Reason reason;

    @Redirect(method = "extractBackground", at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screens/LevelLoadingScreen;reason:Lnet/minecraft/client/gui/screens/LevelLoadingScreen$Reason;", opcode = Opcodes.GETFIELD))
    private LevelLoadingScreen.Reason hideDownloadTerrainScreenTransitionEffects(LevelLoadingScreen levelLoadingScreen) {
        if (ClientSetting.INSTANCE.hideDownloadTerrainScreenTransitionEffects.isEnabled()) {
            return LevelLoadingScreen.Reason.OTHER;
        } else {
            return this.reason;
        }
    }

}
