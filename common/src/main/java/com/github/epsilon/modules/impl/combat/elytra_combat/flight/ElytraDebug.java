package com.github.epsilon.modules.impl.combat.elytra_combat.flight;

import com.github.epsilon.utils.player.ChatUtils;

/**
 * ElytraCombat 的调试输出。
 *
 * <p>用 {@link ChatUtils#addChatMessage(String)} 按决策点打印标签，用于在游戏内定位
 * "转头抽风"这类问题：观察是决策层在摇摆，还是旋转链路被别处覆盖。</p>
 *
 * <p>默认关闭；开启后每 tick 会打印多行，建议只在复现问题的短时间内打开。</p>
 */
public final class ElytraDebug {

    /** 全局开关；默认关闭，避免刷屏。 */
    public static boolean enabled = false;

    /** 同一标签的最小重复间隔（毫秒），避免逐 tick 打印刷爆聊天栏。 */
    private static final long THROTTLE_MS = 200L;

    /** 标签槽位：每个决策点一个，用来判断"变化"而不是"每秒打印"。 */
    private static final int SLOTS = 32;
    private static final String[] LAST_LABELS = new String[SLOTS];
    private static final long[] LAST_TIMES = new long[SLOTS];

    private ElytraDebug() {
    }

    /**
     * 打印一个决策点。只有标签发生变化（或超过节流间隔）时才真正输出，
     * 这样聊天栏里出现的每一行都代表一次真实的决策切换。
     *
     * @param slot 决策点固定槽位，见调用处的常量
     * @param tag  决策点名称
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

    /** 重置缓存的标签，避免下次开启时把旧标签当成"没变化"而漏印。 */
    public static void reset() {
        for (int i = 0; i < SLOTS; i++) {
            LAST_LABELS[i] = null;
            LAST_TIMES[i] = 0L;
        }
    }

    // ===== 决策点槽位 =====
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

    /** 浮点数统一按 2 位小数输出，便于肉眼比对。 */
    public static String fmt(double value) {
        return String.format("%.2f", value);
    }
}
