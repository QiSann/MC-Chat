package com.minecompanion.core.scheduler;

import com.minecompanion.core.config.CompanionConfig;
import com.minecompanion.core.llm.LlmClient;
import com.minecompanion.core.memory.LocalMemoryManager;
import com.minecompanion.mc.chat.ChatColorFormatter;
import com.minecompanion.mc.perception.StateSnapshotter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

import java.util.function.Consumer;

/**
 * 主动发言调度器 (ProactiveScheduler)
 * 维护全局冷却时间 (Cooldown)、防抖机制、空间地标故地重游检测以及静默闲聊计时器，
 * 当检测到 P0 濒死/低血量事件、P1 故地重游、或 P2 玩家长时间挂机无交互时，驱动 AI 结合环境主动开口搭话！
 */
public class ProactiveScheduler {
    private static long lastSpokenTime = System.currentTimeMillis();
    private static long lastLowHealthAlertTime = 0;
    private static int tickCounter = 0;

    /**
     * 在客户端 Tick 循环中调度（每秒执行一次检查）
     */
    public static void onClientTick(MinecraftClient client, Consumer<String> chatOutput) {
        tickCounter++;
        // 每 20 tick (约 1 秒) 评估一次
        if (tickCounter % 20 != 0) return;

        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) return;

        CompanionConfig config = CompanionConfig.get();
        if (!config.enableProactiveSpeech) return;

        long now = System.currentTimeMillis();
        long cooldownMs = (long) config.globalCooldownSeconds * 1_000L;

        // ==================== 1. P0 紧急高光事件：低血量/濒死提醒 ====================
        if (config.enableLowHealthWarning && player.getHealth() <= 6.0f && !player.isDead()) {
            // 防抖：同类低血量提醒至少间隔 35 秒，防止频繁刷屏
            if (now - lastLowHealthAlertTime > 35_000) {
                lastLowHealthAlertTime = now;
                lastSpokenTime = now;

                String snapshot = StateSnapshotter.takeSnapshot();
                String prompt = "【系统突发紧急事件：玩家生命值危急！】\n" + snapshot
                        + "\n请立即以符合你性格的焦急、关切口吻，惊呼提醒主人注意安全，催促主人快吃食物回血或撤退！控制在 35 字以内！";

                LlmClient.chatSimpleAsync(prompt).thenAccept(reply -> {
                    chatOutput.accept(ChatColorFormatter.formatAssistantReply(reply));
                });
                return;
            }
        }

        // ==================== 2. P1 空间记忆事件：故地重游地标唤醒 ====================
        LocalMemoryManager memMgr = LocalMemoryManager.getCurrent();
        if (memMgr != null && (now - lastSpokenTime) > cooldownMs) {
            String dim = client.world.getRegistryKey().getValue().toString();
            int px = player.getBlockX();
            int py = player.getBlockY();
            int pz = player.getBlockZ();

            LocalMemoryManager.Landmark nearbyLandmark = memMgr.checkNearbyLandmarkForAwakening(dim, px, py, pz);
            if (nearbyLandmark != null) {
                lastSpokenTime = now;
                String snapshot = StateSnapshotter.takeSnapshot();
                String prompt = "【系统空间记忆触发：故地重游】\n玩家刚刚走回了你们之前共同标记的重要地点：【"
                        + nearbyLandmark.name + "】（备注：" + nearbyLandmark.description + "）。\n"
                        + snapshot
                        + "\n请带着温馨或兴奋的语气，主动感叹回到这里的感觉（例如“终于回到咱们的" + nearbyLandmark.name + "啦”），控制在 45 字以内！";

                LlmClient.chatSimpleAsync(prompt).thenAccept(reply -> {
                    chatOutput.accept(ChatColorFormatter.formatAssistantReply(reply));
                });
                return;
            }
        }

        // ==================== 3. P2 静默闲聊触发器 (Idle Chat Timer) ====================
        long idleThresholdMs = (config.idleChatSeconds > 0 ? (long) config.idleChatSeconds : 90L) * 1_000L;

        // 如果距离上次说话已经超过设定的静默时间，且过了最小全局冷却
        if ((now - lastSpokenTime) > idleThresholdMs && (now - lastSpokenTime) > cooldownMs) {
            // 若玩家当前正在打开箱子/背包，为了不打扰玩家操作，暂缓触发
            if (client.currentScreen != null) return;

            lastSpokenTime = now;
            String snapshot = StateSnapshotter.takeSnapshot();
            String memBlock = (memMgr != null)
                    ? memMgr.buildMemoryContextBlock("闲聊", player.getBlockX(), player.getBlockZ())
                    : "";
            String prompt = "【系统环境触发：长时间安静闲聊】\n玩家已经专注生存有一会儿没有说话了。请结合当前真实环境态势与长期记忆：\n"
                    + snapshot
                    + "\n以活泼贴心的同游伙伴身份，自然地找个话题跟主人搭话（可以吐槽天色、提醒怪物、或者提起你们的约定与地标）。控制在 50 字以内！";

            LlmClient.chatWithMemoryAsync("", prompt, memBlock).thenAccept(reply -> {
                chatOutput.accept(ChatColorFormatter.formatAssistantReply(reply));
            });
        }
    }

    /**
     * 当 AI 说话或玩家交互时重置发言时间戳
     */
    public static void recordInteraction() {
        lastSpokenTime = System.currentTimeMillis();
    }
}
