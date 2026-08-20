package me.sofurry.via.injection.mixin;

import me.sofurry.via.visuals.features.r1_7_tab_list_style.LegacyTabList;
import me.sofurry.via.visuals.injection.access.r1_7_tab_list_tyle.IPlayerTabOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class MixinClientPacketListener_VisualsR17TabListStyle {

    @Inject(method = "handleLogin", at = @At("RETURN"))
    private void initPlayerListFix(ClientboundLoginPacket packet, CallbackInfo ci) {
        LegacyTabList.globalTablistIndex = 0;
        ((IPlayerTabOverlay) Minecraft.getInstance().gui.hud.getTabList()).viaVersionVisuals$setMaxPlayers(packet.maxPlayers());
    }

}
