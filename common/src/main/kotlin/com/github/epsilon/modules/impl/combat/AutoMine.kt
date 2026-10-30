package com.github.epsilon.modules.impl.combat

import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.events.impl.ClientTickEvent
import com.github.epsilon.managers.NotificationManager
import com.github.epsilon.managers.target.TargetManager
import com.github.epsilon.managers.target.TargetRequest
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.Setting
import com.github.epsilon.settings.SettingGroup
import com.github.epsilon.settings.getValue
import com.github.epsilon.settings.impl.BoolSetting
import com.github.epsilon.settings.impl.DoubleSetting
import com.github.epsilon.settings.impl.EnumSetting
import com.github.epsilon.settings.impl.IntSetting
import com.github.epsilon.utils.NonNullContext
import com.github.epsilon.utils.player
import com.github.epsilon.utils.timer.TimerUtils
import com.github.epsilon.utils.world
import com.github.epsilon.utils.world.hole.HoleUtils
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import java.util.Comparator
import java.util.HashMap

/**
 * 自动挖掘：在无政府服务器的水晶战斗中，拆掉敌人用来阻挡爆炸射线的坑、卡位方块与脚下支撑。
 *
 * 本模块只负责挑选目标方块，实际破坏交给 [PacketMine] 执行，因此依赖其处于启用状态。
 */
object AutoMine : Module("Auto Mine", Category.COMBAT) {

    enum class TargetOrder {
        Distance,
        Health,
        Hole,
    }

    enum class HeadMode {
        Inside,
        Above,
        Both,
    }

    enum class FeetMode {
        Below,
        Inside,
        Both,
    }

    enum class FeetLogic {
        /** 优先挖掘当前目标脚下的方块。 */
        First,

        /** 所有候选方块按距离排序。 */
        Normal,

        /** 最后挖掘当前目标脚下的方块。 */
        Last,
    }

    private enum class TargetCategory {
        Feet,
        Head,
        Box,
    }

    private data class Candidate(val pos: BlockPos, val category: TargetCategory)

    /** 坑（盒子）的水平方向：正前后左右四格。 */
    private val SIDE_OFFSETS = arrayOf(intArrayOf(1, 0), intArrayOf(-1, 0), intArrayOf(0, 1), intArrayOf(0, -1))

    /** 坑（盒子）的角落方向：四个斜角，仅在开启角落选项时使用。 */
    private val CORNER_OFFSETS = arrayOf(intArrayOf(1, 1), intArrayOf(1, -1), intArrayOf(-1, 1), intArrayOf(-1, -1))

    /** 用于表示「立即可挖」的计时起点：远早于当前时间，使首次选择不受切换延迟限制。 */
    private const val FIRST_PICK_DELAY = 917813L

    private const val PAUSE_NOTIFY_INTERVAL = 5000L

    /**
     * 服务端拒绝破坏（例如领地保护、反作弊）时客户端方块仍会保留，
     * 这里对这类方块设置短暂冷却，避免对同一位置无限重试。
     */
    private const val FAILED_BLOCK_COOLDOWN = 3000L

    private val sgGeneral: SettingGroup = settingGroup("General")
    private val sgTargets: SettingGroup = settingGroup("Targets")
    private val sgFeet: SettingGroup = settingGroup("Feet Settings")
    private val sgBox: SettingGroup = settingGroup("Box Settings")

    private val range by doubleSetting("Range", 4.5, 1.0, 8.0, 0.1).group(sgGeneral)
    private val order: EnumSetting<TargetOrder> by enumSetting("Order", TargetOrder.Distance).group(sgGeneral)
    private val invisible by boolSetting("Invisible", false).group(sgGeneral)
    private val friends by boolSetting("Friends", false).group(sgGeneral)
    private val delay by intSetting("Switch Delay", 100, 0, 1000, 10).group(sgGeneral)
    private val autoPacketMine by boolSetting("Auto Packet Mine", true).group(sgGeneral)

    private val mineBox by boolSetting("Box", true).group(sgTargets)
    private val mineHead by boolSetting("Head", false).group(sgTargets)
    private val mineFeet by boolSetting("Feet", true).group(sgTargets)
    private val headMode: EnumSetting<HeadMode> by enumSetting("Head Mode", HeadMode.Both, Setting.Dependency { mineHead }).group(sgTargets)

    private val feetMode: EnumSetting<FeetMode> by enumSetting("Feet Mode", FeetMode.Below, Setting.Dependency { mineFeet }).group(sgFeet)
    private val feetLogic: EnumSetting<FeetLogic> by enumSetting("Feet Logic", FeetLogic.First, Setting.Dependency { mineFeet }).group(sgFeet)
    private val feetOnGround by boolSetting("Only On Ground", false, Setting.Dependency { mineFeet }).group(sgFeet)

    private val boxCorners by boolSetting("Box Corners", false, Setting.Dependency { mineBox }).group(sgBox)
    private val boxHeadLevel by boolSetting("Box Head Level", false, Setting.Dependency { mineBox }).group(sgBox)

