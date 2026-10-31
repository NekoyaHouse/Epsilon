package com.github.epsilon.modules.impl.combat

import com.github.epsilon.events.api.safeEventHandler
import com.github.epsilon.events.impl.ClientTickEvent
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.kernel.jvm.NonNullContext
import com.github.epsilon.kernel.jvm.player
import com.github.epsilon.managers.RotationManager
import com.github.epsilon.managers.target.TargetManager
import com.github.epsilon.managers.target.TargetRequest
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.modules.impl.movement.Scaffold
import com.github.epsilon.utils.rotation.Priority
import com.github.epsilon.utils.rotation.RaytraceUtils
import com.github.epsilon.utils.rotation.RotationUtils
import com.github.epsilon.utils.timer.TimerUtils
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

object AutoThrow : Module("Auto Throw", Category.COMBAT) {

    private val minRange by doubleSetting("Min Range", 3.0, 0.0..10.0, 0.1)
    private var maxRange by doubleSetting("Max Range", 8.0, 2.0..16.0, 0.1)
    private val fov by intSetting("Fov", 90, 1..360, 1)
    private val delay by intSetting("Delay", 500, 0..2000, 10)
    private val rotationSpeed by intSetting("Rotation Speed", 180, 1..180, 10)
    private val rotationPriority by enumSetting("Rotation Priority", Priority.Lowest)

    private val timer = TimerUtils()
    private var shouldThrow = false
    private var lastSlot = -1

    private var currentTarget: LivingEntity? = null

    init {
        safeEventHandler<ClientTickEvent.Pre> {
            val slot = getThrowSlot()
            if (player.mainHandItem.`is`(Items.SNOWBALL) ||
                player.mainHandItem.`is`(Items.EGG) ||
                player.offhandItem.`is`(Items.SNOWBALL) ||
                player.offhandItem.`is`(Items.EGG) ||
                slot != -1
            ) {
                // 搜索并准备投掷
                if (timer.passedMillise(delay.toLong()) && canWork()) {
                    val maxRange = this@AutoThrow.maxRange
                    val maxRangeSq = maxRange * maxRange

                    val candidates = ArrayList(
                        TargetManager.acquireTargets(
                            TargetRequest.of(
                                maxRange,
                                fov.toFloat(),
                                true,
                                false,
                                false,
                                false,
                                false,
                                false,
                                false,
                                true,
                                64,
                            )
                        )
                    )

                    var best: LivingEntity? = null
                    var minDstSq = Double.MAX_VALUE
                    for (entity in candidates) {
                        val distSq = player.distanceToSqr(entity)
                        if (distSq > maxRangeSq) continue

                        val dx = entity.x - player.x
                        val dz = entity.z - player.z
                        if (sqrt(dx * dx + dz * dz) < minRange) continue

                        if (distSq < minDstSq) {
                            minDstSq = distSq
                            best = entity
                        }
                    }

                    currentTarget = best
                    val target = currentTarget ?: return@safeEventHandler

                    val rotation = RotationUtils.calculate(getAimVec(target), false)

                    RotationManager.setRotations(rotation, rotationSpeed.toDouble(), rotationPriority)

                    val hit = RaytraceUtils.raytrace(rotation, maxRange) ?: return@safeEventHandler
                    if (hit.type != HitResult.Type.ENTITY) return@safeEventHandler

                    // 准备槽位与投掷状态
                    val off = player.offhandItem
                    val main = player.mainHandItem
                    if (!isThrowable(off) && !isThrowable(main)) {
                        lastSlot = player.inventory.selectedSlot
                        player.inventory.selectedSlot = slot
                    }

                    shouldThrow = true
                    timer.reset()
                } else {
                    currentTarget = null
                }
            }
        }

        safeEventHandler<PlayerTickEvent.Pre> {
            if (shouldThrow && canWork()) {
                var used = false
                val off = player.offhandItem
                val main = player.mainHandItem
                if (isThrowable(off)) {
                    gameMode.useItem(player, InteractionHand.OFF_HAND)
                    used = true
                } else if (isThrowable(main)) {
                    gameMode.useItem(player, InteractionHand.MAIN_HAND)
                    used = true
                }
                if (used) {
                    if (lastSlot != -1) {
                        player.inventory.selectedSlot = lastSlot
                    }
                    resetThrowState()
                }
            } else {
                if (lastSlot != -1) {
                    player.inventory.selectedSlot = lastSlot
                    lastSlot = -1
                }
                shouldThrow = false
            }
        }
    }

