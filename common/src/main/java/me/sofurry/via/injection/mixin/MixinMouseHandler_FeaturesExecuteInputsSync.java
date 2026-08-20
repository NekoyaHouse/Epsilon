package me.sofurry.via.injection.mixin;

import com.github.epsilon.modules.impl.ClientSetting;
import me.sofurry.via.injection.access.execute_inputs_sync.IMouseKeyboardHandlers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@Mixin(MouseHandler.class)
public abstract class MixinMouseHandler_FeaturesExecuteInputsSync implements IMouseKeyboardHandlers {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Unique
    private Queue<Runnable> viaVersion$pendingScreenEvents;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void viaVersion$initFields(final CallbackInfo ci) {
        this.viaVersion$pendingScreenEvents = new ConcurrentLinkedQueue<>();
    }

    @Redirect(method = {"lambda$setup$1", "lambda$setup$2"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;execute(Ljava/lang/Runnable;)V"))
    private void storeEvent(Minecraft instance, Runnable runnable) {
        if (this.minecraft.getConnection() != null && this.minecraft.gui.screen() != null && ClientSetting.INSTANCE.executeInputsSynchronously.isEnabled()) {
            this.viaVersion$pendingScreenEvents.offer(runnable);
        } else {
            instance.execute(runnable);
        }
    }

    @Override
    public Queue<Runnable> viaVersion$getPendingScreenEvents() {
        return this.viaVersion$pendingScreenEvents;
    }

}
