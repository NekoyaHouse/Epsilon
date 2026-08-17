package com.github.epsilon.modules.impl.render;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.Render2DEvent;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.renderers.RectRenderer;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.graphics.schedulers.render2d.Render2DScheduler;
import com.github.epsilon.graphics.shaders.BlurShader;
import com.github.epsilon.managers.FriendManager;
import com.github.epsilon.managers.HealthManager;
import com.github.epsilon.managers.target.TargetManager;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.utils.render.WorldToScreen;
import com.google.common.base.Suppliers;
import love.slmpc.ClInitNative;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

@ClInitNative
public class NameTags extends Module {

    private static final Color TAG_BACKGROUND = new Color(0, 0, 0, 130);
    private static final Color FRIEND_COLOR = new Color(20, 255, 20, 235);
    private static final Color NAME_COLOR = new Color(255, 255, 255, 235);
    private static final Color RANK_COLOR = new Color(85, 255, 85, 235);


    private static final int MAX_SEGMENTS = 64;

    private static final float NEW_TEXT_SCALE = 0.5f;

    private static final float NEW_TEXT_PADDING_LEFT = 6.0f;
    private static final float NEW_TEXT_PADDING_TOTAL = 16.0f;
    private static final float NEW_VERTICAL_PADDING = 6.0f;
    private static final float NEW_ACCENT_INSET = 3.0f;

    private static final double NEW_BOX_EXPAND = 0.15;

    public static final NameTags INSTANCE = new NameTags();

