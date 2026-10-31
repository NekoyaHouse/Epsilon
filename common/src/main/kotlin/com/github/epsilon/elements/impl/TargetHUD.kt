package com.github.epsilon.elements.impl

import com.github.epsilon.elements.HudModule
import com.github.epsilon.graphics.LuminTexture
import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.graphics.shaders.BlurShader
import com.github.epsilon.gui.hudeditor.HudEditorScreen
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.managers.HealthManager
import com.github.epsilon.modules.impl.combat.KillAura
import com.github.epsilon.utils.render.animation.Easing
import com.github.epsilon.utils.safety.NonNullContext
import com.google.common.base.Suppliers
import net.minecraft.client.DeltaTracker
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.renderer.texture.AbstractTexture
import net.minecraft.util.Mth
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import java.awt.Color
import java.util.*
import java.util.function.Supplier
import kotlin.math.roundToInt

object TargetHUD : HudModule("Target HUD", 0f, 0f, 180f, 80f) {

    private enum class Style {
        Modern,
        Akrien,
    }

    private val style by enumSetting("Style", Style.Modern)
    private val scale by doubleSetting("Scale", 0.9, 0.5, 2.0, 0.1)
    private val panelWidthSetting by doubleSetting("Width", 150.0, 100.0, 300.0, 1.0)
    private val panelHeightSetting by doubleSetting("Height", 52.0, 30.0, 100.0, 1.0)
    private val radius by doubleSetting("Radius", 5.0, 0.0, 20.0, 1.0)
    private val blurStrength by doubleSetting("Blur Strength", 5.0, 1.0, 20.0, 1.0)
    private val healthBarHeight by doubleSetting("Bar Height", 5.0, 2.0, 20.0, 1.0)
    private val healthBarRadius by doubleSetting("Bar Radius", 2.0, 0.0, 15.0, 1.0)
    private val nameSize by doubleSetting("Name Size", 10.5, 8.0, 18.0, 0.5)
    private val delayBar by boolSetting("Delay Bar", true)
    private val delayWait by boolSetting("Delay Wait", true, dependency = { delayBar })
    private val delayTime by doubleSetting(
        "Delay Time", 250.0, 0.0, 500.0, 50.0,
        dependency = { delayBar && delayWait }
    )
    private val delaySpeed by doubleSetting("Delay Speed", 2.0, 0.1, 10.0, 0.1, dependency = { delayBar })
    private val barOutline by boolSetting("Bar Outline", true)
    private val barOutlineWidth by doubleSetting(
        "Bar Outline Width", 1.0, 0.5, 5.0, 0.5,
        dependency = { barOutline }
    )
    private val backgroundColor by colorSetting("Background Color", Color(15, 15, 15, 50))
    private val barBackgroundColor by colorSetting("Bar Background Color", Color(255, 255, 255, 55))
    private val barFillColor by colorSetting("Bar Fill Color", Color(255, 236, 248, 235))
    private val delayBarColor by colorSetting("Delay Bar Color", Color(190, 190, 190, 100), dependency = { delayBar })
    private val barOutlineColor by colorSetting("Bar Outline Color", Color(255, 255, 255, 85), dependency = { barOutline })
    private val textColor by colorSetting("Text Color", Color(255, 255, 255, 235))
    private val drawShadow by boolSetting("Drop Shadow", true)
    private val shadowBlur by doubleSetting("Shadow Blur", 10.0, 2.0, 32.0, 1.0, dependency = { drawShadow })
    private val shadowColor by colorSetting("Shadow Color", Color(255, 255, 255, 110), dependency = { drawShadow })

    private var lastTargetId: Int = Int.MIN_VALUE
    private var displayedHealth: Float = 0.0f
    private var delayedHealth: Float = 0.0f
    private var lastKnownHealth: Float = -1.0f
    private var lastKnownMaxHealth: Float = 1.0f
    private var lastDamageTimeMs: Long = 0L
    private var renderedTarget: LivingEntity? = null
    private var visibilityProgress: Float = 0.0f
    private var lastVisibilityUpdateMs: Long = 0L

