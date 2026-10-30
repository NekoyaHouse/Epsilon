package com.github.epsilon.modules.impl.combat

import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.Setting
import com.github.epsilon.settings.getValue
import com.github.epsilon.utils.NonNullContext
import com.github.epsilon.utils.math.MathUtils
import com.github.epsilon.utils.player
import net.minecraft.client.KeyMapping

/**
 * @author Moli
 * fix: 已基本害死伪随机。
 */

object AutoClicker : Module("Auto Clicker", Category.COMBAT) {

    private val button by enumSetting("Button", ClickButton.BOTH)

    private val leftMinCPS by intSetting("Left Min CPS", 8, 1, 520, 1, Setting.Dependency { isLeftButtonEnabled() })
    private val leftMaxCPS by intSetting("Left Max CPS", 12, 1, 520, 1, Setting.Dependency { isLeftButtonEnabled() })
    private val leftJitter by boolSetting("Left Jitter", false, Setting.Dependency { isLeftButtonEnabled() })

    private val rightMinCPS by intSetting("Right Min CPS", 8, 1, 520, 1, Setting.Dependency { isRightButtonEnabled() })
    private val rightMaxCPS by intSetting("Right Max CPS", 12, 1, 520, 1, Setting.Dependency { isRightButtonEnabled() })
    private val rightJitter by boolSetting("Right Jitter", false, Setting.Dependency { isRightButtonEnabled() })

    private val enableBurst by boolSetting("Enable Burst", true)
    private val burstChance by intSetting("Burst Chance", 15, 1, 100, 1, Setting.Dependency { enableBurst })
    private val burstDuration by intSetting("Burst Duration", 800, 100, 3000, 100, Setting.Dependency { enableBurst })
    private val burstCPSMultiplier by doubleSetting(
        "Burst CPS Multiplier", 1.8, 1.1, 3.0, 0.1, Setting.Dependency { enableBurst })
    private val decayDuration by intSetting("Decay Duration", 1200, 500, 5000, 100, Setting.Dependency { enableBurst })
    private val decayCurve by doubleSetting("Decay Curve", 1.5, 1.0, 3.0, 0.1, Setting.Dependency { enableBurst })

    private val enableDoubleClick by boolSetting("Enable Double Click", true)
    private val doubleClickChance by intSetting(
        "Double Click Chance", 8, 1, 50, 1, Setting.Dependency { enableDoubleClick })
    private val doubleClickDelay by intSetting(
        "Double Click Delay", 40, 10, 100, 5, Setting.Dependency { enableDoubleClick })

    private val enableMiss by boolSetting("Enable Miss", true)
    private val missChance by intSetting("Miss Chance", 5, 1, 30, 1, Setting.Dependency { enableMiss })

    private val leftState = ButtonState()
    private val rightState = ButtonState()

    init {
        nonNullListener<PlayerTickEvent.Pre> {
            if (mc.gui.screen() != null) return@nonNullListener

            val currentTime = System.currentTimeMillis()

            if (isLeftButtonEnabled()) {
                handleButton(leftState, ClickButton.LEFT, currentTime)
            } else {
                leftState.reset()
            }

            if (isRightButtonEnabled()) {
                handleButton(rightState, ClickButton.RIGHT, currentTime)
            } else {
                rightState.reset()
            }
        }
    }

    override fun onDisable() {
        leftState.reset()
        rightState.reset()
    }

    private fun isLeftButtonEnabled(): Boolean {
        val btn = button
        return btn == ClickButton.LEFT || btn == ClickButton.BOTH
    }

    private fun isRightButtonEnabled(): Boolean {
        val btn = button
        return btn == ClickButton.RIGHT || btn == ClickButton.BOTH
    }

    context(nn: NonNullContext)
    private fun handleButton(state: ButtonState, clickButton: ClickButton, currentTime: Long) {
        if (!isButtonPressed(clickButton)) {
            state.reset()
            return
        }

        state.updateBurstState(currentTime)

        if (currentTime - state.lastClickTime < state.nextDelay) {
            handlePendingDoubleClick(state, clickButton, currentTime)
            return
        }

        if (shouldMiss()) {
            state.lastClickTime = currentTime
            updateNextDelay(state, clickButton, currentTime)
            return
        }

        performClick(clickButton)
        applyJitter(clickButton)
        scheduleDoubleClick(state, currentTime)
        handlePendingDoubleClick(state, clickButton, currentTime)

        state.lastClickTime = currentTime
        updateNextDelay(state, clickButton, currentTime)
    }

