package com.github.epsilon.modules.impl.render;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.utils.render.WireframeEntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;

import java.awt.*;

public class CrystalChams extends Module {

    public static final CrystalChams INSTANCE = new CrystalChams();

    private CrystalChams() {
        super("Crystal Chams", Category.RENDER);
    }

    private final ColorSetting sideColor = colorSetting("Side Color", new Color(160, 120, 255, 70), true);
    private final ColorSetting lineColor = colorSetting("Line Color", new Color(230, 220, 255, 220), true);
    private final DoubleSetting scale = doubleSetting("Scale", 1.0, 0.1, 3.0, 0.05);
    private final DoubleSetting lineWidth = doubleSetting("Line Width", 2.0, 0.5, 5.0, 0.5);

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        boolean batching = false;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof EndCrystal endCrystal)) continue;
            if (!batching) {
                WireframeEntityRenderer.beginBatch(event.getPoseStack());
                batching = true;
            }

            WireframeEntityRenderer.render(event.getPoseStack(), endCrystal, scale.getValue(), sideColor.getValue(), lineColor.getValue(), lineWidth.getValue().floatValue());
        }

        if (batching) {
            WireframeEntityRenderer.endBatch();
        }
    }

}
