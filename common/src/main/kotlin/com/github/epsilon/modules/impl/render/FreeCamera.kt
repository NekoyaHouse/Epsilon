package com.github.epsilon.modules.impl.render

import com.github.epsilon.events.api.handler
import com.github.epsilon.events.api.nonNullHandler
import com.github.epsilon.events.bus.EventPriority
import com.github.epsilon.events.impl.*
import com.github.epsilon.managers.rotation.RotationManager
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.utils.client.KeybindUtils
import com.github.epsilon.utils.player.ChatUtils
import com.github.epsilon.utils.rotation.Priority
import com.github.epsilon.utils.rotation.Rot2f
import com.github.epsilon.utils.rotation.RotationUtils
import com.github.epsilon.utils.safety.level
import com.github.epsilon.utils.safety.player
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.CameraType
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket
import net.minecraft.network.protocol.game.ClientboundRespawnPacket
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket
import net.minecraft.util.Mth
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.Vec3
import org.joml.Vector3d
import kotlin.math.sqrt

object FreeCamera : Module("Free Camera", Category.RENDER) {

    private val speed by doubleSetting("Speed", 1.0, 0.0, 10.0, 0.1, onChanged = { v -> speedValue = v })
    private val speedScrollSensitivity by doubleSetting("Speed Scroll Sensitivity", 0.0, 0.0, 2.0, 0.1)
    private val staySneaking by boolSetting("Stay Sneaking", true)
    private val toggleOnDamage by boolSetting("Toggle On Damage", false)
    private val toggleOnDeath by boolSetting("Toggle On Death", false)
    private val toggleOnLog by boolSetting("Toggle On Log", true)
    private val reloadChunks by boolSetting("Reload Chunks", true)
    private val renderHands by boolSetting("Show Hands", true)
    private val rotate by boolSetting("Rotate", false)
    private val staticView by boolSetting("Static", true)

    val pos = Vector3d()
    val prevPos = Vector3d()

    private var perspective: CameraType? = null
    private var speedValue = 0.0

    var yaw = 0.0f
    var pitch = 0.0f
    var lastYaw = 0.0f
    var lastPitch = 0.0f

    private var fovScale = 0.0
    private var bobView = false

    private var forward = false
    private var backward = false
    private var right = false
    private var left = false
    private var up = false
    private var down = false
    private var isSneaking = false

    private var rotation: Rot2f? = null

    init {
        handler<OpenScreenEvent> {
            unpress()

            prevPos.set(pos)
            lastYaw = yaw
            lastPitch = pitch
        }

        nonNullHandler<ClientTickEvent.Post> {
            val cameraEntity = mc.cameraEntity ?: return@nonNullHandler
            if (cameraEntity.isInWall) cameraEntity.noPhysics = true
            if (perspective?.isFirstPerson != true) mc.options.cameraType = CameraType.FIRST_PERSON

            val forwardDir = Vec3.directionFromRotation(0.0f, yaw)
            val rightDir = Vec3.directionFromRotation(0.0f, yaw + 90)
            var velX = 0.0
            var velY = 0.0
            var velZ = 0.0

            if (rotate) {
                rotation = null

                val hit = mc.hitResult
                if (hit is EntityHitResult) {
                    rotation = RotationUtils.calculate(hit.entity.blockPosition())
                } else if (hit is BlockHitResult) {
                    val crossHairPos: BlockPos = hit.blockPos
                    if (!level.getBlockState(crossHairPos).isAir) {
                        rotation = RotationUtils.calculate(hit.location)
                    }
                }

                val currentRotation = rotation
                if (currentRotation != null) {
                    RotationManager.INSTANCE.setRotations(currentRotation, 180.0, Priority.Highest)
                }
            }

            var s = 0.5
            if (KeybindUtils.isPressed(mc.options.keySprint)) s = 1.0

            var a = false
            if (this.forward) {
                velX += forwardDir.x * s * speedValue
                velZ += forwardDir.z * s * speedValue
                a = true
            }
            if (this.backward) {
                velX -= forwardDir.x * s * speedValue
                velZ -= forwardDir.z * s * speedValue
                a = true
            }

            var b = false
            if (this.right) {
                velX += rightDir.x * s * speedValue
                velZ += rightDir.z * s * speedValue
                b = true
            }
            if (this.left) {
                velX -= rightDir.x * s * speedValue
                velZ -= rightDir.z * s * speedValue
                b = true
            }

            if (a && b) {
                val diagonal = 1 / sqrt(2.0)
                velX *= diagonal
                velZ *= diagonal
            }

            if (this.up) {
                velY += s * speedValue
            }
            if (this.down) {
                velY -= s * speedValue
            }

            prevPos.set(pos)
            pos.set(pos.x + velX, pos.y + velY, pos.z + velZ)
        }

        nonNullHandler<KeyboardInputEvent> {
            if (!player.abilities.flying && staySneaking && isSneaking) {
                it.isSneak = true
            }
        }

        handler<KeyPressEvent>(EventPriority.HIGH) {
            if (onInput(it.getKey(), it.action) && !KeybindUtils.isPressed(InputConstants.KEY_F3)) {
                it.cancel()
            }
        }

        handler<MousePressEvent>(EventPriority.HIGH) {
            if (onInput(it.button, it.action)) {
                it.cancel()
            }
        }

        handler<MouseScrollEvent> {
            if (speedScrollSensitivity > 0 && mc.gui.screen() == null) {
                speedValue += it.value * 0.25 * (speedScrollSensitivity * speedValue)
                if (speedValue < 0.1) speedValue = 0.1
                it.cancel()
            }
        }

        handler<ChunkOcclusionEvent> {
            it.cancel()
        }

        handler<GameLeftEvent> {
            if (toggleOnLog) {
                toggle()
            }
        }

        nonNullHandler<PacketEvent.Receive> {
            val packet = it.packet
            if (packet is ClientboundPlayerCombatKillPacket) {
                val entity = level.getEntity(packet.playerId())
                if (entity === player && toggleOnDeath) {
                    toggle()
                    sendChatInfo("Toggled off because you died.")
                }
            } else if (packet is ClientboundSetHealthPacket) {
                if (player.health - packet.health > 0 && toggleOnDamage) {
                    toggle()
                    sendChatInfo("Toggled off because you took damage.")
                }
            } else if (packet is ClientboundRespawnPacket) {
                toggle()
                sendChatInfo("Toggled off because you changed dimensions.")
            }
        }
    }