    override fun onEnable() {
        timer.reset()
        shouldThrow = false
        lastSlot = -1
        currentTarget = null
    }

    override fun onDisable() {
        shouldThrow = false
        currentTarget = null
        val localPlayer = mc.player
        if (localPlayer != null && lastSlot != -1) {
            localPlayer.inventory.selectedSlot = lastSlot
            lastSlot = -1
        }
    }

    override fun getInfo(): String? {
        return currentTarget?.name?.string
    }

    private fun resetThrowState() {
        shouldThrow = false
        currentTarget = null
        timer.reset()
    }

    private fun isThrowable(stack: ItemStack): Boolean {
        return !stack.isEmpty && (stack.`is`(Items.SNOWBALL) || stack.`is`(Items.EGG))
    }

    private fun getThrowSlot(): Int {
        for (i in 0 until 9) {
            val stack = mc.player?.inventory?.nonEquipmentItems?.get(i) ?: return -1
            if (isThrowable(stack)) return i
        }
        return -1
    }

    context(nn: NonNullContext)
    private fun canWork(): Boolean {
        if (player.isUsingItem) {
            return false
        }

        if (mc.gui.screen() != null) {
            return false
        }

        if (Scaffold.isEnabled && !player.onGround()) {
            return false
        }

        val killAuraTarget = KillAura.target
        return !(KillAura.isEnabled && killAuraTarget != null && RotationUtils.getEyeDistanceToEntity(killAuraTarget) > KillAura.aimRange) && (mc.options.keyUse.isDown || !mc.options.keyAttack.isDown)
    }

    // 通过离散模拟计算可行的命中方向，移植自现有实现并适配 Vec3
    context(nn: NonNullContext)
    private fun getAimVec(livingEntity: LivingEntity): Vec3 {
        val shooterPosNow = player.eyePosition
        val shooterVelNow = player.deltaMovement

        val targetPosNow = livingEntity.boundingBox.center
        val targetVelNow = livingEntity.deltaMovement

        // 目标 AABB 半尺寸（包含 ~0.15 的投掷物半径冗余）
        val bb = livingEntity.boundingBox
        val hx = (bb.maxX - bb.minX) * 0.5 + 0.15
        val hy = (bb.maxY - bb.minY) * 0.5 + 0.15
        val hz = (bb.maxZ - bb.minZ) * 0.5 + 0.15

        // 抛物线参数（雪球/鸡蛋）
        val s = 1.5   // 初速
        val g = 0.03  // 重力
        val k = 0.99  // 阻力

        val hasInHand = isThrowable(player.mainHandItem) || isThrowable(player.offhandItem)
        val fireDelay = if (hasInHand) 2 else 3

        // 只在 XZ 上外推到开火时刻；Y 维持当前高度
        val shooterPosFire = Vec3(
            shooterPosNow.x + shooterVelNow.x * fireDelay,
            shooterPosNow.y,
            shooterPosNow.z + shooterVelNow.z * fireDelay,
        )
        val targetPosFire = Vec3(
            targetPosNow.x + targetVelNow.x * fireDelay,
            targetPosNow.y,
            targetPosNow.z + targetVelNow.z * fireDelay,
        )

        val r = targetPosFire.subtract(shooterPosFire)
        val targetVelXZ = Vec3(targetVelNow.x, 0.0, targetVelNow.z)

        var bestDir: Vec3? = null
        var bestScore = Double.POSITIVE_INFINITY

        val nMax = 80 // 搜索最大飞行 tick
        for (n in 2..nMax) {
            val rhX = r.x + targetVelXZ.x * n
            val rhZ = r.z + targetVelXZ.z * n
            val rhLen = sqrt(rhX * rhX + rhZ * rhZ)
            if (rhLen < 1e-6) continue

            val dirHx = rhX / rhLen
            val dirHz = rhZ / rhLen

            val sn = geomSum(k, n)
            val tn = triGeomSum(k, n)

            val cosTheta = (rhLen / sn) / s
            val sinTheta = (r.y + g * tn) / (s * sn)

            if (!cosTheta.isFinite() || !sinTheta.isFinite()) continue
            if (cosTheta < -1 || cosTheta > 1 || sinTheta < -1 || sinTheta > 1) continue
            val norm2 = cosTheta * cosTheta + sinTheta * sinTheta
            if (norm2 > 1.0005) continue

            var dir = Vec3(dirHx * cosTheta, sinTheta, dirHz * cosTheta)
            val l = sqrt(dir.x * dir.x + dir.y * dir.y + dir.z * dir.z)
            if (l < 1e-6) continue
            dir = Vec3(dir.x / l, dir.y / l, dir.z / l)

            val err = simulatePathAABBError(
                shooterPosFire,
                Vec3.ZERO,
                dir,
                targetPosFire,
                targetVelXZ,
                n, s, g, k,
                hx, hy, hz,
                false,
            )

            val score = err + n * 0.002
            if (score < bestScore) {
                bestScore = score
                bestDir = dir
            }
        }

        return if (bestDir != null) {
            shooterPosNow.add(bestDir)
        } else {
            val d = shooterPosNow.distanceTo(targetPosNow)
            val t = maxOf(d / maxOf(s, 1e-3), 0.05)
            val lead = targetPosNow.add(targetVelXZ.scale(t))
            Vec3(lead.x, lead.y + 0.5 * g * t * t, lead.z)
        }
    }

