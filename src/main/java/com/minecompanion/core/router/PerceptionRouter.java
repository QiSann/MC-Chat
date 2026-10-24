package com.minecompanion.core.router;

/**
 * 混合感知路由器 (PerceptionRouter)
 * 负责解析玩家自然语言意图，并动态组装手持物品、注视目标、已装模组、外部百科与生存态势数据
 */
public class PerceptionRouter {

    public enum IntentType {
        MODS_SUMMARY,    // 询问整合包安装了哪些模组
        HAND_ITEM,       // 询问手持物品用途/合成
        CROSSHAIR_TARGET,// 询问准星注视的目标方块或生物
        WIKI_LOOKUP,     // 询问复杂机制、BOSS 攻略或要求查百科
        SAVE_LANDMARK,   // 自然语言标记当前坐标为地标（如“记住这里叫樱花基地”）
        SAVE_PROMISE,    // 自然语言立下共同约定（如“记住下次我们要去打末影龙”）
        GENERAL_CHAT     // 普通交谈
    }

    /**
     * 识别玩家输入的自然语言意图
     */
    public static IntentType classifyIntent(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return IntentType.GENERAL_CHAT;
        }

        String lower = userMessage.toLowerCase();

        // 1. 空间地标记忆意图（如“记住这里叫主基地”、“把这儿标记为刷怪塔”）
        if ((lower.contains("记住这里") || lower.contains("标记这里") || lower.contains("把这里记作")
                || lower.contains("这里是我们的") || lower.contains("这里叫"))) {
            return IntentType.SAVE_LANDMARK;
        }

        // 2. 共同约定记忆意图（如“记住我们约定明天去下界”、“别忘了下次一起造小黑塔”）
        if ((lower.contains("记住我们") || lower.contains("我们的约定") || lower.contains("别忘了下次")
                || lower.contains("下次我们一起") || lower.contains("约定好"))) {
            return IntentType.SAVE_PROMISE;
        }

        // 3. 百科与深度攻略查询意图（如“查一下暮色森林九头蛇怎么打”、“查百科”、“机制是什么”）
        if (lower.contains("百科") || lower.contains("wiki") || lower.contains("攻略")
                || lower.contains("怎么打") || lower.contains("怎么召唤") || lower.contains("多方块")) {
            return IntentType.WIKI_LOOKUP;
        }

        // 4. 模组清单意图
        if (lower.contains("模组") || lower.contains("有哪些mod") || lower.contains("装了什么mod")
                || lower.contains("装了哪些mod") || lower.contains("mod列表") || lower.contains("整合包")) {
            if (lower.contains("哪些") || lower.contains("什么") || lower.contains("有啥")
                    || lower.contains("清单") || lower.contains("列表") || lower.contains("多少")) {
                return IntentType.MODS_SUMMARY;
            }
        }

        // 5. 手持物品意图
        if (lower.contains("手里") || lower.contains("手持") || lower.contains("拿着") || lower.contains("拿的")
                || lower.contains("这是什么") || lower.contains("这东西") || lower.contains("怎么做")
                || lower.contains("怎么合成") || lower.contains("有什么用") || lower.contains("怎么用")
                || lower.contains("配方") || lower.contains("合成表")) {
            return IntentType.HAND_ITEM;
        }

        // 6. 注视目标意图
        if (lower.contains("前面") || lower.contains("面前") || lower.contains("看着")
                || lower.contains("注视") || lower.contains("那是什么") || lower.contains("这生物")
                || lower.contains("这怪") || lower.contains("这树") || lower.contains("这个方块")) {
            return IntentType.CROSSHAIR_TARGET;
        }

        return IntentType.GENERAL_CHAT;
    }

    /**
     * 从自然语言句子中提取地标名称（例如“记住这里叫樱花木屋” -> “樱花木屋”）
     */
    public static String extractLandmarkName(String msg) {
        String cleaned = msg.replaceAll(".*[叫为是作][:：\\s]*", "")
                .replaceAll("[！!。.~～喵]+$", "")
                .trim();
        if (cleaned.isEmpty() || cleaned.length() > 16) {
            return "重要据点";
        }
        return cleaned;
    }

    /**
     * 从自然语言句子中提取需要检索百科的核心关键词
     */
    public static String extractWikiKeyword(String msg, String handItemName, String targetName) {
        String cleaned = msg.replaceAll("(帮我|请问|小爱|查一下|查百科|百科|wiki|怎么打|怎么召唤|攻略|是什么|有什么用|呢|吗|呀|喵|[？?！!。])", "").trim();
        if (!cleaned.isBlank() && cleaned.length() >= 2) {
            return cleaned;
        }
        if (handItemName != null && !handItemName.isBlank()) return handItemName;
        if (targetName != null && !targetName.isBlank()) return targetName;
        return msg;
    }

    /**
     * 根据意图自动动态组装送往大模型的提示词（融合环境、自省名片与百科摘要）
     */
    public static String assembleContextPrompt(
            String userMessage,
            String handItemCard,
            String crosshairCard,
            String modsSummary,
            String stateSnapshot,
            String wikiSnippet) {

        IntentType intent = classifyIntent(userMessage);

        StringBuilder sb = new StringBuilder();
        sb.append(userMessage).append("\n\n");
        sb.append("【玩家实时生存态势】\n").append(stateSnapshot).append("\n");

        if (wikiSnippet != null && !wikiSnippet.isBlank()) {
            sb.append(wikiSnippet).append("\n\n");
        }

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

            case WIKI_LOOKUP:
                if (handItemCard != null && !handItemCard.contains("空手")) {
                    sb.append(handItemCard).append("\n");
                }
                sb.append(crosshairCard).append("\n");
                sb.append("（请结合外部百科检索资料与游戏内数据，通俗清晰地为主人讲解机制或攻略要点，控制在 110 字以内）");
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
