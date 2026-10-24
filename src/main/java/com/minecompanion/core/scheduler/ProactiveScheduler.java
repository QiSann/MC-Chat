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
 * 主动发言与战斗/生死情绪调度器 (ProactiveScheduler)
 * 实时感知玩家的生死状态、被怪物攻击受击、周围怪物群包围数量、故地重游与安静闲聊，
 * 并将所有主动发言完整记入 L1 对话上下文，展现真实的喜怒哀乐（着急、生气训人、心疼、开心）。
 */
public class ProactiveScheduler {
    private static long lastSpokenTime = System.currentTimeMillis();
    private static long lastLowHealthAlertTime = 0;
    private static long lastAttackedAlertTime = 0;
    private static long lastSwarmAlertTime = 0;
    private static int tickCounter = 0;

    // 追踪玩家上一帧生命值与死亡状态，用于精准捕捉“刚死掉”、“刚复活”、“刚挨揍掉血”
    private static boolean wasPlayerDead = false;
    private static float lastRecordedHealth = 20.0f;
    private static int consecutiveHurtCount = 0;

    /**
     * 在客户端 Tick 循环中调度（每 5 tick = 0.25 秒高频检测生死与受击，绝不漏掉挨打与死亡瞬间）
     */
    public static void onClientTick(MinecraftClient client, Consumer<String> chatOutput) {
        tickCounter++;
        if (tickCounter % 5 != 0) return;

        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) return;

        CompanionConfig config = CompanionConfig.get();
        if (!config.enableProactiveSpeech) return;

        long now = System.currentTimeMillis();
        float currentHealth = player.getHealth();
        boolean isCurrentlyDead = player.isDead() || currentHealth <= 0.0f;

        LocalMemoryManager memMgr = LocalMemoryManager.getCurrent();
        String snapshot = StateSnapshotter.takeSnapshot();
        String memBlock = (memMgr != null)
                ? memMgr.buildMemoryContextBlock("实时状态", player.getBlockX(), player.getBlockZ())
                : "";
        String fullSystemAddons = snapshot + "\n" + memBlock;

        // ==================== 1. 💀 P0 最高优事件：角色死亡瞬间识别 ====================
        if (isCurrentlyDead && !wasPlayerDead) {
            wasPlayerDead = true;
            lastRecordedHealth = 0.0f;
            lastSpokenTime = now;

            int dx = player.getBlockX();
            int dy = player.getBlockY();
            int dz = player.getBlockZ();
            String dim = client.world.getRegistryKey().getValue().toString();
            String killerName = StateSnapshotter.findNearestAliveAttackerName(player, client.world);
            String deathReason = killerName.isEmpty() ? "意外身亡" : "被【" + killerName + "】击杀";

            if (memMgr != null) {
                memMgr.recordDeathEvent(client.world.getTimeOfDay() / 24000L, dim, dx, dy, dz, deathReason);
            }

            String eventLabel = "[突发事件：主人刚刚阵亡了！(" + deathReason + "，死亡坐标 X:" + dx + " Y:" + dy + " Z:" + dz + ")]";
            String prompt = eventLabel + "\n请表现出极具活人感的真实情绪（可以先又气又急地数落主人刚才太莽撞不听劝，或者心疼惊呼），并把死亡坐标 (X:"
                    + dx + ", Y:" + dy + ", Z:" + dz + ") 告诉主人方便复活后去捡装备！口语化自然表达，不要像系统播报！";

            LlmClient.chatWithMemoryAsync(eventLabel, prompt, fullSystemAddons).thenAccept(reply -> {
                chatOutput.accept(ChatColorFormatter.formatAssistantReply(reply));
            });
            return;
        }

        // 若玩家从死亡界面点击了重生，重置状态
        if (!isCurrentlyDead && wasPlayerDead) {
            wasPlayerDead = false;
            lastRecordedHealth = currentHealth;
            consecutiveHurtCount = 0;
            return;
        }

        if (isCurrentlyDead) return;

        // ==================== 2. 🚨 P0 残血濒死危机 (<= 6.0 HP) ====================
        if (config.enableLowHealthWarning && currentHealth <= 6.0f) {
            if (now - lastLowHealthAlertTime > 25_000) {
                lastLowHealthAlertTime = now;
                lastAttackedAlertTime = now;
                lastSpokenTime = now;

                String attacker = StateSnapshotter.findNearestAliveAttackerName(player, client.world);
                String eventLabel = "[紧急事件：主人只剩 " + String.format("%.1f", currentHealth) + " 滴血残血濒死了！"
                        + (attacker.isEmpty() ? "" : "旁边还有活着的" + attacker) + "]";
                String prompt = eventLabel + "\n主人快要死了！请立刻带着又急又气或万分焦急的真人情绪大喊提醒！"
                        + "【铁律】：请仔细看系统信息里的【背包真实物资】，主人背包里有什么食物就催他吃什么；如果写着【完全没有】，就严厉催他赶紧跑路或垫方块自保，绝对不准提他身上没有的金苹果！";

                LlmClient.chatWithMemoryAsync(eventLabel, prompt, fullSystemAddons).thenAccept(reply -> {
                    chatOutput.accept(ChatColorFormatter.formatAssistantReply(reply));
                });
                lastRecordedHealth = currentHealth;
                return;
            }
        }

