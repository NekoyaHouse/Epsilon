package com.github.epsilon.gui.screen;

import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.gui.theme.MD3Theme;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 通用二次确认对话框。
 */
public class ConfirmDialogScreen extends EpsilonDialogScreen {

    private static final float ROW_HEIGHT = 16.0f;
    private static final float MAX_WIDTH = 360.0f;

    private final String title;
    private final List<String> lines;
    private final String confirmLabel;
    private final String cancelLabel;
    private final Runnable onConfirm;

    public ConfirmDialogScreen(Screen parent, String title, List<String> lines,
                               String confirmLabel, String cancelLabel, Runnable onConfirm) {
        super(parent, Component.literal(title));
        this.title = title;
        this.lines = List.copyOf(lines);
        this.confirmLabel = confirmLabel;
        this.cancelLabel = cancelLabel;
        this.onConfirm = onConfirm;
    }

    @Override
    protected String titleText() {
        return title;
    }

    @Override
    protected float cardWidth() {
        return MAX_WIDTH;
    }

    @Override
    protected float cardHeight() {
        return CARD_PADDING * 2.0f + 36.0f + ROW_HEIGHT * Math.max(1, lines.size()) + 24.0f;
    }

    @Override
    protected void buildBody(UiTree.Scope scope, float contentTop, int mouseX, int mouseY) {
        float y = contentTop;
        for (String line : lines) {
            scope.text(line, CARD_PADDING, y, BODY_SCALE, MD3Theme.TEXT_SECONDARY);
            y += ROW_HEIGHT;
        }
    }

    @Override
    protected List<DialogButton> buttons() {
        return List.of(
                new DialogButton(cancelLabel, false, this::onClose),
                new DialogButton(confirmLabel, true, () -> {
                    onConfirm.run();
                    onClose();
                })
        );
    }

}
