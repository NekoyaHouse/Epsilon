package com.github.epsilon.modules.impl.combat

import com.github.epsilon.events.api.safeEventHandler
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.managers.target.TargetManager
import com.github.epsilon.managers.target.TargetRequest
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.utils.math.MathUtils
import com.github.epsilon.utils.player.SwingMode
import net.minecraft.world.InteractionHand

object MultiAura : Module("Multi Aura", Category.COMBAT) {

    private val range by doubleSetting("Range", 5.0, 1.0..6.0, 0.05)
    private val minCPS by intSetting("Min CPS", 10, 1..20, 1)
    private val maxCPS by intSetting("Max CPS", 12, 1..20, 1)
    private val fov by intSetting("FOV", 360, 30..360, 10)
    private val swingHand by boolSetting("Swing Hand", true)
    private val pauseOnContainers by boolSetting("Pause On Containers", false)

    private val targetPlayer by boolSetting("Player", true)
    private val targetMob by boolSetting("Mob", true)
    private val targetAnimal by boolSetting("Animal", true)
    private val targetVillager by boolSetting("Villager", false)
    private val targetAmbient by boolSetting("Ambient", false)
    private val targetWater by boolSetting("Water", false)
    private val targetOthers by boolSetting("Others", false)
    private val targetInvisible by boolSetting("Invisible", true)

    private var attacks = 0.0

    init {
        onDisable {
            attacks = 0.0
        }

        safeEventHandler<PlayerTickEvent.Pre> {
            if (pauseOnContainers && mc.gui.screen() != null) return@safeEventHandler
            if (player.isUsingItem || player.isBlocking) return@safeEventHandler

            attacks += MathUtils.getRandom(minCPS.toDouble(), maxCPS.toDouble()) / 20.0
            if (attacks < 1.0) return@safeEventHandler

            val targets = TargetManager.acquireTargets(
                TargetRequest.of(
                    range,
                    fov.toFloat(),
                    targetPlayer,
                    targetMob,
                    targetAnimal,
                    targetVillager,
                    targetAmbient,
                    targetWater,
                    targetOthers,
                    targetInvisible,
                    64
                )
            )

            if (targets.isEmpty()) return@safeEventHandler

            while (attacks >= 1.0) {
                for (target in targets) {
                    gameMode.attack(player, target)
                }
                attacks -= 1.0
            }

            SwingMode.of(swingHand).attackSwing(InteractionHand.MAIN_HAND)
        }
    }

}
