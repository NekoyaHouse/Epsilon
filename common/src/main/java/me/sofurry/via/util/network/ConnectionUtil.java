package me.sofurry.via.util.network;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import dev.kastle.netty.channel.nethernet.config.NetherNetAddress;
import me.sofurry.via.injection.access.core.IServerData;
import me.sofurry.via.injection.access.core.bedrock.IServerAddress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;

public final class ConnectionUtil {

    public static void connect(final String address, final ProtocolVersion version) {
        connect(address, address, version);
    }

    public static void connect(final String name, final String address) {
        connect(name, address, null);
    }

    public static void connect(final String name, final String address, final ProtocolVersion version) {
        final ServerAddress serverAddress = ServerAddress.parseString(address);
        final ServerData entry = new ServerData(name, serverAddress.getHost(), ServerData.Type.OTHER);

        if (version != null) {
            ((IServerData) entry).viaVersion$forceVersion(version);
        }
        ConnectScreen.startConnecting(Minecraft.getInstance().gui.screen(), Minecraft.getInstance(), serverAddress, entry, false, null);
    }

    public static void connectNetherNet(final NetherNetAddress address) {
        final ServerAddress serverAddress = ServerAddress.parseString(address.getNetworkId() + ".nethernet.viafabricplus.localhost");
        ((IServerAddress) (Object) serverAddress).viaVersion$setNetherNetAddress(address);

        final ServerData entry = new ServerData("Bedrock Realm " + address.getNetworkId(), serverAddress.getHost(), ServerData.Type.OTHER);
        ((IServerData) entry).viaVersion$forceVersion(BedrockProtocolVersion.bedrockLatest);

        ConnectScreen.startConnecting(Minecraft.getInstance().gui.screen(), Minecraft.getInstance(), serverAddress, entry, false, null);
    }

}
