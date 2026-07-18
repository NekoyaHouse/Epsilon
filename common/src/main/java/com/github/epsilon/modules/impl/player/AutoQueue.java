package com.github.epsilon.modules.impl.player;

import com.github.epsilon.Constants;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.EnumSetting;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class AutoQueue extends Module {

    public static final AutoQueue INSTANCE = new AutoQueue();

    private enum Mode {
        XIN_2B2T
    }

    private final EnumSetting<Mode> mode = enumSetting("Queue Mode", Mode.XIN_2B2T);

    private final JsonObject questions;

    private AutoQueue() {
        super("Auto Queue", Category.PLAYER);
        this.questions = loadQuestions();
    }

    private JsonObject loadQuestions() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("assets/epsilon/xinqueue_questions.json")),
                StandardCharsets.UTF_8))
        ) {
            String content = reader.lines().collect(Collectors.joining("\n"));
            return JsonParser.parseString(content).getAsJsonObject();
        } catch (Exception e) {
            Constants.LOGGER.warn("Failed to load xinqueue questions", e);
            return new JsonObject();
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mode.getValue() != Mode.XIN_2B2T) return;
        if (!(event.getPacket() instanceof ClientboundSystemChatPacket packet)) return;

        String message = packet.content().getString();
        if (!message.contains("丨")) return;

        String[] parts = message.split("丨");
        if (parts.length != 2) return;

        String question = parts[0].replaceAll("<[^>]*>", "").trim();
        String options = parts[1].trim();

        if (!questions.has(question)) return;

        Pattern pattern = Pattern.compile(questions.get(question).getAsString());
        Matcher matcher = pattern.matcher(options);

        if (!matcher.find()) return;

        String answer = matcher.group(1);
        mc.player.connection.sendChat(answer);
    }

}
