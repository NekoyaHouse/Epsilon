package com.github.epsilon.mixins;

import com.github.epsilon.Constants;
import com.github.epsilon.events.impl.ClientEntityEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.github.epsilon.kernel.MinecraftWrapper.mc;

@Mixin(ClientLevel.class)
public abstract class MixinClientLevel {

    @Inject(method = "tickEntities", at = @At("HEAD"))
    private void onTickEntities(CallbackInfo ci) {
        new ClientEntityEvent.TickPre((ClientLevel) (Object) this).post();
    }

    @Inject(method = "addEntity", at = @At("TAIL"))
    private void onEntityAdded(Entity entity, CallbackInfo ci) {
        ClientLevel level = (ClientLevel) (Object) this;
        if (level.getEntity(entity.getId()) == entity) {
            new ClientEntityEvent.Added(level, entity).post();
        }
    }

    @Inject(method = "removeEntity", at = @At("TAIL"))
    private void onEntityRemoved(int id, Entity.RemovalReason reason, CallbackInfo ci) {
        new ClientEntityEvent.Removed((ClientLevel) (Object) this, id).post();
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
