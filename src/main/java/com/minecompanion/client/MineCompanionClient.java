package com.minecompanion.client;

import com.minecompanion.MineCompanionMod;
import com.minecompanion.core.config.CompanionConfig;
import com.minecompanion.core.llm.LlmClient;
import com.minecompanion.core.router.PerceptionRouter;
import com.minecompanion.mc.chat.ChatColorFormatter;
import com.minecompanion.mc.modcompat.ModIntrospector;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * MineCompanion AI 模组客户端入口类
 * 负责客户端聊天监听、免指令自然语言唤醒、意图路由与异步消息渲染
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

        // 2. 🌟 监听原生聊天栏输入（免 /ai 自然语言自动唤醒，支持开关）
        ClientSendMessageEvents.CHAT.register((message) -> {
            reloadConfigQuietly();
            CompanionConfig config = CompanionConfig.get();
            if (config.enableChatAutoWakeup) {
                // 原生聊天已被游戏发到聊天栏，此处不重复打印 [你]，直接唤醒 AI
                handleUserAiCommand(message, false);
            }
        });

        // 3. 注册斜杠指令集（提供 /ai 以及超短的 /item, /target, /mods 简短快捷指令）
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            // (1) /ai <消息>
            dispatcher.register(ClientCommandManager.literal("ai")
                .then(ClientCommandManager.literal("item").executes(c -> { handleInspectHandItem(); return 1; }))
                .then(ClientCommandManager.literal("target").executes(c -> { handleInspectTarget(); return 1; }))
                .then(ClientCommandManager.literal("mods").executes(c -> { handleListMods(); return 1; }))
                .then(ClientCommandManager.argument("message", StringArgumentType.greedyString())
                    .executes(context -> {
                        String userMessage = StringArgumentType.getString(context, "message");
                        handleUserAiCommand(userMessage, true);
                        return 1;
                    }))
                .executes(context -> {
                    sendToChatHud("§e【AI 伙伴快捷用法】§f直接在聊天栏打字即可与伙伴聊天！");
                    sendToChatHud("§7也可使用极简指令：§b/item §7(手持) │ §b/target §7(注视) │ §b/mods §7(模组)");
                    return 1;
                })
            );

            // (2) 极简独立别名指令：/item
            dispatcher.register(ClientCommandManager.literal("item").executes(c -> { handleInspectHandItem(); return 1; }));

            // (3) 极简独立别名指令：/target
            dispatcher.register(ClientCommandManager.literal("target").executes(c -> { handleInspectTarget(); return 1; }));

            // (4) 极简独立别名指令：/mods
            dispatcher.register(ClientCommandManager.literal("mods").executes(c -> { handleListMods(); return 1; }));
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
     * 一键列出模组清单
     */
    private static void handleListMods() {
        sendToChatHud("§b[小爱] §f" + ModIntrospector.getInstalledModsSummary());
    }

    /**
     * 核心处理：智能感知路由器（支持自然语言意图识别 + 显式 /ai 指令）
     *
     * @param printSelfEcho 是否需要在聊天框回显玩家自己的话（通过 /ai 调用时为 true；普通聊天栏直接发出的为 false）
     */
    private static void handleUserAiCommand(String userMessage, boolean printSelfEcho) {
        MinecraftClient client = MinecraftClient.getInstance();

        if (printSelfEcho) {
            sendToChatHud("§7[你] §f" + userMessage);
        }
        sendToChatHud("§7§o* [小爱] 正在歪着头思考中... *§r");

        // 提取手持与准星数据
        ItemStack hand = (client.player != null) ? client.player.getMainHandStack() : ItemStack.EMPTY;
        String handCard = ModIntrospector.inspectItemStack(hand);
        String crosshairCard = ModIntrospector.inspectCrosshairTarget();
        String modsSummary = ModIntrospector.getInstalledModsSummary();

        // 🌟 通过 PerceptionRouter 智能路由器，根据玩家说的自然语言自动组装真实游戏数据
        String assembledPrompt = PerceptionRouter.assembleContextPrompt(userMessage, handCard, crosshairCard, modsSummary);

        reloadConfigQuietly();
        // 异步向大模型发送请求（不阻塞游戏渲染）
        LlmClient.chatSimpleAsync(assembledPrompt).thenAccept(reply -> {
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
