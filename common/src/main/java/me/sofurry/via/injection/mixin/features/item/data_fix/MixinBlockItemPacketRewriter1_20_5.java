package me.sofurry.via.injection.mixin.features.item.data_fix;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.HolderSet;
import com.viaversion.viaversion.api.minecraft.data.StructuredDataContainer;
import com.viaversion.viaversion.api.minecraft.data.StructuredDataKey;
import com.viaversion.viaversion.api.minecraft.item.Item;
import com.viaversion.viaversion.api.minecraft.item.data.ToolProperties;
import com.viaversion.viaversion.api.minecraft.item.data.ToolRule;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.type.Type;
import com.viaversion.viaversion.libs.fastutil.ints.IntOpenHashSet;
import com.viaversion.viaversion.libs.fastutil.ints.IntSet;
import com.viaversion.viaversion.libs.gson.JsonArray;
import com.viaversion.viaversion.libs.gson.JsonElement;
import com.viaversion.viaversion.libs.gson.JsonObject;
import com.viaversion.viaversion.protocols.v1_20_2to1_20_3.packet.ClientboundPacket1_20_3;
import com.viaversion.viaversion.protocols.v1_20_3to1_20_5.Protocol1_20_3To1_20_5;
import com.viaversion.viaversion.protocols.v1_20_3to1_20_5.packet.ServerboundPacket1_20_5;
import com.viaversion.viaversion.protocols.v1_20_3to1_20_5.rewriter.BlockItemPacketRewriter1_20_5;
import com.viaversion.viaversion.rewriter.ItemRewriter;
import me.sofurry.via.ViaVersionImpl;
import me.sofurry.via.api.events.LoadingCycleCallback;
import me.sofurry.via.protocoltranslator.impl.ViaVersionMappingDataLoader;
import net.raphimc.vialegacy.api.LegacyProtocolVersion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;

@Mixin(value = BlockItemPacketRewriter1_20_5.class, remap = false)
public abstract class MixinBlockItemPacketRewriter1_20_5 extends ItemRewriter<ClientboundPacket1_20_3, ServerboundPacket1_20_5, Protocol1_20_3To1_20_5> {

    @Unique
    private final Set<String> viaVersion$foodItems_b1_7_3 = new HashSet<>();

    @Unique
    private final Map<String, Integer> viaVersion$armorMaxDamage_b1_8_1 = new HashMap<>();

    @Unique
    private final Map<ProtocolVersion, Map<String, ToolProperties>> viaVersion$toolDataChanges = new LinkedHashMap<>();

