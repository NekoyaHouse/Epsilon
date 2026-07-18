package com.github.epsilon.modules.impl.render;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.graphics.schedulers.render3d.Render3DScheduler;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.awt.*;

public class BlockHighlight extends Module {

    public static final BlockHighlight INSTANCE = new BlockHighlight();

    private BlockHighlight() {
        super("Block Highlight", Category.RENDER);
    }

    private enum Mode {
        Both,
        BothSide,
        Fill,
        FilledSide,
        Outline,
        OutlinedSide
    }

    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.Outline);
    private final ColorSetting sideColor = colorSetting("Color", new Color(255, 255, 255, 100), () -> mode.is(Mode.Both) || mode.is(Mode.BothSide) || mode.is(Mode.Fill) || mode.is(Mode.FilledSide));
    private final ColorSetting lineColor = colorSetting("Line Color", new Color(255, 255, 255, 255), () -> mode.is(Mode.Both) || mode.is(Mode.BothSide) || mode.is(Mode.Outline) || mode.is(Mode.OutlinedSide));
    private final DoubleSetting lineWidth = doubleSetting("Line Width", 1.0, 0.0, 5.0, 0.5);
    private final BoolSetting blur = boolSetting("Blur", true);
    private final DoubleSetting blurStrength = doubleSetting("Blur Strength", 5.0, 0.0, 16.0, 0.5, () -> blur.getValue() && (mode.is(Mode.Both) || mode.is(Mode.BothSide) || mode.is(Mode.Fill)));

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        HitResult hitResult = mc.hitResult;
        if (hitResult == null || hitResult.getType() != HitResult.Type.BLOCK || !(hitResult instanceof BlockHitResult bhr)) {
            return;
        }

        float thickness = lineWidth.getValue().floatValue();
        AABB box = new AABB(bhr.getBlockPos());
        int fillColor = sideColor.getValue().getRGB();
        int outlineColor = lineColor.getValue().getRGB();
        Direction direction = bhr.getDirection();

        switch (mode.getValue()) {
            case Both -> {
                drawBlur(box);
                Render3DScheduler.INSTANCE.addFilledBox(box, fillColor);
                Render3DScheduler.INSTANCE.addOutlineBox(box, outlineColor, thickness);
            }
            case BothSide -> {
                drawBlur(box);
                Render3DScheduler.INSTANCE.addSideOutline(box, outlineColor, thickness, direction);
                Render3DScheduler.INSTANCE.addFilledSide(box, fillColor, direction);
            }
            case Fill -> {
                drawBlur(box);
                Render3DScheduler.INSTANCE.addFilledBox(box, fillColor);
            }
            case FilledSide -> {
                Render3DScheduler.INSTANCE.addFilledSide(box, fillColor, direction);
            }
            case Outline -> {
                Render3DScheduler.INSTANCE.addOutlineBox(box, outlineColor, thickness);
            }
            case OutlinedSide -> {
                Render3DScheduler.INSTANCE.addSideOutline(box, outlineColor, thickness, direction);
            }
        }
    }

    private void drawBlur(AABB aabb) {
        if (blur.getValue()) Render3DScheduler.INSTANCE.addBlurredBox(aabb, blurStrength.getValue());
    }

}
