package com.github.epsilon.kernel.hardware

import cn.cutemic.clion.CLion
import java.io.IOException
import java.net.URL
import javax.sound.sampled.*


object AudioSystemSound {
    private var nBytesRead = 0

    @JvmStatic
    fun startMusic(path: URL, value: Float) {
        CLion.LOGGER.info("Received audio playback request with path $path")
        Thread {
            val audioInputStream: AudioInputStream
            try {
                audioInputStream = AudioSystem.getAudioInputStream(path)
                val format = audioInputStream.format
                val sdl: SourceDataLine
                val info = DataLine.Info(SourceDataLine::class.java, format)
                sdl = AudioSystem.getLine(info) as SourceDataLine
                sdl.open(format)
                val control = sdl.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
                control.setValue(value)
                sdl.start()
                val abData = ByteArray(512)
                while (nBytesRead != -1) {
                    nBytesRead = audioInputStream.read(abData, 0, abData.size)
                    if (nBytesRead >= 0) sdl.write(abData, 0, nBytesRead)
                }
                sdl.drain()
                sdl.close()
            } catch (e: UnsupportedAudioFileException) {
                e.printStackTrace()
            } catch (e: IOException) {
                e.printStackTrace()
            } catch (e: LineUnavailableException) {
                e.printStackTrace()
            }
        }.start()
    }

}
