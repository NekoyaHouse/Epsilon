package me.sofurry.via.injection.mixin;

import me.sofurry.via.features.block.connections.BlockConnectionsEmulation1_12_2;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class MixinClientChunkCache {

    @Shadow
    private ClientLevel level;

    @Inject(method = "updateLevelChunk", at = @At("TAIL"))
    private void updateBlockConnections(int x, int z, ClientboundLevelChunkPacketData chunkData, CallbackInfo ci) {
        BlockConnectionsEmulation1_12_2.updateChunkNeighborConnections(this.level, x, z);
    }

    @Inject(method = "handleBlockUpdate", at = @At("TAIL"))
    private void updateBlockConnections(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
        BlockConnectionsEmulation1_12_2.updateChunkNeighborConnections(this.level, packet.getPos());
    }

    @Inject(method = "handleBlockEvent", at = @At("TAIL"))
    private void updateBlockConnections(ClientboundBlockEventPacket packet, CallbackInfo ci) {
        BlockConnectionsEmulation1_12_2.updateChunkNeighborConnections(this.level, packet.getPos());
    }

    @Inject(method = "handleBlockDestruction", at = @At("TAIL"))
    private void updateBlockConnections(ClientboundBlockDestructionPacket packet, CallbackInfo ci) {
        BlockConnectionsEmulation1_12_2.updateChunkNeighborConnections(this.level, packet.getPos());
    }

}
