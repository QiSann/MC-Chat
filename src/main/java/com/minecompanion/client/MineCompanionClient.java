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

    private static String lastKnownDimension = "minecraft:overworld";
    private static long lastKnownGameDay = 0;
    private static int lastKnownX = 0, lastKnownY = 64, lastKnownZ = 0;

    @Override
    public void onInitializeClient() {
        CLIENT_LOGGER.info("[{}] 客户端全栈陪伴系统正在初始化喵！(=^･ω･^=)", MineCompanionMod.MOD_NAME);

        // 1. 初始化并加载本地配置文件、默认记忆目录与百科缓存
        MinecraftClient client = MinecraftClient.getInstance();
        configDir = new File(client.runDirectory, "config/minecompanion");
        CompanionConfig.load(configDir);
        LocalMemoryManager.setDefaultBaseConfigDir(configDir.toPath());
        WikiLookupService.initCache(configDir.toPath());

        // 2. 注册三大核心快捷键：H 键(自省)、V 键(看图)、K 键(配置中心)
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

        // 3. 监听玩家进入存档事件：加载存档专属三层记忆库 & 延迟 4 秒触发重逢问候
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

        // 4. 监听玩家退出存档事件：非阻塞保存 L1 对话、L2 离线时间与 L3 情景日记
        ClientPlayConnectionEvents.DISCONNECT.register((handler, mcClient) -> {
            LocalMemoryManager memMgr = LocalMemoryManager.getCurrent();
            if (memMgr != null) {
                memMgr.onWorldDisconnect(lastKnownGameDay, lastKnownDimension, lastKnownX, lastKnownY, lastKnownZ);
            }
        });

        // 5. 客户端 Tick 循环：监听快捷键按下、更新坐标快照与运行主动发言调度器
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
                handleVisionLookCommand("快看看我现在眼前的画面，你觉得怎么样？");
            }
            while (openConfigKey.wasPressed()) {
                openConfigScreen();
            }

            ProactiveScheduler.onClientTick(minecraftClient, MineCompanionClient::sendToChatHud);
        });

        // 6. 监听原生聊天栏输入（免 /ai 自然语言自动唤醒）
        ClientSendMessageEvents.CHAT.register((message) -> {
            reloadConfigQuietly();
            CompanionConfig config = CompanionConfig.get();
            if (config.enableChatAutoWakeup) {
                handleUserAiCommand(message, false);
            }
        });

        // 7. 注册斜杠指令集
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("ai")
                .then(ClientCommandManager.literal("item").executes(c -> { handleInspectHandItem(); return 1; }))
                .then(ClientCommandManager.literal("target").executes(c -> { handleInspectTarget(); return 1; }))
                .then(ClientCommandManager.literal("mods").executes(c -> { handleListMods(); return 1; }))
                .then(ClientCommandManager.literal("memory").executes(c -> { handleShowMemorySummary(); return 1; }))
                .then(ClientCommandManager.literal("config").executes(c -> { openConfigScreen(); return 1; }))
                .then(ClientCommandManager.literal("look")
                    .executes(c -> { handleVisionLookCommand("快看看我现在眼前的画面，你觉得怎么样？"); return 1; })
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

            dispatcher.register(ClientCommandManager.literal("item").executes(c -> { handleInspectHandItem(); return 1; }));
            dispatcher.register(ClientCommandManager.literal("target").executes(c -> { handleInspectTarget(); return 1; }));
            dispatcher.register(ClientCommandManager.literal("mods").executes(c -> { handleListMods(); return 1; }));
            dispatcher.register(ClientCommandManager.literal("memory").executes(c -> { handleShowMemorySummary(); return 1; }));
            dispatcher.register(ClientCommandManager.literal("config").executes(c -> { openConfigScreen(); return 1; }));

            dispatcher.register(ClientCommandManager.literal("look")
                .executes(c -> { handleVisionLookCommand("快看看我现在眼前的画面，你觉得怎么样？"); return 1; })
                .then(ClientCommandManager.argument("question", StringArgumentType.greedyString())
                    .executes(c -> {
                        handleVisionLookCommand(StringArgumentType.getString(c, "question"));
                        return 1;
                    })));

            dispatcher.register(ClientCommandManager.literal("wiki")
                .then(ClientCommandManager.argument("keyword", StringArgumentType.greedyString())
                    .executes(c -> {
                        String kw = StringArgumentType.getString(c, "keyword");
                        handleWikiSearchCommand(kw);
                        return 1;
                    })));

            dispatcher.register(ClientCommandManager.literal("remember")
                .then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
                    .executes(c -> {
                        String name = StringArgumentType.getString(c, "name");
                        handleSaveLandmark(name, "通过 /remember 指令手动标记");
                        return 1;
                    })));
        });
    }

    private static void openConfigScreen() {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            reloadConfigQuietly();
            client.setScreen(new CompanionConfigScreen(client.currentScreen, configDir));
        });
    }

    private static String resolveCurrentWorldId(MinecraftClient client) {
        if (client.getServer() != null && client.getServer().getSaveProperties() != null) {
            return client.getServer().getSaveProperties().getLevelName();
        }
        if (client.getCurrentServerEntry() != null) {
            return "server_" + client.getCurrentServerEntry().address;
        }
        return "default_world";
    }

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
        LlmClient.chatWithMemoryAsync("[事件：主人刚刚上线进入存档]", reEntryPrompt, snapshot + "\n" + memBlock).thenAccept(reply -> {
            sendToChatHud(ChatColorFormatter.formatAssistantReply(reply));
        });
    }

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

        String sysAddons = PerceptionRouter.assembleDynamicSystemAddons(
                PerceptionRouter.IntentType.VISION_LOOK, "", crosshairCard, "", stateSnapshot, "", memBlock);

        ScreenCapturer.captureScreenBase64Async().thenCompose(base64 ->
                LlmClient.chatVisionAsync(userQuestion, userQuestion, base64, sysAddons)
        ).thenAccept(reply -> {
            sendToChatHud(ChatColorFormatter.formatAssistantReply(reply));
        });
    }

    private static void handleInspectHandItem() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        ItemStack hand = client.player.getMainHandStack();
        String itemCard = ModIntrospector.inspectItemStack(hand);
        String snapshot = StateSnapshotter.takeSnapshot();
        sendToChatHud("§7[你] §f哎，你看我手里拿的这个是什么？怎么用呀？");
        sendToChatHud("§7§o* [小爱] 正在凑过来瞧你手里的东西... *§r");

        reloadConfigQuietly();
        ProactiveScheduler.recordInteraction();
        String userQ = "你看我手里正拿着的这个东西是什么？怎么合或者有什么用呀？（像平时聊天一样自然告诉我，别背说明书格式）";
        LlmClient.chatWithMemoryAsync("我手里拿的这个是什么？", userQ, snapshot + "\n" + itemCard).thenAccept(reply -> {
            sendToChatHud(ChatColorFormatter.formatAssistantReply(reply));
        });
    }

    private static void handleInspectTarget() {
        String targetCard = ModIntrospector.inspectCrosshairTarget();
        String snapshot = StateSnapshotter.takeSnapshot();
        sendToChatHud("§7[你] §f你看我前面正盯着的是什么呀？");
        sendToChatHud("§7§o* [小爱] 正在顺着你的目光往前看... *§r");

        reloadConfigQuietly();
        ProactiveScheduler.recordInteraction();
        String userQ = "你看我面前正盯着的目标是什么呀？（像真朋友聊天一样自然告诉我，别列死板格式）";
        LlmClient.chatWithMemoryAsync("你看我面前盯着的是什么？", userQ, snapshot + "\n" + targetCard).thenAccept(reply -> {
            sendToChatHud(ChatColorFormatter.formatAssistantReply(reply));
        });
    }

    private static void handleListMods() {
        sendToChatHud("§b[小爱] §f" + ModIntrospector.getInstalledModsSummary());
    }

    private static void handleShowMemorySummary() {
        LocalMemoryManager memMgr = LocalMemoryManager.getCurrent();
        if (memMgr == null) {
            sendToChatHud("§e[记忆档案] §f当前尚未加载存档记忆库喵~");
            return;
        }
        LocalMemoryManager.CoreMemoryDoc doc = memMgr.getCoreMemory();
        sendToChatHud("§e====== 【小爱的当前存档核心记忆 (L2)】 ======");
        sendToChatHud("§7- 最近互动记录：§f" + (doc.lastLogoutSummary.isEmpty() ? "初次探索中" : doc.lastLogoutSummary));
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

    private static void handleWikiSearchCommand(String keyword) {
        sendToChatHud("§7[你] §f请小爱帮我查一下百科：" + keyword);
        sendToChatHud("§7§o* [小爱] 正在翻阅 MC百科与中文 Wiki 资料库... *§r");
        ProactiveScheduler.recordInteraction();

        String snapshot = StateSnapshotter.takeSnapshot();
        WikiLookupService.searchWikiAsync(keyword).thenCompose(wikiSnippet -> {
            String userQ = "我想了解关于「" + keyword + "」的玩法或攻略，你刚查完百科，快用你平时的口语重点跟我讲讲吧！";
            return LlmClient.chatWithMemoryAsync("帮我查一下：" + keyword, userQ, snapshot + "\n" + wikiSnippet);
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

        if (intent == PerceptionRouter.IntentType.SAVE_LANDMARK && client.player != null && client.world != null) {
            String lmName = PerceptionRouter.extractLandmarkName(userMessage);
            CompletableFuture.runAsync(() -> {
                handleSaveLandmark(lmName, userMessage);
            }, CompletableFuture.delayedExecutor(100, TimeUnit.MILLISECONDS));
            return;
        }

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

        ItemStack hand = (client.player != null) ? client.player.getMainHandStack() : ItemStack.EMPTY;
        String handName = hand.isEmpty() ? "" : hand.getName().getString();
        String handCard = ModIntrospector.inspectItemStack(hand);
        String crosshairCard = ModIntrospector.inspectCrosshairTarget();
        String modsSummary = ModIntrospector.getInstalledModsSummary();
        String stateSnapshot = StateSnapshotter.takeSnapshot();

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
                String sysAddons = PerceptionRouter.assembleDynamicSystemAddons(
                        intent, handCard, crosshairCard, modsSummary, stateSnapshot, wikiSnippet, memoryContextBlock);
                return LlmClient.chatWithMemoryAsync(userMessage, userMessage, sysAddons);
            }).thenAccept(reply -> {
                sendToChatHud(ChatColorFormatter.formatAssistantReply(reply));
            });
            return;
        }

        // 分支 2：触发视觉看图意图
        if (intent == PerceptionRouter.IntentType.VISION_LOOK) {
            String sysAddons = PerceptionRouter.assembleDynamicSystemAddons(
                    intent, handCard, crosshairCard, modsSummary, stateSnapshot, "", memoryContextBlock);
            ScreenCapturer.captureScreenBase64Async().thenCompose(base64 ->
                    LlmClient.chatVisionAsync(userMessage, userMessage, base64, sysAddons)
            ).thenAccept(reply -> {
                sendToChatHud(ChatColorFormatter.formatAssistantReply(reply));
            });
            return;
        }

        // 分支 3：常规对话与自省意图（User 消息保持纯粹原话，环境与背包真实状态注入 System）
        String sysAddons = PerceptionRouter.assembleDynamicSystemAddons(
                intent, handCard, crosshairCard, modsSummary, stateSnapshot, "", memoryContextBlock);
        LlmClient.chatWithMemoryAsync(userMessage, userMessage, sysAddons).thenAccept(reply -> {
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
