package com.github.epsilon.modules.impl.player;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.*;
import com.github.epsilon.features.*;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.*;
import net.minecraft.world.entity.player.Player;
import java.util.*;

public final class EventCommand extends Module {
    public static final EventCommand INSTANCE = new EventCommand();
    private final StringSetting totem = stringSetting("Totem Action", "");
    private final StringSetting death = stringSetting("Death Action", "");
    private final StringSetting enter = stringSetting("Enter Action", "");
    private final StringSetting leave = stringSetting("Leave Action", "");
    private final StringSetting world = stringSetting("World Action", "");
    private final StringSetting timer = stringSetting("Timer Action", "");
    private final IntSetting period = intSetting("Period Seconds", 60, 1, 3600, 1);
    private final IntSetting cooldown = intSetting("Cooldown", 3000, 500, 60000, 100);
    private final Map<UUID, String> visible = new HashMap<>();
    private final ArrayDeque<Action> pending = new ArrayDeque<>();
    private record Action(String type, String text, String target, String scope, long expires) {}
    private Object level;
    private boolean wasDead;
    private long nextTimer;
    private EventCommand() { super("Event Command", Category.PLAYER); }
    private void enqueue(String type, String text, String target) {
        if (!text.isBlank() && pending.size() < 32 && pending.stream().noneMatch(a -> a.type.equals(type) && a.target.equals(target)))
            pending.add(new Action(type, text, target, SessionScope.key(), System.currentTimeMillis() + 5000));
    }
    @EventHandler private void tick(ClientTickEvent.Post e) {
        if (nullCheck()) { clear(); return; }
        long now = System.currentTimeMillis();
        if (level != mc.level) {
            clear(); level = mc.level; nextTimer = now + Math.max(1, period.getValue()) * 1000L;
            for (var p : mc.level.players()) if (p != mc.player) visible.put(p.getUUID(), p.getGameProfile().name());
            enqueue("world", world.getValue(), "");
        }
        boolean dead = mc.player.getHealth() <= 0;
        if (dead && !wasDead) enqueue("death", death.getValue(), "");
        wasDead = dead;
        Map<UUID, String> current = new HashMap<>();
        for (Player p : mc.level.players()) if (p != mc.player) current.put(p.getUUID(), p.getGameProfile().name());
        current.forEach((id, name) -> { if (!visible.containsKey(id)) enqueue("enter", enter.getValue(), name); });
        visible.forEach((id, name) -> { if (!current.containsKey(id)) enqueue("leave", leave.getValue(), name); });
        visible.clear(); visible.putAll(current);
        if (now >= nextTimer) { nextTimer = now + Math.max(1, period.getValue()) * 1000L; enqueue("timer", timer.getValue(), ""); }
        while (!pending.isEmpty() && (pending.peek().expires < now || !pending.peek().scope.equals(SessionScope.key()))) pending.remove();
        if (!pending.isEmpty()) {
            Action a = pending.peek();
            if (CommandActions.ready("event:" + a.type)) {
                pending.remove(); // Invalid/unsupported actions must not retry and flood local chat.
                CommandActions.run("event:" + a.type, a.text, a.target, cooldown.getValue());
            }
        }
    }
    @EventHandler private void totem(TotemPopEvent event) {
        if (!nullCheck() && event.entity() == mc.player) enqueue("totem", totem.getValue(), mc.player.getGameProfile().name());
    }
    private void clear() { pending.clear(); visible.clear(); level = null; wasDead = false; nextTimer = 0; }
    @EventHandler private void left(GameLeftEvent e) { clear(); CommandActions.clear(); }
    @EventHandler private void world(LevelUpdateEvent e) { clear(); CommandActions.clear(); }
    @Override protected void onDisable() { clear(); CommandActions.clearSources("event:"); }
}
