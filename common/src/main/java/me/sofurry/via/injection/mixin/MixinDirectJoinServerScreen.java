package me.sofurry.via.injection.mixin;

import com.github.epsilon.modules.impl.ClientSetting;
import me.sofurry.via.screen.impl.ProtocolSelectionScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DirectJoinServerScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DirectJoinServerScreen.class)
public abstract class MixinDirectJoinServerScreen extends Screen {

    public MixinDirectJoinServerScreen(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void addProtocolSelectionButton(CallbackInfo ci) {
        final int buttonPosition = ClientSetting.INSTANCE.directConnectScreenButtonOrientation.getModeIndex();
        if (buttonPosition == 0) { // Off
            return;
        }

        final Button.Builder builder = Button.builder(Component.nullToEmpty("ViaVersion"), button -> ProtocolSelectionScreen.INSTANCE.open(this)).size(98, 20);
        ClientSetting.setOrientation(builder::pos, buttonPosition, width, height);
        this.addRenderableWidget(builder.build());
    }

}
