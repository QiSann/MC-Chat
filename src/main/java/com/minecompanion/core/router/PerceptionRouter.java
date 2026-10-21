package com.minecompanion.core.router;

/**
 * 混合感知路由器 (PerceptionRouter)
 * 负责解析玩家自然语言意图，并动态组装手持物品、注视目标、已装模组与生存态势数据
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
     * 根据意图自动动态组装送往大模型的提示词（融合环境与生存态势）
     */
    public static String assembleContextPrompt(
            String userMessage,
            String handItemCard,
            String crosshairCard,
            String modsSummary,
            String stateSnapshot) {

        IntentType intent = classifyIntent(userMessage);

        StringBuilder sb = new StringBuilder();
        sb.append(userMessage).append("\n\n");
        sb.append("【玩家实时生存态势】\n").append(stateSnapshot).append("\n");

        switch (intent) {
            case MODS_SUMMARY:
                sb.append("【已装模组信息】\n").append(modsSummary)
                  .append("\n（请以你的活泼伙伴口吻，为玩家挑重点简要介绍这个整合包的核心模组特色）");
                break;

            case HAND_ITEM:
                sb.append(handItemCard)
                  .append("\n（请根据上面提取的游戏内真实数据，向玩家讲解这件手持物品的名称、出处模组、用途及合成关键）");
                break;

            case CROSSHAIR_TARGET:
                sb.append(crosshairCard)
                  .append("\n（请根据上面提取的视线目标数据，向玩家介绍面前的目标属于哪个模组、有什么机制或注意事项）");
                break;

            case GENERAL_CHAT:
            default:
                if (handItemCard != null && !handItemCard.contains("空手")) {
                    sb.append(handItemCard).append("\n");
                }
                sb.append(crosshairCard);
                break;
        }

        return sb.toString();
    }
}
