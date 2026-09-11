package com.github.epsilon.gui.screen;

import com.github.epsilon.assets.i18n.EpsilonLanguage;
import com.github.epsilon.managers.ConfigManager;
import com.github.epsilon.modules.impl.ClientSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

import java.awt.*;
import java.net.URI;
import java.util.List;

public class WelcomeScreen extends Screen {

    private static final String TITLE = "欢迎使用 Epsilon / Welcome to Epsilon";
    private static final String NOTICE = "本客户端为免费项目，别买！/ This client is FURRY, don't buy.";
    private static final String WEBSITE_PREFIX_ZH = "官网: ";
    private static final String WEBSITE_PREFIX_EN = "Official website: ";
    private static final String WEBSITE_LABEL = "https://sofurry.me/";
    private static final String WEBSITE_URL = "https://sofurry.me/";
    private static final String CONTINUE = "继续 / Continue";
    private static final String DONT_SHOW_AGAIN = "下次不再显示 / Do not show again";
    private static final String OPEN_WEBSITE = "打开官网 / Open Website";
    private static final String LANGUAGE = "语言 / Language";

    private static final Component WEBSITE_LINK = Component.literal(WEBSITE_LABEL).withStyle(style -> style
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
    private static final int BUTTON_BLOCK_HEIGHT = BUTTON_HEIGHT * 4 + BUTTON_GAP * 3;
    private static final int BUTTON_BOTTOM_PADDING = 12;

    private final List<Component> bodyLines = List.of(
            Component.literal(NOTICE),
            websiteLine(WEBSITE_PREFIX_ZH),
            websiteLine(WEBSITE_PREFIX_EN)
    );

    private WelcomeScreen() {
        super(Component.literal(TITLE));
    }

    private long openedAtMs;

    @Override
    protected void init() {
        super.init();
        openedAtMs = Util.getMillis();

        int buttonWidth = Math.clamp(this.width - 40, 160, 200);
        int buttonX = (this.width - buttonWidth) / 2;
        int cardHeight = getCardHeight();
        int buttonY = getCardY(cardHeight) + cardHeight - BUTTON_BOTTOM_PADDING - BUTTON_BLOCK_HEIGHT;

        this.addRenderableWidget(CycleButton.<EpsilonLanguage>builder(
                        language -> Component.literal(language.toString()),
                        ClientSetting.INSTANCE.language::getValue
                )
                .withValues(EpsilonLanguage.values())
                .create(buttonX, buttonY, buttonWidth, BUTTON_HEIGHT, Component.literal(LANGUAGE), (button, language) -> {
                    ClientSetting.INSTANCE.language.setValue(language);
                    ConfigManager.INSTANCE.saveNow();
                }));
        this.addRenderableWidget(Button.builder(Component.literal(DONT_SHOW_AGAIN), button -> confirmDoNotShowAgain())
                .bounds(buttonX, buttonY + BUTTON_HEIGHT + BUTTON_GAP, buttonWidth, BUTTON_HEIGHT)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal(CONTINUE), button -> continueToNextScreen())
                .bounds(buttonX, buttonY + (BUTTON_HEIGHT + BUTTON_GAP) * 2, buttonWidth, BUTTON_HEIGHT)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal(OPEN_WEBSITE), button -> Util.getPlatform().openUri(URI.create(WEBSITE_URL)))
                .bounds(buttonX, buttonY + (BUTTON_HEIGHT + BUTTON_GAP) * 3, buttonWidth, BUTTON_HEIGHT)
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
        ConfigManager.INSTANCE.saveNow();
        continueToNextScreen();
    }

    private void continueToNextScreen() {
        if (ClientSetting.INSTANCE.useMainMenu.getValue()) {
            minecraft.gui.setScreen(MainMenuScreen.INSTANCE);
        } else {
            minecraft.gui.setScreen(new TitleScreen());
        }
    }

    private static Component websiteLine(String prefix) {
        return Component.literal(prefix).append(WEBSITE_LINK);
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

}
