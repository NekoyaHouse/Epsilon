package com.github.epsilon.gui.theme

import com.github.epsilon.gui.lib.scene.LayerOffset

/**
 * 对话框 / 卡片类界面的层叠语义。
 *
 * 卡片外壳与内容之间需要一次 layer 提升，否则内容图元可能因为批次按图元种类排序
 * 而被画到外壳下面。
 */
object DialogZ {

    /** 卡片外壳：背景、描边。 */
    val Card: LayerOffset = LayerOffset(0)

    /** 卡片内容：按钮、文字。 */
    val Content: LayerOffset = LayerOffset(10)

}