    private val textRendererSupplier: Supplier<TextRenderer> = Suppliers.memoize(com.google.common.base.Supplier { TextRenderer.create() })

    override fun render(deltaTracker: DeltaTracker) {
        if (style == Style.Akrien) {
            renderAkrien(deltaTracker)
            return
        }

        val panelScale = scale.toFloat()
        val panelWidth = panelWidthSetting.toFloat() * panelScale
        val panelHeight = panelHeightSetting.toFloat() * panelScale
        setBounds(panelWidth, panelHeight)

        val frameTime = deltaTracker.gameTimeDeltaTicks / 20.0f
        val target = updateRenderedTarget(resolveTarget())
        val animationScale = Easing.EASE_OUT_SINE.function.apply(Mth.clamp(visibilityProgress, 0.0f, 1.0f))
        if (target == null || animationScale <= 0.01f) return

        val textRenderer = textRendererSupplier.get()
        val scope: UiTree.Scope = renderScope()

        val liveTarget = resolveTarget()
        val maxHealth: Float
        val healthPercent: Float
        if (liveTarget === target) {
            val health = HealthManager.getHealth(target)
            maxHealth = maxOf(1.0f, target.maxHealth + maxOf(0.0f, target.absorptionAmount))
            lastKnownMaxHealth = maxHealth
            healthPercent = updateAnimatedHealth(target, health, maxHealth, frameTime)
        } else {
            maxHealth = maxOf(1.0f, lastKnownMaxHealth)
            displayedHealth = Mth.clamp(displayedHealth, 0.0f, maxHealth)
            delayedHealth = Mth.clamp(delayedHealth, 0.0f, maxHealth)
            healthPercent = Mth.clamp(displayedHealth / maxHealth, 0.0f, 1.0f)
        }
        val delayHealthPercent = Mth.clamp(delayedHealth / maxHealth, 0.0f, 1.0f)

        val pad = 5.0f * panelScale
        val cornerRadius = radius.toFloat() * panelScale
        val barHeight = healthBarHeight.toFloat() * panelScale
        val barRadius = healthBarRadius.toFloat() * panelScale
        val barWidth = maxOf(1.0f, panelWidth - pad * 2.0f)
        val delayedBarWidth = Mth.clamp(barWidth, 0.0f, barWidth * delayHealthPercent)
        val filledBarWidth = Mth.clamp(barWidth, 0.0f, barWidth * healthPercent)

        val innerHeight = maxOf(1.0f, panelHeight - pad * 2.0f)
        val contentAreaHeight = maxOf(1.0f, innerHeight - pad - barHeight)
        val headSize = minOf(contentAreaHeight, maxOf(26.0f * panelScale, panelHeight * 0.6f) * 1.05f)
        val textScale = maxOf(0.45f, nameSize.toFloat() / 14.0f) * panelScale
        val textHeight = textRenderer.getHeight(textScale)
        val contentRowHeight = maxOf(headSize, textHeight)
        val contentBlockHeight = contentRowHeight + pad + barHeight
        val contentStartY = y + pad + maxOf(0.0f, (innerHeight - contentBlockHeight) / 2.0f)
        val headY = contentStartY + (contentRowHeight - headSize) / 2.0f
        val headX = x + pad
        val barY = contentStartY + contentRowHeight + pad

        val textStartX = headX + headSize + pad

        val nameText = target.name.string
        val healthText = String.format(Locale.ROOT, "%.1f", displayedHealth)

        val contentY = headY + 2.0f * panelScale
        val healthTextWidth = textRenderer.getWidth(healthText, textScale)
        val healthTextX = x + panelWidth - pad - healthTextWidth
        val equipmentY = contentY + textHeight + 2.8f * panelScale
        val equipmentScale = EQUIPMENT_ITEM_SCALE * panelScale
        val equipmentGap = 1.5f * panelScale

        val centerX = x + panelWidth / 2.0f
        val centerY = y + panelHeight / 2.0f
        val scaledPanelX = Mth.lerp(animationScale, centerX, x)
        val scaledPanelY = Mth.lerp(animationScale, centerY, y)
        val scaledPanelWidth = panelWidth * animationScale
        val scaledPanelHeight = panelHeight * animationScale
        val scaledCornerRadius = cornerRadius * animationScale
        val scaledBarHeight = barHeight * animationScale
        val scaledBarRadius = barRadius * animationScale
        val scaledBarOutlineWidth = barOutlineWidth.toFloat() * panelScale * animationScale
        val scaledTextScale = textScale * animationScale
        val scaledHeadRadius = headSize * 0.23f * animationScale
        val scaledPadX = Mth.lerp(animationScale, centerX, x + pad)
        val scaledBarY = Mth.lerp(animationScale, centerY, barY)
        val scaledBarWidth = barWidth * animationScale
        val scaledDelayedBarWidth = delayedBarWidth * animationScale
        val scaledFilledBarWidth = filledBarWidth * animationScale
        val scaledHeadX = Mth.lerp(animationScale, centerX, headX)
        val scaledHeadY = Mth.lerp(animationScale, centerY, headY)
        val scaledHeadSize = headSize * animationScale
        val scaledTextStartX = Mth.lerp(animationScale, centerX, textStartX)
        val scaledContentY = Mth.lerp(animationScale, centerY, contentY)
        val scaledHealthTextX = Mth.lerp(animationScale, centerX, healthTextX)
        val scaledEquipmentX = Mth.lerp(animationScale, centerX, textStartX)
        val scaledEquipmentY = Mth.lerp(animationScale, centerY, equipmentY)
        val scaledEquipmentScale = equipmentScale * animationScale
        val scaledEquipmentGap = equipmentGap * animationScale
        val damageProgress = Easing.EASE_OUT_SINE.function.apply(Mth.clamp(target.hurtTime / 10.0f, 0.0f, 1.0f))
        val headDamageScale = 1.0f - damageProgress * HEAD_DAMAGE_SCALE_FACTOR
        val finalHeadSize = scaledHeadSize * headDamageScale
        val finalHeadX = scaledHeadX + (scaledHeadSize - finalHeadSize) / 2.0f
        val finalHeadY = scaledHeadY + (scaledHeadSize - finalHeadSize) / 2.0f
        val finalHeadRadius = scaledHeadRadius * headDamageScale
        val headTintColor = withAlpha(tintColor(Color.WHITE, damageProgress), animationScale)

        BlurShader.render(scaledPanelX, scaledPanelY, scaledPanelWidth, scaledPanelHeight, scaledCornerRadius, blurStrength.toFloat())

        if (drawShadow) {
            scope.shadow(
                scaledPanelX, scaledPanelY, scaledPanelWidth, scaledPanelHeight, scaledCornerRadius,
                shadowBlur.toFloat() * animationScale, withAlpha(shadowColor, animationScale)
            )
        }

        scope.roundRect(
            scaledPanelX, scaledPanelY, scaledPanelWidth, scaledPanelHeight, scaledCornerRadius,
            withAlpha(backgroundColor, animationScale)
        )
        scope.roundRect(
            scaledPadX, scaledBarY, scaledBarWidth, scaledBarHeight, scaledBarRadius,
            withAlpha(barBackgroundColor, animationScale)
        )
        if (delayBar && delayedHealth > displayedHealth) {
            scope.roundRect(
                scaledPadX, scaledBarY, scaledDelayedBarWidth, scaledBarHeight, scaledBarRadius,
                withAlpha(delayBarColor, animationScale)
            )
        }
        scope.roundRect(
            scaledPadX, scaledBarY, scaledFilledBarWidth, scaledBarHeight, scaledBarRadius,
            withAlpha(barFillColor, animationScale)
        )
        if (target !is AbstractClientPlayer) {
            scope.roundRect(
                finalHeadX, finalHeadY, finalHeadSize, finalHeadSize, finalHeadRadius,
                withAlpha(tintColor(Color(80, 80, 80, 200), damageProgress), animationScale)
            )
        }

        if (barOutline && scaledBarOutlineWidth > 0.0f) {
            scope.outline(
                scaledPadX, scaledBarY, scaledBarWidth, scaledBarHeight, scaledBarRadius,
                scaledBarOutlineWidth, withAlpha(barOutlineColor, animationScale)
            )
        }

        if (target is AbstractClientPlayer) {
            val abstractTexture: AbstractTexture = mc.textureManager.getTexture(target.skin.body().texturePath())
            scope.playerHead(
                LuminTexture(abstractTexture.texture, abstractTexture.textureView, abstractTexture.sampler),
                finalHeadX, finalHeadY, finalHeadSize, finalHeadRadius, headTintColor
            )
        }

        scope.text(nameText, scaledTextStartX, scaledContentY, scaledTextScale, withAlpha(textColor, animationScale))
        scope.text(healthText, scaledHealthTextX, scaledContentY, scaledTextScale, withAlpha(textColor, animationScale))
    }

