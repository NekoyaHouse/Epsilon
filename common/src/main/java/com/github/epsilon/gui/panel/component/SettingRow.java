package com.github.epsilon.gui.panel.component;

import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.gui.lib.UiRect;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.settings.Setting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;
import org.jspecify.annotations.Nullable;

public abstract class SettingRow<T extends Setting<?>> implements AutoCloseable {

    protected static final TextRenderer FALLBACK_TEXT_METRICS = TextRenderer.create();

    protected final T setting;

    protected SettingRow(T setting) {
        this.setting = setting;
    }

    public T getSetting() {
        return setting;
    }

    public float getHeight() {
        return 28.0f;
    }

    public void buildUi(UiTree.Scope scope, GuiGraphicsExtractor guiGraphics, TextRenderer textRenderer,
                        UiRect bounds, float hoverProgress, int mouseX, int mouseY, float partialTick) {
    }

    public boolean mouseClicked(UiRect bounds, MouseButtonEvent event, boolean isDoubleClick) {
        return false;
    }

    public boolean mouseReleased(UiRect bounds, MouseButtonEvent event) {
        return false;
    }

    public boolean mouseScrolled(UiRect bounds, double mouseX, double mouseY, double scrollX, double scrollY) {
        return false;
    }

    public boolean keyPressed(KeyEvent event) {
        return false;
    }

    public boolean charTyped(CharacterEvent event) {
        return false;
    }

    public boolean preeditUpdated(@Nullable PreeditEvent event) {
        return false;
    }

    public void setFocused(boolean focused) {
    }

    public boolean isFocused() {
        return false;
    }

    public boolean hasActiveAnimation() {
        return false;
    }

    @Override
    public void close() {
    }

}
