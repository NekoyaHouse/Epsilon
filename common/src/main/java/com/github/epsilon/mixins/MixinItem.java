package com.github.epsilon.mixins;

import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.events.impl.UseItemRaytraceEvent;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Item.class)
public class MixinItem {

    @ModifyExpressionValue(method = "getPlayerPOVHitResult", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;calculateViewVector(FF)Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 hookUseItemRayTrace(Vec3 original, Level level, Player player, ClipContext.Fluid fluid) {
        UseItemRaytraceEvent event = EventBus.INSTANCE.post(new UseItemRaytraceEvent(player.getYRot(), player.getXRot()));
        return player.calculateViewVector(event.getPitch(), event.getYaw());
    }

}
