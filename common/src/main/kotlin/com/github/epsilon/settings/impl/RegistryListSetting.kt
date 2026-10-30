package com.github.epsilon.settings.impl

import com.github.epsilon.settings.Setting
import com.github.epsilon.utils.world.BlockRegistryUtils
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent
import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Block
import java.util.ArrayList
import java.util.function.Predicate

class RegistryListSetting<T : Any>(
    name: String,
    defaultValue: Collection<T>?,
    val registryType: Type,
    filter: Predicate<T>?,
    dependency: Setting.Dependency?,
) : Setting<List<T>>(name, dependency, null) {

    enum class Type {
        BLOCK,
        ITEM,
        ENTITY_TYPE,
        SOUND_EVENT,
        ENCHANTMENT;

        @Suppress("UNCHECKED_CAST")
        fun <R : Any> registry(): Registry<R>? = when (this) {
            BLOCK -> BuiltInRegistries.BLOCK
            ITEM -> BuiltInRegistries.ITEM
            ENTITY_TYPE -> BuiltInRegistries.ENTITY_TYPE
            SOUND_EVENT -> BuiltInRegistries.SOUND_EVENT
            ENCHANTMENT -> null
        } as Registry<R>?

        @Suppress("UNCHECKED_CAST")
        fun <R> defaultFilter(): Predicate<R> = when (this) {
            BLOCK -> Predicate<Block> { block -> BlockRegistryUtils.isSelectable(block) } as Predicate<R>
            ITEM -> Predicate<Item> { item -> item !== Items.AIR } as Predicate<R>
            ENTITY_TYPE -> Predicate<EntityType<*>> { entityType -> entityType != null } as Predicate<R>
            SOUND_EVENT -> Predicate<SoundEvent> { sound -> sound != null } as Predicate<R>
            ENCHANTMENT -> Predicate<String> { id -> id != null && Identifier.tryParse(id) != null } as Predicate<R>
        }

        fun toId(entry: Any): String {
            if (this == ENCHANTMENT) {
                return entry as? String ?: ""
            }
            val registry = registry<Any>() ?: return ""
            val key: Identifier? = registry.getKey(entry)
            if (key == null) return ""
            return key.toString()
        }

        @Suppress("UNCHECKED_CAST")
        fun <R : Any> fromId(id: String): R? {
            val loc = Identifier.tryParse(id) ?: return null
            if (this == ENCHANTMENT) {
                return loc.toString() as R
            }
            val registry = registry<R>() ?: return null
            return registry.getOptional(loc).orElse(null)
        }
    }

    private val registry: Registry<T>? = registryType.registry<T>()

    private val filterRef: Predicate<T>? = filter ?: registryType.defaultFilter()

    init {
        val normalized = normalize(defaultValue)
        initializeDefault(normalized)
        initializeCurrent(ArrayList(normalized))
    }

    override var value: List<T>
        get() = super.value
        set(newValue) {
            writeValue(normalize(newValue))
        }

    override fun setValueSilently(newValue: List<T>) {
        writeValueSilently(normalize(newValue))
    }

    override fun reset() {
        value = ArrayList(defaultValue)
    }

    fun contains(entry: T): Boolean = value.contains(entry)

    fun add(entry: T?) {
        if (!canUse(entry) || value.contains(entry)) return
        val next = ArrayList(value)
        next.add(entry as T)
        value = next
    }

    fun remove(entry: T) {
        if (!value.contains(entry)) return
        val next = ArrayList(value)
        next.remove(entry)
        value = next
    }

    fun toggle(entry: T?) {
        if (entry != null && contains(entry)) {
            remove(entry)
        } else {
            add(entry)
        }
    }

    fun getIds(): List<String> {
        val ids = ArrayList<String>()
        for (entry in value) {
            val id = registryType.toId(entry)
            if (id.isNotBlank()) {
                ids.add(id)
            }
        }
        return ids
    }

    fun setIds(ids: Collection<String>?) {
        val entries = ArrayList<T>()
        if (ids != null) {
            for (id in ids) {
                val entry = registryType.fromId<T>(id)
                if (canUse(entry) && !entries.contains(entry)) {
                    entries.add(entry as T)
                }
            }
        }
        value = entries
    }

    fun size(): Int = value.size

    fun isEmpty(): Boolean = value.isEmpty()

    fun getRegistry(): Registry<T>? = registry

    fun getFilter(): Predicate<T>? = filterRef

    private fun normalize(source: Collection<T>?): List<T> {
        if (source == null) return ArrayList()
        val result = ArrayList<T>()
        for (entry in source) {
            if (canUse(entry) && !result.contains(entry)) {
                result.add(entry as T)
            }
        }
        return result
    }

    private fun canUse(entry: T?): Boolean =
        entry != null && (filterRef == null || filterRef.test(entry))

}
