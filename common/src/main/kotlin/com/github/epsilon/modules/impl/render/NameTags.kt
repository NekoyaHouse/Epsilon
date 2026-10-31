package com.github.epsilon.modules.impl.render

import com.github.epsilon.events.api.handler
import com.github.epsilon.events.impl.Render2DEvent
import com.github.epsilon.graphics.LuminRenderSystem
import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.graphics.schedulers.render2d.Render2DScheduler
import com.github.epsilon.graphics.shaders.BlurShader
import com.github.epsilon.graphics.text.IconChars
import com.github.epsilon.graphics.text.StaticFontLoader
import com.github.epsilon.managers.FriendManager
import com.github.epsilon.managers.HealthManager
import com.github.epsilon.managers.target.TargetManager
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.utils.render.WorldToScreen
import com.github.epsilon.utils.safety.runSafe
import com.google.common.base.Suppliers
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.Player
import java.awt.Color
import java.util.function.Supplier
import kotlin.math.roundToInt

object NameTags : Module("Name Tags", Category.RENDER) {

    private val range by doubleSetting("Range", 128.0, 8.0..256.0, 1.0)
    private val scale by doubleSetting("Scale", 0.75, 0.4..1.4, 0.05)
    private val limitMinScale by boolSetting("Limit Min Scale", false)
    private val minScale by doubleSetting(
        "Min Scale", 0.5, 0.1..1.4, 0.05,
        { limitMinScale }
    )
    private val heightOffset by doubleSetting("Height Offset", 0.5, 0.0..1.5, 0.05)
    private val showSelf by boolSetting("Show Self", false)
    private val friendStatus by boolSetting("Friend Status", true)
    private val teamStatus by boolSetting("Team Status", true)
    private val showHealth by boolSetting("Show Health", true)
    private val outline by boolSetting("Outline", true)
    private val nameColor by colorSetting("Name Color", Color(247, 249, 252, 245))
    private val friendColor by colorSetting(
        "Friend Color", Color(105, 229, 166, 245),
        true,
        { friendStatus }
    )
    private val teamColor by colorSetting(
        "Team Color", Color(116, 210, 150, 245),
        true,
        { teamStatus }
    )
    private val healthColor by colorSetting(
        "Health Color", Color(215, 220, 228, 245),
        true,
        { showHealth }
    )
    private val heartColor by colorSetting(
        "Heart Color", Color(255, 91, 105, 250),
        true,
        { showHealth }
    )
    private val backgroundColor by colorSetting("Background Color", Color(13, 16, 20, 165))
    private val outlineColor by colorSetting(
        "Outline Color", Color(255, 255, 255, 28),
        true,
        { outline }
    )
    private val backgroundBlur by boolSetting("Background Blur", true)
    private val blurStrength by intSetting(
        "Blur Strength", 6, 1..16, 1,
        { backgroundBlur }
    )
    private val dropShadow by boolSetting("Drop Shadow", true)
    private val shadowBlur by doubleSetting(
        "Shadow Blur", 8.0, 2.0..24.0, 0.5,
        { dropShadow }
    )
    private val shadowColor by colorSetting(
        "Shadow Color", Color(0, 0, 0, 135),
        true,
        { dropShadow }
    )

    private val schedulerSupplier: Supplier<Render2DScheduler> = Suppliers.memoize { Render2DScheduler() }

    init {
        handler<Render2DEvent.Level> {
            runSafe {
                val scheduler = schedulerSupplier.get()
                scheduler.clear()

                val tags = collectTags(scheduler.textMetrics())
                if (tags.isEmpty()) return@runSafe

                if (backgroundBlur) {
                    renderBlur(tags)
                }

                for (index in tags.indices) {
                    renderTag(scheduler.layer(index), tags[index])
                }

                scheduler.flushAndClear()
            }
        }
    }

