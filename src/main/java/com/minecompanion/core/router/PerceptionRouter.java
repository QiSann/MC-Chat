package com.minecompanion.core.router;

/**
 * 混合感知路由器 (PerceptionRouter)
 * 负责解析玩家自然语言意图（例如“手里拿的是什么”、“装了哪些模组”、“面前是什么”），
 * 智能命中手持物品、注视目标或模组全息数据，无需硬记繁琐的斜杠指令！
 */
public class PerceptionRouter {

    public enum IntentType {
        MODS_SUMMARY,   // 询问整合包安装了哪些模组
        HAND_ITEM,      // 询问手持物品用途/合成
        CROSSHAIR_TARGET,// 询问准星注视的目标方块或生物
        GENERAL_CHAT    // 普通交谈
    }

    /**
     * 识别玩家输入的自然语言意图
     */
    public static IntentType classifyIntent(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return IntentType.GENERAL_CHAT;
        }

        String lower = userMessage.toLowerCase();

        // 1. 模组清单意图
        if (lower.contains("模组") || lower.contains("有哪些mod") || lower.contains("装了什么mod")
                || lower.contains("装了哪些mod") || lower.contains("mod列表") || lower.contains("整合包")) {
            if (lower.contains("哪些") || lower.contains("什么") || lower.contains("有啥")
                    || lower.contains("清单") || lower.contains("列表") || lower.contains("多少")) {
                return IntentType.MODS_SUMMARY;
            }
        }

        // 2. 手持物品意图
        if (lower.contains("手里") || lower.contains("手持") || lower.contains("拿着") || lower.contains("拿的")
                || lower.contains("这是什么") || lower.contains("这东西") || lower.contains("怎么做")
                || lower.contains("怎么合成") || lower.contains("有什么用") || lower.contains("怎么用")
                || lower.contains("配方") || lower.contains("合成表")) {
            return IntentType.HAND_ITEM;
        }

        // 3. 注视目标意图
        if (lower.contains("前面") || lower.contains("面前") || lower.contains("看着")
                || lower.contains("注视") || lower.contains("那是什么") || lower.contains("这生物")
                || lower.contains("这怪") || lower.contains("这树") || lower.contains("这个方块")) {
            return IntentType.CROSSHAIR_TARGET;
        }

        return IntentType.GENERAL_CHAT;
    }

    /**
     * 根据意图自动动态组装送往大模型的提示词
     */
    public static String assembleContextPrompt(
            String userMessage,
            String handItemCard,
            String crosshairCard,
            String modsSummary) {

        IntentType intent = classifyIntent(userMessage);

        switch (intent) {
            case MODS_SUMMARY:
                return userMessage + "\n\n【游戏内真实数据-已装模组】\n" + modsSummary
                        + "\n（请以你的活泼伙伴口吻，为玩家挑重点简要介绍这个整合包的核心模组特色）";

            case HAND_ITEM:
                return userMessage + "\n\n" + handItemCard
                        + "\n（请根据上面提取的游戏内真实数据，向玩家讲解这件手持物品的名称、出处模组、用途及合成关键）";

            case CROSSHAIR_TARGET:
                return userMessage + "\n\n" + crosshairCard
                        + "\n（请根据上面提取的视线目标数据，向玩家介绍面前的目标属于哪个模组、有什么机制或注意事项）";

            case GENERAL_CHAT:
            default:
                // 普通聊天也悄悄带上手持与视线感知，以便 AI 心领神会
                StringBuilder sb = new StringBuilder(userMessage);
                if (handItemCard != null && !handItemCard.contains("空手")) {
                    sb.append("\n\n【环境感知补充】\n").append(handItemCard).append("\n").append(crosshairCard);
                }
                return sb.toString();
        }
    }
}
