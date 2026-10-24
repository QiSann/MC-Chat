package com.minecompanion.core.memory;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;

/**
 * 重开游戏“重逢问候与久别抱怨”构建引擎 (ReEntryGreetingEngine)
 * 负责在玩家进入存档 (ClientPlayConnectionEvents.JOIN 延迟触发) 时，
 * 根据现实离线时长、当前现实作息时间、上次下线摘要与未完成约定，构建极具情感温度的重逢问候指令。
 */
public class ReEntryGreetingEngine {

    /**
     * 现实离线时长五档情感阶梯
     */
    public enum OfflineTier {
        FIRST_MEET,       // 首次在这个存档相见
        T0_QUICK_REJOIN,  // < 30 分钟：闪断、调设置或刚退重进
        T1_SAME_DAY,      // 30 分钟 ~ 12 小时：同天小别
        T2_NEXT_DAY,      // 12 小时 ~ 3 天：隔日如约回归
        T3_MISSED_YOU,    // 3 天 ~ 14 天：多日未见，委屈抱怨 + 撒娇想念 + 留守日常
        T4_LONG_ABSENCE   // > 14 天：跨月久别重逢，强烈控诉与感动 + 唤醒尘封记忆
    }

    /**
     * 构建重返世界的情感问候提示词指令
     */
    public static String buildReEntryPromptInstruction(
            LocalMemoryManager.CoreMemoryDoc memory,
            String currentSnapshotSummary,
            LocalTime currentRealLocalTime) {

        if (memory == null || memory.lastPlayedRealTime == null || memory.lastPlayedRealTime.isBlank()) {
            return "【系统事件：首次进入存档】玩家刚刚进入这个 Minecraft 世界，这是你们在这个存档的第一次相遇！\n"
                    + "当前周围环境态势：\n" + currentSnapshotSummary
                    + "\n请结合当前周围环境，热情、活泼地向主人打个招呼，并开启你们的生存冒险之旅（控制在 60 字以内）！";
        }

        Instant lastPlayed;
        try {
            lastPlayed = Instant.parse(memory.lastPlayedRealTime);
        } catch (Exception e) {
            lastPlayed = Instant.now().minus(Duration.ofHours(20));
        }

        Duration offlineDuration = Duration.between(lastPlayed, Instant.now());
        long minutes = Math.max(1, offlineDuration.toMinutes());
        long hours = offlineDuration.toHours();
        long days = offlineDuration.toDays();

        OfflineTier tier = classifyTier(minutes, hours, days);
        String realTimeHint = buildRealClockHint(currentRealLocalTime);
        String lastSummary = (memory.lastLogoutSummary == null || memory.lastLogoutSummary.isBlank())
                ? "一起在世界里探索生存" : memory.lastLogoutSummary;
        String promises = (memory.activePromises == null || memory.activePromises.isEmpty())
                ? "暂无特定约定" : String.join("；", memory.activePromises);
        String landmarkName = (memory.landmarks != null && !memory.landmarks.isEmpty())
                ? memory.landmarks.get(0).name : "咱们的基地";

        StringBuilder prompt = new StringBuilder();
        prompt.append("【系统事件：玩家重新打开游戏进入存档（重逢问候触发）】\n");
        prompt.append("- 距离上次游玩的现实时间差：").append(formatDurationHumanReadable(minutes, hours, days)).append("\n");
        prompt.append("- 玩家上次下线前的记录：").append(lastSummary).append("\n");
        prompt.append("- 之前定下的共同约定：").append(promises).append("\n");
        if (!realTimeHint.isEmpty()) {
            prompt.append("- 现实作息时间感知：").append(realTimeHint).append("\n");
        }
        prompt.append("- 当前环境态势：\n").append(currentSnapshotSummary).append("\n");

        switch (tier) {
            case T0_QUICK_REJOIN -> prompt.append(
                "【情绪与发言指引】：主人离开不到半小时就回来了（可能是重启调了个设置、加了个模组或刚才掉线了）。请自然地问候一句，顺着上次下线前正在做的事情继续聊（控制在 55 字以内）。"
            );
            case T1_SAME_DAY -> prompt.append(
                "【情绪与发言指引】：主人离开了几小时后再次上线。像默契的同游伙伴一样打个招呼，自然提起刚才做了一半的事或接下来的计划（控制在 65 字以内）。"
            );
            case T2_NEXT_DAY -> prompt.append(
                "【情绪与发言指引】：主人隔了一两天如约回来。请温暖热情地欢迎主人回来，主动提到上次下线前完成的事（"
                + lastSummary + "），并询问今天是不是要按约定去完成【" + promises + "】（控制在 80 字以内）。"
            );
            case T3_MISSED_YOU -> prompt.append(
                "【情绪与发言指引（重点：小抱怨+很想念主人）】：主人已经整整 " + days + " 天没有上线了！"
                + "请先符合你的人格设定，带着一点小委屈、撒娇或傲娇地抱怨主人冷落了你这么久（比如怀疑主人是不是去玩别的游戏把你忘了，描述你这几天一个人在【"
                + landmarkName + "】里发呆、看雨或守家的无聊心情），然后真诚表达你非常想念主人，最后拉着主人开启今天的冒险（控制在 100 字以内）！"
            );
            case T4_LONG_ABSENCE -> prompt.append(
                "【情绪与发言指引（重点：久别重逢的控诉与感动）】：主人已经足足 " + days + " 天没有打开这个存档了！"
                + "请表现出久别重逢的强烈情绪——先控诉/抱怨主人狠心把你一个人丢在存档里等了整整 " + days + " 天（感叹基地都快积灰了），"
                + "紧接着表达再次见到主人的巨大惊喜与思念，并提起你们之前一起经历的回忆（" + lastSummary + "），看看主人还记不记得这里（控制在 110 字以内）！"
            );
            default -> {}
        }
        return prompt.toString();
    }

    private static OfflineTier classifyTier(long minutes, long hours, long days) {
        if (minutes < 30) return OfflineTier.T0_QUICK_REJOIN;
        if (hours < 12) return OfflineTier.T1_SAME_DAY;
        if (days < 3) return OfflineTier.T2_NEXT_DAY;
        if (days < 14) return OfflineTier.T3_MISSED_YOU;
        return OfflineTier.T4_LONG_ABSENCE;
    }

    private static String buildRealClockHint(LocalTime time) {
        int hour = time.getHour();
        if (hour >= 1 && hour < 5) {
            return "现在是现实生活中的凌晨 " + hour + " 点多，主人在熬夜玩游戏，请在问候中顺带温柔吐槽或心疼主人这么晚还没睡。";
        } else if (hour >= 5 && hour < 9) {
            return "现在是现实生活中的清晨 " + hour + " 点多，主人起了个大早来玩游戏。";
        }
        return "";
    }

    private static String formatDurationHumanReadable(long minutes, long hours, long days) {
        if (days >= 1) return days + " 天 " + (hours % 24) + " 小时";
        if (hours >= 1) return hours + " 小时 " + (minutes % 60) + " 分钟";
        return minutes + " 分钟";
    }
}
