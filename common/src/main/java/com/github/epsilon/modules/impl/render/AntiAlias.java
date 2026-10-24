package com.github.epsilon.modules.impl.render;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.AfterRender3DEvent;
import com.github.epsilon.events.impl.GameLeftEvent;
import com.github.epsilon.graphics.shaders.FXAAShader;
import com.github.epsilon.graphics.shaders.OverlayAntiAlias;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.EnumSetting;

public class AntiAlias extends Module {

    public static final AntiAlias INSTANCE = new AntiAlias();

    /**
     * 抗锯齿模式：FXAA 对整屏主渲染目标做后处理；SSAA 只对 {@code Render3DScheduler} 批处理的覆盖层做超采样。
     */
    public enum Mode {
        FXAA,
        SSAA
    }

    public enum Samples {
        X4(2), X16(4);

        private final int scale;

        Samples(int scale) {
            this.scale = scale;
        }
    }

    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.SSAA, this::onModeChanged);
    private final EnumSetting<Samples> samples = enumSetting("Samples", Samples.X4, () -> mode.is(Mode.SSAA));

    private AntiAlias() {
        super("Anti Alias", Category.RENDER);
    }

    /**
     * SSAA 叠加层入口：仅 SSAA 模式下重定向绘制目标，其余模式直接执行原绘制。
     */
    public void render(Runnable drawAction) {
        if (isEnabled() && mode.is(Mode.SSAA)) {
            OverlayAntiAlias.INSTANCE.render(samples.getValue().scale, drawAction);
        } else {
            drawAction.run();
        }
    }

    /**
     * FXAA 在 3D 渲染完成后处理整屏颜色目标，因此也会平滑覆盖层内容。
     */
    @EventHandler
    private void onAfterRender3D(AfterRender3DEvent event) {
        if (mode.is(Mode.FXAA)) {
            FXAAShader.INSTANCE.renderMainTarget();
        }
    }

    @Override
    protected void onDisable() {
        mc.execute(this::closeOffscreenTargets);
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        mc.execute(this::closeOffscreenTargets);
    }

    /**
     * 切换模式后释放另一种模式占用的离屏纹理；GPU 资源只在渲染线程释放，之后按需重建。
     */
    private void onModeChanged(Mode newMode) {
        mc.execute(() -> {
            if (newMode == Mode.SSAA) {
                FXAAShader.INSTANCE.close();
            } else {
                OverlayAntiAlias.INSTANCE.close();
            }
        });
    }

    private void closeOffscreenTargets() {
        OverlayAntiAlias.INSTANCE.close();
        FXAAShader.INSTANCE.close();
    }

}
