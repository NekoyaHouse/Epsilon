package com.github.epsilon.modules.impl.combat

import com.github.epsilon.utils.*
import com.github.epsilon.events.bus.EventPriority
import com.github.epsilon.events.impl.ClientTickEvent
import com.github.epsilon.events.impl.KeyboardInputEvent
import com.github.epsilon.events.impl.PacketEvent
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.events.impl.Render3DEvent
import com.github.epsilon.interfaces.ClientboundEntityEventPacketAccessor
import com.github.epsilon.managers.rotation.RotationManager
import com.github.epsilon.managers.target.TargetManager
import com.github.epsilon.managers.target.TargetRequest
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.modules.impl.render.AntiAlias
import com.github.epsilon.settings.Setting
import com.github.epsilon.settings.getValue
import com.github.epsilon.utils.NonNullContext
import com.github.epsilon.utils.interaction
import com.github.epsilon.utils.player
import com.github.epsilon.utils.player.FindItemResult
import com.github.epsilon.utils.player.InvUtils
import com.github.epsilon.utils.player.PlayerUtils
import com.github.epsilon.utils.render.esp.CaptureMarkESP
import com.github.epsilon.utils.render.esp.CircleESP
import com.github.epsilon.utils.render.esp.FireflyESP
import com.github.epsilon.utils.rotation.Priority
import com.github.epsilon.utils.rotation.RaytraceUtils
import com.github.epsilon.utils.rotation.Rot2f
import com.github.epsilon.utils.rotation.RotationUtils
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.KeyMapping
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket
import net.minecraft.tags.ItemTags
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityEvent
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.Items
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import java.awt.Color

/**
 * 长矛自动蓄力与瞄准，kinetic 命中后延迟一 tick 尝试重锤补刀。
 */
object SpearAura : Module("Spear Aura", Category.COMBAT) {

    /**
     * 与目标小于 3 格时不再转头/移动，避免贴脸转头。
     */
    private const val SPEAR_MIN_TRACK_DISTANCE = 3.0

    /**
     * 重锤补刀只在摔落高度大于 3 格时触发。
     */
    private const val MACE_MIN_FALL_DISTANCE = 3.0

    /**
     * 命中后停止瞄准/移动的 tick 数，覆盖目标无敌帧。
     */
    private const val HIT_PAUSE_TICKS = 10

    /**
     * kinetic 伤害所需的最小相对速度，单位米/秒。
     */
    private const val SPEAR_MIN_RELATIVE_SPEED = 4.6

    /**
     * 补发左键点击的最大等待 tick 数。
     */
    private const val ATTACK_CLICK_RETRY_TICKS = 10

    private const val MAX_TARGETS = 64

    private enum class MaceSwapMode {
        Normal,
        Silent,
        InvSwitch,
    }

    /**
     * 目标 ESP 样式；DeobfESP 由 KillAura 全局驱动，不在此重复渲染。
     */
    private enum class ESPMode {
        CaptureMark,
        Circle,
        Firefly,
    }

    private val range by doubleSetting("Range", 4.0, 0.0, 6.0, 0.1)
    private val fov by intSetting("FOV", 360, 10, 360, 1)
    private val autoCharge by boolSetting("Auto Charge", true)
    private val move by boolSetting("Move", true)
    private val rotationSpeed by intSetting("Rotation Speed", 180, 10, 180, 10)
    private val rotationPriority by enumSetting("Rotation Priority", Priority.High)

    private val players by boolSetting("Players", true)
    private val mobs by boolSetting("Mobs", true)
    private val animals by boolSetting("Animals", true)
    private val villagers by boolSetting("Villagers", false)
    private val invisible by boolSetting("Invisible", true)

    private val mace by boolSetting("Mace", true)
    private val maceSwapMode by enumSetting(
        "Mace Swap Mode", MaceSwapMode.Silent, dependency = { mace })

