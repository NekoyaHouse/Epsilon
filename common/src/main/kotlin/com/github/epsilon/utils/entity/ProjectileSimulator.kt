package com.github.epsilon.utils.entity

import com.github.epsilon.utils.math.toRadians
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.tags.FluidTags
import net.minecraft.util.Mth
import net.minecraft.world.entity.*
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.*
import net.minecraft.world.entity.projectile.arrow.AbstractArrow
import net.minecraft.world.entity.projectile.arrow.ThrownTrident
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.AbstractWindCharge
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownLingeringPotion
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion
import net.minecraft.world.item.*
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 客户端投掷物弹道模拟器。
 *
 * 逐 tick 复现原版投掷物的重力、阻力、入水与碰撞顺序；不同投掷物通过 [MotionData]
 * 描述参数与更新顺序。世界由构造参数显式传入，不依赖本地玩家上下文。
 */
class ProjectileSimulator(private val level: Level) {

    private enum class UpdateOrder {
        GRAVITY_DRAG_POSITION,
        POSITION_DRAG_GRAVITY,
        GRAVITY_POSITION_DRAG,
    }

    private val blockPos = BlockPos.MutableBlockPos()

    private var positionVec: Vec3 = Vec3.ZERO
    private var velocity: Vec3 = Vec3.ZERO
    private lateinit var collisionSource: Entity
    private var ignoredEntity: Entity? = null
    private lateinit var dimensions: EntityDimensions
    private lateinit var motion: MotionData
    private var inWater: Boolean = false
    private var ticks: Int = 0
    private var pierceLevel: Int = 0
    private val piercedEntities: MutableSet<Entity> = HashSet()

    /**
     * 按手持物品配置弹道。
     */
    fun configureHeld(user: Entity, stack: ItemStack, angleOffset: Double, partialTick: Float, piercing: Int): Boolean {
        val item = stack.item
        val data: MotionData

        if (item is BowItem) {
            if (user !is LivingEntity) return false
            var charge = BowItem.getPowerForTime(user.ticksUsingItem)
            if (charge <= 0.1F) {
                if (user is Player && user.isLocalPlayer) {
                    charge = 1.0F
                } else {
                    return false
                }
            }
            data = ARROW.withPower(charge * 3.0)
        } else if (item is CrossbowItem) {
            return false // 调用方会提供已装填投掷物的精确运动数据。
        } else if (item is FishingRodItem) {
            configureFishing(user, partialTick)
            return true
        } else if (item is TridentItem) data = TRIDENT
        else if (item is SnowballItem) data = SNOWBALL
        else if (item is EggItem) data = EGG
        else if (item is EnderpearlItem) data = ENDER_PEARL
        else if (item is ExperienceBottleItem) data = EXPERIENCE_BOTTLE
        else if (item is SplashPotionItem) data = SPLASH_POTION
        else if (item is LingeringPotionItem) data = LINGERING_POTION
        else if (item is WindChargeItem) data = WIND_CHARGE
        else return false

        configureShot(user, angleOffset, partialTick, data, piercing)
        return true
    }

    /**
     * 按弩的装填物配置弹道。
     */
    fun configureCrossbow(user: Entity, firework: Boolean, angleOffset: Double, partialTick: Float, piercing: Int) {
        val data = if (firework) FIREWORK.withPower(1.6) else ARROW.withPower(3.15)
        configureShot(user, angleOffset, partialTick, data, if (firework) 0 else piercing)
    }

    /**
     * 按已存在的投掷物实体配置弹道。
     */
    fun configureFired(projectile: Projectile): Boolean {
        val data: MotionData
        if (projectile is ThrownTrident) data = TRIDENT
        else if (projectile is AbstractArrow) data = ARROW
        else if (projectile is ThrownEnderpearl) data = ENDER_PEARL
        else if (projectile is ThrownExperienceBottle) data = EXPERIENCE_BOTTLE
        else if (projectile is ThrownSplashPotion) data = SPLASH_POTION
        else if (projectile is ThrownLingeringPotion) data = LINGERING_POTION
        else if (projectile is AbstractWindCharge) data = WIND_CHARGE
        else if (projectile is AbstractHurtingProjectile) data = EXPLOSIVE
        else if (projectile is LlamaSpit) data = LLAMA_SPIT
        else if (projectile is FishingHook) data = FISHING_BOBBER
        else if (projectile is ThrowableProjectile) data = SNOWBALL
        else return false

        positionVec = projectile.position()
        velocity = projectile.deltaMovement
        collisionSource = projectile
        ignoredEntity = projectile.owner
        setMotion(data, projectile.tickCount, 0, projectile.isInWater)
        dimensions = projectile.getDimensions(projectile.pose)
        if (projectile.isNoGravity) {
            motion = MotionData(data.power, data.roll, 0.0, data.airDrag, data.waterDrag, data.entityType, data.updateOrder)
        }
        return velocity.lengthSqr() > 1.0E-8
    }

