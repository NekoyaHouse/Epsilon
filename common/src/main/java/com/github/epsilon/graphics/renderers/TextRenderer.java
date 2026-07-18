package com.github.epsilon.graphics.renderers;

import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.text.ITextRenderer;
import com.github.epsilon.graphics.text.StaticFontLoader;
import com.github.epsilon.graphics.text.ttf.TtfFontLoader;
import com.github.epsilon.graphics.text.ttf.TtfTextRenderer;
import com.github.epsilon.holders.RendererHolder;
import com.mojang.blaze3d.systems.RenderPass;

import java.awt.*;

public class TextRenderer implements IRenderer {

    private final ITextRenderer textRenderer;
    private boolean registered = true;

    private TextRenderer(long bufferSize) {
        textRenderer = new TtfTextRenderer(bufferSize);
    }

    private TextRenderer() {
        textRenderer = new TtfTextRenderer();
    }

    public static TextRenderer create(long bufferSize) {
        return RendererHolder.INSTANCE.register(new TextRenderer(bufferSize));
    }

    public static TextRenderer create() {
        return RendererHolder.INSTANCE.register(new TextRenderer());
    }

    public void addText(String text, float x, float y, float scale, Color color, TtfFontLoader fontLoader) {
        ensureRegistered();
        textRenderer.addText(text, x, y, scale, color, fontLoader);
    }

    public void addGradientText(String text, float x, float y, float scale, Color startColor, Color endColor, TtfFontLoader fontLoader) {
        ensureRegistered();
        textRenderer.addGradientText(text, x, y, scale, startColor, endColor, fontLoader);
    }

    public void addRotatedText(String text, float x, float y, float scale, Color color, TtfFontLoader fontLoader, float originX, float originY, float rotationDegrees) {
        ensureRegistered();
        textRenderer.addRotatedText(text, x, y, scale, color, fontLoader, originX, originY, rotationDegrees);
    }

    public void addText(String text, float x, float y, float scale, Color color) {
        textRenderer.addText(text, x, y, scale, color, StaticFontLoader.defaultFont());
    }

    public void addRotatedText(String text, float x, float y, float scale, Color color, float originX, float originY, float rotationDegrees) {
        textRenderer.addRotatedText(text, x, y, scale, color, StaticFontLoader.defaultFont(), originX, originY, rotationDegrees);
    }

    public void addGradientText(String text, float x, float y, float scale, Color startColor, Color endColor) {
        textRenderer.addGradientText(text, x, y, scale, startColor, endColor, StaticFontLoader.defaultFont());
    }

    public void addText(String text, float x, float y, Color color, TtfFontLoader fontLoader) {
        textRenderer.addText(text, x, y, 1.0f, color, fontLoader);
    }

    public void addText(String text, float x, float y, Color color) {
        textRenderer.addText(text, x, y, 1.0f, color, StaticFontLoader.defaultFont());
    }

    public float getHeight(float scale) {
        return textRenderer.getHeight(scale, StaticFontLoader.defaultFont());
    }

    public float getHeight(float scale, TtfFontLoader fontLoader) {
        return textRenderer.getHeight(scale, fontLoader);
    }

    public float getWidth(String text, float scale) {
        return textRenderer.getWidth(text, scale, StaticFontLoader.defaultFont());
    }

    public float getWidth(String text, float scale, TtfFontLoader fontLoader) {
        return textRenderer.getWidth(text, scale, fontLoader);
    }

    public void setScissor(int x, int y, int width, int height) {
        textRenderer.setScissor(x, y, width, height);
    }

    public void clearScissor() {
        textRenderer.clearScissor();
    }

    @Override
    public void draw() {
        LuminRenderSystem.applyOrthoProjection();
        textRenderer.draw();
    }

    @Override
    public boolean prepareSharedDraw() {
        return textRenderer.prepareSharedDraw();
    }

    @Override
    public void draw(RenderPass pass) {
        textRenderer.draw(pass);
    }

    @Override
    public void clear() {
        textRenderer.clear();
    }

    @Override
    public void close() {
        textRenderer.close();
        if (registered) {
            RendererHolder.INSTANCE.unregister(this);
            registered = false;
        }
    }

    private void ensureRegistered() {
        if (!registered) {
            RendererHolder.INSTANCE.register(this);
            registered = true;
        }
    }

}
