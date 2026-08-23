package me.sofurry.via.injection.mixin;

import com.github.epsilon.modules.impl.ClientSetting;
import me.sofurry.via.screen.impl.ProtocolSelectionScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(JoinMultiplayerScreen.class)
public abstract class MixinJoinMultiplayerScreen_CoreGui extends Screen {

    @Unique
    private Button viaVersion$button;

    public MixinJoinMultiplayerScreen_CoreGui(Component title) {
        super(title);
    }

    @Inject(method = "repositionElements", at = @At("RETURN"))
    private void addProtocolSelectionButton(CallbackInfo ci) {
        final int displayMode = ClientSetting.INSTANCE.multiplayerScreenButtonOrientation.getModeIndex();
        if (displayMode == 0) { // Off
            if (viaVersion$button != null) {
                viaVersion$button.visible = false;
            }
            return;
        }

        if (viaVersion$button == null) {
            viaVersion$button = Button.builder(Component.literal("ViaVersion"), _ -> ProtocolSelectionScreen.INSTANCE.open(this))
                    .size(98, 20)
                    .build();
            this.addRenderableWidget(viaVersion$button);
        }

        viaVersion$button.visible = true;
        ClientSetting.setOrientation(viaVersion$button::setPosition, displayMode, width, height);
    }

}
