package com.github.epsilon.mixins;

import com.github.epsilon.Constants;
import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.events.impl.*;
import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.video.VideoPlayer;
import com.github.epsilon.gui.screen.MainMenuScreen;
import com.github.epsilon.managers.sound.SoundKey;
import com.github.epsilon.managers.sound.SoundManager;
import com.github.epsilon.modules.impl.ClientSetting;
import com.github.epsilon.modules.impl.player.MultiTask;
import com.github.epsilon.modules.impl.player.UseCooldown;
import com.github.epsilon.modules.impl.render.FreeCamera;
import com.github.epsilon.modules.impl.render.HandView;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.sofurry.smtc.SmtcService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
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

    @Unique
    private boolean epsilon$freeCameraSet = false;

    @Inject(method = "onGameLoadFinished", at = @At("TAIL"))
    private void onGameLoadFinished(CallbackInfo ci) {
        if (!ClientSetting.INSTANCE.showReisaOnStartup.getValue()) return;

        if (ClientSetting.INSTANCE.useMainMenu.getValue()) {
            MainMenuScreen.INSTANCE.queueReisaGreeting();
        } else {
            SoundManager.INSTANCE.playSound(
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
    }

    @ModifyArg(method = "updateTitle", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;setTitle(Ljava/lang/String;)V"))
    private String onUpdateTitle(String title) {
        ClientSetting.TitleMode mode = ClientSetting.INSTANCE.customTitle.getValue();
        if (mode == ClientSetting.TitleMode.Vanilla) {
            return title;
        } else if (mode == ClientSetting.TitleMode.Minecraft_1_8_9) {
            return "Minecraft 1.8.9";
        } else if (mode == ClientSetting.TitleMode.Endfield) {
            return "Endfield";
        } else {
            return Constants.NAME + " " + Constants.VERSION + " for " + title;
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
        return instance == null || (!MultiTask.INSTANCE.isEnabled() && original.call(instance));
    }

    @WrapOperation(method = "startUseItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;isDestroying()Z"))
    private boolean useMultiTask(MultiPlayerGameMode instance, Operation<Boolean> original) {
        return !MultiTask.INSTANCE.isEnabled() && original.call(instance);
    }

    @Inject(method = "handleKeybinds", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Options;keyUse:Lnet/minecraft/client/KeyMapping;", ordinal = 0, opcode = Opcodes.GETFIELD))
    private void onItemUseMouseHandle(CallbackInfo ci) {
        HandView handView = HandView.INSTANCE;
        Minecraft mc = (Minecraft) (Object) this;
        if (handView.isEnabled() && handView.swingWhileUsing.getValue()
                && mc.options.keyAttack.isDown()
                && mc.options.keyAttack.consumeClick()
                && (!handView.onlyOnBlock.getValue() || mc.hitResult.getType() == HitResult.Type.BLOCK)
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
        SmtcService.INSTANCE.stop();
        VideoPlayer.stop();
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
