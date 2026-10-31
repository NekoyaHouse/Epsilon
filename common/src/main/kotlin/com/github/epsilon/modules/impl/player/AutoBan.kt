package com.github.epsilon.modules.impl.player

import com.github.epsilon.events.api.nonNullHandler
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.kernel.jvm.runSafe
import net.minecraft.network.chat.Component
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.*

object AutoBan : Module("Auto Ban", Category.PLAYER) {

    enum class Order {
        SEQUENTIAL,
        RANDOM,
    }

    private val order by enumSetting("Order", Order.SEQUENTIAL)
    private val delay by intSetting("Delay", 1000, 100..10000, 100)

    private val words: MutableList<String> = ArrayList()
    private val random = Random()

    private var countdownTicks = 0
    private var lastSendTime = 0L
    private var currentIndex = 0

    init {
        nonNullHandler<PlayerTickEvent.Pre> {
            if (countdownTicks > 0) {
                if (countdownTicks == 60) {
                    sendMessage("保护机制: 3秒后开始发送违禁词")
                    sendMessage("3")
                } else if (countdownTicks == 40) {
                    sendMessage("2")
                } else if (countdownTicks == 20) {
                    sendMessage("1")
                }
                countdownTicks--
                if (countdownTicks == 0) {
                    sendMessage("开始发送!")
                    lastSendTime = System.currentTimeMillis()
                }
                return@nonNullHandler
            }

            if (System.currentTimeMillis() - lastSendTime >= delay) {
                sendNextWord()
                lastSendTime = System.currentTimeMillis()
            }
        }
    }

    override fun onEnable() {
        loadWords()
        countdownTicks = 60
        currentIndex = 0
    }

    override fun onDisable() {
        words.clear()
    }

    private fun sendNextWord() {
        if (words.isEmpty()) {
            sendMessage("词库为空，请检查文件")
            isEnabled = false
            return
        }

        val word = if (order == Order.RANDOM) {
            words[random.nextInt(words.size)]
        } else {
            if (currentIndex >= words.size) {
                currentIndex = 0
            }
            val next = words[currentIndex]
            currentIndex++
            next
        }

        sendChatMessage(word)
    }

    private fun loadWords() {
        words.clear()
        try {
            val stream: InputStream = javaClass.classLoader.getResourceAsStream("assets/epsilon/autoban.txt") ?: run {
                sendMessage("无法找到默认词库文件")
                return
            }
            BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty()) {
                        words.add(trimmed)
                    }
                    line = reader.readLine()
                }
            }
            sendMessage("已加载 " + words.size + " 个违禁词")
        } catch (e: Exception) {
            sendMessage("加载词库失败: " + e.message)
        }
    }

    private fun sendChatMessage(message: String) {
        runSafe {
            var text = message
            if (text.isNotEmpty()) {
                if (text.length > 256) {
                    text = text.substring(0, 256)
                }
                player.connection.sendChat(text)
            }
        }
    }

    private fun sendMessage(message: String) {
        runSafe {
            player.sendSystemMessage(Component.literal("[AutoBan] $message"))
        }
    }

}
