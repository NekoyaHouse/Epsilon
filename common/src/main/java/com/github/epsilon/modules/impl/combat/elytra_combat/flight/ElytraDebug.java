package com.github.epsilon.modules.impl.combat.elytra_combat.flight;

import com.github.epsilon.utils.player.ChatUtils;

/** ElytraCombat 决策调试输出，默认关闭，同一标签按时间节流。 */
public final class ElytraDebug {

    public static boolean enabled = false;

    /** 同一标签的最小重复间隔（毫秒）。 */
    private static final long THROTTLE_MS = 200L;

    /** 各决策点独立保存标签与输出时间。 */
    private static final int SLOTS = 32;
    private static final String[] LAST_LABELS = new String[SLOTS];
    private static final long[] LAST_TIMES = new long[SLOTS];

    private ElytraDebug() {
    }

    /**
     * 输出决策标签；内容变化或超过节流间隔时打印。
     *
     * @param slot 决策点槽位
     * @param tag 决策点名称
     * @param detail 附加数据
     */
    public static void log(int slot, String tag, Object detail) {
        if (!enabled || slot < 0 || slot >= SLOTS) {
            return;
        }

        String label = detail == null ? tag : tag + " " + detail;
        long now = System.currentTimeMillis();
        if (label.equals(LAST_LABELS[slot]) && now - LAST_TIMES[slot] < THROTTLE_MS) {
            return;
        }
        LAST_LABELS[slot] = label;
        LAST_TIMES[slot] = now;
        ChatUtils.addChatMessage("[ElytraCombat] " + label);
    }

    /** 清空标签与节流时间。 */
    public static void reset() {
        for (int i = 0; i < SLOTS; i++) {
            LAST_LABELS[i] = null;
            LAST_TIMES[i] = 0L;
        }
    }

    public static final int SLOT_MODE = 0;
    public static final int SLOT_TARGET = 1;
    public static final int SLOT_ACTION = 2;
    public static final int SLOT_BEHAVIOR = 3;
    public static final int SLOT_MACE_STATE = 4;
    public static final int SLOT_MACE_MANEUVER = 5;
    public static final int SLOT_SPEAR_STATE = 6;
    public static final int SLOT_PLANNER = 7;
    public static final int SLOT_AVOIDANCE = 8;
    public static final int SLOT_INTENT = 9;
    public static final int SLOT_ROTATION = 10;
    public static final int SLOT_HAZARD = 11;
    public static final int SLOT_PROBE = 12;
    public static final int SLOT_FIREWORK = 13;
    /** Spear 命中 / 脱战事件；与逐 tick 的状态摘要分开，避免互相覆盖标签。 */
    public static final int SLOT_SPEAR_HIT = 14;

    public static String fmt(double value) {
        return String.format("%.2f", value);
    }
}
