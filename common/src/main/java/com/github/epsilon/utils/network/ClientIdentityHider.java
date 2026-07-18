package com.github.epsilon.utils.network;

import com.github.epsilon.Constants;
import com.github.epsilon.modules.impl.ClientSetting;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;

public final class ClientIdentityHider {

    private static final String VANILLA_BRAND = "vanilla";
    private static final Set<String> HIDDEN_NAMESPACES = Set.of(Constants.MOD_ID);
    private static final Set<String> VANILLA_HIDDEN_NAMESPACES = Set.of("c", "neoforge", "fabric", "fabric-networking-api-v1");
    private static final Set<String> VANILLA_HIDDEN_MINECRAFT_PAYLOADS = Set.of("register", "unregister");

    private ClientIdentityHider() {
    }

    public static Packet<?> filterServerboundPacket(Packet<?> packet) {
        if (!(packet instanceof ServerboundCustomPayloadPacket customPayloadPacket)) {
            return packet;
        }

        ClientSetting.HideMode mode = ClientSetting.INSTANCE.hideMode.getValue();
        if (mode == ClientSetting.HideMode.None) {
            return packet;
        }

        CustomPacketPayload payload = customPayloadPacket.payload();
        if (payload instanceof BrandPayload) {
            return mode == ClientSetting.HideMode.Vanilla
                    ? new ServerboundCustomPayloadPacket(new BrandPayload(VANILLA_BRAND))
                    : packet;
        }

        Identifier id = payload.type().id();
        if (mode == ClientSetting.HideMode.Vanilla && shouldHideAsVanilla(id)) {
            return null;
        }

        CustomPacketPayload filteredPayload = filterPayload(payload, mode);
        if (filteredPayload == null) {
            return null;
        }
        if (filteredPayload == payload) {
            return packet;
        }
        return new ServerboundCustomPayloadPacket(filteredPayload);
    }

    public static String filterClientBrand(String brand) {
        return ClientSetting.INSTANCE.hideMode.is(ClientSetting.HideMode.Vanilla) ? VANILLA_BRAND : brand;
    }

    private static boolean shouldHideAsVanilla(Identifier id) {
        if ("minecraft".equals(id.getNamespace())) {
            return VANILLA_HIDDEN_MINECRAFT_PAYLOADS.contains(id.getPath());
        }
        return VANILLA_HIDDEN_NAMESPACES.contains(id.getNamespace());
    }

    private static CustomPacketPayload filterPayload(CustomPacketPayload payload, ClientSetting.HideMode mode) {
        CustomPacketPayload channelPayload = filterChannelPayload(payload, mode);
        if (channelPayload != payload) {
            return channelPayload;
        }
        return filterNeoForgeQueryPayload(payload);
    }

    private static CustomPacketPayload filterChannelPayload(CustomPacketPayload payload, ClientSetting.HideMode mode) {
        Collection<Identifier> channels = readChannels(payload);
        if (channels == null || channels.isEmpty()) {
            return payload;
        }

        List<Identifier> filteredChannels = channels.stream()
                .filter(channel -> !HIDDEN_NAMESPACES.contains(channel.getNamespace()))
                .toList();
        if (filteredChannels.size() == channels.size()) {
            return payload;
        }

        Identifier payloadId = payload.type().id();
        if (filteredChannels.isEmpty() && "minecraft".equals(payloadId.getNamespace()) && !"register".equals(payloadId.getPath())) {
            return null;
        }
        if (filteredChannels.isEmpty() && mode == ClientSetting.HideMode.Hide && "minecraft".equals(payloadId.getNamespace())) {
            return null;
        }

        return recreateChannelPayload(payload, filteredChannels);
    }