    private val esp by boolSetting("ESP", true)
    private val espMode by enumSetting("ESP Mode", ESPMode.Circle, dependency = { esp })
    private val espColor1 by colorSetting(
        "ESP Main", Color(255, 183, 197),
        dependency = { esp && espMode == ESPMode.CaptureMark })
    private val espColor2 by colorSetting(
        "ESP Second", Color(255, 133, 161),
        dependency = { esp && espMode == ESPMode.CaptureMark })
    private val espSize by doubleSetting(
        "ESP Size", 1.2, 0.5, 3.0, 0.1,
        dependency = { esp && espMode == ESPMode.CaptureMark })
    private val espRotSpeed by doubleSetting(
        "Rot Speed", 2.0, 0.5, 10.0, 0.1,
        dependency = { esp && espMode == ESPMode.CaptureMark })
    private val waveSpeed by doubleSetting(
        "Wave Speed", 3.0, 0.5, 10.0, 0.1,
        dependency = { esp && espMode == ESPMode.CaptureMark })
    private val sideColor by colorSetting(
        "Side Color", Color.WHITE, false,
        dependency = { esp && espMode == ESPMode.Circle })
    private val lineColor by colorSetting(
        "Line Color", Color(255, 255, 255, 233),
        dependency = { esp && espMode == ESPMode.Circle })
    private val circleAlphaFactor by doubleSetting(
        "Circle Alpha Factor", 1.0, 0.0, 2.0, 0.05,
        dependency = { esp && espMode == ESPMode.Circle })
    private val fireflyColorMode by enumSetting(
        "Firefly Color Mode", FireflyESP.ColorMode.Blend,
        dependency = { esp && espMode == ESPMode.Firefly })
    private val fireflyColor by colorSetting(
        "Firefly Color", Color(149, 149, 149, 255),
        dependency = { esp && espMode == ESPMode.Firefly })
    private val fireflyColor2 by colorSetting(
        "Firefly Color 2", Color(255, 133, 161, 255),
        dependency = { esp && espMode == ESPMode.Firefly && fireflyColorMode == FireflyESP.ColorMode.Blend })
    private val fireflyColorMix by doubleSetting(
        "Firefly Color Mix", 0.65, 0.0, 1.0, 0.05,
        dependency = { esp && espMode == ESPMode.Firefly && fireflyColorMode == FireflyESP.ColorMode.Blend })
    private val fireflyColorSpeed by doubleSetting(
        "Firefly Color Speed", 1.2, 0.1, 6.0, 0.1,
        dependency = { esp && espMode == ESPMode.Firefly && fireflyColorMode == FireflyESP.ColorMode.Blend })
    private val fireflyRainbowSpeed by doubleSetting(
        "Firefly Rainbow Speed", 1.0, 0.1, 6.0, 0.1,
        dependency = { esp && espMode == ESPMode.Firefly && fireflyColorMode == FireflyESP.ColorMode.Rainbow })
    private val fireflyRainbowSaturation by doubleSetting(
        "Firefly Rainbow Saturation", 0.85, 0.1, 1.0, 0.05,
        dependency = { esp && espMode == ESPMode.Firefly && fireflyColorMode == FireflyESP.ColorMode.Rainbow })
    private val fireflyRainbowBrightness by doubleSetting(
        "Firefly Rainbow Brightness", 1.0, 0.1, 1.0, 0.05,
        dependency = { esp && espMode == ESPMode.Firefly && fireflyColorMode == FireflyESP.ColorMode.Rainbow })
    private val fireflyLength by intSetting(
        "Firefly Length", 14, 8, 128, 1, dependency = { esp && espMode == ESPMode.Firefly })
    private val fireflyFactor by intSetting(
        "Firefly Factor", 8, 1, 10, 1, dependency = { esp && espMode == ESPMode.Firefly })
    private val fireflyShaking by doubleSetting(
        "Firefly Shaking", 1.8, 0.25, 10.0, 0.25, dependency = { esp && espMode == ESPMode.Firefly })
    private val fireflyAmplitude by doubleSetting(
        "Firefly Amplitude", 3.0, 0.0, 10.0, 0.25, dependency = { esp && espMode == ESPMode.Firefly })

