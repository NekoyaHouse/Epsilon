package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.BetterChat;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ChatScreen.class)
public abstract class MixinChatScreen extends net.minecraft.client.gui.screens.Screen {
    @org.spongepowered.asm.mixin.Shadow protected net.minecraft.client.gui.components.EditBox input;
    @org.spongepowered.asm.mixin.Unique private boolean epsilon$sent;
    protected MixinChatScreen(net.minecraft.network.chat.Component title) { super(title); }
    @org.spongepowered.asm.mixin.injection.Inject(method = "init", at = @At("RETURN"))
    private void epsilon$chatTools(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        epsilon$sent = false;
        BetterChat chat = BetterChat.INSTANCE;
        if (!chat.isEnabled()) return;
        if (chat.keepDraft.getValue() && input.getValue().isEmpty()) input.setValue(chat.getDraft());
        if (chat.quickCharacters.getValue()) {
            int[] chars = chat.characters.getValue().codePoints().limit(Math.max(0, Math.min(12, (width - 12) / 22))).toArray();
            for (int i = 0; i < chars.length; i++) {
                String symbol = new String(Character.toChars(chars[i]));
                addRenderableWidget(net.minecraft.client.gui.components.Button.builder(net.minecraft.network.chat.Component.literal(symbol),
                    b -> { input.insertText(symbol); setFocused(input); }).bounds(4 + i * 22, height - 40, 20, 18).build());
            }
        }
    }
    @org.spongepowered.asm.mixin.injection.Inject(method = "handleChatInput", at = @At("HEAD"))
    private void epsilon$sent(String text, boolean addToHistory, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        epsilon$sent = true; BetterChat.INSTANCE.setDraft("");
    }
    @org.spongepowered.asm.mixin.injection.Inject(method = "removed", at = @At("HEAD"))
    private void epsilon$saveDraft(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (BetterChat.INSTANCE.isEnabled() && BetterChat.INSTANCE.keepDraft.getValue() && !epsilon$sent && input != null)
            BetterChat.INSTANCE.setDraft(input.getValue());
    }


    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"))
    private void onExtractRenderState(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int col, Operation<Void> original) {
        if (!BetterChat.INSTANCE.isEnabled()) {
            original.call(graphics, x0, y0, x1, y1, col);
        }
    }

}
