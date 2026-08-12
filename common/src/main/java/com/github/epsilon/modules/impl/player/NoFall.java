package com.github.epsilon.modules.impl.player;

import com.github.epsilon.Constants;
import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.KeyboardInputEvent;
import com.github.epsilon.events.impl.SendPositionEvent;
import com.github.epsilon.managers.NotificationManager;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.modules.impl.movement.NoSlowdown;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.utils.network.NetworkUtils;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

public class NoFall extends Module {

    public static final NoFall INSTANCE = new NoFall();

    private NoFall() {
        super("No Fall", Category.PLAYER);
    }

    private enum Mode {
        GroundSpoof,
        GrimJump
    }

    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.GroundSpoof);
    private final DoubleSetting fallDistance = doubleSetting("Fall Distance", 3.0, 3.0, 15.0, 0.1);

    private boolean jump;
    private double lastFallDistance;

    @Override
    protected void onEnable() {
        lastFallDistance = 0.0;
    }

    @Override
    protected void onDisable() {
        lastFallDistance = 0.0;
    }

    @EventHandler
    private void onSendPosition(SendPositionEvent event) {
        switch (mode.getValue()) {
            case GroundSpoof -> {
                if (mc.player.fallDistance > fallDistance.getValue()) event.setOnGround(true);
            }
            case GrimJump -> {
                double oldFallDistance = mc.player.fallDistance;

                if (lastFallDistance >= fallDistance.getValue()) {
                    NoSlowdown noSlowdown = NoSlowdown.INSTANCE;
                    if (noSlowdown.isWorking()) {
                        NotificationManager.INSTANCE.warning(noSlowdown.getTranslatedName(), EpsilonTranslations.Notifications.NO_SLOWDOWN_DISABLED_FALLING.getTranslatedName());
                        noSlowdown.stop();
                    }

                    if (event.isOnGround()) {
                        event.setOnGround(false);
                        NetworkUtils.sendPacketNoEvent(new ServerboundMovePlayerPacket.StatusOnly(true, mc.player.horizontalCollision));
                        Constants.skipTicks++;
                        jump = true;
                    }
                }

                lastFallDistance = oldFallDistance;
            }
        }
    }

    @EventHandler
    private void onKeyboardInput(KeyboardInputEvent event) {
        if (!mode.is(Mode.GrimJump)) return;

        if (lastFallDistance >= fallDistance.getValue() && mc.player.onGround()) {
            event.setSneak(false);
        }

        if (jump) {
            event.setJump(true);
            jump = false;
        }
    }

}
