package com.github.epsilon.features;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import static com.github.epsilon.Constants.mc;

/** Stores the actual server-provided stack, including custom data; text is usable while offline. */
public final class ObservedStack {
    private ObservedStack() {}
    public static String slimefunId(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return "";
        var root = data.copyTag();
        if (root.get("PublicBukkitValues") instanceof CompoundTag values
                && values.get("slimefun:slimefun_item") instanceof StringTag id) return id.value();
        return "";
    }
    public static String identity(ItemStack stack) {
        if (stack.isEmpty()) return "";
        String sf = slimefunId(stack);
        return sf.isEmpty() ? BuiltInRegistries.ITEM.getKey(stack.getItem()).toString() : "slimefun:" + sf;
    }
    public static JsonObject encode(ItemStack stack) {
        JsonObject data = new JsonObject();
        data.addProperty("id", identity(stack));
        data.addProperty("name", stack.isEmpty() ? "" : stack.getDisplayName().getString());
        data.addProperty("count", stack.getCount());
        if (!stack.isEmpty() && mc.level != null) {
            var encoded = ItemStack.CODEC.encodeStart(mc.level.registryAccess().createSerializationContext(JsonOps.INSTANCE), stack);
            encoded.result().ifPresent(value -> data.add("stack", value));
            encoded.error().ifPresent(error -> com.github.epsilon.Constants.LOGGER.warn("Cannot serialize observed item: {}", error.message()));
        }
        return data;
    }
    public static ItemStack decode(JsonObject data) {
        if (!data.has("stack") || mc.level == null) return ItemStack.EMPTY;
        return ItemStack.CODEC.parse(mc.level.registryAccess().createSerializationContext(JsonOps.INSTANCE), data.get("stack")).result().orElse(ItemStack.EMPTY);
    }
    public static String label(JsonObject data) {
        return data.get("count").getAsInt() + " × " + data.get("name").getAsString() + " [" + data.get("id").getAsString() + "]";
    }
}