    private fun collectTags(metrics: TextRenderer): MutableList<TagLayout> {
        val partialTick = mc.deltaTracker.getGameTimeDeltaPartialTick(true)
        val screenWidth = LuminRenderSystem.getScaledWidth()
        val screenHeight = LuminRenderSystem.getScaledHeight()
        val maxDistanceSquared = Mth.square(range)
        val tags = ArrayList<TagLayout>()
        val level = mc.level ?: return tags

        for (entity in level.entitiesForRendering()) {
            if (entity !is Player || !shouldRender(entity, maxDistanceSquared)) continue

            val anchor = entity.getPosition(partialTick)
                .add(0.0, entity.bbHeight + heightOffset, 0.0)
            val projected = WorldToScreen.calcWorld2Screen(anchor) ?: continue
            if (!projected.x.isFinite() || !projected.y.isFinite()) continue

            val perspectiveScale = WorldToScreen.calcScale(anchor)
            if (!perspectiveScale.isFinite() || perspectiveScale <= 0.0f) continue

            var renderScale = perspectiveScale * scale.toFloat()
            // 选择性限制最小尺寸：避免远距离或低视野缩放下标签过小
            if (limitMinScale) {
                renderScale = maxOf(renderScale, minScale.toFloat())
            }
            val tag = createLayout(metrics, entity, projected.x, projected.y, renderScale)
            if (tag.x + tag.width < 0.0f || tag.y + tag.height < 0.0f ||
                tag.x > screenWidth || tag.y > screenHeight
            ) {
                continue
            }
            tags.add(tag)
        }

        tags.sortWith(compareByDescending<TagLayout> { it.distanceSquared })
        return tags
    }

    private fun shouldRender(player: Player, maxDistanceSquared: Double): Boolean {
        if (!player.isAlive || player.isRemoved) return false
        if (player.name.string.startsWith("CIT-")) return false
        val self = mc.player ?: return false
        if (player === self) {
            return showSelf && !mc.options.cameraType.isFirstPerson
        }
        return self.distanceToSqr(player) <= maxDistanceSquared
    }

    private fun createLayout(
        metrics: TextRenderer, player: Player, centerX: Float, anchorY: Float, renderScale: Float,
    ): TagLayout {
        val paddingX = 5.0f * renderScale
        val paddingY = 3.0f * renderScale
        val segmentGap = 3.0f * renderScale
        val iconGap = 2.25f * renderScale
        val radius = 4.25f * renderScale

        val contents = ArrayList<Segment>(4)
        if (friendStatus && FriendManager.isFriend(player)) {
            contents.add(Segment(IconChars.HANDSHAKE, "Friend", true, friendColor, friendColor))
        }
        if (teamStatus && TargetManager.isSameTeam(player)) {
            contents.add(Segment(IconChars.GROUPS, "Team", true, teamColor, teamColor))
        }
        contents.add(Segment(null, player.name.string, true, nameColor, nameColor))
        if (showHealth) {
            contents.add(
                Segment(
                    IconChars.FAVORITE, HealthManager.getHealth(player).toString(), false,
                    heartColor, healthColor
                )
            )
        }

        val measured = ArrayList<MeasuredSegment>(contents.size)
        var totalWidth = 0.0f
        var tagHeight = 0.0f
        for (content in contents) {
            val textWidth = metrics.getWidth(content.text, renderScale)
            val textHeight = metrics.getHeight(renderScale)
            val iconWidth =
                if (content.icon == null) 0.0f else metrics.getWidth(content.icon, renderScale, StaticFontLoader.ICONS)
            val iconHeight = if (content.icon == null) 0.0f else metrics.getHeight(renderScale, StaticFontLoader.ICONS)
            val contentWidth = textWidth + iconWidth + (if (content.icon == null) 0.0f else iconGap)
            val width = contentWidth + paddingX * 2.0f
            val height = maxOf(textHeight, iconHeight) + paddingY * 2.0f
            measured.add(MeasuredSegment(content, width, height, textWidth, textHeight, iconWidth, iconHeight))
            totalWidth += width
            tagHeight = maxOf(tagHeight, height)
        }
        totalWidth += segmentGap * maxOf(0, measured.size - 1)

        val startX = centerX - totalWidth * 0.5f
        val startY = anchorY - tagHeight - 2.0f * renderScale
        var currentX = startX
        val segments = ArrayList<SegmentLayout>(measured.size)
        for (segment in measured) {
            val y = startY + (tagHeight - segment.height) * 0.5f
            segments.add(
                SegmentLayout(
                    segment.content, currentX, y, segment.width, segment.height, radius, paddingX,
                    iconGap, segment.textWidth, segment.textHeight, segment.iconWidth, segment.iconHeight
                )
            )
            currentX += segment.width + segmentGap
        }

        return TagLayout(
            startX, startY, totalWidth, tagHeight, radius, renderScale,
            mc.player!!.distanceToSqr(player), segments
        )
    }

