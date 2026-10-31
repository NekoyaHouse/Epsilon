package com.github.epsilon.settings.impl

import com.github.epsilon.settings.Setting

class StringListSetting(
    name: String,
    defaultValue: Collection<String>,
    visible: Visibility?,
) : Setting<List<String>>(name, visible, null) {

    init {
        val normalized = normalize(defaultValue)
        initializeDefault(normalized)
        initializeCurrent(ArrayList(normalized))
    }

    override var value: List<String>
        get() = super.value
        set(newValue) {
            writeValue(normalize(newValue))
        }

    override fun setValueSilently(newValue: List<String>) {
        writeValueSilently(normalize(newValue))
    }

    override fun reset() {
        value = ArrayList(defaultValue)
    }

    fun contains(entry: String): Boolean = value.contains(entry)

    fun add(entry: String?) {
        if (entry == null || value.contains(entry)) return
        val next = ArrayList(value)
        next.add(entry)
        value = next
    }

    fun remove(entry: String) {
        if (!value.contains(entry)) return
        val next = ArrayList(value)
        next.remove(entry)
        value = next
    }

    fun toggle(entry: String) {
        if (contains(entry)) {
            remove(entry)
        } else {
            add(entry)
        }
    }

    fun get(index: Int): String = value[index]

    fun size(): Int = value.size

    fun isEmpty(): Boolean = value.isEmpty()

    private fun normalize(source: Collection<String>?): List<String> {
        if (source == null) return ArrayList()
        val result = ArrayList<String>()
        for (entry in source) {
            if (entry != null && !result.contains(entry)) {
                result.add(entry)
            }
        }
        return result
    }

}
