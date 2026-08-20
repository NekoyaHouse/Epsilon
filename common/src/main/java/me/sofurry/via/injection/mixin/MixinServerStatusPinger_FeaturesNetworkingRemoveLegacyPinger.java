package me.sofurry.via.injection.mixin;

import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.server.network.EventLoopGroupHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.net.InetSocketAddress;

@Mixin(ServerStatusPinger.class)
public abstract class MixinServerStatusPinger_FeaturesNetworkingRemoveLegacyPinger {

    /**
     * @author RK_01
     * @reason Remove legacy ping which didn't even work
     */
    @Overwrite
    public void pingLegacyServer(InetSocketAddress inetSocketAddress, final ServerAddress serverAddress, final ServerData serverData, EventLoopGroupHolder eventLoopGroupHolder) {
    }

}
