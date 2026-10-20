package com.github.epsilon.features;

import com.github.epsilon.managers.ModuleManager;
import com.github.epsilon.utils.player.ChatUtils;
import net.minecraft.world.entity.Entity;
import java.util.*;
import static com.github.epsilon.Constants.mc;

public final class CommandActions {
    private static boolean dispatching;
    private static long nextAllowed;
    private static final Map<String, Long> COOLDOWNS = new HashMap<>();
    private CommandActions() {}
    public static void clear() { COOLDOWNS.clear(); nextAllowed = 0; }
    public static void clearSources(String prefix) { COOLDOWNS.keySet().removeIf(key -> key.startsWith(prefix)); }
    public static boolean ready(String source) {
        long now = System.currentTimeMillis();
        return !dispatching && now >= nextAllowed && now >= COOLDOWNS.getOrDefault(source, 0L);
    }
    public static boolean run(String source, String template, String target, int cooldownMillis) {
        if (!ready(source) || mc.player == null || mc.getConnection() == null || template.isBlank()) return false;
        if (template.contains("{target}") && (target == null || target.isBlank()) && mc.crosshairPickEntity == null) return false;
        long now = System.currentTimeMillis();
        if (now < nextAllowed || now < COOLDOWNS.getOrDefault(source, 0L)) return false;
        String command = expand(template, target).strip();
        if (command.isEmpty() || command.length() > 256 || command.chars().anyMatch(c -> c < 32 || c == 127)) return false;
        if (COOLDOWNS.size() > 512) COOLDOWNS.entrySet().removeIf(e -> e.getValue() < now);
        nextAllowed = now + 1000;
        COOLDOWNS.put(source, now + Math.max(1000, cooldownMillis));
        dispatching = true;
        try {
            if (command.startsWith("client:")) {
                String[] args = command.substring(7).split(":", 2);
                if (args.length == 2 && Set.of("toggle", "enable", "disable").contains(args[0])) {
                    for (var module : ModuleManager.INSTANCE.getModules()) {
                        if (module.getName().equalsIgnoreCase(args[1]) && !Set.of("Bind Command", "Event Command", "Auto Logout", "Auto Reconnect").contains(module.getName())) {
                            if (args[0].equals("toggle")) module.toggle(); else module.setEnabled(args[0].equals("enable"));
                            return true;
                        }
                    }
                }
                if (command.equals("client:copy-position")) { mc.keyboardHandler.setClipboard(expand("{x} {y} {z}", target)); return true; }
                ChatUtils.addChatMessage(FeatureText.get("unsupported_action")); return false;
            }
            if (command.startsWith("/")) mc.getConnection().sendCommand(command.substring(1));
            else mc.getConnection().sendChat(command);
            return true;
        } finally { dispatching = false; }
    }
    public static String expand(String template, String target) {
        if (mc.player == null) return template;
        Entity aimed = mc.crosshairPickEntity;
        String actualTarget = target == null || target.isBlank() ? (aimed == null ? "" : aimed.getName().getString()) : target;
        return template.replace("{player}", mc.player.getGameProfile().name()).replace("{target}", actualTarget)
                .replace("{x}", Integer.toString(mc.player.getBlockX())).replace("{y}", Integer.toString(mc.player.getBlockY()))
                .replace("{z}", Integer.toString(mc.player.getBlockZ())).replace("{dimension}", SessionScope.dimension());
    }
}
