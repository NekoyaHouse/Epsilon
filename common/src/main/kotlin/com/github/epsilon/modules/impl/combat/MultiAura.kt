package com.github.epsilon.modules.impl.combat

import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.managers.target.TargetManager
import com.github.epsilon.managers.target.TargetRequest
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.getValue
import com.github.epsilon.utils.interaction
import com.github.epsilon.utils.math.MathUtils
import com.github.epsilon.utils.player
import com.github.epsilon.utils.player.PlayerUtils
import net.minecraft.world.InteractionHand

object MultiAura : Module("Multi Aura", Category.COMBAT) {

    private val range by doubleSetting("Range", 5.0, 1.0, 6.0, 0.05)
    private val minCPS by intSetting("Min CPS", 10, 1, 20, 1)
    private val maxCPS by intSetting("Max CPS", 12, 1, 20, 1)
    private val fov by intSetting("FOV", 360, 30, 360, 10)
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
        nonNullListener<PlayerTickEvent.Pre> {
            if (pauseOnContainers && mc.gui.screen() != null) return@nonNullListener
            if (player.isUsingItem || player.isBlocking) return@nonNullListener

            attacks += MathUtils.getRandom(minCPS.toDouble(), maxCPS.toDouble()) / 20.0
            if (attacks < 1.0) return@nonNullListener

            val targets = TargetManager.INSTANCE.acquireTargets(
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

            if (targets.isEmpty()) return@nonNullListener

            while (attacks >= 1.0) {
                for (target in targets) {
                    interaction.attack(player, target)
                }
                attacks -= 1.0
            }

            if (swingHand) {
                PlayerUtils.swingHand(InteractionHand.MAIN_HAND)
            }
        }
    }

    override fun onDisable() {
        attacks = 0.0
    }

}
