package com.github.epsilon.modules.impl.movement

import com.github.epsilon.events.api.EventPriority
import com.github.epsilon.events.api.safeEventHandler
import com.github.epsilon.events.impl.MoveEvent
import com.github.epsilon.events.impl.PacketEvent
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.kernel.jvm.NonNullContext
import com.github.epsilon.kernel.jvm.runSafe
import com.github.epsilon.managers.RotationManager
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.utils.math.toRadians
import com.github.epsilon.utils.player.PlayerUtils
import com.github.epsilon.utils.timer.TimerUtils
import net.minecraft.network.protocol.game.ClientboundExplodePacket
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket
import net.minecraft.util.Mth
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.vehicle.boat.Boat
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

object Speed : Module("Speed", Category.MOVEMENT) {

    private enum class Mode {
        Strafe,
        StrafeStrict,
        Grim,
    }

    private val mode by enumSetting("Mode", Mode.Strafe)
    private val collideSpeed by doubleSetting("Collide Speed", 0.08, 0.0..0.08, 0.01, { mode == Mode.Grim })
    private val strict by boolSetting("Strict", true, { mode == Mode.Grim })
    private val boat by boolSetting("Boat LongJump", true, { mode == Mode.Grim })
    private val boatExpand by doubleSetting("Boat Expand", 0.2, 0.0..1.0, 0.01, { mode == Mode.Grim })
    private val boatSpeed by doubleSetting("Boat Speed", 0.2, -2.0..2.0, 0.01, { mode == Mode.Grim })
    private val boatJump by doubleSetting("Boa tJump", 0.2, 0.0..2.0, 0.01, { mode == Mode.Grim })

    private val inWater by boolSetting("In Water", false, { mode != Mode.Grim })
    private val inBlock by boolSetting("In Block", false, { mode != Mode.Grim })
    private val airStop by boolSetting("Air Stop", true, { mode != Mode.Grim })
    private val lagTime by doubleSetting("Lag Time", 500.0, 0.0..1000.0, 1.0, { mode != Mode.Grim })

    private val jump by boolSetting("Jump", true, { mode == Mode.Strafe })
    private val strafeSpeed by doubleSetting("Speed", 0.2873, 0.0..1.0, 0.0001, { mode == Mode.Strafe })
    private val explosions by boolSetting("Explosions Boost", false, { mode == Mode.Strafe })
    private val velocity by boolSetting("Velocity Boost", true, { mode == Mode.Strafe })
    private val multiplier by doubleSetting("H-Factor", 1.0, 0.0..5.0, 0.01, { mode == Mode.Strafe })
    private val vertical by doubleSetting("V-Factor", 1.0, 0.0..5.0, 0.01, { mode == Mode.Strafe })
    private val coolDown by intSetting("Cooldown", 1000, 0..5000, 1, { mode == Mode.Strafe })
    private val slow by boolSetting("Slowness", false, { mode == Mode.Strafe })

    private var stop = false
    private var speed = 0.0
    private var distance = 0.0

    private var strictTicks = 0
    private var strafe = 4
    private var stage = 0
    private var lastExp = 0.0
    private var boost = false

    private val expTimer = TimerUtils()
    private val lagTimer = TimerUtils()

