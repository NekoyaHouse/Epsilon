package me.sofurry.via.injection.mixin.core.gui;

import com.github.epsilon.modules.impl.ClientSetting;
import me.sofurry.via.screen.ProtocolVersionSlider;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(JoinMultiplayerScreen.class)
public abstract class MixinJoinMultiplayerScreen extends Screen {

    @Unique
    private ProtocolVersionSlider viaVersion$slider;

    public MixinJoinMultiplayerScreen(Component title) {
        super(title);
    }

    @Inject(method = "repositionElements", at = @At("RETURN"))
    private void addProtocolSelectionSlider(CallbackInfo ci) {
        final int displayMode = ClientSetting.INSTANCE.multiplayerScreenButtonOrientation.getModeIndex();
        if (displayMode == 0) { // Off
            if (viaVersion$slider != null) {
                viaVersion$slider.visible = false;
            }
            return;
        }

        if (viaVersion$slider == null) {
            viaVersion$slider = new ProtocolVersionSlider(5, 5);
            this.addRenderableWidget(viaVersion$slider);
        }

        viaVersion$slider.visible = true;
        viaVersion$slider.setPosition(5, 5);
    }

}
