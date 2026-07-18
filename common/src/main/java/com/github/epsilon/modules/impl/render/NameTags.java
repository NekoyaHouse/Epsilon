package com.github.epsilon.modules.impl.render;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.Render2DEvent;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.renderers.RectRenderer;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.utils.render.WorldToScreen;
import com.google.common.base.Suppliers;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector4d;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

public class NameTags extends Module {

    private static final Color TAG_BACKGROUND = new Color(0, 0, 0, 130);
    private static final Color FRIEND_COLOR = new Color(20, 255, 20, 235);
    private static final Color NAME_COLOR = new Color(255, 255, 255, 235);

    public static final NameTags INSTANCE = new NameTags();

    private final DoubleSetting range = doubleSetting("Range", 64.0, 4.0, 128.0, 1.0);
    private final DoubleSetting scale = doubleSetting("Scale", 0.4, 0.1, 1.5, 0.1);
    private final DoubleSetting heightOffset = doubleSetting("Height Offset", 0.15, -0.5, 1.0, 0.05);
    private final ColorSetting backgroundColor = colorSetting("Background Color", TAG_BACKGROUND);
    public final BoolSetting vanillaNameTags = boolSetting("Vanilla Name Tags", false);
    private final BoolSetting showEquipment = boolSetting("Show Equipment", true);
    private final BoolSetting showHands = boolSetting("Show Hands", true, showEquipment::getValue);
    private final BoolSetting showSelf = boolSetting("Show Self", true);

    private final Supplier<TextRenderer> textRendererSupplier = Suppliers.memoize(TextRenderer::create);
    private final Supplier<RectRenderer> rectRendererSupplier = Suppliers.memoize(RectRenderer::create);

    private NameTags() {
        super("Name Tags", Category.RENDER);
    }

    private final List<TagDrawData> drawList = new ArrayList<>();

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (nullCheck()) return;

        drawList.clear();

        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        double maxDistanceSq = range.getValue() * range.getValue();
        float textScale = scale.getValue().floatValue();

        TextRenderer textRenderer = textRendererSupplier.get();

