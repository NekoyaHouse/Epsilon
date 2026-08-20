package me.sofurry.via.util.bedrock;

import dev.kastle.netty.channel.nethernet.config.NetherNetAddress;
import net.minecraft.client.multiplayer.resolver.ResolvedServerAddress;

import java.net.InetSocketAddress;

public final class NetherNetResolvedServerAddress implements ResolvedServerAddress {

    private final NetherNetAddress netherNetAddress;

    public NetherNetResolvedServerAddress(final NetherNetAddress netherNetAddress) {
        this.netherNetAddress = netherNetAddress;
    }

    @Override
    public String getHostName() {
        return this.netherNetAddress.getNetworkId();
    }

    @Override
    public String getHostIp() {
        return this.netherNetAddress.getNetworkId();
    }

    @Override
    public int getPort() {
        return 0;
    }

    @Override
    public InetSocketAddress asInetSocketAddress() {
        return new NetherNetInetSocketAddress(this.netherNetAddress);
    }

}
