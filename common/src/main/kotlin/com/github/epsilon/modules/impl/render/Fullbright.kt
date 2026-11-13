package com.github.epsilon.modules.impl.render

import com.github.epsilon.events.api.safeEventHandler
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.kernel.jvm.runSafe
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects

object Fullbright : Module("Fullbright", Category.RENDER) {

    init {
        onDisable {
            if (mode == Mode.Gamma) return@onDisable
            runSafe {
                player.removeEffect(MobEffects.NIGHT_VISION)
            }
        }

        safeEventHandler<PlayerTickEvent.Pre> { onPlayerTick(it) }
    }

    private enum class Mode {
        Gamma,
        Potion
    }

    private val mode by enumSetting("Mode", Mode.Gamma, onChanged = { value ->
        if (value == Mode.Gamma) {
            mc.player?.removeEffect(MobEffects.NIGHT_VISION)
        }
    })

    fun isGammaMode(): Boolean = isEnabled && mode == Mode.Gamma

    private fun onPlayerTick(event: PlayerTickEvent.Pre) {
        if (mode == Mode.Potion) {
            runSafe {
                player.addEffect(MobEffectInstance(MobEffects.NIGHT_VISION, -1, 0))
            }
        }
    }

}
