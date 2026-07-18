package com.github.epsilon.modules.impl.player;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.settings.impl.StringSetting;
import net.minecraft.network.chat.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/*
 * @author Moli
 * todo: 点名道姓模式、支持dm、扩充语料库
 */

public class AutoKouZi extends Module {

    public static final AutoKouZi INSTANCE = new AutoKouZi();

    public enum Mode {
        TXT,
        SENTENCE
    }

    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.TXT);
    private final IntSetting delay = intSetting("Delay", 1000, 100, 10000, 100);
    private final StringSetting customPath = stringSetting("Custom Path", "");

    private List<String> allSentences = new ArrayList<>();
    private Set<String> sentSentences = new HashSet<>();
    private long lastSendTime = 0;

    private final Random random = new Random();

    private AutoKouZi() {
        super("Auto KouZi", Category.PLAYER);
    }

    @Override
    protected void onEnable() {
        loadSentences();
        sentSentences.clear();
        lastSendTime = System.currentTimeMillis();
        sendMessage("已加载 " + allSentences.size() + " 条句子");
    }

    @Override
    protected void onDisable() {
        allSentences.clear();
        sentSentences.clear();
    }

    @EventHandler
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        if (!canSend()) return;

        if (mode.getValue() == Mode.TXT) {
            sendTxtMode();
        } else {
            sendSentenceMode();
        }

        lastSendTime = System.currentTimeMillis();
    }

    private boolean canSend() {
        return System.currentTimeMillis() - lastSendTime >= delay.getValue();
    }

    private void sendTxtMode() {
        if (allSentences.isEmpty()) {
            sendMessage("句子列表为空，请检查文件");
            setEnabled(false);
            return;
        }

        List<String> available = new ArrayList<>();
        for (String sentence : allSentences) {
            if (!sentSentences.contains(sentence)) {
                available.add(sentence);
            }
        }

        if (available.isEmpty()) {
            sentSentences.clear();
            sendMessage("所有句子已发送完毕，重新开始");
            available.addAll(allSentences);
        }

        String selected = available.get(random.nextInt(available.size()));
        sentSentences.add(selected);

        sendChatMessage(selected);
    }

    private void sendSentenceMode() {
        if (allSentences.isEmpty()) {
            sendMessage("关键词列表为空，请检查文件");
            setEnabled(false);
            return;
        }

        String sentence = generateSentence();
        sendChatMessage(sentence);
    }

    private String generateSentence() {
        List<String> templates = Arrays.asList(
                "我$verb了你的$family",
                "你$family被我$verb了",
                "$adjective的$family",
                "你个$adjective$family",
                "我$verb你$family",
                "$family被我$verb了",
                "你$family真$adjective",
                "$adjective$family被我$verb",
                "我$adjective$family",
                "$family$verb了你"
        );

        List<String> verbs = Arrays.asList(
                "操", "日", "干", "草", "艹", "强奸", "上了", "爆了", "插了", "弄了"
        );

        List<String> families = Arrays.asList(
                "妈", "母亲", "老母", "妈妈", "妈逼", "母亲逼",
                "爹", "爸", "父亲", "老爹",
                "奶奶", "爷爷", "祖宗",
                "全家", "一家老小"
        );

        List<String> adjectives = Arrays.asList(
                "傻逼", "脑瘫", "废物", "垃圾", "贱货", "婊子",
                "狗", "猪", "畜生", "杂种", "野种",
                "死", "烂", "臭", "脏"
        );

        String template = templates.get(random.nextInt(templates.size()));
        String verb = verbs.get(random.nextInt(verbs.size()));
        String family = families.get(random.nextInt(families.size()));
        String adjective = adjectives.get(random.nextInt(adjectives.size()));

        template = template.replace("$verb", verb);
        template = template.replace("$family", family);
        template = template.replace("$adjective", adjective);

        if (random.nextBoolean() && !allSentences.isEmpty()) {
            String extraWord = allSentences.get(random.nextInt(Math.min(20, allSentences.size())));
            if (extraWord.length() < 20) {
                template = template + "，" + extraWord;
            }
        }

        return template;
    }

    private void loadSentences() {
        allSentences.clear();

        String path = customPath.getValue();
        if (!path.isEmpty()) {
            loadFromCustomPath(path);
        } else {
            loadFromDefaultPath();
        }
    }

    private void loadFromDefaultPath() {
        try {
            InputStream is = getClass().getClassLoader().getResourceAsStream(
                    "assets/epsilon/kouzi.txt"
            );

            if (is == null) {
                sendMessage("无法找到默认文件");
                return;
            }

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8)
            );

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && line.length() <= 100) {
                    allSentences.add(line);
                }
            }

            reader.close();
        } catch (Exception e) {
            sendMessage("加载默认文件失败: " + e.getMessage());
        }
    }

    private void loadFromCustomPath(String path) {
        try {
            java.nio.file.Path filePath = java.nio.file.Paths.get(path);
            BufferedReader reader = java.nio.file.Files.newBufferedReader(filePath, StandardCharsets.UTF_8);

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && line.length() <= 100) {
                    allSentences.add(line);
                }
            }

            reader.close();
        } catch (Exception e) {
            sendMessage("加载自定义文件失败: " + e.getMessage());
            loadFromDefaultPath();
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
            mc.player.sendSystemMessage(Component.literal("[AutoKouZi] " + message));
        }
    }

}