    override fun renderOverlay(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker) {
        if (style == Style.Akrien) {
            return
        }

        val panelScale = scale.toFloat()
        val target = renderedTarget ?: return
        if (visibilityProgress <= 0.01f) return

        val textRenderer = textRendererSupplier.get()
        val panelWidth = panelWidthSetting.toFloat() * panelScale
        val panelHeight = panelHeightSetting.toFloat() * panelScale
        val animationScale = Easing.EASE_OUT_SINE.function.apply(Mth.clamp(visibilityProgress, 0.0f, 1.0f))
        val pad = 5.0f * panelScale
        val barHeight = healthBarHeight.toFloat() * panelScale
        val innerHeight = maxOf(1.0f, panelHeight - pad * 2.0f)
        val contentAreaHeight = maxOf(1.0f, innerHeight - pad - barHeight)
        val headSize = minOf(contentAreaHeight, maxOf(26.0f * panelScale, panelHeight * 0.6f) * 1.05f)
        val textScale = maxOf(0.45f, nameSize.toFloat() / 14.0f) * panelScale
        val textHeight = textRenderer.getHeight(textScale)
        val contentRowHeight = maxOf(headSize, textHeight)
        val contentBlockHeight = contentRowHeight + pad + barHeight
        val contentStartY = y + pad + maxOf(0.0f, (innerHeight - contentBlockHeight) / 2.0f)
        val headY = contentStartY + (contentRowHeight - headSize) / 2.0f
        val headX = x + pad
        val textStartX = headX + headSize + pad
        val contentY = headY + 2.0f * panelScale
        val equipmentY = contentY + textHeight + 2.8f * panelScale
        val equipmentScale = EQUIPMENT_ITEM_SCALE * panelScale
        val equipmentGap = 1.5f * panelScale
        val centerX = x + panelWidth / 2.0f
        val centerY = y + panelHeight / 2.0f
        val scaledEquipmentX = Mth.lerp(animationScale, centerX, textStartX)
        val scaledEquipmentY = Mth.lerp(animationScale, centerY, equipmentY)
        val scaledEquipmentScale = equipmentScale * animationScale
        val scaledEquipmentGap = equipmentGap * animationScale

        renderEquipmentItems(graphics, target, scaledEquipmentX, scaledEquipmentY, scaledEquipmentScale, scaledEquipmentGap)
    }

