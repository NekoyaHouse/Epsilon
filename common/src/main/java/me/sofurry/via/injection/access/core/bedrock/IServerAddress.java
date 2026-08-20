package me.sofurry.via.injection.access.core.bedrock;

import dev.kastle.netty.channel.nethernet.config.NetherNetAddress;

public interface IServerAddress {

    NetherNetAddress viaVersion$getNetherNetAddress();

    void viaVersion$setNetherNetAddress(final NetherNetAddress address);

}
