package com.github.epsilon.modules.impl.player

import com.github.epsilon.events.api.nonNullHandler
import com.github.epsilon.events.impl.AttackEntityEvent
import com.github.epsilon.managers.sound.SoundKey
import com.github.epsilon.managers.sound.SoundManager
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.getValue
import com.github.epsilon.utils.math.MathUtils
import com.github.epsilon.utils.timer.TimerUtils

object SoundFX : Module("Sound FX", Category.PLAYER) {

    private enum class HitSound {
        UwU,
        Nya,
        Moan,
        OFF,
    }

    private val volume by intSetting("Volume", 100, 1, 100, 5)
    private val delay by intSetting("Delay", 0, 0, 20, 1)
    private val hitSound by enumSetting("Hit Sound", HitSound.OFF)

    private val timer = TimerUtils()

    init {
        nonNullHandler<AttackEntityEvent> {
            if (timer.hasDelayed(delay)) {
                playHitSound(hitSound)
                timer.reset()
            }
        }
    }

    private fun playHitSound(value: HitSound) {
        when (value) {
            HitSound.UwU -> playSound(SoundKey.UWU)

            HitSound.Nya -> playSound(SoundKey.NYA)

            HitSound.Moan -> {
                val sound = when (MathUtils.getRandom(0, 3)) {
                    0 -> SoundKey.MOAN1
                    1 -> SoundKey.MOAN2
                    2 -> SoundKey.MOAN3
                    else -> SoundKey.MOAN4
                }
                playSound(sound)
            }

            HitSound.OFF -> {}
        }
    }

    private fun playSound(key: SoundKey) {
        SoundManager.playSound(key, volume.toFloat() / 100.0f)
    }

}
