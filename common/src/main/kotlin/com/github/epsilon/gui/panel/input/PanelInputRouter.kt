package com.github.epsilon.gui.panel.input

import com.github.epsilon.gui.panel.popup.PanelPopupHost
import com.github.epsilon.gui.panel.view.CategoryRailPanel
import com.github.epsilon.gui.panel.view.ClientSettingPanel
import com.github.epsilon.gui.panel.view.ModuleDetailPanel
import com.github.epsilon.gui.panel.view.ModuleListPanel
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent

class PanelInputRouter {

    fun routeMouseClicked(
        event: MouseButtonEvent, isDoubleClick: Boolean, popupHost: PanelPopupHost,
        detailPanel: ModuleDetailPanel, moduleListPanel: ModuleListPanel,
        categoryRailPanel: CategoryRailPanel, clientSettingPanel: ClientSettingPanel, clientSettingMode: Boolean,
    ): Boolean {
        if (popupHost.mouseClicked(event, isDoubleClick)) {
            return true
        }
        if (clientSettingMode) {
            if (clientSettingPanel.mouseClicked(event, isDoubleClick)) {
                return true
            }
        } else {
            if (detailPanel.mouseClicked(event, isDoubleClick)) {
                return true
            }
            if (moduleListPanel.mouseClicked(event, isDoubleClick)) {
                return true
            }
        }
        return categoryRailPanel.mouseClicked(event, isDoubleClick)
    }

    fun routeKeyPressed(
        event: KeyEvent, popupHost: PanelPopupHost, detailPanel: ModuleDetailPanel,
        moduleListPanel: ModuleListPanel, clientSettingPanel: ClientSettingPanel, clientSettingMode: Boolean,
    ): Boolean {
        if (popupHost.keyPressed(event)) {
            return true
        }
        if (clientSettingMode) {
            return clientSettingPanel.keyPressed(event)
        }
        if (moduleListPanel.keyPressed(event)) {
            return true
        }
        return detailPanel.keyPressed(event)
    }

    fun routeMouseReleased(
        event: MouseButtonEvent, popupHost: PanelPopupHost, detailPanel: ModuleDetailPanel,
        moduleListPanel: ModuleListPanel, clientSettingPanel: ClientSettingPanel, clientSettingMode: Boolean,
    ): Boolean {
        if (popupHost.activePopup != null) {
            return popupHost.mouseReleased(event)
        }
        if (clientSettingMode) {
            return clientSettingPanel.mouseReleased(event)
        }
        if (detailPanel.mouseReleased(event)) {
            return true
        }
        return moduleListPanel.mouseReleased(event)
    }

    fun routeMouseDragged(
        event: MouseButtonEvent, mouseX: Double, mouseY: Double, popupHost: PanelPopupHost,
        detailPanel: ModuleDetailPanel, moduleListPanel: ModuleListPanel,
        clientSettingPanel: ClientSettingPanel, clientSettingMode: Boolean,
    ): Boolean {
        if (popupHost.activePopup != null) {
            return popupHost.mouseDragged(event, mouseX, mouseY)
        }
        if (clientSettingMode) {
            return clientSettingPanel.mouseDragged(event, mouseX, mouseY)
        }
        if (detailPanel.mouseDragged(event, mouseX, mouseY)) {
            return true
        }
        return moduleListPanel.mouseDragged(event, mouseX, mouseY)
    }

    fun routeCharTyped(
        event: CharacterEvent, popupHost: PanelPopupHost, detailPanel: ModuleDetailPanel,
        moduleListPanel: ModuleListPanel, clientSettingPanel: ClientSettingPanel, clientSettingMode: Boolean,
    ): Boolean {
        if (popupHost.activePopup != null) {
            return popupHost.charTyped(event)
        }
        if (clientSettingMode) {
            return clientSettingPanel.charTyped(event)
        }
        if (moduleListPanel.charTyped(event)) {
            return true
        }
        return detailPanel.charTyped(event)
    }

}
