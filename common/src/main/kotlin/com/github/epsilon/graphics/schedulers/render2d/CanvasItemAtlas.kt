package com.github.epsilon.graphics.schedulers.render2d

import com.github.epsilon.graphics.LuminRenderSystem
import com.github.epsilon.graphics.LuminTexture
import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.kernel.MinecraftWrapper.mc
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.renderpearl.api.textures.FilterMode
import com.mojang.renderpearl.api.textures.GpuTextureView
import net.minecraft.client.gui.render.GuiItemAtlas
import net.minecraft.client.gui.render.pip.OversizedItemRenderer
import net.minecraft.client.renderer.item.TrackingItemStackRenderState
import net.minecraft.client.renderer.state.gui.BlitRenderState
import net.minecraft.client.renderer.state.gui.GuiItemRenderState
import net.minecraft.client.renderer.state.gui.GuiRenderState
import net.minecraft.client.renderer.state.gui.pip.OversizedItemRenderState
import net.minecraft.world.item.ItemDisplayContext
import org.joml.Matrix3x2f
import java.awt.Color
import java.util.*
import kotlin.math.ceil

/**
 * Canvas 的原版物品模型桥接。普通模型使用 GuiItemAtlas，超大模型使用原版 PIP；
 * 这里只准备纹理，最终合成仍进入 Canvas 的有序队列。所有 GPU 操作在渲染线程执行。
 */
internal class CanvasItemAtlas(private val metrics: TextRenderer) : AutoCloseable {
    private var atlas: GuiItemAtlas? = null
    private var generation = Long.MIN_VALUE
    private var frame = Long.MIN_VALUE
    private var guiScale = Double.NaN
    private val identities = HashSet<Any>()
    private val oversized = HashMap<Any, LargeItem>()

    private class LargeItem(val renderer: OversizedItemRenderer, var frame: Long)

