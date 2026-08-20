package me.sofurry.via.injection.mixin;

import com.github.epsilon.modules.impl.ClientSetting;
import me.sofurry.via.injection.access.execute_inputs_sync.IMouseKeyboardHandlers;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Queue;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft_FeaturesExecuteInputsSync {

    @Shadow
    @Final
    public MouseHandler mouseHandler;

    @Shadow
    @Final
    public KeyboardHandler keyboardHandler;

    @Inject(method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;screen()Lnet/minecraft/client/gui/screens/Screen;", ordinal = 0),
            slice = @Slice(
                    from = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;missTime:I", ordinal = 0, opcode = Opcodes.PUTFIELD),
                    to = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;fillReportDetails(Lnet/minecraft/CrashReport;)Lnet/minecraft/CrashReportCategory;")
            )
    )
    private void processInputQueues(CallbackInfo ci) {
        if (ClientSetting.INSTANCE.executeInputsSynchronously.isEnabled()) {
            Queue<Runnable> inputEvents = ((IMouseKeyboardHandlers) this.mouseHandler).viaVersion$getPendingScreenEvents();
            while (!inputEvents.isEmpty()) inputEvents.poll().run();

            inputEvents = ((IMouseKeyboardHandlers) this.keyboardHandler).viaVersion$getPendingScreenEvents();
            while (!inputEvents.isEmpty()) inputEvents.poll().run();
        }
    }

}
