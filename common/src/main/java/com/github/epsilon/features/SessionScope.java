package com.github.epsilon.features;

import net.minecraft.world.level.storage.LevelResource;
import static com.github.epsilon.Constants.mc;

public final class SessionScope {
    private SessionScope() {}
    public static String server() {
        if (mc.getConnection() == null || mc.level == null) return "";
        if (mc.getSingleplayerServer() != null)
            return "local:" + mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        return mc.getCurrentServer() == null ? "" : "server:" + mc.getCurrentServer().ip.toLowerCase(java.util.Locale.ROOT);
    }
    public static String dimension() { return mc.level == null ? "" : mc.level.dimension().identifier().toString(); }
    public static String key() { return server() + "|" + dimension(); }
}
