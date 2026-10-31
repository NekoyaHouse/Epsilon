package com.github.epsilon.modules.impl.combat

import com.github.epsilon.events.api.handler
import com.github.epsilon.events.api.nonNullHandler
import com.github.epsilon.events.impl.ClientTickEvent
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.events.impl.Render3DEvent
import com.github.epsilon.managers.rotation.RotationManager
import com.github.epsilon.managers.target.TargetManager
import com.github.epsilon.managers.target.TargetRequest
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.modules.impl.movement.NoSlowdown
import com.github.epsilon.modules.impl.movement.Scaffold
import com.github.epsilon.modules.impl.movement.Velocity
import com.github.epsilon.modules.impl.render.AntiAlias
import com.github.epsilon.utils.player.PlayerUtils
import com.github.epsilon.utils.player.SwingMode
import com.github.epsilon.utils.render.esp.CaptureMarkESP
import com.github.epsilon.utils.render.esp.CircleESP
import com.github.epsilon.utils.render.esp.DeobfESP
import com.github.epsilon.utils.render.esp.FireflyESP
import com.github.epsilon.utils.rotation.Priority
import com.github.epsilon.utils.rotation.RaytraceUtils
import com.github.epsilon.utils.rotation.RotationUtils
import com.github.epsilon.utils.safety.NonNullContext
import com.github.epsilon.utils.safety.interaction
import com.github.epsilon.utils.safety.player
import com.github.epsilon.utils.timer.TimerUtils
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult
import java.awt.Color
import kotlin.math.abs

object KillAura : Module("Kill Aura", Category.COMBAT) {

    private enum class Mode {
        OnePointEight,
        OnePointNinePlus,
    }

    private enum class TargetMode {
        Single,
        Switch,
    }

    private enum class PriorityMode {
        None,
        Health,
        Fov,
        Range,
    }

    private enum class ESPMode {
        CaptureMark,
        Circle,
        Firefly,
        Deobf,
    }

    private val pauseOnEat by boolSetting("Pause On Eat", true)
    private val pauseOnScaffold by boolSetting("Pause On Scaffold", true)
    private val hitSelect by boolSetting("Hit Select", true)
    private val throughWalls by boolSetting("Through Walls", false)
    private val mode by enumSetting("Mode", Mode.OnePointEight)
    private val targetMode by enumSetting("Target Mode", TargetMode.Single)
    private val switchDelay by intSetting(
        "Switch Delay",
        100,
        0..500,
        1, { targetMode == TargetMode.Switch })
    private val priorityMode by enumSetting("Priority Mode", PriorityMode.None)
    val searchRange by doubleSetting("Search Range", 4.0, 1.0..6.0, 0.1)
    val aimRange by doubleSetting("Aim Range", 3.0, 1.0..6.0, 0.1)
    private val fov by intSetting("FOV", 360, 10..360, 1)
    private val rotate by boolSetting("Rotate", true)
    private val rotationSpeed by intSetting("Rotation Speed", 180, 10..180, 10, { rotate })
    private val rotationPriority by enumSetting("Rotation Priority", Priority.High, { rotate })
    private val cps by intSetting("CPS", 12, 1..20, 1, { mode == Mode.OnePointEight })

    private val players by boolSetting("Players", true)
    private val mobs by boolSetting("Mobs", true)
    private val animals by boolSetting("Animals", true)
    private val villagers by boolSetting("Villagers", false)
    private val ambient by boolSetting("Ambient", false)
    private val water by boolSetting("Water", false)
    private val others by boolSetting("Others", false)
    private val invisible by boolSetting("Invisible", true)

