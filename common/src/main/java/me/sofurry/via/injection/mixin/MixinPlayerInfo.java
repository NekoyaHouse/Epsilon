package me.sofurry.via.injection.mixin;

import me.sofurry.via.visuals.features.r1_7_tab_list_style.LegacyTabList;
import me.sofurry.via.visuals.injection.access.r1_7_tab_list_tyle.IPlayerInfo;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerInfo.class)
public abstract class MixinPlayerInfo implements IPlayerInfo {

    @Unique
    private int viaVersionVisuals$index;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void viaVersionVisuals$initFields(final CallbackInfo ci) {
        this.viaVersionVisuals$index = LegacyTabList.globalTablistIndex++;
    }

    @Override
    public int viaVersionVisuals$getIndex() {
        return viaVersionVisuals$index;
    }
}
