package com.github.epsilon.interfaces

import net.minecraft.network.chat.Component

interface ChatComponentAccessor {

    fun `epsilon$addClientSystemMessage`(message: Component, hash: Int)

    fun `epsilon$getVisibility`(currentTick: Int, focused: Boolean): ChatVisibility

    data class ChatVisibility(
        val lineCount: Int,
        val opacity: Float,
        val heightFactor: Float,
    )

}
