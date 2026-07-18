package com.github.epsilon.graphics.text;

import com.github.epsilon.graphics.text.ttf.TtfFontLoader;
import com.mojang.blaze3d.systems.RenderPass;

import java.awt.*;

public interface ITextRenderer {

    void addText(String text, float x, float y, float scale, Color color, TtfFontLoader fontLoader);

    default void addRotatedText(String text, float x, float y, float scale, Color color,
                                TtfFontLoader fontLoader, float originX, float originY, float rotationDegrees) {
        addText(text, x, y, scale, color, fontLoader);
    }

    default void addGradientText(String text, float x, float y, float scale, Color startColor, Color endColor, TtfFontLoader fontLoader) {
        addText(text, x, y, scale, startColor, fontLoader);
    }

    void draw();

    /**
     * 在共享 RenderPass 打开前完成所有缓冲映射、uniform 写入和 atlas 准备。
     */
    default boolean prepareSharedDraw() {
        return false;
    }

    /**
     * 写入已经绑定好 pipeline 的 RenderPass，只允许绑定状态并提交 draw。
     */
    default void draw(RenderPass pass) {
    }

    void clear();

    void close();

    float getHeight(float scale, TtfFontLoader fontLoader);

    float getWidth(String text, float scale, TtfFontLoader fontLoader);

    default void setScissor(int x, int y, int width, int height) {
    }

    default void clearScissor() {
    }

}
