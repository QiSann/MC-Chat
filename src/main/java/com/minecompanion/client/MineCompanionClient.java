package com.minecompanion.client;

import com.minecompanion.MineCompanionMod;
import com.minecompanion.core.config.CompanionConfig;
import com.minecompanion.core.llm.LlmClient;
import com.minecompanion.mc.chat.ChatColorFormatter;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * MineCompanion AI 模组客户端入口类
 * 负责注册客户端指令 /ai、拦截聊天与异步消息安全推送到 ChatHud
 */
public class MineCompanionClient implements ClientModInitializer {
    public static final Logger CLIENT_LOGGER = LoggerFactory.getLogger(MineCompanionMod.MOD_ID + "-client");

    @Override
    public void onInitializeClient() {
        CLIENT_LOGGER.info("[{}] 客户端陪伴感知与聊天渲染层正在初始化喵！(=^･ω･^=)", MineCompanionMod.MOD_NAME);

        // 1. 初始化并加载本地配置文件 (.minecraft/config/minecompanion/)
        MinecraftClient client = MinecraftClient.getInstance();
        File configDir = new File(client.runDirectory, "config/minecompanion");
        CompanionConfig.load(configDir);

        // 2. 注册客户端原生指令：/ai <消息>
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("ai")
                .then(ClientCommandManager.argument("message", StringArgumentType.greedyString())
                    .executes(context -> {
                        String userMessage = StringArgumentType.getString(context, "message");
                        handleUserAiCommand(userMessage);
                        return 1;
                    }))
                .executes(context -> {
                    // 只输入 /ai 时给出帮助提示
                    sendToChatHud("§e【AI 伙伴用法】§f在聊天栏输入 §b/ai <你想说的话> §f即可与伙伴对话喵！");
                    return 1;
                })
            );
        });
    }

    /**
     * 处理玩家输入的 /ai 指令并异步向大模型发送请求
     */
    private static void handleUserAiCommand(String userMessage) {
        // 先在聊天栏打印玩家自己说的话
        sendToChatHud("§7[你] §f" + userMessage);
        sendToChatHud("§7§o* [小爱] 正在歪着头思考中... *§r");

        // 异步向大模型发送请求（不阻塞游戏渲染）
        LlmClient.chatSimpleAsync(userMessage).thenAccept(reply -> {
            // 解析 Markdown 颜色染色
            String formattedReply = ChatColorFormatter.formatAssistantReply(reply);
            // 🌟 核心死律：安全切回 Minecraft 主渲染线程投递消息
            sendToChatHud(formattedReply);
        });
    }

    /**
     * 辅助方法：确保在 Minecraft 主线程安全输出消息到原生聊天栏
     */
    public static void sendToChatHud(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;

        client.execute(() -> {
            if (client.inGameHud != null && client.inGameHud.getChatHud() != null) {
                client.inGameHud.getChatHud().addMessage(Text.literal(message));
            }
        });
    }
}