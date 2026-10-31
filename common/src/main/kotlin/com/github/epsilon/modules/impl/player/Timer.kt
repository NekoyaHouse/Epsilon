package com.github.epsilon.modules.impl.player

import com.github.epsilon.events.api.handler
import com.github.epsilon.events.api.nonNullHandler
import com.github.epsilon.events.impl.*
import com.github.epsilon.managers.TimerManager
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.modules.impl.combat.KillAura
import com.github.epsilon.utils.safety.player
import com.github.epsilon.utils.timer.TimerUtils
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.common.ClientboundPingPacket
import net.minecraft.network.protocol.game.*
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

object Timer : Module("Timer", Category.MOVEMENT) {

    private enum class Mode {
        Always,
        Balance,
        SlowBalance,
    }

    private val mode by enumSetting("Mode", Mode.Always)
    private val activateKey by keybindSetting(
        "Activate Key",
        InputConstants.KEY_X, { mode != Mode.Always })
    val multiplier by doubleSetting("Multiplier", 1.8, 0.1..10.0, 0.1)
    private val maxBalance by intSetting("Max Balance", 1000, 0..10000, 1, { mode == Mode.Balance })
    val ticks by intSetting("Ticks", 13, 1..20, 1, { mode == Mode.SlowBalance })

    private var balance = 0.0f
    private var currentTime = 0L
    private var balanceTime = 0L
    private var onGround = false
    private var attack = false
    private var releasing = false
    private var shouldRelease = false
    var lagging = false

    private val timer = TimerUtils()
    val packets: Queue<Packet<ClientGamePacketListener>> = ConcurrentLinkedQueue()

    init {
        nonNullHandler<ClientTickEvent.Pre> {
            // 先收回其他模块留下的临时倍率，再应用当前 Timer 模式。
            TimerManager.tryReset()

            when (mode) {
                Mode.Always -> TimerManager.set(multiplier.toFloat())

                Mode.SlowBalance -> {
                    val moving = player.isMoving
                    if (shouldRelease && balance > 0.1F) {
                        val speed = multiplier.toFloat()
                        if (balance >= speed - 1.0F) {
                            TimerManager.set(speed)
                            balance -= speed - 1.0F
                        } else {
                            TimerManager.set(1.0F)
                        }
                    } else if (KillAura.target == null && balance < ticks) {
                        val speed = if (!moving && player.onGround()) 0.8F else 0.96F
                        TimerManager.set(speed)
                        balance += 1.0F - speed
                    } else {
                        TimerManager.set(1.0F)
                    }
                    balance = balance.coerceIn(0.0F, ticks.toFloat())
                }

                Mode.Balance -> {
                    if (releasing && balanceTime <= 10L) {
                        updateTimerSpeed()
                    }

                    if (!lagging) releaseAll()

                    val killAuraTarget = KillAura.target
                    if (KillAura.isEnabled && killAuraTarget != null && player.distanceTo(killAuraTarget) <= KillAura.aimRange.toFloat()) {
                        stopBalance()
                        return@nonNullHandler
                    }

                    if (!player.isAlive || player.health <= 0.0f) {
                        isEnabled = false
                        return@nonNullHandler
                    }

                    if (!shouldRelease && !lagging && !releasing && !player.isMoving) {
                        timer.reset()
                        balanceTime = 0L
                        currentTime = System.currentTimeMillis()
                        startBalance()
                    } else if (lagging && !shouldRelease && !releasing && !player.isMoving && balanceTime < 0L) {
                        balanceTime = 0L
                        currentTime = System.currentTimeMillis()
                    }

                    updateTimerSpeed()
                }
            }
        }

        nonNullHandler<ClientTickEvent.Post> {
            if (mode == Mode.Balance && releasing && balanceTime <= 10L) {
                stopBalance()
            }
        }

        nonNullHandler<PlayerTickEvent.Post> {
            if (mode == Mode.Balance && attack) {
                if (lagging) stopBalance()
                attack = false
            }
        }

        nonNullHandler<PacketEvent.Receive> { event ->
            if (mode != Mode.Balance) return@nonNullHandler

            val packet = event.packet
            if (packet is ClientboundPlayerPositionPacket) {
                stopBalance()
                return@nonNullHandler
            }
            if (packet is ClientboundSetEntityMotionPacket && packet.id() == player.id) {
                stopBalance()
                return@nonNullHandler
            }
            if (packet is ClientboundPingPacket && lagging) {
                packets.add(packet as Packet<ClientGamePacketListener>)
                event.cancel()
            }
        }

        nonNullHandler<PacketEvent.Send> { event ->
            if (mode != Mode.Balance) return@nonNullHandler

            val packet = event.packet
            if (packet is ServerboundMovePlayerPacket) {
                if (lagging) {
                    if (!packet.hasPosition() && !packet.hasRotation() && packet.isOnGround == onGround) {
                        event.cancel()
                    } else {
                        balanceTime -= if (releasing) 100L else 50L
                        balanceTime = maxOf(-3000L, balanceTime)
                    }

                    val now = System.currentTimeMillis()
                    if (balanceTime <= maxBalance) {
                        balanceTime += now - currentTime
                    }
                    currentTime = now
                }
                onGround = packet.isOnGround
            } else if (packet is ServerboundInteractPacket && lagging && packets.isNotEmpty()) {
                attack = true
                event.cancel()
            }
        }

        nonNullHandler<LevelUpdateEvent> {
            if (mode == Mode.Balance) {
                packets.clear()
                stopBalance()
            }
        }

        handler<KeyPressEvent> { event ->
            if (event.getKey() != activateKey) return@handler
            if (event.action == InputConstants.PRESS) {
                shouldRelease = true
            } else if (event.action == InputConstants.RELEASE) {
                shouldRelease = false
            }
        }

        handler<MousePressEvent> { event ->
            if (event.button != activateKey) return@handler
            if (event.action == InputConstants.PRESS) {
                shouldRelease = true
            } else if (event.action == InputConstants.RELEASE) {
                shouldRelease = false
            }
        }
    }

