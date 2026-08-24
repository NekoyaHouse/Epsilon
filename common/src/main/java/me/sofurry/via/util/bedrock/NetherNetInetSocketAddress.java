package me.sofurry.via.util.bedrock;

import dev.kastle.netty.channel.nethernet.config.NetherNetAddress;

import java.net.InetSocketAddress;

public class NetherNetInetSocketAddress extends InetSocketAddress {

    private final NetherNetAddress netherNetAddress;

    public NetherNetInetSocketAddress(final NetherNetAddress netherNetAddress) {
        super(netherNetAddress.getNetworkId() + ".nethernet.viafabricplus.localhost", 0);
        this.netherNetAddress = netherNetAddress;
    }

    public NetherNetAddress getNetherNetAddress() {
        return this.netherNetAddress;
    }

}
