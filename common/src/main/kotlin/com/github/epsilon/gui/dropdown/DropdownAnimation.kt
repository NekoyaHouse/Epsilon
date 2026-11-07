package com.github.epsilon.gui.dropdown

import com.github.epsilon.utils.render.animation.Animation

/**
 * 动画是否正处于推进中。
 *
 * 与 `!isFinished` 的区别：从未被 `run` 过的 [Animation] 其 `isFinished` 也是 `false`，
 * 但这类动画（例如折叠分组里从未绘制的控件）并不会产生逐帧变化。把它们当作「活动」
 * 会让上层内容层误判为需要每帧重建，从而完全失去 recompose 缓存的意义。
 */
internal fun Animation.isAdvancing(): Boolean {
    return !isFinished && millis != 0L
}
