package com.github.epsilon.modules.impl.player;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.bus.EventPriority;
import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.utils.network.NetworkUtils;
import com.github.epsilon.utils.player.ChatUtils;
import com.github.epsilon.utils.timer.TimerUtils;
import love.slmpc.ClInitNative;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.util.Mth;

import java.util.Random;

@ClInitNative
public class Disabler extends Module {

    public static final Disabler INSTANCE = new Disabler();

    private Disabler() {
        super("Disabler", Category.PLAYER);
    }

    private final BoolSetting badPacketsA = boolSetting("Grim BadPacketsA", true);
    private final BoolSetting grimDuplicateRotPlace = boolSetting("Grim DuplicateRotPlace", true);
    private final BoolSetting acaFastSwitch = boolSetting("ACA FastSwitch", true);
    private final BoolSetting acaInventoryFrequency = boolSetting("ACA InventoryFrequency", false);
    private final BoolSetting acaAimStep = boolSetting("ACA AimStep", true);
    private final BoolSetting acaPerfectRotation = boolSetting("ACA PerfectRotation", true);
    private final BoolSetting themisBlink = boolSetting("Themis Blink", true);
    private final BoolSetting logging = boolSetting("Logging", false);

    private int lastSentSlot = -1;
    private long inventoryOpenTime;
    private boolean inventoryOpen;
    private final TimerUtils inventoryTimer = new TimerUtils();
    private ServerboundContainerClosePacket storedClosePacket;
    private long inventoryCloseDelay;
    private long themisBlinkLastSend = System.currentTimeMillis();
    private int themisBlinkCount;
    private float lastYaw;
    private float lastPitch;
    private float currentYaw;
    private float currentPitch;
    private float yawDiff;
    private float pitchDiff;
    private float lastPlacedYawDiff;
    private float lastPlacedPitchDiff;
    private boolean rotated;

    private final Random random = new Random();

    private static final double[] PERFECT_ROT_STEPS = {
            0.0, 5.625, 11.25, 16.875, 22.5, 28.125, 33.75, 39.375, 45.0,
            50.625, 56.25, 61.875, 67.5, 73.125, 78.75, 84.375, 90.0
    };

    @Override
    protected void onEnable() {
        resetState();
    }

