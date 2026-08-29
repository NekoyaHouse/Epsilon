package com.github.epsilon.modules.impl.render;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.Render2DEvent;
import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.graphics.schedulers.render2d.Render2DScheduler;
import com.github.epsilon.graphics.shaders.BlurShader;
import com.github.epsilon.graphics.text.IconChars;
import com.github.epsilon.graphics.text.StaticFontLoader;
import com.github.epsilon.managers.FriendManager;
import com.github.epsilon.managers.HealthManager;
import com.github.epsilon.managers.target.TargetManager;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.render.WorldToScreen;
import com.google.common.base.Suppliers;
import me.sofurry.ClInitNative;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

@ClInitNative
public class NameTags extends Module {

    public static final NameTags INSTANCE = new NameTags();

    private NameTags() {
        super("Name Tags", Category.RENDER);
    }

    private final DoubleSetting range = doubleSetting("Range", 128.0, 8.0, 256.0, 1.0);
    private final DoubleSetting scale = doubleSetting("Scale", 0.75, 0.4, 1.4, 0.05);
    private final DoubleSetting heightOffset = doubleSetting("Height Offset", 0.5, 0.0, 1.5, 0.05);
    private final BoolSetting showSelf = boolSetting("Show Self", false);
    private final BoolSetting friendStatus = boolSetting("Friend Status", true);
    private final BoolSetting teamStatus = boolSetting("Team Status", true);
    private final BoolSetting showHealth = boolSetting("Show Health", true);
    private final BoolSetting outline = boolSetting("Outline", true);
    private final ColorSetting nameColor = colorSetting("Name Color", new Color(247, 249, 252, 245));
    private final ColorSetting friendColor = colorSetting("Friend Color", new Color(105, 229, 166, 245), friendStatus::getValue);
    private final ColorSetting teamColor = colorSetting("Team Color", new Color(116, 210, 150, 245), teamStatus::getValue);
    private final ColorSetting healthColor = colorSetting("Health Color", new Color(215, 220, 228, 245), showHealth::getValue);
    private final ColorSetting heartColor = colorSetting("Heart Color", new Color(255, 91, 105, 250), showHealth::getValue);
    private final ColorSetting backgroundColor = colorSetting("Background Color", new Color(13, 16, 20, 165));
    private final ColorSetting outlineColor = colorSetting("Outline Color", new Color(255, 255, 255, 28), outline::getValue);
    private final BoolSetting backgroundBlur = boolSetting("Background Blur", true);
    private final IntSetting blurStrength = intSetting("Blur Strength", 6, 1, 16, 1, backgroundBlur::getValue);
    private final BoolSetting dropShadow = boolSetting("Drop Shadow", true);
    private final DoubleSetting shadowBlur = doubleSetting("Shadow Blur", 8.0, 2.0, 24.0, 0.5, dropShadow::getValue);
    private final ColorSetting shadowColor = colorSetting("Shadow Color", new Color(0, 0, 0, 135), dropShadow::getValue);

    private final Supplier<Render2DScheduler> schedulerSupplier = Suppliers.memoize(Render2DScheduler::new);

    @EventHandler
    private void onRender2D(Render2DEvent.Level event) {
        if (nullCheck()) return;

        Render2DScheduler scheduler = schedulerSupplier.get();
        scheduler.clear();

        List<TagLayout> tags = collectTags(scheduler.textMetrics());
        if (tags.isEmpty()) return;

        if (backgroundBlur.getValue()) {
            renderBlur(tags);
        }

        for (int index = 0; index < tags.size(); index++) {
            renderTag(scheduler.layer(index), tags.get(index));
        }

        scheduler.flushAndClear();
    }

