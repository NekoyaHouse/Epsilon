package com.github.epsilon.mixins;

import com.github.epsilon.Constants;
import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.events.impl.*;
import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.gui.screen.MainMenuScreen;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.managers.impl.sound.SoundKey;
import com.github.epsilon.modules.impl.ClientSetting;
import com.github.epsilon.modules.impl.player.MultiTask;
import com.github.epsilon.modules.impl.player.UseCooldown;
import com.github.epsilon.modules.impl.render.FreeCamera;
import com.github.epsilon.modules.impl.render.HandsView;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft {

    @Shadow
    private int rightClickDelay;

    @Shadow
    public ClientLevel level;

    @Shadow
    public abstract Entity getCameraEntity();

    @Shadow
    public abstract void pick(float partialTicks);

    @Shadow
    public abstract void stop();

    @Unique
    private boolean epsilon$freeCameraSet = false;

    @Unique
    private SoundInstance epsilon$shutdownSound;

    @Unique
    private boolean epsilon$shutdownReady;

    @Inject(method = "onGameLoadFinished", at = @At("TAIL"))
    private void onGameLoadFinished(CallbackInfo ci) {
        if (!ClientSetting.INSTANCE.showReisaOnStartup.getValue()) return;

        if (ClientSetting.INSTANCE.useMainMenu.getValue()) {
            MainMenuScreen.INSTANCE.queueReisaGreeting();
        } else {
            Managers.SOUND.playSound(
                    SoundKey.REISA_WELCOME,
                    ClientSetting.INSTANCE.reisaVolume.getValue().floatValue()
            );
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void onPreTick(CallbackInfo info) {
        EventBus.INSTANCE.post(new ClientTickEvent.Pre());
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void onPostTick(CallbackInfo info) {
        EventBus.INSTANCE.post(new ClientTickEvent.Post());

        if (epsilon$shutdownSound == null) return;

        Minecraft minecraft = (Minecraft) (Object) this;
        SoundManager soundManager = minecraft.getSoundManager();

        if (soundManager.isActive(epsilon$shutdownSound)) return;

        epsilon$shutdownSound = null;
        epsilon$shutdownReady = true;

        stop();
    }

    @ModifyArg(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/SoundManager;tick(Z)V"))
    private boolean tickShutdownSoundWhilePaused(boolean paused) {
        return epsilon$shutdownSound == null && paused;
    }

    @Inject(method = "stop", at = @At("HEAD"), cancellable = true)
    private void delayShutdown(CallbackInfo ci) {
        if (epsilon$shutdownReady) return;

        if (!ClientSetting.INSTANCE.showReisaOnShutdown.getValue()) {
            epsilon$shutdownReady = true;
            return;
        }

        Minecraft minecraft = (Minecraft) (Object) this;
        if (minecraft.screen == MainMenuScreen.INSTANCE) {
            if (MainMenuScreen.INSTANCE.requestShutdown()) {
                ci.cancel();
                return;
            }
            epsilon$shutdownReady = true;
            return;
        }

        if (epsilon$shutdownSound == null) {
            minecraft.getSoundManager().stop();
            epsilon$shutdownSound = Managers.SOUND.playTracked(
                    SoundKey.REISA_BYE,
                    ClientSetting.INSTANCE.reisaVolume.getValue().floatValue()
            ).orElse(null);
            if (epsilon$shutdownSound == null) {
                epsilon$shutdownReady = true;
                return;
            }
        }

        ci.cancel();
    }

    @ModifyArg(method = "updateTitle", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;setTitle(Ljava/lang/String;)V"))
    private String onUpdateTitle(String title) {
        return switch (ClientSetting.INSTANCE.customTitle.getValue()) {
            case Vanilla -> title;
            case Minecraft_1_8_9 -> "Minecraft 1.8.9";
            case Epsilon -> Constants.NAME + " " + Constants.VERSION + " for " + title;
        };
    }

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void onSetScreen(Screen screen, CallbackInfo ci) {
        OpenScreenEvent event = EventBus.INSTANCE.post(new OpenScreenEvent(screen));
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("HEAD"))
    private void onDisconnect(Screen screen, boolean keepResourcePacks, boolean stopSound, CallbackInfo ci) {
        if (level != null) {
            EventBus.INSTANCE.post(new GameLeftEvent());
        }
    }

    @Inject(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z", ordinal = 0, shift = At.Shift.BEFORE), cancellable = true)
    private void onHandleKeybinds(CallbackInfo ci) {
        RightClickEvent event = EventBus.INSTANCE.post(new RightClickEvent());
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "startUseItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/InteractionHand;values()[Lnet/minecraft/world/InteractionHand;"), cancellable = true)
    private void onStartUseItemBeforeHands(CallbackInfo ci) {
        StartUseItemEvent event = EventBus.INSTANCE.post(new StartUseItemEvent());
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "startUseItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;isItemEnabled(Lnet/minecraft/world/flag/FeatureFlagSet;)Z"))
    private void onStartUseItem(CallbackInfo ci) {
        UseCooldown useCooldown = UseCooldown.INSTANCE;
        if (useCooldown.isEnabled()) {
            rightClickDelay = useCooldown.cooldown.getValue();
        }
    }

    @WrapOperation(method = "continueAttack", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z"))
    private boolean attackMultiTask(LocalPlayer instance, Operation<Boolean> original) {
        return original.call(instance) && !MultiTask.INSTANCE.isEnabled();
    }

    @WrapOperation(method = "startUseItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;isDestroying()Z"))
    private boolean useMultiTask(MultiPlayerGameMode instance, Operation<Boolean> original) {
        return original.call(instance) && !MultiTask.INSTANCE.isEnabled();
    }

    @Inject(method = "handleKeybinds", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Options;keyUse:Lnet/minecraft/client/KeyMapping;", ordinal = 0, opcode = Opcodes.GETFIELD))
    private void onItemUseMouseHandle(CallbackInfo ci) {
        HandsView handsView = HandsView.INSTANCE;
        Minecraft mc = (Minecraft) (Object) this;
        if (handsView.isEnabled() && handsView.swingWhileUsing.getValue()
                && mc.options.keyAttack.isDown()
                && mc.options.keyAttack.consumeClick()
                && (!handsView.onlyOnBlock.getValue() || mc.hitResult.getType() == HitResult.Type.BLOCK)
        ) {
            mc.player.swing(InteractionHand.MAIN_HAND, false); // Use this method can swing client side.
        }
    }

    @Inject(method = "updateLevelInEngines(Lnet/minecraft/client/multiplayer/ClientLevel;Z)V", at = @At("HEAD"))
    private void onUpdateLevelInEngines(ClientLevel level, boolean stopSound, CallbackInfo ci) {
        EventBus.INSTANCE.post(new LevelUpdateEvent());
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void onClose(CallbackInfo ci) {
        LuminRenderSystem.destroyAll();
    }

    @Inject(method = "pick", at = @At("HEAD"), cancellable = true)
    private void updateTargetedEntityInvoke(float partialTicks, CallbackInfo ci) {
        FreeCamera freeCamera = FreeCamera.INSTANCE;

        if (freeCamera.isEnabled() && this.getCameraEntity() != null && !epsilon$freeCameraSet) {
            ci.cancel();
            Entity cameraEntity = this.getCameraEntity();

            double x = cameraEntity.getX();
            double y = cameraEntity.getY();
            double z = cameraEntity.getZ();
            double lastX = cameraEntity.xo;
            double lastY = cameraEntity.yo;
            double lastZ = cameraEntity.zo;
            float yaw = cameraEntity.getYRot();
            float pitch = cameraEntity.getXRot();
            float lastYaw = cameraEntity.yRotO;
            float lastPitch = cameraEntity.xRotO;

            cameraEntity.position().x = freeCamera.pos.x;
            cameraEntity.position().y = freeCamera.pos.y - cameraEntity.getEyeHeight(cameraEntity.getPose());
            cameraEntity.position().z = freeCamera.pos.z;
            cameraEntity.xo = freeCamera.prevPos.x;
            cameraEntity.yo = freeCamera.prevPos.y - cameraEntity.getEyeHeight(cameraEntity.getPose());
            cameraEntity.zo = freeCamera.prevPos.z;
            cameraEntity.setYRot(freeCamera.yaw);
            cameraEntity.setXRot(freeCamera.pitch);
            cameraEntity.yRotO = freeCamera.lastYaw;
            cameraEntity.xRotO = freeCamera.lastPitch;

            epsilon$freeCameraSet = true;
            pick(partialTicks);
            epsilon$freeCameraSet = false;

            cameraEntity.position().x = x;
            cameraEntity.position().y = y;
            cameraEntity.position().z = z;
            cameraEntity.xo = lastX;
            cameraEntity.yo = lastY;
            cameraEntity.zo = lastZ;
            cameraEntity.setYRot(yaw);
            cameraEntity.setXRot(pitch);
            cameraEntity.yRotO = lastYaw;
            cameraEntity.xRotO = lastPitch;
        }
    }

}
