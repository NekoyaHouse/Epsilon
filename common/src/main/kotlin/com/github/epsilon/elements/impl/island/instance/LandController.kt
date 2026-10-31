package com.github.epsilon.elements.impl.island.instance

/**
 * 岛屿实例的注册表，负责回收已关闭实例并选出当前应当展示的那一个。
 */
object LandController {

    private val instances: MutableList<LandInstance> = ArrayList()

    fun post(instance: LandInstance?) {
        if (instance == null) {
            return
        }
        instances.add(instance)
    }

    /**
     * 移除已关闭实例，并返回优先级最高的实例。
     *
     * @return 当前应展示的实例，没有实例时返回 null
     */
    fun update(): LandInstance? {
        val iterator = instances.iterator()
        while (iterator.hasNext()) {
            val instance = iterator.next()
            if (instance.isClosed) {
                iterator.remove()
                instance.onRemoved()
            }
        }

        if (instances.isEmpty()) return null

        val comparator = Comparator.comparingInt<LandInstance> { it.priority }

        var best: LandInstance? = null
        for (i in instances.indices.reversed()) {
            val current = instances[i]
            if (best == null || comparator.compare(current, best) > 0) {
                best = current
            }
        }

        return best
    }

    fun removeInstances(type: Class<out LandInstance>?) {
        if (type == null) return
        val iterator = instances.iterator()
        while (iterator.hasNext()) {
            val instance = iterator.next()
            if (type.isInstance(instance)) {
                iterator.remove()
                instance.onRemoved()
            }
        }
    }

    fun <T : LandInstance> getInstance(type: Class<T>?): T? {
        if (type == null) return null
        for (instance in instances) {
            if (type.isInstance(instance)) {
                return type.cast(instance)
            }
        }
        return null
    }

}