    @SuppressWarnings("unchecked")
    private static CustomPacketPayload filterNeoForgeQueryPayload(CustomPacketPayload payload) {
        if (!"neoforge:register".equals(payload.type().id().toString())) {
            return payload;
        }

        try {
            Method queriesMethod = payload.getClass().getMethod("queries");
            Map<Object, Set<Object>> queries = (Map<Object, Set<Object>>) queriesMethod.invoke(payload);
            Map<Object, Set<Object>> filteredQueries = new IdentityHashMap<>();
            boolean changed = false;

            for (Map.Entry<Object, Set<Object>> entry : queries.entrySet()) {
                Set<Object> filteredComponents = new HashSet<>();
                for (Object component : entry.getValue()) {
                    Method idMethod = component.getClass().getMethod("id");
                    Identifier componentId = (Identifier) idMethod.invoke(component);
                    if (HIDDEN_NAMESPACES.contains(componentId.getNamespace())) {
                        changed = true;
                    } else {
                        filteredComponents.add(component);
                    }
                }
                filteredQueries.put(entry.getKey(), filteredComponents);
            }

            if (!changed) {
                return payload;
            }

            Constructor<?> constructor = payload.getClass().getConstructor(Map.class);
            return (CustomPacketPayload) constructor.newInstance(filteredQueries);
        } catch (ReflectiveOperationException | ClassCastException exception) {
            Constants.LOGGER.warn("Failed to filter NeoForge network query payload.", exception);
            return payload;
        }
    }

    @SuppressWarnings("unchecked")
    private static Collection<Identifier> readChannels(CustomPacketPayload payload) {
        try {
            Method channelsMethod = payload.getClass().getMethod("channels");
            Object value = channelsMethod.invoke(payload);
            return value instanceof Collection<?> collection ? (Collection<Identifier>) collection : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static CustomPacketPayload recreateChannelPayload(CustomPacketPayload payload, List<Identifier> filteredChannels) {
        try {
            Object type = payload.type();
            Object version = readAccessor(payload, "version");
            Object protocol = readAccessor(payload, "protocol");

            for (Constructor<?> constructor : payload.getClass().getConstructors()) {
                Object[] args = createConstructorArguments(constructor.getParameterTypes(), type, version, protocol, filteredChannels);
                if (args == null) {
                    continue;
                }
                return (CustomPacketPayload) constructor.newInstance(args);
            }
        } catch (ReflectiveOperationException | ClassCastException exception) {
            Constants.LOGGER.warn("Failed to filter channel registration payload {}.", payload.type().id(), exception);
        }

        return payload;
    }

    private static Object readAccessor(CustomPacketPayload payload, String accessor) {
        try {
            return payload.getClass().getMethod(accessor).invoke(payload);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Object[] createConstructorArguments(Class<?>[] parameterTypes, Object type, Object version, Object protocol, List<Identifier> channels) {
        if (parameterTypes.length == 1 && isCollectionParameter(parameterTypes[0])) {
            return new Object[]{asCollection(parameterTypes[0], channels)};
        }
        if (parameterTypes.length == 2 && type != null && parameterTypes[0].isAssignableFrom(type.getClass()) && isCollectionParameter(parameterTypes[1])) {
            return new Object[]{type, asCollection(parameterTypes[1], channels)};
        }
        if (parameterTypes.length == 3 && version instanceof Integer && protocol != null && isCollectionParameter(parameterTypes[2])) {
            Object convertedProtocol = convertProtocol(protocol, parameterTypes[1]);
            if (convertedProtocol != null) {
                return new Object[]{version, convertedProtocol, asCollection(parameterTypes[2], channels)};
            }
        }
        return null;
    }

    private static boolean isCollectionParameter(Class<?> type) {
        return Collection.class.isAssignableFrom(type);
    }

    private static Collection<Identifier> asCollection(Class<?> type, List<Identifier> channels) {
        if (Set.class.isAssignableFrom(type)) {
            return new HashSet<>(channels);
        }
        return new ArrayList<>(channels);
    }

    private static Object convertProtocol(Object protocol, Class<?> targetType) {
        if (targetType.isAssignableFrom(protocol.getClass())) {
            return protocol;
        }
        if (targetType == String.class && protocol instanceof ConnectionProtocol connectionProtocol) {
            return connectionProtocol.id();
        }
        return null;
    }

}