    private fun renderEquipmentItems(
        graphics: GuiGraphicsExtractor,
        target: LivingEntity,
        startX: Float,
        y: Float,
        scale: Float,
        gap: Float,
    ) {
        val equipmentItems: MutableList<ItemStack> = ArrayList(5)
        appendEquipmentItem(equipmentItems, target.mainHandItem)
        appendEquipmentItem(equipmentItems, target.getItemBySlot(EquipmentSlot.HEAD))
        appendEquipmentItem(equipmentItems, target.getItemBySlot(EquipmentSlot.CHEST))
        appendEquipmentItem(equipmentItems, target.getItemBySlot(EquipmentSlot.LEGS))
        appendEquipmentItem(equipmentItems, target.getItemBySlot(EquipmentSlot.FEET))

        if (equipmentItems.isEmpty()) {
            return
        }

        val itemSize = 16.0f * scale
        var itemX = startX
        for (i in equipmentItems.indices) {
            drawItem(graphics, target, equipmentItems[i], itemX, y, scale, target.id + i)
            itemX += itemSize + gap
        }
    }

    private fun appendEquipmentItem(items: MutableList<ItemStack>, stack: ItemStack) {
        if (!stack.isEmpty) {
            items.add(stack)
        }
    }

    /**
     * Akrien's compact HUD: a square-cornered panel, two thin status bars,
     * and the target's head/name/health/distance arranged like the original.
     */
    private fun renderAkrien(deltaTracker: DeltaTracker) {
        val panelScale = scale.toFloat()
        val frameTime = deltaTracker.gameTimeDeltaTicks / 20.0f
        val target = updateRenderedTarget(resolveTarget())
        val animationScale = Easing.EASE_OUT_SINE.function.apply(Mth.clamp(visibilityProgress, 0.0f, 1.0f))
        if (target == null || animationScale <= 0.01f) {
            setBounds(110.0f * panelScale, 39.0f * panelScale)
            return
        }

        val maxHealth = maxOf(1.0f, target.maxHealth + maxOf(0.0f, target.absorptionAmount))
        val health = HealthManager.getHealth(target)
        val healthPercent = updateAnimatedHealth(target, health, maxHealth, frameTime)
        val armorPercent = Mth.clamp(target.armorValue / 20.0f, 0.0f, 1.0f)
        val damageProgress = Easing.EASE_OUT_SINE.function.apply(Mth.clamp(target.hurtTime / 10.0f, 0.0f, 1.0f))
        val headScale = 1.0f - damageProgress * AKRIEN_HEAD_DAMAGE_SCALE_FACTOR

        val textRenderer = textRendererSupplier.get()
        val nameScale = maxOf(0.45f, nameSize.toFloat() / 14.0f) * panelScale
        val bodyScale = maxOf(0.4f, nameScale * 0.78f)
        val name = target.name.string
        val healthText = String.format(Locale.ROOT, "Health: %.1f", health)
        val self = NonNullContext.current?.player
        val distance = if (self == null) 0.0f else self.distanceTo(target)
        val distanceText = String.format(Locale.ROOT, "Distance: %.1f m", distance)
        val textWidth = maxOf(textRenderer.getWidth(name, nameScale), maxOf(textRenderer.getWidth(healthText, bodyScale), textRenderer.getWidth(distanceText, bodyScale)))
        val panelWidth = maxOf(110.0f * panelScale, textWidth + 40.0f * panelScale)
        val panelHeight = 39.0f * panelScale
        setBounds(panelWidth, panelHeight)

        val centerX = x + panelWidth / 2.0f
        val centerY = y + panelHeight / 2.0f
        val panelX = Mth.lerp(animationScale, centerX, x)
        val panelY = Mth.lerp(animationScale, centerY, y)
        val scaledWidth = panelWidth * animationScale
        val scaledHeight = panelHeight * animationScale
        val scaled = panelScale * animationScale
        val pad = 2.5f * scaled
        val barWidth = maxOf(1.0f, scaledWidth - 4.5f * scaled)
        val healthWidth = maxOf(0.0f, barWidth * healthPercent)
        val armorWidth = maxOf(0.0f, barWidth * armorPercent)
        val background = withAlpha(Color(8, 8, 8, 225), 0.62f * animationScale)
        val track = withAlpha(Color(0, 0, 0, 205), animationScale)
        val border = withAlpha(Color(0, 0, 0, 235), animationScale)
        val shadow = withAlpha(Color.BLACK, 0.68f * animationScale)
        val scope: UiTree.Scope = renderScope()

        BlurShader.render(panelX, panelY, scaledWidth, scaledHeight, 0.0f, blurStrength.toFloat())
        val shadowBlurRadius = maxOf(6.0f, blurStrength.toFloat() * 1.35f) * animationScale
        scope.shadow(panelX, panelY, scaledWidth, scaledHeight, 0.0f, shadowBlurRadius, shadow)
        scope.rect(panelX, panelY, scaledWidth, scaledHeight, background)
        scope.rect(panelX + pad, panelY + 31.0f * scaled, barWidth, 2.5f * scaled, track)
        scope.rect(panelX + pad, panelY + 34.5f * scaled, barWidth, 2.5f * scaled, track)
        if (healthWidth > 0.0f) {
            scope.rectHorizontalGradient(
                panelX + pad, panelY + 31.0f * scaled, healthWidth, 2.5f * scaled,
                withAlpha(Color(0, 156, 65), animationScale),
                withAlpha(Color(142, 255, 193), animationScale)
            )
        }
        if (armorWidth > 0.0f) {
            scope.rectHorizontalGradient(
                panelX + pad, panelY + 34.5f * scaled, armorWidth, 2.5f * scaled,
                withAlpha(Color(0, 103, 176), animationScale),
                withAlpha(Color(57, 213, 255), animationScale)
            )
        }
        scope.rectOutline(panelX + pad, panelY + 31.0f * scaled, barWidth, 2.5f * scaled, 0.74f * scaled, border)
        scope.rectOutline(panelX + pad, panelY + 34.5f * scaled, barWidth, 2.5f * scaled, 0.74f * scaled, border)

        val headSize = AKRIEN_HEAD_SIZE * scaled * headScale
        val headX = panelX + 3.0f * scaled + (AKRIEN_HEAD_SIZE * scaled - headSize) / 2.0f
        val headY = panelY + 3.0f * scaled + (AKRIEN_HEAD_SIZE * scaled - headSize) / 2.0f
        val headColor = withAlpha(tintColor(Color.WHITE, damageProgress), animationScale)
        if (target is AbstractClientPlayer) {
            val texture: AbstractTexture = mc.textureManager.getTexture(target.skin.body().texturePath())
            scope.playerHead(
                LuminTexture(texture.texture, texture.textureView, texture.sampler),
                headX, headY, headSize, 0.0f, headColor
            )
        } else {
            scope.rect(
                panelX + 3.0f * scaled, panelY + 3.0f * scaled, 25.0f * scaled, 25.0f * scaled,
                withAlpha(Color(35, 35, 35, 220), animationScale)
            )
            val questionScale = maxOf(0.72f * panelScale, nameScale * 1.45f) * animationScale
            val questionWidth = textRenderer.getWidth("?", questionScale)
            val questionHeight = textRenderer.getHeight(questionScale)
            val questionX = panelX + 3.0f * scaled + (25.0f * scaled - questionWidth) / 2.0f
            val questionY = panelY + 3.0f * scaled + (25.0f * scaled - questionHeight) / 2.0f
            scope.text("?", questionX, questionY, questionScale, withAlpha(Color(255, 255, 255, 245), animationScale))
        }

        if (animationScale > 0.01f) {
            val textX = panelX + 31.0f * scaled
            val textY = panelY + 2.0f * scaled
            scope.text(name, textX, textY, nameScale, withAlpha(Color(255, 255, 255, 250), animationScale))
            scope.text(healthText, textX, panelY + 13.0f * scaled, bodyScale, withAlpha(Color(228, 228, 228, 238), animationScale))
            scope.text(distanceText, textX, panelY + 22.0f * scaled, bodyScale, withAlpha(Color(175, 175, 175, 225), animationScale))
        }
    }

