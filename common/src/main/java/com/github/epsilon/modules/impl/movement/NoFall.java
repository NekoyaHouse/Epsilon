package com.github.epsilon.modules.impl.movement;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.ClientTickEvent;
import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.events.impl.SendPositionEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

public class NoFall extends Module {

    public static final NoFall INSTANCE = new NoFall();

    private NoFall() {
        super("No Fall", Category.MOVEMENT);
    }

    private enum Mode {
        GroundSpoof,
        Grim2B2T
    }

    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.GroundSpoof);
    private final DoubleSetting fallDistance = doubleSetting("Fall Distance", 3, 3, 16, 1, () -> mode.is(Mode.GroundSpoof));

    private boolean shouldCancel = false;

    @Override
    protected void onEnable() {
        shouldCancel = false;
    }

    @EventHandler
    private void onClientTick(ClientTickEvent.Pre event) {
        if (!nullCheck() && mode.is(Mode.Grim2B2T) && isFalling()) {
            mc.getConnection().send(new ServerboundMovePlayerPacket.PosRot(mc.player.getX(), mc.player.getY() + 0.000000001, mc.player.getZ(), mc.player.getYRot(), mc.player.getXRot(), false, mc.player.horizontalCollision));
            mc.player.resetFallDistance();
        }
    }

    @EventHandler
    private void onSendPosition(SendPositionEvent event) {
        if (isFalling() && mode.is(Mode.GroundSpoof)) {
            shouldCancel = true;
        }
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (event.getPacket() instanceof ServerboundMovePlayerPacket packet) {
            if (shouldCancel) packet.onGround = false;
        }
    }

    private boolean isFalling() {
        if (mc.player.isFallFlying()) {
            return false;
        }
        if (mode.is(Mode.Grim2B2T)) {
            return mc.player.fallDistance > 3f;
        }
        return mc.player.fallDistance > fallDistance.getValue();
    }

}
