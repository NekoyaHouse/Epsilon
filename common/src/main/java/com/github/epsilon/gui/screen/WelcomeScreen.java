package com.github.epsilon.gui.screen;

import com.github.epsilon.holders.ConfigHolder;
import com.github.epsilon.modules.impl.ClientSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

import java.awt.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

public class WelcomeScreen extends Screen {

    private static final String TITLE_B64 = "5qyi6L+O5L2/55SoIEVwc2lsb24gLyBXZWxjb21lIHRvIEVwc2lsb24=";
    private static final String NOTICE_B64 = "5pys5a6i5oi356uv5a6M5YWo5YWN6LS577yM6K+35Yu/5ZCR5Lu75L2V5Lq65LuY6LS56LSt5Lmw44CCLyBUaGlzIGNsaWVudCBpcyBjb21wbGV0ZWx5IGZyZWUuIFBsZWFzZSBkbyBub3QgcGF5IGFueW9uZSBmb3IgaXQu";
    private static final String GITHUB_PREFIX_ZH_B64 = "R2l0SHViIOS7k+W6kzog";
    private static final String GITHUB_PREFIX_EN_B64 = "R2l0SHViIHJlcG9zaXRvcnk6IA==";
    private static final String REPO_LABEL_B64 = "TmVrb3lhSG91c2UvRXBzaWxvbg==";
    private static final String REPOSITORY_URL_B64 = "aHR0cHM6Ly9naXRodWIuY29tL05la295YUhvdXNlL0Vwc2lsb24=";
    private static final String CONTINUE_B64 = "57un57utIC8gQ29udGludWU=";
    private static final String DONT_SHOW_AGAIN_B64 = "5LiL5qyh5LiN5YaN5pi+56S6IC8gRG8gbm90IHNob3cgYWdhaW4=";
    private static final String OPEN_GITHUB_B64 = "5omT5byAR2l0aHViIC8gT3BlbiBHaXRodWI=";
    private static final Component REPO_LINK = Component.literal(decode(REPO_LABEL_B64)).withStyle(style -> style
            .withColor(0x6FA8FF)
            .withUnderlined(true)
    );

    public static final WelcomeScreen INSTANCE = new WelcomeScreen();

    private static final int CARD_MARGIN = 16;
    private static final int CARD_PADDING = 20;
    private static final int TITLE_TOP = 18;
    private static final int TITLE_TO_BODY_GAP = 26;
    private static final int BODY_LINE_HEIGHT = 12;
    private static final int BODY_BLOCK_GAP = 4;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 6;
    private static final int BUTTON_BLOCK_HEIGHT = BUTTON_HEIGHT * 3 + BUTTON_GAP * 2;
    private static final int BUTTON_BOTTOM_PADDING = 12;

    private final List<Component> bodyLines = List.of(
            Component.literal(decode(NOTICE_B64)),
            githubLine(decode(GITHUB_PREFIX_ZH_B64)),
            githubLine(decode(GITHUB_PREFIX_EN_B64))
    );

    private WelcomeScreen() {
        super(Component.literal(decode(TITLE_B64)));
    }

    private long openedAtMs;

    @Override
    protected void init() {
        super.init();
        openedAtMs = Util.getMillis();

        int buttonWidth = Math.min(200, Math.max(160, this.width - 40));
        int buttonX = (this.width - buttonWidth) / 2;
        int cardHeight = getCardHeight();
        int buttonY = getCardY(cardHeight) + cardHeight - BUTTON_BOTTOM_PADDING - BUTTON_BLOCK_HEIGHT;

        this.addRenderableWidget(Button.builder(Component.literal(decode(DONT_SHOW_AGAIN_B64)), button -> confirmDoNotShowAgain())
                .bounds(buttonX, buttonY, buttonWidth, BUTTON_HEIGHT)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal(decode(CONTINUE_B64)), button -> continueToNextScreen())
                .bounds(buttonX, buttonY + BUTTON_HEIGHT + BUTTON_GAP, buttonWidth, BUTTON_HEIGHT)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal(decode(OPEN_GITHUB_B64)), button -> Util.getPlatform().openUri(URI.create(decode(REPOSITORY_URL_B64))))
                .bounds(buttonX, buttonY + (BUTTON_HEIGHT + BUTTON_GAP) * 2, buttonWidth, BUTTON_HEIGHT)
                .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        float fade = Mth.clamp((Util.getMillis() - openedAtMs) / 250.0f, 0.0f, 1.0f);
        int alpha = Math.round(255.0f * fade);

        int cardWidth = Math.min(560, this.width - CARD_MARGIN * 2);
        int bodyWidth = cardWidth - CARD_PADDING * 2;
        int cardHeight = getCardHeight();
        int cardX = (this.width - cardWidth) / 2;
        int cardY = getCardY(cardHeight);

        int cardColor = new Color(20, 20, 24, Math.min(235, alpha)).getRGB();
        int outlineColor = new Color(120, 124, 132, Math.min(120, alpha)).getRGB();
        int titleColor = new Color(245, 245, 245, alpha).getRGB();
        int bodyColor = new Color(220, 223, 230, alpha).getRGB();

        graphics.fill(cardX, cardY, cardX + cardWidth, cardY + cardHeight, cardColor);
        graphics.outline(cardX, cardY, cardWidth, cardHeight, outlineColor);

        String title = getTitle().getString();
        int titleX = this.width / 2 - font.width(title) / 2;
        int titleY = cardY + TITLE_TOP;
        graphics.text(font, title, titleX, titleY, titleColor, false);

        int textX = cardX + CARD_PADDING;
        int textY = titleY + TITLE_TO_BODY_GAP;
        for (Component line : bodyLines) {
            List<net.minecraft.util.FormattedCharSequence> wrapped = font.split(line, bodyWidth);
            for (net.minecraft.util.FormattedCharSequence wrappedLine : wrapped) {
                graphics.text(font, wrappedLine, textX, textY, bodyColor, false);
                textY += BODY_LINE_HEIGHT;
            }
            textY += BODY_BLOCK_GAP;
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void onClose() {
        continueToNextScreen();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void confirmDoNotShowAgain() {
        ClientSetting.INSTANCE.showWelcomeScreen.setValue(false);
        ConfigHolder.INSTANCE.saveNow();
        continueToNextScreen();
    }

    private void continueToNextScreen() {
        if (ClientSetting.INSTANCE.useMainMenu.getValue()) {
            minecraft.setScreen(MainMenuScreen.INSTANCE);
        } else {
            minecraft.setScreen(new TitleScreen());
        }
    }

    private static Component githubLine(String prefix) {
        return Component.literal(prefix).append(REPO_LINK);
    }

    private int getCardHeight() {
        int cardWidth = Math.min(560, this.width - CARD_MARGIN * 2);
        int bodyWidth = cardWidth - CARD_PADDING * 2;
        int bodyHeight = 0;
        for (Component line : bodyLines) {
            bodyHeight += font.split(line, bodyWidth).size() * BODY_LINE_HEIGHT;
            bodyHeight += BODY_BLOCK_GAP;
        }
        if (bodyHeight > 0) {
            bodyHeight -= BODY_BLOCK_GAP;
        }
        return Math.min(this.height - CARD_MARGIN * 2, TITLE_TOP + TITLE_TO_BODY_GAP + bodyHeight + BUTTON_BOTTOM_PADDING + BUTTON_BLOCK_HEIGHT);
    }

    private int getCardY(int cardHeight) {
        return (this.height - cardHeight) / 2;
    }

    private static String decode(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }

}
