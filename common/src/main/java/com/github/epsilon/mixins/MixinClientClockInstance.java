package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.WorldTweaks;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.ClientClockManager;
import net.minecraft.core.Holder;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;

import static com.github.epsilon.kernel.MinecraftWrapper.mc;

@Mixin(ClientClockManager.ClientClockInstance.class)
public class MixinClientClockInstance {

    @ModifyReturnValue(method = "totalTicks", at = @At("RETURN"))
    private long modifyTotalTicks(long original) {
        Level level = mc.level;
        if (level == null) {
            return original;
        }

        Optional<? extends Holder<WorldClock>> defaultClock = level.dimensionType().defaultClock();
        if (defaultClock.isEmpty()) {
            return original;
        }

        Holder<WorldClock> clock = defaultClock.get();
        if (level.clockManager().getInstance(clock) != this) {
            return original;
        }
        return WorldTweaks.INSTANCE.getModifiedClockTime(clock, original);
    }

}