    private fun drawItem(graphics: GuiGraphicsExtractor, owner: LivingEntity, stack: ItemStack, x: Float, y: Float, scale: Float, seed: Int) {
        graphics.pose().pushMatrix()
        graphics.pose().translate(x + scale, y + scale)
        graphics.pose().scale(scale, scale)
        graphics.item(owner, stack, 0, 0, seed)
        graphics.pose().popMatrix()
    }

    private fun resolveTarget(): LivingEntity? {
        val target = KillAura.target
        if (isRenderableTarget(target)) {
            return target
        }
        return if (mc.gui.screen() is HudEditorScreen) mc.player else null
    }

    private fun isRenderableTarget(target: LivingEntity?): Boolean {
        return target != null && target.isAlive && !target.isDeadOrDying
    }

    private fun updateRenderedTarget(liveTarget: LivingEntity?): LivingEntity? {
        val now = System.currentTimeMillis()
        if (lastVisibilityUpdateMs == 0L) {
            lastVisibilityUpdateMs = now
        }

        val delta = Mth.clamp((now - lastVisibilityUpdateMs) / VISIBILITY_ANIMATION_DURATION_MS.toFloat(), 0.0f, 1.0f)
        lastVisibilityUpdateMs = now

        if (liveTarget != null) {
            renderedTarget = liveTarget
            visibilityProgress = minOf(1.0f, visibilityProgress + delta)
            return renderedTarget
        }

        if (renderedTarget == null) {
            visibilityProgress = 0.0f
            return null
        }

        visibilityProgress = maxOf(0.0f, visibilityProgress - delta)
        if (visibilityProgress <= 0.01f) {
            renderedTarget = null
            resetAnimatedState()
            return null
        }
        return renderedTarget
    }

