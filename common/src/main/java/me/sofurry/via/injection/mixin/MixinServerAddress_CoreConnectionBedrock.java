package me.sofurry.via.injection.mixin;

import dev.kastle.netty.channel.nethernet.config.NetherNetAddress;
import me.sofurry.via.injection.access.core.bedrock.IServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ServerAddress.class)
public abstract class MixinServerAddress_CoreConnectionBedrock implements IServerAddress {

    @Unique
    private NetherNetAddress viaVersion$netherNetAddress;

    @Override
    public NetherNetAddress viaVersion$getNetherNetAddress() {
        return this.viaVersion$netherNetAddress;
    }

    @Override
    public void viaVersion$setNetherNetAddress(final NetherNetAddress address) {
        this.viaVersion$netherNetAddress = address;
    }

}