    var target: LivingEntity? = null
    private var targets: List<LivingEntity>? = null

    /**
     * 本 tick 生效的静默瞄准角度；非 null 表示正在瞄准，移动按它结算。
     */
    private var aimRotations: Rot2f? = null
    /**
     * 本 tick 是否允许在没有输入时模拟按 W 沿瞄准方向前进。
     */
    private var forceForward = false
    private var aimActive = false

    /**
     * 长矛 kinetic 命中包来自网络线程，先转成客户端 tick 消费的状态。
     */
    @Volatile
    private var spearHitPending = false

    @Volatile
    private var localPlayerId = -1
    /**
     * 长矛命中后延迟 1 tick 再补重锤，避免和 kinetic 命中同一 tick 处理。
     */
    private var spearMaceDelay = 0
    /**
     * 命中后暂停瞄准/移动，等待目标无敌帧结束。
     */
    private var hitPauseTicks = 0

    private var autoChargeWanted = false
    /**
     * 本模块是否替玩家按住了右键；只有本字段为 true 时才允许松开按键。
     */
    private var autoChargeKeyHeld = false
    /**
     * 蓄力期间被原版消耗的左键点击，使用状态结束后补发；0 表示无待补点击。
     */
    private var pendingAttackClick = 0

    init {
        nonNullListener<ClientTickEvent.Pre> {
            localPlayerId = player.id
            forceForward = false
            // 蓄力按键在低优先级监听器同步，晚于本 tick 的瞄准请求。
            autoChargeWanted = isHoldingSpear()

            val newTargets = TargetManager.acquireTargets(
                TargetRequest.of(
                    range,
                    fov.toFloat(),
                    players,
                    mobs,
                    animals,
                    villagers,
                    false,
                    false,
                    false,
                    invisible,
                    MAX_TARGETS
                )
            )
            targets = newTargets

            if (newTargets.isEmpty()) {
                target = null
                aimRotations = null
                aimActive = false
                return@nonNullListener
            }

            val currentTarget = newTargets.first()
            target = currentTarget
            aimActive = isHoldingSpear() || isUsingSpear()
            if (!aimActive) {
                aimRotations = null
                return@nonNullListener
            }

            // 命中后的无敌帧窗口内只暂停，不再瞄准/移动；重锤补刀窗口例外。蓄力不受影响。
            if (hitPauseTicks > 0 && spearMaceDelay <= 0) {
                hitPauseTicks--
                aimRotations = null
                return@nonNullListener
            }

            if (getEyeDistanceToEntity(currentTarget) <= SPEAR_MIN_TRACK_DISTANCE) {
                aimRotations = null
                return@nonNullListener
            }

            if (getRelativeSpeedTo(currentTarget) <= SPEAR_MIN_RELATIVE_SPEED) {
                aimRotations = null
                return@nonNullListener
            }

            forceForward = move

            val rotations = calculate(currentTarget, true, range)
            if (raytrace(rotations, range)?.type == HitResult.Type.BLOCK) {
                aimRotations = null
                return@nonNullListener
            }
            aimRotations = rotations
            RotationManager.INSTANCE.setRotations(
                rotations,
                rotationSpeed.toDouble(),
                { rotation ->
                    val hit = raytrace(rotation, range)
                    hit is EntityHitResult && hit.entity === target
                },
                rotationPriority
            )
        }

        /**
         * 沿托管旋转方向移动，直接使用原始 WASD 输入，避免与 MovementFix 重复旋转。
         */
        nonNullListener<KeyboardInputEvent>(EventPriority.LOWEST) { event ->
            if (!aimActive || !move || aimRotations == null) return@nonNullListener

            var forward = (if (mc.options.keyUp.isDown) 1.0f else 0.0f) -
                (if (mc.options.keyDown.isDown) 1.0f else 0.0f)
            val strafe = (if (mc.options.keyLeft.isDown) 1.0f else 0.0f) -
                (if (mc.options.keyRight.isDown) 1.0f else 0.0f)

            if (forward == 0.0f && strafe == 0.0f) {
                if (!forceForward) return@nonNullListener
                forward = 1.0f
            }

            event.forward = forward
            event.strafe = strafe
            event.apply { isSprint = true }
        }

        nonNullListener<Render3DEvent> { event ->
            val currentTarget = target
            if (!esp || currentTarget == null || !currentTarget.isAlive) return@nonNullListener

            val stack: PoseStack = event.poseStack

            AntiAlias.render {
                when (espMode) {
                    ESPMode.CaptureMark -> CaptureMarkESP.render(
                        stack,
                        currentTarget,
                        espSize,
                        espRotSpeed,
                        waveSpeed,
                        espColor1,
                        espColor2
                    )

                    ESPMode.Circle -> CircleESP.render(
                        stack,
                        currentTarget,
                        sideColor,
                        lineColor,
                        circleAlphaFactor.toFloat()
                    )

                    ESPMode.Firefly -> FireflyESP.render(
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
                        fireflyRainbowBrightness
                    )
                }
            }
        }

        handler<PacketEvent.Receive> { event ->
            if (!isEnabled) return@handler
            val packet = event.packet as? ClientboundEntityEventPacket ?: return@handler
            if (packet.eventId != EntityEvent.KINETIC_HIT) return@handler
            if (packet is ClientboundEntityEventPacketAccessor &&
                packet.`epsilon$getEntityId`() == localPlayerId
            ) {
                spearHitPending = true
            }
        }

        nonNullListener<PlayerTickEvent.Pre> {
            if (spearHitPending) {
                spearHitPending = false
                if (isUsingSpear()) {
                    interaction.releaseUsingItem(player)
                }
                spearMaceDelay = 1
                forceForward = false
                // 命中包可能在 ClientTickEvent.Pre 之后才到达，这里再松一次按键，防止下一 tick 原版重新起手。
                stopAutoCharge()
                return@nonNullListener
            }

            if (spearMaceDelay > 0) {
                if (--spearMaceDelay == 0) {
                    if (isUsingSpear()) {
                        interaction.releaseUsingItem(player)
                    }
                    val currentTarget = target
                    if (currentTarget != null && currentTarget.isAlive) {
                        attackWithMace(currentTarget)
                    }
                    hitPauseTicks = HIT_PAUSE_TICKS
                    forceForward = false
                    stopAutoCharge()
                }
            }
        }

        /**
         * 通过按住右键维持蓄力，左键攻击时让位并补发点击。
         * 必须晚于 onClientTick 执行，避免读取上一 tick 的状态。
         */
        nonNullListener<ClientTickEvent.Pre>(EventPriority.LOWEST) {
            val attackDown = mc.options.keyAttack.isDown

            if (attackDown && autoChargeKeyHeld && player.isUsingItem) {
                pendingAttackClick = ATTACK_CLICK_RETRY_TICKS
            }

            if (attackDown) {
                stopAutoCharge()
            }

            if (pendingAttackClick > 0) {
                // 等待使用状态结束期间保持右键释放。
                if (!player.isUsingItem) {
                    KeyMapping.click(mc.options.keyAttack.key)
                    pendingAttackClick = 0
                } else {
                    pendingAttackClick--
                }
                return@nonNullListener
            }

            if (attackDown) return@nonNullListener

            if (!autoCharge || !autoChargeWanted ||
                spearHitPending || spearMaceDelay > 0 || !isHoldingSpear()
            ) {
                stopAutoCharge()
                return@nonNullListener
            }

            if (!mc.options.keyUse.isDown) {
                mc.options.keyUse.isDown = true
                autoChargeKeyHeld = true
            }
        }
    }