    override fun onEnable() {
        val player = mc.player ?: return
        fovScale = mc.options.fovEffectScale().get()
        bobView = mc.options.bobView().get()
        if (staticView) {
            mc.options.fovEffectScale().set(0.0)
            mc.options.bobView().set(false)
        }
        yaw = player.yRot
        pitch = player.xRot

        perspective = mc.options.cameraType
        speedValue = speed

        val cameraPos = mc.gameRenderer.mainCamera().position()
        pos.set(cameraPos.x, cameraPos.y, cameraPos.z)
        prevPos.set(pos)

        if (mc.options.cameraType == CameraType.THIRD_PERSON_FRONT) {
            yaw += 180
            pitch *= -1
        }

        lastYaw = yaw
        lastPitch = pitch

        isSneaking = mc.options.keyShift.isDown

        forward = KeybindUtils.isPressed(mc.options.keyUp)
        backward = KeybindUtils.isPressed(mc.options.keyDown)
        right = KeybindUtils.isPressed(mc.options.keyRight)
        left = KeybindUtils.isPressed(mc.options.keyLeft)
        up = KeybindUtils.isPressed(mc.options.keyJump)
        down = KeybindUtils.isPressed(mc.options.keyShift)

        unpress()

        if (reloadChunks) {
            mc.levelExtractor.allChanged()
        }
    }

    override fun onDisable() {
        if (reloadChunks) {
            mc.execute { mc.levelExtractor.allChanged() }
        }

        val persp = perspective
        if (persp != null) mc.options.cameraType = persp

        if (staticView) {
            mc.options.fovEffectScale().set(fovScale)
            mc.options.bobView().set(bobView)
        }

        isSneaking = false
    }

    private fun unpress() {
        mc.options.keyUp.isDown = false
        mc.options.keyDown.isDown = false
        mc.options.keyRight.isDown = false
        mc.options.keyLeft.isDown = false
        mc.options.keyJump.isDown = false
        mc.options.keyShift.isDown = false
    }

    private fun onInput(key: Int, action: Int): Boolean {
        if (KeybindUtils.getKey(mc.options.keyUp) == key) {
            forward = action != InputConstants.RELEASE
            mc.options.keyUp.isDown = false
        } else if (KeybindUtils.getKey(mc.options.keyDown) == key) {
            backward = action != InputConstants.RELEASE
            mc.options.keyDown.isDown = false
        } else if (KeybindUtils.getKey(mc.options.keyRight) == key) {
            right = action != InputConstants.RELEASE
            mc.options.keyRight.isDown = false
        } else if (KeybindUtils.getKey(mc.options.keyLeft) == key) {
            left = action != InputConstants.RELEASE
            mc.options.keyLeft.isDown = false
        } else if (KeybindUtils.getKey(mc.options.keyJump) == key) {
            up = action != InputConstants.RELEASE
            mc.options.keyJump.isDown = false
        } else if (KeybindUtils.getKey(mc.options.keyShift) == key) {
            down = action != InputConstants.RELEASE
            mc.options.keyShift.isDown = false
        } else {
            return false
        }

        return true
    }

    fun changeLookDirection(deltaX: Double, deltaY: Double) {
        lastYaw = yaw
        lastPitch = pitch

        yaw += deltaX.toFloat()
        pitch += deltaY.toFloat()

        pitch = Mth.clamp(pitch, -90.0f, 90.0f)
    }

    fun sendChatInfo(text: String) {
        ChatUtils.addChatMessage("[Free Camera] $text")
    }

    fun renderHands(): Boolean = !isEnabled || renderHands

    fun getX(tickDelta: Float): Double = Mth.lerp(tickDelta.toDouble(), prevPos.x, pos.x)

    fun getY(tickDelta: Float): Double = Mth.lerp(tickDelta.toDouble(), prevPos.y, pos.y)

    fun getZ(tickDelta: Float): Double = Mth.lerp(tickDelta.toDouble(), prevPos.z, pos.z)

    fun getYaw(tickDelta: Float): Double = Mth.lerp(tickDelta.toDouble(), lastYaw.toDouble(), yaw.toDouble())

    fun getPitch(tickDelta: Float): Double = Mth.lerp(tickDelta.toDouble(), lastPitch.toDouble(), pitch.toDouble())

}
