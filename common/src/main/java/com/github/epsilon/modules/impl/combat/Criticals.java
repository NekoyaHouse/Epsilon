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
        part(new ObservePart());
        part(new DecidePart());
        part(new TransformPart());
    }

    /**
     * OBSERVE：统计用于触发暴击的离地 tick 数。
     */
    private final class ObservePart implements ModulePart {
        @Override
        public void declare(ModuleDeclaration declaration) {
            node(ClientTickEvent.Pre.class, NodeKey.of("observe.fall_ticks"))
                    .phase(Phase.OBSERVE)
                    .priority(EventPriority.HIGHEST)
                    .handler(Criticals.this::onClientTick);
        }
    }

    /**
     * DECIDE：判定本 tick 是否需要中断疾跑。
     * <p>
     * 必须晚于 {@code observe.fall_ticks}（本模块内以 LOWEST 优先级表达），
     * 因为它读取的就是同一 tick 刚更新完的 {@link #fallTicks}。
     */
    private final class DecidePart implements ModulePart {
        @Override
        public void declare(ModuleDeclaration declaration) {
            node(ClientTickEvent.Pre.class, NodeKey.of("decide.sprint_stop"))
                    .phase(Phase.DECIDE)
                    .priority(EventPriority.LOWEST)
                    .handler(Criticals.this::prepareSprintStop);
        }
    }

    /**
     * TRANSFORM：在输入事件上取消疾跑标记，并同步本地按键状态。
     */
    private final class TransformPart implements ModulePart {
        @Override
        public void declare(ModuleDeclaration declaration) {
            node(KeyboardInputEvent.class, NodeKey.of("transform.sprint_input"))
                    .phase(Phase.TRANSFORM)
                    .priority(EventPriority.LOWEST)
                    .handler(Criticals.this::onKeyboardInput);
        }
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
