package com.github.epsilon.gui.screen

import com.github.epsilon.assets.i18n.EpsilonLanguage
import com.github.epsilon.managers.ConfigManager
import com.github.epsilon.modules.impl.ClientSetting
import com.github.epsilon.settings.impl.BoolSetting
import com.mojang.blaze3d.Blaze3D
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.CycleButton
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth
import net.minecraft.util.Util
import java.awt.Color
import java.net.URI
import java.util.function.Function
import java.util.function.Supplier

private const val TITLE = "欢迎使用 Epsilon / Welcome to Epsilon"

private const val NOTICE = "本客户端为免费项目，别买！/ This client is FURRY, don't buy."

private const val WEBSITE_PREFIX_ZH = "官网: "

private const val WEBSITE_PREFIX_EN = "Official website: "

private const val WEBSITE_LABEL = "https://sofurry.me/"

private const val WEBSITE_URL = "https://sofurry.me/"

private const val CONTINUE = "继续 / Continue"

private const val DONT_SHOW_AGAIN = "下次不再显示 / Do not show again"

private const val OPEN_WEBSITE = "打开官网 / Open Website"

private const val LANGUAGE = "语言 / Language"

private val WEBSITE_LINK: Component = Component.literal(WEBSITE_LABEL).withStyle { style ->
    style.withColor(0x6FA8FF).withUnderlined(true)
}

private const val CARD_MARGIN = 16

private const val CARD_PADDING = 20

private const val TITLE_TOP = 18

private const val TITLE_TO_BODY_GAP = 26

private const val BODY_LINE_HEIGHT = 12

private const val BODY_BLOCK_GAP = 4

private const val BUTTON_HEIGHT = 20

private const val BUTTON_GAP = 6

private const val BUTTON_BLOCK_HEIGHT = BUTTON_HEIGHT * 4 + BUTTON_GAP * 3

private const val BUTTON_BOTTOM_PADDING = 12

private val BODY_LINES: List<Component> = listOf(
    Component.literal(NOTICE),
    websiteLine(WEBSITE_PREFIX_ZH),
    websiteLine(WEBSITE_PREFIX_EN)
)

private fun websiteLine(prefix: String): Component {
    return Component.literal(prefix).append(WEBSITE_LINK)
}

object WelcomeScreen : Screen(Component.literal(TITLE)) {

    private var openedAtMs: Long = 0L

    override fun init() {
        super.init()
        openedAtMs = Util.getMillis()

        val buttonWidth = Math.clamp((width - 40).toLong(), 160, 200)
        val buttonX = (width - buttonWidth) / 2
        val cardHeight = getCardHeight()
        val buttonY = getCardY(cardHeight) + cardHeight - BUTTON_BOTTOM_PADDING - BUTTON_BLOCK_HEIGHT

        addRenderableWidget(
            CycleButton.builder<EpsilonLanguage>(
                Function { language -> Component.literal(language.toString()) },
                Supplier { ClientSetting.language.value }
            )
                .withValues(EpsilonLanguage.values().toList())
                .create(buttonX, buttonY, buttonWidth, BUTTON_HEIGHT, Component.literal(LANGUAGE)) { _, language ->
                    ClientSetting.language.value = language
                    ConfigManager.saveNow()
                }
        )
        addRenderableWidget(
            Button.builder(Component.literal(DONT_SHOW_AGAIN), Button.OnPress { confirmDoNotShowAgain() })
                .bounds(buttonX, buttonY + BUTTON_HEIGHT + BUTTON_GAP, buttonWidth, BUTTON_HEIGHT)
                .build()
        )
        addRenderableWidget(
            Button.builder(Component.literal(CONTINUE), Button.OnPress { continueToNextScreen() })
                .bounds(buttonX, buttonY + (BUTTON_HEIGHT + BUTTON_GAP) * 2, buttonWidth, BUTTON_HEIGHT)
                .build()
        )
        addRenderableWidget(
            Button.builder(
                Component.literal(OPEN_WEBSITE),
                Button.OnPress { Blaze3D.openUri(URI.create(WEBSITE_URL)) }
            )
                .bounds(buttonX, buttonY + (BUTTON_HEIGHT + BUTTON_GAP) * 3, buttonWidth, BUTTON_HEIGHT)
                .build()
        )
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val font = this.font ?: return
        val fade = Mth.clamp((Util.getMillis() - openedAtMs) / 250.0f, 0.0f, 1.0f)
        val alpha = Math.round(255.0f * fade)

        val cardWidth = Math.min(560, width - CARD_MARGIN * 2)
        val bodyWidth = cardWidth - CARD_PADDING * 2
        val cardHeight = getCardHeight()
        val cardX = (width - cardWidth) / 2
        val cardY = getCardY(cardHeight)

        val cardColor = Color(20, 20, 24, Math.min(235, alpha)).getRGB()
        val outlineColor = Color(120, 124, 132, Math.min(120, alpha)).getRGB()
        val titleColor = Color(245, 245, 245, alpha).getRGB()
        val bodyColor = Color(220, 223, 230, alpha).getRGB()

        graphics.fill(cardX, cardY, cardX + cardWidth, cardY + cardHeight, cardColor)
        graphics.outline(cardX, cardY, cardWidth, cardHeight, outlineColor)

        val title = this.title.string
        val titleX = width / 2 - font.width(title) / 2
        val titleY = cardY + TITLE_TOP
        graphics.text(font, title, titleX, titleY, titleColor, false)

        val textX = cardX + CARD_PADDING
        var textY = titleY + TITLE_TO_BODY_GAP
        for (line in BODY_LINES) {
            val wrapped = font.split(line, bodyWidth)
            for (wrappedLine in wrapped) {
                graphics.text(font, wrappedLine, textX, textY, bodyColor, false)
                textY += BODY_LINE_HEIGHT
            }
            textY += BODY_BLOCK_GAP
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
    }

    override fun shouldCloseOnEsc(): Boolean {
        return true
    }

    override fun onClose() {
        continueToNextScreen()
    }

    override fun isPauseScreen(): Boolean {
        return false
    }

    private fun confirmDoNotShowAgain() {
        (ClientSetting.settings.firstOrNull { setting -> setting.name == "Show Welcome Screen" }
            as? BoolSetting)?.value = false
        ConfigManager.saveNow()
        continueToNextScreen()
    }

    private fun continueToNextScreen() {
        val minecraft = this.minecraft ?: return
        if (ClientSetting.useMainMenu) {
            minecraft.gui.setScreen(MainMenuScreen)
        } else {
            minecraft.gui.setScreen(TitleScreen())
        }
    }

    private fun getCardHeight(): Int {
        val font = this.font ?: return 0
        val cardWidth = Math.min(560, width - CARD_MARGIN * 2)
        val bodyWidth = cardWidth - CARD_PADDING * 2
        var bodyHeight = 0
        for (line in BODY_LINES) {
            bodyHeight += font.split(line, bodyWidth).size * BODY_LINE_HEIGHT
            bodyHeight += BODY_BLOCK_GAP
        }
        if (bodyHeight > 0) {
            bodyHeight -= BODY_BLOCK_GAP
        }
        return Math.min(
            height - CARD_MARGIN * 2,
            TITLE_TOP + TITLE_TO_BODY_GAP + bodyHeight + BUTTON_BOTTOM_PADDING + BUTTON_BLOCK_HEIGHT
        )
    }

    private fun getCardY(cardHeight: Int): Int {
        return (height - cardHeight) / 2
    }

}