    override fun getInfo(): String {
        return when (mode) {
            Mode.Always -> multiplier.toString()
            Mode.Balance -> balanceTime.toString() + "ms"
            Mode.SlowBalance -> String.format("%.1f", balance) + " Ticks"
        }
    }

    override fun onEnable() {
        resetState()
        if (mode == Mode.Balance) {
            timer.reset()
            currentTime = System.currentTimeMillis()
            updateTimerSpeed()
        }
        releaseAll()
    }

    override fun onDisable() {
        lagging = false
        releasing = false
        shouldRelease = false
        TimerManager.reset()
        releaseAll()
    }

    fun getBalanceTime(): Long {
        return if (mode == Mode.SlowBalance) (balance * 50.0F).toLong() else balanceTime
    }

    fun getMaxBalance(): Long {
        return if (mode == Mode.SlowBalance) ticks.toLong() * 50L else maxBalance.toLong()
    }

    fun isReleasing(): Boolean {
        return if (mode == Mode.SlowBalance) shouldRelease else releasing
    }

    private fun startBalance() {
        releaseAll()
        lagging = true
        releasing = false
    }

    private fun stopBalance() {
        updateTimerSpeed()
        timer.reset()
        balanceTime = 0L
        currentTime = System.currentTimeMillis()
        if (lagging) {
            lagging = false
            releaseAll()
        }
        releasing = false
    }

    private fun updateTimerSpeed() {
        val shouldSpeedUp = shouldRelease && balanceTime > 10L
        releasing = shouldSpeedUp
        TimerManager.set(if (shouldSpeedUp) multiplier.toFloat() else 1.0F)
    }

    private fun releaseAll() {
        if (!lagging && packets.isNotEmpty()) {
            while (true) {
                val packet = packets.poll() ?: break
                val connection = mc.connection ?: break
                packet.handle(connection)
            }
        }
    }

    private fun resetState() {
        balance = 0.0F
        balanceTime = 0L
        currentTime = System.currentTimeMillis()
        onGround = false
        attack = false
        lagging = false
        releasing = false
        shouldRelease = false
        packets.clear()
    }

}
