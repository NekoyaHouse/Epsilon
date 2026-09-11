package com.github.epsilon.modules.impl.render;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.bus.EventPriority;
import com.github.epsilon.events.impl.AfterRender3DEvent;
import com.github.epsilon.events.impl.GameJoinedEvent;
import com.github.epsilon.events.impl.GameLeftEvent;
import com.github.epsilon.events.impl.LevelUpdateEvent;
import com.github.epsilon.graphics.shaders.MotionBlurShader;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.settings.impl.IntSetting;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

public class MotionBlur extends Module {

    public static final MotionBlur INSTANCE = new MotionBlur();

    private MotionBlur() {
        super("Motion Blur", Category.RENDER);
    }

    public enum Algorithm {
        Backwards,
        Centered
    }

    private final DoubleSetting strength = doubleSetting("Strength", 1.0, 0.0, 5.0, 0.05);
    private final IntSetting samples = intSetting("Samples", 100, 4, 256, 4);
    private final EnumSetting<Algorithm> algorithm = enumSetting("Algorithm", Algorithm.Centered);
    private final BoolSetting depthBlur = boolSetting("Depth Blur", true);
    private final BoolSetting renderThirdPerson = boolSetting("Third Person", true);
    private final BoolSetting refreshRateScaling = boolSetting("Refresh Rate Scaling", true);

    private final Matrix4f currentView = new Matrix4f();
    private final Matrix4f currentProjection = new Matrix4f();
    private Vec3 currentCameraPosition = Vec3.ZERO;
    private boolean frameCaptured;

    public void captureFrame(CameraRenderState cameraState, Matrix4fc modelViewMatrix) {
        if (!isEnabled()) return;
        currentView.set(modelViewMatrix);
        currentProjection.set(cameraState.projectionMatrix);
        currentCameraPosition = cameraState.pos;
        frameCaptured = true;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onAfterRender3D(AfterRender3DEvent event) {
        if (!frameCaptured) return;
        frameCaptured = false;

        boolean cameraAllowed = mc.options.getCameraType().isFirstPerson() || renderThirdPerson.getValue();
        MotionBlurShader.INSTANCE.render(
                mc.gameRenderer.mainRenderTarget(),
                currentView,
                currentProjection,
                currentCameraPosition,
                new MotionBlurShader.Settings(
                        strength.getValue().floatValue(),
                        samples.getValue(),
                        algorithm.getValue().ordinal(),
                        depthBlur.getValue(),
                        refreshRateScaling.getValue(),
                        cameraAllowed
                )
        );
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        resetHistory();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        resetHistory();
    }

    @EventHandler
    private void onLevelUpdate(LevelUpdateEvent event) {
        resetHistory();
    }

    @Override
    protected void onEnable() {
        resetHistory();
    }

    @Override
    protected void onDisable() {
        resetHistory();
    }

    private void resetHistory() {
        frameCaptured = false;
        MotionBlurShader.INSTANCE.resetHistory();
    }

}
