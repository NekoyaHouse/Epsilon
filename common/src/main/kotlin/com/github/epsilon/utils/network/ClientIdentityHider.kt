package com.github.epsilon.utils.network

import com.github.epsilon.Constants
import com.github.epsilon.modules.impl.ClientSetting
import net.minecraft.network.ConnectionProtocol
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket
import net.minecraft.network.protocol.common.custom.BrandPayload
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.util.*

object ClientIdentityHider {

    private const val VANILLA_BRAND = "vanilla"
    private val HIDDEN_NAMESPACES: Set<String> = setOf(Constants.MOD_ID)
    private val VANILLA_HIDDEN_NAMESPACES: Set<String> = setOf("c", "neoforge", "fabric", "fabric-networking-api-v1")
    private val VANILLA_HIDDEN_MINECRAFT_PAYLOADS: Set<String> = setOf("register", "unregister")

    /**
     * 按客户端身份隐藏模式过滤即将发送的自定义负载包。
     *
     * @param packet 待发送或过滤的网络包
     * @return 原包、重写后的包，或表示丢弃该包的 null
     */
    fun filterServerboundPacket(packet: Packet<*>?): Packet<*>? {
        val customPayloadPacket = packet as? ServerboundCustomPayloadPacket ?: return packet

        val mode = ClientSetting.hideMode.value
        if (mode == ClientSetting.HideMode.None) {
            return packet
        }

        val payload = customPayloadPacket.payload()
        if (payload is BrandPayload) {
            return if (mode == ClientSetting.HideMode.Vanilla) {
                ServerboundCustomPayloadPacket(BrandPayload(VANILLA_BRAND))
            } else {
                packet
            }
        }

        val id = payload.type().id()
        if (mode == ClientSetting.HideMode.Vanilla && shouldHideAsVanilla(id)) {
            return null
        }

        val filteredPayload = filterPayload(payload, mode) ?: return null
        if (filteredPayload === payload) {
            return packet
        }
        return ServerboundCustomPayloadPacket(filteredPayload)
    }

    /**
     * 按客户端身份隐藏模式过滤客户端品牌字符串。
     *
     * @param brand 原始客户端品牌字符串
     * @return 操作结果
     */
    fun filterClientBrand(brand: String): String {
        return if (ClientSetting.hideMode.`is`(ClientSetting.HideMode.Vanilla)) VANILLA_BRAND else brand
    }

    private fun shouldHideAsVanilla(id: Identifier): Boolean {
        if ("minecraft" == id.namespace) {
            return VANILLA_HIDDEN_MINECRAFT_PAYLOADS.contains(id.path)
        }
        return VANILLA_HIDDEN_NAMESPACES.contains(id.namespace)
    }

    private fun filterPayload(payload: CustomPacketPayload, mode: ClientSetting.HideMode): CustomPacketPayload? {
        val channelPayload = filterChannelPayload(payload, mode)
        if (channelPayload !== payload) {
            return channelPayload
        }
        return filterNeoForgeQueryPayload(payload)
    }

    private fun filterChannelPayload(payload: CustomPacketPayload, mode: ClientSetting.HideMode): CustomPacketPayload? {
        val channels = readChannels(payload)
        if (channels == null || channels.isEmpty()) {
            return payload
        }

        val filteredChannels = channels.filter { channel -> !HIDDEN_NAMESPACES.contains(channel.namespace) }
        if (filteredChannels.size == channels.size) {
            return payload
        }

        val payloadId = payload.type().id()
        if (filteredChannels.isEmpty() && "minecraft" == payloadId.namespace && "register" != payloadId.path) {
            return null
        }
        if (filteredChannels.isEmpty() && mode == ClientSetting.HideMode.Hide && "minecraft" == payloadId.namespace) {
            return null
        }

        return recreateChannelPayload(payload, filteredChannels)
    }

