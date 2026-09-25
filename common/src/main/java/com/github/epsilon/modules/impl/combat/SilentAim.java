package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.events.impl.SwingHandEvent;
import com.github.epsilon.managers.rotation.RotationManager;
import com.github.epsilon.managers.target.TargetManager;
import com.github.epsilon.managers.target.TargetRequest;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.modules.orchestration.*;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.player.PlayerUtils;
import com.github.epsilon.utils.rotation.Priority;
import com.github.epsilon.utils.rotation.Rot2f;
import com.github.epsilon.utils.rotation.RotationUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.HitResult;

public class SilentAim extends Module {

    public static final SilentAim INSTANCE = new SilentAim();

    private SilentAim() {
        super("Silent Aim", Category.COMBAT);
        setDispatchMode(ModuleDispatchMode.MANAGED);
        part(new CommitPart());
        part(new TransformPart());
    }

    /**
     * COMMIT：先向 RotationManager 写入静默旋转，再按该旋转的命中结果直接攻击。
     */
    private final class CommitPart implements ModulePart {
        @Override
        public void declare(ModuleDeclaration declaration) {
            node(PlayerTickEvent.Pre.class, NodeKey.of("commit.silent_attack"))
                    .phase(Phase.COMMIT)

                    .handler(SilentAim.this::onTick);
        }
    }

    /**
     * TRANSFORM：挥手事件没有外部副作用，这里只取消原版挥手并记录待处理的静默重定向。
     */
    private final class TransformPart implements ModulePart {
        @Override
        public void declare(ModuleDeclaration declaration) {
            node(SwingHandEvent.class, NodeKey.of("transform.swing_redirect"))
                    .phase(Phase.TRANSFORM)

                    .handler(SilentAim.this::onSwingHand);
        }
    }

    private final BoolSetting weaponOnly = boolSetting("Weapon Only", false);
    private final BoolSetting player = boolSetting("Player", true);
    private final BoolSetting mob = boolSetting("Mob", false);
    private final BoolSetting animal = boolSetting("Animal", false);
    private final BoolSetting villagers = boolSetting("Villagers", false);
    private final BoolSetting invisible = boolSetting("Invisible", false);
    private final DoubleSetting range = doubleSetting("Range", 3.0, 1.0, 6.0, 0.1);
    private final IntSetting fov = intSetting("FOV", 360, 10, 360, 1);

    private boolean redirecting;
    private LivingEntity target;
    private void onTick(PlayerTickEvent.Pre event) {
        if (nullCheck() || !redirecting) return;

        if (target == null || !target.isAlive() || target.isDeadOrDying() || RotationUtils.getEyeDistanceToEntity(target) > range.getValue()) {
            redirecting = false;
            return;
        }

        Rot2f rotations = RotationUtils.calculate(target.getEyePosition());
        RotationManager.INSTANCE.setRotations(rotations, 180, Priority.High);

        HitResult hitResult = RotationManager.INSTANCE.getHitResult();
        if (hitResult != null && hitResult.getType() == HitResult.Type.ENTITY) {
            mc.gameMode.attack(mc.player, target);
            PlayerUtils.swingHand(InteractionHand.MAIN_HAND);
            redirecting = false;
        }
    }
    private void onSwingHand(SwingHandEvent event) {
        if (redirecting) return;

        if (weaponOnly.getValue() && !mc.player.getMainHandItem().has(DataComponents.WEAPON)) {
            return;
        }

        HitResult hitResult = mc.hitResult;
        if (hitResult == null || hitResult.getType() != HitResult.Type.MISS) {
            return;
        }

        target = TargetManager.INSTANCE.acquirePrimary(TargetRequest.of(
                range.getValue(),
                fov.getValue(),
                player.getValue(),
                mob.getValue(),
                animal.getValue(),
                villagers.getValue(),
                false,
                false,
                false,
                invisible.getValue(),
                1
        ));

        if (target == null) return;

        event.cancel();

        redirecting = true;
    }

}