    private fun tintColor(baseColor: Color, damageProgress: Float): Color {
        val greenBlue = Mth.clamp((255.0f - 155.0f * damageProgress).roundToInt(), 100, 255)
        val red = Mth.clamp((baseColor.red + (255 - baseColor.red) * damageProgress).roundToInt(), 0, 255)
        val green = Mth.clamp((baseColor.green * greenBlue / 255.0f).roundToInt(), 0, 255)
        val blue = Mth.clamp((baseColor.blue * greenBlue / 255.0f).roundToInt(), 0, 255)
        return Color(red, green, blue, baseColor.alpha)
    }

    private fun withAlpha(color: Color, alphaScale: Float): Color {
        val alpha = Mth.clamp((color.alpha * alphaScale).roundToInt(), 0, 255)
        return Color(color.red, color.green, color.blue, alpha)
    }

    private fun updateAnimatedHealth(target: LivingEntity, currentHealth: Float, maxHealth: Float, frameTime: Float): Float {
        val targetId = target.id
        if (targetId != lastTargetId) {
            lastTargetId = targetId
            displayedHealth = currentHealth
            delayedHealth = currentHealth
            lastKnownHealth = currentHealth
            lastKnownMaxHealth = maxHealth
            lastDamageTimeMs = 0L
        } else {
            if (lastKnownHealth >= 0.0f && currentHealth < lastKnownHealth) {
                lastDamageTimeMs = System.currentTimeMillis()
            }
            val speed = Mth.clamp(frameTime * 10.0f, 0.0f, 1.0f)
            displayedHealth = Mth.lerp(speed, displayedHealth, currentHealth)
            delayedHealth = updateDelayedHealth(currentHealth, frameTime)
            lastKnownHealth = currentHealth
            lastKnownMaxHealth = maxHealth
        }
        displayedHealth = Mth.clamp(displayedHealth, 0.0f, maxHealth)
        delayedHealth = Mth.clamp(delayedHealth, 0.0f, maxHealth)
        return Mth.clamp(displayedHealth / maxHealth, 0.0f, 1.0f)
    }

    private fun updateDelayedHealth(currentHealth: Float, frameTime: Float): Float {
        if (!delayBar) {
            return currentHealth
        }
        if (currentHealth >= delayedHealth) {
            return currentHealth
        }
        if (delayWait && System.currentTimeMillis() - lastDamageTimeMs < delayTime.toLong()) {
            return delayedHealth
        }
        val speed = Mth.clamp(frameTime * delaySpeed.toFloat() * 2.0f, 0.0f, 1.0f)
        return Mth.lerp(speed, delayedHealth, currentHealth)
    }

    private fun resetAnimatedState() {
        lastTargetId = Int.MIN_VALUE
        displayedHealth = 0.0f
        delayedHealth = 0.0f
        lastKnownHealth = -1.0f
        lastKnownMaxHealth = 1.0f
        lastDamageTimeMs = 0L
    }


    private const val VISIBILITY_ANIMATION_DURATION_MS = 300L
    private const val HEAD_DAMAGE_SCALE_FACTOR = 0.15f
    private const val AKRIEN_HEAD_SIZE = 28.0f
    private const val AKRIEN_HEAD_DAMAGE_SCALE_FACTOR = 0.08f
    private const val EQUIPMENT_ITEM_SCALE = 0.85f

}
