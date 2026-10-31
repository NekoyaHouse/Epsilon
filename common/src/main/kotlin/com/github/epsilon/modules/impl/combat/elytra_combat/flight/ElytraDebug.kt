package com.github.epsilon.modules.impl.combat.elytra_combat.flight

import com.github.epsilon.utils.player.ChatUtils

/**
 * ElytraCombat 决策调试输出，默认关闭，同一标签按时间节流。
 */
object ElytraDebug {

    var enabled: Boolean = false

    /** 同一标签的最小重复间隔（毫秒）。 */
    private const val THROTTLE_MS = 200L

    /** 各决策点独立保存标签与输出时间。 */
    private const val SLOTS = 32
    private val LAST_LABELS = arrayOfNulls<String>(SLOTS)
    private val LAST_TIMES = LongArray(SLOTS)

    /**
     * 输出决策标签；内容变化或超过节流间隔时打印。
     *
     * @param slot   决策点槽位
     * @param tag    决策点名称
     * @param detail 附加数据
     */
    fun log(slot: Int, tag: String, detail: Any?) {
        if (!enabled || slot < 0 || slot >= SLOTS) {
            return
        }

        val label = if (detail == null) tag else "$tag $detail"
        val now = System.currentTimeMillis()
        if (label == LAST_LABELS[slot] && now - LAST_TIMES[slot] < THROTTLE_MS) {
            return
        }
        LAST_LABELS[slot] = label
        LAST_TIMES[slot] = now
        ChatUtils.sendMessage("[ElytraCombat] $label")
    }

    /** 清空标签与节流时间。 */
    fun reset() {
        for (i in 0 until SLOTS) {
            LAST_LABELS[i] = null
            LAST_TIMES[i] = 0L
        }
    }

    const val SLOT_MODE: Int = 0
    const val SLOT_TARGET: Int = 1
    const val SLOT_ACTION: Int = 2
    const val SLOT_BEHAVIOR: Int = 3
    const val SLOT_MACE_STATE: Int = 4
    const val SLOT_MACE_MANEUVER: Int = 5
    const val SLOT_SPEAR_STATE: Int = 6
    const val SLOT_PLANNER: Int = 7
    const val SLOT_AVOIDANCE: Int = 8
    const val SLOT_INTENT: Int = 9
    const val SLOT_ROTATION: Int = 10
    const val SLOT_HAZARD: Int = 11
    const val SLOT_PROBE: Int = 12
    const val SLOT_FIREWORK: Int = 13

    /** Spear 命中 / 脱战事件；与逐 tick 的状态摘要分开，避免互相覆盖标签。 */
    const val SLOT_SPEAR_HIT: Int = 14

    fun fmt(value: Double): String {
        return String.format("%.2f", value)
    }

}
