package com.github.epsilon.managers

import com.github.epsilon.graphics.renderers.IRenderer
import java.util.ArrayList

object RendererManager {

    private val renderers = ArrayList<IRenderer>()

    @Synchronized
    fun <T : IRenderer> register(renderer: T): T {
        renderers.add(renderer)
        return renderer
    }

    @Synchronized
    fun unregister(renderer: IRenderer) {
        renderers.remove(renderer)
    }

    @Synchronized
    fun destroyAll() {
        for (renderer in renderers.toList()) {
            renderer.close()
        }
        renderers.clear()
    }

}
