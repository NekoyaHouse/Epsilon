package com.github.epsilon.interfaces;

import net.minecraft.network.chat.Component;

public interface ChatComponentAccessor {

    void epsilon$addClientSystemMessage(Component message, int hash);

    void epsilon$resetCombine();

    ChatVisibility epsilon$getVisibility(int currentTick, boolean focused);

    record ChatVisibility(int lineCount, float opacity, float heightFactor) {
    }

}