    public MixinBlockItemPacketRewriter1_20_5(Protocol1_20_3To1_20_5 protocol, Type<Item> itemType, Type<Item[]> itemArrayType, Type<Item> mappedItemType, Type<Item[]> mappedItemArrayType) {
        super(protocol, itemType, itemArrayType, mappedItemType, mappedItemArrayType);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void loadItemMappings(Protocol1_20_3To1_20_5 protocol, CallbackInfo ci) {
        // Technically, it would be cleaner to split mapping loading into their respective protocols, but that will be impossible
        // in a clean way, so let's just wait for Via* to load all protocols and then load everything in here.
        this.viaVersion$foodItems_b1_7_3.add("minecraft:apple");
        this.viaVersion$foodItems_b1_7_3.add("minecraft:mushroom_stew");
        this.viaVersion$foodItems_b1_7_3.add("minecraft:bread");
        this.viaVersion$foodItems_b1_7_3.add("minecraft:porkchop");
        this.viaVersion$foodItems_b1_7_3.add("minecraft:cooked_porkchop");
        this.viaVersion$foodItems_b1_7_3.add("minecraft:golden_apple");
        this.viaVersion$foodItems_b1_7_3.add("minecraft:cod");
        this.viaVersion$foodItems_b1_7_3.add("minecraft:cooked_cod");
        this.viaVersion$foodItems_b1_7_3.add("minecraft:cookie");

        final JsonObject armorMaxDamages = ViaVersionMappingDataLoader.INSTANCE.loadData("armor-damages-b1.8.1.json");
        for (Map.Entry<String, JsonElement> entry : armorMaxDamages.entrySet()) {
            this.viaVersion$armorMaxDamage_b1_8_1.put(entry.getKey(), entry.getValue().getAsInt());
        }

        ViaVersionImpl.LOADING_CYCLE.register(cycle -> {
            if (cycle != LoadingCycleCallback.LoadingCycle.POST_VIAVERSION_LOAD) {
                return;
            }

            final JsonObject itemToolComponents = ViaVersionMappingDataLoader.INSTANCE.loadData("item-tool-components.json");
            for (Map.Entry<String, JsonElement> entry : itemToolComponents.entrySet()) {
                final ProtocolVersion version = ProtocolVersion.getClosest(entry.getKey());
                if (version == null) {
                    throw new IllegalStateException("Unknown protocol version: " + entry.getKey());
                }
                final Map<String, ToolProperties> toolProperties = new HashMap<>();
                final JsonArray toolComponents = entry.getValue().getAsJsonArray();
                for (JsonElement toolComponent : toolComponents) {
                    final JsonObject toolComponentObject = toolComponent.getAsJsonObject();
                    final String item = toolComponentObject.get("item").getAsString();
                    final float defaultMiningSpeed = toolComponentObject.get("default_mining_speed").getAsFloat();
                    final int damagePerBlock = toolComponentObject.get("damage_per_block").getAsInt();
                    final int[] suitableFor = this.viaVersion$blockJsonArrayToIds(version, toolComponentObject.getAsJsonArray("suitable_for"));
                    final List<ToolRule> toolRules = new ArrayList<>();
                    final JsonArray miningSpeeds = toolComponentObject.getAsJsonArray("mining_speeds");
                    for (JsonElement miningSpeed : miningSpeeds) {
                        final JsonObject miningSpeedObject = miningSpeed.getAsJsonObject();
                        final int[] blocks = this.viaVersion$blockJsonArrayToIds(version, miningSpeedObject.getAsJsonArray("blocks"));
                        final float speed = miningSpeedObject.get("speed").getAsFloat();
                        toolRules.add(new ToolRule(HolderSet.of(blocks), speed, null));
                    }
                    if (suitableFor.length > 0) {
                        toolRules.add(new ToolRule(HolderSet.of(suitableFor), null, true));
                    }
                    toolProperties.put(item, new ToolProperties(toolRules.toArray(new ToolRule[0]), defaultMiningSpeed, damagePerBlock));
                }
                this.viaVersion$toolDataChanges.put(version, toolProperties);
            }
        });
    }

    // Older servers don't have these components, so we can use them to emulate old item behaviour without the need
    // of modifying tons of code in the game.
    @Inject(method = "appendItemDataFixComponents", at = @At("RETURN"))
    private void appendItemDataFixComponents(UserConnection connection, Item item, CallbackInfo ci) {
        final StructuredDataContainer data = item.dataContainer();
        final String identifier = this.protocol.getMappingData().getFullItemMappings().identifier(item.identifier());

        // Fix durability tooltip displaying wrong
        if (connection.getProtocolInfo().serverProtocolVersion().olderThanOrEqualTo(LegacyProtocolVersion.b1_8tob1_8_1)) {
            if (this.viaVersion$armorMaxDamage_b1_8_1.containsKey(identifier)) {
                data.set(StructuredDataKey.MAX_DAMAGE, this.viaVersion$armorMaxDamage_b1_8_1.get(identifier));
            }
        }

        // Fix item desyncs
        if (connection.getProtocolInfo().serverProtocolVersion().olderThanOrEqualTo(LegacyProtocolVersion.b1_7tob1_7_3)) {
            if (this.viaVersion$foodItems_b1_7_3.contains(identifier)) {
                data.set(StructuredDataKey.MAX_STACK_SIZE, 1);
                data.setEmpty(StructuredDataKey.FOOD1_20_5);
            }
        }

        // Tool data changes include mining speeds as well as suitable blocks and damage values
        for (Map.Entry<ProtocolVersion, Map<String, ToolProperties>> entry : this.viaVersion$toolDataChanges.entrySet()) {
            if (connection.getProtocolInfo().serverProtocolVersion().olderThanOrEqualTo(entry.getKey())) {
                final ToolProperties toolProperties = entry.getValue().get(identifier);
                if (toolProperties != null) {
                    data.set(StructuredDataKey.TOOL1_20_5, toolProperties);
                    break;
                }
            }
        }
    }

    // Converts block identifiers as well as materials (prefixed with #) to block ids
    @Unique
    private int[] viaVersion$blockJsonArrayToIds(final ProtocolVersion protocolVersion, final JsonArray jsonArray) {
        final IntSet ids = new IntOpenHashSet();
        for (final JsonElement element : jsonArray) {
            final String name = element.getAsString();
            if (name.startsWith("#")) { // Material name
                final String material = name.substring(1);
                for (Map.Entry<String, Map<ProtocolVersion, String>> entry : ViaVersionMappingDataLoader.BLOCK_MATERIALS.entrySet()) {
                    for (Map.Entry<ProtocolVersion, String> materialEntry : entry.getValue().entrySet()) {
                        if (protocolVersion.olderThanOrEqualTo(materialEntry.getKey()) && materialEntry.getValue().equals(material)) {
                            ids.add(this.protocol.getMappingData().blockId(entry.getKey()));
                            break;
                        }
                    }
                }
            } else if (name.startsWith("-")) { // Block name
                ids.remove(this.protocol.getMappingData().blockId(name.substring(1)));
            } else { // Block name
                ids.add(this.protocol.getMappingData().blockId(name));
            }
        }
        return ids.toIntArray();
    }

}
