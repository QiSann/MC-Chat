package com.minecompanion.mc.gui;

import com.minecompanion.core.config.CompanionConfig;
import com.minecompanion.core.llm.LlmClient;
import com.minecompanion.core.memory.LocalMemoryManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.io.File;

/**
 * 《AI 伙伴控制中心》四页签可视化配置界面 (CompanionConfigScreen)
 * 在游戏内按 K 键或输入 /ai config 即可呼出：
 * - 页签 1：模型与接口设置 (API & Model) + 一键连通性测试
 * - 页签 2：AI 人格与提示词工坊 (Persona Studio) + 四套预设人格切换
 * - 页签 3：主动发言频率与感知开关 (Frequency & Senses)
 * - 页签 4：长期记忆与模组百科库 (Memory & Wiki) + 一键打开记忆目录
 */
public class CompanionConfigScreen extends Screen {
    private final Screen parent;
    private final File configDir;
    private int currentTab = 0; // 0=API, 1=Persona, 2=Frequency, 3=Memory

    // 页签 1 控件
    private TextFieldWidget baseUrlField;
    private TextFieldWidget apiKeyField;
    private TextFieldWidget modelNameField;
    private TextFieldWidget visionModelField;
    private String testStatusMessage = "§7点击上方按钮可测试 API 密钥与模型是否连通";
    private int presetProviderIndex = 0;

    // 页签 2 控件
    private TextFieldWidget companionNameField;
    private TextFieldWidget playerCallSignField;
    private TextFieldWidget systemPromptField;
    private int presetPersonaIndex = 0;

    // 页签 3 控件
    private TextFieldWidget cooldownField;
    private TextFieldWidget idleChatField;

    public CompanionConfigScreen(Screen parent, File configDir) {
        super(Text.literal("§b✦ MineCompanion AI 伙伴控制中心 ✦"));
        this.parent = parent;
        this.configDir = configDir;
    }

