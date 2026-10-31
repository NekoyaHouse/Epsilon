package com.github.epsilon.managers

import com.github.epsilon.assets.i18n.TranslateComponent
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

object TranslationManager {

    private val components = CopyOnWriteArrayList<TranslateComponent>()
    private val revision = AtomicLong()

    fun refresh() {
        for (component in components) {
            component.refresh()
        }
        revision.incrementAndGet()
    }

    fun registerTranslateComponent(component: TranslateComponent) {
        components.add(component)
    }

    fun getRevision(): Long {
        return revision.get()
    }

}