    override fun getInfo(): String? {
        return if (target == null) null else target!!.name.string
    }

    override fun onEnable() {
        resetState()
    }

    override fun onDisable() {
        resetState()
    }

    /**
     * 松开自动蓄力按住的右键；玩家自己按住的键不处理。
     */
    private fun stopAutoCharge() {
        if (!autoChargeKeyHeld) return
        autoChargeKeyHeld = false
        // 配置恢复可能早于 Minecraft 构造完成，此时还没有 Options。
        if (mc.options != null) {
            mc.options.keyUse.isDown = false
        }
    }

    context(nn: NonNullContext)
    private fun attackEntity(entity: Entity) {
        interaction.attack(player, entity)
        PlayerUtils.swingHand(InteractionHand.MAIN_HAND)
    }

    /**
     * 长矛 kinetic 命中后立刻切换重锤再补一次攻击。
     *
     * Normal 保留切换结果，Silent 只静默切换快捷栏，InvSwitch 通过容器交换从背包取物。
     */
    context(nn: NonNullContext)
    private fun attackWithMace(entity: Entity) {
        if (!mace || player.fallDistance <= MACE_MIN_FALL_DISTANCE || !entity.isAlive) return

        val maceResult: FindItemResult = findMace()
        if (!maceResult.found()) return

        val selectedSlot = player.inventory.getSelectedSlot()
        if (maceResult.slot == selectedSlot) {
            attackEntity(entity)
            return
        }

        when (maceSwapMode) {
            MaceSwapMode.Normal, MaceSwapMode.Silent -> player.inventory.setSelectedSlot(maceResult.slot)
            MaceSwapMode.InvSwitch -> InvUtils.invSwap(maceResult.slot)
        }

        attackEntity(entity)

        when (maceSwapMode) {
            MaceSwapMode.Normal -> {
            }

            MaceSwapMode.Silent -> player.inventory.setSelectedSlot(selectedSlot)
            MaceSwapMode.InvSwitch -> InvUtils.invSwapBack()
        }
    }

