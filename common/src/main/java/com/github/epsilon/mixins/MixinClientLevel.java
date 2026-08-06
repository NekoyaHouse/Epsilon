package com.github.epsilon.mixins;

import com.github.epsilon.Constants;
import com.github.epsilon.modules.impl.render.NoRender;
import com.github.epsilon.modules.impl.render.maseffects.MasEffects;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import static com.github.epsilon.Constants.mc;

@Mixin(ClientLevel.class)
public abstract class MixinClientLevel {

    @Inject(method = "levelEvent", at = @At("HEAD"))
    private void onLevelEvent(Entity source, int type, BlockPos pos, int data, CallbackInfo ci) {
        MasEffects.INSTANCE.onLevelEvent(type, Vec3.atCenterOf(pos).add(0.0, 0.5, 0.0));
    }

    @ModifyArgs(method = "animateTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;doAnimateTick(IIIILnet/minecraft/util/RandomSource;Lnet/minecraft/world/level/block/Block;Lnet/minecraft/core/BlockPos$MutableBlockPos;)V"))
    private void onAnimateTick(Args args) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.barrierInvis.getValue()) {
            args.set(5, Blocks.BARRIER);
        }
    }

    @Redirect(method = "tickNonPassenger", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;tick()V"))
    public void hookTickNonPassenger(Entity instance) {
        if (Constants.skipTicks > 0 && instance == mc.player) {
            Constants.skipTicks--;
        } else {
            instance.tick();
        }
    }

}
