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
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

/*
 * @author Moli
 * todo: 点名道姓模式、支持dm、扩充语料库
 */
object AutoKouZi : Module("Auto Kou Zi", Category.PLAYER) {

    enum class Mode {
        TXT,
        SENTENCE,
    }

    private val mode by enumSetting("Mode", Mode.TXT)
    private val delay by intSetting("Delay", 1000, 100..10000, 100)
    private val customPath by stringSetting("Custom Path", "")

    private val allSentences: MutableList<String> = ArrayList()
    private val sentSentences: MutableSet<String> = HashSet()
    private var lastSendTime = 0L

    private val random = Random()

    init {
        nonNullHandler<PlayerTickEvent.Pre> {
            if (!canSend()) return@nonNullHandler

            if (mode == Mode.TXT) {
                sendTxtMode()
            } else {
                sendSentenceMode()
            }

            lastSendTime = System.currentTimeMillis()
        }
    }

    override fun onEnable() {
        loadSentences()
        sentSentences.clear()
        lastSendTime = System.currentTimeMillis()
        sendMessage("已加载 " + allSentences.size + " 条句子")
    }

    override fun onDisable() {
        allSentences.clear()
        sentSentences.clear()
    }

    private fun canSend(): Boolean {
        return System.currentTimeMillis() - lastSendTime >= delay
    }

    private fun sendTxtMode() {
        if (allSentences.isEmpty()) {
            sendMessage("句子列表为空，请检查文件")
            isEnabled = false
            return
        }

        val available = ArrayList<String>()
        for (sentence in allSentences) {
            if (!sentSentences.contains(sentence)) {
                available.add(sentence)
            }
        }

        if (available.isEmpty()) {
            sentSentences.clear()
            sendMessage("所有句子已发送完毕，重新开始")
            available.addAll(allSentences)
        }

        val selected = available[random.nextInt(available.size)]
        sentSentences.add(selected)

        sendChatMessage(selected)
    }

    private fun sendSentenceMode() {
        if (allSentences.isEmpty()) {
            sendMessage("关键词列表为空，请检查文件")
            isEnabled = false
            return
        }

        val sentence = generateSentence()
        sendChatMessage(sentence)
    }

    private fun generateSentence(): String {
        val templates = listOf(
            "我\$verb了你的\$family",
            "你\$family被我\$verb了",
            "\$adjective的\$family",
            "你个\$adjective\$family",
            "我\$verb你\$family",
            "\$family被我\$verb了",
            "你\$family真\$adjective",
            "\$adjective\$family被我\$verb",
            "我\$adjective\$family",
            "\$family\$verb了你",
        )

        val verbs = listOf(
            "操", "日", "干", "草", "艹", "强奸", "上了", "爆了", "插了", "弄了",
        )

        val families = listOf(
            "妈", "母亲", "老母", "妈妈", "妈逼", "母亲逼",
            "爹", "爸", "父亲", "老爹",
            "奶奶", "爷爷", "祖宗",
            "全家", "一家老小",
        )

        val adjectives = listOf(
            "傻逼", "脑瘫", "废物", "垃圾", "贱货", "婊子",
            "狗", "猪", "畜生", "杂种", "野种",
            "死", "烂", "臭", "脏",
        )

        var template = templates[random.nextInt(templates.size)]
        val verb = verbs[random.nextInt(verbs.size)]
        val family = families[random.nextInt(families.size)]
        val adjective = adjectives[random.nextInt(adjectives.size)]

        template = template.replace("\$verb", verb)
        template = template.replace("\$family", family)
        template = template.replace("\$adjective", adjective)

        if (random.nextBoolean() && allSentences.isNotEmpty()) {
            val extraWord = allSentences[random.nextInt(minOf(20, allSentences.size))]
            if (extraWord.length < 20) {
                template = "$template，$extraWord"
            }
        }

        return template
    }

    private fun loadSentences() {
        allSentences.clear()

        val path = customPath
        if (path.isNotEmpty()) {
            loadFromCustomPath(path)
        } else {
            loadFromDefaultPath()
        }
    }

    private fun loadFromDefaultPath() {
        try {
            val stream: InputStream = javaClass.classLoader.getResourceAsStream("assets/epsilon/kouzi.txt") ?: run {
                sendMessage("无法找到默认文件")
                return
            }

            BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty() && trimmed.length <= 100) {
                        allSentences.add(trimmed)
                    }
                    line = reader.readLine()
                }
            }
        } catch (e: Exception) {
            sendMessage("加载默认文件失败: " + e.message)
        }
    }

    private fun loadFromCustomPath(path: String) {
        try {
            val filePath = Paths.get(path)
            Files.newBufferedReader(filePath, StandardCharsets.UTF_8).use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty() && trimmed.length <= 100) {
                        allSentences.add(trimmed)
                    }
                    line = reader.readLine()
                }
            }
        } catch (e: Exception) {
            sendMessage("加载自定义文件失败: " + e.message)
            loadFromDefaultPath()
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
            player.sendSystemMessage(Component.literal("[AutoKouZi] $message"))
        }
    }

}
