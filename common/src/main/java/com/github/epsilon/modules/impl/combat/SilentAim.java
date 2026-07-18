package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.events.impl.SwingHandEvent;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.managers.impl.target.TargetRequest;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.IntSetting;
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

    @EventHandler
    private void onTick(PlayerTickEvent.Pre event) {
        if (nullCheck() || !redirecting) return;

        if (
                target == null || !target.isAlive() || target.isDeadOrDying()
                        || RotationUtils.getEyeDistanceToEntity(target) > range.getValue()
        ) {
            redirecting = false;
            return;
        }

        Rot2f rotations = RotationUtils.calculate(target.getEyePosition());
        Managers.ROTATION.setRotations(rotations, 180, Priority.High);

        HitResult hitResult = mc.hitResult;
        if (hitResult != null && hitResult.getType() == HitResult.Type.ENTITY) {
            mc.gameMode.attack(mc.player, target);
            mc.player.swing(InteractionHand.MAIN_HAND);
            redirecting = false;
        }
    }

    @EventHandler
    private void onSwingHand(SwingHandEvent event) {
        if (redirecting) return;

        if (weaponOnly.getValue() && !mc.player.getMainHandItem().has(DataComponents.WEAPON)) {
            return;
        }

        HitResult hitResult = mc.hitResult;
        if (hitResult == null || hitResult.getType() != HitResult.Type.MISS) {
            return;
        }

        target = Managers.TARGET.acquirePrimary(TargetRequest.of(
                range.getValue(),
                fov.getValue(),
                player.getValue(),
                mob.getValue(),
                animal.getValue(),
                villagers.getValue(),
                invisible.getValue(),
                1
        ));

        if (target == null) return;

        event.cancel();

        redirecting = true;
    }

}
