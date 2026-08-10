package com.github.epsilon.elements.impl.island.instance;

import com.github.epsilon.elements.impl.island.LandRenderer;
import com.github.epsilon.elements.impl.island.pattern.LCPattern;
import com.github.epsilon.elements.impl.island.pattern.impl.CheckPattern;
import com.github.epsilon.elements.impl.island.pattern.impl.DelayPattern;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.utils.timer.TimerUtils;
import net.minecraft.util.Mth;

import java.awt.*;

/**
 * 岛屿容器中的一屏内容。
 * <p>
 * 每个实例只负责声明自己期望的目标尺寸与绘制内容，容器的形变动画由
 * {@link LandRenderer} 统一插值。
 */
public abstract class LandInstance {

    protected final LCPattern pattern;
    private final int priority;

    private final TimerUtils timer = new TimerUtils();

    protected LandInstance(LCPattern pattern, int priority) {
        this.pattern = pattern;
        this.priority = priority;
    }

    protected float targetRadius;
    protected float targetWidth;
    protected float targetHeight;

    private float contentAlpha = 1.0f;

    /**
     * 每帧刷新实例的数据与目标尺寸。
     */
    public abstract void update();

    /**
     * 绘制实例内容。
     *
     * @param scope      绘制作用域
     * @param translateX 容器左上角 X
     * @param translateY 容器左上角 Y
     * @param width      容器当前宽度
     * @param height     容器当前高度
     */
    public abstract void draw(UiTree.Scope scope, float translateX, float translateY, float width, float height);

    /**
     * 实例离开控制器时释放自身持有的临时资源。
     */
    public void onRemoved() {
    }

    public void setContentAlpha(float contentAlpha) {
        this.contentAlpha = Mth.clamp(contentAlpha, 0.0f, 1.0f);
    }

    protected Color fade(Color color) {
        if (contentAlpha >= 1.0f) return color;
        int alpha = Mth.clamp((int) (color.getAlpha() * contentAlpha), 0, 255);
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    public float getTargetRadius() {
        return targetRadius;
    }

    public float getTargetWidth() {
        return targetWidth;
    }

    public float getTargetHeight() {
        return targetHeight;
    }

    public boolean isClosed() {
        return checkPattern();
    }

    public boolean checkPattern() {
        return switch (pattern) {
            case CheckPattern check -> check.getSupplier().get();
            case DelayPattern delay -> timer.passedMillise(delay.getDelay());
            default -> false;
        };
    }

    public int getPriority() {
        return priority;
    }

}
