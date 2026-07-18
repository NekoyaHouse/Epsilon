package com.github.epsilon.modules.impl.movement;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.KeyboardInputEvent;
import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.events.impl.RightClickEvent;
import com.github.epsilon.events.impl.TravelEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.utils.network.PacketUtils;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

public class Stuck extends Module {

    public static final Stuck INSTANCE = new Stuck();

    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.NoPacket);

    private Stuck() {
        super("Stuck", Category.MOVEMENT);
    }

    private float lastYaw;
    private float lastPitch;

    private enum Mode {
        NoPacket,
        CancelMove
    }

    @Override
    protected void onDisable() {
        if (!nullCheck() && mode.is(Mode.NoPacket) && !mc.player.onGround()) {
            PacketUtils.sendSilently(new ServerboundMovePlayerPacket.PosRot(mc.player.getX() + 1337, mc.player.getY(), mc.player.getZ() + 1337, mc.player.getYRot() + 0.01f, mc.player.getXRot(), mc.player.onGround(), mc.player.horizontalCollision));
        }
    }

    @EventHandler
    private void onKeyboardInput(KeyboardInputEvent event) {
        event.setForward(0);
        event.setStrafe(0);
    }

    @EventHandler
    private void onPacket(PacketEvent.Send event) {
        if (mode.is(Mode.NoPacket)) {
            if (event.getPacket() instanceof ServerboundMovePlayerPacket || (event.getPacket() instanceof ClientboundSetEntityMotionPacket setEntityMotionPacket && setEntityMotionPacket.id() == mc.player.getId())) {
                event.cancel();
            }
        }
        if (event.getPacket() instanceof ClientboundPlayerPositionPacket) {
            toggle();
        }
    }

    @EventHandler
    private void onTravel(TravelEvent event) {
        if (mode.is(Mode.CancelMove) && mc.player.positionReminder < 19) {
            event.cancel();
        }
    }

    @EventHandler
    private void onInteract(RightClickEvent event) {
        if (mode.is(Mode.NoPacket)) {
            if (mc.player.getYRot() != lastYaw || mc.player.getXRot() != lastPitch) {
                PacketUtils.sendSilently(new ServerboundMovePlayerPacket.Rot(mc.player.getYRot(), mc.player.getXRot(), mc.player.onGround(), mc.player.horizontalCollision));
            }
            lastPitch = mc.player.getXRot();
            lastYaw = mc.player.getYRot();
        }
    }

}