    private fun renderTag(layer: Render2DScheduler.LayerHandle, tag: TagLayout) {
        val segmentRects = segmentRects(tag.segments)
        val segmentRadii = segmentRadii(tag.segments)

        if (dropShadow) {
            layer.addShadow(
                tag.x, tag.y, tag.width, tag.height, tag.radius,
                shadowBlur.toFloat() * tag.scale, shadowColor,
                segmentRects, segmentRadii, tag.segments.size
            )
        }

        val base = backgroundColor
        val top = mix(base, Color.WHITE, 0.065f)
        val bottom = mix(base, Color.BLACK, 0.055f)
        for (segment in tag.segments) {
            layer.addRoundRectGradient(
                segment.x, segment.y, segment.width, segment.height,
                segment.radius, segment.radius, segment.radius, segment.radius,
                top, bottom, bottom, top
            )
            if (outline) {
                layer.addOutline(
                    segment.x, segment.y, segment.width, segment.height, segment.radius,
                    maxOf(0.45f, 0.55f * tag.scale), outlineColor
                )
            }
            renderSegmentContent(layer, segment, tag.scale)
        }
    }

    private fun renderSegmentContent(layer: Render2DScheduler.LayerHandle, segment: SegmentLayout, renderScale: Float) {
        val content = segment.content
        var cursorX = segment.x + segment.paddingX
        val textY = segment.y + (segment.height - segment.textHeight) * 0.5f
        val iconY = segment.y + (segment.height - segment.iconHeight) * 0.5f + renderScale

        if (content.icon != null && content.iconFirst) {
            layer.addText(content.icon, cursorX, iconY, renderScale, content.iconColor, StaticFontLoader.ICONS)
            cursorX += segment.iconWidth + segment.iconGap
        }

        layer.addText(content.text, cursorX, textY, renderScale, content.textColor)
        cursorX += segment.textWidth

        if (content.icon != null && !content.iconFirst) {
            layer.addText(
                content.icon,
                cursorX + segment.iconGap,
                iconY,
                renderScale,
                content.iconColor,
                StaticFontLoader.ICONS
            )
        }
    }

    private fun renderBlur(tags: MutableList<TagLayout>) {
        val segments = tags.flatMap { it.segments }
        val strength = blurStrength.toFloat()

        val maxBlurSegments = 64
        for (start in segments.indices step maxBlurSegments) {
            val count = minOf(maxBlurSegments, segments.size - start)
            val rects = FloatArray(count * 4)
            val radii = FloatArray(count)
            var minX = Float.POSITIVE_INFINITY
            var minY = Float.POSITIVE_INFINITY
            var maxX = Float.NEGATIVE_INFINITY
            var maxY = Float.NEGATIVE_INFINITY

            for (index in 0 until count) {
                val segment = segments[start + index]
                val offset = index * 4
                rects[offset] = segment.x
                rects[offset + 1] = segment.y
                rects[offset + 2] = segment.width
                rects[offset + 3] = segment.height
                radii[index] = segment.radius
                minX = minOf(minX, segment.x)
                minY = minOf(minY, segment.y)
                maxX = maxOf(maxX, segment.x + segment.width)
                maxY = maxOf(maxY, segment.y + segment.height)
            }

            BlurShader.render(minX, minY, maxX - minX, maxY - minY, 0.0f, strength, rects, radii, count)
        }
    }

    private fun segmentRects(segments: List<SegmentLayout>): FloatArray {
        val values = FloatArray(segments.size * 4)
        for (index in segments.indices) {
            val segment = segments[index]
            val offset = index * 4
            values[offset] = segment.x
            values[offset + 1] = segment.y
            values[offset + 2] = segment.width
            values[offset + 3] = segment.height
        }
        return values
    }

    private fun segmentRadii(segments: List<SegmentLayout>): FloatArray {
        val values = FloatArray(segments.size)
        for (index in segments.indices) {
            values[index] = segments[index].radius
        }
        return values
    }

    private fun mix(first: Color, second: Color, amount: Float): Color {
        val clamped = amount.coerceIn(0.0f, 1.0f)
        val inverse = 1.0f - clamped
        return Color(
            (first.red * inverse + second.red * clamped).roundToInt(),
            (first.green * inverse + second.green * clamped).roundToInt(),
            (first.blue * inverse + second.blue * clamped).roundToInt(),
            first.alpha
        )
    }

    private data class Segment(
        val icon: String?,
        val text: String,
        val iconFirst: Boolean,
        val iconColor: Color,
        val textColor: Color,
    )

    private data class MeasuredSegment(
        val content: Segment,
        val width: Float,
        val height: Float,
        val textWidth: Float,
        val textHeight: Float,
        val iconWidth: Float,
        val iconHeight: Float,
    )

    private data class SegmentLayout(
        val content: Segment,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val radius: Float,
        val paddingX: Float,
        val iconGap: Float,
        val textWidth: Float,
        val textHeight: Float,
        val iconWidth: Float,
        val iconHeight: Float,
    )

    private data class TagLayout(
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val radius: Float,
        val scale: Float,
        val distanceSquared: Double,
        val segments: List<SegmentLayout>,
    )

}
