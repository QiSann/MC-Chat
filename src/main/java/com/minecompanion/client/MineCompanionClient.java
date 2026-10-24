package com.minecompanion.client;

import com.minecompanion.MineCompanionMod;
import com.minecompanion.core.config.CompanionConfig;
import com.minecompanion.core.llm.LlmClient;
import com.minecompanion.core.memory.LocalMemoryManager;
import com.minecompanion.core.memory.ReEntryGreetingEngine;
import com.minecompanion.core.router.PerceptionRouter;
import com.minecompanion.core.scheduler.ProactiveScheduler;
import com.minecompanion.core.wiki.WikiLookupService;
import com.minecompanion.mc.chat.ChatColorFormatter;
import com.minecompanion.mc.gui.CompanionConfigScreen;
import com.minecompanion.mc.modcompat.ModIntrospector;
import com.minecompanion.mc.perception.StateSnapshotter;
import com.minecompanion.mc.vision.ScreenCapturer;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.LocalTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * MineCompanion AI 模组客户端入口类
 * 负责客户端聊天监听、免指令自然语言唤醒、H 键自省、V 键截屏看图、K 键四页签配置中心、三层持久记忆与自主百科检索
 */
public class MineCompanionClient implements ClientModInitializer {
    public static final Logger CLIENT_LOGGER = LoggerFactory.getLogger(MineCompanionMod.MOD_ID + "-client");
    private static File configDir;
    private static KeyBinding inspectKey;
    private static KeyBinding visionLookKey;
    private static KeyBinding openConfigKey;

    // 缓存玩家最新坐标与维度，供退出存档瞬间安全落盘使用
    private static String lastKnownDimension = "minecraft:overworld";
    private static long lastKnownGameDay = 0;
    private static int lastKnownX = 0, lastKnownY = 64, lastKnownZ = 0;

