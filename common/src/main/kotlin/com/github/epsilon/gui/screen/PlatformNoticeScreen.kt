package com.github.epsilon.gui.screen

import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.gui.screen.DialogCard.BODY_SCALE
import com.github.epsilon.gui.screen.DialogCard.CARD_PADDING
import com.github.epsilon.gui.screen.DialogCard.DialogButton
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.kernel.MinecraftWrapper.mc
import com.github.epsilon.kernel.client.platform.ClientPlatform
import com.github.epsilon.kernel.client.platform.PlatformRequirement
import com.github.epsilon.kernel.jvm.hooks.ViaFabricPlusHooks
import com.github.epsilon.settings.Setting
import com.github.epsilon.settings.ViaVersionRequirement
import com.github.epsilon.settings.impl.EnumSetting
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

/**
 * 平台限定功能的说明界面。
 *
 * 当前平台或协议版本不满足要求时，点击置灰的设置项可查看说明。
 */
object PlatformNoticeScreen : Screen(Component.empty()) {

    private const val ROW_HEIGHT = 17.0f

    private fun show(parent: Screen?, featureName: String, requirement: PlatformRequirement?) {
        if (requirement == null || requirement == PlatformRequirement.ANY || requirement.isSatisfied()) return
        DialogCard.show(this, parent, Notice(featureName, requirement))
    }

    /** 设置行在当前平台或协议版本上不可用时显示说明。 */
    fun show(setting: Setting<*>?) {
        if (setting == null) return
        val parent = mc.gui.screen()
        val via = setting.viaRequirement
        if (setting.isPlatformSupported() && via != null && !via.isSatisfied()) {
            DialogCard.show(this, parent, Notice(setting.displayName, PlatformRequirement.ANY, via))
        } else {
            show(parent, setting.displayName, setting.platformRequirement)
        }
    }

    /** 枚举选项在当前平台不可用时显示说明。 */
    fun showOption(setting: EnumSetting<*>?, mode: Enum<*>?) {
        if (setting == null || mode == null) return
        val requirement = setting.getModeRequirementUnchecked(mode)
        val feature = setting.displayName + " · " + setting.getTranslatedValueUnchecked(mode)
        show(mc.gui.screen(), feature, requirement)
    }

    override fun added() = DialogCard.added(this)

    override fun removed() = DialogCard.removed()

    override fun getTitle(): Component = DialogCard.title(this) ?: super.getTitle()

    override fun isPauseScreen(): Boolean = false

    override fun onClose() = DialogCard.close(this)

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) =
        DialogCard.extractRenderState(this, graphics, mouseX, mouseY)

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean =
        DialogCard.mouseClicked(event, doubleClick)

    override fun mouseReleased(event: MouseButtonEvent): Boolean = true

    private fun noticeTitle(viaRequirement: ViaVersionRequirement?): String {
        val title =
            if (viaRequirement == null) EpsilonTranslations.PlatformOnly.TITLE else EpsilonTranslations.ViaOnly.TITLE
        return title.getTranslatedName()
    }

    private class Notice(
        private val featureName: String,
        private val requirement: PlatformRequirement,
        private val viaRequirement: ViaVersionRequirement? = null,
    ) : DialogCard.Content(Component.literal(noticeTitle(viaRequirement))) {

        override fun titleText(): String {
            return noticeTitle(viaRequirement)
        }

        override fun cardWidth(): Float {
            return 320.0f
        }

        override fun cardHeight(): Float {
            return CARD_PADDING * 2.0f + 36.0f + ROW_HEIGHT * 4.0f + 24.0f
        }

        override fun buildBody(scope: UiTree.Scope, contentTop: Float) {
            var y = contentTop
            val labelScale = BODY_SCALE

            scope.text(
                String.format(EpsilonTranslations.PlatformOnly.FEATURE.getTranslatedName(), featureName),
                CARD_PADDING, y, labelScale, MD3Theme.TEXT_PRIMARY
            )
            y += ROW_HEIGHT
            scope.text(
                String.format(
                    (if (viaRequirement == null) EpsilonTranslations.PlatformOnly.REQUIREMENT else EpsilonTranslations.ViaOnly.REQUIREMENT).getTranslatedName(),
                    viaRequirement?.displayName() ?: requirement.displayName()
                ),
                CARD_PADDING, y, labelScale, MD3Theme.TEXT_SECONDARY
            )
            y += ROW_HEIGHT
            scope.text(
                String.format(
                    (if (viaRequirement == null) EpsilonTranslations.PlatformOnly.CURRENT else EpsilonTranslations.ViaOnly.CURRENT).getTranslatedName(),
                    if (viaRequirement == null) ClientPlatform.displayName() else
                        ViaFabricPlusHooks.viaVersion?.toString()
                            ?: EpsilonTranslations.ViaOnly.UNAVAILABLE.getTranslatedName()
                ),
                CARD_PADDING, y, labelScale, MD3Theme.TEXT_SECONDARY
            )
            y += ROW_HEIGHT + 6.0f
            scope.text(
                (if (viaRequirement == null) EpsilonTranslations.PlatformOnly.HINT else EpsilonTranslations.ViaOnly.HINT).getTranslatedName(),
                CARD_PADDING, y, labelScale, MD3Theme.TEXT_MUTED
            )
        }

        override fun buttons(): List<DialogButton> {
            return listOf(
                DialogButton(
                    EpsilonTranslations.PlatformOnly.CONFIRM.getTranslatedName(), true
                ) { PlatformNoticeScreen.onClose() }
            )
        }
    }

}
