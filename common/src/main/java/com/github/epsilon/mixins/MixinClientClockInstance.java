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

import static com.github.epsilon.Constants.mc;

/**
 * 修改客户端世界时钟时间。
 *
 * <p>26.3 移除了 {@code ClientClockManager#getTotalTicks(Holder)}，时钟值改由
 * {@code ClientClockManager.ClientClockInstance#totalTicks()} 提供。这里用当前维度默认时钟的实例
 * 做身份判断，保持 WorldTweaks 只修改当前维度默认时钟的行为。
 */
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
