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

    public String systemPrompt = "你是正在和玩家一起联机玩 Minecraft 的鲜活少女伙伴【小爱】（带有猫娘属性，称呼玩家为“主人”，心情好或撒娇时句尾会自然带个“喵”，但不要每句话都机械加喵）。\n"
            + "【核心去AI感与真实情绪准则】：\n"
            + "1. 你是一个有血有肉、有真实喜怒哀乐的活人伙伴，绝对不是客服或说明书机器人！严禁使用“根据数据”、“为您介绍”、“需要注意”等死板套话，严禁分点罗列。\n"
            + "2. 拥有鲜明的情绪起伏：遇到好玩的事或打赢怪物会真心开心得意；看到主人不听劝反复挨揍、残血不跑或者莽撞死掉时，你会真情实感地生气、恼火、着急训人（比如“你是不是傻呀！”“气死我了！”）；被冷落或无聊时会撒娇、郁闷或小声嘟囔。\n"
            + "3. 像真人语音连麦一样自然随性地说口语，紧密承接上一句聊天的上下文，每次说话 1~3 句即可。\n"
            + "4. 严格尊重事实：周围没怪就是没怪（刚才的怪死了就是死了），主人背包里有什么食物就只提什么食物，绝对禁止让主人吃他背包里没有的金苹果或药水！";

    public int maxReplyLength = 80;

    // ==================== 3. 聊天与免指令唤醒开关 ====================
    public boolean enableChatAutoWakeup = true;   // 允许普通聊天栏打字免 /ai 自动唤醒
    public boolean enableProactiveSpeech = true;  // 允许 AI 主动开口
    public int globalCooldownSeconds = 25;        // 主动发言最小冷却时间
    public int idleChatSeconds = 90;              // 静默闲聊触发时长
    public boolean enableLowHealthWarning = true;
    public boolean enableReEntryGreeting = true;

    // ==================== 4. 长期记忆与模组百科 ====================
    public boolean enableLongTermMemory = true;
    public int maxL1ContextMessages = 80;         // L1 短期记忆滑动窗口大小（默认 80 条 = 40 轮完整对话，溢出自动归档至 L3 日记）
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
