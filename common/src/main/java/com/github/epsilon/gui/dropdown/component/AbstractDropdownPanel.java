package com.github.epsilon.gui.dropdown.component;

import com.github.epsilon.assets.i18n.TranslateComponent;
import com.github.epsilon.graphics.text.StaticFontLoader;
import com.github.epsilon.gui.dropdown.DropdownScreen;
import com.github.epsilon.gui.dropdown.DropdownTheme;
import com.github.epsilon.gui.dropdown.ReisaDropdownCompanion;
import com.github.epsilon.gui.lib.UiRect;
import com.github.epsilon.gui.lib.UiTextMetrics;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.gui.lib.control.UiScrollBar;
import com.github.epsilon.gui.theme.EpsilonUiTheme;
import com.github.epsilon.gui.theme.MD3Theme;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;
import net.minecraft.util.Mth;

public abstract class AbstractDropdownPanel implements DropdownPanel {

    protected final String id;
    protected final String title;
    protected final TranslateComponent titleComponent;
    protected final TitleSupplier titleSupplier;
    protected final String icon;
    protected final Animation openAnim = new Animation(Easing.EASE_IN_OUT_CUBIC, DropdownTheme.ANIM_OPEN);
    protected final Animation introAnim;
    protected final UiScrollBar scrollBar = new UiScrollBar(EpsilonUiTheme.INSTANCE);

    protected float x;
    protected float y;
    protected float width = DropdownTheme.PANEL_WIDTH;
    protected boolean opened;
    protected boolean visible;
    protected boolean dragging;
    protected float dragOffsetX;
    protected float dragOffsetY;
    protected float scroll;
    protected float targetScroll;
    protected float maxScroll;
    protected float maxPanelHeight = 300.0f;

    private static final float SCROLL_SMOOTHING = 0.16f;
    private static final float SCROLL_EPSILON = 0.05f;
    private int renderFrameId = Integer.MIN_VALUE;
    private int cachedMetricsFrameId = Integer.MIN_VALUE;
    private float cachedExpand;
    private float cachedContentHeight;
    private float cachedVisibleContentHeight;
    private float cachedPanelHeight;

    protected AbstractDropdownPanel(String id, String title, String icon, int panelIndex) {
        this(id, title, null, icon, panelIndex);
    }

    protected AbstractDropdownPanel(String id, TranslateComponent titleComponent, String icon, int panelIndex) {
        this(id, null, titleComponent, icon, panelIndex);
    }

    protected AbstractDropdownPanel(String id, TitleSupplier titleSupplier, String icon, int panelIndex) {
        this.id = id;
        this.title = null;
        this.titleComponent = null;
        this.titleSupplier = titleSupplier;
        this.icon = icon;
        this.introAnim = new Animation(Easing.EASE_OUT_SINE, 120L + panelIndex * 45L);
    }