    /**
     * 当前模拟位置。
     */
    fun position(): Vec3 {
        return positionVec
    }

    /**
     * 推进一步模拟并返回本 tick 的碰撞结果。
     */
    fun tick(): Step {
        val previous = positionVec
        ticks++

        when (motion.updateOrder) {
            UpdateOrder.GRAVITY_DRAG_POSITION -> {
                velocity = velocity.add(0.0, -motion.gravity, 0.0).scale(if (inWater) motion.waterDrag else motion.airDrag)
                positionVec = positionVec.add(velocity)
                updateWaterState()
            }

            UpdateOrder.POSITION_DRAG_GRAVITY -> {
                positionVec = positionVec.add(velocity)
                velocity = velocity.scale(if (inWater) motion.waterDrag else motion.airDrag).add(0.0, -motion.gravity, 0.0)
                updateWaterState()
            }

            UpdateOrder.GRAVITY_POSITION_DRAG -> {
                updateWaterState()
                velocity = velocity.add(0.0, -motion.gravity, 0.0)
                positionVec = positionVec.add(velocity)
                velocity = velocity.scale(if (inWater) motion.waterDrag else motion.airDrag)
            }
        }

        if (positionVec.y < level.minY ||
            !level.chunkSource.hasChunk(SectionPos.posToSectionCoord(positionVec.x), SectionPos.posToSectionCoord(positionVec.z))
        ) {
            return Step(positionVec, null, true)
        }

        val hit = findCollision(previous, positionVec)
        if (hit.type == HitResult.Type.MISS) return Step(positionVec, null, false)

        positionVec = hit.location
        if (hit is EntityHitResult && pierceLevel > 0) {
            piercedEntities.add(hit.entity)
            pierceLevel--
            return Step(positionVec, hit, false)
        }
        return Step(positionVec, hit, true)
    }

    private fun configureShot(user: Entity, angleOffset: Double, partialTick: Float, data: MotionData, piercing: Int) {
        positionVec = user.getPosition(partialTick).add(0.0, user.getEyeHeight(user.pose) - 0.1, 0.0)
        val yaw = (user.getYRot(partialTick).toDouble()).toRadians()
        val pitch = ((user.getXRot(partialTick) + data.roll).toDouble()).toRadians()
        var direction = Vec3(-sin(yaw) * cos(pitch), -sin(pitch), cos(yaw) * cos(pitch))

        if (angleOffset != 0.0) {
            val axis = user.getUpVector(partialTick).normalize()
            val cos = cos(angleOffset)
            val sin = sin(angleOffset)
            direction = direction.scale(cos).add(axis.cross(direction).scale(sin)).add(axis.scale(axis.dot(direction) * (1.0 - cos)))
        }

        velocity = direction.normalize().scale(data.power)

        collisionSource = user
        ignoredEntity = user
        setMotion(data, 0, piercing, false)
    }

    private fun configureFishing(user: Entity, partialTick: Float) {
        val yaw = (user.getYRot(partialTick).toDouble()).toRadians()
        val pitch = (user.getXRot(partialTick).toDouble()).toRadians()
        val yawCos = cos(-yaw - PI)
        val yawSin = sin(-yaw - PI)
        val pitchCos = -cos(-pitch)
        val pitchSin = sin(-pitch)

        positionVec = user.getPosition(partialTick)
            .subtract(yawSin * 0.3, 0.0, yawCos * 0.3)
            .add(0.0, user.getEyeHeight(user.pose).toDouble(), 0.0)
        velocity = Vec3(-yawSin, Mth.clamp(-(pitchSin / pitchCos), -5.0, 5.0), -yawCos)
        val length = velocity.length()
        velocity = velocity.scale(0.6 / length + 0.5)
        collisionSource = user
        ignoredEntity = user
        setMotion(FISHING_BOBBER, 0, 0, false)
    }

    private fun setMotion(data: MotionData, ticks: Int, piercing: Int, inWater: Boolean) {
        motion = data
        dimensions = data.entityType.dimensions
        this.ticks = ticks
        this.pierceLevel = piercing
        this.inWater = inWater
        piercedEntities.clear()
    }

    private fun findCollision(from: Vec3, to: Vec3): HitResult {
        val fluid = if (motion.waterDrag == 0.0) ClipContext.Fluid.ANY else ClipContext.Fluid.NONE
        val blockHit = level.clipIncludingBorder(
            ClipContext(from, to, ClipContext.Block.COLLIDER, fluid, collisionSource)
        )
        val clippedTo = if (blockHit.type == HitResult.Type.MISS) to else blockHit.location
        val delta = clippedTo.subtract(from)
        val search = dimensions.makeBoundingBox(from).expandTowards(delta).inflate(1.0)
        val entityHit = ProjectileUtil.getEntityHitResult(
            level,
            collisionSource,
            from,
            clippedTo,
            search,
            { entity ->
                entity !== ignoredEntity &&
                        !piercedEntities.contains(entity) &&
                        !entity.isSpectator &&
                        entity.isAlive &&
                        entity.isPickable
            },
            Mth.clamp((ticks - 2) / 20.0F, 0.0F, 0.3F),
        )
        return entityHit ?: blockHit
    }

