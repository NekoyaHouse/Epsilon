package com.github.epsilon.mixins;

import com.github.epsilon.interfaces.ChatComponentAccessor;
import com.github.epsilon.modules.impl.render.BetterChat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

@Mixin(ChatComponent.class)
public abstract class MixinChatComponentHash implements ChatComponentAccessor {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private List<GuiMessage> allMessages;

    @Shadow
    @Final
    private List<GuiMessage.Line> trimmedMessages;

    @Shadow
    private int chatScrollbarPos;

    @Shadow
    private Predicate<GuiMessage> visibleMessageFilter;

    @Shadow
    private void logChatMessage(GuiMessage message) {
    }

    @Shadow
    private void addMessageToDisplayQueue(GuiMessage message) {
    }

    @Shadow
    private void addMessageToQueue(GuiMessage message) {
    }

    @Shadow
    private void refreshTrimmedMessages() {
    }

    @Unique
    private Map<Integer, GuiMessage> epsilon$hashedMessages;

    @Unique
    private boolean epsilon$chatScissorPushed;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void initHashedMessages(Minecraft minecraft, CallbackInfo ci) {
        this.epsilon$hashedMessages = new HashMap<>();
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V", at = @At("HEAD"))
    private void pushBetterChatScissor(GuiGraphicsExtractor graphics, Font font, int ticks, int mouseX, int mouseY, ChatComponent.DisplayMode displayMode, boolean changeCursorOnInsertions, CallbackInfo ci) {
        ScreenRectangle scissor = BetterChat.INSTANCE.getChatTextScissor();
        epsilon$chatScissorPushed = scissor != null;
        if (epsilon$chatScissorPushed) {
            graphics.enableScissor(scissor.left(), scissor.top(), scissor.right(), scissor.bottom());
        }
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V", at = @At("RETURN"))
    private void popBetterChatScissor(GuiGraphicsExtractor graphics, Font font, int ticks, int mouseX, int mouseY, ChatComponent.DisplayMode displayMode, boolean changeCursorOnInsertions, CallbackInfo ci) {
        if (epsilon$chatScissorPushed) {
            graphics.disableScissor();
            epsilon$chatScissorPushed = false;
        }
    }

    @Override
    public ChatVisibility epsilon$getVisibility(int currentTick, boolean focused) {
        int availableLines = Math.max(0, this.trimmedMessages.size() - this.chatScrollbarPos);
        int linesPerPage = ((ChatComponent) (Object) this).getLinesPerPage();
        int checkedLines = Math.min(availableLines, linesPerPage);
        int visibleLines = 0;
        float maxOpacity = 0.0f;
        float heightFactor = 0.0f;

        for (int i = 0; i < checkedLines; i++) {
            GuiMessage.Line line = this.trimmedMessages.get(i + this.chatScrollbarPos);
            float opacity = focused ? 1.0f : epsilon$calculateUnfocusedOpacity(line, currentTick);
            if (opacity > 1.0E-5f) {
                visibleLines++;
                maxOpacity = Math.max(maxOpacity, opacity);
                heightFactor += opacity;
            }
        }

        return new ChatVisibility(visibleLines, maxOpacity, heightFactor);
    }

    @Override
    public void epsilon$addClientSystemMessage(Component message, int hash) {
        GuiMessage guiMessage = new GuiMessage(
                this.minecraft.gui.hud.getGuiTicks(),
                message,
                null,
                GuiMessageSource.SYSTEM_CLIENT,
                GuiMessageTag.systemSinglePlayer()
        );
        if (!this.visibleMessageFilter.test(guiMessage)) {
            return;
        }

        GuiMessage previous = this.epsilon$hashedMessages.put(hash, guiMessage);

        if (previous != null) {
            int previousIndex = this.allMessages.indexOf(previous);
            if (previousIndex != -1) {
                this.allMessages.remove(previousIndex);
                this.logChatMessage(guiMessage);
                this.addMessageToQueue(guiMessage);
                this.refreshTrimmedMessages();
                this.epsilon$pruneMissingHashedMessages();
                return;
            }
        }

        this.epsilon$addHashedMessage(message, null, GuiMessageSource.SYSTEM_CLIENT, GuiMessageTag.systemSinglePlayer(), hash);
        this.epsilon$pruneMissingHashedMessages();
    }

    @Unique
    private float epsilon$calculateUnfocusedOpacity(GuiMessage.Line line, int currentTick) {
        double progress = 1.0 - (currentTick - line.addedTime()) / 200.0;
        progress = Mth.clamp(progress * 10.0, 0.0, 1.0);
        return (float) (progress * progress);
    }

    @Unique
    private void epsilon$addHashedMessage(Component contents, MessageSignature signature, GuiMessageSource source, GuiMessageTag tag, int hash) {
        GuiMessage message = new GuiMessage(this.minecraft.gui.hud.getGuiTicks(), contents, signature, source, tag);
        if (this.visibleMessageFilter.test(message)) {
            this.logChatMessage(message);
            this.addMessageToDisplayQueue(message);
            this.addMessageToQueue(message);
            this.epsilon$hashedMessages.put(hash, message);
        }
    }

    @Unique
    private void epsilon$pruneMissingHashedMessages() {
        this.epsilon$hashedMessages.entrySet().removeIf(integerGuiMessageEntry -> !this.allMessages.contains(integerGuiMessageEntry.getValue()));
    }

}