    @Override
    protected void onDisable() {
        releaseStoredClosePacket();
        resetState();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onPacketReceive(PacketEvent.Receive event) {
        Packet<?> packet = event.getPacket();
        if (!prepareForPacket(packet)) return;

        if (packet instanceof ClientboundOpenScreenPacket) {
            inventoryOpenTime = System.currentTimeMillis();
            inventoryOpen = true;
            log("Inventory opened at: " + inventoryOpenTime);
        }

        handleThemisBlink(packet);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onPacketSend(PacketEvent.Send event) {
        Packet<?> packet = event.getPacket();
        if (!prepareForPacket(packet)) return;

        if (packet instanceof ServerboundSetCarriedItemPacket carriedItemPacket) {
            int slot = carriedItemPacket.getSlot();
            if (badPacketsA.getValue() && slot == lastSentSlot && slot != -1) {
                event.cancel();
                log("BadPacketsA: Cancelled duplicate slot packet: " + slot);
                return;
            }

            if (acaFastSwitch.getValue() && lastSentSlot != -1 && slot != lastSentSlot) {
                sendIntermediateSlots(lastSentSlot, slot);
            }

            lastSentSlot = slot;
            log("Processed slot switch: " + lastSentSlot + " -> " + slot);
        }

        if (acaInventoryFrequency.getValue() && packet instanceof ServerboundContainerClosePacket closePacket && inventoryOpen) {
            long openDuration = System.currentTimeMillis() - inventoryOpenTime;
            if (openDuration <= 150L) {
                event.cancel();
                storedClosePacket = closePacket;
                inventoryCloseDelay = 151L - openDuration;
                inventoryTimer.reset();
                inventoryOpen = false;
                log("InventoryFrequency: Storing close packet, will send after " + inventoryCloseDelay + "ms");
                return;
            }

            inventoryOpen = false;
            log("InventoryFrequency: Allowed close packet after " + openDuration + "ms");
        }

        handleThemisBlink(packet);

        if (grimDuplicateRotPlace.getValue()) {
            if (packet instanceof ServerboundMovePlayerPacket movePacket && movePacket.hasRotation()) {
                float previousYaw = currentYaw;
                float previousPitch = currentPitch;
                currentYaw = movePacket.yRot;
                currentPitch = movePacket.xRot;
                yawDiff = Math.abs(currentYaw - previousYaw);
                pitchDiff = Math.abs(currentPitch - previousPitch);
                rotated = true;

                float yawDiffDelta = Math.abs(yawDiff - lastPlacedYawDiff);
                if (yawDiff > 2.0F && yawDiffDelta < 1.0E-4F) {
                    float newYaw = currentYaw - nextJitter();
                    movePacket.yRot = newYaw;
                    log("DuplicateRotPlace: Modified yaw from " + currentYaw + " to " + newYaw + " (yawDiff: " + yawDiffDelta + ")");
                }

                float pitchDiffDelta = Math.abs(pitchDiff - lastPlacedPitchDiff);
                if (pitchDiff > 2.0F && pitchDiffDelta < 1.0E-4F) {
                    float newPitch = Mth.clamp(currentPitch - nextJitter(), -90.0F, 90.0F);
                    movePacket.xRot = newPitch;
                    log("DuplicateRotPlace: Modified pitch from " + currentPitch + " to " + newPitch + " (pitchDiff: " + pitchDiffDelta + ")");
                }
            } else if (packet instanceof ServerboundUseItemOnPacket && rotated) {
                lastPlacedYawDiff = yawDiff;
                lastPlacedPitchDiff = pitchDiff;
                rotated = false;
            }
        }

        if ((acaAimStep.getValue() || acaPerfectRotation.getValue()) && packet instanceof ServerboundMovePlayerPacket movePacket) {
            float yawAim = movePacket.yRot;
            float pitchAim = movePacket.xRot;
            boolean modified = false;

            if (acaAimStep.getValue() && isAimStepRotation(yawAim, pitchAim)) {
                float[] rotation = applyAimStep(yawAim, pitchAim);
                yawAim = rotation[0];
                pitchAim = rotation[1];
                modified = true;
            }

            if (acaPerfectRotation.getValue()) {
                float[] rotation = applyPerfectRotation(yawAim, pitchAim);
                if (rotation[0] != yawAim || rotation[1] != pitchAim) {
                    yawAim = rotation[0];
                    pitchAim = rotation[1];
                    modified = true;
                    log("PerfectRotation: Modified rotation");
                }
            }

            if (modified) {
                movePacket.yRot = yawAim;
                movePacket.xRot = Mth.clamp(pitchAim, -90.0F, 90.0F);
            }

            lastYaw = movePacket.yRot;
            lastPitch = movePacket.xRot;
        }
    }

    private boolean prepareForPacket(Packet<?> packet) {
        if (packet instanceof ClientboundLoginPacket) {
            resetState();
            return false;
        }

        if (mc.player.isSpectator() || !mc.player.isAlive() || mc.player.isDeadOrDying() || mc.screen instanceof ProgressScreen) {
            resetState();
            return false;
        }

        if (storedClosePacket != null && inventoryTimer.passedMillise(inventoryCloseDelay)) {
            releaseStoredClosePacket();
            log("InventoryFrequency: Released stored close packet");
        }

        return true;
    }

    private boolean isAimStepRotation(float yaw, float pitch) {
        if (lastYaw == 0.0F && lastPitch == 0.0F) {
            return false;
        }

        double yawDelta = Math.abs(wrapDegrees(yaw - lastYaw));
        double pitchDelta = Math.abs(pitch - lastPitch);
        boolean yawStuck = yawDelta < 1.0E-5 && pitchDelta > 1.0;
        boolean pitchStuck = pitchDelta < 1.0E-5 && yawDelta > 1.0;
        return yawStuck || pitchStuck;
    }

    private void handleThemisBlink(Packet<?> packet) {
        if (!themisBlink.getValue()) return;

        if (System.currentTimeMillis() - themisBlinkLastSend > 200L) {
            if (themisBlinkCount == 0) {
                NetworkUtils.sendPacketNoEvent(new ServerboundPongPacket(0));
            }
            themisBlinkLastSend = System.currentTimeMillis();
            themisBlinkCount = 0;
        }

        if (packet instanceof ServerboundMovePlayerPacket.StatusOnly || packet instanceof ServerboundPongPacket) {
            themisBlinkCount++;
        }
    }

    private float[] applyAimStep(float yaw, float pitch) {
        double yawDelta = Math.abs(wrapDegrees(yaw - lastYaw));
        double pitchDelta = Math.abs(pitch - lastPitch);
        float newYaw = yaw;
        float newPitch = pitch;

        if (yawDelta < 1.0E-5 && pitchDelta > 1.0) {
            newYaw = lastYaw + (float) (random.nextGaussian() * 0.001);
        }
        if (pitchDelta < 1.0E-5 && yawDelta > 1.0) {
            newPitch = lastPitch + (float) (random.nextGaussian() * 0.001);
        }

        return new float[]{newYaw, newPitch};
    }

    private float[] applyPerfectRotation(float yaw, float pitch) {
        if (lastYaw == 0.0F && lastPitch == 0.0F) {
            return new float[]{yaw, pitch};
        }

        double yawDelta = Math.abs(wrapDegrees(yaw - lastYaw));
        double pitchDelta = Math.abs(pitch - lastPitch);
        float newYaw = yaw;
        float newPitch = pitch;

        if (!isNearZeroOrMultiple(yawDelta) && isKnownRotationStep(yawDelta)) {
            newYaw = yaw + (float) (random.nextGaussian() * 0.005);
        }
        if (!isNearZeroOrMultiple(pitchDelta) && isKnownRotationStep(pitchDelta)) {
            newPitch = pitch + (float) (random.nextGaussian() * 0.005);
        }

        return new float[]{newYaw, newPitch};
    }

    private boolean isNearZeroOrMultiple(double value) {
        return Math.abs(value) <= 1.0E-10 || isMultipleOf(360.0, value);
    }

    private boolean isKnownRotationStep(double value) {
        if (Double.isInfinite(value) || Double.isNaN(value)) {
            return false;
        }

        for (double step : PERFECT_ROT_STEPS) {
            if (isMultipleOf(step, value)) {
                return true;
            }
        }
        return false;
    }

    private boolean isMultipleOf(double base, double value) {
        if (base == 0.0) {
            return Math.abs(value) <= 1.0E-10;
        }

        double ratio = value / base;
        return Math.abs(ratio - Math.round(ratio)) <= 1.0E-10;
    }

    private float wrapDegrees(float degrees) {
        while (degrees > 180.0F) {
            degrees -= 360.0F;
        }
        while (degrees < -180.0F) {
            degrees += 360.0F;
        }
        return degrees;
    }

    private void sendIntermediateSlots(int fromSlot, int toSlot) {
        int distance = Math.abs(fromSlot - toSlot);
        if (distance <= 1 || isWrapAroundSlot(fromSlot, toSlot)) {
            return;
        }

        int step = fromSlot > toSlot ? -1 : 1;
        for (int slot = fromSlot + step; slot != toSlot; slot += step) {
            if (slot < 0 || slot > 8) continue;
            NetworkUtils.sendPacketNoEvent(new ServerboundSetCarriedItemPacket(slot));
            log("Sent intermediate slot: " + slot);
        }
    }

    private boolean isWrapAroundSlot(int fromSlot, int toSlot) {
        return fromSlot == 0 && toSlot == 8 || fromSlot == 8 && toSlot == 0;
    }

    private float nextJitter() {
        return 0.001F + random.nextFloat() * 0.009F;
    }

    private void releaseStoredClosePacket() {
        if (storedClosePacket != null) {
            NetworkUtils.sendPacketNoEvent(storedClosePacket);
            storedClosePacket = null;
        }
    }

    private void log(String message) {
        if (logging.getValue()) {
            ChatUtils.addChatMessage(false, "[Disabler] " + message);
        }
    }

    private void resetState() {
        lastSentSlot = -1;
        inventoryOpenTime = 0L;
        inventoryOpen = false;
        storedClosePacket = null;
        inventoryCloseDelay = 0L;
        lastYaw = 0.0F;
        lastPitch = 0.0F;
        currentYaw = 0.0F;
        currentPitch = 0.0F;
        yawDiff = 0.0F;
        pitchDiff = 0.0F;
        lastPlacedYawDiff = 0.0F;
        lastPlacedPitchDiff = 0.0F;
        rotated = false;
    }

}
