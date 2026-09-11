package com.github.epsilon.gui.screen.accounts;

import com.github.epsilon.accounts.Account;
import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.graphics.LuminTexture;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.graphics.text.IconChars;
import com.github.epsilon.graphics.text.StaticFontLoader;
import com.github.epsilon.gui.lib.UiRect;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.gui.theme.MD3Theme;
import net.minecraft.util.Mth;

import java.awt.*;

public class AccountRow {

    public static final float HEIGHT = 52.0f;
    public static final float GAP = 6.0f;

    private static final float AVATAR_SIZE = 32.0f;
    private static final float CONTENT_INSET = 12.0f;
    private static final float ACTION_SIZE = 28.0f;
    private static final float ACTION_GAP = 4.0f;
    private static final float TRAILING_INSET = 8.0f;
    private static final float OUTLINE_INSET = 0.5f;

    private final Account<?> account;

    private UiRect bounds;
    private UiRect loginBounds;
    private UiRect deleteBounds;
    private float hoverProgress;
    private float focusProgress;
    private boolean confirmingDelete;

    public AccountRow(Account<?> account) {
        this.account = account;
    }

    public Account<?> getAccount() {
        return account;
    }

    public UiRect getBounds() {
        return bounds;
    }

    public UiRect getLoginBounds() {
        return loginBounds;
    }

    public UiRect getDeleteBounds() {
        return deleteBounds;
    }

    public boolean isConfirmingDelete() {
        return confirmingDelete;
    }

    public void setConfirmingDelete(boolean confirmingDelete) {
        this.confirmingDelete = confirmingDelete;
    }

    public void draw(UiTree.Scope scope, UiRect rowBounds, double mouseX, double mouseY, TextRenderer textRenderer, State state) {
        this.bounds = rowBounds;

        boolean hovered = rowBounds.contains(mouseX, mouseY);
        hoverProgress = Mth.lerp(0.24f, hoverProgress, hovered ? 1.0f : 0.0f);
        focusProgress = Mth.lerp(0.24f, focusProgress, state.keyboardFocused() ? 1.0f : 0.0f);

        float deleteX = rowBounds.width() - TRAILING_INSET - ACTION_SIZE;
        float loginX = deleteX - ACTION_GAP - ACTION_SIZE;
        float actionY = (rowBounds.height() - ACTION_SIZE) / 2.0f;
        loginBounds = new UiRect(rowBounds.x() + loginX, rowBounds.y() + actionY, ACTION_SIZE, ACTION_SIZE);
        deleteBounds = new UiRect(rowBounds.x() + deleteX, rowBounds.y() + actionY, ACTION_SIZE, ACTION_SIZE);

        scope.pushAbsolute(rowBounds, s -> {
            float w = rowBounds.width();
            float h = rowBounds.height();

            s.roundRect(0, 0, w, h, MD3Theme.CARD_RADIUS, MD3Theme.rowSurface(hoverProgress));

            if (state.current()) {
                s.roundRect(0, 0, w, h, MD3Theme.CARD_RADIUS, MD3Theme.stateLayer(MD3Theme.PRIMARY, 0.18f, 40));
            }
            if (focusProgress > 0.01f) {
                s.outline(OUTLINE_INSET, OUTLINE_INSET, w - OUTLINE_INSET * 2.0f, h - OUTLINE_INSET * 2.0f, MD3Theme.CARD_RADIUS, 1.0f, MD3Theme.withAlpha(MD3Theme.PRIMARY, (int) (200 * focusProgress)));
            }

            LuminTexture head = account.getCache().getHeadTexture();
            float avatarY = (h - AVATAR_SIZE) / 2.0f;
            s.roundedTexture(head, CONTENT_INSET, avatarY, AVATAR_SIZE, AVATAR_SIZE, AVATAR_SIZE * 0.25f, 0.0f, 0.0f, 1.0f, 1.0f, Color.WHITE);

            float textX = CONTENT_INSET + AVATAR_SIZE + 10.0f;
            float available = loginX - textX - 8.0f;

            String username = account.getUsername();
            float nameScale = 1.0f;
            float nameHeight = textRenderer.getHeight(nameScale);
            String clippedName = clip(textRenderer, username, nameScale, available);

            String secondary = account.getType().name();
            float subScale = 0.76f;
            float subHeight = textRenderer.getHeight(subScale);

            float lineGap = 3.0f;
            float blockHeight = nameHeight + lineGap + subHeight;
            float nameY = (h - blockHeight) / 2.0f;
            float subY = nameY + nameHeight + lineGap;

            s.text(clippedName, textX, nameY, nameScale, state.current() ? MD3Theme.PRIMARY : MD3Theme.TEXT_PRIMARY);
            s.text(secondary, textX, subY, subScale, MD3Theme.TEXT_MUTED);

            if (state.current()) {
                float subWidth = textRenderer.getWidth(secondary, subScale);
                String badge = EpsilonTranslations.Gui.ACCOUNTS_CURRENT.getTranslatedName();
                float badgeScale = 0.46f;
                float badgeTextWidth = textRenderer.getWidth(badge, badgeScale);
                float badgeWidth = badgeTextWidth + 10.0f;
                float badgeHeight = subHeight + 4.0f;
                float badgeX = textX + subWidth + 6.0f;
                if (badgeX + badgeWidth <= loginX - 8.0f) {
                    s.roundRect(badgeX, subY - 2.0f, badgeWidth, badgeHeight, MD3Theme.CHIP_RADIUS, MD3Theme.PRIMARY_CONTAINER);
                    s.text(badge, badgeX + (badgeWidth - badgeTextWidth) / 2.0f, subY - 2.0f + (badgeHeight - textRenderer.getHeight(badgeScale)) / 2.0f, badgeScale, MD3Theme.ON_PRIMARY_CONTAINER);
                }
            }

            drawLoginAction(s, textRenderer, loginX, actionY, state);
            drawDeleteAction(s, textRenderer, deleteX, actionY, mouseX, mouseY, state);
        });
    }