        // ==================== 3. ⚔️ P1 被怪物攻击 / 突然掉血感知 ====================
        float healthDrop = lastRecordedHealth - currentHealth;
        lastRecordedHealth = currentHealth;

        if (healthDrop >= 1.0f || player.hurtTime >= 8) {
            consecutiveHurtCount++;
            String attackerName = StateSnapshotter.findNearestAliveAttackerName(player, client.world);
            int aliveHostiles = StateSnapshotter.countNearbyAliveHostiles(player, client.world);

            // 防抖：普通受击提醒间隔 18 秒，避免每砍一刀刷屏
            if (now - lastAttackedAlertTime > 18_000) {
                lastAttackedAlertTime = now;
                lastSpokenTime = now;

                String eventLabel;
                String prompt;
                if (!attackerName.isEmpty()) {
                    eventLabel = "[战斗事件：主人正在被【" + attackerName + "】攻击掉血！(周围共有 " + aliveHostiles + " 只活着的怪物)]";
                    if (consecutiveHurtCount >= 3) {
                        prompt = eventLabel + "\n主人已经连续挨了好几下揍了！请表现出真人生气、着急或恨铁不成钢的语气（比如吐槽他走位、催他举盾反击或拉开距离），如果周围怪物多就让他别硬刚！";
                    } else {
                        prompt = eventLabel + "\n主人刚被【" + attackerName + "】偷袭/打中了！像一起打游戏的真朋友一样惊呼或提醒他反击/小心！";
                    }
                } else {
                    eventLabel = "[受伤事件：主人突然掉了 " + String.format("%.1f", healthDrop) + " 滴血（摔伤/烫伤/中毒等）]";
                    prompt = eventLabel + "\n主人突然掉血受伤了（周围没有近身怪物，可能是摔了或碰到危险），自然地关心或吐槽他一句！";
                }

                LlmClient.chatWithMemoryAsync(eventLabel, prompt, fullSystemAddons).thenAccept(reply -> {
                    chatOutput.accept(ChatColorFormatter.formatAssistantReply(reply));
                });
                return;
            }
        }

        // 以下非紧急事件每秒（20 ticks）评估一次即可
        if (tickCounter % 20 != 0) return;

        // ==================== 4. 🧟 P1 周围怪物过多 / 被尸潮包围预警 (>= 4 只活怪) ====================
        int aliveHostiles = StateSnapshotter.countNearbyAliveHostiles(player, client.world);
        if (aliveHostiles >= 4 && (now - lastSwarmAlertTime) > 35_000 && (now - lastSpokenTime) > 12_000) {
            lastSwarmAlertTime = now;
            lastSpokenTime = now;

            String eventLabel = "[高危态势：周围足足聚集了 " + aliveHostiles + " 只活着的怪物把主人包围了！]";
            String prompt = eventLabel + "\n周围活着的怪物太多了（足足 " + aliveHostiles + " 只）！请表现出紧张、倒吸凉气或急切警告的情绪，劝主人千万别恋战被群殴，快找高处或撤退！";

            LlmClient.chatWithMemoryAsync(eventLabel, prompt, fullSystemAddons).thenAccept(reply -> {
                chatOutput.accept(ChatColorFormatter.formatAssistantReply(reply));
            });
            return;
        }

        // ==================== 5. 🏡 P1 空间记忆事件：故地重游地标唤醒 ====================
        long cooldownMs = (long) config.globalCooldownSeconds * 1_000L;
        if (memMgr != null && (now - lastSpokenTime) > cooldownMs) {
            String dim = client.world.getRegistryKey().getValue().toString();
            int px = player.getBlockX();
            int py = player.getBlockY();
            int pz = player.getBlockZ();

            LocalMemoryManager.Landmark nearbyLandmark = memMgr.checkNearbyLandmarkForAwakening(dim, px, py, pz);
            if (nearbyLandmark != null) {
                lastSpokenTime = now;
                String eventLabel = "[空间记忆触发：和主人一起走回了重要地点【" + nearbyLandmark.name + "】]";
                String prompt = eventLabel + "\n你们刚刚走回了之前标记的【" + nearbyLandmark.name + "】。请带着开心、放松或温馨的情绪随口感慨一句回家/回到老地方真好！";

                LlmClient.chatWithMemoryAsync(eventLabel, prompt, fullSystemAddons).thenAccept(reply -> {
                    chatOutput.accept(ChatColorFormatter.formatAssistantReply(reply));
                });
                return;
            }
        }

        // ==================== 6. 💬 P2 静默闲聊触发器 (Idle Chat Timer) ====================
        long idleThresholdMs = (config.idleChatSeconds > 0 ? (long) config.idleChatSeconds : 90L) * 1_000L;
        if ((now - lastSpokenTime) > idleThresholdMs && (now - lastSpokenTime) > cooldownMs) {
            if (client.currentScreen != null) return;

            lastSpokenTime = now;
            String eventLabel = "[日常安静陪伴：主人专注玩了一会儿没说话，你主动找他搭话]";
            String prompt = eventLabel + "\n主人安静玩了一会儿了。请顺着你们刚才聊过的【历史对话上下文】、或者结合现在的天色/周围情况/手头的事，像真朋友一样随性自然地跟主人搭句话（可以开心分享想法、小声抱怨无聊、或者调侃他正在干嘛）。千万别说死板的套话！";

            LlmClient.chatWithMemoryAsync(eventLabel, prompt, fullSystemAddons).thenAccept(reply -> {
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
