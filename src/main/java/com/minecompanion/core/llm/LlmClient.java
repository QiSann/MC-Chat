package com.minecompanion.core.llm;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.minecompanion.core.config.CompanionConfig;
import com.minecompanion.core.memory.LocalMemoryManager;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 通用大模型异步通信客户端
 * 基于 Java 17 原生 HttpClient 实现，兼容 OpenAI / Gemini / DeepSeek 等主流服务商，
 * 全面支持 L1 多轮上下文记忆衔接、双模型视觉路由 (Vision Multimodal) 以及纯文本模型平滑自动降级
 */
public class LlmClient {
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * 单条对话消息结构体（支持 system、user、assistant）
     */
    public record ChatMessage(String role, String content) {}

    /**
     * 发起多轮异步对话请求（支持完整上下文历史）
     *
     * @param messages 上下文消息列表
     * @return CompletableFuture 异步返回 AI 的文字回复
     */
    public static CompletableFuture<String> chatAsync(List<ChatMessage> messages) {
        CompanionConfig config = CompanionConfig.get();

        if (config.apiKey == null || config.apiKey.isBlank()) {
            return CompletableFuture.completedFuture("【提示】未配置 API Key 喵！请按 K 键打开配置中心填入您的大模型密钥。");
        }

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", config.modelName);
        requestBody.addProperty("temperature", 0.85);

        JsonArray msgArray = new JsonArray();
        for (ChatMessage msg : messages) {
            JsonObject m = new JsonObject();
            m.addProperty("role", msg.role());
            m.addProperty("content", msg.content());
            msgArray.add(m);
        }
        requestBody.add("messages", msgArray);

        String endpoint = normalizeEndpoint(config.baseUrl);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.apiKey.trim())
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(requestBody), StandardCharsets.UTF_8))
                .build();

        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> {
                    int statusCode = response.statusCode();
                    String body = response.body();

                    if (statusCode == 200) {
                        return parseAssistantReply(body);
                    } else if (statusCode == 401) {
                        return "【API 报错】API Key 认证失败 (401)，请按 K 键打开配置界面检查密钥喵！";
                    } else if (statusCode == 402) {
                        return "【API 报错】账号余额不足 (402)，请前往服务商平台充值喵！";
                    } else if (statusCode == 429) {
                        return "【API 报错】请求过于频繁或超过配额限制 (429)，稍后再试一次吧喵~";
                    } else {
                        return "【网络异常】服务器返回状态码 " + statusCode + "：" + truncate(body, 100);
                    }
                })
                .exceptionally(ex -> "【连接超时/错误】无法连接到 AI 服务器喵，请检查网络或代理设置！错误：" + ex.getMessage());
    }

    /**
     * 结合 L1 连续对话上下文、实时环境快照与 L2/L3 记忆库发起对话，并将本轮互动（含主动事件）记入 L1 滑动窗口
     *
     * @param historyRecordText   写入 L1 历史记录的简短事件或玩家原话（保证下一句聊天能接上本轮）
     * @param currentTurnPrompt   本轮发送给模型的用户消息/事件触发提示
     * @param dynamicSystemAddons 注入在 System Prompt 尾部的实时环境、背包食物与长期记忆块
     */
    public static CompletableFuture<String> chatWithMemoryAsync(
            String historyRecordText,
            String currentTurnPrompt,
            String dynamicSystemAddons) {

        CompanionConfig config = CompanionConfig.get();
        List<ChatMessage> list = new ArrayList<>();

        // 1. 组装 System Prompt（人设 + 实时环境态势 + 背包食物 + L2/L3 长期记忆）
        StringBuilder sysBuilder = new StringBuilder();
        if (config.systemPrompt != null && !config.systemPrompt.isBlank()) {
            sysBuilder.append(config.systemPrompt).append("\n\n");
        }
        sysBuilder.append("【重要对话准则】你正在和玩家实时连麦同游，请务必紧接着【历史对话上下文】往下聊，表现出真实的开心、生气、着急、吐槽或撒娇情绪，不要像机器人一样自顾自说套话！\n");
        if (dynamicSystemAddons != null && !dynamicSystemAddons.isBlank()) {
            sysBuilder.append("\n").append(dynamicSystemAddons);
        }
        list.add(new ChatMessage("system", sysBuilder.toString().trim()));

        // 2. 注入 L1 短期多轮对话滑动窗口（包括之前玩家说的话与 AI 自己主动说的话）
        LocalMemoryManager memMgr = LocalMemoryManager.getCurrent();
        if (memMgr != null) {
            list.addAll(memMgr.getRecentChatHistory());
        }

        // 3. 压入本轮提问或事件触发指令
        list.add(new ChatMessage("user", currentTurnPrompt));

        return chatAsync(list).thenApply(reply -> {
            // 只要成功拿到正常回复，无论是由玩家打字触发还是游戏内事件主动触发，全部记入 L1 上下文！
            if (memMgr != null && !reply.startsWith("【")) {
                memMgr.recordChatTurn(historyRecordText, reply);
            }
            return reply;
        });
    }

    /**
     * 兼容简便调用（同样自动走 L1 多轮上下文记忆！）
     */
    public static CompletableFuture<String> chatSimpleAsync(String userQuestion) {
        return chatWithMemoryAsync(userQuestion, userQuestion, "");
    }

    /**
     * 多模态视觉看图请求（支持双模型路由：优先调用独立视觉模型，若当前模型为纯文本模型报错 400/422 则平滑自动降级为纯文本态势感知回答）
     */
    public static CompletableFuture<String> chatVisionAsync(
            String rawUserText,
            String assembledUserPrompt,
            String jpegBase64,
            String dynamicSystemAddons) {

        if (jpegBase64 == null || jpegBase64.isBlank()) {
            return chatWithMemoryAsync(rawUserText, assembledUserPrompt, dynamicSystemAddons);
        }

        CompanionConfig config = CompanionConfig.get();
        if (config.apiKey == null || config.apiKey.isBlank()) {
            return CompletableFuture.completedFuture("【提示】未配置 API Key 喵！请按 K 键打开配置中心填入您的大模型密钥。");
        }

        String targetBaseUrl = (config.enableVisionModel && config.visionBaseUrl != null && !config.visionBaseUrl.isBlank())
                ? config.visionBaseUrl : config.baseUrl;
        String targetModel = (config.enableVisionModel && config.visionModelName != null && !config.visionModelName.isBlank())
                ? config.visionModelName : config.modelName;

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", targetModel);
        requestBody.addProperty("temperature", 0.8);

        JsonArray msgArray = new JsonArray();

        StringBuilder sysBuilder = new StringBuilder();
        if (config.systemPrompt != null && !config.systemPrompt.isBlank()) {
            sysBuilder.append(config.systemPrompt).append("\n\n");
        }
        if (dynamicSystemAddons != null && !dynamicSystemAddons.isBlank()) {
            sysBuilder.append(dynamicSystemAddons);
        }
        if (!sysBuilder.isEmpty()) {
            JsonObject sysMsg = new JsonObject();
            sysMsg.addProperty("role", "system");
            sysMsg.addProperty("content", sysBuilder.toString().trim());
            msgArray.add(sysMsg);
        }

        // 注入最近上下文
        LocalMemoryManager memMgr = LocalMemoryManager.getCurrent();
        if (memMgr != null) {
            for (ChatMessage historyMsg : memMgr.getRecentChatHistory()) {
                JsonObject hm = new JsonObject();
                hm.addProperty("role", historyMsg.role());
                hm.addProperty("content", historyMsg.content());
                msgArray.add(hm);
            }
        }

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        JsonArray contentArr = new JsonArray();

        JsonObject textPart = new JsonObject();
        textPart.addProperty("type", "text");
        textPart.addProperty("text", assembledUserPrompt);
        contentArr.add(textPart);

        JsonObject imagePart = new JsonObject();
        imagePart.addProperty("type", "image_url");
        JsonObject imageUrlObj = new JsonObject();
        imageUrlObj.addProperty("url", "data:image/jpeg;base64," + jpegBase64);
        imagePart.add("image_url", imageUrlObj);
        contentArr.add(imagePart);

        userMsg.add("content", contentArr);
        msgArray.add(userMsg);
        requestBody.add("messages", msgArray);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(normalizeEndpoint(targetBaseUrl)))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.apiKey.trim())
                .timeout(Duration.ofSeconds(35))
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(requestBody), StandardCharsets.UTF_8))
                .build();

        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenCompose(response -> {
                    if (response.statusCode() == 200) {
                        String reply = parseAssistantReply(response.body());
                        if (memMgr != null && !reply.startsWith("【")) {
                            memMgr.recordChatTurn(rawUserText, reply);
                        }
                        return CompletableFuture.completedFuture(reply);
                    }
                    return chatWithMemoryAsync(rawUserText, assembledUserPrompt, dynamicSystemAddons);
                })
                .exceptionallyCompose(ex -> chatWithMemoryAsync(rawUserText, assembledUserPrompt, dynamicSystemAddons));
    }

    /**
     * 一键测试连通性（发送 5 个 Token 的极简请求）
     */
    public static CompletableFuture<String> testConnectionAsync() {
        List<ChatMessage> testMsgs = List.of(
                new ChatMessage("user", "Hello! 请回复'OK'")
        );
        return chatAsync(testMsgs);
    }

    private static String normalizeEndpoint(String baseUrl) {
        String base = (baseUrl == null ? "https://api.openai.com/v1" : baseUrl).trim().replaceAll("/+$", "");
        return base.endsWith("/chat/completions") ? base : base + "/chat/completions";
    }

    private static String parseAssistantReply(String jsonResponse) {
        try {
            JsonObject json = JsonParser.parseString(jsonResponse).getAsJsonObject();
            JsonArray choices = json.getAsJsonArray("choices");
            if (choices != null && !choices.isEmpty()) {
                JsonObject firstChoice = choices.get(0).getAsJsonObject();
                JsonObject message = firstChoice.getAsJsonObject("message");
                if (message != null && message.has("content")) {
                    return message.get("content").getAsString().trim();
                }
            }
        } catch (Exception e) {
            return "【解析失败】返回的数据格式异常喵：" + truncate(jsonResponse, 80);
        }
        return "【无回复】AI 没有返回有效文本。";
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