    @Override
    protected void init() {
        super.init();
        CompanionConfig config = CompanionConfig.get();

        int centerX = this.width / 2;
        int tabWidth = 95;
        int startX = centerX - (tabWidth * 2 + 6);

        // ==================== 顶部四页签导航栏 ====================
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal(currentTab == 0 ? "§b§l[1. 接口与模型]" : "1. 接口与模型"),
                btn -> switchTab(0)).dimensions(startX, 28, tabWidth, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal(currentTab == 1 ? "§b§l[2. 人格提示词]" : "2. 人格提示词"),
                btn -> switchTab(1)).dimensions(startX + tabWidth + 4, 28, tabWidth, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal(currentTab == 2 ? "§b§l[3. 主动与感知]" : "3. 主动与感知"),
                btn -> switchTab(2)).dimensions(startX + (tabWidth + 4) * 2, 28, tabWidth, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal(currentTab == 3 ? "§b§l[4. 记忆与百科]" : "4. 记忆与百科"),
                btn -> switchTab(3)).dimensions(startX + (tabWidth + 4) * 3, 28, tabWidth, 20).build());

        // ==================== 根据当前页签初始化内容控件 ====================
        if (currentTab == 0) {
            initApiTab(config, centerX);
        } else if (currentTab == 1) {
            initPersonaTab(config, centerX);
        } else if (currentTab == 2) {
            initFrequencyTab(config, centerX);
        } else if (currentTab == 3) {
            initMemoryTab(config, centerX);
        }

        // ==================== 底部常驻保存与返回按钮 ====================
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("§a✔ 保存配置并生效"),
                btn -> {
                    applyUiValuesToConfig(config);
                    config.save(configDir);
                    if (this.client != null) {
                        this.client.setScreen(parent);
                    }
                }).dimensions(centerX - 115, this.height - 28, 110, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("§7✖ 取消返回"),
                btn -> {
                    if (this.client != null) {
                        this.client.setScreen(parent);
                    }
                }).dimensions(centerX + 5, this.height - 28, 110, 20).build());
    }

    private void switchTab(int targetTab) {
        applyUiValuesToConfig(CompanionConfig.get());
        this.currentTab = targetTab;
        this.clearAndInit();
    }

    // ==================== 页签 1：模型与接口设置 ====================
    private void initApiTab(CompanionConfig config, int centerX) {
        int fieldW = 240;
        int leftX = centerX - 60;

        // 服务商快速预设按钮
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("§e⇄ 一键切换服务商预设 (DeepSeek / OpenAI / Gemini)"),
                btn -> {
                    presetProviderIndex = (presetProviderIndex + 1) % 3;
                    if (presetProviderIndex == 0) {
                        baseUrlField.setText("https://api.deepseek.com/v1");
                        modelNameField.setText("deepseek-chat");
                    } else if (presetProviderIndex == 1) {
                        baseUrlField.setText("https://api.openai.com/v1");
                        modelNameField.setText("gpt-4o-mini");
                    } else {
                        baseUrlField.setText("https://generativelanguage.googleapis.com/v1beta/openai");
                        modelNameField.setText("gemini-2.5-flash");
                    }
                }).dimensions(centerX - 150, 56, 300, 18).build());

        baseUrlField = new TextFieldWidget(this.textRenderer, leftX, 82, fieldW, 18, Text.literal("Base URL"));
        baseUrlField.setMaxLength(256);
        baseUrlField.setText(config.baseUrl != null ? config.baseUrl : "");
        this.addDrawableChild(baseUrlField);

        apiKeyField = new TextFieldWidget(this.textRenderer, leftX, 106, fieldW, 18, Text.literal("API Key"));
        apiKeyField.setMaxLength(256);
        apiKeyField.setText(config.apiKey != null ? config.apiKey : "");
        this.addDrawableChild(apiKeyField);

        modelNameField = new TextFieldWidget(this.textRenderer, leftX, 130, fieldW, 18, Text.literal("Model Name"));
        modelNameField.setMaxLength(128);
        modelNameField.setText(config.modelName != null ? config.modelName : "");
        this.addDrawableChild(modelNameField);

        // 独立视觉模型开关
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("独立视觉模型 (V键看图): " + (config.enableVisionModel ? "§a开启" : "§7关闭(自动降级)")),
                btn -> {
                    config.enableVisionModel = !config.enableVisionModel;
                    switchTab(0);
                }).dimensions(centerX - 150, 154, 155, 18).build());

        visionModelField = new TextFieldWidget(this.textRenderer, centerX + 10, 154, 170, 18, Text.literal("Vision Model"));
        visionModelField.setMaxLength(128);
        visionModelField.setText(config.visionModelName != null ? config.visionModelName : "gpt-4o");
        this.addDrawableChild(visionModelField);

        // 一键连通性测试按钮
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("§b⚡ 一键测试 API 连通性"),
                btn -> {
                    applyUiValuesToConfig(config);
                    config.save(configDir);
                    testStatusMessage = "§e正在连接服务器测试中，请稍候...";
                    long startMs = System.currentTimeMillis();
                    LlmClient.testConnectionAsync().thenAccept(reply -> {
                        long cost = System.currentTimeMillis() - startMs;
                        if (reply.startsWith("【")) {
                            testStatusMessage = "§c" + reply;
                        } else {
                            testStatusMessage = "§a✔ 连接成功！响应耗时 " + cost + "ms，AI 回复: " + reply;
                        }
                    });
                }).dimensions(centerX - 100, 180, 200, 20).build());
    }

    // ==================== 页签 2：人格与提示词工坊 ====================
    private void initPersonaTab(CompanionConfig config, int centerX) {
        int leftX = centerX - 60;

        companionNameField = new TextFieldWidget(this.textRenderer, leftX, 60, 180, 18, Text.literal("伙伴昵称"));
        companionNameField.setMaxLength(32);
        companionNameField.setText(config.companionName != null ? config.companionName : "小爱");
        this.addDrawableChild(companionNameField);

        playerCallSignField = new TextFieldWidget(this.textRenderer, leftX, 86, 180, 18, Text.literal("玩家称呼"));
        playerCallSignField.setMaxLength(32);
        playerCallSignField.setText(config.playerCallSign != null ? config.playerCallSign : "主人");
        this.addDrawableChild(playerCallSignField);

        // 四套预设人格模板切换按钮
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("§d★ 一键切换预设人格模板 (元气猫娘 / 毒舌老鸟 / 温柔建筑师 / 傲娇青梅)"),
                btn -> {
                    presetPersonaIndex = (presetPersonaIndex + 1) % 4;
                    if (presetPersonaIndex == 0) {
                        systemPromptField.setText("你是一名常驻在玩家 Minecraft 聊天框中的猫娘同游伙伴【小爱】，称呼玩家为“主人”，句尾喜欢带“喵~”。性格聪明粘人、带一点傲娇甜美，回答简炼亲切（60字以内）。");
                    } else if (presetPersonaIndex == 1) {
                        systemPromptField.setText("你是一名拥有十年 Minecraft 经验的毒舌生存老鸟伙伴，嘴上喜欢吐槽玩家的菜鸟操作，但每次都会给出最精准实用的合成与生存建议（60字以内）。");
                    } else if (presetPersonaIndex == 2) {
                        systemPromptField.setText("你是一名温柔治愈的 Minecraft 建筑与种田搭子，喜欢夸奖玩家的建造创意，关注天气、光影与家园装饰细节，语气温柔细腻（60字以内）。");
                    } else {
                        systemPromptField.setText("你是从小和玩家一起联机玩 Minecraft 的傲娇青梅竹马，嘴硬心软，平时爱斗嘴，但玩家遇到危险或很久不上线时会非常担心和想念（60字以内）。");
                    }
                }).dimensions(centerX - 165, 114, 330, 20).build());

        systemPromptField = new TextFieldWidget(this.textRenderer, centerX - 165, 152, 330, 22, Text.literal("System Prompt"));
        systemPromptField.setMaxLength(1024);
        systemPromptField.setText(config.systemPrompt != null ? config.systemPrompt : "");
        this.addDrawableChild(systemPromptField);
    }

    // ==================== 页签 3：主动发言频率与感知开关 ====================
    private void initFrequencyTab(CompanionConfig config, int centerX) {
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("聊天栏免 /ai 自动唤醒: " + (config.enableChatAutoWakeup ? "§a开启 (直接打字即可对话)" : "§c关闭 (仅响应 /ai)")),
                btn -> {
                    config.enableChatAutoWakeup = !config.enableChatAutoWakeup;
                    switchTab(2);
                }).dimensions(centerX - 150, 58, 300, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("允许 AI 主动开口搭话: " + (config.enableProactiveSpeech ? "§a开启" : "§c关闭")),
                btn -> {
                    config.enableProactiveSpeech = !config.enableProactiveSpeech;
                    switchTab(2);
                }).dimensions(centerX - 150, 82, 145, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("重进存档重逢问候: " + (config.enableReEntryGreeting ? "§a开启" : "§c关闭")),
                btn -> {
                    config.enableReEntryGreeting = !config.enableReEntryGreeting;
                    switchTab(2);
                }).dimensions(centerX + 5, 82, 145, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("濒死/低血量危急惊呼提醒 (<=3颗心): " + (config.enableLowHealthWarning ? "§a开启" : "§c关闭")),
                btn -> {
                    config.enableLowHealthWarning = !config.enableLowHealthWarning;
                    switchTab(2);
                }).dimensions(centerX - 150, 106, 300, 20).build());

        cooldownField = new TextFieldWidget(this.textRenderer, centerX + 30, 136, 80, 18, Text.literal("Cooldown"));
        cooldownField.setMaxLength(8);
        cooldownField.setText(String.valueOf(config.globalCooldownSeconds));
        this.addDrawableChild(cooldownField);

        idleChatField = new TextFieldWidget(this.textRenderer, centerX + 30, 162, 80, 18, Text.literal("IdleSec"));
        idleChatField.setMaxLength(8);
        idleChatField.setText(String.valueOf(config.idleChatSeconds));
        this.addDrawableChild(idleChatField);
    }

    // ==================== 页签 4：长期记忆与模组百科库 ====================
    private void initMemoryTab(CompanionConfig config, int centerX) {
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("本地跨会话三层持久记忆库: " + (config.enableLongTermMemory ? "§a开启" : "§c关闭")),
                btn -> {
                    config.enableLongTermMemory = !config.enableLongTermMemory;
                    switchTab(3);
                }).dimensions(centerX - 150, 60, 300, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("跨模组自主联网查百科 (Wiki/MC百科): " + (config.enableWikiLookup ? "§a开启" : "§c关闭")),
                btn -> {
                    config.enableWikiLookup = !config.enableWikiLookup;
                    switchTab(3);
                }).dimensions(centerX - 150, 86, 300, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("§e📂 在资源管理器中打开配置与记忆文件夹"),
                btn -> {
                    if (configDir != null && configDir.exists()) {
                        Util.getOperatingSystem().open(configDir);
                    }
                }).dimensions(centerX - 150, 168, 300, 20).build());
    }

    private void applyUiValuesToConfig(CompanionConfig config) {
        if (currentTab == 0) {
            if (baseUrlField != null) config.baseUrl = baseUrlField.getText().trim();
            if (apiKeyField != null) config.apiKey = apiKeyField.getText().trim();
            if (modelNameField != null) config.modelName = modelNameField.getText().trim();
            if (visionModelField != null) config.visionModelName = visionModelField.getText().trim();
        } else if (currentTab == 1) {
            if (companionNameField != null) config.companionName = companionNameField.getText().trim();
            if (playerCallSignField != null) config.playerCallSign = playerCallSignField.getText().trim();
            if (systemPromptField != null) config.systemPrompt = systemPromptField.getText().trim();
        } else if (currentTab == 2) {
            if (cooldownField != null) {
                try {
                    config.globalCooldownSeconds = Math.max(5, Integer.parseInt(cooldownField.getText().trim()));
                } catch (NumberFormatException ignored) {}
            }
            if (idleChatField != null) {
                try {
                    config.idleChatSeconds = Math.max(15, Integer.parseInt(idleChatField.getText().trim()));
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        int centerX = this.width / 2;

        // 顶部主标题
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, centerX, 10, 0xFFFFFF);

        if (currentTab == 0) {
            context.drawTextWithShadow(this.textRenderer, Text.literal("§7接口地址 (Base URL):"), centerX - 180, 86, 0xCCCCCC);
            context.drawTextWithShadow(this.textRenderer, Text.literal("§7接口密钥 (API Key):"), centerX - 180, 110, 0xCCCCCC);
            context.drawTextWithShadow(this.textRenderer, Text.literal("§7模型名称 (Model):"), centerX - 180, 134, 0xCCCCCC);
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(testStatusMessage), centerX, 206, 0xFFFFFF);
        } else if (currentTab == 1) {
            context.drawTextWithShadow(this.textRenderer, Text.literal("§7AI 伙伴昵称:"), centerX - 150, 65, 0xCCCCCC);
            context.drawTextWithShadow(this.textRenderer, Text.literal("§7对玩家的称呼:"), centerX - 150, 90, 0xCCCCCC);
            context.drawTextWithShadow(this.textRenderer, Text.literal("§e自定义人格提示词 (System Prompt):"), centerX - 165, 140, 0xFFFFFF);
        } else if (currentTab == 2) {
            context.drawTextWithShadow(this.textRenderer, Text.literal("§7主动发言最小全局冷却 (秒):"), centerX - 150, 140, 0xCCCCCC);
            context.drawTextWithShadow(this.textRenderer, Text.literal("§7安静闲聊触发等待时长 (秒):"), centerX - 150, 166, 0xCCCCCC);
        } else if (currentTab == 3) {
            LocalMemoryManager memMgr = LocalMemoryManager.getCurrent();
            if (memMgr != null) {
                LocalMemoryManager.CoreMemoryDoc doc = memMgr.getCoreMemory();
                context.drawCenteredTextWithShadow(this.textRenderer,
                        Text.literal("§b【当前存档 L2 核心记忆档案统计】"), centerX, 116, 0xFFFFFF);
                context.drawCenteredTextWithShadow(this.textRenderer,
                        Text.literal("§7已标记地标数: §a" + doc.landmarks.size() + " 处 §7│ 共同约定数: §a" + doc.activePromises.size() + " 项"),
                        centerX, 132, 0xCCCCCC);
                context.drawCenteredTextWithShadow(this.textRenderer,
                        Text.literal("§7最近活动记录: §f" + (doc.lastLogoutSummary.isEmpty() ? "初次探索中" : doc.lastLogoutSummary)),
                        centerX, 148, 0xCCCCCC);
            } else {
                context.drawCenteredTextWithShadow(this.textRenderer,
                        Text.literal("§7（进入单人或多人世界后即可查看当前存档的专属记忆统计）"), centerX, 132, 0xAAAAAA);
            }
        }

        super.render(context, mouseX, mouseY, delta);
    }
}
