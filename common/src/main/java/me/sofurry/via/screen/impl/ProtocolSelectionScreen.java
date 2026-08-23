package me.sofurry.via.screen.impl;

import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.graphics.text.IconChars;
import com.github.epsilon.graphics.text.StaticFontLoader;
import com.github.epsilon.gui.lib.UiRect;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.gui.lib.control.UiScrollBar;
import com.github.epsilon.gui.lib.scene.UiLayer;
import com.github.epsilon.gui.lib.scene.UiScene;
import com.github.epsilon.gui.theme.EpsilonUiTheme;
import com.github.epsilon.gui.theme.MD3Theme;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.util.List;

public class ProtocolSelectionScreen extends Screen {

    public static final ProtocolSelectionScreen INSTANCE = new ProtocolSelectionScreen();

    private static final Color SCRIM = new Color(0, 0, 0, 150);
    private static final float MAX_PANEL_WIDTH = 430.0f;
    private static final float MAX_PANEL_HEIGHT = 520.0f;
    private static final float PANEL_MARGIN = 12.0f;
    private static final float PANEL_PADDING = 6.0f;
    private static final float SCROLLBAR_RESERVED_WIDTH = 6.0f;
    private static final float HEADER_HEIGHT = 50.0f;
    private static final float HEADER_BUTTON_SIZE = 30.0f;
    private static final float ROW_HEIGHT = 28.0f;
    private static final float ROW_GAP = 3.0f;
    private static final float ROW_RADIUS = 7.0f;

    private final UiScene scene = new UiScene(EpsilonUiTheme.INSTANCE);
    private final TextRenderer textRenderer = TextRenderer.create();
    private final UiScrollBar scrollBar = new UiScrollBar(EpsilonUiTheme.INSTANCE);

    private List<ProtocolVersion> protocols = List.of();
    private Screen parent;
    private LuminRenderSystem.LuminRenderTarget renderTarget;

    private UiRect panelBounds;
    private UiRect backBounds;
    private UiRect listViewport;
    private float rowWidth;
    private float contentHeight;
    private float scroll;
    private float maxScroll;
    private float scrollVelocity;
    private int focusedIndex = -1;
    private boolean revealFocusedVersion;

    private ProtocolSelectionScreen() {
        super(Component.literal("ViaVersion"));
    }

    public void open(final Screen parent) {
        this.parent = parent;
        this.minecraft.execute(() -> this.minecraft.gui.setScreen(this));
    }

    @Override
    protected void init() {
        super.init();
        this.protocols = List.copyOf(ProtocolVersion.getReversedProtocols());
        this.focusedIndex = this.protocols.indexOf(ProtocolTranslator.getTargetVersion());
        if (this.focusedIndex < 0 && !this.protocols.isEmpty()) {
            this.focusedIndex = 0;
        }
        this.scrollVelocity = 0.0f;
        this.revealFocusedVersion = true;
        this.scrollBar.reset();
    }

