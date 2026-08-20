package me.sofurry.via.injection.mixin.features.screen_changes;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.client.gui.screens.inventory.StructureBlockEditScreen$1")
public abstract class MixinStructureBlockEditScreen_1 extends EditBox {

    public MixinStructureBlockEditScreen_1(Font textRenderer, int x, int y, int width, int height, EditBox copyFrom, Component text) {
        super(textRenderer, x, y, width, height, copyFrom, text);
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void removeValidation(CharacterEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2)) {
            cir.setReturnValue(super.charTyped(event));
        }
    }

}
