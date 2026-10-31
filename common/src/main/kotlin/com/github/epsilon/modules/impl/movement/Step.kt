package com.github.epsilon.modules.impl.movement

import com.github.epsilon.events.api.handler
import com.github.epsilon.events.api.nonNullHandler
import com.github.epsilon.events.impl.ClientTickEvent
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.managers.TimerManager
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.modules.impl.combat.FeetTrap
import com.github.epsilon.settings.impl.EnumSetting
import com.github.epsilon.utils.player.PlayerUtils
import com.github.epsilon.utils.safety.connection
import com.github.epsilon.utils.safety.player
import com.github.epsilon.kernel.jvm.runSafe
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.entity.ai.attributes.Attributes

object Step : Module("Step", Category.MOVEMENT) {

    private enum class Mode {
        Vanilla,
        OldNCP,
        NCP,
    }

    private val modeSetting: EnumSetting<Mode> = enumSetting("Mode", Mode.Vanilla)
    private val mode by modeSetting
    private val height by doubleSetting("Height", 1.0, 0.0..5.0, 0.5)
    private val useTimer by boolSetting("Timer", true, { mode == Mode.OldNCP || mode == Mode.NCP })
    private val fast by boolSetting("Fast", true, { mode == Mode.NCP && useTimer })
    private val feetTrapPause by boolSetting("FeetTrap Pause", true)
    private val inWebPause by boolSetting("In Web Pause", true)
    private val insideBlockPause by boolSetting("Inside Block Pause", true)
    private val sneakingPause by boolSetting("Sneaking Pause", true)

    private var timer = false
    private var packets = 0

    init {
        nonNullHandler<ClientTickEvent.Pre> {
            if (sneakingPause && player.isCrouching || insideBlockPause && PlayerUtils.isInBlock() ||
                player.isInLava || player.isInWater || inWebPause && PlayerUtils.isInWeb() ||
                !player.onGround() || feetTrapPause && FeetTrap.isEnabled
            ) {
                player.setStepHeight(0.6f)
                return@nonNullHandler
            }

            player.setStepHeight(height.toFloat())
        }

        nonNullHandler<PlayerTickEvent.Pre> {
            if (timer && packets <= 0) {
                TimerManager.reset()
                timer = false
            }
            val strict = mode == Mode.NCP
            if (mode == Mode.OldNCP || strict) {
                val stepHeight = player.y - player.yo
                if (stepHeight <= 0.75 || stepHeight > height) {
                    return@nonNullHandler
                }

                val offsets = getOffset(stepHeight)
                if (offsets != null && offsets.size > 1) {
                    if (useTimer) {
                        TimerManager.set(getTimer(stepHeight).toFloat())
                        timer = true
                        packets = 2
                    }
                    for (offset in offsets) {
                        connection.send(
                            ServerboundMovePlayerPacket.Pos(
                                player.xo,
                                player.yo + offset,
                                player.zo,
                                false,
                                player.horizontalCollision
                            )
                        )
                    }
                }
            }
        }

        handler<PlayerTickEvent.Post> {
            packets--
        }
    }

    override fun getInfo(): String {
        return modeSetting.translatedValue
    }

    override fun onDisable() {
        TimerManager.reset()
        timer = false
        packets = 0
        runSafe {
            player.setStepHeight(0.6f)
        }
    }

    private fun getTimer(height: Double): Double {
        if (height > 0.6 && height <= 1) {
            if (!fast && mode == Mode.NCP) {
                return 1.0 / 3.0
            }
            return 0.5
        }
        val offsets = getOffset(height) ?: return 1.0
        return 1.0 / offsets.size
    }

    private fun getOffset(height: Double): DoubleArray? {
        val strict = mode == Mode.NCP
        if (height == 0.75) {
            return if (strict) {
                doubleArrayOf(0.42, 0.753, 0.75)
            } else {
                doubleArrayOf(0.42, 0.753)
            }
        } else if (height == 0.8125) {
            return if (strict) {
                doubleArrayOf(0.39, 0.7, 0.8125)
            } else {
                doubleArrayOf(0.39, 0.7)
            }
        } else if (height == 0.875) {
            return if (strict) {
                doubleArrayOf(0.39, 0.7, 0.875)
            } else {
                doubleArrayOf(0.39, 0.7)
            }
        } else if (height == 1.0) {
            return if (strict) {
                doubleArrayOf(0.42, 0.753, 1.0)
            } else {
                doubleArrayOf(0.42, 0.753)
            }
        } else if (height == 1.5) {
            return doubleArrayOf(0.42, 0.75, 1.0, 1.16, 1.23, 1.2)
        } else if (height == 2.0) {
            return doubleArrayOf(0.42, 0.78, 0.63, 0.51, 0.9, 1.21, 1.45, 1.43)
        } else if (height == 2.5) {
            return doubleArrayOf(0.425, 0.821, 0.699, 0.599, 1.022, 1.372, 1.652, 1.869, 2.019, 1.907)
        }

        return null
    }

    private fun LocalPlayer.setStepHeight(v: Float) {
        getAttribute(Attributes.STEP_HEIGHT)!!.baseValue = v.toDouble()
    }

}
