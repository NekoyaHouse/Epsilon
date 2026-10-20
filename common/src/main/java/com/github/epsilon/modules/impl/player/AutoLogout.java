package com.github.epsilon.modules.impl.player;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.*;
import com.github.epsilon.features.*;
import com.github.epsilon.managers.FriendManager;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.*;
import net.minecraft.world.item.Items;

public final class AutoLogout extends Module {
    public static final AutoLogout INSTANCE = new AutoLogout();
    public enum Response { AlertOnly, Disconnect }
    private final EnumSetting<Response> response = enumSetting("Response", Response.AlertOnly);
    private final BoolSetting health = boolSetting("Low Health", true);
    private final DoubleSetting minimumHealth = doubleSetting("Minimum Health", 6, 1, 40, 0.5, health::getValue);
    private final BoolSetting totems = boolSetting("Low Totems", false);
    private final IntSetting minimumTotems = intSetting("Minimum Totems", 1, 0, 36, 1, totems::getValue);
    private final BoolSetting height = boolSetting("Low Height", false);
    private final IntSetting minimumHeight = intSetting("Minimum Height", -60, -128, 512, 1, height::getValue);
    private final BoolSetting strangers = boolSetting("Strangers", false);
    private final DoubleSetting range = doubleSetting("Stranger Range", 32, 1, 256, 1, strangers::getValue);
    private final IntSetting grace = intSetting("Grace Seconds", 5, 0, 60, 1);
    private Object world;
    private long readyAt;
    private long lastAlert;
    private boolean fired;
    private AutoLogout() { super("Auto Logout", Category.PLAYER); }
    public static void disconnectSafely(String reason) { DisconnectGuard.safetyLogout(reason); }
    @EventHandler private void tick(ClientTickEvent.Post e) {
        if (nullCheck()) { resetState(); return; }
        long now = System.currentTimeMillis();
        if (world != mc.level) { resetState(); world = mc.level; readyAt = now + grace.getValue() * 1000L; }
        if (fired || now < readyAt) return;
        String reason = "";
        if (health.getValue() && mc.player.getHealth() + mc.player.getAbsorptionAmount() <= minimumHealth.getValue()) reason = FeatureText.get("low_health");
        int count = 0;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            var stack = mc.player.getInventory().getItem(i); if (stack.is(Items.TOTEM_OF_UNDYING)) count += stack.getCount();
        }
        if (totems.getValue() && count <= minimumTotems.getValue()) reason = FeatureText.get("low_totems");
        if (height.getValue() && mc.player.getY() <= minimumHeight.getValue()) reason = FeatureText.get("low_height");
        if (strangers.getValue()) for (var player : mc.level.players()) {
            if (player != mc.player && !player.isSpectator() && !FriendManager.INSTANCE.isFriend(player)
                    && player.distanceToSqr(mc.player) <= range.getValue() * range.getValue()) {
                reason = FeatureText.get("stranger") + ": " + player.getGameProfile().name(); break;
            }
        }
        if (reason.isEmpty()) return;
        if (now - lastAlert >= 10000) { PlayerAlarms.INSTANCE.safetyAlert(reason); lastAlert = now; }
        if (response.is(Response.Disconnect)) { fired = true; disconnectSafely(FeatureText.get("safety_logout") + ": " + reason); }
    }
    private void resetState() { world = null; readyAt = 0; fired = false; lastAlert = 0; }
    @EventHandler private void left(GameLeftEvent e) { resetState(); }
    @EventHandler private void world(LevelUpdateEvent e) { resetState(); }
    @Override protected void onDisable() { resetState(); }
}
