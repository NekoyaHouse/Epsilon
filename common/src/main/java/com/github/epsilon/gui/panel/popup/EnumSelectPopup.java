package com.github.epsilon.gui.panel.popup;

import com.github.epsilon.graphics.text.IconChars;
import com.github.epsilon.graphics.text.StaticFontLoader;
import com.github.epsilon.gui.lib.UiRect;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.gui.lib.render.UiContentBuffer;
import com.github.epsilon.gui.lib.render.UiRenderBatch;
import com.github.epsilon.gui.theme.EpsilonUiTheme;
import com.github.epsilon.gui.theme.MD3Theme;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

import java.awt.*;

public class EnumSelectPopup implements PanelPopupHost.Popup {

    public static final int MAX_VISIBLE_ITEMS = 5;
    private static final float ITEM_HEIGHT = 24.0f;
    private static final float ITEM_INNER_HEIGHT = 22.0f;
    private static final float CONTENT_PADDING = 6.0f;

    private final UiRect bounds;
    private final EnumSetting<?> setting;
    private final boolean scrollable;
    private final float maxScroll;
    private float scroll;

    private final UiContentBuffer contentBuffer = new UiContentBuffer(EpsilonUiTheme.INSTANCE);
    private final Animation openAnimation = new Animation(Easing.EASE_OUT_CUBIC, 140L);
    private int hoveredIndex = -1;

    public EnumSelectPopup(UiRect bounds, EnumSetting<?> setting) {
        this.setting = setting;

        int optionCount = setting.getModes().length;
        this.scrollable = optionCount > MAX_VISIBLE_ITEMS;
        if (scrollable) {
            float cappedHeight = MAX_VISIBLE_ITEMS * ITEM_HEIGHT + CONTENT_PADDING * 2;
            this.bounds = new UiRect(bounds.x(), bounds.y(), bounds.width(), cappedHeight);
        } else {
            this.bounds = bounds;
        }

        float fullContentHeight = optionCount * ITEM_HEIGHT;
        float viewportHeight = this.bounds.height() - CONTENT_PADDING * 2;
        this.maxScroll = Math.max(0, fullContentHeight - viewportHeight);

        this.openAnimation.setStartValue(0.0f);
    }

    public EnumSetting<?> getSetting() {
        return setting;
    }

    @Override
    public UiRect getBounds() {
        return bounds;
    }