    @Suppress("UNCHECKED_CAST")
    private fun filterNeoForgeQueryPayload(payload: CustomPacketPayload): CustomPacketPayload {
        if ("neoforge:register" != payload.type().id().toString()) {
            return payload
        }

        try {
            val queriesMethod: Method = payload.javaClass.getMethod("queries")
            val queries = queriesMethod.invoke(payload) as Map<Any, Set<Any>>
            val filteredQueries = IdentityHashMap<Any, Set<Any>>()
            var changed = false

            for (entry in queries.entries) {
                val filteredComponents = HashSet<Any>()
                for (component in entry.value) {
                    val idMethod: Method = component.javaClass.getMethod("id")
                    val componentId = idMethod.invoke(component) as Identifier
                    if (HIDDEN_NAMESPACES.contains(componentId.namespace)) {
                        changed = true
                    } else {
                        filteredComponents.add(component)
                    }
                }
                filteredQueries.put(entry.key, filteredComponents)
            }

            if (!changed) {
                return payload
            }

            val constructor: Constructor<*> = payload.javaClass.getConstructor(Map::class.java)
            return constructor.newInstance(filteredQueries) as CustomPacketPayload
        } catch (exception: ReflectiveOperationException) {
            Constants.LOGGER.warn("Failed to filter NeoForge network query payload.", exception)
            return payload
        } catch (exception: ClassCastException) {
            Constants.LOGGER.warn("Failed to filter NeoForge network query payload.", exception)
            return payload
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun readChannels(payload: CustomPacketPayload): Collection<Identifier>? {
        try {
            val channelsMethod: Method = payload.javaClass.getMethod("channels")
            val value = channelsMethod.invoke(payload)
            return if (value is Collection<*>) value as Collection<Identifier> else null
        } catch (ignored: ReflectiveOperationException) {
            return null
        }
    }

    private fun recreateChannelPayload(
        payload: CustomPacketPayload,
        filteredChannels: List<Identifier>
    ): CustomPacketPayload {
        try {
            val type: Any = payload.type()
            val version = readAccessor(payload, "version")
            val protocol = readAccessor(payload, "protocol")

            for (constructor in payload.javaClass.constructors) {
                val args =
                    createConstructorArguments(constructor.parameterTypes, type, version, protocol, filteredChannels)
                        ?: continue
                return constructor.newInstance(*args) as CustomPacketPayload
            }
        } catch (exception: ReflectiveOperationException) {
            Constants.LOGGER.warn("Failed to filter channel registration payload {}.", payload.type().id(), exception)
        } catch (exception: ClassCastException) {
            Constants.LOGGER.warn("Failed to filter channel registration payload {}.", payload.type().id(), exception)
        }

        return payload
    }

    private fun readAccessor(payload: CustomPacketPayload, accessor: String): Any? {
        try {
            return payload.javaClass.getMethod(accessor).invoke(payload)
        } catch (ignored: ReflectiveOperationException) {
            return null
        }
    }

    private fun createConstructorArguments(
        parameterTypes: Array<Class<*>>,
        type: Any?,
        version: Any?,
        protocol: Any?,
        channels: List<Identifier>
    ): Array<Any?>? {
        if (parameterTypes.size == 1 && isCollectionParameter(parameterTypes[0])) {
            return arrayOf<Any?>(asCollection(parameterTypes[0], channels))
        }
        if (parameterTypes.size == 2 && type != null && parameterTypes[0].isAssignableFrom(type.javaClass) &&
            isCollectionParameter(parameterTypes[1])
        ) {
            return arrayOf<Any?>(type, asCollection(parameterTypes[1], channels))
        }
        if (parameterTypes.size == 3 && version is Int && protocol != null && isCollectionParameter(parameterTypes[2])) {
            val convertedProtocol = convertProtocol(protocol, parameterTypes[1])
            if (convertedProtocol != null) {
                return arrayOf<Any?>(version, convertedProtocol, asCollection(parameterTypes[2], channels))
            }
        }
        return null
    }

    private fun isCollectionParameter(type: Class<*>): Boolean {
        return Collection::class.java.isAssignableFrom(type)
    }

    private fun asCollection(type: Class<*>, channels: List<Identifier>): Collection<Identifier> {
        if (Set::class.java.isAssignableFrom(type)) {
            return HashSet(channels)
        }
        return ArrayList(channels)
    }

    private fun convertProtocol(protocol: Any, targetType: Class<*>): Any? {
        if (targetType.isAssignableFrom(protocol.javaClass)) {
            return protocol
        }
        if (targetType == String::class.java && protocol is ConnectionProtocol) {
            return protocol.id()
        }
        return null
    }

}
