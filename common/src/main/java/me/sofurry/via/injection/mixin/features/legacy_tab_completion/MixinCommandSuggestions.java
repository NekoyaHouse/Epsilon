package me.sofurry.via.injection.mixin.features.legacy_tab_completion;

import com.github.epsilon.modules.impl.ClientSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(CommandSuggestions.class)
public abstract class MixinCommandSuggestions {

    @Shadow
    public abstract void updateCommandInfo();

    @Shadow
    private CommandSuggestions.SuggestionsList suggestions;

    @Shadow
    @Final
    private EditBox input;

    @Shadow
    @Final
    private List<FormattedCharSequence> commandUsage;

    @Inject(method = "formatChat", at = @At(value = "HEAD"), cancellable = true)
    private void disableTextFieldColors(String text, int offset, CallbackInfoReturnable<FormattedCharSequence> cir) {
        if (this.viaVersion$cancelTabComplete()) {
            cir.setReturnValue(FormattedCharSequence.forward(text, Style.EMPTY));
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void handle1_12_2KeyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (this.viaVersion$cancelTabComplete()) {
            if (event.key() == GLFW.GLFW_KEY_TAB && this.suggestions == null) {
                this.updateCommandInfo();
            } else if (this.suggestions != null) {
                if (this.suggestions.keyPressed(event)) {
                    cir.setReturnValue(true);
                    return;
                }
                this.input.setSuggestion(null);
                this.suggestions = null;
            }
        }
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void clearMessages(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (this.viaVersion$cancelTabComplete()) {
            this.commandUsage.clear();
        }
    }

    @Unique
    private boolean viaVersion$cancelTabComplete() {
        return ClientSetting.INSTANCE.legacyTabCompletions.isEnabled() && this.input.getValue().startsWith("/");
    }

}