    private AbstractDropdownPanel(String id, String title, TranslateComponent titleComponent, String icon, int panelIndex) {
        this.id = id;
        this.title = title;
        this.titleComponent = titleComponent;
        this.titleSupplier = null;
        this.icon = icon;
        this.introAnim = new Animation(Easing.EASE_OUT_SINE, 120L + panelIndex * 45L);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void startIntro() {
        introAnim.setStartValue(0.0f);
        introAnim.run(0.0f);
        introAnim.run(1.0f);
        openAnim.setStartValue(opened ? 1.0f : 0.0f);
    }

    @Override
    public void beginRenderFrame(int frameId) {
        renderFrameId = frameId;
        cachedMetricsFrameId = Integer.MIN_VALUE;
    }

    @Override
    public float getIntroValue() {
        introAnim.run(1.0f);
        return introAnim.getValue();
    }

    @Override
    public void drawBackground(UiTree.Scope scope, UiTextMetrics textMetrics) {
        ensureFrameMetrics();
        float expand = cachedExpand;
        float contentHeight = cachedContentHeight;
        float visibleHeight = cachedVisibleContentHeight;
        updateScroll(contentHeight, visibleHeight, true);
        float panelHeight = cachedPanelHeight;

        scope.shadow(x, y, width, panelHeight, DropdownTheme.PANEL_RADIUS, DropdownTheme.PANEL_SHADOW_BLUR, DropdownTheme.panelShadow());
        scope.roundRect(x, y, width, panelHeight, DropdownTheme.PANEL_RADIUS, DropdownTheme.panelBackground());

        float iconX = x + 7.5f;
        float textX = icon == null || icon.isBlank() ? x + 10.0f : iconX + 16.0f;
        float textY = y + (DropdownTheme.PANEL_HEADER_HEIGHT - textMetrics.textHeight(DropdownTheme.HEADER_TEXT_SCALE)) * 0.5f;
        if (icon != null && !icon.isBlank()) {
            float iconY = y + (DropdownTheme.PANEL_HEADER_HEIGHT - textMetrics.textHeight(DropdownTheme.HEADER_ICON_SCALE, StaticFontLoader.ICONS)) * 0.5f - 2.0f;
            scope.text(icon, iconX, iconY, DropdownTheme.HEADER_ICON_SCALE, MD3Theme.PRIMARY, StaticFontLoader.ICONS);
        }
        String headerTitle = getTitle();
        scope.text(headerTitle, textX, textY, DropdownTheme.HEADER_TEXT_SCALE, MD3Theme.TEXT_PRIMARY);
        scope.triangle(x + width - 10.0f, y + DropdownTheme.PANEL_HEADER_HEIGHT * 0.5f, 3.0f, expand, DropdownTheme.groupChevron(0.0f));

    }

    @Override
    public void drawContent(UiTree.Scope scope, UiTextMetrics textMetrics, int mouseX, int mouseY) {
        ensureFrameMetrics();
        if (cachedExpand < 0.01f) return;

        float contentHeight = cachedContentHeight;
        float visibleHeight = cachedVisibleContentHeight;
        updateScroll(contentHeight, visibleHeight, false);
        UiRect scrollbarViewport = getScrollbarViewport();
        boolean scrollbarHovered = scrollBar.isHovered(mouseX, mouseY, scrollbarViewport, scroll, maxScroll, contentHeight);
        int contentMouseX = scrollbarHovered || scrollBar.isDragging() ? Integer.MIN_VALUE : mouseX;
        int contentMouseY = scrollbarHovered || scrollBar.isDragging() ? Integer.MIN_VALUE : mouseY;
        drawPanelContent(scope, textMetrics, contentMouseX, contentMouseY, visibleHeight);
        scrollBar.draw(scope, scrollbarViewport, scroll, maxScroll, contentHeight, mouseX, mouseY);
    }

    @Override
    public float getContentClipY() {
        return y + DropdownTheme.PANEL_HEADER_HEIGHT;
    }

    @Override
    public float getContentClipHeight() {
        ensureFrameMetrics();
        return cachedVisibleContentHeight * cachedExpand;
    }

    @Override
    public boolean requiresContentScissor() {
        ensureFrameMetrics();
        return cachedExpand < 1.0f || cachedContentHeight > cachedVisibleContentHeight;
    }

    @Override
    public float getPanelHeight() {
        ensureFrameMetrics();
        return cachedPanelHeight;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        ensureFrameMetrics();
        updateScroll(cachedContentHeight, cachedVisibleContentHeight, false);

        if (isHeaderHovered(mouseX, mouseY)) {
            if (button == 0) {
                dragging = true;
                dragOffsetX = (float) (x - mouseX);
                dragOffsetY = (float) (y - mouseY);
                DropdownScreen.INSTANCE.react(ReisaDropdownCompanion.Action.DRAG);
                return true;
            }
            if (button == 1) {
                opened = !opened;
                DropdownScreen.INSTANCE.react(opened
                        ? ReisaDropdownCompanion.Action.PANEL_OPEN
                        : ReisaDropdownCompanion.Action.PANEL_CLOSE);
                return true;
            }
        }

        if (button == 0 && scrollBar.mouseClicked(mouseX, mouseY, getScrollbarViewport(), scroll, maxScroll, cachedContentHeight)) {
            float newScroll = scrollBar.mouseDragged(mouseY, getScrollbarViewport(), maxScroll, cachedContentHeight);
            if (newScroll >= 0.0f) {
                setScrollImmediate(newScroll);
            }
            DropdownScreen.INSTANCE.react(ReisaDropdownCompanion.Action.SLIDER_ADJUST);
            return true;
        }

        if (opened && cachedExpand > 0.5f && isContentHovered(mouseX, mouseY)) {
            return mouseClickedContent(mouseX, mouseY, button);
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && scrollBar.mouseReleased()) {
            return true;
        }
        if (button == 0 && dragging) {
            dragging = false;
            return true;
        }
        return mouseReleasedContent(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY) {
        if (scrollBar.isDragging()) {
            float newScroll = scrollBar.mouseDragged(mouseY, getScrollbarViewport(), maxScroll, cachedContentHeight);
            if (newScroll >= 0.0f) {
                setScrollImmediate(newScroll);
            }
            return true;
        }
        if (dragging) {
            x = (float) (mouseX + dragOffsetX);
            y = (float) (mouseY + dragOffsetY);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!opened) return false;
        if (isPanelHovered(mouseX, mouseY)) {
            targetScroll = Mth.clamp(targetScroll - (float) amount * DropdownTheme.SCROLL_SPEED, 0.0f, maxScroll);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    @Override
    public boolean charTyped(String typedText) {
        return false;
    }

    @Override
    public boolean hasActiveInput() {
        return false;
    }

    @Override
    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public void setMaxPanelHeight(float maxPanelHeight) {
        this.maxPanelHeight = maxPanelHeight;
    }

    @Override
    public float getX() {
        return x;
    }

    @Override
    public float getY() {
        return y;
    }

    @Override
    public float getWidth() {
        return width;
    }

    @Override
    public boolean isOpened() {
        return opened;
    }

    @Override
    public void setOpened(boolean opened) {
        this.opened = opened;
        if (!opened) {
            scrollBar.reset();
            setScrollImmediate(0.0f);
        }
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public void setVisible(boolean visible) {
        this.visible = visible;
        if (!visible) {
            dragging = false;
            scrollBar.reset();
        }
    }

    protected abstract float computeContentHeight();

    protected abstract void drawPanelContent(UiTree.Scope scope, UiTextMetrics textMetrics, int mouseX, int mouseY, float visibleHeight);

    protected boolean mouseClickedContent(double mouseX, double mouseY, int button) {
        return false;
    }

    protected boolean mouseReleasedContent(double mouseX, double mouseY, int button) {
        return false;
    }

    protected String getTitle() {
        if (titleSupplier != null) return titleSupplier.get();
        return titleComponent != null ? titleComponent.getTranslatedName() : title;
    }

    @FunctionalInterface
    public interface TitleSupplier {
        String get();
    }

    protected float computeVisibleContentHeight(float contentHeight) {
        float maxContentHeight = Math.max(0.0f, maxPanelHeight - DropdownTheme.PANEL_HEADER_HEIGHT - DropdownTheme.PANEL_BOTTOM_PADDING);
        return Math.min(contentHeight, maxContentHeight);
    }

    protected int getRenderFrameId() {
        return renderFrameId;
    }

    private void ensureFrameMetrics() {
        if (cachedMetricsFrameId == renderFrameId) {
            return;
        }
        openAnim.run(opened ? 1.0f : 0.0f);
        cachedExpand = openAnim.getValue();
        cachedContentHeight = computeContentHeight();
        cachedVisibleContentHeight = computeVisibleContentHeight(cachedContentHeight);
        cachedPanelHeight = DropdownTheme.PANEL_HEADER_HEIGHT + (cachedVisibleContentHeight + DropdownTheme.PANEL_BOTTOM_PADDING) * cachedExpand;
        cachedMetricsFrameId = renderFrameId;
    }

    protected boolean isHeaderHovered(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + DropdownTheme.PANEL_HEADER_HEIGHT;
    }

    protected boolean isContentHovered(double mouseX, double mouseY) {
        float clipY = getContentClipY();
        float clipH = getContentClipHeight();
        return mouseX >= x && mouseX <= x + width && mouseY >= clipY && mouseY <= clipY + clipH;
    }

    protected boolean isPanelHovered(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + getPanelHeight();
    }

    protected boolean isHovered(double mouseX, double mouseY, float x, float y, float w, float h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    protected void setScrollImmediate(float value) {
        scroll = Mth.clamp(value, 0.0f, maxScroll);
        targetScroll = scroll;
    }

    private void updateScroll(float contentHeight, float visibleHeight, boolean animate) {
        maxScroll = Math.max(0.0f, contentHeight - visibleHeight);
        targetScroll = Mth.clamp(targetScroll, 0.0f, maxScroll);
        scroll = Mth.clamp(scroll, 0.0f, maxScroll);
        if (maxScroll <= 0.0f) {
            scrollBar.reset();
        }

        if (!animate) {
            return;
        }

        if (Math.abs(scroll - targetScroll) <= SCROLL_EPSILON) {
            scroll = targetScroll;
        } else {
            scroll = Mth.lerp(SCROLL_SMOOTHING, scroll, targetScroll);
        }
    }

    private UiRect getScrollbarViewport() {
        return new UiRect(x, y + DropdownTheme.PANEL_HEADER_HEIGHT, width, cachedVisibleContentHeight * cachedExpand);
    }

    protected String trimToWidth(String value, float scale, float maxWidth, UiTextMetrics textMetrics) {
        if (value == null || value.isEmpty()) return "";
        if (textMetrics.textWidth(value, scale) <= maxWidth) return value;
        String ellipsis = "...";
        float ellipsisWidth = textMetrics.textWidth(ellipsis, scale);
        if (ellipsisWidth >= maxWidth) return ellipsis;
        for (int len = value.length() - 1; len >= 0; len--) {
            String candidate = value.substring(0, len) + ellipsis;
            if (textMetrics.textWidth(candidate, scale) <= maxWidth) return candidate;
        }
        return ellipsis;
    }

}
