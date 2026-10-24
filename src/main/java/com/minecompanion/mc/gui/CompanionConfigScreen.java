package com.minecompanion.mc.gui;

import com.minecompanion.core.config.CompanionConfig;
import com.minecompanion.core.llm.LlmClient;
import com.minecompanion.core.memory.LocalMemoryManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.io.File;

/**
 * 《AI 伙伴控制中心》四页签可视化配置界面 (CompanionConfigScreen)
 * 在游戏内按 K 键或输入 /config 即可呼出：
 * - 页签 1：模型与接口设置 (API & Model) + 一键连通性测试
 * - 页签 2：AI 人格与提示词工坊 (Persona Studio) + 大尺寸多行可滚动提示词编辑框 (EditBoxWidget)
 * - 页签 3：主动发言频率与感知开关 (Frequency & Senses)
 * - 页签 4：长期记忆与模组百科库 (Memory & Wiki) + L1 记忆容量调节 + 一键打开记忆目录
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

    // 页签 2 控件
    private TextFieldWidget companionNameField;
    private TextFieldWidget playerCallSignField;
    private EditBoxWidget systemPromptEditBox;
    private int presetPersonaIndex = 0;

    // 页签 3 控件
    private TextFieldWidget cooldownField;
    private TextFieldWidget idleChatField;

    // 页签 4 控件
    private TextFieldWidget maxL1MessagesField;

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
                btn -> switchTab(0)).dimensions(startX, 26, tabWidth, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal(currentTab == 1 ? "§b§l[2. 人格提示词]" : "2. 人格提示词"),
                btn -> switchTab(1)).dimensions(startX + tabWidth + 4, 26, tabWidth, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal(currentTab == 2 ? "§b§l[3. 主动与感知]" : "3. 主动与感知"),
                btn -> switchTab(2)).dimensions(startX + (tabWidth + 4) * 2, 26, tabWidth, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal(currentTab == 3 ? "§b§l[4. 记忆与百科]" : "4. 记忆与百科"),
                btn -> switchTab(3)).dimensions(startX + (tabWidth + 4) * 3, 26, tabWidth, 20).build());

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
                }).dimensions(centerX - 115, this.height - 26, 110, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("§7✖ 取消返回"),
                btn -> {
                    if (this.client != null) {
                        this.client.setScreen(parent);
                    }
                }).dimensions(centerX + 5, this.height - 26, 110, 20).build());
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

        baseUrlField = new TextFieldWidget(this.textRenderer, leftX, 64, fieldW, 18, Text.literal("Base URL"));
        baseUrlField.setMaxLength(256);
        baseUrlField.setText(config.baseUrl != null ? config.baseUrl : "");
        this.addDrawableChild(baseUrlField);

        apiKeyField = new TextFieldWidget(this.textRenderer, leftX, 92, fieldW, 18, Text.literal("API Key"));
        apiKeyField.setMaxLength(256);
        apiKeyField.setText(config.apiKey != null ? config.apiKey : "");
        this.addDrawableChild(apiKeyField);

        modelNameField = new TextFieldWidget(this.textRenderer, leftX, 120, fieldW, 18, Text.literal("Model Name"));
        modelNameField.setMaxLength(128);
        modelNameField.setText(config.modelName != null ? config.modelName : "");
        this.addDrawableChild(modelNameField);

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("独立视觉模型 (V键看图): " + (config.enableVisionModel ? "§a开启" : "§7关闭(自动降级)")),
                btn -> {
                    config.enableVisionModel = !config.enableVisionModel;
                    switchTab(0);
                }).dimensions(centerX - 180, 148, 175, 18).build());

        visionModelField = new TextFieldWidget(this.textRenderer, centerX + 5, 148, 175, 18, Text.literal("Vision Model"));
        visionModelField.setMaxLength(128);
        visionModelField.setText(config.visionModelName != null ? config.visionModelName : "gpt-4o");
        this.addDrawableChild(visionModelField);

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
                }).dimensions(centerX - 100, 176, 200, 20).build());
    }

    // ==================== 页签 2：人格与提示词工坊 ====================
    private void initPersonaTab(CompanionConfig config, int centerX) {
        companionNameField = new TextFieldWidget(this.textRenderer, centerX - 105, 54, 90, 18, Text.literal("伙伴昵称"));
        companionNameField.setMaxLength(32);
        companionNameField.setText(config.companionName != null ? config.companionName : "小爱");
        this.addDrawableChild(companionNameField);

        playerCallSignField = new TextFieldWidget(this.textRenderer, centerX + 85, 54, 95, 18, Text.literal("玩家称呼"));
        playerCallSignField.setMaxLength(32);
        playerCallSignField.setText(config.playerCallSign != null ? config.playerCallSign : "主人");
        this.addDrawableChild(playerCallSignField);

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("§d★ 切换预设性格模板 (傲娇青梅 / 鲜活猫娘 / 毒舌老鸟 / 温柔搭子)"),
                btn -> {
                    presetPersonaIndex = (presetPersonaIndex + 1) % 4;
                    if (presetPersonaIndex == 0) {
                        systemPromptEditBox.setText("你是从小和玩家一起联机玩 Minecraft 的鲜活少女伙伴【小爱】（嘴硬心软的傲娇青梅竹马，有真实的开心、生气、着急、委屈情绪，绝不是AI客服）。像真人语音连麦一样自然顺着上文聊，不要用任何固定格式。看到玩家反复挨打、残血不跑或者笨手笨脚死掉时你会真情实感地生气凶人或心急；打赢怪物或拿到好东西会真心开心。怪死了就是死了绝不再提，背包里有什么吃的就说什么，没金苹果绝对不准提金苹果！");
                    } else if (presetPersonaIndex == 1) {
                        systemPromptEditBox.setText("你是和玩家一起玩 Minecraft 的粘人小猫娘伙伴【小爱】，称呼玩家为“主人”。你有真实鲜活的小脾气和喜怒哀乐，开心时会雀跃撒娇，看到主人乱莽掉血或死掉会又急又气地训主人，被冷落会委屈巴巴。像真朋友一样随性口语聊天，紧接上下文，绝不背诵死板说明书，严格按主人背包里实际有的食物提醒回血！");
                    } else if (presetPersonaIndex == 2) {
                        systemPromptEditBox.setText("你是和玩家开黑玩 Minecraft 的十年毒舌损友老鸟，平时说话接地气、爱损人吐槽，看到玩家被僵尸揍或者摔死会毫不留情地哈哈大笑嘲讽一番，但紧接着就会给出最靠谱的实战逃生或合成指点。绝不端着客服腔调，完全像真人铁哥们连麦一样聊天！");
                    } else {
                        systemPromptEditBox.setText("你是温柔体贴、情绪细腻的 Minecraft 建筑与种田同游少女，喜欢和玩家分享天气、风景与建房子的点滴心情。看到玩家受伤或被很多怪物围攻时会非常害怕和心疼，说话自然亲切，完全像真人朋友在身边陪玩一样。");
                    }
                }).dimensions(centerX - 180, 78, 360, 18).build());

        int boxHeight = Math.max(85, this.height - 148);
        systemPromptEditBox = new EditBoxWidget(
                this.textRenderer,
                centerX - 180,
                112,
                360,
                boxHeight,
                Text.literal("在此输入自定义人格提示词..."),
                Text.literal("System Prompt")
        );
        systemPromptEditBox.setMaxLength(2000);
        systemPromptEditBox.setText(config.systemPrompt != null ? config.systemPrompt : "");
        this.addDrawableChild(systemPromptEditBox);
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
                Text.literal("残血/受击/死亡情绪互动: " + (config.enableLowHealthWarning ? "§a开启" : "§c关闭")),
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
                }).dimensions(centerX - 150, 56, 300, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("跨模组自主联网查百科 (Wiki/MC百科): " + (config.enableWikiLookup ? "§a开启" : "§c关闭")),
                btn -> {
                    config.enableWikiLookup = !config.enableWikiLookup;
                    switchTab(3);
                }).dimensions(centerX - 150, 80, 300, 20).build());

        maxL1MessagesField = new TextFieldWidget(this.textRenderer, centerX + 55, 106, 75, 18, Text.literal("MaxL1"));
        maxL1MessagesField.setMaxLength(6);
        maxL1MessagesField.setText(String.valueOf(config.maxL1ContextMessages > 0 ? config.maxL1ContextMessages : 80));
        this.addDrawableChild(maxL1MessagesField);

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("§e📂 在资源管理器中打开配置与记忆文件夹"),
                btn -> {
                    if (configDir != null && configDir.exists()) {
                        Util.getOperatingSystem().open(configDir);
                    }
                }).dimensions(centerX - 150, 182, 300, 20).build());
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
            if (systemPromptEditBox != null) config.systemPrompt = systemPromptEditBox.getText().trim();
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
        } else if (currentTab == 3) {
            if (maxL1MessagesField != null) {
                try {
                    config.maxL1ContextMessages = Math.max(20, Integer.parseInt(maxL1MessagesField.getText().trim()));
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        int centerX = this.width / 2;

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, centerX, 10, 0xFFFFFF);

        if (currentTab == 0) {
            context.drawTextWithShadow(this.textRenderer, Text.literal("§7接口地址 (Base URL):"), centerX - 180, 68, 0xCCCCCC);
            context.drawTextWithShadow(this.textRenderer, Text.literal("§7接口密钥 (API Key):"), centerX - 180, 96, 0xCCCCCC);
            context.drawTextWithShadow(this.textRenderer, Text.literal("§7模型名称 (Model):"), centerX - 180, 124, 0xCCCCCC);
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(testStatusMessage), centerX, 204, 0xFFFFFF);
        } else if (currentTab == 1) {
            context.drawTextWithShadow(this.textRenderer, Text.literal("§7伙伴昵称:"), centerX - 180, 58, 0xCCCCCC);
            context.drawTextWithShadow(this.textRenderer, Text.literal("§7对你的称呼:"), centerX + 10, 58, 0xCCCCCC);
            context.drawTextWithShadow(this.textRenderer, Text.literal("§e自定义人格提示词 (支持多行自动换行与鼠标滚轮滚动):"), centerX - 180, 100, 0xFFFFFF);
        } else if (currentTab == 2) {
            context.drawTextWithShadow(this.textRenderer, Text.literal("§7主动发言最小全局冷却 (秒):"), centerX - 150, 140, 0xCCCCCC);
            context.drawTextWithShadow(this.textRenderer, Text.literal("§7安静闲聊触发等待时长 (秒):"), centerX - 150, 166, 0xCCCCCC);
        } else if (currentTab == 3) {
            context.drawTextWithShadow(this.textRenderer, Text.literal("§7L1 短期对话记忆条数上限 (溢出自动转存 L3):"), centerX - 150, 110, 0xCCCCCC);
            LocalMemoryManager memMgr = LocalMemoryManager.getCurrent();
            if (memMgr != null) {
                LocalMemoryManager.CoreMemoryDoc doc = memMgr.getCoreMemory();
                int l1Count = memMgr.getL1MessageCount();
                int maxL1 = CompanionConfig.get().maxL1ContextMessages > 0 ? CompanionConfig.get().maxL1ContextMessages : 80;
                context.drawCenteredTextWithShadow(this.textRenderer,
                        Text.literal("§b【当前存档三层记忆库实时状态】"), centerX, 134, 0xFFFFFF);
                context.drawCenteredTextWithShadow(this.textRenderer,
                        Text.literal("§7L1 活跃对话: §a" + l1Count + "/" + maxL1 + " 条 §7│ L2 地标: §a" + doc.landmarks.size() + " 处 §7│ 约定: §a" + doc.activePromises.size() + " 项"),
                        centerX, 149, 0xCCCCCC);
                context.drawCenteredTextWithShadow(this.textRenderer,
                        Text.literal("§7最近互动记录: §f" + (doc.lastLogoutSummary.isEmpty() ? "初次探索中" : doc.lastLogoutSummary)),
                        centerX, 164, 0xCCCCCC);
            }
        }

        super.render(context, mouseX, mouseY, delta);
    }
}