    private val switchTimer = TimerUtils()
    private val pauseNotifyTimer = TimerUtils()

    private val failedUntil: MutableMap<BlockPos, Long> = HashMap()

    /** 最近一次交给 Packet Mine 的方块，用于判断它是否被服务端拒绝破坏。 */
    private var pendingPos: BlockPos? = null

    private var autoEnabledPacketMine = false

    init {
        nonNullListener<ClientTickEvent.Pre> {
            if (!player.isAlive) return@nonNullListener

            if (!PacketMine.isEnabled) {
                pendingPos = null
                notifyPacketMinePaused()
                return@nonNullListener
            }

            checkPendingBlock()

            // Packet Mine 同一时间只维护一个（双挖时两个）目标，必须等它挖完再切换，否则会反复重置进度。
            // 忙碌期间持续重置计时器，使切换延迟真正从当前方块挖完的时刻开始计算。
            if (PacketMine.isBusy()) {
                switchTimer.reset()
                return@nonNullListener
            }
            if (!switchTimer.passedMillise(delay.toLong())) return@nonNullListener

            val candidates = findCandidates()
            if (candidates.isEmpty()) return@nonNullListener

            pendingPos = candidates.first().pos
            PacketMine.mine(pendingPos!!)
            switchTimer.reset()
        }
    }

    override fun onEnable() {
        switchTimer.setMs(FIRST_PICK_DELAY)
        pauseNotifyTimer.reset()
        failedUntil.clear()
        pendingPos = null
        autoEnabledPacketMine = false

        if (NonNullContext.current == null) {
            toggle()
            return
        }

        if (PacketMine.isEnabled) return

        if (autoPacketMine) {
            PacketMine.isEnabled = true
            autoEnabledPacketMine = true
            NotificationManager.INSTANCE.info(translatedName, EpsilonTranslations.AutoMine.PACKET_MINE_ENABLED.translatedName)
        } else {
            NotificationManager.INSTANCE.error(translatedName, EpsilonTranslations.AutoMine.PACKET_MINE_REQUIRED.translatedName)
            isEnabled = false
        }
    }

    override fun onDisable() {
        // 只回收本模块自动开启的 Packet Mine，用户原本就开启的状态保持不变。
        if (autoEnabledPacketMine && PacketMine.isEnabled) {
            PacketMine.isEnabled = false
        }
        autoEnabledPacketMine = false
        failedUntil.clear()
        pendingPos = null
    }

    /** Packet Mine 空闲后仍未消失的方块视为服务端拒绝破坏，加入冷却名单。 */
    context(nn: NonNullContext)
    private fun checkPendingBlock() {
        val now = System.currentTimeMillis()
        failedUntil.values.removeAll { expireAt -> expireAt <= now }
        val pending = pendingPos ?: return
        if (PacketMine.isBusy()) return

        if (!isAirOrReplacable(pending) && withinMineRange(pending)) {
            failedUntil[pending] = now + FAILED_BLOCK_COOLDOWN
        }
        pendingPos = null
    }

