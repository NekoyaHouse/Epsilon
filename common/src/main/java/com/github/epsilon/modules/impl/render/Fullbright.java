package com.github.epsilon.modules.impl.render;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.EnumSetting;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public class Fullbright extends Module {

    public static final Fullbright INSTANCE = new Fullbright();

    private Fullbright() {
        super("Fullbright", Category.RENDER);
    }

    private enum Mode {
        Gamma,
        Potion
    }

    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.Gamma, v -> {
        if (v == Mode.Gamma && mc.player != null) {
            mc.player.removeEffect(MobEffects.NIGHT_VISION);
        }
    });

    public boolean isGammaMode() {
        return isEnabled() && mode.is(Mode.Gamma);
    }

    @Override
    protected void onDisable() {
        if (nullCheck() || mode.is(Mode.Gamma)) return;
        mc.player.removeEffect(MobEffects.NIGHT_VISION);
    }

    @EventHandler
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        if (mode.is(Mode.Potion)) mc.player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, -1, 0));
    }

}
