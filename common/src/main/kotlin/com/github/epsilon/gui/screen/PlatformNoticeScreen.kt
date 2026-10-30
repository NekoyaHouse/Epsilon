package com.github.epsilon.gui.screen

import com.github.epsilon.Constants
import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.settings.Setting
import com.github.epsilon.settings.impl.EnumSetting
import com.github.epsilon.utils.client.ClientPlatform
import com.github.epsilon.utils.client.PlatformRequirement
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * 平台限定功能的说明界面。
 *
 * 非支持平台上功能开关会被置灰，用户每次点击都会弹出该说明界面（同一功能不会重复堆叠），
 * 且不会修改用户已保存的配置值。
 */
class PlatformNoticeScreen private constructor(
    parent: Screen?,
    private val noticeKey: String,
    private val featureName: String,
    private val requirement: PlatformRequirement,
) : EpsilonDialogScreen(parent, Component.literal(EpsilonTranslations.PlatformOnly.TITLE.getTranslatedName())) {

    override fun removed() {
        super.removed()
        OPEN_KEYS.remove(noticeKey)
    }

    override fun titleText(): String {
        return EpsilonTranslations.PlatformOnly.TITLE.getTranslatedName()
    }

    override fun cardWidth(): Float {
        return 320.0f
    }

    override fun cardHeight(): Float {
        return EpsilonDialogScreen.CARD_PADDING * 2.0f + 36.0f + ROW_HEIGHT * 4.0f + 24.0f
    }

    override fun buildBody(scope: UiTree.Scope, contentTop: Float, mouseX: Int, mouseY: Int) {
        var y = contentTop
        val labelScale = EpsilonDialogScreen.BODY_SCALE

        scope.text(
            String.format(EpsilonTranslations.PlatformOnly.FEATURE.getTranslatedName(), featureName),
            EpsilonDialogScreen.CARD_PADDING, y, labelScale, MD3Theme.TEXT_PRIMARY
        )
        y += ROW_HEIGHT
        scope.text(
            String.format(EpsilonTranslations.PlatformOnly.REQUIREMENT.getTranslatedName(), requirement.displayName()),
            EpsilonDialogScreen.CARD_PADDING, y, labelScale, MD3Theme.TEXT_SECONDARY
        )
        y += ROW_HEIGHT
        scope.text(
            String.format(EpsilonTranslations.PlatformOnly.CURRENT.getTranslatedName(), ClientPlatform.displayName()),
            EpsilonDialogScreen.CARD_PADDING, y, labelScale, MD3Theme.TEXT_SECONDARY
        )
        y += ROW_HEIGHT + 6.0f
        scope.text(
            EpsilonTranslations.PlatformOnly.HINT.getTranslatedName(),
            EpsilonDialogScreen.CARD_PADDING, y, labelScale, MD3Theme.TEXT_MUTED
        )
    }

    override fun buttons(): List<DialogButton> {
        return listOf(
            DialogButton(
                EpsilonTranslations.PlatformOnly.CONFIRM.getTranslatedName(), true,
                Runnable { onClose() }
            )
        )
    }

    companion object {

        /**
         * 正在展示中的提示 key，用于避免同一功能重复堆叠窗口。
         */
        private val OPEN_KEYS: MutableSet<String> = ConcurrentHashMap.newKeySet()

        private const val ROW_HEIGHT = 17.0f

        /**
         * 在功能不可用的平台上弹出说明界面；同一 key 的窗口未关闭时不会重复打开。
         */
        fun show(parent: Screen?, key: String, featureName: String, requirement: PlatformRequirement?) {
            if (requirement == null || requirement == PlatformRequirement.ANY || requirement.isSatisfied()) {
                return
            }
            val minecraft = Constants.mc
            if (minecraft == null || !OPEN_KEYS.add(key)) {
                return
            }
            minecraft.gui.setScreen(PlatformNoticeScreen(parent, key, featureName, requirement))
        }

        /**
         * 设置行/枚举选项在非支持平台上被点击时调用。
         */
        fun show(setting: Setting<*>?) {
            if (setting == null) {
                return
            }
            val minecraft = Constants.mc
            val parent = if (minecraft != null) minecraft.gui.screen() else null
            show(parent, setting.noticeKey, setting.displayName, setting.platformRequirement)
        }

        /**
         * 枚举选项在非支持平台上被点击时调用。
         */
        fun showOption(setting: EnumSetting<*>?, mode: Enum<*>?) {
            if (setting == null || mode == null) {
                return
            }
            val requirement = setting.getModeRequirementUnchecked(mode)
            val feature = setting.displayName + " · " + setting.getTranslatedValueUnchecked(mode)
            val minecraft = Constants.mc
            val parent = if (minecraft != null) minecraft.gui.screen() else null
            show(parent, setting.noticeKey + "#" + mode.name, feature, requirement)
        }

    }

}
