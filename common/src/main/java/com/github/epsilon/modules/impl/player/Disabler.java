package com.github.epsilon.modules.impl.player;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.utils.network.PacketUtils;
import love.slmpc.ClInitNative;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.entity.player.Input;

import java.util.Random;

@ClInitNative
public class Disabler extends Module {

    public static final Disabler INSTANCE = new Disabler();

    private Disabler() {
        super("Disabler", Category.PLAYER);
    }

    private final BoolSetting badPacketsA = boolSetting("BadPacketsA", true);
    private final BoolSetting input = boolSetting("Input", true);
    private final BoolSetting duplicateRotPlace = boolSetting("Duplicate Rot Place", false);
    private final BoolSetting aimModulo360 = boolSetting("Aim Modulo 360", false);

    private int lastSendSlot = -1;
    private boolean hasOldInput;
    private Input oldInput;
    private boolean shouldRestore;

    private float playerYaw = 0.0f;
    private float yawOffset = 0.0f;

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        Packet<?> packet = event.getPacket();

        if (aimModulo360.getValue()) {
            if (event.getPacket() instanceof ServerboundMovePlayerPacket c03Packet && c03Packet.hasRotation()) {
                float yaw = c03Packet.yRot;
                if (yaw < 360.0f && yaw > -360.0f) {
                    yawOffset = 720f;
                    c03Packet.yRot = yaw + yawOffset;
                } else {
                    yawOffset = 0.0f;
                }
            }
        }

        if (duplicateRotPlace.getValue()) {
            if (event.getPacket() instanceof ServerboundMovePlayerPacket c03Packet && c03Packet.hasRotation()) {
                float originalYaw = c03Packet.yRot;

                if (originalYaw < 360.0F && originalYaw > -360.0F) {
                    yawOffset = 720f;
                    c03Packet.yRot = originalYaw + yawOffset;
                } else {
                    yawOffset = 0.0f;
                }

                float lastPlayerYaw = this.playerYaw;
                this.playerYaw = c03Packet.yRot;

                float deltaYaw = Math.abs(this.playerYaw - lastPlayerYaw);
                if (deltaYaw > 2.0F) {
                    Random random = new Random();
                    float perturbation = 0.005f + random.nextFloat() * 0.015f;
                    if (random.nextBoolean()) {
                        c03Packet.yRot = c03Packet.yRot + perturbation;
                        yawOffset += perturbation;
                    } else {
                        c03Packet.yRot = c03Packet.yRot - perturbation;
                        yawOffset -= perturbation;
                    }
                }
            }
        }

        if (event.getPacket() instanceof ServerboundUseItemPacket useItemPacket) {
            if ((aimModulo360.getValue() || duplicateRotPlace.getValue()) && yawOffset != 0.0f) {
                float yaw = useItemPacket.yRot;
                if (yaw < 360.0f && yaw > -360.0f) {
                    useItemPacket.yRot = yaw + yawOffset;
                }
            }
        }

        if (badPacketsA.getValue()) {
            if (packet instanceof ServerboundSetCarriedItemPacket setCarriedItemPacket) {
                int slot = setCarriedItemPacket.getSlot();
                if (slot == lastSendSlot && slot != -1) {
                    event.cancel();
                }
                lastSendSlot = setCarriedItemPacket.getSlot();
            }
        }

        if (packet instanceof ServerboundContainerClickPacket || packet instanceof ServerboundContainerClosePacket) {
            event.cancel();

            if (input.getValue()) {
                spoofInput();
                hasOldInput = true;
            }

            PacketUtils.sendSilently(packet);

            if (input.getValue() && hasOldInput) {
                restoreInput();
                hasOldInput = false;
            }
        }
    }

    private void spoofInput() {
        if (shouldRestore) return;
        oldInput = mc.player.input.keyPresses;
        mc.player.input.keyPresses = Input.EMPTY;
        mc.getConnection().send(new ServerboundPlayerInputPacket(Input.EMPTY));
        mc.player.lastSentInput = Input.EMPTY;
        shouldRestore = true;
    }

    private void restoreInput() {
        if (!shouldRestore) return;
        mc.player.input.keyPresses = oldInput;
        oldInput = null;
        shouldRestore = false;
    }

}
