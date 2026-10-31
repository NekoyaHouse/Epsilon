package com.github.epsilon.elements.impl.island

import com.github.epsilon.Constants
import com.github.epsilon.elements.HudModule
import com.github.epsilon.elements.impl.island.instance.LandController
import com.github.epsilon.elements.impl.island.instance.impl.*
import com.github.epsilon.elements.impl.island.pattern.impl.CheckPattern
import com.github.epsilon.events.api.handler
import com.github.epsilon.events.impl.Render2DEvent
import com.github.epsilon.graphics.renderers.RectRenderer
import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.graphics.shaders.OverlayAntiAlias
import com.github.epsilon.modules.impl.combat.KillAura
import com.github.epsilon.modules.impl.movement.Scaffold
import com.github.epsilon.modules.impl.player.Timer
import com.github.epsilon.utils.client.ClientPlatform
import com.github.epsilon.utils.client.PlatformRequirement
import com.github.epsilon.utils.player.InvHelper
import com.github.epsilon.utils.safety.NonNullContext
import com.github.epsilon.utils.safety.runSafe
import com.google.common.base.Suppliers
import com.github.epsilon.smtc.SmtcService
import net.minecraft.client.DeltaTracker
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import java.awt.Color
import java.util.function.Supplier

object Island : HudModule("Island", 0.0f, 0.0f, 154.0f, 24.0f) {

    private val targetHud by boolSetting("Target", true)
    private val scaffoldBlocks by boolSetting("Scaffold Blocks", true)
    private val timerBalance by boolSetting("Timer Balance", true)
    private val tabList by boolSetting("Tab List", true)
    private val music by boolSetting("Music", true).platformOnly(PlatformRequirement.WINDOWS_X64)
    private val traces by boolSetting("Traces", true)
    private val traceRange by doubleSetting("Trace Range", 128.0, 8.0, 256.0, 1.0, dependency = { traces })
    private val traceWidth by doubleSetting("Trace Width", 1.5, 0.5, 5.0, 0.25, dependency = { traces })
    private val traceColor by colorSetting("Trace Color", Color(111, 224, 211, 180), dependency = { traces })
    val backgroundColor by colorSetting("Background Color", Color(15, 15, 15, 50))
    val drawShadow by boolSetting("Drop Shadow", true)
    val backgroundBlur by boolSetting("Background Blur", true)
    val blurStrength by doubleSetting("Blur Strength", 5.0, 1.0, 20.0, 1.0, dependency = { backgroundBlur })
    val shadowBlur by doubleSetting("Shadow Blur", 10.0, 2.0, 32.0, 1.0, dependency = { drawShadow })
    val shadowColor by colorSetting("Shadow Color", Color(255, 255, 255, 110), dependency = { drawShadow })
    val outline by boolSetting("Outline", false)
    val outlineWidth by doubleSetting("Outline Width", 1.0, 0.1, 3.0, 0.1, dependency = { outline })
    val outlineColor by colorSetting("Outline Color", Color(0, 0, 0, 125))
    val textGlitch by boolSetting("Text Glitch", false)
    val glitchColor by colorSetting("Glitch Color", Color.WHITE, false, dependency = { textGlitch })
    val chromaticX by doubleSetting("Chromatic X", 0.8, 0.0, 6.0, 0.1, dependency = { textGlitch })
    val chromaticY by doubleSetting("Chromatic Y", 0.0, 0.0, 6.0, 0.1, dependency = { textGlitch })
    val glitchGlowRadius by doubleSetting("Glitch Glow Radius", 1.5, 0.0, 10.0, 0.1, dependency = { textGlitch })
    val glitchGlowIntensity by doubleSetting("Glitch Glow Intensity", 0.9, 0.0, 3.0, 0.1, dependency = { textGlitch })
    val sliceHeight by doubleSetting("Slice Height", 4.0, 1.0, 32.0, 0.5, dependency = { textGlitch })
    val sliceAmount by doubleSetting("Slice Amount", 1.2, 0.0, 10.0, 0.1, dependency = { textGlitch })
    val glitchStrength by doubleSetting("Glitch Strength", 0.35, 0.0, 1.0, 0.05, dependency = { textGlitch })
    val scanlineStrength by doubleSetting("Scanline Strength", 0.2, 0.0, 1.0, 0.05, dependency = { textGlitch })
    val noiseStrength by doubleSetting("Noise Strength", 0.1, 0.0, 1.0, 0.05, dependency = { textGlitch })

    private val textRendererSupplier: Supplier<TextRenderer> = Suppliers.memoize(com.google.common.base.Supplier { TextRenderer.create() })
    private val landRenderer = LandRenderer(LandController, 48.0f, 48.0f)
    private var rectRenderer: RectRenderer? = null

    private var target: LivingEntity? = null

    init {
        handler<Render2DEvent.Level> { renderTraces() }
    }

    override fun onEnable() {
        if (music && ClientPlatform.isWindowsX64()) SmtcService.INSTANCE.start()
    }

