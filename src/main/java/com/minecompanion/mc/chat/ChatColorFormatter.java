package com.minecompanion.mc.chat;

import com.minecompanion.core.config.CompanionConfig;

/**
 * 负责将 AI 返回的轻量 Markdown 格式转换为 Minecraft 原生彩色格式码（§ 代码）
 */
public class ChatColorFormatter {

    /**
     * 将 AI 消息渲染为带伙伴昵称前缀的 Minecraft 富文本格式
     *
     * @param rawReply 大模型返回的原始 Markdown 字符串
     * @return Minecraft 原生颜色代码字符串
     */
    public static String formatAssistantReply(String rawReply) {
        if (rawReply == null || rawReply.isBlank()) {
            return "";
        }

        CompanionConfig config = CompanionConfig.get();
        String prefix = config.nameColor + "[" + config.companionName + "] §f";

        // 1. 处理加粗 **text** -> §ltext§r§f
        String formatted = rawReply.replaceAll("\\*\\*(.*?)\\*\\*", "§l$1§r§f");

        // 2. 处理斜体 *text* -> §otext§r§f
        formatted = formatted.replaceAll("(?<!\\*)\\*(?!\\*)(.*?)(?<!\\*)\\*(?!\\*)", "§o$1§r§f");

        // 3. 处理行内代码 `code` -> §7§o$1§r§f
        formatted = formatted.replaceAll("`(.*?)`", "§7§o$1§r§f");

        // 4. 处理换行（在 Minecraft 原生聊天栏中，换行自动附带前缀缩进以保持美观）
        formatted = formatted.replace("\n", "\n§7│ §f");

        return prefix + formatted;
    }

    /**
     * 系统提示信息染色（浅灰色斜体）
     */
    public static String formatSystemHint(String hint) {
        return "§7§o[系统] " + hint + "§r";
    }

    /**
     * 错误/报警信息染色（浅红色）
     */
    public static String formatError(String error) {
        return "§c[错误] " + error + "§r";
    }
}