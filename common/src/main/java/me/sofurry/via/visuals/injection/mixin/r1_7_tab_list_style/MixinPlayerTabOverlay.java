package me.sofurry.via.visuals.injection.mixin.r1_7_tab_list_style;

import com.github.epsilon.modules.impl.ClientSetting;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.sofurry.via.visuals.injection.access.r1_7_tab_list_tyle.IPlayerInfo;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Mixin(PlayerTabOverlay.class)
public abstract class MixinPlayerTabOverlay implements IPlayerTabOverlay {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Unique
    private static final Comparator<PlayerInfo> viaVersionVisuals$FIFO_COMPARATOR = Comparator.comparingInt(e -> ((IPlayerInfo) e).viaVersionVisuals$getIndex());

    @Unique
    private int viaVersionVisuals$maxSlots;

    @Unique
    private boolean viaVersionVisuals$hideSkins = true;

    @Inject(method = "getPlayerInfos", at = @At("HEAD"), cancellable = true)
    private void collectPlayerEntries(CallbackInfoReturnable<List<PlayerInfo>> result) {
        if (ClientSetting.INSTANCE.enableLegacyTablist.isEnabled()) {
            result.setReturnValue(this.minecraft.player.connection.getListedOnlinePlayers().stream()
                    .sorted(viaVersionVisuals$FIFO_COMPARATOR)
                    .limit(viaVersionVisuals$maxSlots)
                    .collect(Collectors.collectingAndThen(Collectors.toList(), this::viaVersionVisuals$transpose)));
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
            viaVersionVisuals$hideSkins = list.stream().noneMatch(e -> e.getProfile().properties().containsKey("textures"));
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