    private fun updateNextDelay(state: ButtonState, clickButton: ClickButton, currentTime: Long) {
        val minCPS = if (clickButton == ClickButton.LEFT) leftMinCPS else rightMinCPS
        val maxCPS = if (clickButton == ClickButton.LEFT) leftMaxCPS else rightMaxCPS

        val baseCPS = MathUtils.getRandom(minCPS.value, maxCPS.value).toDouble()
        val adjustedCPS = applyBurstAndDecay(state, baseCPS, currentTime)

        state.nextDelay = (1000.0 / adjustedCPS).toLong()
    }

    context(nn: NonNullContext)
    private fun applyJitter(clickButton: ClickButton) {
        val jitterSetting = if (clickButton == ClickButton.LEFT) leftJitter else rightJitter
        if (!jitterSetting.value) return

        val yaw = player.yRot
        val pitch = player.xRot
        val yawRandom = MathUtils.getRandom(-0.5f, 0.5f) * 0.5f
        val pitchRandom = MathUtils.getRandom(-0.5f, 0.5f) * 0.5f
        player.yRot = yaw + yawRandom
        player.xRot = pitch + pitchRandom
    }

    private fun isButtonPressed(clickButton: ClickButton): Boolean {
        return if (clickButton == ClickButton.LEFT) mc.mouseHandler.isLeftPressed() else mc.mouseHandler.isRightPressed()
    }

    private fun performClick(clickButton: ClickButton) {
        val key: KeyMapping = if (clickButton == ClickButton.LEFT) mc.options.keyAttack else mc.options.keyUse
        KeyMapping.click(key.defaultKey)
    }

    private fun shouldMiss(): Boolean {
        if (!enableMiss) return false
        return MathUtils.getRandom(1, 100) <= missChance
    }

    private fun scheduleDoubleClick(state: ButtonState, currentTime: Long) {
        if (!enableDoubleClick) return
        if (state.pendingDoubleClick) return

        if (MathUtils.getRandom(1, 100) <= doubleClickChance) {
            state.pendingDoubleClick = true
            state.doubleClickTime = currentTime + doubleClickDelay
        }
    }

    private fun handlePendingDoubleClick(state: ButtonState, clickButton: ClickButton, currentTime: Long) {
        if (!state.pendingDoubleClick) return
        if (currentTime < state.doubleClickTime) return

        val key: KeyMapping = if (clickButton == ClickButton.LEFT) mc.options.keyAttack else mc.options.keyUse
        KeyMapping.click(key.defaultKey)

        state.pendingDoubleClick = false
    }

    private fun applyBurstAndDecay(state: ButtonState, value: Double, currentTime: Long): Double {
        if (!enableBurst) return value

        if (state.isInBurst) {
            return value * burstCPSMultiplier
        } else if (state.isInDecay) {
            var decayProgress = (currentTime - state.decayStartTime).toDouble() / decayDuration
            decayProgress = Math.min(1.0, Math.max(0.0, decayProgress))

            val curveFactor = Math.pow(1.0 - decayProgress, decayCurve)
            val multiplier = 1.0 + (burstCPSMultiplier - 1.0) * curveFactor

            return value * multiplier
        }

        return value
    }

    private enum class ClickButton(private val label: String) {
        LEFT("Left"),
        RIGHT("Right"),
        BOTH("Both");

        override fun toString(): String {
            return label
        }
    }

    private class ButtonState {
        var lastClickTime = 0L
        var nextDelay = 0L

        var isInBurst = false
        var isInDecay = false
        var burstStartTime = 0L
        var burstEndTime = 0L
        var decayStartTime = 0L
        var decayEndTime = 0L

        var pendingDoubleClick = false
        var doubleClickTime = 0L

        fun updateBurstState(currentTime: Long) {
            if (!enableBurst) return

            if (isInBurst) {
                if (currentTime >= burstEndTime) {
                    isInBurst = false
                    isInDecay = true
                    decayStartTime = currentTime
                }
            } else if (isInDecay) {
                if (currentTime >= decayEndTime) {
                    isInDecay = false
                }
            } else {
                if (MathUtils.getRandom(1, 100) <= burstChance) {
                    isInBurst = true
                    burstStartTime = currentTime
                    burstEndTime = currentTime + burstDuration
                    decayEndTime = burstEndTime + decayDuration
                }
            }
        }

        fun reset() {
            lastClickTime = 0
            nextDelay = 0
            isInBurst = false
            isInDecay = false
            burstStartTime = 0
            burstEndTime = 0
            decayStartTime = 0
            decayEndTime = 0
            pendingDoubleClick = false
            doubleClickTime = 0
        }
    }

}
