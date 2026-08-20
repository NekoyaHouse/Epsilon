package me.sofurry.via.features.networking.armor_hud;

import com.github.epsilon.Constants;
import com.github.epsilon.modules.impl.ClientSetting;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.protocols.v1_8to1_9.Protocol1_8To1_9;
import com.viaversion.viaversion.protocols.v1_8to1_9.data.ArmorTypes1_8;
import com.viaversion.viaversion.protocols.v1_8to1_9.packet.ClientboundPackets1_9;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import me.sofurry.via.util.ViaVersionClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public final class ArmorHudEmulation1_8 {

    private static final UUID ARMOR_POINTS_UUID = UUID.fromString("2AD3F246-FEE1-4E67-B886-69FD380BB150");
    private static final EquipmentSlot[] ARMOR_SLOTS = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private static double previousArmorPoints = 0;

    public static void init() {
        ViaVersionClientEvents.START_LEVEL_TICK.register(world -> {
            if (!ClientSetting.INSTANCE.emulateArmorHud.isEnabled()) {
                return;
            }

            if (Minecraft.getInstance().player != null) {
                final UserConnection connection = ProtocolTranslator.getPlayNetworkUserConnection();
                if (connection != null) {
                    try {
                        sendArmorUpdate(connection);
                    } catch (Throwable t) {
                        Constants.LOGGER.error("Error sending armor update", t);
                    }
                }
            } else {
                previousArmorPoints = 0;
            }
        });
    }

    private static void sendArmorUpdate(final UserConnection connection) {
        // Calculate the armor points.
        int armor = 0;
        for (final EquipmentSlot slot : ARMOR_SLOTS) {
            final ItemStack stack = Minecraft.getInstance().player.getInventory().equipment.get(slot);
            if (stack.isEmpty()) {
                continue;
            }

            final String identifier = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            armor += ArmorTypes1_8.findByType(identifier).getArmorPoints();
        }

        // We only want to update the armor points if they actually changed.
        if (armor == previousArmorPoints) {
            return;
        }
        previousArmorPoints = armor;

        final PacketWrapper updateAttributes = PacketWrapper.create(ClientboundPackets1_9.UPDATE_ATTRIBUTES, connection);
        updateAttributes.write(Types.VAR_INT, Minecraft.getInstance().player.getId());
        updateAttributes.write(Types.INT, 1);
        updateAttributes.write(Types.STRING, "generic.armor");
        updateAttributes.write(Types.DOUBLE, 0.0D);
        updateAttributes.write(Types.VAR_INT, 1);
        updateAttributes.write(Types.UUID, ARMOR_POINTS_UUID);
        updateAttributes.write(Types.DOUBLE, (double) armor);
        updateAttributes.write(Types.BYTE, (byte) 0);
        updateAttributes.scheduleSend(Protocol1_8To1_9.class);
    }

}
