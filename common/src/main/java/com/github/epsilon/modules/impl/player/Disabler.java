package com.github.epsilon.modules.impl.player;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import love.slmpc.ClInitNative;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.util.Mth;

import java.util.Random;

@ClInitNative
public class Disabler extends Module {

    public static final Disabler INSTANCE = new Disabler();

    private Disabler() {
        super("Disabler", Category.PLAYER);
    }

    private final BoolSetting duplicateRotPlace = boolSetting("Duplicate Rot Place", true);

    private float currentYaw;
    private float currentPitch;
    private float yawDiff;
    private float pitchDiff;
    private float lastPlacedYawDiff;
    private float lastPlacedPitchDiff;
    private boolean rotated;

    private final Random random = new Random();

    @Override
    protected void onEnable() {
        resetState();
    }

    @Override
    protected void onDisable() {
        resetState();
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (!duplicateRotPlace.getValue()) return;
        if (event.getPacket() instanceof ServerboundMovePlayerPacket packet && packet.hasRotation()) {
            float previousYaw = currentYaw;
            float previousPitch = currentPitch;
            currentYaw = packet.yRot;
            currentPitch = packet.xRot;
            yawDiff = Math.abs(currentYaw - previousYaw);
            pitchDiff = Math.abs(currentPitch - previousPitch);
            rotated = true;

            float yawDiffDelta = Math.abs(yawDiff - lastPlacedYawDiff);
            if (yawDiff > 2.0F && yawDiffDelta < 1.0E-4F) {
                packet.yRot = currentYaw - nextJitter();
            }

            float pitchDiffDelta = Math.abs(pitchDiff - lastPlacedPitchDiff);
            if (pitchDiff > 2.0F && pitchDiffDelta < 1.0E-4F) {
                packet.xRot = Mth.clamp(currentPitch - nextJitter(), -90.0F, 90.0F);
            }
        } else if (event.getPacket() instanceof ServerboundUseItemOnPacket && rotated) {
            lastPlacedYawDiff = yawDiff;
            lastPlacedPitchDiff = pitchDiff;
            rotated = false;
        }
    }

    private float nextJitter() {
        return 0.001F + random.nextFloat() * 0.009F;
    }

    private void resetState() {
        currentYaw = 0.0F;
        currentPitch = 0.0F;
        yawDiff = 0.0F;
        pitchDiff = 0.0F;
        lastPlacedYawDiff = 0.0F;
        lastPlacedPitchDiff = 0.0F;
        rotated = false;
    }

}