    private enum Mode {
        Old,
        New
    }

    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.New);
    private final DoubleSetting range = doubleSetting("Range", 64.0, 4.0, 128.0, 1.0);
    private final BoolSetting showSelf = boolSetting("Show Self", true);

    private final DoubleSetting scale = doubleSetting("Scale", 0.4, 0.1, 1.5, 0.1, () -> mode.is(Mode.Old));
    private final DoubleSetting heightOffset = doubleSetting("Height Offset", 0.40, -0.5, 1.0, 0.05, () -> mode.is(Mode.Old));
    private final ColorSetting backgroundColor = colorSetting("Background Color", TAG_BACKGROUND, () -> mode.is(Mode.Old));
    private final BoolSetting showEquipment = boolSetting("Show Equipment", true, () -> mode.is(Mode.Old));
    private final BoolSetting showHands = boolSetting("Show Hands", true, () -> mode.is(Mode.Old) && showEquipment.getValue());

    private final DoubleSetting tagScale = doubleSetting("Tag Scale", 1.0, 0.5, 3.0, 0.1, () -> mode.is(Mode.New));
    private final DoubleSetting tagOffsetY = doubleSetting("Tag Offset Y", 0.0, -50.0, 50.0, 1.0, () -> mode.is(Mode.New));
    private final BoolSetting showRank = boolSetting("Show Rank", true, () -> mode.is(Mode.New));
    private final BoolSetting showHealth = boolSetting("Show Health", true, () -> mode.is(Mode.New));
    private final ColorSetting tagBackgroundColor = colorSetting("Tag Background Color", new Color(0, 0, 0, 76), () -> mode.is(Mode.New));
    private final BoolSetting accentBar = boolSetting("Accent Bar", true, () -> mode.is(Mode.New));
    private final ColorSetting accentColor = colorSetting("Accent Color", new Color(255, 175, 63), () -> mode.is(Mode.New) && accentBar.getValue());
    private final BoolSetting tagBlur = boolSetting("Tag Blur", true, () -> mode.is(Mode.New));
    private final DoubleSetting tagBlurStrength = doubleSetting("Tag Blur Strength", 6.0, 1.0, 20.0, 0.5, () -> mode.is(Mode.New) && tagBlur.getValue());
    private final BoolSetting tagGlow = boolSetting("Tag Glow", true, () -> mode.is(Mode.New));
    private final DoubleSetting tagGlowRadius = doubleSetting("Tag Glow Radius", 8.0, 2.0, 24.0, 1.0, () -> mode.is(Mode.New) && tagGlow.getValue());
    private final ColorSetting tagGlowColor = colorSetting("Tag Glow Color", new Color(0, 0, 0, 200), () -> mode.is(Mode.New) && tagGlow.getValue());

    private final Supplier<TextRenderer> textRendererSupplier = Suppliers.memoize(TextRenderer::create);
    private final Supplier<RectRenderer> rectRendererSupplier = Suppliers.memoize(RectRenderer::create);
    private final Supplier<Render2DScheduler> schedulerSupplier = Suppliers.memoize(Render2DScheduler::new);

    private NameTags() {
        super("Name Tags", Category.RENDER);
    }

    private final List<TagDrawData> drawList = new ArrayList<>();
    private final List<CornerTagData> cornerDrawList = new ArrayList<>();

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (nullCheck()) return;

        drawList.clear();
        cornerDrawList.clear();

        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        double maxDistanceSq = range.getValue() * range.getValue();
        float screenWidth = LuminRenderSystem.getScaledWidth();
        float screenHeight = LuminRenderSystem.getScaledHeight();

        TextRenderer textRenderer = textRendererSupplier.get();
        boolean cornerMode = mode.is(Mode.New);

        for (Player target : mc.level.players()) {
            if (!target.isAlive() || target.isSpectator()) continue;
            if (mc.options.getCameraType().isFirstPerson() && target == mc.player) continue;
            if (target == mc.player && !showSelf.getValue()) continue;
            double distanceSq = mc.player.distanceToSqr(target);
            if (distanceSq > maxDistanceSq) continue;

            if (cornerMode) {
                collectCornerTag(target, partialTick, textRenderer, screenWidth, screenHeight);
            } else {
                collectTag(target, partialTick, textRenderer, screenWidth, screenHeight);
            }
        }
    }

    private void collectTag(Player target, float partialTick, TextRenderer textRenderer, float screenWidth, float screenHeight) {
        float textScale = scale.getValue().floatValue();
        Vec3 anchor = target.getPosition(partialTick)
                .add(0.0, heightOffset.getValue() + target.getEyeHeight(), 0.0);
        Vector3f projectedPosition = WorldToScreen.calcWorld2Screen(anchor);
        if (projectedPosition == null) return;

        float renderScale = textScale * WorldToScreen.calcScale(anchor);

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

        float centerX = projectedPosition.x;
        float x = centerX - boxWidth / 2.0f;
        float y = projectedPosition.y - totalHeight - 4.0f * renderScale;
        float itemLeft = centerX - itemRowWidth / 2.0f;
        float itemTop = y - (equipmentItems.isEmpty() ? 0.0f : itemRowGap + itemSize);
        float visualLeft = Math.min(x, itemLeft);
        float visualRight = Math.max(x + boxWidth, itemLeft + itemRowWidth);
        float visualTop = equipmentItems.isEmpty() ? y : itemTop;

        if (visualRight < 0.0f || y + boxHeight < 0.0f || visualLeft > screenWidth || visualTop > screenHeight) {
            return;
        }

        Color healthColor = healthColor(totalHealth);
        final var isFriend = FriendManager.INSTANCE.isFriend(nameText);

        drawList.add(new TagDrawData(equipmentItems, nameText, isFriend, healthText, healthColor, x, y, boxWidth, boxHeight, renderScale, padding, lineGap, itemScale, itemSize, itemGap, itemRowGap));
    }


    private void collectCornerTag(Player target, float partialTick, TextRenderer textRenderer, float screenWidth, float screenHeight) {
        Vec3 renderPosition = target.getPosition(partialTick);
        AABB box = target.getBoundingBox()
                .move(renderPosition.subtract(target.position()))
                .inflate(NEW_BOX_EXPAND);
        float[] bounds = WorldToScreen.calcScreenBounds(box);
        if (bounds == null) return;

        float tagScale = this.tagScale.getValue().floatValue();
        float textScale = NEW_TEXT_SCALE * tagScale;
        float textHeight = textRenderer.getHeight(textScale);
        float spacing = textRenderer.getWidth(" ", textScale);

        String nameText = target.getName().getString();
        boolean isFriend = FriendManager.INSTANCE.isFriend(nameText);

        List<TagSegment> segments = new ArrayList<>(3);
        if (showRank.getValue()) {
            String rankText = resolveRankText(target, isFriend);
            if (!rankText.isEmpty()) {
                segments.add(new TagSegment(rankText, RANK_COLOR));
            }
        }
        segments.add(new TagSegment(nameText, isFriend ? FRIEND_COLOR : NAME_COLOR));
        if (showHealth.getValue()) {
            float health = HealthManager.INSTANCE.getHealth(target);
            segments.add(new TagSegment(String.format(Locale.ROOT, "%.1f", health), healthColor(health)));
        }

        float textWidth = 0.0f;
        for (int i = 0; i < segments.size(); i++) {
            if (i > 0) textWidth += spacing;
            textWidth += textRenderer.getWidth(segments.get(i).text(), textScale);
        }

        float width = textWidth + NEW_TEXT_PADDING_TOTAL * tagScale;
        float height = textHeight + NEW_VERTICAL_PADDING * tagScale;
        float x = bounds[0];
        float y = bounds[1] + tagOffsetY.getValue().floatValue() * tagScale;

        if (x + width < 0.0f || y + height < 0.0f || x > screenWidth || y > screenHeight) {
            return;
        }

        cornerDrawList.add(new CornerTagData(x, y, width, height, tagScale, textScale, textHeight, spacing, segments));
    }

    @EventHandler
    private void renderTagList(Render2DEvent.Level event) {
        if (mode.is(Mode.New)) {
            renderCornerTags();
            return;
        }

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

    private void renderCornerTags() {
        if (cornerDrawList.isEmpty()) return;

        TextRenderer textRenderer = textRendererSupplier.get();
        boolean blur = tagBlur.getValue();
        boolean glow = tagGlow.getValue();
        List<SegmentBatch> batches = blur || glow ? buildSegmentBatches(cornerDrawList) : List.of();


        if (blur) {
            float strength = tagBlurStrength.getValue().floatValue();
            for (SegmentBatch batch : batches) {
                BlurShader.INSTANCE.render(
                        batch.x(), batch.y(), batch.width(), batch.height(),
                        0.0f, strength,
                        batch.rects(), batch.radii(), batch.count()
                );
            }
        }

        Render2DScheduler renderScheduler = schedulerSupplier.get();
        renderScheduler.clear();
        Render2DScheduler.LayerHandle layer = renderScheduler.layer(0);

        if (glow) {
            float glowRadius = tagGlowRadius.getValue().floatValue();
            Color glowColor = tagGlowColor.getValue();
            for (SegmentBatch batch : batches) {
                layer.addShadow(
                        batch.x(), batch.y(), batch.width(), batch.height(),
                        0.0f, glowRadius, glowColor,
                        batch.rects(), batch.radii(), batch.count()
                );
            }
        }

        boolean drawAccent = accentBar.getValue();
        Color accent = accentColor.getValue();
        Color background = tagBackgroundColor.getValue();

        for (CornerTagData tag : cornerDrawList) {
            layer.addRect(tag.x(), tag.y(), tag.width(), tag.height(), background);

            if (drawAccent) {
                float inset = NEW_ACCENT_INSET * tag.scale();
                float barHeight = Math.max(1.0f, tag.height() - inset * 2.0f);
                layer.addRect(tag.x(), tag.y() + inset, tag.scale(), barHeight, accent);
            }

            float textX = tag.x() + NEW_TEXT_PADDING_LEFT * tag.scale();
            float textY = tag.y() + (tag.height() - tag.textHeight()) * 0.5f;
            for (TagSegment segment : tag.segments()) {
                layer.addText(segment.text(), textX, textY, tag.textScale(), segment.color());
                textX += textRenderer.getWidth(segment.text(), tag.textScale()) + tag.spacing();
            }
        }

        renderScheduler.flushAndClear();
        cornerDrawList.clear();
    }

    private List<SegmentBatch> buildSegmentBatches(List<CornerTagData> tags) {
        List<SegmentBatch> batches = new ArrayList<>((tags.size() + MAX_SEGMENTS - 1) / MAX_SEGMENTS);
        for (int offset = 0; offset < tags.size(); offset += MAX_SEGMENTS) {
            int count = Math.min(MAX_SEGMENTS, tags.size() - offset);
            float[] rects = new float[count * 4];
            float[] radii = new float[count];
            float minX = Float.POSITIVE_INFINITY;
            float minY = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY;
            float maxY = Float.NEGATIVE_INFINITY;

            for (int i = 0; i < count; i++) {
                CornerTagData tag = tags.get(offset + i);
                int index = i * 4;
                rects[index] = tag.x();
                rects[index + 1] = tag.y();
                rects[index + 2] = tag.width();
                rects[index + 3] = tag.height();

                minX = Math.min(minX, tag.x());
                minY = Math.min(minY, tag.y());
                maxX = Math.max(maxX, tag.x() + tag.width());
                maxY = Math.max(maxY, tag.y() + tag.height());
            }

            if (maxX > minX && maxY > minY) {
                batches.add(new SegmentBatch(minX, minY, maxX - minX, maxY - minY, rects, radii, count));
            }
        }
        return batches;
    }

    private String resolveRankText(Player target, boolean isFriend) {
        if (target == mc.player) return "[You]";
        if (isFriend) return "[Friend]";
        if (TargetManager.INSTANCE.isSameTeam(target)) return "[Team]";
        return "";
    }

    private Color healthColor(float health) {
        return health < 10.0f ? new Color(255, 214, 64, 240) : new Color(120, 255, 120, 240);
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

    private record CornerTagData(
            float x,
            float y,
            float width,
            float height,
            float scale,
            float textScale,
            float textHeight,
            float spacing,
            List<TagSegment> segments
    ) {
    }

    private record TagSegment(String text, Color color) {
    }

    private record SegmentBatch(
            float x, float y, float width, float height,
            float[] rects, float[] radii, int count
    ) {
    }

}
