package com.github.epsilon.gui.panel

class PanelDirtyState {

    private var chromeDirty = true
    private var railDirty = true
    private var moduleListDirty = true
    private var detailDirty = true
    private var clientSettingDirty = true

    fun markAllDirty() {
        chromeDirty = true
        railDirty = true
        moduleListDirty = true
        detailDirty = true
        clientSettingDirty = true
    }

    fun markLayoutDirty() {
        chromeDirty = true
        railDirty = true
        moduleListDirty = true
        detailDirty = true
        clientSettingDirty = true
    }

    fun markRailDirty() {
        railDirty = true
    }

    fun markModuleListDirty() {
        moduleListDirty = true
    }

    fun markDetailDirty() {
        detailDirty = true
    }

    fun markClientSettingDirty() {
        clientSettingDirty = true
    }

    fun consumeChromeDirty(): Boolean {
        return consume(chromeDirty) { chromeDirty = false }
    }

    fun consumeRailDirty(): Boolean {
        return consume(railDirty) { railDirty = false }
    }

    fun consumeModuleListDirty(): Boolean {
        return consume(moduleListDirty) { moduleListDirty = false }
    }

    fun consumeDetailDirty(): Boolean {
        return consume(detailDirty) { detailDirty = false }
    }

    fun consumeClientSettingDirty(): Boolean {
        return consume(clientSettingDirty) { clientSettingDirty = false }
    }

    private fun consume(dirty: Boolean, clearAction: () -> Unit): Boolean {
        if (!dirty) {
            return false
        }
        clearAction()
        return true
    }

}
