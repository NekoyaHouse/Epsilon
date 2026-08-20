package me.sofurry.via.injection.mixin.features.networking.level_loading;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.injection.access.networking.downloading_terrain.ILevelLoadingScreen;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.CommonListenerCookie;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class MixinClientPacketListener extends ClientCommonPacketListenerImpl {

    protected MixinClientPacketListener(Minecraft client, Connection connection, CommonListenerCookie connectionState) {
        super(client, connection, connectionState);
    }

    @Inject(method = "handleSetSpawn", at = @At("RETURN"))
    private void moveDownloadingTerrainClosing(ClientboundSetDefaultSpawnPositionPacket packet, CallbackInfo ci) {
        if (ProtocolTranslator.getTargetVersion().betweenInclusive(ProtocolVersion.v1_18_2, ProtocolVersion.v1_20_2) && this.minecraft.gui.screen() instanceof ILevelLoadingScreen mixinDownloadingTerrainScreen) {
            mixinDownloadingTerrainScreen.viaVersion$setReady();
        }
    }

    @Inject(method = "handleMovePlayer", at = @At("RETURN"))
    private void closeDownloadingTerrain(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_18) && this.minecraft.gui.screen() instanceof ILevelLoadingScreen mixinDownloadingTerrainScreen) {
            mixinDownloadingTerrainScreen.viaVersion$setReady();
        }
    }

}