    private val swingHand by boolSetting("Swing Hand", true)
    private val esp by boolSetting("ESP", true)
    private val espMode by enumSetting("ESP Mode", ESPMode.Circle, { esp })
    val deobfMode by enumSetting(
        "Deobf Mode",
        DeobfESP.TextureMode.Mengcha, { esp && espMode == ESPMode.Deobf })
    private val deobfSize by doubleSetting(
        "Deobf Size",
        0.75,
        0.25..2.0,
        0.05, { esp && espMode == ESPMode.Deobf })
    private val deobfSpins by doubleSetting(
        "Deobf Spins",
        3.0,
        0.5..8.0,
        0.25, { esp && espMode == ESPMode.Deobf })
    private val deobfWobble by doubleSetting(
        "Deobf Wobble",
        1.0,
        0.0..2.0,
        0.1, { esp && espMode == ESPMode.Deobf })
    private val deobfFlyHeight by doubleSetting(
        "Deobf Fly Height",
        5.0,
        1.0..12.0,
        0.5, { esp && espMode == ESPMode.Deobf })
    private val espColor1 by colorSetting(
        "ESP Main",
        Color(255, 183, 197), true, { esp && espMode == ESPMode.CaptureMark })
    private val espColor2 by colorSetting(
        "ESP Second",
        Color(255, 133, 161), true, { esp && espMode == ESPMode.CaptureMark })
    private val espSize by doubleSetting(
        "ESP Size",
        1.2,
        0.5..3.0,
        0.1, { esp && espMode == ESPMode.CaptureMark })
    private val espRotSpeed by doubleSetting(
        "Rot Speed",
        2.0,
        0.5..10.0,
        0.1, { esp && espMode == ESPMode.CaptureMark })
    private val waveSpeed by doubleSetting(
        "Wave Speed",
        3.0,
        0.5..10.0,
        0.1, { esp && espMode == ESPMode.CaptureMark })
    private val sideColor by colorSetting(
        "Side Color",
        Color.WHITE,
        false, { esp && espMode == ESPMode.Circle })
    private val lineColor by colorSetting(
        "Line Color",
        Color(255, 255, 255, 233), true, { esp && espMode == ESPMode.Circle })
    private val circleAlphaFactor by doubleSetting(
        "Circle Alpha Factor",
        1.0,
        0.0..2.0,
        0.05, { esp && espMode == ESPMode.Circle })
    private val fireflyColorMode by enumSetting(
        "Firefly Color Mode",
        FireflyESP.ColorMode.Blend, { esp && espMode == ESPMode.Firefly })
    private val fireflyColor by colorSetting(
        "Firefly Color",
        Color(149, 149, 149, 255), true, { esp && espMode == ESPMode.Firefly })
    private val fireflyColor2 by colorSetting(
        "Firefly Color 2",
        Color(255, 133, 161, 255), true, { esp && espMode == ESPMode.Firefly && fireflyColorMode == FireflyESP.ColorMode.Blend })
    private val fireflyColorMix by doubleSetting(
        "Firefly Color Mix",
        0.65,
        0.0..1.0,
        0.05, { esp && espMode == ESPMode.Firefly && fireflyColorMode == FireflyESP.ColorMode.Blend })
    private val fireflyColorSpeed by doubleSetting(
        "Firefly Color Speed",
        1.2,
        0.1..6.0,
        0.1, { esp && espMode == ESPMode.Firefly && fireflyColorMode == FireflyESP.ColorMode.Blend })
    private val fireflyRainbowSpeed by doubleSetting(
        "Firefly Rainbow Speed",
        1.0,
        0.1..6.0,
        0.1, { esp && espMode == ESPMode.Firefly && fireflyColorMode == FireflyESP.ColorMode.Rainbow })
    private val fireflyRainbowSaturation by doubleSetting(
        "Firefly Rainbow Saturation",
        0.85,
        0.1..1.0,
        0.05, { esp && espMode == ESPMode.Firefly && fireflyColorMode == FireflyESP.ColorMode.Rainbow })
    private val fireflyRainbowBrightness by doubleSetting(
        "Firefly Rainbow Brightness",
        1.0,
        0.1..1.0,
        0.05, { esp && espMode == ESPMode.Firefly && fireflyColorMode == FireflyESP.ColorMode.Rainbow })
    private val fireflyLength by intSetting(
        "Firefly Length",
        14,
        8..128,
        1, { esp && espMode == ESPMode.Firefly })
    private val fireflyFactor by intSetting(
        "Firefly Factor",
        8,
        1..10,
        1, { esp && espMode == ESPMode.Firefly })
    private val fireflyShaking by doubleSetting(
        "Firefly Shaking",
        1.8,
        0.25..10.0,
        0.25, { esp && espMode == ESPMode.Firefly })
    private val fireflyAmplitude by doubleSetting(
        "Firefly Amplitude",
        3.0,
        0.0..10.0,
        0.25, { esp && espMode == ESPMode.Firefly })

