package com.github.epsilon.mixins;

import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.events.impl.GameJoinedEvent;
import com.github.epsilon.events.impl.GameLeftEvent;
import com.github.epsilon.events.impl.RespawnEvent;
import com.github.epsilon.modules.impl.player.NoRotate;
import com.github.epsilon.modules.impl.render.SneakTweak;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.CommonListenerCookie;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
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

    @Inject(method = "handleLogin", at = @At("TAIL"))
    private void onHandleLoginTail(ClientboundLoginPacket packet, CallbackInfo ci, @Share("worldNotNull") LocalBooleanRef worldNotNull) {
        if (worldNotNull.get()) {
            EventBus.INSTANCE.post(new GameLeftEvent());
        }
        EventBus.INSTANCE.post(new GameJoinedEvent());
    }

    // the server sends a GameJoin packet after the reconfiguration phase
    @Inject(method = "handleConfigurationStart", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V", shift = At.Shift.AFTER))
    private void onHandleConfigurationStart(ClientboundStartConfigurationPacket packet, CallbackInfo ci) {
        EventBus.INSTANCE.post(new GameLeftEvent());
    }

    @Inject(method = "handleRespawn", at = @At("RETURN"))
    private void onHandleRespawnReturn(ClientboundRespawnPacket packet, CallbackInfo ci) {
        EventBus.INSTANCE.post(new RespawnEvent());
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

}