    private void drawLoginAction(UiTree.Scope scope, TextRenderer textRenderer, float x, float y, State state) {
        boolean hovered = loginBounds.contains(state.mouseX(), state.mouseY());

        if (state.busy()) {
            scope.roundRect(x, y, ACTION_SIZE, ACTION_SIZE, ACTION_SIZE / 2.0f, MD3Theme.PRIMARY);
            drawGlyph(scope, textRenderer, IconChars.HOURGLASS_EMPTY, x, y, 1.0f, MD3Theme.ON_PRIMARY);
            return;
        }

        boolean dimmed = state.anyBusy();
        Color bg = dimmed
                ? MD3Theme.withAlpha(MD3Theme.PRIMARY, 60)
                : MD3Theme.withAlpha(MD3Theme.PRIMARY, hovered ? 255 : 210);
        Color fg = dimmed ? MD3Theme.withAlpha(MD3Theme.ON_PRIMARY, 110) : MD3Theme.ON_PRIMARY;

        scope.roundRect(x, y, ACTION_SIZE, ACTION_SIZE, ACTION_SIZE / 2.0f, bg);
        drawGlyph(scope, textRenderer, IconChars.LOGIN, x, y, 1.0f, fg);
    }

    private void drawDeleteAction(UiTree.Scope scope, TextRenderer textRenderer, float x, float y, double mouseX, double mouseY, State state) {
        boolean hovered = deleteBounds.contains(mouseX, mouseY);

        if (confirmingDelete) {
            scope.roundRect(x, y, ACTION_SIZE, ACTION_SIZE, ACTION_SIZE / 2.0f, MD3Theme.ERROR);
            drawGlyph(scope, textRenderer, IconChars.DELETE, x, y, 1.0f, new Color(40, 20, 20));
            return;
        }

        if (state.busy()) {
            drawGlyph(scope, textRenderer, IconChars.DELETE, x, y, 1.0f, MD3Theme.withAlpha(MD3Theme.ERROR, 70));
            return;
        }

        if (hovered) {
            scope.roundRect(x, y, ACTION_SIZE, ACTION_SIZE, ACTION_SIZE / 2.0f,
                    MD3Theme.stateLayer(MD3Theme.ERROR, 0.9f, 30));
        }
        drawGlyph(scope, textRenderer, IconChars.DELETE, x, y, 1.0f, MD3Theme.withAlpha(MD3Theme.ERROR, hovered ? 255 : 190));
    }

    private static void drawGlyph(UiTree.Scope scope, TextRenderer textRenderer, String glyph, float x, float y, float scale, Color color) {
        float glyphWidth = textRenderer.getWidth(glyph, scale, StaticFontLoader.ICONS);
        float glyphHeight = textRenderer.getHeight(scale, StaticFontLoader.ICONS);
        scope.text(glyph, x + (ACTION_SIZE - glyphWidth) / 2.0f, y + (ACTION_SIZE - glyphHeight) / 2.0f, scale, color, StaticFontLoader.ICONS);
    }

    private static String clip(TextRenderer textRenderer, String text, float scale, float available) {
        if (available <= 0.0f) return "";
        if (textRenderer.getWidth(text, scale) <= available) return text;

        String ellipsis = "...";
        float ellipsisWidth = textRenderer.getWidth(ellipsis, scale);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            builder.append(text.charAt(i));
            if (textRenderer.getWidth(builder.toString(), scale) + ellipsisWidth > available) {
                builder.deleteCharAt(builder.length() - 1);
                break;
            }
        }
        return builder + ellipsis;
    }

    public record State(
            boolean current, boolean busy, boolean anyBusy, boolean keyboardFocused, double mouseX, double mouseY
    ) {
    }

}
