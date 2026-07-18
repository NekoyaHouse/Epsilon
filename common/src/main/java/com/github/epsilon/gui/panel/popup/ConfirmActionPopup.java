package com.github.epsilon.gui.panel.popup;

import com.github.epsilon.gui.lib.UiRect;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.gui.lib.render.UiRenderBatch;
import com.github.epsilon.gui.theme.MD3Theme;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

import java.awt.*;
import java.util.function.Supplier;

public class ConfirmActionPopup implements PanelPopupHost.Popup {

    private final UiRect bounds;
    private final Supplier<String> titleSupplier;
    private final Supplier<String> messageSupplier;
    private final String detail;
    private final Supplier<String> confirmLabelSupplier;
    private final Supplier<String> cancelLabelSupplier;
    private final Runnable onConfirm;

    private final Animation openAnimation = new Animation(Easing.EASE_OUT_CUBIC, 160L);
    private final Animation confirmHoverAnimation = new Animation(Easing.EASE_OUT_CUBIC, 120L);
    private final Animation cancelHoverAnimation = new Animation(Easing.EASE_OUT_CUBIC, 120L);

    private boolean closeAfterClick;
    private float animatedY;
    private UiRect confirmButtonBounds;
    private UiRect cancelButtonBounds;

    public ConfirmActionPopup(UiRect bounds, String title, String message, String detail,
                              String confirmLabel, String cancelLabel, Runnable onConfirm) {
        this(bounds, () -> title, () -> message, detail, () -> confirmLabel, () -> cancelLabel, onConfirm);
    }

    public ConfirmActionPopup(UiRect bounds, Supplier<String> titleSupplier, Supplier<String> messageSupplier, String detail,
                              Supplier<String> confirmLabelSupplier, Supplier<String> cancelLabelSupplier, Runnable onConfirm) {
        this.bounds = bounds;
        this.titleSupplier = titleSupplier;
        this.messageSupplier = messageSupplier;
        this.detail = detail;
        this.confirmLabelSupplier = confirmLabelSupplier;
        this.cancelLabelSupplier = cancelLabelSupplier;
        this.onConfirm = onConfirm;
        this.openAnimation.setStartValue(0.0f);
        this.confirmHoverAnimation.setStartValue(0.0f);
        this.cancelHoverAnimation.setStartValue(0.0f);
        updateLayout(bounds.y());
    }

    @Override
    public UiRect getBounds() {
        return bounds;
    }

    @Override
    public void extractGui(GuiGraphicsExtractor guiGraphics, UiRenderBatch renderBatch, int mouseX, int mouseY, float partialTick) {
        openAnimation.run(1.0f);
        float progress = openAnimation.getValue();
        animatedY = bounds.y() - (1.0f - progress) * 6.0f;
        updateLayout(animatedY);

        confirmHoverAnimation.run(confirmButtonBounds.contains(mouseX, mouseY) ? 1.0f : 0.0f);
        cancelHoverAnimation.run(cancelButtonBounds.contains(mouseX, mouseY) ? 1.0f : 0.0f);
        UiTree tree = UiTree.build(scope -> {
            UiRect popupBounds = new UiRect(bounds.x(), animatedY, bounds.width(), bounds.height());
            scope.pushAbsolute(popupBounds, popup -> {
                popup.popupCard(popupBounds.atOrigin(),
                        MD3Theme.CARD_RADIUS,
                        MD3Theme.POPUP_SHADOW_BLUR,
                        MD3Theme.withAlpha(MD3Theme.SHADOW, (int) (MD3Theme.POPUP_SHADOW_ALPHA * progress)),
                        MD3Theme.withAlpha(MD3Theme.SURFACE_CONTAINER_LOW, 255));

                float titleScale = 0.66f;
                float messageScale = 0.56f;
                float detailScale = 0.60f;
                String title = titleSupplier.get();
                String message = messageSupplier.get();
                String cancelLabel = cancelLabelSupplier.get();
                String confirmLabel = confirmLabelSupplier.get();
                popup.text(title, 12.0f, 10.0f, titleScale, MD3Theme.TEXT_PRIMARY);
                popup.text(message, 12.0f, 24.0f, messageScale, MD3Theme.TEXT_SECONDARY);
                if (detail != null && !detail.isBlank()) {
                    popup.text(detail, 12.0f, 37.0f, detailScale, MD3Theme.PRIMARY);
                }

                buildButton(popup, cancelButtonBounds.relativeTo(popupBounds), cancelLabel, false, cancelHoverAnimation.getValue());
                buildButton(popup, confirmButtonBounds.relativeTo(popupBounds), confirmLabel, true, confirmHoverAnimation.getValue());
            });
        });
        renderBatch.render(tree);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        if (event.button() != 0 || !bounds.contains(event.x(), event.y())) {
            return false;
        }
        closeAfterClick = false;
        if (confirmButtonBounds.contains(event.x(), event.y())) {
            onConfirm.run();
            closeAfterClick = true;
            return true;
        }
        if (cancelButtonBounds.contains(event.x(), event.y())) {
            closeAfterClick = true;
            return true;
        }
        return true;
    }

    @Override
    public boolean shouldCloseAfterClick() {
        return closeAfterClick;
    }

    private void buildButton(UiTree.Scope scope, UiRect buttonBounds, String label, boolean destructive, float hover) {
        Color baseColor = destructive ? MD3Theme.ERROR : MD3Theme.SURFACE_CONTAINER_HIGH;
        Color hoverColor = destructive ? MD3Theme.withAlpha(MD3Theme.ERROR, 220) : MD3Theme.SURFACE_CONTAINER_HIGHEST;
        Color textColor = destructive ? MD3Theme.ON_PRIMARY : MD3Theme.TEXT_PRIMARY;
        scope.button(buttonBounds, buttonBounds.height() / 2.0f,
                MD3Theme.withAlpha(MD3Theme.lerp(baseColor, hoverColor, hover * 0.35f), 255),
                label, 0.56f, textColor);
    }

    private void updateLayout(float popupY) {
        float buttonWidth = 68.0f;
        float buttonHeight = 24.0f;
        float gap = 6.0f;
        float cancelX = bounds.right() - buttonWidth * 2.0f - gap - 12.0f;
        float confirmX = bounds.right() - buttonWidth - 12.0f;
        float buttonY = popupY + bounds.height() - buttonHeight - 10.0f;
        cancelButtonBounds = new UiRect(cancelX, buttonY, buttonWidth, buttonHeight);
        confirmButtonBounds = new UiRect(confirmX, buttonY, buttonWidth, buttonHeight);
    }
}