    @Override
    public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float partialTick) {
        final var window = this.minecraft.getWindow();
        if (this.renderTarget == null) {
            this.renderTarget = LuminRenderSystem.LuminRenderTarget.create("protocol-selection-screen", window.getWidth(), window.getHeight());
        }
        this.renderTarget.resize(window.getWidth(), window.getHeight());
        this.renderTarget.clear();

        final int epsilonMouseX = LuminRenderSystem.toEpsilonMouseX(mouseX);
        final int epsilonMouseY = LuminRenderSystem.toEpsilonMouseY(mouseY);
        final int scaledWidth = LuminRenderSystem.getScaledWidthInt();
        final int scaledHeight = LuminRenderSystem.getScaledHeightInt();

        this.layout(scaledWidth, scaledHeight);
        if (this.revealFocusedVersion) {
            this.revealFocusedVersion = false;
            this.scrollFocusedIntoView();
        }
        this.scroll = Mth.clamp(this.scroll + this.scrollVelocity * partialTick, 0.0f, this.maxScroll);
        this.scrollVelocity *= 0.84f;

        final LuminRenderSystem.LuminRenderTarget previousTarget = LuminRenderSystem.getActiveTarget();
        LuminRenderSystem.setActiveTarget(this.renderTarget);
        try {
            this.scene.beginFrame();
            final UiTree tree = UiTree.build(scope -> {
                scope.rect(0.0f, 0.0f, scaledWidth, scaledHeight, SCRIM);
                scope.layer(0, panel -> {
                    panel.shadow(this.panelBounds.x(), this.panelBounds.y(), this.panelBounds.width(), this.panelBounds.height(), MD3Theme.PANEL_RADIUS, MD3Theme.PANEL_SHADOW_BLUR, MD3Theme.SHADOW);
                    panel.roundRect(this.panelBounds.x(), this.panelBounds.y(), this.panelBounds.width(), this.panelBounds.height(), MD3Theme.PANEL_RADIUS, MD3Theme.SURFACE);
                });
                scope.layer(10, content -> {
                    this.drawHeader(content, epsilonMouseX, epsilonMouseY);
                    this.drawProtocolList(content, epsilonMouseX, epsilonMouseY);
                });
            });
            this.scene.submit(UiLayer.CONTENT, tree);
            this.scene.flush();
            this.scene.clear();
        } finally {
            LuminRenderSystem.setActiveTarget(previousTarget);
        }

        graphics.blit(this.renderTarget.getIdentifier(), 0, 0, window.getGuiScaledWidth(), window.getGuiScaledHeight(), 0, 1, 1, 0);
    }

    private void layout(final int width, final int height) {
        final float panelWidth = Mth.clamp(width - PANEL_MARGIN * 2.0f, 0.0f, MAX_PANEL_WIDTH);
        final float panelHeight = Mth.clamp(height - PANEL_MARGIN * 2.0f, 0.0f, MAX_PANEL_HEIGHT);
        final float panelX = (width - panelWidth) / 2.0f;
        final float panelY = (height - panelHeight) / 2.0f;
        this.panelBounds = new UiRect(panelX, panelY, panelWidth, panelHeight);

        this.backBounds = new UiRect(panelX + PANEL_PADDING, panelY + (HEADER_HEIGHT - HEADER_BUTTON_SIZE) / 2.0f, HEADER_BUTTON_SIZE, HEADER_BUTTON_SIZE);

        final float listTop = panelY + HEADER_HEIGHT + 5.0f;
        final float listBottom = panelY + panelHeight - PANEL_PADDING;
        this.listViewport = new UiRect(panelX + PANEL_PADDING, listTop, Math.max(0.0f, panelWidth - PANEL_PADDING * 2.0f), Math.max(0.0f, listBottom - listTop));

        this.contentHeight = this.protocols.size() * (ROW_HEIGHT + ROW_GAP);
        this.maxScroll = Math.max(0.0f, this.contentHeight - this.listViewport.height());
        this.rowWidth = this.maxScroll > 0.0f ? Math.max(0.0f, this.listViewport.width() - SCROLLBAR_RESERVED_WIDTH) : this.listViewport.width();
        this.scroll = Mth.clamp(this.scroll, 0.0f, this.maxScroll);
    }

    private void drawHeader(final UiTree.Scope scope, final int mouseX, final int mouseY) {
        this.drawIconButton(scope, this.backBounds, IconChars.ARROW_BACK, this.backBounds.contains(mouseX, mouseY), MD3Theme.TEXT_PRIMARY);

        final float titleX = this.backBounds.right() + 10.0f;
        final float titleScale = 1.0f;
        final float titleHeight = this.textRenderer.getHeight(titleScale, StaticFontLoader.JURA_LIGHT);
        scope.text("ViaVersion", titleX, this.panelBounds.y() + 13.0f, titleScale, MD3Theme.TEXT_PRIMARY, StaticFontLoader.JURA_LIGHT);

        final String targetVersion = EpsilonTranslations.component(EpsilonTranslations.Via.BASE_TARGET_VERSION,
                ProtocolTranslator.getTargetVersion().getName()).getString();
        final float subtitleScale = 0.58f;
        scope.text(targetVersion, titleX, this.panelBounds.y() + 17.0f + titleHeight, subtitleScale, MD3Theme.TEXT_MUTED);

        scope.rect(this.panelBounds.x() + PANEL_PADDING, this.panelBounds.y() + HEADER_HEIGHT, this.panelBounds.width() - PANEL_PADDING * 2.0f, 1.0f, MD3Theme.OUTLINE_SOFT);
    }

    private void drawProtocolList(final UiTree.Scope scope, final int mouseX, final int mouseY) {
        final boolean selectionEnabled = this.minecraft.getConnection() == null;
        scope.scissor(this.listViewport, clipped -> {
            float rowY = this.listViewport.y() - this.scroll;
            for (int index = 0; index < this.protocols.size(); index++) {
                final UiRect bounds = new UiRect(this.listViewport.x(), rowY, this.rowWidth, ROW_HEIGHT);
                if (bounds.bottom() >= this.listViewport.y() && bounds.y() <= this.listViewport.bottom()) {
                    this.drawProtocolRow(clipped, bounds, index, mouseX, mouseY, selectionEnabled);
                }
                rowY += ROW_HEIGHT + ROW_GAP;
            }
        });
        this.scrollBar.draw(scope, this.listViewport, this.scroll, this.maxScroll, this.contentHeight, mouseX, mouseY);
    }

    private void drawProtocolRow(final UiTree.Scope scope, final UiRect bounds, final int index, final int mouseX, final int mouseY, final boolean selectionEnabled) {
        final ProtocolVersion protocol = this.protocols.get(index);
        final boolean selected = ProtocolTranslator.getTargetVersion().equals(protocol);
        final boolean hovered = selectionEnabled && this.listViewport.contains(mouseX, mouseY) && bounds.contains(mouseX, mouseY);
        final boolean focused = index == this.focusedIndex;

        Color background = selected ? MD3Theme.PRIMARY_CONTAINER : MD3Theme.rowSurface(hovered ? 1.0f : 0.0f);
        Color foreground = selected ? MD3Theme.ON_PRIMARY_CONTAINER : MD3Theme.TEXT_PRIMARY;
        Color iconColor = selected ? MD3Theme.PRIMARY : MD3Theme.TEXT_MUTED;
        if (!selectionEnabled) {
            background = MD3Theme.withAlpha(background, 120);
            foreground = MD3Theme.withAlpha(foreground, 110);
            iconColor = MD3Theme.withAlpha(iconColor, 90);
        }

        scope.roundRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), ROW_RADIUS, background);
        if (focused) {
            final float outlineInset = 1.0f;
            scope.outline(
                    bounds.x() + outlineInset,
                    bounds.y() + outlineInset,
                    Math.max(0.0f, bounds.width() - outlineInset * 2.0f),
                    Math.max(0.0f, bounds.height() - outlineInset * 2.0f),
                    ROW_RADIUS,
                    1.0f,
                    selectionEnabled ? MD3Theme.PRIMARY : MD3Theme.OUTLINE_SOFT
            );
        }

        final float iconScale = 0.78f;
        final String icon = selected ? IconChars.RADIO_BUTTON_CHECKED : IconChars.RADIO_BUTTON_UNCHECKED;
        final float iconWidth = this.textRenderer.getWidth(icon, iconScale, StaticFontLoader.ICONS);
        final float iconHeight = this.textRenderer.getHeight(iconScale, StaticFontLoader.ICONS);
        scope.text(icon, bounds.right() - 10.0f - iconWidth, bounds.centerY() - iconHeight / 2.0f, iconScale, iconColor, StaticFontLoader.ICONS);

        final float textScale = 0.68f;
        final float textHeight = this.textRenderer.getHeight(textScale);
        scope.text(protocol.getName(), bounds.x() + 10.0f, bounds.centerY() - textHeight / 2.0f, textScale, foreground);
    }

    private void drawIconButton(final UiTree.Scope scope, final UiRect bounds, final String icon, final boolean hovered, final Color color) {
        if (hovered) {
            scope.roundRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), bounds.height() / 2.0f, MD3Theme.stateLayer(color, 1.0f, 34));
        }
        this.drawIcon(scope, icon, bounds.centerX(), bounds.centerY(), 0.88f, color);
    }

    private void drawIcon(final UiTree.Scope scope, final String icon, final float centerX, final float centerY, final float scale, final Color color) {
        final float iconWidth = this.textRenderer.getWidth(icon, scale, StaticFontLoader.ICONS);
        final float iconHeight = this.textRenderer.getHeight(scale, StaticFontLoader.ICONS);
        scope.text(icon, centerX - iconWidth / 2.0f, centerY - iconHeight / 2.0f, scale, color, StaticFontLoader.ICONS);
    }

    @Override
    public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
        if (event.button() != 0) {
            return super.mouseClicked(event, doubleClick);
        }

        final double mouseX = LuminRenderSystem.toEpsilonMouseX(event.x());
        final double mouseY = LuminRenderSystem.toEpsilonMouseY(event.y());
        if (this.backBounds != null && this.backBounds.contains(mouseX, mouseY)) {
            this.playClickSound();
            this.onClose();
            return true;
        }
        if (this.listViewport != null && this.scrollBar.mouseClicked(mouseX, mouseY, this.listViewport, this.scroll, this.maxScroll, this.contentHeight)) {
            return true;
        }
        if (this.minecraft.getConnection() == null && this.listViewport != null && this.listViewport.contains(mouseX, mouseY)) {
            final int index = this.protocolIndexAt(mouseY);
            if (index >= 0) {
                this.focusedIndex = index;
                ProtocolTranslator.setTargetVersion(this.protocols.get(index));
                this.playClickSound();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(final MouseButtonEvent event) {
        if (event.button() == 0 && this.scrollBar.mouseReleased()) {
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(final MouseButtonEvent event, final double deltaX, final double deltaY) {
        if (this.listViewport != null && this.scrollBar.isDragging()) {
            final float nextScroll = this.scrollBar.mouseDragged(LuminRenderSystem.toEpsilonMouseY(event.y()), this.listViewport, this.maxScroll, this.contentHeight);
            if (nextScroll >= 0.0f) {
                this.scroll = nextScroll;
                this.scrollVelocity = 0.0f;
            }
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY) {
        final double epsilonMouseX = LuminRenderSystem.toEpsilonMouseX(mouseX);
        final double epsilonMouseY = LuminRenderSystem.toEpsilonMouseY(mouseY);
        if (this.listViewport != null && this.listViewport.contains(epsilonMouseX, epsilonMouseY)) {
            this.scrollVelocity -= (float) scrollY * 24.0f;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(final KeyEvent event) {
        switch (event.key()) {
            case GLFW.GLFW_KEY_UP -> this.moveFocus(-1);
            case GLFW.GLFW_KEY_DOWN -> this.moveFocus(1);
            case GLFW.GLFW_KEY_PAGE_UP -> this.moveFocus(-this.visibleRowCount());
            case GLFW.GLFW_KEY_PAGE_DOWN -> this.moveFocus(this.visibleRowCount());
            case GLFW.GLFW_KEY_HOME -> this.focusVersion(0);
            case GLFW.GLFW_KEY_END -> this.focusVersion(this.protocols.size() - 1);
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (this.minecraft.getConnection() == null && this.focusedIndex >= 0 && this.focusedIndex < this.protocols.size()) {
                    ProtocolTranslator.setTargetVersion(this.protocols.get(this.focusedIndex));
                    this.playClickSound();
                }
            }
            default -> {
                return super.keyPressed(event);
            }
        }
        return true;
    }

    private int protocolIndexAt(final double mouseY) {
        final double relativeY = mouseY - this.listViewport.y() + this.scroll;
        if (relativeY < 0.0) {
            return -1;
        }
        final int index = (int) (relativeY / (ROW_HEIGHT + ROW_GAP));
        final double offsetInRow = relativeY - index * (ROW_HEIGHT + ROW_GAP);
        return index >= 0 && index < this.protocols.size() && offsetInRow <= ROW_HEIGHT ? index : -1;
    }

    private void moveFocus(final int delta) {
        if (this.protocols.isEmpty()) {
            this.focusedIndex = -1;
            return;
        }
        this.focusedIndex = this.focusedIndex < 0
                ? (delta >= 0 ? 0 : this.protocols.size() - 1)
                : Mth.clamp(this.focusedIndex + delta, 0, this.protocols.size() - 1);
        this.scrollFocusedIntoView();
    }

    private void focusVersion(final int index) {
        if (this.protocols.isEmpty()) {
            this.focusedIndex = -1;
            return;
        }
        this.focusedIndex = Mth.clamp(index, 0, this.protocols.size() - 1);
        this.scrollFocusedIntoView();
    }

    private int visibleRowCount() {
        return this.listViewport == null ? 1 : Math.max(1, (int) (this.listViewport.height() / (ROW_HEIGHT + ROW_GAP)));
    }

    private void scrollFocusedIntoView() {
        if (this.listViewport == null || this.focusedIndex < 0) {
            return;
        }
        final float rowTop = this.focusedIndex * (ROW_HEIGHT + ROW_GAP);
        final float rowBottom = rowTop + ROW_HEIGHT;
        if (rowTop < this.scroll) {
            this.scroll = rowTop;
        } else if (rowBottom > this.scroll + this.listViewport.height()) {
            this.scroll = rowBottom - this.listViewport.height();
        }
        this.scroll = Mth.clamp(this.scroll, 0.0f, this.maxScroll);
        this.scrollVelocity = 0.0f;
    }

    private void playClickSound() {
        AbstractWidget.playButtonClickSound(this.minecraft.getSoundManager());
    }

    @Override
    public Component getNarrationMessage() {
        return EpsilonTranslations.component(EpsilonTranslations.Via.BASE_TARGET_VERSION, ProtocolTranslator.getTargetVersion().getName());
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }

    @Override
    public void removed() {
        super.removed();
        this.scrollBar.reset();
        if (this.renderTarget != null) {
            this.renderTarget.close();
            this.renderTarget = null;
        }
    }

}
