package com.minecompanion.core.router;

/**
 * 混合感知路由器 (PerceptionRouter)
 * 负责解析玩家自然语言意图，并以自然、去AI化的方式将手持物品、注视目标、已装模组或外部百科融合进对话
 */
public class PerceptionRouter {

    public enum IntentType {
        MODS_SUMMARY,    // 询问整合包安装了哪些模组
        HAND_ITEM,       // 询问手持物品用途/合成
        CROSSHAIR_TARGET,// 询问准星注视的目标方块或生物
        WIKI_LOOKUP,     // 询问复杂机制、BOSS 攻略或要求查百科
        VISION_LOOK,     // 询问画面、建筑外观、风景评价（触发 OpenGL 关键时刻视觉截图）
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

        // 1. 空间地标记忆意图
        if (lower.contains("记住这里") || lower.contains("标记这里") || lower.contains("把这里记作")
                || lower.contains("这里是我们的") || lower.contains("这里叫")) {
            return IntentType.SAVE_LANDMARK;
        }

        // 2. 共同约定记忆意图
        if (lower.contains("记住我们") || lower.contains("我们的约定") || lower.contains("别忘了下次")
                || lower.contains("下次我们一起") || lower.contains("约定好")) {
            return IntentType.SAVE_PROMISE;
        }

        // 3. 视觉画面/建筑鉴赏意图
        if (lower.startsWith("[看]") || lower.contains("好看吗") || lower.contains("评价一下")
                || lower.contains("打个分") || lower.contains("打分") || lower.contains("这建筑")
                || lower.contains("建的房") || lower.contains("建的家") || lower.contains("装修")
                || lower.contains("配色") || lower.contains("这风景") || lower.contains("看看画面")) {
            return IntentType.VISION_LOOK;
        }

        // 4. 百科与深度攻略查询意图
        if (lower.contains("百科") || lower.contains("wiki") || lower.contains("攻略")
                || lower.contains("怎么打") || lower.contains("怎么召唤") || lower.contains("多方块")) {
            return IntentType.WIKI_LOOKUP;
        }

        // 5. 模组清单意图
        if (lower.contains("模组") || lower.contains("有哪些mod") || lower.contains("装了什么mod")
                || lower.contains("装了哪些mod") || lower.contains("mod列表") || lower.contains("整合包")) {
            if (lower.contains("哪些") || lower.contains("什么") || lower.contains("有啥")
                    || lower.contains("清单") || lower.contains("列表") || lower.contains("多少")) {
                return IntentType.MODS_SUMMARY;
            }
        }

        // 6. 手持物品意图
        if (lower.contains("手里") || lower.contains("手持") || lower.contains("拿着") || lower.contains("拿的")
                || lower.contains("这是什么") || lower.contains("这东西") || lower.contains("怎么做")
                || lower.contains("怎么合成") || lower.contains("有什么用") || lower.contains("怎么用")
                || lower.contains("配方") || lower.contains("合成表")) {
            return IntentType.HAND_ITEM;
        }

        // 7. 注视目标意图
        if (lower.contains("前面") || lower.contains("面前") || lower.contains("看着")
                || lower.contains("注视") || lower.contains("那是什么") || lower.contains("这生物")
                || lower.contains("这怪") || lower.contains("这树") || lower.contains("这个方块")) {
            return IntentType.CROSSHAIR_TARGET;
        }

        return IntentType.GENERAL_CHAT;
    }

    public static String extractLandmarkName(String msg) {
        String cleaned = msg.replaceAll(".*[叫为是作][:：\\s]*", "")
                .replaceAll("[！!。.~～喵]+$", "")
                .trim();
        if (cleaned.isEmpty() || cleaned.length() > 16) {
            return "重要据点";
        }
        return cleaned;
    }

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
     * 组装注入到 System Prompt 尾部的实时环境、手持物、视线与记忆背景块（让 User 消息保持纯净，确保多轮对话上下文高度连贯）
     */
    public static String assembleDynamicSystemAddons(
            IntentType intent,
            String handItemCard,
            String crosshairCard,
            String modsSummary,
            String stateSnapshot,
            String wikiSnippet,
            String memoryContextBlock) {

        StringBuilder sb = new StringBuilder();
        sb.append("=== 【当前游戏实时环境与背包真实状态】 ===\n");
        sb.append(stateSnapshot).append("\n");

        switch (intent) {
            case MODS_SUMMARY:
                sb.append(modsSummary).append("\n");
                break;
            case HAND_ITEM:
                sb.append(handItemCard).append("\n");
                break;
            case CROSSHAIR_TARGET:
            case VISION_LOOK:
                sb.append(crosshairCard).append("\n");
                break;
            case WIKI_LOOKUP:
                if (handItemCard != null && !handItemCard.contains("空手")) {
                    sb.append(handItemCard).append("\n");
                }
                sb.append(crosshairCard).append("\n");
                if (wikiSnippet != null && !wikiSnippet.isBlank()) {
                    sb.append(wikiSnippet).append("\n");
                }
                break;
            case GENERAL_CHAT:
            default:
                if (handItemCard != null && !handItemCard.contains("空手")) {
                    sb.append("玩家主手正拿着: ").append(handItemCard.lines().findFirst().orElse("")).append("\n");
                }
                sb.append(crosshairCard).append("\n");
                break;
        }

        if (memoryContextBlock != null && !memoryContextBlock.isBlank()) {
            sb.append("\n").append(memoryContextBlock);
        }

        return sb.toString();
    }
}
