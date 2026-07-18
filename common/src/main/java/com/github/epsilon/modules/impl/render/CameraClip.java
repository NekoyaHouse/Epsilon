package com.github.epsilon.modules.impl.render;

import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class CameraClip extends Module {

    public static final CameraClip INSTANCE = new CameraClip();

    private CameraClip() {
        super("Camera Clip", Category.RENDER);
    }

    public final DoubleSetting distance = doubleSetting("Distance", 3.5, 1.0, 20.0, 0.5);
    public final BoolSetting action = boolSetting("Action", true, _ -> resetCameraPos());
    public final DoubleSetting actionSmoothness = doubleSetting("Action Smoothness", 0.3, 0.1, 0.95, 0.01, action::getValue);
    public final DoubleSetting actionMaxDistance = doubleSetting("Action Max Distance", 20.0, 1.0, 50.0, 0.5, action::getValue);

    private Vec3 cameraPos;

    @Override
    protected void onEnable() {
        cameraPos = null;
    }

    @Override
    protected void onDisable() {
        cameraPos = null;
    }

    public void updateActionCamera(Vec3 targetPos) {
        if (cameraPos == null) {
            cameraPos = targetPos;
            return;
        }

        double distanceToTarget = cameraPos.distanceTo(targetPos);
        double maxDistance = actionMaxDistance.getValue();
        if (distanceToTarget > maxDistance) {
            cameraPos = targetPos;
            return;
        }

        double smoothFactor = actionSmoothness.getValue() * (1.0 - Math.exp(-distanceToTarget / maxDistance));

        cameraPos = new Vec3(
                Mth.lerp(smoothFactor, cameraPos.x, targetPos.x),
                Mth.lerp(smoothFactor, cameraPos.y, targetPos.y),
                Mth.lerp(smoothFactor, cameraPos.z, targetPos.z)
        );
    }

    public void resetCameraPos() {
        cameraPos = null;
    }

    public Vec3 getCameraPos() {
        return cameraPos;
    }

}
