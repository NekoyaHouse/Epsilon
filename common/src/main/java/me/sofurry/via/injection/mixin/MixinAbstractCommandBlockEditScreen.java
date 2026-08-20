package me.sofurry.via.injection.mixin;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.screens.inventory.AbstractCommandBlockEditScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AbstractCommandBlockEditScreen.class)
public abstract class MixinAbstractCommandBlockEditScreen {

    @Redirect(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/CommandSuggestions;updateCommandInfo()V"))
    private void cancelAutoComplete(CommandSuggestions instance) {
        if (ProtocolTranslator.getTargetVersion().newerThanOrEqualTo(ProtocolVersion.v1_13)) {
            instance.updateCommandInfo();
        }
    }

}
