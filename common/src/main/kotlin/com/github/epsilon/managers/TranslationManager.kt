package com.github.epsilon.managers

import com.github.epsilon.assets.i18n.TranslateComponent
import com.github.epsilon.kernel.state.mutableStateOf
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

object TranslationManager {

    private val components = CopyOnWriteArrayList<TranslateComponent>()
    private val revision = AtomicLong()
    private val resourceState = mutableStateOf(0L)

    fun refresh() {
        for (component in components) {
            component.refresh()
        }
        resourceState.value = revision.incrementAndGet()
    }

    fun registerTranslateComponent(component: TranslateComponent) {
        components.add(component)
    }

    fun getRevision(): Long {
        return resourceState.value
    }

}
