package com.github.epsilon.graphics.schedulers.render2d

/** 先取出再执行，后续 flush 无法重放已经消费的提交；选择层时保留其他层。 */
internal class OrderedSubmissionQueue<T>(private val layerOf: (T) -> Int, private val sequenceOf: (T) -> Long) :
    ArrayList<T>() {
    fun consume(layer: Int? = null): List<T> {
        val selected = filter { layer == null || layerOf(it) == layer }
            .sortedWith(compareBy<T> { layerOf(it) }.thenBy { sequenceOf(it) })
        removeAll { layer == null || layerOf(it) == layer }
        return selected
    }
}
