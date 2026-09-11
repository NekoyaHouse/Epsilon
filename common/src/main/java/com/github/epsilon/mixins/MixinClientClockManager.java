package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.WorldTweaks;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.ClientClockManager;
import net.minecraft.core.Holder;
import net.minecraft.world.clock.WorldClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientClockManager.class)
public class MixinClientClockManager {

    @ModifyReturnValue(method = "getTotalTicks", at = @At("RETURN"))
    private long modifyTotalTicks(long original, Holder<WorldClock> definition) {
        return WorldTweaks.INSTANCE.getModifiedClockTime(definition, original);
    }

}
