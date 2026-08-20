package me.sofurry.via.injection.mixin.features.execute_inputs_sync;

import com.github.epsilon.modules.impl.ClientSetting;
import me.sofurry.via.injection.access.execute_inputs_sync.IMouseKeyboardHandlers;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@Mixin(KeyboardHandler.class)
public abstract class MixinKeyboardHandler implements IMouseKeyboardHandlers {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Unique
    private final Queue<Runnable> viaVersion$pendingScreenEvents = new ConcurrentLinkedQueue<>();

    @Redirect(method = {"lambda$setup$0", "lambda$setup$2"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;execute(Ljava/lang/Runnable;)V"))
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
