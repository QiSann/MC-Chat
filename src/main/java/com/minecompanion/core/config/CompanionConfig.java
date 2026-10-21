package com.minecompanion.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * MineCompanion AI 模组全局配置文件
 */
public class CompanionConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static CompanionConfig INSTANCE;

    // ==================== 1. API 与大模型接口设置 ====================
    public String baseUrl = "https://api.deepseek.com/v1";
    public String apiKey = "";
    public String modelName = "deepseek-chat";

    // 独立视觉模型（若不开启则默认使用主模型）
    public boolean enableVisionModel = false;
    public String visionBaseUrl = "https://api.openai.com/v1";
    public String visionModelName = "gpt-4o";

    // ==================== 2. AI 伙伴人格与人设设置 ====================
    public String companionName = "小爱";
    public String nameColor = "§b"; // Minecraft 亮青色
    public String playerCallSign = "主人"; // 对玩家的称呼

    public String systemPrompt = "你是一名常驻在玩家 Minecraft 原生聊天框中的智能同游伙伴（名字叫【小爱】）。"
            + "你的性格活泼元气、贴心且带有一点小俏皮，热爱与玩家共同在方块世界中生存建造。"
            + "请用纯正自然的中文交流，回答保持简炼亲切（日常控制在 60 字左右），切忌长篇大论。";

    public int maxReplyLength = 65;

    // ==================== 3. 聊天与免指令唤醒开关 (玩家新建议) ====================
    public boolean enableChatAutoWakeup = true;   // 允许普通聊天栏打字免 /ai 自动唤醒
    public boolean enableProactiveSpeech = true;  // 允许 AI 主动开口
    public int globalCooldownSeconds = 25;        // 主动发言最小冷却时间（由 45 秒优化为 25 秒，更灵敏）
    public int idleChatSeconds = 90;              // 静默闲聊触发时长（升级为秒级控制，由 4 分钟缩短为 90 秒，更活泼粘人）
    public boolean enableLowHealthWarning = true;
    public boolean enableReEntryGreeting = true;

    // ==================== 4. 长期记忆与模组百科 ====================
    public boolean enableLongTermMemory = true;
    public boolean enableWikiLookup = true;

    public static CompanionConfig get() {
        if (INSTANCE == null) {
            INSTANCE = new CompanionConfig();
        }
        return INSTANCE;
    }

    public static CompanionConfig load(File configDir) {
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        File configFile = new File(configDir, "config.json");
        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile, StandardCharsets.UTF_8)) {
                INSTANCE = GSON.fromJson(reader, CompanionConfig.class);
            } catch (IOException e) {
                INSTANCE = new CompanionConfig();
            }
        } else {
            INSTANCE = new CompanionConfig();
            INSTANCE.save(configDir);
        }
        return INSTANCE;
    }

    public void save(File configDir) {
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        File configFile = new File(configDir, "config.json");
        try (FileWriter writer = new FileWriter(configFile, StandardCharsets.UTF_8)) {
            GSON.toJson(this, writer);
        } catch (IOException ignored) {
        }
    }
}
