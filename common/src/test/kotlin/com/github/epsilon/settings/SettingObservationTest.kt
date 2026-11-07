package com.github.epsilon.settings

import com.github.epsilon.gui.lib.state.asState
import com.github.epsilon.kernel.state.*
import com.github.epsilon.managers.TranslationManager
import com.github.epsilon.settings.impl.BoolSetting
import com.github.epsilon.settings.impl.StringListSetting
import kotlin.test.*

class SettingObservationTest {
    @AfterTest fun drain() = StateNotifications.drain()

    @Test fun silentWriteResetAndBooleanSetterUseTheSameObservableStorage() {
        var callbacks = 0
        val setting = BoolSetting("observable", false, null) { callbacks++ }
        val adapter = setting.asState()
        var changes = 0
        val observer = StateObservation { changes++ }
        observer.observeReads { assertFalse(adapter.value) }
        setting.setValueSilently(true)
        StateNotifications.drain()
        assertEquals(1, changes)
        assertEquals(0, callbacks)
        observer.observeReads { assertTrue(adapter.value) }
        setting.reset()
        StateNotifications.drain()
        assertEquals(2, changes)
        assertEquals(0, callbacks)
        observer.observeReads { setting.value }
        setting.value = true
        StateNotifications.drain()
        assertEquals(3, changes)
        assertEquals(1, callbacks)
        observer.observeReads { setting.value }
        setting.value = true
        StateNotifications.drain()
        assertEquals(3, changes)
        assertEquals(2, callbacks)
        observer.close()
    }

    @Test fun listPublishesImmutableSnapshotsAndVisibilityObservesSettings() {
        val visible = BoolSetting("visible", false, null, null)
        val list = StringListSetting("entries", listOf("a"), Setting.Visibility { visible.value })
        val old = list.value
        list.add("b")
        assertEquals(listOf("a"), old)
        assertEquals(listOf("a", "b"), list.value)
        assertFailsWith<UnsupportedOperationException> { (list.value as MutableList<String>).add("escape") }
        var changes = 0
        val observer = StateObservation { changes++ }
        observer.observeReads { assertFalse(list.isVisible) }
        visible.setValueSilently(true)
        StateNotifications.drain()
        assertEquals(1, changes)
        observer.close()
    }

    @Test fun resourceRefreshPublishesAfterUpdatingTranslations() {
        var changes = 0
        val observer = StateObservation { changes++ }
        val previous = observer.observeReads { TranslationManager.getRevision() }
        TranslationManager.refresh()
        StateNotifications.drain()
        assertEquals(1, changes)
        assertTrue(TranslationManager.getRevision() > previous)
        observer.close()
    }
}