    override fun onDisable() {
        LandController.removeInstances(TargetInstance::class.java)
        LandController.removeInstances(ScaffoldBlocksInstance::class.java)
        LandController.removeInstances(BalanceInstance::class.java)
        LandController.removeInstances(TabListInstance::class.java)
        LandController.removeInstances(MusicInstance::class.java)
        SmtcService.INSTANCE.stop()
        rectRenderer?.clear()
        target = null
    }

    private fun renderTraces() {
        if (!traces || nullCheck() || Constants.client.gameRenderer.gameRenderState().guiRenderState.isHudHidden) {
            rectRenderer?.clear()
            return
        }

        var renderer = rectRenderer
        if (renderer == null) {
            renderer = RectRenderer.create()
            rectRenderer = renderer
        }
        renderer.clear()
        val activeRenderer = renderer
        val traceCount = TracesRenderer.render(
            activeRenderer,
            landRenderer.x, landRenderer.y, landRenderer.width, landRenderer.height,
            traceRange, traceWidth.toFloat(), traceColor
        )
        if (traceCount > 0) {
            OverlayAntiAlias.render(4, Runnable { activeRenderer.drawAndClear() })
        }
    }

    override fun render(deltaTracker: DeltaTracker) {
        runSafe {
            landRenderer.setTextRenderer(textRendererSupplier.get())
            landRenderer.update()

            updateTargetInstance()
            updateBlockInstance()
            updateBalanceInstance()
            updateTabListInstance()
            updateMusicInstance()

            setBounds(landRenderer.width, landRenderer.height)

            landRenderer.syncPosition(x, y)
            landRenderer.draw(this, renderScope())
        }
    }

    private fun updateTargetInstance() {
        if (!targetHud) {
            target = null
            LandController.removeInstances(TargetInstance::class.java)
            return
        }

        var next: LivingEntity? = null
        if (KillAura.isEnabled && KillAura.target != null) {
            next = KillAura.target
        } else if (mc.gui.screen() is ChatScreen) {
            next = mc.player
        }
        target = next

        val instance = LandController.getInstance(TargetInstance::class.java)
        val captured = target
        if (captured != null && (instance == null || instance.target !== captured)) {
            LandController.post(
                TargetInstance(
                    captured, textRendererSupplier,
                    CheckPattern(Supplier { this.target !== captured })
                )
            )
        }
    }

    private fun updateBlockInstance() {
        if (!scaffoldBlocks || !Scaffold.isEnabled) {
            LandController.removeInstances(ScaffoldBlocksInstance::class.java)
            return
        }

        val instance = LandController.getInstance(ScaffoldBlocksInstance::class.java)
        if (instance == null) {
            val nn = NonNullContext.current ?: return
            val count: Supplier<Int> = Supplier { context(nn) { InvHelper.getBlockCountInInventory() } }
            val icon: Supplier<ItemStack> = Supplier { Scaffold.getBlockStack() }
            LandController.post(
                ScaffoldBlocksInstance(
                    count, icon, textRendererSupplier,
                    CheckPattern(Supplier { !scaffoldBlocks || !Scaffold.isEnabled })
                )
            )
        }
    }

    private fun updateBalanceInstance() {
        if (!timerBalance || !Timer.isEnabled) {
            LandController.removeInstances(BalanceInstance::class.java)
            return
        }

        val instance = LandController.getInstance(BalanceInstance::class.java)
        if (instance == null) {
            LandController.post(
                BalanceInstance(
                    Supplier { Timer.getBalanceTime() },
                    Supplier { Timer.getMaxBalance() },
                    textRendererSupplier,
                    CheckPattern(
                        Supplier {
                            !timerBalance
                                    || !Timer.isEnabled
                                    || (!Timer.isReleasing() && Timer.getBalanceTime() >= Timer.getMaxBalance())
                        }
                    )
                )
            )
        }
    }

    private fun updateTabListInstance() {
        if (!tabList || nullCheck() || mc.gui.screen() != null || !mc.options.keyPlayerList.isDown) {
            LandController.removeInstances(TabListInstance::class.java)
            return
        }

        if (LandController.getInstance(TabListInstance::class.java) == null) {
            LandController.post(
                TabListInstance(
                    textRendererSupplier,
                    CheckPattern(
                        Supplier {
                            !tabList || nullCheck() || mc.gui.screen() != null || !mc.options.keyPlayerList.isDown
                        }
                    )
                )
            )
        }
    }

    private fun updateMusicInstance() {
        if (music && ClientPlatform.isWindowsX64()) {
            SmtcService.INSTANCE.start()
            val snapshot = SmtcService.INSTANCE.snapshot()
            if (!snapshot.available() || !snapshot.isPlaying) {
                LandController.removeInstances(MusicInstance::class.java)
                return
            }

            if (LandController.getInstance(MusicInstance::class.java) == null) {
                LandController.post(
                    MusicInstance(
                        SmtcService.INSTANCE,
                        textRendererSupplier,
                        CheckPattern(
                            Supplier {
                                val current = SmtcService.INSTANCE.snapshot()
                                !music || !current.available() || !current.isPlaying
                            }
                        )
                    )
                )
            }
        } else {
            LandController.removeInstances(MusicInstance::class.java)
            SmtcService.INSTANCE.stop()
        }
    }

}
