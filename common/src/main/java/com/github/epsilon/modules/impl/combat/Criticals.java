package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.events.bus.EventPriority;
import com.github.epsilon.events.impl.ClientTickEvent;
import com.github.epsilon.events.impl.KeyboardInputEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.modules.orchestration.*;
import com.github.epsilon.modules.impl.movement.Velocity;

public class Criticals extends Module {

    public static final Criticals INSTANCE = new Criticals();

    private Criticals() {
        super("Criticals", Category.COMBAT);
        setDispatchMode(ModuleDispatchMode.MANAGED);
        node(ClientTickEvent.Pre.class, NodeKey.of("managed.onClientTick.clienttickevent_pre")).phase(Phase.OBSERVE).priority(EventPriority.HIGHEST).handler(this::onClientTick);
        node(ClientTickEvent.Pre.class, NodeKey.of("managed.prepareSprintStop.clienttickevent_pre")).phase(Phase.OBSERVE).priority(EventPriority.LOWEST).handler(this::prepareSprintStop);
        node(KeyboardInputEvent.class, NodeKey.of("managed.onKeyboardInput.keyboardinputevent")).phase(Phase.TRANSFORM).priority(EventPriority.LOWEST).handler(this::onKeyboardInput);

    }

    public int fallTicks;
    private boolean stopSprinting;

    @Override
    protected void onDisable() {
        fallTicks = 0;
        stopSprinting = false;
    }
    private void onClientTick(ClientTickEvent.Pre event) {
        if (nullCheck() || !canCrit() || mc.player.fallDistance >= 1.0f) {
            fallTicks = 0;
        } else {
            fallTicks++;
        }
    }
    private void prepareSprintStop(ClientTickEvent.Pre event) {
        stopSprinting = !nullCheck()
                && fallTicks > 0
                && fallTicks < 3
                && mc.player.isSprinting()
                && KillAura.INSTANCE.target != null
                && Velocity.INSTANCE.attackQueue == 0;
    }
    private void onKeyboardInput(KeyboardInputEvent event) {
        if (!stopSprinting) return;
        stopSprinting = false;

        if (fallTicks > 0
                && fallTicks < 3
                && canCrit()
                && mc.player.isSprinting()
                && KillAura.INSTANCE.target != null
                && Velocity.INSTANCE.attackQueue == 0
                && event.getForward() > 0.0f) {
            event.setSprint(false);
            mc.player.setSprinting(false);
            mc.options.keySprint.setDown(false);
        }
    }

    private boolean canCrit() {
        return mc.player.fallDistance > 0.0 && !mc.player.onGround() && !mc.player.onClimbable() && !mc.player.isInWater() && !mc.player.isMobilityRestricted() && !mc.player.isPassenger();
    }

}
