package com.github.epsilon.gui.hudeditor;

import com.github.epsilon.elements.HudModule;

public class HudLayoutHelper {

    private HudLayoutHelper() {
    }

    public static float getHorizontalAnchorOffset(HudModule.HorizontalAnchor anchor, float width) {
        return switch (anchor) {
            case Left -> 0.0f;
            case Center -> width / 2.0f;
            case Right -> width;
        };
    }

    public static float getVerticalAnchorOffset(HudModule.VerticalAnchor anchor, float height) {
        return switch (anchor) {
            case Top -> 0.0f;
            case Center -> height / 2.0f;
            case Bottom -> height;
        };
    }

    public static float getRenderX(HudModule.HorizontalAnchor anchor, float anchorX, float width, int screenWidth) {
        return switch (anchor) {
            case Left -> anchorX;
            case Center -> screenWidth / 2.0f - width / 2.0f + anchorX;
            case Right -> screenWidth - width + anchorX;
        };
    }

    public static float getRenderY(HudModule.VerticalAnchor anchor, float anchorY, float height, int screenHeight) {
        return switch (anchor) {
            case Top -> anchorY;
            case Center -> screenHeight / 2.0f - height / 2.0f + anchorY;
            case Bottom -> screenHeight - height + anchorY;
        };
    }

    public static float toAnchorX(HudModule.HorizontalAnchor anchor, float renderX, float width, int screenWidth) {
        return switch (anchor) {
            case Left -> renderX;
            case Center -> renderX + width / 2.0f - screenWidth / 2.0f;
            case Right -> renderX + width - screenWidth;
        };
    }

    public static float toAnchorY(HudModule.VerticalAnchor anchor, float renderY, float height, int screenHeight) {
        return switch (anchor) {
            case Top -> renderY;
            case Center -> renderY + height / 2.0f - screenHeight / 2.0f;
            case Bottom -> renderY + height - screenHeight;
        };
    }

    public static HudModule.HorizontalAnchor resolveHorizontalAnchor(float renderX, float width, int screenWidth) {
        float splitLeft = screenWidth / 3.0f;
        float splitRight = splitLeft * 2.0f;

        boolean left = renderX <= splitLeft;
        boolean right = renderX + width >= splitRight;

        if ((left && right) || (!left && !right)) {
            return HudModule.HorizontalAnchor.Center;
        }

        return left ? HudModule.HorizontalAnchor.Left : HudModule.HorizontalAnchor.Right;
    }

    public static HudModule.VerticalAnchor resolveVerticalAnchor(float renderY, float height, int screenHeight) {
        float splitTop = screenHeight / 3.0f;
        float splitBottom = splitTop * 2.0f;

        boolean top = renderY <= splitTop;
        boolean bottom = renderY + height >= splitBottom;

        if ((top && bottom) || (!top && !bottom)) {
            return HudModule.VerticalAnchor.Center;
        }

        return top ? HudModule.VerticalAnchor.Top : HudModule.VerticalAnchor.Bottom;
    }

    public static float getAnchorPointX(HudModule.HorizontalAnchor anchor, float renderX, float width) {
        return renderX + getHorizontalAnchorOffset(anchor, width);
    }

    public static float getAnchorPointY(HudModule.VerticalAnchor anchor, float renderY, float height) {
        return renderY + getVerticalAnchorOffset(anchor, height);
    }

}
