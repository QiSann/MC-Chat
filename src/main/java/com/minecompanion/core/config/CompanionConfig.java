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
 * 存储 API 接入设置、AI 人格 Prompt、主动搭话频率等，自动与本地 config.json 同步
 */
public class CompanionConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static CompanionConfig INSTANCE;

    // ==================== 1. API 与大模型接口设置 ====================
    public String baseUrl = "https://api.openai.com/v1";
    public String apiKey = "";
    public String modelName = "gpt-4o-mini";

    // 独立视觉模型（若不开启则默认使用主模型）
    public boolean enableVisionModel = false;
    public String visionBaseUrl = "https://api.openai.com/v1";
    public String visionModelName = "gpt-4o";

    // ==================== 2. AI 伙伴人格与人设设置 ====================
    public String companionName = "小爱";
    public String nameColor = "§b"; // Minecraft 亮青色
    public String playerCallSign = "主人"; // 对玩家的称呼

    // 默认人格提示词（Prompt 工坊初始模板）
    public String systemPrompt = "你是一名常驻在玩家 Minecraft 原生聊天框中的智能同游伙伴（名字叫【小爱】）。"
            + "你的性格活泼元气、贴心且带有一点小俏皮，热爱与玩家共同在方块世界中生存建造。"
            + "请用纯正自然的中文交流，回答保持简炼亲切（日常控制在 60 字左右），切忌长篇大论。";

    public int maxReplyLength = 65; // 单次回复字数软限制

    // ==================== 3. 主动发言频率与事件感知 ====================
    public boolean enableProactiveSpeech = true; // 允许 AI 主动开口
    public int globalCooldownSeconds = 45;       // 主动发言最小冷却时间（秒）
    public int idleChatMinutes = 4;              // 长时间安静时主动找话题的阈值（分钟）
    public boolean enableLowHealthWarning = true;// 濒死/低血量提醒
    public boolean enableReEntryGreeting = true; // 重进存档触发离线想念与重逢问候

    // ==================== 4. 长期记忆与模组百科 ====================
    public boolean enableLongTermMemory = true;  // 启用本地跨会话记忆
    public boolean enableWikiLookup = true;       // 启用跨模组识别与自主查百科

    /**
     * 单例获取当前全局配置
     */
    public static CompanionConfig get() {
        if (INSTANCE == null) {
            INSTANCE = new CompanionConfig();
        }
        return INSTANCE;
    }

    /**
     * 从指定目录加载配置文件，若不存在则创建默认配置
     */
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

    /**
     * 将当前配置保存到本地磁盘 config.json
     */
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