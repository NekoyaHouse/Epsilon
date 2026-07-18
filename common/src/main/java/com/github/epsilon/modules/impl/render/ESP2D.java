package com.github.epsilon.modules.impl.render;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.Render2DEvent;
import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.renderers.RectRenderer;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.utils.render.WorldToScreen;
import com.google.common.base.Suppliers;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector4d;

import java.awt.*;
import java.util.function.Supplier;

public class ESP2D extends Module {

    public static final ESP2D INSTANCE = new ESP2D();

    private ESP2D() {
        super("ESP 2D", Category.RENDER);
    }

    private final BoolSetting players = boolSetting("Players", true);
    private final BoolSetting friends = boolSetting("Friends", true);
    private final BoolSetting creatures = boolSetting("Creatures", false);
    private final BoolSetting monsters = boolSetting("Monsters", false);
    private final BoolSetting ambients = boolSetting("Ambients", false);
    private final BoolSetting others = boolSetting("Others", false);
    private final BoolSetting renderHealth = boolSetting("Render Health", true);
    private final DoubleSetting healthBarWidth = doubleSetting("Health Bar Width", 2.0, 0.5, 6.0, 0.5, renderHealth::getValue);
    private final BoolSetting healthBarOutline = boolSetting("Health Bar Outline", true, renderHealth::getValue);
    private final DoubleSetting healthBarOutlineWidth = doubleSetting("Health Bar Outline Width", 1.0, 0.5, 3.0, 0.5, () -> renderHealth.getValue() && healthBarOutline.getValue());
    private final BoolSetting renderBox = boolSetting("Render Box", true);
    private final BoolSetting boxOutline = boolSetting("Box Outline", true, renderBox::getValue);

    private final ColorSetting playersColor = colorSetting("Players Color", new Color(0xFF9200), false);
    private final ColorSetting friendsColor = colorSetting("Friends Color", new Color(0x30FF00), false);
    private final ColorSetting creaturesColor = colorSetting("Creatures Color", new Color(0xA0A4A6), false);
    private final ColorSetting monstersColor = colorSetting("Monsters Color", new Color(0xFF0000), false);
    private final ColorSetting ambientsColor = colorSetting("Ambients Color", new Color(0x7B00FF), false);
    private final ColorSetting othersColor = colorSetting("Others Color", new Color(0xFF0062), false);
    private final ColorSetting healthColor = colorSetting("Health Color", new Color(0x2FFF00), false, renderHealth::getValue);

    private final Supplier<RectRenderer> rectRendererSupplier = Suppliers.memoize(RectRenderer::create);

    @EventHandler
    private void onRender2D(Render2DEvent.Level event) {
        if (nullCheck() || mc.options.hideGui) return;

        RectRenderer rectRenderer = rectRendererSupplier.get();
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        float screenWidth = LuminRenderSystem.getScaledWidth();
        float screenHeight = LuminRenderSystem.getScaledHeight();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity livingEntity) || !shouldRender(livingEntity)) continue;

            Vector4d position = WorldToScreen.getEntityPositionsOn2D(livingEntity, partialTick);
            if (position == null || position.z < 0.0 || position.w < 0.0 || position.x > screenWidth || position.y > screenHeight)
                continue;

            float x = (float) position.x;
            float y = (float) position.y;
            float endX = (float) position.z;
            float endY = (float) position.w;

            if (renderBox.getValue()) {
                if (boxOutline.getValue()) {
                    Color black = Color.BLACK;
                    rectRenderer.addRect(x - 1.0f, y, 1.5f, endY - y + 0.5f, black);
                    rectRenderer.addRect(x - 1.0f, y - 0.5f, endX - x + 1.5f, 1.0f, black);
                    rectRenderer.addRect(endX - 1.0f, y, 1.5f, endY - y + 0.5f, black);
                    rectRenderer.addRect(x - 1.0f, endY - 1.0f, endX - x + 1.5f, 1.5f, black);
                }

                Color color = getEntityColor(livingEntity);
                drawSolidBox(rectRenderer, x, y, endX, endY, color);
            }

            if (renderHealth.getValue()) {
                drawHealthBar(rectRenderer, livingEntity, x, y, endY);
            }
        }

        rectRenderer.drawAndClear();
    }

    private boolean shouldRender(Entity entity) {
        if (mc.player == null) return false;
        if (!entity.isAlive() || entity.isSpectator()) return false;

        if (entity instanceof Player player) {
            if (entity == mc.player) return false;
            if (Managers.FRIEND.isFriend(player)) return friends.getValue();
            return players.getValue();
        }

        MobCategory category = entity.getType().getCategory();
        return switch (category) {
            case CREATURE, WATER_CREATURE, AXOLOTLS, UNDERGROUND_WATER_CREATURE -> creatures.getValue();
            case MONSTER -> monsters.getValue();
            case AMBIENT, WATER_AMBIENT -> ambients.getValue();
            default -> others.getValue();
        };
    }

    private Color getEntityColor(LivingEntity entity) {
        if (entity instanceof Player player) {
            if (Managers.FRIEND.isFriend(player)) return friendsColor.getValue();
            return playersColor.getValue();
        }

        MobCategory category = entity.getType().getCategory();
        return switch (category) {
            case CREATURE, WATER_CREATURE, AXOLOTLS, UNDERGROUND_WATER_CREATURE -> creaturesColor.getValue();
            case MONSTER -> monstersColor.getValue();
            case AMBIENT, WATER_AMBIENT -> ambientsColor.getValue();
            default -> othersColor.getValue();
        };
    }

    private void drawSolidBox(RectRenderer rectRenderer, float x, float y, float endX, float endY, Color color) {
        rectRenderer.addRect(x - 0.5f, y, 0.5f, endY - y, color);
        rectRenderer.addRect(x, endY - 0.5f, endX - x, 0.5f, color);
        rectRenderer.addRect(x - 0.5f, y, endX - x + 0.5f, 0.5f, color);
        rectRenderer.addRect(endX - 0.5f, y, 0.5f, endY - y, color);
    }

    private void drawHealthBar(RectRenderer rectRenderer, LivingEntity entity, float x, float y, float endY) {
        float height = endY - y;
        if (height <= 0.0f) return;

        float health = Managers.HEALTH.getHealth(entity);
        float maxHealth = Math.max(1.0f, entity.getMaxHealth() + Math.max(0.0f, entity.getAbsorptionAmount()));
        float healthRatio = Mth.clamp(health / maxHealth, 0.0f, 1.0f);
        float fillY = endY - height * healthRatio;

        float distanceScale = height / 45.0f;
        float width = healthBarWidth.getValue().floatValue() * distanceScale;
        float gap = 3.0f * distanceScale;
        float outlineWidth = healthBarOutline.getValue() ? healthBarOutlineWidth.getValue().floatValue() * distanceScale : 0.0f;
        float barX = x - gap - outlineWidth - width;

        if (healthBarOutline.getValue()) {
            rectRenderer.addRect(barX - outlineWidth, y - outlineWidth, width + outlineWidth * 2.0f, height + outlineWidth * 2.0f, Color.BLACK);
        } else {
            rectRenderer.addRect(barX, y, width, height, Color.BLACK);
        }

        rectRenderer.addRect(barX, fillY, width, endY - fillY, healthColor.getValue());
    }

}