    @Override
    public void onInitializeClient() {
        CLIENT_LOGGER.info("[{}] 客户端全栈陪伴系统（感知/记忆/百科/视觉/配置中心）正在初始化喵！(=^･ω･^=)", MineCompanionMod.MOD_NAME);

        // 1. 初始化并加载本地配置文件与百科缓存 (.minecraft/config/minecompanion/)
        MinecraftClient client = MinecraftClient.getInstance();
        configDir = new File(client.runDirectory, "config/minecompanion");
        CompanionConfig.load(configDir);
        WikiLookupService.initCache(configDir.toPath());

        // 2. 🌟 注册三大核心快捷键：H 键(自省)、V 键(看图)、K 键(配置中心)
        inspectKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.minecompanion.inspect",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                "category.minecompanion"
        ));
        visionLookKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.minecompanion.vision_look",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                "category.minecompanion"
        ));
        openConfigKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.minecompanion.open_config",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                "category.minecompanion"
        ));

        // 3. 🌟 监听玩家进入存档事件：加载存档专属三层记忆库 & 延迟 4 秒触发重逢问候
        ClientPlayConnectionEvents.JOIN.register((handler, sender, mcClient) -> {
            String worldId = resolveCurrentWorldId(mcClient);
            LocalMemoryManager.initForWorld(configDir.toPath(), worldId);
            ProactiveScheduler.recordInteraction();

            reloadConfigQuietly();
            if (CompanionConfig.get().enableReEntryGreeting) {
                CompletableFuture.runAsync(() -> {
                    mcClient.execute(MineCompanionClient::triggerReEntryGreeting);
                }, CompletableFuture.delayedExecutor(4, TimeUnit.SECONDS));
            }
        });

        // 4. 🌟 监听玩家退出存档事件：毫秒级非阻塞保存 L1 对话、L2 离线时间与 L3 情景日记
        ClientPlayConnectionEvents.DISCONNECT.register((handler, mcClient) -> {
            LocalMemoryManager memMgr = LocalMemoryManager.getCurrent();
            if (memMgr != null) {
                memMgr.onWorldDisconnect(lastKnownGameDay, lastKnownDimension, lastKnownX, lastKnownY, lastKnownZ);
            }
        });

        // 5. 🌟 客户端 Tick 循环：监听快捷键按下、更新坐标快照与运行主动发言调度器
        ClientTickEvents.END_CLIENT_TICK.register(minecraftClient -> {
            if (minecraftClient.player != null && minecraftClient.world != null) {
                lastKnownDimension = minecraftClient.world.getRegistryKey().getValue().toString();
                lastKnownGameDay = minecraftClient.world.getTimeOfDay() / 24000L;
                lastKnownX = minecraftClient.player.getBlockX();
                lastKnownY = minecraftClient.player.getBlockY();
                lastKnownZ = minecraftClient.player.getBlockZ();
            }

            while (inspectKey.wasPressed()) {
                handleQuickInspect();
            }
            while (visionLookKey.wasPressed()) {
                handleVisionLookCommand("请看看我现在的游戏画面，点评一下我眼前的景象或建筑喵！");
            }
            while (openConfigKey.wasPressed()) {
                openConfigScreen();
            }

            // 运行主动发言调度器（低血量报警 / 故地重游唤醒 / 静默闲聊）
            ProactiveScheduler.onClientTick(minecraftClient, MineCompanionClient::sendToChatHud);
        });

        // 6. 🌟 监听原生聊天栏输入（免 /ai 自然语言自动唤醒）
        ClientSendMessageEvents.CHAT.register((message) -> {
            reloadConfigQuietly();
            CompanionConfig config = CompanionConfig.get();
            if (config.enableChatAutoWakeup) {
                handleUserAiCommand(message, false);
            }
        });

        // 7. 注册斜杠指令集（/ai, /item, /target, /mods, /wiki, /remember, /memory, /look, /config）
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            // (1) /ai 聚合主指令
            dispatcher.register(ClientCommandManager.literal("ai")
                .then(ClientCommandManager.literal("item").executes(c -> { handleInspectHandItem(); return 1; }))
                .then(ClientCommandManager.literal("target").executes(c -> { handleInspectTarget(); return 1; }))
                .then(ClientCommandManager.literal("mods").executes(c -> { handleListMods(); return 1; }))
                .then(ClientCommandManager.literal("memory").executes(c -> { handleShowMemorySummary(); return 1; }))
                .then(ClientCommandManager.literal("config").executes(c -> { openConfigScreen(); return 1; }))
                .then(ClientCommandManager.literal("look")
                    .executes(c -> { handleVisionLookCommand("请看看我现在的游戏画面，点评一下我眼前的景象或建筑喵！"); return 1; })
                    .then(ClientCommandManager.argument("question", StringArgumentType.greedyString())
                        .executes(c -> {
                            handleVisionLookCommand(StringArgumentType.getString(c, "question"));
                            return 1;
                        })))
                .then(ClientCommandManager.argument("message", StringArgumentType.greedyString())
                    .executes(context -> {
                        String userMessage = StringArgumentType.getString(context, "message");
                        handleUserAiCommand(userMessage, true);
                        return 1;
                    }))
                .executes(context -> {
                    sendToChatHud("§e【AI 伙伴全栈快捷指南】§f直接在聊天栏打字即可与伙伴对话！");
                    sendToChatHud("§7快捷键：§bH 键§7(自省物品/目标) │ §bV 键§7(截屏看画面) │ §bK 键§7(打开四页签配置中心)");
                    sendToChatHud("§7快捷指令：§b/item §7│ §b/target §7│ §b/mods §7│ §b/look §7│ §b/config");
                    sendToChatHud("§7记忆与百科：§b/wiki <词条> §7│ §b/remember <地标名> §7│ §b/memory §7(查看记忆)");
                    return 1;
                })
            );

            // (2) 极简别名指令
            dispatcher.register(ClientCommandManager.literal("item").executes(c -> { handleInspectHandItem(); return 1; }));
            dispatcher.register(ClientCommandManager.literal("target").executes(c -> { handleInspectTarget(); return 1; }));
            dispatcher.register(ClientCommandManager.literal("mods").executes(c -> { handleListMods(); return 1; }));
            dispatcher.register(ClientCommandManager.literal("memory").executes(c -> { handleShowMemorySummary(); return 1; }));
            dispatcher.register(ClientCommandManager.literal("config").executes(c -> { openConfigScreen(); return 1; }));

            // (3) /look [问题] 主动截屏让 AI 看画面
            dispatcher.register(ClientCommandManager.literal("look")
                .executes(c -> { handleVisionLookCommand("请看看我现在的游戏画面，点评一下我眼前的景象或建筑喵！"); return 1; })
                .then(ClientCommandManager.argument("question", StringArgumentType.greedyString())
                    .executes(c -> {
                        handleVisionLookCommand(StringArgumentType.getString(c, "question"));
                        return 1;
                    })));

            // (4) /wiki <关键词> 主动检索外部百科
            dispatcher.register(ClientCommandManager.literal("wiki")
                .then(ClientCommandManager.argument("keyword", StringArgumentType.greedyString())
                    .executes(c -> {
                        String kw = StringArgumentType.getString(c, "keyword");
                        handleWikiSearchCommand(kw);
                        return 1;
                    })));

            // (5) /remember <地标名称> 标记当前位置为永久地标
            dispatcher.register(ClientCommandManager.literal("remember")
                .then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
                    .executes(c -> {
                        String name = StringArgumentType.getString(c, "name");
                        handleSaveLandmark(name, "通过 /remember 指令手动标记");
                        return 1;
                    })));
        });
    }

    /**
     * 打开《AI 伙伴控制中心》四页签可视化配置界面
     */
    private static void openConfigScreen() {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            reloadConfigQuietly();
            client.setScreen(new CompanionConfigScreen(client.currentScreen, configDir));
        });
    }

    /**
     * 获取当前存档唯一标识名称（实现不同存档记忆完全隔离）
     */
    private static String resolveCurrentWorldId(MinecraftClient client) {
        if (client.getServer() != null && client.getServer().getSaveProperties() != null) {
            return client.getServer().getSaveProperties().getLevelName();
        }
        if (client.getCurrentServerEntry() != null) {
            return "server_" + client.getCurrentServerEntry().address;
        }
        return "default_world";
    }

    /**
     * 触发进入存档时的阶梯式久别重逢问候
     */
    private static void triggerReEntryGreeting() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        LocalMemoryManager memMgr = LocalMemoryManager.getCurrent();
        LocalMemoryManager.CoreMemoryDoc coreMem = (memMgr != null) ? memMgr.getCoreMemory() : null;
        String snapshot = StateSnapshotter.takeSnapshot();

        String reEntryPrompt = ReEntryGreetingEngine.buildReEntryPromptInstruction(
                coreMem, snapshot, LocalTime.now());
        String memBlock = (memMgr != null)
                ? memMgr.buildMemoryContextBlock("重逢问候", client.player.getBlockX(), client.player.getBlockZ())
                : "";

        ProactiveScheduler.recordInteraction();
        LlmClient.chatWithMemoryAsync("", reEntryPrompt, memBlock).thenAccept(reply -> {
            sendToChatHud(ChatColorFormatter.formatAssistantReply(reply));
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
     * 按 V 键或输入 /look 触发 OpenGL 视觉截屏看图
     */
    private static void handleVisionLookCommand(String userQuestion) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        sendToChatHud("§7[你] §f[视觉看图] " + userQuestion);
        sendToChatHud("§7§o* [小爱] 正在截取当前画面并仔细观察中... *§r");
        ProactiveScheduler.recordInteraction();
        reloadConfigQuietly();

        String crosshairCard = ModIntrospector.inspectCrosshairTarget();
        String stateSnapshot = StateSnapshotter.takeSnapshot();
        LocalMemoryManager memMgr = LocalMemoryManager.getCurrent();
        String memBlock = (memMgr != null)
                ? memMgr.buildMemoryContextBlock(userQuestion, client.player.getBlockX(), client.player.getBlockZ())
                : "";

        String assembledPrompt = PerceptionRouter.assembleContextPrompt(
                "[看] " + userQuestion, "", crosshairCard, "", stateSnapshot, "");

        ScreenCapturer.captureScreenBase64Async().thenCompose(base64 ->
                LlmClient.chatVisionAsync(userQuestion, assembledPrompt, base64, memBlock)
        ).thenAccept(reply -> {
            sendToChatHud(ChatColorFormatter.formatAssistantReply(reply));
        });
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
     * 查看当前存档已记住的地标与约定摘要 (/memory)
     */
    private static void handleShowMemorySummary() {
        LocalMemoryManager memMgr = LocalMemoryManager.getCurrent();
        if (memMgr == null) {
            sendToChatHud("§e[记忆档案] §f当前尚未加载存档记忆库喵~");
            return;
        }
        LocalMemoryManager.CoreMemoryDoc doc = memMgr.getCoreMemory();
        sendToChatHud("§e====== 【小爱的当前存档核心记忆 (L2)】 ======");
        sendToChatHud("§7- 最近活动摘要：§f" + (doc.lastLogoutSummary.isEmpty() ? "初次探索中" : doc.lastLogoutSummary));
        sendToChatHud("§7- 共同约定事项：§f" + (doc.activePromises.isEmpty() ? "暂无约定（可在聊天中说“记住我们约定...”）" : String.join("；", doc.activePromises)));
        if (doc.landmarks.isEmpty()) {
            sendToChatHud("§7- 已标记地标：§f暂无（可输入 §b/remember 基地 §f或说“记住这里叫基地”）");
        } else {
            StringBuilder lmStr = new StringBuilder();
            for (LocalMemoryManager.Landmark lm : doc.landmarks) {
                lmStr.append(String.format("§b%s§7(X:%d,Y:%d,Z:%d) ", lm.name, lm.x, lm.y, lm.z));
            }
            sendToChatHud("§7- 已标记地标：§f" + lmStr);
        }
    }

    /**
     * 标记当前位置为永久地标
     */
    private static void handleSaveLandmark(String landmarkName, String note) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        LocalMemoryManager memMgr = LocalMemoryManager.getCurrent();
        if (memMgr != null) {
            String dim = client.world.getRegistryKey().getValue().toString();
            int x = client.player.getBlockX();
            int y = client.player.getBlockY();
            int z = client.player.getBlockZ();
            memMgr.addLandmark(landmarkName, dim, x, y, z, note);
            sendToChatHud(String.format("§a★ [记忆已镌刻] §f小爱已经把这里牢牢记作 §b【%s】§7(坐标 X:%d, Y:%d, Z:%d) §f啦喵！以后每次散步回这里我都会认出来的~", landmarkName, x, y, z));
        }
    }

    /**
     * 主动查询外部百科 (/wiki <关键词>)
     */
    private static void handleWikiSearchCommand(String keyword) {
        sendToChatHud("§7[你] §f请小爱帮我查一下百科：" + keyword);
        sendToChatHud("§7§o* [小爱] 正在翻阅 MC百科与中文 Wiki 资料库... *§r");
        ProactiveScheduler.recordInteraction();

        WikiLookupService.searchWikiAsync(keyword).thenCompose(wikiSnippet -> {
            String prompt = "主人想了解关于「" + keyword + "」的百科资料或攻略。请结合以下检索到的百科摘要，用你活泼贴心的伙伴语气为主人提炼重点讲解（控制在 110 字以内）：\n\n" + wikiSnippet;
            return LlmClient.chatSimpleAsync(prompt);
        }).thenAccept(reply -> {
            sendToChatHud(ChatColorFormatter.formatAssistantReply(reply));
        });
    }

    /**
     * 核心处理：智能感知、记忆唤醒、视觉看图与百科路由器
     */
    private static void handleUserAiCommand(String userMessage, boolean printSelfEcho) {
        MinecraftClient client = MinecraftClient.getInstance();
        ProactiveScheduler.recordInteraction();

        PerceptionRouter.IntentType intent = PerceptionRouter.classifyIntent(userMessage);

        // 若玩家在聊天栏用自然语言说“记住这里叫樱花木屋”，直接镌刻空间地标
        if (intent == PerceptionRouter.IntentType.SAVE_LANDMARK && client.player != null && client.world != null) {
            String lmName = PerceptionRouter.extractLandmarkName(userMessage);
            CompletableFuture.runAsync(() -> {
                handleSaveLandmark(lmName, userMessage);
            }, CompletableFuture.delayedExecutor(100, TimeUnit.MILLISECONDS));
            return;
        }

        // 若玩家在聊天栏用自然语言立下约定“记住我们约定明天去屠龙”
        if (intent == PerceptionRouter.IntentType.SAVE_PROMISE) {
            LocalMemoryManager memMgr = LocalMemoryManager.getCurrent();
            if (memMgr != null) {
                memMgr.addPromiseOrFact(userMessage, true);
            }
        }

        if (printSelfEcho) {
            sendToChatHud("§7[你] §f" + userMessage);
            sendToChatHud("§7§o* [小爱] 正在歪着头思考中... *§r");
        } else {
            // 微延时 100ms 弹出思考提示，确保排在玩家自身消息下方
            String thinkingTip;
            if (intent == PerceptionRouter.IntentType.WIKI_LOOKUP) {
                thinkingTip = "§7§o* [小爱] 正在快速翻阅外部百科与配方资料... *§r";
            } else if (intent == PerceptionRouter.IntentType.VISION_LOOK) {
                thinkingTip = "§7§o* [小爱] 正在截取当前画面并仔细观察中... *§r";
            } else {
                thinkingTip = "§7§o* [小爱] 正在歪着头思考中... *§r";
            }
            CompletableFuture.runAsync(() -> {
                sendToChatHud(thinkingTip);
            }, CompletableFuture.delayedExecutor(100, TimeUnit.MILLISECONDS));
        }

        // 提取手持、准星、模组列表与实时生存态势
        ItemStack hand = (client.player != null) ? client.player.getMainHandStack() : ItemStack.EMPTY;
        String handName = hand.isEmpty() ? "" : hand.getName().getString();
        String handCard = ModIntrospector.inspectItemStack(hand);
        String crosshairCard = ModIntrospector.inspectCrosshairTarget();
        String modsSummary = ModIntrospector.getInstalledModsSummary();
        String stateSnapshot = StateSnapshotter.takeSnapshot();

        // 提取当前存档的 L2 核心档案 + L3 关联情景日记
        LocalMemoryManager memMgr = LocalMemoryManager.getCurrent();
        int px = (client.player != null) ? client.player.getBlockX() : 0;
        int pz = (client.player != null) ? client.player.getBlockZ() : 0;
        String memoryContextBlock = (memMgr != null)
                ? memMgr.buildMemoryContextBlock(userMessage, px, pz)
                : "";

        reloadConfigQuietly();

        // 分支 1：触发百科查询意图
        if (intent == PerceptionRouter.IntentType.WIKI_LOOKUP) {
            String wikiKeyword = PerceptionRouter.extractWikiKeyword(userMessage, handName, "");
            WikiLookupService.searchWikiAsync(wikiKeyword).thenCompose(wikiSnippet -> {
                String assembledPrompt = PerceptionRouter.assembleContextPrompt(
                        userMessage, handCard, crosshairCard, modsSummary, stateSnapshot, wikiSnippet);
                return LlmClient.chatWithMemoryAsync(userMessage, assembledPrompt, memoryContextBlock);
            }).thenAccept(reply -> {
                sendToChatHud(ChatColorFormatter.formatAssistantReply(reply));
            });
            return;
        }

        // 分支 2：触发视觉看图意图（如“看看我建的房子好看吗”）
        if (intent == PerceptionRouter.IntentType.VISION_LOOK) {
            String assembledPrompt = PerceptionRouter.assembleContextPrompt(
                    userMessage, handCard, crosshairCard, modsSummary, stateSnapshot, "");
            ScreenCapturer.captureScreenBase64Async().thenCompose(base64 ->
                    LlmClient.chatVisionAsync(userMessage, assembledPrompt, base64, memoryContextBlock)
            ).thenAccept(reply -> {
                sendToChatHud(ChatColorFormatter.formatAssistantReply(reply));
            });
            return;
        }

        // 分支 3：常规多维态势感知对话
        String assembledPrompt = PerceptionRouter.assembleContextPrompt(
                userMessage, handCard, crosshairCard, modsSummary, stateSnapshot, "");
        LlmClient.chatWithMemoryAsync(userMessage, assembledPrompt, memoryContextBlock).thenAccept(reply -> {
            sendToChatHud(ChatColorFormatter.formatAssistantReply(reply));
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
