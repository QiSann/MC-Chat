package com.minecompanion.client;

import com.minecompanion.MineCompanionMod;
import com.minecompanion.core.config.CompanionConfig;
import com.minecompanion.core.llm.LlmClient;
import com.minecompanion.mc.chat.ChatColorFormatter;
import com.minecompanion.mc.modcompat.ModIntrospector;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * MineCompanion AI 模组客户端入口类
 * 负责注册客户端指令 /ai、整合跨模组物品全息自省、并异步安全推送到 ChatHud
 */
public class MineCompanionClient implements ClientModInitializer {
    public static final Logger CLIENT_LOGGER = LoggerFactory.getLogger(MineCompanionMod.MOD_ID + "-client");
    private static File configDir;

    @Override
    public void onInitializeClient() {
        CLIENT_LOGGER.info("[{}] 客户端陪伴感知与聊天渲染层正在初始化喵！(=^･ω･^=)", MineCompanionMod.MOD_NAME);

        // 1. 初始化并加载本地配置文件 (.minecraft/config/minecompanion/)
        MinecraftClient client = MinecraftClient.getInstance();
        configDir = new File(client.runDirectory, "config/minecompanion");
        CompanionConfig.load(configDir);

        // 2. 注册客户端原生指令：/ai <消息>
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("ai")
                .then(ClientCommandManager.literal("item")
                    .executes(context -> {
                        handleInspectHandItem();
                        return 1;
                    }))
                .then(ClientCommandManager.literal("target")
                    .executes(context -> {
                        handleInspectTarget();
                        return 1;
                    }))
                .then(ClientCommandManager.literal("mods")
                    .executes(context -> {
                        sendToChatHud("§b[小爱] §f" + ModIntrospector.getInstalledModsSummary());
                        return 1;
                    }))
                .then(ClientCommandManager.argument("message", StringArgumentType.greedyString())
                    .executes(context -> {
                        String userMessage = StringArgumentType.getString(context, "message");
                        handleUserAiCommand(userMessage);
                        return 1;
                    }))
                .executes(context -> {
                    sendToChatHud("§e【AI 伙伴用法】§f在聊天栏输入：");
                    sendToChatHud("§b/ai <话语> §7- 与伙伴自由交谈");
                    sendToChatHud("§b/ai item   §7- 深度自省手持物品与合成表");
                    sendToChatHud("§b/ai target §7- 深度自省准星所指方块或生物");
                    sendToChatHud("§b/ai mods   §7- 查看当前整合包已装功能模组清单");
                    return 1;
                })
            );
        });
    }

    /**
     * 一键自省手持物品并让 AI 讲解用法
     */
    private static void handleInspectHandItem() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        ItemStack hand = client.player.getMainHandStack();
        String itemCard = ModIntrospector.inspectItemStack(hand);
        sendToChatHud("§7[你] §f让小爱看一眼我手里的东西...");
        sendToChatHud("§7§o* [小爱] 正在翻看游戏注册表与配方库... *§r");

        String prompt = "我手里正拿着这件物品，请根据以下游戏内自省名片，以你的活泼伙伴口吻为我简要介绍它的所属模组、用途和合成关键：\n\n" + itemCard;
        reloadConfigQuietly();
        LlmClient.chatSimpleAsync(prompt).thenAccept(reply -> {
            sendToChatHud(ChatColorFormatter.formatAssistantReply(reply));
        });
    }

    /**
     * 一键自省准星注视的目标
     */
    private static void handleInspectTarget() {
        String targetCard = ModIntrospector.inspectCrosshairTarget();
        sendToChatHud("§7[你] §f让小爱看一眼我正在注视的东西...");
        sendToChatHud("§7§o* [小爱] 正在聚焦视线分析目标... *§r");

        String prompt = "我正看着前面的目标，请根据以下视线焦点数据，为我介绍这是什么、属于哪个模组以及有什么注意事项：\n\n" + targetCard;
        reloadConfigQuietly();
        LlmClient.chatSimpleAsync(prompt).thenAccept(reply -> {
            sendToChatHud(ChatColorFormatter.formatAssistantReply(reply));
        });
    }

    /**
     * 处理玩家输入的普通 /ai 指令（自动附带手持物品与准星感知上下文）
     */
    private static void handleUserAiCommand(String userMessage) {
        MinecraftClient client = MinecraftClient.getInstance();

        // 先在聊天栏打印玩家自己说的话
        sendToChatHud("§7[你] §f" + userMessage);
        sendToChatHud("§7§o* [小爱] 正在歪着头思考中... *§r");

        // 自动提取当前手持与视线焦点注入上下文
        StringBuilder contextBuilder = new StringBuilder(userMessage);
        if (client.player != null) {
            ItemStack hand = client.player.getMainHandStack();
            if (!hand.isEmpty()) {
                contextBuilder.append("\n\n【玩家当前感知环境】\n");
                contextBuilder.append(ModIntrospector.inspectItemStack(hand)).append("\n");
                contextBuilder.append(ModIntrospector.inspectCrosshairTarget());
            }
        }

        reloadConfigQuietly();
        // 异步向大模型发送请求（不阻塞游戏渲染）
        LlmClient.chatSimpleAsync(contextBuilder.toString()).thenAccept(reply -> {
            String formattedReply = ChatColorFormatter.formatAssistantReply(reply);
            sendToChatHud(formattedReply);
        });
    }

    /**
     * 每次提问时静默热重载 config.json，玩家修改配置无需重启游戏
     */
    private static void reloadConfigQuietly() {
        if (configDir != null && configDir.exists()) {
            CompanionConfig.load(configDir);
        }
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
