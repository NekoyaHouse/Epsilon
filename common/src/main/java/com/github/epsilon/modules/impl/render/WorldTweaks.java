package com.github.epsilon.modules.impl.render;

import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.IntSetting;
import net.minecraft.core.Holder;
import net.minecraft.world.clock.WorldClock;

import java.awt.*;

public class WorldTweaks extends Module {

    public static final WorldTweaks INSTANCE = new WorldTweaks();

    private WorldTweaks() {
        super("World Tweaks", Category.RENDER);
    }

    public final BoolSetting fogModify = boolSetting("Fog Modify", true).rootSetting();
    public final IntSetting fogStart = intSetting("Fog Start", 0, 0, 256, 1, fogModify::getValue);
    public final IntSetting fogEnd = intSetting("Fog End", 64, 10, 256, 1, fogModify::getValue);
    public final ColorSetting fogColor = colorSetting("Fog Color", new Color(0xA900FF), false, fogModify::getValue);
    public final BoolSetting changeTime = boolSetting("Change Time", false).rootSetting();
    public final IntSetting time = intSetting("Time", 21, 0, 23, 1, changeTime::getValue);

    public long getModifiedClockTime(Holder<WorldClock> clock, long original) {
        if (!isEnabled() || !changeTime.getValue() || mc.level == null) return original;
        return mc.level.dimensionType().defaultClock().filter(clock::equals).isPresent() ? time.getValue() * 1000L : original;
    }

}
