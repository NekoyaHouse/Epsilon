package com.github.epsilon.gui.lib.control;

import com.github.epsilon.gui.lib.UiRect;
import com.github.epsilon.gui.lib.UiTheme;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;

import java.util.Objects;

/**
 * 轻量滚动条，统一提供绘制、命中测试和拖动值换算。
 * <p>
 * 控件只接收通用坐标与 {@link UiTheme}，不持有 Panel 或 Dropdown 状态。
 */
public final class UiScrollBar {

    public static final float WIDTH = 2.0f;
    public static final float RIGHT_INSET = 2.5f;
    public static final float MIN_THUMB_HEIGHT = 10.0f;
    public static final float HIT_WIDTH = 10.0f;
    public static final float HOVER_WIDTH = 2.5f;
    public static final float TOTAL_WIDTH = HIT_WIDTH;

    private final UiTheme theme;
    private final Animation hoverAnimation;
    private boolean dragging;
    private float dragOffset;

    public UiScrollBar(UiTheme theme) {
        this.theme = Objects.requireNonNull(theme, "theme");
        this.hoverAnimation = new Animation(Easing.EASE_OUT_CUBIC, theme.hoverAnimationDuration());
    }

    public boolean isDragging() {
        return dragging;
    }

    public void draw(UiTree.Scope scope, UiRect viewport, float scroll, float maxScroll,
                     float contentHeight, double mouseX, double mouseY) {
        Geometry geometry = computeGeometry(viewport, scroll, maxScroll, contentHeight);
        if (geometry == null) {
            hoverAnimation.run(0.0f);
            return;
        }
        hoverAnimation.run(geometry.trackContains(mouseX, mouseY) || dragging ? 1.0f : 0.0f);
        draw(scope, geometry, hoverAnimation.getValue(), theme);
    }

    public static void draw(UiTree.Scope scope, UiTheme theme, UiRect viewport, float scroll, float maxScroll,
                            float contentHeight) {
        Geometry geometry = computeGeometry(viewport, scroll, maxScroll, contentHeight);
        if (geometry != null) {
            draw(scope, geometry, 0.0f, theme);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, UiRect viewport, float scroll,
                                float maxScroll, float contentHeight) {
        Geometry geometry = computeGeometry(viewport, scroll, maxScroll, contentHeight);
        if (geometry == null || !geometry.trackContains(mouseX, mouseY)) {
            return false;
        }
        dragging = true;
        dragOffset = geometry.thumbContains(mouseX, mouseY)
                ? (float) mouseY - geometry.thumbY()
                : geometry.thumbHeight() / 2.0f;
        return true;
    }

    public float mouseDragged(double mouseY, UiRect viewport, float maxScroll, float contentHeight) {
        if (!dragging || maxScroll <= 0.0f) {
            return -1.0f;
        }
        return scrollFromThumbTopY((float) mouseY - dragOffset, viewport, maxScroll, contentHeight);
    }

    public boolean mouseReleased() {
        if (!dragging) {
            return false;
        }
        dragging = false;
        return true;
    }

    public void reset() {
        dragging = false;
        hoverAnimation.run(0.0f);
    }

    public boolean isHovered(double mouseX, double mouseY, UiRect viewport, float scroll,
                             float maxScroll, float contentHeight) {
        Geometry geometry = computeGeometry(viewport, scroll, maxScroll, contentHeight);
        return geometry != null && geometry.trackContains(mouseX, mouseY);
    }

    public static Geometry computeGeometry(UiRect viewport, float scroll, float maxScroll, float contentHeight) {
        if (maxScroll <= 0.0f || contentHeight <= viewport.height()) {
            return null;
        }
        float trackHeight = viewport.height();
        if (trackHeight <= 0.5f) {
            return null;
        }
        float thumbHeight = Math.min(trackHeight, Math.max(MIN_THUMB_HEIGHT, viewport.height() / contentHeight * trackHeight));
        float thumbTravel = trackHeight - thumbHeight;
        float scrollRatio = maxScroll > 0.0f ? scroll / maxScroll : 0.0f;
        float thumbY = viewport.y() + thumbTravel * Math.clamp(scrollRatio, 0.0f, 1.0f);
        float thumbX = viewport.right() - RIGHT_INSET;
        float trackX = viewport.right() - HIT_WIDTH;
        return new Geometry(thumbX, thumbY, WIDTH, thumbHeight, trackX, viewport.y(), HIT_WIDTH, trackHeight);
    }

    public static float scrollFromThumbTopY(float thumbTopY, UiRect viewport, float maxScroll, float contentHeight) {
        Geometry geometry = computeGeometry(viewport, 0.0f, maxScroll, contentHeight);
        if (geometry == null) {
            return 0.0f;
        }
        float thumbTravel = geometry.trackHeight() - geometry.thumbHeight();
        if (thumbTravel <= 0.0f) {
            return 0.0f;
        }
        float ratio = (thumbTopY - geometry.trackY()) / thumbTravel;
        return Math.clamp(ratio, 0.0f, 1.0f) * maxScroll;
    }

    private static void draw(UiTree.Scope scope, Geometry geometry, float hoverProgress, UiTheme theme) {
        float thumbWidth = geometry.thumbWidth() + (HOVER_WIDTH - geometry.thumbWidth()) * hoverProgress;
        float thumbX = geometry.thumbX() - (thumbWidth - geometry.thumbWidth()) * 0.5f;
        scope.roundRect(thumbX, geometry.thumbY(), thumbWidth, geometry.thumbHeight(),
                thumbWidth * 0.5f, theme.scrollBar(hoverProgress));
    }

    public record Geometry(float thumbX, float thumbY, float thumbWidth, float thumbHeight,
                           float trackX, float trackY, float trackWidth, float trackHeight) {
        public boolean thumbContains(double mouseX, double mouseY) {
            return mouseX >= trackX && mouseX <= trackX + trackWidth && mouseY >= thumbY && mouseY <= thumbY + thumbHeight;
        }

        public boolean trackContains(double mouseX, double mouseY) {
            return mouseX >= trackX && mouseX <= trackX + trackWidth && mouseY >= trackY && mouseY <= trackY + trackHeight;
        }
    }
}
