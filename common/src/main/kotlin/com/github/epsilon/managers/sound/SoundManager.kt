package com.github.epsilon.managers.sound

import com.github.epsilon.Constants.mc
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.client.sounds.SoundEngine
import net.minecraft.sounds.SoundSource
import java.util.*

object SoundManager {

    private var lastUiPlayTimeMs = 0L
    private const val UI_DEBOUNCE_MS = 40L

    fun playInUi(key: SoundKey) {
        playSound(key, 1.2f)
    }

    fun playSound(key: SoundKey, volume: Float) {
        playSound(key, 1.0f, volume)
    }

    fun playSound(key: SoundKey, pitch: Float, volume: Float) {
        val now = System.currentTimeMillis()
        if (now - lastUiPlayTimeMs < UI_DEBOUNCE_MS) return
        lastUiPlayTimeMs = now

        mc.soundManager.play(createSoundInstance(key, pitch, volume))
    }

    fun playTracked(key: SoundKey, volume: Float): Optional<SoundInstance> {
        val instance = createSoundInstance(key, 1.0f, volume)
        val result = mc.soundManager.play(instance)
        return if (result == SoundEngine.PlayResult.NOT_STARTED) Optional.empty() else Optional.of(instance)
    }

    private fun createSoundInstance(key: SoundKey, pitch: Float, volume: Float): SimpleSoundInstance {
        return SimpleSoundInstance(
            key.id(),
            SoundSource.UI,
            volume.coerceIn(0.0f, 1.0f),
            pitch.coerceIn(0.5f, 2.0f),
            SoundInstance.createUnseededRandom(),
            false,
            0,
            SoundInstance.Attenuation.NONE,
            0.0,
            0.0,
            0.0,
            true
        )
    }

    fun stop(key: SoundKey) {
        mc.soundManager.stop(key.id(), null)
    }

    fun stopAllSounds() {
        for (key in SoundKey.entries) {
            mc.soundManager.stop(key.id(), null)
        }
    }

}
