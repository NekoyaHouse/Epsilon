package com.github.epsilon.modules.impl.player;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.*;
import com.github.epsilon.features.*;
import com.github.epsilon.gui.screen.FeatureBrowserScreen;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.*;
import com.github.epsilon.utils.player.ChatUtils;
import com.google.gson.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import java.time.Instant;
import java.util.*;

public final class ChestHistory extends Module {
    public static final ChestHistory INSTANCE = new ChestHistory();
    private final IntSetting maximum = intSetting("Maximum Records", 1000, 10, 5000, 10);
    private final BoolSetting allServers = boolSetting("All Servers", false);
    private final StringSetting search = stringSetting("Search", "");
    private JsonObject database;
    private boolean loadFailed;
    private BlockPos pendingPosition;
    private long clickedAt;
    private String clickedScope = "";
    private AbstractContainerMenu activeMenu;
    private JsonObject activeRecord;
    private String activeKey;
    private String activeScope = "";
    private boolean dirty;
    private long lastWrite;
    private ChestHistory() {
        super("Chest History", Category.PLAYER);
        buttonSetting("Browse", this::browse);
        buttonSetting("Clear Server", this::clearServer);
        buttonSetting("Clear All", this::clearAll);
    }
    private boolean ensureLoaded() {
        if (database != null) return true;
        if (loadFailed) return false;
        try {
            JsonObject loaded = FeatureStore.read("chest-history");
            for (var entry : loaded.entrySet()) validateRecord(entry.getValue().getAsJsonObject());
            database = loaded; return true;
        }
        catch (RuntimeException e) { loadFailed = true; ChatUtils.addChatMessage(FeatureText.get("load_failed")); return false; }
    }
    private static void validateRecord(JsonObject record) {
        for (String key : List.of("server", "dimension", "title", "position")) record.get(key).getAsString();
        record.get("seen").getAsLong();
        JsonArray items = record.getAsJsonArray("items");
        if (items.size() > 256) throw new IllegalStateException("Invalid history size");
        for (var value : items) {
            JsonObject item = value.getAsJsonObject();
            item.get("id").getAsString(); item.get("name").getAsString(); item.get("count").getAsInt();
        }
    }
    @EventHandler private void clicked(BlockInteractionEvent event) {
        if (nullCheck()) return;
        // A block entity is required. Virtual server menus stay explicitly unlocated.
        pendingPosition = mc.level.getBlockEntity(event.position()) == null ? null : event.position();
        clickedAt = System.currentTimeMillis(); clickedScope = SessionScope.key();
    }
    @EventHandler private void content(ContainerContentEvent event) {
        if (nullCheck() || !ensureLoaded()) return;
        AbstractContainerMenu menu = mc.player.containerMenu;
        if (menu == mc.player.inventoryMenu || menu.containerId != event.containerId()) return;
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> screen) || screen.getMenu() != menu) return;
        int count = containerSlots(menu);
        if (count == 0 || count > 256) return;
        if (menu != activeMenu) {
            if (!event.full()) return;
            activeMenu = menu; activeScope = SessionScope.key();
            BlockPos pos = System.currentTimeMillis() - clickedAt < 3000 && clickedScope.equals(activeScope) ? pendingPosition : null;
            pendingPosition = null;
            activeRecord = new JsonObject();
            activeRecord.addProperty("server", SessionScope.server());
            activeRecord.addProperty("dimension", SessionScope.dimension());
            activeRecord.addProperty("title", screen.getTitle().getString());
            activeRecord.addProperty("position", pos == null ? "" : pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
            activeKey = activeScope + "|" + (pos == null ? "unlocated:" + UUID.randomUUID() : pos.asLong());
            JsonArray items = new JsonArray();
            for (int i = 0; i < count; i++) items.add(ObservedStack.encode(ItemStack.EMPTY));
            activeRecord.add("items", items);
        }
        if (!activeScope.equals(SessionScope.key())) { clearSession(); return; }
        JsonArray items = activeRecord.getAsJsonArray("items");
        if (event.full()) {
            if (event.items().size() < count) return;
            for (int i = 0; i < count; i++) items.set(i, ObservedStack.encode(event.items().get(i)));
        } else if (event.slot() >= 0 && event.slot() < count) {
            items.set(event.slot(), ObservedStack.encode(event.items().getFirst()));
        } else return;
        activeRecord.addProperty("seen", System.currentTimeMillis());
        database.add(activeKey, activeRecord); dirty = true;
        while (database.size() > Math.max(10, maximum.getValue())) {
            String oldest = database.entrySet().stream().min(Comparator.comparingLong(e -> e.getValue().getAsJsonObject().get("seen").getAsLong())).orElseThrow().getKey();
            database.remove(oldest);
        }
    }
    public static int containerSlots(AbstractContainerMenu menu) {
        if (mcPlayerAbsent()) return 0;
        int count = 0;
        for (var slot : menu.slots) {
            if (slot.container == com.github.epsilon.Constants.mc.player.getInventory()) break;
            count++;
        }
        return count == menu.slots.size() ? 0 : count;
    }
    private static boolean mcPlayerAbsent() { return com.github.epsilon.Constants.mc.player == null; }
    @EventHandler private void tick(ClientTickEvent.Post event) {
        if (activeMenu != null && (nullCheck() || mc.player.containerMenu != activeMenu || !SessionScope.key().equals(activeScope))) { flush(); clearSession(); }
        if (dirty && System.currentTimeMillis() - lastWrite > 1500) flush();
    }
    @EventHandler private void left(GameLeftEvent event) { flush(); clearSession(); }
    @EventHandler private void world(LevelUpdateEvent event) { flush(); clearSession(); }
    @Override protected void onDisable() { flush(); clearSession(); }
    private void clearSession() { activeMenu = null; activeRecord = null; pendingPosition = null; activeKey = null; activeScope = ""; clickedScope = ""; }
    private void flush() { if (dirty && database != null) { dirty = !FeatureStore.write("chest-history", database); lastWrite = System.currentTimeMillis(); } }
    private void clearServer() {
        if (!ensureLoaded() || SessionScope.server().isEmpty()) return;
        String server = SessionScope.server();
        database.entrySet().removeIf(e -> e.getValue().getAsJsonObject().get("server").getAsString().equals(server));
        clearSession(); dirty = true; flush();
    }
    public void saveHistory() { flush(); }
    private void clearAll() { if (!ensureLoaded()) return; database = new JsonObject(); clearSession(); dirty = true; flush(); }
    public void browse() {
        if (!ensureLoaded()) return;
        String server = SessionScope.server();
        mc.gui.setScreen(new FeatureBrowserScreen(mc.gui.screen(), getTranslatedName(), FeatureText.get("last_observed"), () -> {
            List<FeatureBrowserScreen.Row> rows = new ArrayList<>();
            database.entrySet().stream().sorted(Comparator.comparingLong((Map.Entry<String, JsonElement> e) -> e.getValue().getAsJsonObject().get("seen").getAsLong()).reversed()).forEach(e -> {
                JsonObject record = e.getValue().getAsJsonObject();
                if (!allServers.getValue() && !server.isEmpty() && !record.get("server").getAsString().equals(server)) return;
                List<String> lines = new ArrayList<>();
                lines.add(FeatureText.get("last_observed") + ": " + Instant.ofEpochMilli(record.get("seen").getAsLong()));
                lines.add(record.get("server").getAsString() + " | " + record.get("dimension").getAsString());
                lines.add(record.get("position").getAsString().isEmpty() ? FeatureText.get("unknown_position") : record.get("position").getAsString());
                int slot = 0;
                for (JsonElement item : record.getAsJsonArray("items")) {
                    JsonObject value = item.getAsJsonObject();
                    if (value.get("count").getAsInt() > 0) lines.add("#" + slot + " " + ObservedStack.label(value));
                    slot++;
                }
                if (lines.size() == 3) lines.add(FeatureText.get("empty_contents"));
                String label = record.get("title").getAsString() + " @ " + (record.get("position").getAsString().isEmpty() ? FeatureText.get("unknown_position") : record.get("position").getAsString());
                if ((label + lines).toLowerCase(Locale.ROOT).contains(search.getValue().toLowerCase(Locale.ROOT))) rows.add(new FeatureBrowserScreen.Row(label, lines, null));
            });
            return rows;
        }));
    }
}
