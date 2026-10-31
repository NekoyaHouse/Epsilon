package com.github.epsilon.managers

import com.github.epsilon.graphics.LuminRenderSystem

object RenderTargetManager {

    private val targets = ArrayList<LuminRenderSystem.LuminRenderTarget>()

    @Synchronized
    fun register(target: LuminRenderSystem.LuminRenderTarget): LuminRenderSystem.LuminRenderTarget {
        targets.add(target)
        return target
    }

    @Synchronized
    fun unregister(target: LuminRenderSystem.LuminRenderTarget) {
        targets.remove(target)
    }

    @Synchronized
    fun destroyAll() {
        for (target in targets.toList()) {
            target.close()
        }
        targets.clear()
    }

}