    init {
        onEnable {
            runSafe {
                speed = getSpeed(false)
                distance = getDistance2D()
            }
            stage = 4
        }

        safeEventHandler<PacketEvent.Receive>(EventPriority.HIGH) { event ->
            val packet = event.packet

            if (mode == Mode.Strafe) {
                if (packet is ClientboundSetEntityMotionPacket) {
                    if (packet.id == player.id && velocity) {
                        val packetSpeed = hypot(packet.movement.x, packet.movement.z)

                        this@Speed.lastExp = if (expTimer.passedMillise(coolDown.toLong())) {
                            packetSpeed
                        } else {
                            packetSpeed - this@Speed.lastExp
                        }

                        if (this@Speed.lastExp > 0) {
                            expTimer.reset()

                            this@Speed.speed += this@Speed.lastExp * multiplier
                            this@Speed.distance += this@Speed.lastExp * multiplier

                            if (player.deltaMovement.y > 0 && vertical != 0.0) {
                                setMotionY(player.deltaMovement.y * vertical)
                            }
                        }
                    }
                } else if (packet is ClientboundExplodePacket) {
                    if (explosions) {
                        if (player.position().distanceTo(packet.center) < 15) {
                            val knockback = packet.playerKnockback.orElse(Vec3.ZERO)
                            val packetSpeed = hypot(knockback.x, knockback.z)
                            this@Speed.lastExp = if (expTimer.passedMillise(coolDown.toLong())) {
                                packetSpeed
                            } else {
                                packetSpeed - this@Speed.lastExp
                            }

                            if (this@Speed.lastExp > 0) {
                                expTimer.reset()

                                this@Speed.speed += this@Speed.lastExp * multiplier
                                this@Speed.distance += this@Speed.lastExp * multiplier

                                if (player.deltaMovement.y > 0) {
                                    setMotionY(player.deltaMovement.y * vertical)
                                }
                            }
                        }
                    }
                }
            }
            if (packet is ClientboundPlayerPositionPacket || packet is ClientboundPlayerRotationPacket) {
                lagTimer.reset()
                resetStrafe()
            }
        }

        safeEventHandler<PlayerTickEvent.Pre> {
            if (mode == Mode.Grim) {
                if (!player.isMoving) {
                    return@safeEventHandler
                }

                var collisions = 0
                val box = if (strict) player.boundingBox else player.boundingBox.inflate(1.0)

                for (entity in level.entitiesForRendering()) {
                    val entityBox = entity.boundingBox
                    if (boat && player.onGround() && entity is Boat && box.intersects(entityBox.inflate(boatExpand))) {
                        val yaw = (getSprintYaw(player.yRot).toDouble()).toRadians()
                        val boatBoost = boatSpeed
                        player.deltaMovement = Vec3(-sin(yaw) * boatBoost, boatJump, cos(yaw) * boatBoost)
                        return@safeEventHandler
                    } else if (entity != player && entity is LivingEntity && entity !is ArmorStand && box.intersects(
                            entityBox
                        )
                    ) {
                        collisions++
                    }
                }

                val yaw = (getSprintYaw(player.yRot).toDouble()).toRadians()
                val collisionBoost = collideSpeed * collisions
                player.push(-sin(yaw) * collisionBoost, 0.0, cos(yaw) * collisionBoost)
            }
        }

        safeEventHandler<PlayerTickEvent.Post> {
            distance = getDistance2D()
        }

        safeEventHandler<MoveEvent> { event ->
            if (!player.isMoving && airStop && mode != Mode.Grim) {
                player.deltaMovement = Vec3(0.0, player.deltaMovement.y, 0.0)
            }
            if (!inWater && (player.isUnderWater || player.isInWater || player.isInLava) ||
                player.isPassenger ||
                player.onClimbable() ||
                !inBlock && PlayerUtils.isInBlock() ||
                player.abilities.flying ||
                player.isFallFlying ||
                !player.isMoving
            ) {
                resetStrafe()
                stop = true
                return@safeEventHandler
            }
            if (mode == Mode.Strafe) {
                if (stop) {
                    stop = false
                    return@safeEventHandler
                }

                if (!lagTimer.passedMillise(lagTime.toLong())) {
                    return@safeEventHandler
                }

                if (stage == 1) {
                    speed = 1.35 * getSpeed(slow, strafeSpeed) - 0.01
                } else if (stage == 2 && player.onGround() && (mc.options.keyJump.isDown || jump)) {
                    val yMotion = 0.3999 + getJumpSpeed()
                    setMotionY(yMotion)
                    event.y = yMotion
                    event.cancel()
                    speed = speed * (if (boost) 1.6835 else 1.395)
                } else if (stage == 3) {
                    speed = distance - 0.66 * (distance - getSpeed(slow, strafeSpeed))

                    boost = !boost
                } else {
                    if ((canCollide(
                            null,
                            player.boundingBox.move(0.0, player.deltaMovement.y, 0.0)
                        ) || player.minorHorizontalCollision) && stage > 0
                    ) {
                        stage = 1
                    }

                    speed = distance - distance / 159.0
                }

                speed = minOf(speed, 10.0)
                speed = maxOf(speed, getSpeed(slow, strafeSpeed))
                val moveVector = player.input.moveVector
                val n = moveVector.y.toDouble()
                val n2 = moveVector.x.toDouble()
                val n3 = RotationManager.getYaw().toDouble()
                if (n == 0.0 && n2 == 0.0) {
                    event.x = 0.0
                    event.z = 0.0
                }
                event.x = (n * speed * -sin((n3).toRadians()) + n2 * speed * cos((n3).toRadians())) * 0.99
                event.z = (n * speed * cos((n3).toRadians()) - n2 * speed * -sin((n3).toRadians())) * 0.99
                event.cancel()

                stage++
                return@safeEventHandler
            }
            var speedEffect = 1.0
            var slowEffect = 1.0
            if (player.hasEffect(MobEffects.SPEED)) {
                val amplifier = player.getEffect(MobEffects.SPEED)!!.amplifier
                speedEffect = 1 + (0.2 * (amplifier + 1))
            }
            if (player.hasEffect(MobEffects.SLOWNESS)) {
                val amplifier = player.getEffect(MobEffects.SLOWNESS)!!.amplifier
                slowEffect = 1 + (0.2 * (amplifier + 1))
            }
            val base = 0.2873f * speedEffect / slowEffect
            var jumpEffect = 0.0f
            if (player.hasEffect(MobEffects.JUMP_BOOST)) {
                jumpEffect += (player.getEffect(MobEffects.JUMP_BOOST)!!.amplifier + 1) * 0.1f
            }

            if (mode == Mode.StrafeStrict) {
                if (!lagTimer.passedMillise(lagTime.toLong())) {
                    return@safeEventHandler
                }
                if (strafe == 1) {
                    speed = 1.35f * base - 0.01f
                } else if (strafe == 2) {
                    if (player.input.keyPresses.jump || !player.onGround()) {
                        return@safeEventHandler
                    }
                    val jumpMotion = 0.3999999463558197f + jumpEffect
                    event.y = jumpMotion.toDouble()
                    event.cancel()
                    setMotionY(jumpMotion.toDouble())
                    speed *= 2.149
                } else if (strafe == 3) {
                    val moveSpeed = 0.66 * (distance - base)
                    speed = distance - moveSpeed
                } else {
                    if ((!level.noCollision(
                            player,
                            player.boundingBox.move(0.0, player.deltaMovement.y, 0.0)
                        ) || player.verticalCollision) && strafe > 0
                    ) {
                        strafe = 1
                    }
                    speed = distance - distance / 159.0
                }
                strictTicks++
                speed = maxOf(speed, base)
                val baseMax = 0.465 * speedEffect / slowEffect
                val baseMin = 0.44 * speedEffect / slowEffect
                speed = minOf(speed, if (strictTicks > 25) baseMax else baseMin)
                if (strictTicks > 50) {
                    strictTicks = 0
                }
                val motion = handleStrafeMotion(speed.toFloat())
                event.x = motion.x.toDouble()
                event.z = motion.y.toDouble()
                event.cancel()
                strafe++
            }
        }
    }

