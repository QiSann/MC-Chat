package com.minecompanion.client;

import com.minecompanion.MineCompanionMod;
import com.minecompanion.core.config.CompanionConfig;
import com.minecompanion.core.llm.LlmClient;
import com.minecompanion.core.router.PerceptionRouter;
import com.minecompanion.core.scheduler.ProactiveScheduler;
import com.minecompanion.mc.chat.ChatColorFormatter;
import com.minecompanion.mc.modcompat.ModIntrospector;
import com.minecompanion.mc.perception.StateSnapshotter;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * MineCompanion AI 模组客户端入口类
 * 负责客户端聊天监听、免指令自然语言唤醒、H 键快捷自省、主动调度与时态感知
 */
public class MineCompanionClient implements ClientModInitializer {
    public static final Logger CLIENT_LOGGER = LoggerFactory.getLogger(MineCompanionMod.MOD_ID + "-client");
    private static File configDir;
    private static KeyBinding inspectKey;

    @Override
    public void onInitializeClient() {
        CLIENT_LOGGER.info("[{}] 客户端陪伴感知与聊天渲染层正在初始化喵！(=^･ω･^=)", MineCompanionMod.MOD_NAME);

        // 1. 初始化并加载本地配置文件 (.minecraft/config/minecompanion/)
        MinecraftClient client = MinecraftClient.getInstance();
        configDir = new File(client.runDirectory, "config/minecompanion");
        CompanionConfig.load(configDir);

        // 2. 🌟 注册 H 键快捷自省热键（在世界中按 H 键一键问 AI）
        inspectKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.minecompanion.inspect",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                "category.minecompanion"
        ));

        // 3. 🌟 客户端 Tick 循环：监听 H 键按下与主动发言调度器 (低血量报警 / 静默闲聊)
        ClientTickEvents.END_CLIENT_TICK.register(minecraftClient -> {
            while (inspectKey.wasPressed()) {
                handleQuickInspect();
            }
            // 运行主动发言调度器
            ProactiveScheduler.onClientTick(minecraftClient, MineCompanionClient::sendToChatHud);
        });

        // 4. 🌟 监听原生聊天栏输入（免 /ai 自然语言自动唤醒）
        ClientSendMessageEvents.CHAT.register((message) -> {
            reloadConfigQuietly();
            CompanionConfig config = CompanionConfig.get();
            if (config.enableChatAutoWakeup) {
                handleUserAiCommand(message, false);
            }
        });

        // 5. 注册斜杠指令集（/ai, /item, /target, /mods）
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
                    sendToChatHud("§7快捷键：按 §bH 键 §7可一键自省手中或面前的目标！");
                    sendToChatHud("§7指令：§b/item §7(手持) │ §b/target §7(注视) │ §b/mods §7(模组)");
                    return 1;
                })
            );

            // (2) 极简别名指令
            dispatcher.register(ClientCommandManager.literal("item").executes(c -> { handleInspectHandItem(); return 1; }));
            dispatcher.register(ClientCommandManager.literal("target").executes(c -> { handleInspectTarget(); return 1; }));
            dispatcher.register(ClientCommandManager.literal("mods").executes(c -> { handleListMods(); return 1; }));
        });
    }

    /**
     * 按 H 键快捷自省：手中有物品则自省手持物，空手则自省面前目标
     */
    private static void handleQuickInspect() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        ItemStack hand = client.player.getMainHandStack();
        if (!hand.isEmpty()) {
            handleInspectHandItem();
        } else {
            handleInspectTarget();
        }
    }

    /**
     * 自省手持物品并让 AI 讲解用法
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
        ProactiveScheduler.recordInteraction();
        LlmClient.chatSimpleAsync(prompt).thenAccept(reply -> {
            sendToChatHud(ChatColorFormatter.formatAssistantReply(reply));
        });
    }

    /**
     * 自省准星注视的目标
     */
    private static void handleInspectTarget() {
        String targetCard = ModIntrospector.inspectCrosshairTarget();
        sendToChatHud("§7[你] §f让小爱看一眼我正在注视的东西...");
        sendToChatHud("§7§o* [小爱] 正在聚焦视线分析目标... *§r");

        String prompt = "我正看着前面的目标，请根据以下视线焦点数据，为我介绍这是什么、属于哪个模组以及有什么注意事项：\n\n" + targetCard;
        reloadConfigQuietly();
        ProactiveScheduler.recordInteraction();
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
     * 核心处理：智能感知路由器
     */
    private static void handleUserAiCommand(String userMessage, boolean printSelfEcho) {
        MinecraftClient client = MinecraftClient.getInstance();
        ProactiveScheduler.recordInteraction();

        if (printSelfEcho) {
            sendToChatHud("§7[你] §f" + userMessage);
            sendToChatHud("§7§o* [小爱] 正在歪着头思考中... *§r");
        } else {
            // 微延时 100ms 弹出思考提示，确保排在玩家自身消息下方
            CompletableFuture.runAsync(() -> {
                sendToChatHud("§7§o* [小爱] 正在歪着头思考中... *§r");
            }, CompletableFuture.delayedExecutor(100, TimeUnit.MILLISECONDS));
        }

        // 提取手持、准星、模组列表与实时生存态势
        ItemStack hand = (client.player != null) ? client.player.getMainHandStack() : ItemStack.EMPTY;
        String handCard = ModIntrospector.inspectItemStack(hand);
        String crosshairCard = ModIntrospector.inspectCrosshairTarget();
        String modsSummary = ModIntrospector.getInstalledModsSummary();
        String stateSnapshot = StateSnapshotter.takeSnapshot();

        // 🌟 通过 PerceptionRouter 融合感知提示词
        String assembledPrompt = PerceptionRouter.assembleContextPrompt(
                userMessage, handCard, crosshairCard, modsSummary, stateSnapshot);

        reloadConfigQuietly();
        // 异步向大模型发送请求
        LlmClient.chatSimpleAsync(assembledPrompt).thenAccept(reply -> {
            String formattedReply = ChatColorFormatter.formatAssistantReply(reply);
            sendToChatHud(formattedReply);
        });
    }

    private static void reloadConfigQuietly() {
        if (configDir != null && configDir.exists()) {
            CompanionConfig.load(configDir);
        }
    }

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
