package com.github.epsilon.modules.impl.player;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.*;
import com.github.epsilon.features.CommandActions;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.*;
import com.github.epsilon.utils.client.KeybindUtils;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.*;

public final class BindCommand extends Module {
    public static final BindCommand INSTANCE = new BindCommand();
    private final KeybindSetting trigger = keybindSetting("Trigger", -1);
    private final StringSetting action = stringSetting("Action", "client:copy-position");
    private final StringListSetting bindings = stringListSetting("Bindings", List.of());
    private final IntSetting cooldown = intSetting("Cooldown", 1000, 500, 60000, 100);
    private final Set<Integer> pressed = new HashSet<>();
    private BindCommand() { super("Bind Command", Category.PLAYER); }
    @EventHandler private void key(KeyPressEvent e) { handle(e.getKey(), e.getAction()); }
    @EventHandler private void mouse(MousePressEvent e) { handle(KeybindUtils.encodeMouseButton(e.getButton()), e.getAction()); }
    private void handle(int key, int state) {
        if (state == InputConstants.RELEASE) { pressed.remove(key); return; }
        if (state != InputConstants.PRESS || nullCheck() || mc.gui.screen() != null || !pressed.add(key)) return;
        if (key == trigger.getValue()) CommandActions.run("key:" + key, action.getValue(), "", cooldown.getValue());
        // Optional additional rows: Epsilon key code | action. Split once so action may contain '|'.
        for (String row : bindings.getValue()) {
            String[] parts = row.split("\\|", 2);
            if (parts.length != 2) continue;
            try { if (Integer.parseInt(parts[0].trim()) == key) CommandActions.run("key:" + key, parts[1], "", cooldown.getValue()); }
            catch (NumberFormatException ignored) { }
        }
    }
    @EventHandler private void left(GameLeftEvent e) { pressed.clear(); CommandActions.clear(); }
    @EventHandler private void world(LevelUpdateEvent e) { pressed.clear(); CommandActions.clear(); }
    @Override protected void onDisable() { pressed.clear(); CommandActions.clearSources("key:"); }
}
