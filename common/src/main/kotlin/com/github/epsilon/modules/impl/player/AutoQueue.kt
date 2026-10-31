package com.github.epsilon.modules.impl.player

import com.github.epsilon.Constants
import com.github.epsilon.events.impl.PacketEvent
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.getValue
import com.github.epsilon.utils.safety.*
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

object AutoQueue : Module("Auto Queue", Category.PLAYER) {

    private enum class Mode {
        XIN_2B2T,
    }

    private val mode by enumSetting("Queue Mode", Mode.XIN_2B2T)

    private val questions: JsonObject = loadQuestions()

    init {
        nonNullListener<PacketEvent.Receive> { event ->
            if (mode != Mode.XIN_2B2T) return@nonNullListener
            val packet = event.packet
            if (packet !is ClientboundSystemChatPacket) return@nonNullListener

            val message = packet.content().string
            if (!message.contains("丨")) return@nonNullListener

            val parts = message.split("丨")
            if (parts.size != 2) return@nonNullListener

            val question = parts[0].replace(Regex("<[^>]*>"), "").trim()
            val options = parts[1].trim()

            if (!questions.has(question)) return@nonNullListener

            val match = Regex(questions.get(question).asString).find(options) ?: return@nonNullListener
            val answer = match.groupValues[1]
            player.connection.sendChat(answer)
        }
    }

    private fun loadQuestions(): JsonObject {
        try {
            BufferedReader(InputStreamReader(requireNotNull(javaClass.classLoader.getResourceAsStream("assets/epsilon/xinqueue_questions.json")), StandardCharsets.UTF_8)).use { reader ->
                val content = reader.lineSequence().joinToString("\n")
                return JsonParser.parseString(content).asJsonObject
            }
        } catch (e: Exception) {
            Constants.LOGGER.warn("Failed to load xinqueue questions", e)
            return JsonObject()
        }
    }

}
