package com.github.epsilon.mixins;

import com.github.epsilon.interfaces.ChatComponentAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.HashMap;
import java.util.Iterator;
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
    private final Map<Integer, GuiMessage> epsilon$hashedMessages = new HashMap<>();

    @Override
    public void epsilon$addClientSystemMessage(Component message, int hash) {
        GuiMessage guiMessage = new GuiMessage(
                this.minecraft.gui.getGuiTicks(),
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
    private void epsilon$addHashedMessage(Component contents, MessageSignature signature, GuiMessageSource source, GuiMessageTag tag, int hash) {
        GuiMessage message = new GuiMessage(this.minecraft.gui.getGuiTicks(), contents, signature, source, tag);
        if (this.visibleMessageFilter.test(message)) {
            this.logChatMessage(message);
            this.addMessageToDisplayQueue(message);
            this.addMessageToQueue(message);
            this.epsilon$hashedMessages.put(hash, message);
        }
    }

    @Unique
    private void epsilon$pruneMissingHashedMessages() {
        Iterator<Map.Entry<Integer, GuiMessage>> iterator = this.epsilon$hashedMessages.entrySet().iterator();
        while (iterator.hasNext()) {
            if (!this.allMessages.contains(iterator.next().getValue())) {
                iterator.remove();
            }
        }
    }

}