    fun prepare(items: List<Render2DCommand.Item>): Map<Render2DCommand.Item, List<Render2DCommand>> {
        val nextFrame = LuminRenderSystem.getRenderFrameId()
        val scale = LuminRenderSystem.getGuiScale()
        if (generation != Render2DResources.generation || guiScale != scale) {
            close()
            generation = Render2DResources.generation
            guiScale = scale
        }
        if (frame != nextFrame) {
            atlas?.endFrame()
            identities.clear()
            val iterator = oversized.values.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.frame < frame) {
                    entry.renderer.close()
                    iterator.remove()
                }
            }
            frame = nextFrame
        }

        val states = items.map { command ->
            val state = TrackingItemStackRenderState()
            val owner = command.owner ?: mc.player
            mc.itemModelResolver.updateForTopItem(
                state, command.stack, ItemDisplayContext.GUI, owner?.level() ?: mc.level, owner, command.seed
            )
            val guiItem = GuiItemRenderState(Matrix3x2f(), state, 0, 0, null)
            if (guiItem.oversizedItemBounds() == null) identities.add(state.modelIdentity)
            command to guiItem
        }
        val result = IdentityHashMap<Render2DCommand.Item, List<Render2DCommand>>()
        // 同一图集共享纹理引用，令相邻物品能够合并为一个纹理批次。
        val textures = IdentityHashMap<GpuTextureView, LuminTexture>()
        fun borrowed(view: GpuTextureView) = textures.getOrPut(view) {
            LuminTexture(
                view.texture(),
                view,
                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST),
                false,
                false
            )
        }

        val projection = RenderSystem.getProjectionMatrixBuffer()
        val projectionType = RenderSystem.getProjectionType()
        val modelView = RenderSystem.getModelViewStack()
        modelView.pushMatrix().identity()
        try {
            val slotSize = maxOf(1, ceil(16 * scale).toInt())
            if (identities.isNotEmpty()) prepareAtlas(slotSize)
            for ((command, state) in states) {
                val commands = ArrayList<Render2DCommand>()
                val bounds = state.oversizedItemBounds()
                if (bounds == null) {
                    atlas?.getOrUpdate(state.itemStackRenderState())?.let { slot ->
                        commands.add(
                            texture(
                                command,
                                borrowed(slot.textureView()),
                                command.bounds,
                                slot.u0(),
                                slot.v0(),
                                slot.u1(),
                                slot.v1()
                            )
                        )
                    }
                } else {
                    val entry = oversized.getOrPut(state.itemStackRenderState().modelIdentity) {
                        LargeItem(OversizedItemRenderer(), frame)
                    }
                    entry.frame = frame
                    val gui = GuiRenderState()
                    val pip =
                        OversizedItemRenderState(state, bounds.left(), bounds.top(), bounds.right(), bounds.bottom())
                    entry.renderer.prepare(
                        pip,
                        gui,
                        mc.gameRenderer.featureRenderDispatcher(),
                        maxOf(1, ceil(scale).toInt())
                    )
                    gui.forEachElement({ element ->
                        if (element is BlitRenderState) {
                            val size = command.bounds.width / 16f
                            val area = Render2DBounds.of(
                                command.bounds.x + element.x0() * size, command.bounds.y + element.y0() * size,
                                (element.x1() - element.x0()) * size, (element.y1() - element.y0()) * size
                            )
                            commands.add(
                                texture(
                                    command, borrowed(checkNotNull(element.textureSetup().texure0())), area,
                                    element.u0(), element.v0(), element.u1(), element.v1()
                                )
                            )
                        }
                    }, GuiRenderState.TraverseRange.ALL)
                }
                if (command.decorations) decorate(command, commands)
                result[command] = commands
            }
        } finally {
            RenderSystem.disableScissorForRenderTypeDraws()
            modelView.popMatrix()
            if (projection != null) RenderSystem.setProjectionMatrix(projection, projectionType)
        }
        return result
    }

    private fun prepareAtlas(slotSize: Int) {
        val existing = atlas
        if (existing != null && existing.tryPrepareFor(identities)) return
        val size = GuiItemAtlas.computeTextureSizeFor(slotSize, identities.size)
        if (existing != null && existing.textureSize() == size) return
        existing?.close()
        atlas = GuiItemAtlas(mc.gameRenderer.featureRenderDispatcher(), size, slotSize)
    }

    private fun texture(
        item: Render2DCommand.Item, texture: LuminTexture, bounds: Render2DBounds,
        u0: Float, v0: Float, u1: Float, v1: Float,
    ) = Render2DCommand.Texture(
        item.layer, item.sequence, bounds, item.scissor, Render2DTexture.LuminRef(texture),
        0f, 0f, 0f, 0f, u0, v0, u1, v1, item.color, 0f, 0f, 0f, premultipliedAlpha = true
    )

    /** 与 GuiGraphicsExtractor.itemDecorations 相同的位置和顺序，数量使用 Canvas 字体。 */
    private fun decorate(item: Render2DCommand.Item, output: MutableList<Render2DCommand>) {
        val stack = item.stack
        val size = item.bounds.width / 16f
        fun rect(x: Float, y: Float, width: Float, height: Float, color: Color) {
            val tint = Color(
                color.red * item.color.red / 255, color.green * item.color.green / 255,
                color.blue * item.color.blue / 255, color.alpha * item.color.alpha / 255
            )
            output.add(
                Render2DCommand.Rect(
                    item.layer, item.sequence, Render2DBounds.of(
                        item.bounds.x + x * size, item.bounds.y + y * size, width * size, height * size
                    ), item.scissor, tint, tint, tint, tint
                )
            )
        }
        if (stack.isBarVisible) {
            rect(2f, 13f, 13f, 2f, Color.BLACK)
            rect(2f, 13f, stack.barWidth.toFloat(), 1f, Color(stack.barColor))
        }
        val cooldown =
            mc.player?.cooldowns?.getCooldownPercent(stack, mc.deltaTracker.getGameTimeDeltaPartialTick(true)) ?: 0f
        if (cooldown > 0f) {
            val top = kotlin.math.floor(16f * (1f - cooldown))
            rect(0f, top, 16f, ceil(16f * cooldown), Color(255, 255, 255, 127))
        }
        if (stack.count != 1 || item.countText != null) {
            val text = item.countText ?: stack.count.toString()
            val textScale = size * 0.6f
            val width = metrics.getWidth(text, textScale)
            val x = item.bounds.x + 17f * size - width
            val y = item.bounds.y + 9f * size
            val bounds = Render2DBounds.of(x, y, width, metrics.getHeight(textScale))
            output.add(
                Render2DCommand.Text(
                    item.layer, item.sequence, bounds.copy(x = x + size, y = y + size), item.scissor,
                    text, x + size, y + size, textScale, Color(0, 0, 0, item.color.alpha), null, 0f, 0f, 0f
                )
            )
            output.add(
                Render2DCommand.Text(
                    item.layer, item.sequence, bounds, item.scissor,
                    text, x, y, textScale, item.color, null, 0f, 0f, 0f
                )
            )
        }
    }

    override fun close() {
        atlas?.close()
        atlas = null
        oversized.values.forEach { it.renderer.close() }
        oversized.clear()
        identities.clear()
        frame = Long.MIN_VALUE
    }
}