    context(nn: NonNullContext)
    private fun handleStrafeMotion(speed: Float): Vec2 {
        val moveVector = nn.player.input.moveVector
        val forward = moveVector.y
        val strafe = moveVector.x
        val tickDelta = mc.deltaTracker.getGameTimeDeltaPartialTick(true)
        val yaw = Mth.lerp(tickDelta, RotationManager.getLastRotation().yaw, RotationManager.getYaw())
        if (forward == 0.0f && strafe == 0.0f) {
            return Vec2.ZERO
        }
        val rx = cos((yaw.toDouble()).toRadians()).toFloat()
        val rz = (-sin((yaw.toDouble()).toRadians())).toFloat()
        return Vec2((forward * speed * rz) + (strafe * speed * rx), (forward * speed * rx) - (strafe * speed * rz))
    }

    context(nn: NonNullContext)
    private fun getSpeed(slowness: Boolean): Double {
        val defaultSpeed = 0.2873
        return getSpeed(slowness, defaultSpeed)
    }

    context(nn: NonNullContext)
    private fun getSpeed(slowness: Boolean, defaultSpeed: Double): Double {
        var result = defaultSpeed
        if (nn.player.hasEffect(MobEffects.SPEED)) {
            val amplifier = nn.player.getEffect(MobEffects.SPEED)!!.amplifier
            result *= 1.0 + 0.2 * (amplifier + 1)
        }

        if (slowness && nn.player.hasEffect(MobEffects.SLOWNESS)) {
            val amplifier = nn.player.getEffect(MobEffects.SLOWNESS)!!.amplifier
            result /= 1.0 + 0.2 * (amplifier + 1)
        }

        if (nn.player.isCrouching) {
            result /= 5.0
        }
        return result
    }

    context(nn: NonNullContext)
    private fun getJumpSpeed(): Double {
        var defaultSpeed = 0.0

        if (nn.player.hasEffect(MobEffects.JUMP_BOOST)) {
            val amplifier = nn.player.getEffect(MobEffects.JUMP_BOOST)!!.amplifier
            defaultSpeed += (amplifier + 1) * 0.1
        }

        return defaultSpeed
    }

    context(nn: NonNullContext)
    private fun canCollide(entity: Entity?, box: AABB): Boolean {
        return !nn.level.noBlockCollision(entity, box)
    }

    context(nn: NonNullContext)
    private fun setMotionY(y: Double) {
        val movement = nn.player.deltaMovement
        nn.player.deltaMovement = Vec3(movement.x, y, movement.z)
    }

    context(nn: NonNullContext)
    private fun getDistance2D(): Double {
        val xDist = nn.player.x - nn.player.xo
        val zDist = nn.player.z - nn.player.zo
        return sqrt(xDist * xDist + zDist * zDist)
    }

    private fun getSprintYaw(yaw: Float): Float {
        var result = yaw
        if (mc.options.keyUp.isDown && !mc.options.keyDown.isDown) {
            if (mc.options.keyLeft.isDown && !mc.options.keyRight.isDown) {
                result -= 45f
            } else if (mc.options.keyRight.isDown && !mc.options.keyLeft.isDown) {
                result += 45f
            }
        } else if (mc.options.keyDown.isDown && !mc.options.keyUp.isDown) {
            result += 180f
            if (mc.options.keyLeft.isDown && !mc.options.keyRight.isDown) {
                result += 45f
            } else if (mc.options.keyRight.isDown && !mc.options.keyLeft.isDown) {
                result -= 45f
            }
        } else if (mc.options.keyLeft.isDown && !mc.options.keyRight.isDown) {
            result -= 90f
        } else if (mc.options.keyRight.isDown && !mc.options.keyLeft.isDown) {
            result += 90f
        }
        return Mth.wrapDegrees(result)
    }

    private fun resetStrafe() {
        strafe = 4
        strictTicks = 0
        speed = 0.0
        distance = 0.0
    }

}
