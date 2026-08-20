package me.sofurry.via.injection.mixin.features.networking.run_command_action;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class MixinScreen {

    @Final
    @Shadow
    protected Minecraft minecraft;

    @Inject(method = "clickCommandAction", at = @At("HEAD"), cancellable = true)
    private static void changeCommandHandling(LocalPlayer player, String command, Screen screenAfterCommand, CallbackInfo ci) {
        if (ProtocolTranslator.getTargetVersion().newerThan(ProtocolVersion.v1_21_4)) {
            return;
        }

        if (!command.startsWith("/")) {
            ci.cancel();
            if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_19)) {
                player.connection.sendChat(command);
            }
        }
    }

}