    var target: LivingEntity? = null
    private var targets: MutableList<LivingEntity>? = null
    private var targetIndex = 0

    private var attacks = 0
    private var lastAttackTime = 0L

    private val switchTimer = TimerUtils()

    init {
        // 常驻监听：模块禁用时也需要继续绘制 Deobf ESP 的上升效果。
        handler<Render3DEvent>(alwaysListening = true) { event ->
            val nn = NonNullContext.current ?: return@handler
            context(nn) {
                DeobfESP.render(
                    event.poseStack,
                    deobfSize.toFloat(),
                    deobfSpins.toFloat(),
                    deobfWobble.toFloat(),
                    deobfFlyHeight.toFloat(),
                )
            }
        }

        nonNullHandler<ClientTickEvent.Pre> {
            if (!esp || espMode != ESPMode.Deobf) {
                DeobfESP.clear()
            }

            if (pauseOnScaffold && Scaffold.isEnabled) {
                resetState()
                return@nonNullHandler
            }

            targets = ArrayList(
                TargetManager.acquireTargets(
                    TargetRequest.of(
                        searchRange,
                        fov.toFloat(),
                        players,
                        mobs,
                        animals,
                        villagers,
                        ambient,
                        water,
                        others,
                        invisible,
                        64,
                    )
                )
            )

            val velocity = Velocity

            if (velocity.delay) {
                targets!!.sortBy { o -> abs(velocity.yaw - RotationUtils.calculate(o).yaw).toDouble() }
            }

            when (targetMode) {
                TargetMode.Single -> targetIndex = 0

                TargetMode.Switch -> {
                    if (switchTimer.passedMillise(switchDelay.toLong())) {
                        switchTimer.reset()
                        if (++targetIndex >= targets!!.size) {
                            targetIndex = 0
                        }
                    }
                }
            }

            if (targetIndex >= targets!!.size) {
                targetIndex = 0
            }

            if (targets!!.isEmpty()) {
                target = null
                return@nonNullHandler
            }

            when (priorityMode) {
                PriorityMode.Range -> targets!!.sortBy { o -> o.distanceTo(player).toDouble() }

                PriorityMode.Fov -> {
                    targets!!.sortBy { o -> abs(Mth.wrapDegrees(player.xRot - RotationUtils.calculate(o).yaw)).toDouble() }
                }

                PriorityMode.Health -> {
                    targets!!.sortBy { o -> o.health.toDouble() }
                }

                PriorityMode.None -> {}
            }

            target = targets!![targetIndex]
            val currentTarget = target!!

            val ignoreWalls = throughWalls

            if (rotate) {
                val calculate = RotationUtils.calculate(currentTarget, true, aimRange)

                if (!ignoreWalls && RaytraceUtils.raytrace(calculate, aimRange)?.type == HitResult.Type.BLOCK) {
                    return@nonNullHandler
                }

                if (ignoreWalls) {
                    RotationManager.INSTANCE.setRotations(calculate, rotationSpeed.toDouble(), rotationPriority)
                } else {
                    RotationManager.INSTANCE.setRotations(
                        calculate,
                        rotationSpeed.toDouble(),
                        { rotation ->
                            val hit = RaytraceUtils.raytrace(rotation, 3.0)
                            hit is EntityHitResult && hit.entity === currentTarget
                        },
                        rotationPriority,
                    )
                }
            }

            val attackEntity = getAttackEntity()
            if (hitSelect && attackEntity is Player && !AntiBot.isBot(attackEntity) && !TargetManager.isSameTeam(
                    attackEntity
                ) && velocity.attackQueue <= 0
            ) {
                val connection = mc.connection
                val localPlayerInfo = connection?.getPlayerInfo(player.uuid)
                val latencyTicks = localPlayerInfo?.latency?.div(50) ?: 0
                if (attackEntity.hurtTime <= latencyTicks + 1 || (player.hurtTime >= 6 && !Velocity.isEnabled) || Criticals.fallTicks == 2) {
                    when (mode) {
                        Mode.OnePointNinePlus -> {
                            if (attacks == 0 && player.getAttackStrengthScale(0.5f) >= 1.0f) {
                                attacks++
                            }
                        }

                        Mode.OnePointEight -> {
                            val time = System.currentTimeMillis()
                            if (time - lastAttackTime >= (1000.0 / cps).toLong()) {
                                attacks++
                                lastAttackTime = time
                            }
                        }
                    }
                }
            }
        }

        nonNullHandler<PlayerTickEvent.Pre> {
            while (attacks > 0) {
                attacks--
                if (pauseOnEat && PlayerUtils.isEating() || NoSlowdown.isWorking()) return@nonNullHandler

                val entity = getAttackEntity() ?: return@nonNullHandler

                interaction.attack(player, entity)

                if (espMode == ESPMode.Deobf) DeobfESP.markHit(entity)

                SwingMode.of(swingHand).attackSwing(InteractionHand.MAIN_HAND)
            }
        }

        nonNullHandler<Render3DEvent> { event ->
            if (target != null && Velocity.attackQueue <= 0) {
                if (!hitSelect || getAttackEntity() !is Player) {
                    when (mode) {
                        Mode.OnePointNinePlus -> {
                            if (attacks == 0 && player.getAttackStrengthScale(0.5f) >= 1.0f) {
                                attacks++
                            }
                        }

                        Mode.OnePointEight -> {
                            val time = System.currentTimeMillis()
                            if (time - lastAttackTime >= (1000.0 / cps).toLong()) {
                                attacks++
                                lastAttackTime = time
                            }
                        }
                    }
                }
            }

            val currentTarget = target
            if (!esp || espMode == ESPMode.Deobf || currentTarget == null) return@nonNullHandler

            val stack = event.poseStack

            AntiAlias.render {
                when (espMode) {
                    ESPMode.CaptureMark -> {
                        CaptureMarkESP.render(
                            stack,
                            currentTarget,
                            espSize,
                            espRotSpeed,
                            waveSpeed,
                            espColor1,
                            espColor2,
                        )
                    }

                    ESPMode.Circle -> {
                        CircleESP.render(
                            stack,
                            currentTarget,
                            sideColor,
                            lineColor,
                            circleAlphaFactor.toFloat(),
                        )
                    }

                    ESPMode.Firefly -> {
                        FireflyESP.render(
                            stack,
                            currentTarget,
                            fireflyLength,
                            fireflyFactor,
                            fireflyShaking,
                            fireflyAmplitude,
                            fireflyColor,
                            fireflyColorMode,
                            fireflyColor2,
                            fireflyColorMix,
                            fireflyColorSpeed,
                            fireflyRainbowSpeed,
                            fireflyRainbowSaturation,
                            fireflyRainbowBrightness,
                        )
                    }

                    ESPMode.Deobf -> {}
                }
            }
        }
    }

    override fun getInfo(): String? {
        return target?.name?.string
    }

    override fun onDisable() {
        resetState()
        val nn = NonNullContext.current ?: return
        context(nn) { DeobfESP.retainRisingEffects() }
    }

    context(nn: NonNullContext)
    private fun getAttackEntity(): Entity? {
        if (throughWalls) {
            val currentTarget = target ?: return null
            if (!currentTarget.isAlive || currentTarget.isDeadOrDying) return null
            return if (RotationUtils.getEyeDistanceToEntity(currentTarget) <= aimRange) currentTarget else null
        }

        val hitResult = RotationManager.INSTANCE.getHitResult()
        if (hitResult !is EntityHitResult) return null

        val entity = hitResult.entity
        if (!entity.isAlive) return null

        if (!rotate && (targets == null || !targets!!.contains(entity))) return null

        return entity
    }

    private fun resetState() {
        targets = null
        target = null
        attacks = 0
        lastAttackTime = 0L
    }

}
