package com.github.epsilon.modules.impl.render

import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.settings.getValue
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.impl.EnumSetting
import com.github.epsilon.utils.player
import com.github.epsilon.utils.runSafe
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects

object Fullbright : Module("Fullbright", Category.RENDER) {

    init {
        nonNullListener<PlayerTickEvent.Pre> { onPlayerTick(it) }
    }

    private enum class Mode {
        Gamma,
        Potion
    }

    private val mode by enumSetting("Mode", Mode.Gamma) { value ->
        if (value == Mode.Gamma) {
            mc.player?.removeEffect(MobEffects.NIGHT_VISION)
        }
    }

    fun isGammaMode(): Boolean = isEnabled && mode.`is`(Mode.Gamma)

    override fun onDisable() {
        if (mode.`is`(Mode.Gamma)) return
        runSafe {
            player.removeEffect(MobEffects.NIGHT_VISION)
        }
    }

    private fun onPlayerTick(event: PlayerTickEvent.Pre) {
        if (mode.`is`(Mode.Potion)) {
            runSafe {
                player.addEffect(MobEffectInstance(MobEffects.NIGHT_VISION, -1, 0))
            }
        }
    }

}