    private List<TagLayout> collectTags(TextRenderer metrics) {
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        float screenWidth = LuminRenderSystem.getScaledWidth();
        float screenHeight = LuminRenderSystem.getScaledHeight();
        double maxDistanceSquared = Mth.square(range.getValue());
        List<TagLayout> tags = new ArrayList<>();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof Player player) || !shouldRender(player, maxDistanceSquared)) continue;

            Vec3 anchor = player.getPosition(partialTick).add(0.0, player.getBbHeight() + heightOffset.getValue(), 0.0);
            Vector3f projected = WorldToScreen.calcWorld2Screen(anchor);
            if (projected == null || !Float.isFinite(projected.x) || !Float.isFinite(projected.y)) continue;

            float perspectiveScale = WorldToScreen.calcScale(anchor);
            if (!Float.isFinite(perspectiveScale) || perspectiveScale <= 0.0f) continue;

            float renderScale = perspectiveScale * scale.getValue().floatValue();
            TagLayout tag = createLayout(metrics, player, projected.x, projected.y, renderScale);
            if (tag.x() + tag.width() < 0.0f || tag.y() + tag.height() < 0.0f
                    || tag.x() > screenWidth || tag.y() > screenHeight) {
                continue;
            }
            tags.add(tag);
        }

        tags.sort(Comparator.comparingDouble(TagLayout::distanceSquared).reversed());
        return tags;
    }

    private boolean shouldRender(Player player, double maxDistanceSquared) {
        if (!player.isAlive() || player.isRemoved()) return false;
        if (player.getName().getString().startsWith("CIT-")) return false;
        if (player == mc.player) {
            return showSelf.getValue() && !mc.options.getCameraType().isFirstPerson();
        }
        return mc.player.distanceToSqr(player) <= maxDistanceSquared;
    }

    private TagLayout createLayout(TextRenderer metrics, Player player, float centerX, float anchorY, float renderScale) {
        float paddingX = 5.0f * renderScale;
        float paddingY = 3.0f * renderScale;
        float segmentGap = 3.0f * renderScale;
        float iconGap = 2.25f * renderScale;
        float radius = 4.25f * renderScale;

        List<Segment> contents = new ArrayList<>(4);
        if (friendStatus.getValue() && FriendManager.INSTANCE.isFriend(player)) {
            contents.add(new Segment(IconChars.HANDSHAKE, "Friend", true, friendColor.getValue(), friendColor.getValue()));
        }
        if (teamStatus.getValue() && TargetManager.INSTANCE.isSameTeam(player)) {
            contents.add(new Segment(IconChars.GROUPS, "Team", true, teamColor.getValue(), teamColor.getValue()));
        }
        contents.add(new Segment(null, player.getName().getString(), true, nameColor.getValue(), nameColor.getValue()));
        if (showHealth.getValue()) {
            contents.add(new Segment(IconChars.FAVORITE, String.valueOf(HealthManager.INSTANCE.getHealth(player)), false, heartColor.getValue(), healthColor.getValue()));
        }

        List<MeasuredSegment> measured = new ArrayList<>(contents.size());
        float totalWidth = 0.0f;
        float tagHeight = 0.0f;
        for (Segment content : contents) {
            float textWidth = metrics.getWidth(content.text(), renderScale);
            float textHeight = metrics.getHeight(renderScale);
            float iconWidth = content.icon() == null ? 0.0f : metrics.getWidth(content.icon(), renderScale, StaticFontLoader.ICONS);
            float iconHeight = content.icon() == null ? 0.0f : metrics.getHeight(renderScale, StaticFontLoader.ICONS);
            float contentWidth = textWidth + iconWidth + (content.icon() == null ? 0.0f : iconGap);
            float width = contentWidth + paddingX * 2.0f;
            float height = Math.max(textHeight, iconHeight) + paddingY * 2.0f;
            measured.add(new MeasuredSegment(content, width, height, textWidth, textHeight, iconWidth, iconHeight));
            totalWidth += width;
            tagHeight = Math.max(tagHeight, height);
        }
        totalWidth += segmentGap * Math.max(0, measured.size() - 1);

        float startX = centerX - totalWidth * 0.5f;
        float startY = anchorY - tagHeight - 2.0f * renderScale;
        float currentX = startX;
        List<SegmentLayout> segments = new ArrayList<>(measured.size());
        for (MeasuredSegment segment : measured) {
            float y = startY + (tagHeight - segment.height()) * 0.5f;
            segments.add(new SegmentLayout(segment.content(), currentX, y, segment.width(), segment.height(), radius, paddingX, iconGap, segment.textWidth(), segment.textHeight(), segment.iconWidth(), segment.iconHeight()));
            currentX += segment.width() + segmentGap;
        }

        return new TagLayout(startX, startY, totalWidth, tagHeight, radius, renderScale, mc.player.distanceToSqr(player), List.copyOf(segments));
    }

    private void renderTag(Render2DScheduler.LayerHandle layer, TagLayout tag) {
        float[] segmentRects = segmentRects(tag.segments());
        float[] segmentRadii = segmentRadii(tag.segments());

        if (dropShadow.getValue()) {
            layer.addShadow(tag.x(), tag.y(), tag.width(), tag.height(), tag.radius(), shadowBlur.getValue().floatValue() * tag.scale(), shadowColor.getValue(), segmentRects, segmentRadii, tag.segments().size());
        }

        Color base = backgroundColor.getValue();
        Color top = mix(base, Color.WHITE, 0.065f);
        Color bottom = mix(base, Color.BLACK, 0.055f);
        for (SegmentLayout segment : tag.segments()) {
            layer.addRoundRectGradient(segment.x(), segment.y(), segment.width(), segment.height(), segment.radius(), segment.radius(), segment.radius(), segment.radius(), top, bottom, bottom, top);
            if (outline.getValue()) {
                layer.addOutline(segment.x(), segment.y(), segment.width(), segment.height(), segment.radius(), Math.max(0.45f, 0.55f * tag.scale()), outlineColor.getValue());
            }
            renderSegmentContent(layer, segment, tag.scale());
        }
    }

    private void renderSegmentContent(Render2DScheduler.LayerHandle layer, SegmentLayout segment, float renderScale) {
        Segment content = segment.content();
        float cursorX = segment.x() + segment.paddingX();
        float textY = segment.y() + (segment.height() - segment.textHeight()) * 0.5f;
        float iconY = segment.y() + (segment.height() - segment.iconHeight()) * 0.5f + renderScale;

        if (content.icon() != null && content.iconFirst()) {
            layer.addText(content.icon(), cursorX, iconY, renderScale, content.iconColor(), StaticFontLoader.ICONS);
            cursorX += segment.iconWidth() + segment.iconGap();
        }

        layer.addText(content.text(), cursorX, textY, renderScale, content.textColor());
        cursorX += segment.textWidth();

        if (content.icon() != null && !content.iconFirst()) {
            layer.addText(content.icon(), cursorX + segment.iconGap(), iconY, renderScale, content.iconColor(), StaticFontLoader.ICONS);
        }
    }

    private void renderBlur(List<TagLayout> tags) {
        List<SegmentLayout> segments = tags.stream().flatMap(tag -> tag.segments().stream()).toList();
        float strength = blurStrength.getValue().floatValue();

        int maxBlurSegments = 64;
        for (int start = 0; start < segments.size(); start += maxBlurSegments) {
            int count = Math.min(maxBlurSegments, segments.size() - start);
            float[] rects = new float[count * 4];
            float[] radii = new float[count];
            float minX = Float.POSITIVE_INFINITY;
            float minY = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY;
            float maxY = Float.NEGATIVE_INFINITY;

            for (int index = 0; index < count; index++) {
                SegmentLayout segment = segments.get(start + index);
                int offset = index * 4;
                rects[offset] = segment.x();
                rects[offset + 1] = segment.y();
                rects[offset + 2] = segment.width();
                rects[offset + 3] = segment.height();
                radii[index] = segment.radius();
                minX = Math.min(minX, segment.x());
                minY = Math.min(minY, segment.y());
                maxX = Math.max(maxX, segment.x() + segment.width());
                maxY = Math.max(maxY, segment.y() + segment.height());
            }

            BlurShader.INSTANCE.render(minX, minY, maxX - minX, maxY - minY, 0.0f, strength, rects, radii, count);
        }
    }

    private static float[] segmentRects(List<SegmentLayout> segments) {
        float[] values = new float[segments.size() * 4];
        for (int index = 0; index < segments.size(); index++) {
            SegmentLayout segment = segments.get(index);
            int offset = index * 4;
            values[offset] = segment.x();
            values[offset + 1] = segment.y();
            values[offset + 2] = segment.width();
            values[offset + 3] = segment.height();
        }
        return values;
    }

    private static float[] segmentRadii(List<SegmentLayout> segments) {
        float[] values = new float[segments.size()];
        for (int index = 0; index < segments.size(); index++) {
            values[index] = segments.get(index).radius();
        }
        return values;
    }

    private static Color mix(Color first, Color second, float amount) {
        float clamped = Mth.clamp(amount, 0.0f, 1.0f);
        float inverse = 1.0f - clamped;
        return new Color(
                Math.round(first.getRed() * inverse + second.getRed() * clamped),
                Math.round(first.getGreen() * inverse + second.getGreen() * clamped),
                Math.round(first.getBlue() * inverse + second.getBlue() * clamped),
                first.getAlpha()
        );
    }

    private record Segment(String icon, String text, boolean iconFirst, Color iconColor, Color textColor) {
    }

    private record MeasuredSegment(
            Segment content,
            float width,
            float height,
            float textWidth,
            float textHeight,
            float iconWidth,
            float iconHeight
    ) {
    }

    private record SegmentLayout(
            Segment content,
            float x,
            float y,
            float width,
            float height,
            float radius,
            float paddingX,
            float iconGap,
            float textWidth,
            float textHeight,
            float iconWidth,
            float iconHeight
    ) {
    }

    private record TagLayout(
            float x,
            float y,
            float width,
            float height,
            float radius,
            float scale,
            double distanceSquared,
            List<SegmentLayout> segments
    ) {
    }

}