    private fun updateWaterState() {
        val box = dimensions.makeBoundingBox(positionVec).deflate(0.001)
        val minX = Mth.floor(box.minX)
        val maxX = Mth.ceil(box.maxX)
        val minY = Mth.floor(box.minY)
        val maxY = Mth.ceil(box.maxY)
        val minZ = Mth.floor(box.minZ)
        val maxZ = Mth.ceil(box.maxZ)

        for (x in minX until maxX) {
            for (y in minY until maxY) {
                for (z in minZ until maxZ) {
                    blockPos.set(x, y, z)
                    val state: FluidState = level.getFluidState(blockPos)
                    if (state.`is`(FluidTags.WATER) && y + state.getHeight(level, blockPos) >= box.minY) {
                        inWater = true
                        return
                    }
                }
            }
        }
        inWater = false
    }

    /**
     * 本 tick 的模拟结果。
     */
    data class Step(val position: Vec3, val hit: HitResult?, val stop: Boolean)

    private data class MotionData(
        val power: Double,
        val roll: Double,
        val gravity: Double,
        val airDrag: Double,
        val waterDrag: Double,
        val entityType: EntityType<*>,
        val updateOrder: UpdateOrder,
    ) {

        fun withPower(power: Double): MotionData {
            return MotionData(power, roll, gravity, airDrag, waterDrag, entityType, updateOrder)
        }

    }

    companion object {

        /**
         * 判断物品是否受本模拟器支持。
         */
        fun supports(item: Item): Boolean {
            return item is BowItem || item is CrossbowItem || item is FishingRodItem ||
                    item is TridentItem || item is SnowballItem || item is EggItem ||
                    item is EnderpearlItem || item is ExperienceBottleItem ||
                    item is SplashPotionItem || item is LingeringPotionItem ||
                    item is WindChargeItem
        }

        private fun throwable(power: Double, roll: Double, gravity: Double, type: EntityType<*>): MotionData {
            return MotionData(power, roll, gravity, 0.99, 0.8, type, UpdateOrder.GRAVITY_DRAG_POSITION)
        }

        private val EGG: MotionData = throwable(1.5, 0.0, 0.03, EntityTypes.EGG)
        private val ENDER_PEARL: MotionData = throwable(1.5, 0.0, 0.03, EntityTypes.ENDER_PEARL)
        private val SNOWBALL: MotionData = throwable(1.5, 0.0, 0.03, EntityTypes.SNOWBALL)
        private val EXPERIENCE_BOTTLE: MotionData = throwable(0.7, -20.0, 0.07, EntityTypes.EXPERIENCE_BOTTLE)
        private val LINGERING_POTION: MotionData = throwable(0.5, -20.0, 0.05, EntityTypes.LINGERING_POTION)
        private val SPLASH_POTION: MotionData = throwable(0.5, -20.0, 0.05, EntityTypes.SPLASH_POTION)
        private val WIND_CHARGE: MotionData =
            MotionData(1.5, 0.0, 0.0, 1.0, 1.0, EntityTypes.WIND_CHARGE, UpdateOrder.GRAVITY_DRAG_POSITION)
        private val EXPLOSIVE: MotionData =
            MotionData(0.0, 0.0, 0.0, 1.0, 1.0, EntityTypes.FIREBALL, UpdateOrder.GRAVITY_DRAG_POSITION)
        private val ARROW: MotionData =
            MotionData(0.0, 0.0, 0.05, 0.99, 0.6, EntityTypes.ARROW, UpdateOrder.POSITION_DRAG_GRAVITY)
        private val TRIDENT: MotionData =
            MotionData(2.5, 0.0, 0.05, 0.99, 0.99, EntityTypes.TRIDENT, UpdateOrder.POSITION_DRAG_GRAVITY)
        private val LLAMA_SPIT: MotionData =
            MotionData(0.0, 0.0, 0.06, 0.99, 0.0, EntityTypes.LLAMA_SPIT, UpdateOrder.POSITION_DRAG_GRAVITY)
        private val FIREWORK: MotionData =
            MotionData(0.0, 0.0, 0.0, 1.0, 1.0, EntityTypes.FIREWORK_ROCKET, UpdateOrder.GRAVITY_POSITION_DRAG)
        private val FISHING_BOBBER: MotionData =
            MotionData(0.0, 0.0, 0.03, 0.92, 0.0, EntityTypes.FISHING_BOBBER, UpdateOrder.GRAVITY_POSITION_DRAG)

    }

}
