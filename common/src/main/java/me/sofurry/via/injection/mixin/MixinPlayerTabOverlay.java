package me.sofurry.via.injection.mixin;

import com.github.epsilon.modules.impl.ClientSetting;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.sofurry.via.visuals.features.r1_7_tab_list_style.PlayerInfoIndexComparator;
import me.sofurry.via.visuals.injection.access.r1_7_tab_list_tyle.IPlayerTabOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Mixin(PlayerTabOverlay.class)
public abstract class MixinPlayerTabOverlay implements IPlayerTabOverlay {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Unique
    private static final Comparator<PlayerInfo> viaVersionVisuals$FIFO_COMPARATOR = PlayerInfoIndexComparator.INSTANCE;

    @Unique
    private int viaVersionVisuals$maxSlots;

    @Unique
    private boolean viaVersionVisuals$hideSkins;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void viaVersionVisuals$initFields(final CallbackInfo ci) {
        this.viaVersionVisuals$hideSkins = true;
    }

    @Inject(method = "getPlayerInfos", at = @At("HEAD"), cancellable = true)
    private void collectPlayerEntries(CallbackInfoReturnable<List<PlayerInfo>> result) {
        if (ClientSetting.INSTANCE.enableLegacyTablist.isEnabled()) {
            List<PlayerInfo> playerInfos = new ArrayList<>(this.minecraft.player.connection.getListedOnlinePlayers());
            playerInfos.sort(viaVersionVisuals$FIFO_COMPARATOR);
            if (playerInfos.size() > viaVersionVisuals$maxSlots) {
                playerInfos = new ArrayList<>(playerInfos.subList(0, viaVersionVisuals$maxSlots));
            }
            result.setReturnValue(this.viaVersionVisuals$transpose(playerInfos));
        } else {
            viaVersionVisuals$hideSkins = false;
        }
    }

    @ModifyExpressionValue(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;onlineMode()Z"))
    private boolean hideSkins(boolean original) {
        return original && !viaVersionVisuals$hideSkins;
    }

    @Unique
    private List<PlayerInfo> viaVersionVisuals$transpose(final List<PlayerInfo> list) {
        // Only bother transposing if we know the list is full
        if (list.size() != viaVersionVisuals$maxSlots) {
            viaVersionVisuals$hideSkins = true;
            for (final PlayerInfo playerInfo : list) {
                if (playerInfo.getProfile().properties().containsKey("textures")) {
                    viaVersionVisuals$hideSkins = false;
                    break;
                }
            }
            return list;
        }

        final List<PlayerInfo> result = new ArrayList<>(list.size());

        final int columns = viaVersionVisuals$maxSlots / PlayerTabOverlay.MAX_ROWS_PER_COL;
        boolean anyHasSkinData = false;
        for (int i = 0; i < viaVersionVisuals$maxSlots; i++) {
            final int row = i % PlayerTabOverlay.MAX_ROWS_PER_COL;
            final int col = i / PlayerTabOverlay.MAX_ROWS_PER_COL;
            final PlayerInfo current = list.get(row * columns + col);
            result.add(current);
            anyHasSkinData = anyHasSkinData || current.getProfile().properties().containsKey("textures");
        }
        viaVersionVisuals$hideSkins = !anyHasSkinData;
        return result;
    }

    @Override
    public void viaVersionVisuals$setMaxPlayers(int maxPlayers) {
        this.viaVersionVisuals$maxSlots = Math.clamp(((maxPlayers + PlayerTabOverlay.MAX_ROWS_PER_COL - 1) / PlayerTabOverlay.MAX_ROWS_PER_COL) * PlayerTabOverlay.MAX_ROWS_PER_COL, 20, 200);
    }

}
