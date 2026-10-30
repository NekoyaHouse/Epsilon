package com.github.epsilon.neoforge.mixins;

import com.github.epsilon.gui.overlay.DownloadJobOverlay;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Stack;

@Mixin(Gui.class)
public class MixinGui {

    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/client/ClientHooks;extractScreen(Lnet/minecraft/client/gui/screens/Screen;Ljava/util/Stack;Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
    private void extractDownloadJob(Screen screen, Stack<Screen> backgroundLayers, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, Operation<Void> original) {
        original.call(screen, backgroundLayers, graphics, mouseX, mouseY, delta);
        DownloadJobOverlay.INSTANCE.extractRenderState(screen, graphics, mouseX, mouseY);
    }

}
