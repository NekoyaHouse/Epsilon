package me.sofurry.via.injection.mixin.core.gui;

import com.github.epsilon.modules.impl.ClientSetting;
import com.viaversion.viaversion.api.connection.UserConnection;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import me.sofurry.via.util.ChatUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.raphimc.vialegacy.protocol.classic.c0_28_30toa1_0_15.storage.ClassicProgressStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelLoadingScreen.class)
public abstract class MixinLevelLoadingScreen extends Screen {

    public MixinLevelLoadingScreen(Component title) {
        super(title);
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void renderClassicProgress(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        if (ClientSetting.INSTANCE.showClassicLoadingProgressInConnectScreen.getValue()) {
            // Check if ViaVersion is translating
            final UserConnection connection = ProtocolTranslator.getPlayNetworkUserConnection();
            if (connection == null) {
                return;
            }

            // Check if the client is connecting to a classic server
            final ClassicProgressStorage classicProgressStorage = connection.get(ClassicProgressStorage.class);
            if (classicProgressStorage == null) {
                return;
            }

            // Draw the classic loading progress
            graphics.centeredText(
                    minecraft.font,
                    ChatUtil.prefixText(classicProgressStorage.status),
                    width / 2,
                    height / 2 - 30,
                    -1
            );
        }
    }

}
