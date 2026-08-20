package me.sofurry.via.injection.mixin;

import me.sofurry.via.ViaVersionImpl;
import me.sofurry.via.api.events.LoadingCycleCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft_CoreIntegrationEvent {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void callPostGameLoadEvent(GameConfig gameConfig, CallbackInfo ci) {
        ViaVersionImpl.LOADING_CYCLE.invoker().onLoadCycle(LoadingCycleCallback.LoadingCycle.POST_GAME_LOAD);
    }

}
