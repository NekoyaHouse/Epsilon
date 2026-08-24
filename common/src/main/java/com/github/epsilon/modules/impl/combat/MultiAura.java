package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.managers.target.TargetManager;
import com.github.epsilon.managers.target.TargetRequest;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.math.MathUtils;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

public class MultiAura extends Module {

    public static final MultiAura INSTANCE = new MultiAura();

    private MultiAura() {
        super("Multi Aura", Category.COMBAT);
    }

    private final DoubleSetting range = doubleSetting("Range", 5.0, 1.0, 6.0, 0.05);
    private final IntSetting minCPS = intSetting("Min CPS", 10, 1, 20, 1);
    private final IntSetting maxCPS = intSetting("Max CPS", 12, 1, 20, 1);
    private final IntSetting fov = intSetting("FOV", 360, 30, 360, 10);
    private final BoolSetting swingHand = boolSetting("Swing Hand", true);
    private final BoolSetting pauseOnContainers = boolSetting("Pause On Containers", false);

    private final BoolSetting targetPlayer = boolSetting("Player", true);
    private final BoolSetting targetMob = boolSetting("Mob", true);
    private final BoolSetting targetAnimal = boolSetting("Animal", true);
    private final BoolSetting targetVillager = boolSetting("Villager", false);
    private final BoolSetting targetAmbient = boolSetting("Ambient", false);
    private final BoolSetting targetWater = boolSetting("Water", false);
    private final BoolSetting targetOthers = boolSetting("Others", false);
    private final BoolSetting targetInvisible = boolSetting("Invisible", true);

    private double attacks;

    @Override
    protected void onDisable() {
        attacks = 0.0;
    }

    @EventHandler
    private void onTick(PlayerTickEvent.Pre event) {
        if (nullCheck()) return;
        if (pauseOnContainers.getValue() && mc.gui.screen() != null) return;
        if (mc.player.isUsingItem() || mc.player.isBlocking()) return;

        attacks += MathUtils.getRandom(minCPS.getValue().doubleValue(), maxCPS.getValue().doubleValue()) / 20.0;
        if (attacks < 1.0) return;

        List<LivingEntity> targets = TargetManager.INSTANCE.acquireTargets(TargetRequest.of(
                range.getValue(),
                fov.getValue().floatValue(),
                targetPlayer.getValue(),
                targetMob.getValue(),
                targetAnimal.getValue(),
                targetVillager.getValue(),
                targetAmbient.getValue(),
                targetWater.getValue(),
                targetOthers.getValue(),
                targetInvisible.getValue(),
                64
        ));

        if (targets.isEmpty()) return;

        while (attacks >= 1.0) {
            for (LivingEntity target : targets) {
                mc.gameMode.attack(mc.player, target);
            }
            attacks -= 1.0;
        }

        if (swingHand.getValue()) {
            mc.player.swing(InteractionHand.MAIN_HAND);
        } else {
            mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
        }
    }

}
