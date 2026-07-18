package com.github.epsilon.elements.impl;

import com.github.epsilon.elements.HudModule;
import com.github.epsilon.graphics.shaders.BlurShader;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.IntSetting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

import java.awt.*;

public class Inventory extends HudModule {

    public static final Inventory INSTANCE = new Inventory();

    private Inventory() {
        super("Inventory HUD", 0f, 0f, 180f, 80f);
    }

    private final DoubleSetting scale = doubleSetting("Scale", 1.0, 0.5, 2.0, 0.1);
    private final DoubleSetting cornerRadius = doubleSetting("Corner Radius", 3.0, 0.0, 14.0, 0.5);
    private final ColorSetting backgroundColor = colorSetting("Background Color", new Color(15, 15, 15, 135));
    private final ColorSetting slotColor = colorSetting("Slot Color", new Color(0, 0, 0, 70));
    private final BoolSetting drawShadow = boolSetting("Drop Shadow", true);
    private final DoubleSetting shadowBlur = doubleSetting("Shadow Blur", DEFAULT_SHADOW_BLUR, MIN_SHADOW_BLUR, MAX_SHADOW_BLUR, SHADOW_BLUR_STEP, drawShadow::getValue);
    private final ColorSetting shadowColor = colorSetting("Shadow Color", DEFAULT_SHADOW_COLOR, drawShadow::getValue);
    private final BoolSetting backgroundBlur = boolSetting("Background Blur", true);
    private final IntSetting blurStrength = intSetting("Blur Strength", 5, 1, 16, 1);

    private final BoolSetting showCount = boolSetting("Show Count", true);

    private static final float SLOT_SIZE = 17.0f;
    private static final float SLOT_GAP = 1.5f;
    private static final float PADDING = 4.5f;

    @Override
    public void render(DeltaTracker deltaTracker) {
        if (nullCheck()) return;

        var scope = renderScope();

        float scale = this.scale.getValue().floatValue();
        float slotSize = SLOT_SIZE * scale;
        float gap = SLOT_GAP * scale;
        float padding = PADDING * scale;
        float radius = cornerRadius.getValue().floatValue() * scale;
        float slotRadius = 2.0f * scale;

        float totalWidth = padding * 2f + 9 * slotSize + (9 - 1) * gap;
        float totalHeight = padding * 2f + 3 * slotSize + (3 - 1) * gap;

        if (backgroundBlur.getValue()) {
            BlurShader.INSTANCE.render(this.x, this.y, totalWidth, totalHeight, radius, blurStrength.getValue());
        }

        if (drawShadow.getValue()) {
            scope.shadow(this.x, this.y, totalWidth, totalHeight, radius, shadowBlur.getValue().floatValue(), shadowColor.getValue());
        }
        scope.roundRect(this.x, this.y, totalWidth, totalHeight, radius, backgroundColor.getValue());

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                float slotX = this.x + padding + col * (slotSize + gap);
                float slotY = this.y + padding + row * (slotSize + gap);
                scope.roundRect(slotX, slotY, slotSize, slotSize, slotRadius, slotColor.getValue());
            }
        }

        setBounds(totalWidth, totalHeight);
    }

    @Override
    public void renderOverlay(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (nullCheck()) return;

        float scale = this.scale.getValue().floatValue();
        float slotSize = SLOT_SIZE * scale;
        float gap = SLOT_GAP * scale;
        float padding = PADDING * scale;

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotIndex = 9 + row * 9 + col;
                ItemStack stack = mc.player.getInventory().getItem(slotIndex);
                if (!stack.isEmpty()) {
                    float slotX = this.x + padding + col * (slotSize + gap);
                    float slotY = this.y + padding + row * (slotSize + gap);
                    drawItem(graphics, stack, slotX, slotY, scale);
                }
            }
        }
    }

    private void drawItem(GuiGraphicsExtractor graphics, ItemStack stack, float slotX, float slotY, float scale) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(slotX + scale, slotY + scale);
        graphics.pose().scale(scale, scale);
        graphics.item(stack, 0, 0);
        if (showCount.getValue() && stack.getCount() > 1) {
            graphics.itemDecorations(mc.font, stack, 0, 0, String.valueOf(stack.getCount()));
        }
        graphics.pose().popMatrix();
    }

}