    private fun simulatePathAABBError(
        shooterPos: Vec3,
        shooterVel: Vec3,
        dirUnit: Vec3,
        targetPos0: Vec3,
        targetVel0: Vec3,
        n: Int,
        speed: Double,
        gProj: Double,
        kProj: Double,
        hx: Double,
        hy: Double,
        hz: Double,
        dampedY: Boolean,
    ): Double {
        var p = shooterPos
        var v = Vec3(
            dirUnit.x * speed + shooterVel.x,
            dirUnit.y * speed + shooterVel.y,
            dirUnit.z * speed + shooterVel.z,
        )

        var t = targetPos0
        var tvx = targetVel0.x
        var tvy = targetVel0.y
        var tvz = targetVel0.z

        var minDist = Double.POSITIVE_INFINITY

        for (i in 0 until n) {
            p = p.add(v)
            v = Vec3(v.x * kProj, v.y * kProj - gProj, v.z * kProj)

            if (dampedY) {
                tvy = tvy * 0.85 - 0.08
            }
            t = Vec3(t.x + tvx, t.y + tvy, t.z + tvz)

            val d = distancePointToAABB(p, t, hx, hy, hz)
            if (d < minDist) minDist = d
        }

        return minDist
    }

    private fun distancePointToAABB(point: Vec3, center: Vec3, hx: Double, hy: Double, hz: Double): Double {
        val dx = maxOf(0.0, abs(point.x - center.x) - hx)
        val dy = maxOf(0.0, abs(point.y - center.y) - hy)
        val dz = maxOf(0.0, abs(point.z - center.z) - hz)
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun geomSum(k: Double, n: Int): Double {
        if (n <= 0) return 0.0
        if (abs(1.0 - k) < 1e-9) return n.toDouble()
        return (1.0 - (k).pow(n.toDouble())) / (1.0 - k)
    }

    private fun triGeomSum(k: Double, n: Int): Double {
        if (n <= 1) return 0.0
        if (abs(1.0 - k) < 1e-9) return (n - 1) * n / 2.0
        val oneMinusK = (1.0 - k)
        return ((n - 1) / oneMinusK) - (k * (1.0 - (k).pow((n - 1).toDouble())) / (oneMinusK * oneMinusK))
    }

}
