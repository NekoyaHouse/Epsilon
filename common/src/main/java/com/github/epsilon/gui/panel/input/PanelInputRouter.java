package com.github.epsilon.gui.panel.input;

import com.github.epsilon.gui.panel.popup.PanelPopupHost;
import com.github.epsilon.gui.panel.view.CategoryRailPanel;
import com.github.epsilon.gui.panel.view.ClientSettingPanel;
import com.github.epsilon.gui.panel.view.ModuleDetailPanel;
import com.github.epsilon.gui.panel.view.ModuleListPanel;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

public class PanelInputRouter {

    public boolean routeMouseClicked(MouseButtonEvent event, boolean isDoubleClick, PanelPopupHost popupHost, ModuleDetailPanel detailPanel, ModuleListPanel moduleListPanel, CategoryRailPanel categoryRailPanel, ClientSettingPanel clientSettingPanel, boolean clientSettingMode) {
        if (popupHost.mouseClicked(event, isDoubleClick)) {
            return true;
        }
        if (clientSettingMode) {
            if (clientSettingPanel.mouseClicked(event, isDoubleClick)) {
                return true;
            }
        } else {
            if (detailPanel.mouseClicked(event, isDoubleClick)) {
                return true;
            }
            if (moduleListPanel.mouseClicked(event, isDoubleClick)) {
                return true;
            }
        }
        return categoryRailPanel.mouseClicked(event, isDoubleClick);
    }

    public boolean routeKeyPressed(KeyEvent event, PanelPopupHost popupHost, ModuleDetailPanel detailPanel, ModuleListPanel moduleListPanel, ClientSettingPanel clientSettingPanel, boolean clientSettingMode) {
        if (popupHost.keyPressed(event)) {
            return true;
        }
        if (clientSettingMode) {
            return clientSettingPanel.keyPressed(event);
        }
        if (moduleListPanel.keyPressed(event)) {
            return true;
        }
        return detailPanel.keyPressed(event);
    }

    public boolean routeMouseReleased(MouseButtonEvent event, PanelPopupHost popupHost, ModuleDetailPanel detailPanel, ModuleListPanel moduleListPanel, ClientSettingPanel clientSettingPanel, boolean clientSettingMode) {
        if (popupHost.getActivePopup() != null) {
            return popupHost.mouseReleased(event);
        }
        if (clientSettingMode) {
            return clientSettingPanel.mouseReleased(event);
        }
        if (detailPanel.mouseReleased(event)) {
            return true;
        }
        return moduleListPanel.mouseReleased(event);
    }

    public boolean routeMouseDragged(MouseButtonEvent event, double mouseX, double mouseY, PanelPopupHost popupHost, ModuleDetailPanel detailPanel, ModuleListPanel moduleListPanel, ClientSettingPanel clientSettingPanel, boolean clientSettingMode) {
        if (popupHost.getActivePopup() != null) {
            return popupHost.mouseDragged(event, mouseX, mouseY);
        }
        if (clientSettingMode) {
            return clientSettingPanel.mouseDragged(event, mouseX, mouseY);
        }
        if (detailPanel.mouseDragged(event, mouseX, mouseY)) {
            return true;
        }
        return moduleListPanel.mouseDragged(event, mouseX, mouseY);
    }

    public boolean routeCharTyped(CharacterEvent event, PanelPopupHost popupHost, ModuleDetailPanel detailPanel, ModuleListPanel moduleListPanel, ClientSettingPanel clientSettingPanel, boolean clientSettingMode) {
        if (popupHost.getActivePopup() != null) {
            return popupHost.charTyped(event);
        }
        if (clientSettingMode) {
            return clientSettingPanel.charTyped(event);
        }
        if (moduleListPanel.charTyped(event)) {
            return true;
        }
        return detailPanel.charTyped(event);
    }

}