        for (Player target : mc.level.players()) {
            if (!target.isAlive() || target.isSpectator()) continue;
            if (mc.options.getCameraType().isFirstPerson() && target == mc.player) continue;
            if (target == mc.player && !showSelf.getValue()) continue;
            double distanceSq = mc.player.distanceToSqr(target);
            if (distanceSq > maxDistanceSq) continue;

            Vector4d projected = WorldToScreen.getEntityPositionsOn2D(target, partialTick);
            if (projected == null) continue;

            float screenWidth = LuminRenderSystem.getScaledWidth();
            float screenHeight = LuminRenderSystem.getScaledHeight();
            if (projected.z < 0 || projected.w < 0 || projected.x > screenWidth || projected.y > screenHeight) continue;

            float projectedHeight = (float) Math.max(1.0, projected.w - projected.y);
            float renderScale = getPerspectiveScale(textScale, projectedHeight);

            List<ItemStack> equipmentItems = buildEquipmentItems(target);
            String nameText = target.getName().getString();
            float totalHealth = target.getHealth() + target.getAbsorptionAmount();
            String healthText = String.format(Locale.ROOT, "[%.1f HP]", totalHealth);

            float padding = 3.0f * renderScale;
            float lineGap = 2.0f * renderScale;
            float lineHeight = textRenderer.getHeight(renderScale);
            float itemScale = getItemScale(renderScale);
            float itemSize = 16.0f * itemScale;
            float itemGap = 2.0f * renderScale;
            float itemRowWidth = equipmentItems.isEmpty() ? 0.0f : equipmentItems.size() * itemSize + Math.max(0, equipmentItems.size() - 1) * itemGap;
            float headerWidth = textRenderer.getWidth(nameText, renderScale)
                    + textRenderer.getWidth(" ", renderScale)
                    + textRenderer.getWidth(healthText, renderScale);

            float boxWidth = headerWidth + padding * 2.0f;
            float boxHeight = padding * 2.0f + lineHeight;
            float itemRowGap = equipmentItems.isEmpty() ? 0.0f : (3.0f * renderScale);
            float totalHeight = boxHeight + (equipmentItems.isEmpty() ? 0.0f : itemRowGap + itemSize);

            final var currentPosition = WorldToScreen.interpolate(target, partialTick);

            final var projectedPosition = WorldToScreen.getWorldPositionToScreen(currentPosition.add(0.0f, heightOffset.getValue() + target.getEyeHeight(), 0.0f));
            if (projectedPosition.z > 1.0f || projectedPosition.z < 0.0f) continue;

            float guiScale = (float) LuminRenderSystem.getGuiScale();

            float centerX = projectedPosition.x / guiScale;
            float x = centerX - boxWidth / 2.0f;
            float y = projectedPosition.y / guiScale - totalHeight - 4.0f * renderScale;
            float itemLeft = centerX - itemRowWidth / 2.0f;
            float itemTop = y - (equipmentItems.isEmpty() ? 0.0f : itemRowGap + itemSize);
            float visualLeft = Math.min(x, itemLeft);
            float visualRight = Math.max(x + boxWidth, itemLeft + itemRowWidth);
            float visualTop = equipmentItems.isEmpty() ? y : itemTop;

            if (visualRight < 0.0f || y + boxHeight < 0.0f || visualLeft > screenWidth || visualTop > screenHeight)
                continue;

            Color healthColor = totalHealth < 10.0f ? new Color(255, 214, 64, 240) : new Color(120, 255, 120, 240);
            final var isFriend = Managers.FRIEND.isFriend(nameText);

            drawList.add(new TagDrawData(equipmentItems, nameText, isFriend, healthText, healthColor, x, y, boxWidth, boxHeight, renderScale, padding, lineGap, itemScale, itemSize, itemGap, itemRowGap));
        }

    }

    @EventHandler
    private void renderTagList(Render2DEvent.Level event) {
        GuiGraphicsExtractor graphics = event.getGuiGraphics();
        RectRenderer rectRenderer = rectRendererSupplier.get();
        TextRenderer textRenderer = textRendererSupplier.get();

        for (TagDrawData data : drawList) {
            rectRenderer.addRect(data.x, data.y, data.width, data.height, backgroundColor.getValue());

            float headerY = data.y + data.padding;

            float nameWidth = textRenderer.getWidth(data.nameText, data.scale);
            float spaceWidth = textRenderer.getWidth(" ", data.scale);
            float healthWidth = textRenderer.getWidth(data.healthText, data.scale);
            float headerWidth = nameWidth + spaceWidth + healthWidth;
            float headerX = data.x + (data.width - headerWidth) * 0.5f;

            textRenderer.addText(data.nameText, headerX, headerY, data.scale, data.isFriend ? FRIEND_COLOR : NAME_COLOR);
            textRenderer.addText(data.healthText, headerX + nameWidth + spaceWidth, headerY, data.scale, data.healthColor);

            if (!data.equipmentItems.isEmpty()) {
                float itemRowWidth = data.equipmentItems.size() * data.itemSize + Math.max(0, data.equipmentItems.size() - 1) * data.itemGap;
                float itemX = data.x + data.width * 0.5f - itemRowWidth * 0.5f;
                float itemY = data.y - data.itemRowGap - data.itemSize;
                for (ItemStack stack : data.equipmentItems) {
                    drawItem(graphics, stack, itemX, itemY, data.itemScale);
                    itemX += data.itemSize + data.itemGap;
                }
            }
        }

        rectRenderer.drawAndClear();
        textRenderer.drawAndClear();

        drawList.clear();
    }

    private List<ItemStack> buildEquipmentItems(Player player) {
        List<ItemStack> items = new ArrayList<>();
        if (!showEquipment.getValue()) {
            return items;
        }

        if (showHands.getValue()) {
            appendItem(items, player.getOffhandItem());
        }

        appendItem(items, player.getItemBySlot(EquipmentSlot.HEAD));
        appendItem(items, player.getItemBySlot(EquipmentSlot.CHEST));
        appendItem(items, player.getItemBySlot(EquipmentSlot.LEGS));
        appendItem(items, player.getItemBySlot(EquipmentSlot.FEET));
        if (showHands.getValue()) {
            appendItem(items, player.getMainHandItem());
        }
        return items;
    }

    private float getPerspectiveScale(float baseScale, float projectedHeight) {
        float perspectiveFactor = Mth.clamp(projectedHeight / 36.0f, 0.55f, 2.2f);
        return baseScale * perspectiveFactor;
    }

    private float getItemScale(float renderScale) {
        return renderScale * 1.5f;
    }

    private void appendItem(List<ItemStack> items, ItemStack stack) {
        if (!stack.isEmpty()) {
            items.add(stack.copy());
        }
    }

    private void drawItem(GuiGraphicsExtractor graphics, ItemStack stack, float x, float y, float scale) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        graphics.item(stack, 0, 0);
        graphics.itemDecorations(mc.font, stack, 0, 0);
        graphics.pose().popMatrix();
    }

    private record TagDrawData(
            List<ItemStack> equipmentItems,
            String nameText,
            boolean isFriend,
            String healthText,
            Color healthColor,
            float x,
            float y,
            float width,
            float height,
            float scale,
            float padding,
            float lineGap,
            float itemScale,
            float itemSize,
            float itemGap,
            float itemRowGap
    ) {
    }

}
