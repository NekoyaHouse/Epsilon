package com.github.epsilon.gui.hudeditor

import com.github.epsilon.elements.HudModule

object HudLayoutHelper {

    fun getHorizontalAnchorOffset(anchor: HudModule.HorizontalAnchor, width: Float): Float {
        return when (anchor) {
            HudModule.HorizontalAnchor.Left -> 0.0f
            HudModule.HorizontalAnchor.Center -> width / 2.0f
            HudModule.HorizontalAnchor.Right -> width
        }
    }

    fun getVerticalAnchorOffset(anchor: HudModule.VerticalAnchor, height: Float): Float {
        return when (anchor) {
            HudModule.VerticalAnchor.Top -> 0.0f
            HudModule.VerticalAnchor.Center -> height / 2.0f
            HudModule.VerticalAnchor.Bottom -> height
        }
    }

    fun getRenderX(anchor: HudModule.HorizontalAnchor, anchorX: Float, width: Float, screenWidth: Int): Float {
        return when (anchor) {
            HudModule.HorizontalAnchor.Left -> anchorX
            HudModule.HorizontalAnchor.Center -> screenWidth / 2.0f - width / 2.0f + anchorX
            HudModule.HorizontalAnchor.Right -> screenWidth - width + anchorX
        }
    }

    fun getRenderY(anchor: HudModule.VerticalAnchor, anchorY: Float, height: Float, screenHeight: Int): Float {
        return when (anchor) {
            HudModule.VerticalAnchor.Top -> anchorY
            HudModule.VerticalAnchor.Center -> screenHeight / 2.0f - height / 2.0f + anchorY
            HudModule.VerticalAnchor.Bottom -> screenHeight - height + anchorY
        }
    }

    fun toAnchorX(anchor: HudModule.HorizontalAnchor, renderX: Float, width: Float, screenWidth: Int): Float {
        return when (anchor) {
            HudModule.HorizontalAnchor.Left -> renderX
            HudModule.HorizontalAnchor.Center -> renderX + width / 2.0f - screenWidth / 2.0f
            HudModule.HorizontalAnchor.Right -> renderX + width - screenWidth
        }
    }

    fun toAnchorY(anchor: HudModule.VerticalAnchor, renderY: Float, height: Float, screenHeight: Int): Float {
        return when (anchor) {
            HudModule.VerticalAnchor.Top -> renderY
            HudModule.VerticalAnchor.Center -> renderY + height / 2.0f - screenHeight / 2.0f
            HudModule.VerticalAnchor.Bottom -> renderY + height - screenHeight
        }
    }

    fun resolveHorizontalAnchor(renderX: Float, width: Float, screenWidth: Int): HudModule.HorizontalAnchor {
        val splitLeft = screenWidth / 3.0f
        val splitRight = splitLeft * 2.0f

        val left = renderX <= splitLeft
        val right = renderX + width >= splitRight

        if ((left && right) || (!left && !right)) {
            return HudModule.HorizontalAnchor.Center
        }

        return if (left) HudModule.HorizontalAnchor.Left else HudModule.HorizontalAnchor.Right
    }

    fun resolveVerticalAnchor(renderY: Float, height: Float, screenHeight: Int): HudModule.VerticalAnchor {
        val splitTop = screenHeight / 3.0f
        val splitBottom = splitTop * 2.0f

        val top = renderY <= splitTop
        val bottom = renderY + height >= splitBottom

        if ((top && bottom) || (!top && !bottom)) {
            return HudModule.VerticalAnchor.Center
        }

        return if (top) HudModule.VerticalAnchor.Top else HudModule.VerticalAnchor.Bottom
    }

    fun getAnchorPointX(anchor: HudModule.HorizontalAnchor, renderX: Float, width: Float): Float {
        return renderX + getHorizontalAnchorOffset(anchor, width)
    }

    fun getAnchorPointY(anchor: HudModule.VerticalAnchor, renderY: Float, height: Float): Float {
        return renderY + getVerticalAnchorOffset(anchor, height)
    }

}
