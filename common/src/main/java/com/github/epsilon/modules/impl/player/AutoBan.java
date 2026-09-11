package com.github.epsilon.modules.impl.player;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.settings.impl.IntSetting;
import net.minecraft.network.chat.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AutoBan extends Module {

    public static final AutoBan INSTANCE = new AutoBan();

    public enum Order {
        SEQUENTIAL,
        RANDOM
    }

    private final EnumSetting<Order> order = enumSetting("Order", Order.SEQUENTIAL);
    private final IntSetting delay = intSetting("Delay", 1000, 100, 10000, 100);

    private final List<String> words = new ArrayList<>();
    private final Random random = new Random();

    private int countdownTicks = 0;
    private long lastSendTime = 0;
    private int currentIndex = 0;

    private AutoBan() {
        super("Auto Ban", Category.PLAYER);
    }

    @Override
    protected void onEnable() {
        loadWords();
        countdownTicks = 60;
        currentIndex = 0;
    }

    @Override
    protected void onDisable() {
        words.clear();
    }

    @EventHandler
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        if (nullCheck()) return;

        if (countdownTicks > 0) {
            if (countdownTicks == 60) {
                sendMessage("保护机制: 3秒后开始发送违禁词");
                sendMessage("3");
            } else if (countdownTicks == 40) {
                sendMessage("2");
            } else if (countdownTicks == 20) {
                sendMessage("1");
            }
            countdownTicks--;
            if (countdownTicks == 0) {
                sendMessage("开始发送!");
                lastSendTime = System.currentTimeMillis();
            }
            return;
        }

        if (System.currentTimeMillis() - lastSendTime >= delay.getValue()) {
            sendNextWord();
            lastSendTime = System.currentTimeMillis();
        }
    }

    private void sendNextWord() {
        if (words.isEmpty()) {
            sendMessage("词库为空，请检查文件");
            setEnabled(false);
            return;
        }

        String word;
        if (order.getValue() == Order.RANDOM) {
            word = words.get(random.nextInt(words.size()));
        } else {
            if (currentIndex >= words.size()) {
                currentIndex = 0;
            }
            word = words.get(currentIndex);
            currentIndex++;
        }

        sendChatMessage(word);
    }

    private void loadWords() {
        words.clear();
        try {
            InputStream is = getClass().getClassLoader().getResourceAsStream("assets/epsilon/autoban.txt");
            if (is == null) {
                sendMessage("无法找到默认词库文件");
                return;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    words.add(line);
                }
            }
            reader.close();
            sendMessage("已加载 " + words.size() + " 个违禁词");
        } catch (Exception e) {
            sendMessage("加载词库失败: " + e.getMessage());
        }
    }

    private void sendChatMessage(String message) {
        if (mc.player != null && !message.isEmpty()) {
            if (message.length() > 256) {
                message = message.substring(0, 256);
            }
            mc.player.connection.sendChat(message);
        }
    }

    private void sendMessage(String message) {
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("[AutoBan] " + message));
        }
    }

}
