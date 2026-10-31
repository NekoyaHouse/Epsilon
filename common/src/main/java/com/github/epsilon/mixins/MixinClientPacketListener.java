package com.github.epsilon.mixins;

import com.github.epsilon.events.impl.GameJoinedEvent;
import com.github.epsilon.events.impl.GameLeftEvent;
import com.github.epsilon.events.impl.RespawnEvent;
import com.github.epsilon.kernel.jvm.NonNullContext;
import com.github.epsilon.modules.impl.player.NoRotate;
import com.github.epsilon.modules.impl.render.NoRender;
import com.github.epsilon.modules.impl.render.SneakTweak;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.CommonListenerCookie;
import net.minecraft.core.particles.ExplosionParticleInfo;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(ClientPacketListener.class)
public abstract class MixinClientPacketListener extends ClientCommonPacketListenerImpl {

    protected MixinClientPacketListener(Minecraft minecraft, Connection connection, CommonListenerCookie commonListenerCookie) {
        super(minecraft, connection, commonListenerCookie);
    }

    @Inject(method = {"handleLogin", "handleRespawn"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V", shift = At.Shift.AFTER))
    private void onPlayerStateChange(CallbackInfo ci) {
        NonNullContext.reset();
    }

    @Inject(method = "handleLogin", at = @At("TAIL"))
    private void onHandleLoginTail(ClientboundLoginPacket packet, CallbackInfo ci, @Share("worldNotNull") LocalBooleanRef worldNotNull) {
        if (worldNotNull.get()) {
            NonNullContext.reset();
            new GameLeftEvent().post();
        }
        NonNullContext.update();
        new GameJoinedEvent().post();
    }

    @Inject(method = "handleConfigurationStart", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V", shift = At.Shift.AFTER))
    private void onHandleConfigurationStart(ClientboundStartConfigurationPacket packet, CallbackInfo ci) {
        NonNullContext.reset();
        new GameLeftEvent().post();
    }

    @Inject(method = "handleRespawn", at = @At("RETURN"))
    private void onHandleRespawnReturn(ClientboundRespawnPacket packet, CallbackInfo ci) {
        NonNullContext.update();
        new RespawnEvent().post();
    }

    @Inject(method = "handleSetEntityData", at = @At("HEAD"), cancellable = true)
    private void hookSneakTweakSetEntityData(ClientboundSetEntityDataPacket packet, CallbackInfo ci) {
        if (SneakTweak.INSTANCE.isEnabled()) {
            PacketUtils.ensureRunningOnSameThread(packet, (ClientPacketListener) (Object) this, minecraft.packetProcessor());
            if (minecraft.player == null || minecraft.level == null || packet.id() != minecraft.player.getId()) {
                return;
            }

            List<SynchedEntityData.DataValue<?>> filteredItems = new ArrayList<>(packet.packedItems().size());
            boolean removedPose = false;
            for (SynchedEntityData.DataValue<?> item : packet.packedItems()) {
                if (item.serializer() == EntityDataSerializers.POSE) {
                    removedPose = true;
                    continue;
                }

                filteredItems.add(item);
            }

            if (!removedPose) {
                return;
            }

            Entity entity = minecraft.level.getEntity(packet.id());
            if (entity != null && !filteredItems.isEmpty()) {
                entity.getEntityData().assignValues(filteredItems);
            }
            ci.cancel();
        }
    }

    @Inject(method = "handleMovePlayer", at = @At("HEAD"))
    private void onHandleMovePlayerHead(ClientboundPlayerPositionPacket packet, CallbackInfo ci, @Share("noRotateYaw") LocalFloatRef yawRef, @Share("noRotatePitch") LocalFloatRef pitchRef) {
        if (!NoRotate.INSTANCE.isEnabled() || minecraft.player == null) return;
        yawRef.set(minecraft.player.getYRot());
        pitchRef.set(minecraft.player.getXRot());
    }

    @Inject(method = "handleMovePlayer", at = @At("RETURN"))
    private void onHandleMovePlayerReturn(ClientboundPlayerPositionPacket packet, CallbackInfo ci, @Share("noRotateYaw") LocalFloatRef yawRef, @Share("noRotatePitch") LocalFloatRef pitchRef) {
        if (!NoRotate.INSTANCE.isEnabled() || minecraft.player == null) return;

        float savedYaw = yawRef.get();
        float savedPitch = pitchRef.get();

        // 这强制服务器更新，玩家不会注意到
        minecraft.player.setYRot(savedYaw + 0.000001f);
        minecraft.player.setXRot(savedPitch + 0.000001f);
        minecraft.player.yHeadRot = savedYaw;
        minecraft.player.yBodyRot = savedYaw;
    }

    @Inject(method = "handleAddEntity", at = @At("HEAD"), cancellable = true)
    private void onHandleAddEntity(net.minecraft.network.protocol.game.ClientboundAddEntityPacket packet, CallbackInfo ci) {
        if (packet != null && NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.noEntity(packet.getType()) && NoRender.INSTANCE.getDropSpawnPacket()) {
            ci.cancel();
        }
    }

    /**
     * 客户端所有爆炸粒子（爆炸云与方块碎屑）都由 {@code ClientboundExplodePacket} 触发，
     * 拦截这两个调用即可移除全部爆炸粒子，不影响烟花等其它来源的粒子。
     */
    @WrapOperation(method = "handleExplosion", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
    private void hookExplosionParticle(ClientLevel level, ParticleOptions particle, double x, double y, double z, double xa, double ya, double za, Operation<Void> original) {
        if (shouldHideExplosion()) {
            return;
        }
        original.call(level, particle, x, y, z, xa, ya, za);
    }

    /**
     * 拦截爆炸包附带的方块碎屑粒子。
     */
    @WrapOperation(method = "handleExplosion", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;trackExplosionEffects(Lnet/minecraft/world/phys/Vec3;FILnet/minecraft/util/random/WeightedList;)V"))
    private void hookExplosionBlockParticles(ClientLevel level, Vec3 center, float radius, int blockCount, WeightedList<ExplosionParticleInfo> blockParticles, Operation<Void> original) {
        if (shouldHideExplosion()) {
            return;
        }
        original.call(level, center, radius, blockCount, blockParticles);
    }

    private static boolean shouldHideExplosion() {
        return NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.getExplosions();
    }

}
