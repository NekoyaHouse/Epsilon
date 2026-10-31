package com.github.epsilon.mixins;

import com.github.epsilon.events.impl.Render2DEvent;
import com.github.epsilon.gui.hudeditor.HudEditorScreen;
import com.github.epsilon.gui.troll.TrollGuiScreen;
import com.github.epsilon.utils.render.EpsilonGuiRenderer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.github.epsilon.kernel.MinecraftWrapper.mc;

@Mixin(GuiRenderer.class)
public class MixinGuiRenderer {

    @Shadow
    @Final
    private FeatureRenderDispatcher featureRenderDispatcher;

    @Unique
    private GuiRenderState epsilon$levelRenderState;

    @Unique
    private EpsilonGuiRenderer epsilon$levelGuiRenderer;

    @Unique
    private GuiRenderState epsilon$renderState;

    @Unique
    private EpsilonGuiRenderer epsilon$guiRenderer;

    @WrapOperation(method = "draw", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;processBlurEffect()V"))
    private void onDraw(GameRenderer renderer, Operation<Void> original) {
        if (((GuiRenderer) (Object) this).getClass() == GuiRenderer.class && mc != null && mc.gui.screen() instanceof TrollGuiScreen screen && screen.renderPendingFrame()) {
            return;
        }
        original.call(renderer);
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void onRenderHead(CallbackInfo ci) {
        // 只在原版主 GuiRenderer 上运行，避免被 MeteorClient 继承的自定义 GuiRenderer 重复触发
        if (((GuiRenderer) (Object) this).getClass() != GuiRenderer.class) {
            return;
        }

        // 26.3 会在 Minecraft 构造期间渲染首帧，此时 Minecraft 尚未初始化，直接跳过。
        if (mc == null) {
            return;
        }

        if (epsilon$levelRenderState == null || epsilon$levelGuiRenderer == null) {
            this.epsilon$levelRenderState = new GuiRenderState();
            this.epsilon$levelGuiRenderer = new EpsilonGuiRenderer(
                    this.epsilon$levelRenderState,
                    this.featureRenderDispatcher
            );
        }
        if (epsilon$renderState == null || epsilon$guiRenderer == null) {
            this.epsilon$renderState = new GuiRenderState();
            this.epsilon$guiRenderer = new EpsilonGuiRenderer(
                    this.epsilon$renderState,
                    this.featureRenderDispatcher
            );
        }

        int mouseX = (int) mc.mouseHandler.getScaledXPos(mc.getWindow());
        int mouseY = (int) mc.mouseHandler.getScaledYPos(mc.getWindow());

        HudEditorScreen.INSTANCE.renderPendingHudElements();

        GuiGraphicsExtractor levelGuiGraphics = new GuiGraphicsExtractor(mc, epsilon$levelRenderState, mouseX, mouseY);
        new Render2DEvent.Level(levelGuiGraphics).post();
        epsilon$levelGuiRenderer.render();
        epsilon$levelGuiRenderer.endFrame();

        GuiGraphicsExtractor guiGraphics = new GuiGraphicsExtractor(mc, epsilon$renderState, mouseX, mouseY);
        new Render2DEvent.HUD(guiGraphics).post();

        epsilon$guiRenderer.render();

        epsilon$guiRenderer.endFrame();
    }

}