    context(nn: NonNullContext)
    private fun withinMineRange(pos: BlockPos): Boolean {
        val limit = Math.min(range, PacketMine.getRange().toDouble())
        return nn.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) < limit * limit
    }

    context(nn: NonNullContext)
    private fun isAirOrReplacable(pos: BlockPos): Boolean {
        val state = nn.world.getBlockState(pos)
        return state.isAir || state.canBeReplaced()
    }

    private fun notifyPacketMinePaused() {
        if (!pauseNotifyTimer.passedMillise(PAUSE_NOTIFY_INTERVAL)) return
        pauseNotifyTimer.reset()
        NotificationManager.INSTANCE.warning(
            translatedName,
            EpsilonTranslations.AutoMine.PACKET_MINE_DISABLED.translatedName,
            hashCode(),
        )
    }

    /** 按目标排序逐个玩家收集候选方块，返回第一个存在可挖方块的玩家的候选列表。 */
    context(nn: NonNullContext)
    private fun findCandidates(): List<Candidate> {
        val targets = ArrayList(TargetManager.INSTANCE.acquireTargets(createTargetRequest()))
        targets.sortWith(createTargetComparator())

        for (living in targets) {
            if (living !is Player) continue
            val candidates = collectCandidates(living)
            if (candidates.isEmpty()) continue
            sortCandidates(candidates)
            return candidates
        }
        return emptyList()
    }

    private fun createTargetRequest(): TargetRequest {
        return TargetRequest.of(
            range,
            360.0f,
            true,
            false,
            false,
            false,
            false,
            false,
            false,
            invisible,
            friends,
            { true },
            64,
        )
    }

    context(nn: NonNullContext)
    private fun createTargetComparator(): Comparator<LivingEntity> {
        val byDistance = Comparator<LivingEntity> { a, b ->
            nn.player.distanceToSqr(a).compareTo(nn.player.distanceToSqr(b))
        }
        return when (order.value) {
            TargetOrder.Distance -> byDistance

            TargetOrder.Health -> Comparator.comparingDouble<LivingEntity> { living -> living.health.toDouble() }.thenComparing(byDistance)

            TargetOrder.Hole -> Comparator.comparingInt<LivingEntity> { living ->
                if (living is Player && HoleUtils.inHole(living)) 0 else 1
            }.thenComparing(byDistance)
        }
    }

    /** 收集目标玩家周围所有可挖方块，并按目标类型去重。 */
    context(nn: NonNullContext)
    private fun collectCandidates(target: Player): MutableList<Candidate> {
        val candidates = ArrayList<Candidate>()
        val limit = Math.min(range, PacketMine.getRange().toDouble())
        val feet = target.blockPosition()

        if (mineFeet) {
            val belowAllowed = !feetOnGround || target.onGround()
            when (feetMode.value) {
                FeetMode.Below -> {
                    if (belowAllowed) addCandidate(candidates, feet.below(), TargetCategory.Feet, limit)
                }

                FeetMode.Inside -> addCandidate(candidates, feet, TargetCategory.Feet, limit)

                FeetMode.Both -> {
                    if (belowAllowed) addCandidate(candidates, feet.below(), TargetCategory.Feet, limit)
                    addCandidate(candidates, feet, TargetCategory.Feet, limit)
                }
            }
        }

        if (mineHead) {
            val head = feet.above()
            when (headMode.value) {
                HeadMode.Inside -> addCandidate(candidates, head, TargetCategory.Head, limit)

                HeadMode.Above -> addCandidate(candidates, head.above(), TargetCategory.Head, limit)

                HeadMode.Both -> {
                    addCandidate(candidates, head, TargetCategory.Head, limit)
                    addCandidate(candidates, head.above(), TargetCategory.Head, limit)
                }
            }
        }

        if (mineBox) {
            val head = feet.above()
            for (offset in SIDE_OFFSETS) {
                addCandidate(candidates, feet.offset(offset[0], 0, offset[1]), TargetCategory.Box, limit)
                if (boxHeadLevel) {
                    addCandidate(candidates, head.offset(offset[0], 0, offset[1]), TargetCategory.Box, limit)
                }
            }
            if (boxCorners) {
                for (offset in CORNER_OFFSETS) {
                    addCandidate(candidates, feet.offset(offset[0], 0, offset[1]), TargetCategory.Box, limit)
                    if (boxHeadLevel) {
                        addCandidate(candidates, head.offset(offset[0], 0, offset[1]), TargetCategory.Box, limit)
                    }
                }
            }
        }

        return candidates
    }

    context(nn: NonNullContext)
    private fun addCandidate(candidates: MutableList<Candidate>, pos: BlockPos, category: TargetCategory, limit: Double) {
        for (candidate in candidates) {
            if (candidate.pos == pos) return
        }
        if (!isMineable(pos, limit)) return
        candidates.add(Candidate(pos, category))
    }

    /** 复用 Packet Mine 的可挖判定：必须有碰撞体积、可破坏、在 Packet Mine 距离内且不在失败冷却中。 */
    context(nn: NonNullContext)
    private fun isMineable(pos: BlockPos, limit: Double): Boolean {
        if (!nn.world.isLoaded(pos)) return false
        if ((failedUntil[pos] ?: 0L) > System.currentTimeMillis()) return false
        val state = nn.world.getBlockState(pos)
        if (state.isAir || state.canBeReplaced()) return false
        // Packet Mine 对硬度 < 0 的方块永远无法完成挖掘，选入会使其卡在忙碌状态。
        if (state.getDestroySpeed(nn.world, pos) < 0.0f) return false
        if (state.getCollisionShape(nn.world, pos).isEmpty) return false
        // 严格小于 Packet Mine 的 farCancel 阈值，避免在临界距离被反复取消。
        return nn.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) < limit * limit
    }

    context(nn: NonNullContext)
    private fun sortCandidates(candidates: MutableList<Candidate>) {
        val byDistance = Comparator<Candidate> { a, b ->
            nn.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(a.pos))
                .compareTo(nn.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(b.pos)))
        }

        when (feetLogic.value) {
            FeetLogic.First -> {
                val feetFirst = Comparator<Candidate> { a, b ->
                    (if (a.category == TargetCategory.Feet) 0 else 1).compareTo(if (b.category == TargetCategory.Feet) 0 else 1)
                }
                candidates.sortWith(feetFirst.thenComparing(byDistance))
            }

            FeetLogic.Normal -> candidates.sortWith(byDistance)

            FeetLogic.Last -> {
                val feetLast = Comparator<Candidate> { a, b ->
                    (if (a.category == TargetCategory.Feet) 1 else 0).compareTo(if (b.category == TargetCategory.Feet) 1 else 0)
                }
                candidates.sortWith(feetLast.thenComparing(byDistance))
            }
        }
    }

}
