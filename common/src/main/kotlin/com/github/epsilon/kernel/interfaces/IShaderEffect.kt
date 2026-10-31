package com.github.epsilon.kernel.interfaces

import net.minecraft.client.gl.Framebuffer
import net.minecraft.client.gl.PostEffectPass

interface IShaderEffect {
    fun addFakeTargetHook(name: String, buffer: Framebuffer?)

    val passesHook: List<PostEffectPass>
}