    context(nn: NonNullContext)
    private fun findMace(): FindItemResult {
        // 攻击只结算主手，因此排除副手槽位 40；Silent 只能操作快捷栏，InvSwitch 才搜索整个主背包。
        if (maceSwapMode == MaceSwapMode.InvSwitch) {
            return InvUtils.find({ stack -> stack.`is`(Items.MACE) }, 0, 35)
        }
        return InvUtils.find({ stack -> stack.`is`(Items.MACE) }, 0, 8)
    }

    context(nn: NonNullContext)
    private fun isHoldingSpear(): Boolean {
        return player.mainHandItem.`is`(ItemTags.SPEARS)
    }

    context(nn: NonNullContext)
    private fun isUsingSpear(): Boolean {
        return player.isUsingItem && player.useItem.`is`(ItemTags.SPEARS)
    }

    /**
     * 沿眼部到目标方向投影双方速度差，单位米/秒。
     */
    context(nn: NonNullContext)
    private fun getRelativeSpeedTo(entity: Entity): Double {
        val look = RotationUtils.getRotationsToEntity(entity)
        val direction = Vec3.directionFromRotation(look.pitch, look.yaw)
        val relativeMotion = player.getKnownSpeed().subtract(entity.getKnownSpeed()).scale(20.0)
        return Math.max(0.0, direction.dot(relativeMotion))
    }

    private fun resetState() {
        target = null
        targets = null
        aimRotations = null
        forceForward = false
        aimActive = false
        spearHitPending = false
        spearMaceDelay = 0
        hitPauseTicks = 0
        autoChargeWanted = false
        stopAutoCharge()
        pendingAttackClick = 0
        localPlayerId = -1
    }

}
