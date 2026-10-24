package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.ACSpoof;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(GuiGraphicsExtractor.class)
public class MixinGuiGraphicsExtractor {

    @ModifyVariable(method = "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V", at = @At("HEAD"), argsOnly = true)
    private FormattedCharSequence spoofText(FormattedCharSequence original, Font font, FormattedCharSequence text, int x, int y, int color, boolean dropShadow) {
        return ACSpoof.INSTANCE.text(original, color);
    }

}
