package com.github.epsilon.modules.impl.movement;

import com.github.epsilon.events.impl.KeyboardInputEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import net.minecraft.util.Mth;

public class MovementFix extends Module {

    public static final MovementFix INSTANCE = new MovementFix();

    private MovementFix() {
        super("Movement Fix", Category.MOVEMENT);
        setDefaultEnabled(true);
    }

    private float getDirection(float forward, float strafe) {
        float direction = mc.player.getYRot();

        boolean isMovingForward = forward > 0.0f;
        boolean isMovingBack = forward < 0.0f;
        boolean isMovingRight = strafe > 0.0f;
        boolean isMovingLeft = strafe < 0.0f;
        boolean isMovingSideways = isMovingRight || isMovingLeft;
        boolean isMovingStraight = isMovingForward || isMovingBack;

        if (forward != 0.0F || strafe != 0.0F) {
            if (isMovingBack && !isMovingSideways) {
                return direction + 180.0f;
            }
            if (isMovingForward && isMovingLeft) {
                return direction + 45.0f;
            }
            if (isMovingForward && isMovingRight) {
                return direction - 45.0f;
            }
            if (!isMovingStraight && isMovingLeft) {
                return direction + 90.0f;
            }
            if (!isMovingStraight && isMovingRight) {
                return direction - 90.0f;
            }
            if (isMovingBack && isMovingLeft) {
                return direction + 135.0f;
            }
            if (isMovingBack) {
                return direction - 135.0f;
            }
        }

        return direction;
    }

    public void fixMovement(KeyboardInputEvent event, float yaw) {
        float forward = event.getForward();
        float strafe = event.getStrafe();

        int angleUnit = 45;
        float angleTolerance = 22.5f;
        float directionFactor = Math.max(Math.abs(forward), Math.abs(strafe));
        double angleDifference = Mth.wrapDegrees(getDirection(forward, strafe) - yaw);
        double angleDistance = Math.abs(angleDifference);

        forward = 0.0f;
        strafe = 0.0f;

        if (angleDistance <= (double) ((float) angleUnit + angleTolerance)) {
            forward++;
        } else if (angleDistance >= (double) (180.0F - (float) angleUnit - angleTolerance)) {
            forward--;
        }

        if (angleDifference >= (double) ((float) angleUnit - angleTolerance) && angleDifference <= (double) (180.0F - (float) angleUnit + angleTolerance)) {
            strafe--;
        } else if (angleDifference <= (double) ((float) (-angleUnit) + angleTolerance) && angleDifference >= (double) (-180.0F + (float) angleUnit - angleTolerance)) {
            strafe++;
        }

        forward *= directionFactor;
        strafe *= directionFactor;

        event.setForward(forward);
        event.setStrafe(strafe);
    }

}
