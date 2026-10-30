package com.github.epsilon.managers

import com.github.epsilon.Constants
import com.github.epsilon.assets.i18n.EpsilonTranslateComponent
import com.github.epsilon.elements.HudModule
import com.github.epsilon.elements.impl.Hotkey
import com.github.epsilon.elements.impl.Inventory
import com.github.epsilon.elements.impl.MTF
import com.github.epsilon.elements.impl.ModuleList
import com.github.epsilon.elements.impl.PlayerInfo
import com.github.epsilon.elements.impl.ScaffoldBlock
import com.github.epsilon.elements.impl.TargetHUD
import com.github.epsilon.elements.impl.VictoryBanner
import com.github.epsilon.elements.impl.Watermark
import com.github.epsilon.elements.impl.island.Island
import com.github.epsilon.elements.impl.notification.Notifications
import com.github.epsilon.events.api.handler
import com.github.epsilon.events.bus.EventBus
import com.github.epsilon.events.impl.Render2DEvent
import com.github.epsilon.gui.hudeditor.HudEditorScreen
import com.github.epsilon.gui.lib.scene.UiLayer
import com.github.epsilon.gui.lib.scene.UiScene
import com.github.epsilon.gui.theme.EpsilonUiTheme
import com.github.epsilon.utils.client.ClientUtils
import java.util.ArrayList

object HudElementManager {

    private val elements = ArrayList<HudModule>()
    private val scene = UiScene(EpsilonUiTheme)

    init {
        EventBus.handler<Render2DEvent.HUD> { event -> onRender2D(event) }
    }

    fun initElements() {
        addElement(Hotkey)
        addElement(Inventory)
        addElement(Island)
        addElement(ModuleList)
        addElement(MTF)
        addElement(Notifications)
        addElement(PlayerInfo)
        addElement(ScaffoldBlock)
        addElement(TargetHUD)
        addElement(VictoryBanner)
        addElement(Watermark)
    }

    private fun addElement(module: HudModule) {
        elements.add(module)
        module.initI18n(EpsilonTranslateComponent.create("elements", module.name.lowercase()))
    }

    fun getElements(): List<HudModule> {
        return elements
    }

    private fun onRender2D(event: Render2DEvent.HUD) {
        if (ClientUtils.isLoading() || Constants.mc.level == null ||
            Constants.mc.gui.screen() is HudEditorScreen
        ) return

        scene.beginFrame()
        for (element in elements) {
            if (element.isEnabled) {
                element.updateLayout()
                // HUD chrome 统一提交到 scheduler；原版物品等 overlay 在 flush 后单独绘制。
                element.renderWithBatch(Constants.mc.deltaTracker, scene.batch(UiLayer.CONTENT))
            }
        }
        scene.endFrame()

        for (element in elements) {
            if (element.isEnabled) {
                element.renderOverlay(event.guiGraphics, Constants.mc.deltaTracker)
            }
        }
    }

}