    @Override
    public void extractGui(GuiGraphicsExtractor guiGraphics, UiRenderBatch renderBatch, int mouseX, int mouseY, float partialTick) {
        contentBuffer.clear();
        UiTree popupTree = UiTree.build(scope -> {
            float progress = scope.animate(openAnimation, 1.0f);
            float popupY = bounds.y() - (1.0f - progress) * 6.0f;
            float viewportHeight = bounds.height() - CONTENT_PADDING * 2;
            float fullContentHeight = setting.getModes().length * ITEM_HEIGHT;
            float itemAreaWidth = bounds.width() - CONTENT_PADDING * 2 - (scrollable ? 6.0f : 0.0f);
            UiRect viewportBounds = new UiRect(bounds.x() + CONTENT_PADDING, popupY + CONTENT_PADDING,
                    bounds.width() - CONTENT_PADDING * 2, viewportHeight);

            UiRect popupBounds = new UiRect(bounds.x(), popupY, bounds.width(), bounds.height());
            scope.pushAbsolute(popupBounds, popup -> {
                popup.popupCard(popupBounds.atOrigin(),
                        MD3Theme.CARD_RADIUS,
                        MD3Theme.POPUP_SHADOW_BLUR,
                        MD3Theme.withAlpha(MD3Theme.SHADOW, (int) (MD3Theme.POPUP_SHADOW_ALPHA * progress)),
                        MD3Theme.withAlpha(MD3Theme.SURFACE_CONTAINER_LOW, 255));

                hoveredIndex = -1;
                UiRect localViewportBounds = viewportBounds.relativeTo(popupBounds);
                popup.viewport(contentBuffer, localViewportBounds, scroll, maxScroll, fullContentHeight, content -> {
                    Enum<?>[] modes = setting.getModes();
                    float itemStartY = popupY + CONTENT_PADDING - scroll;
                    for (int i = 0; i < modes.length; i++) {
                        float itemY = itemStartY + i * ITEM_HEIGHT;
                        UiRect itemBounds = new UiRect(bounds.x() + CONTENT_PADDING, itemY, itemAreaWidth, ITEM_INNER_HEIGHT);
                        boolean visible = itemY + ITEM_INNER_HEIGHT > viewportBounds.y() && itemY < viewportBounds.bottom();
                        boolean hovered = visible && itemBounds.contains(mouseX, mouseY)
                                && mouseY >= viewportBounds.y() && mouseY <= viewportBounds.bottom();
                        if (hovered) {
                            hoveredIndex = i;
                        }
                        boolean selected = i == setting.getModeIndex();
                        Color baseBackground = MD3Theme.withAlpha(MD3Theme.SURFACE_CONTAINER_HIGHEST, 0);
                        Color hoverBackground = MD3Theme.lerp(MD3Theme.SURFACE_CONTAINER_HIGH, MD3Theme.SURFACE_CONTAINER_HIGHEST, 0.55f);
                        Color selectedBackground = MD3Theme.SECONDARY_CONTAINER;
                        Color background = selected ? selectedBackground : (hovered ? hoverBackground : baseBackground);
                        Color textColor = selected ? MD3Theme.ON_SECONDARY_CONTAINER : (hovered ? MD3Theme.withAlpha(MD3Theme.TEXT_PRIMARY, 255) : MD3Theme.TEXT_SECONDARY);
                        UiRect localItemBounds = new UiRect(0.0f, i * ITEM_HEIGHT, itemAreaWidth, ITEM_INNER_HEIGHT);
                        content.roundRect(localItemBounds.x(), localItemBounds.y(), localItemBounds.width(), localItemBounds.height(), 8.0f, background);
                        float textScale = 0.62f;
                        float textHeight = contentBuffer.textMetrics().getHeight(textScale);
                        float textY = localItemBounds.y() + (localItemBounds.height() - textHeight) / 2.0f;
                        if (selected) {
                            float iconScale = 0.72f;
                            float iconHeight = contentBuffer.textMetrics().getHeight(iconScale, StaticFontLoader.ICONS);
                            float iconY = localItemBounds.y() + (localItemBounds.height() - iconHeight) / 2.0f;
                            // TODO: 换个更合适的 icon
                            content.text(IconChars.KEYBOARD_ARROW_DOWN, localItemBounds.x() + 8.0f, iconY, iconScale, MD3Theme.ON_SECONDARY_CONTAINER, StaticFontLoader.ICONS);
                        }
                        content.text(setting.getTranslatedValueByIndex(i), localItemBounds.x() + (selected ? 22.0f : 10.0f), textY, textScale, textColor);
                    }
                });
            });
        });
        renderBatch.render(popupTree);
    }

    @Override
    public void flush(UiRenderBatch renderBatch) {
        contentBuffer.flushAndClear();
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        if (!bounds.contains(event.x(), event.y()) || event.button() != 0) {
            return false;
        }
        Enum[] modes = setting.getModes();
        if (hoveredIndex < 0 || hoveredIndex >= modes.length) {
            return false;
        }
        ((EnumSetting) setting).setMode(modes[hoveredIndex]);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!scrollable || maxScroll <= 0.0f) {
            return false;
        }
        float nextScroll = scroll - (float) scrollY * 20.0f;
        scroll = Math.clamp(nextScroll, 0.0f, maxScroll);
        return true;
    }

    @Override
    public boolean shouldCloseAfterClick() {
        return true;
    }

    @Override
    public void close() {
        contentBuffer.close();
    }

}
