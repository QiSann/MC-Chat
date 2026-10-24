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
 * 基于 Java 17 原生 HttpClient 实现，兼容 OpenAI / Gemini / DeepSeek 等主流服务商
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

        // 1. 基础合法性校验
        if (config.apiKey == null || config.apiKey.isBlank()) {
            return CompletableFuture.completedFuture("【提示】未配置 API Key 喵！请在配置文件中填入您的大模型密钥。");
        }

        // 2. 组装 OpenAI 规范的请求体 JSON
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", config.modelName);
        requestBody.addProperty("temperature", 0.7);

        JsonArray msgArray = new JsonArray();
        for (ChatMessage msg : messages) {
            JsonObject m = new JsonObject();
            m.addProperty("role", msg.role());
            m.addProperty("content", msg.content());
            msgArray.add(m);
        }
        requestBody.add("messages", msgArray);

        // 格式化 API 请求地址（自动去除多余结尾斜杠并补全 /chat/completions）
        String base = config.baseUrl.trim().replaceAll("/+$", "");
        String endpoint = base.endsWith("/chat/completions") ? base : base + "/chat/completions";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.apiKey.trim())
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(requestBody), StandardCharsets.UTF_8))
                .build();

        // 3. 异步发送 HTTP 请求并解析返回的 JSON 结果
        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> {
                    int statusCode = response.statusCode();
                    String body = response.body();

                    if (statusCode == 200) {
                        return parseAssistantReply(body);
                    } else if (statusCode == 401) {
                        return "【API 报错】API Key 认证失败 (401)，请检查密钥是否填写正确喵！";
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
     * 便捷方法：仅发送单条提问（自动附带 System Prompt）
     */
    public static CompletableFuture<String> chatSimpleAsync(String userQuestion) {
        CompanionConfig config = CompanionConfig.get();
        List<ChatMessage> list = new ArrayList<>();
        if (config.systemPrompt != null && !config.systemPrompt.isBlank()) {
            list.add(new ChatMessage("system", config.systemPrompt));
        }
        list.add(new ChatMessage("user", userQuestion));
        return chatAsync(list);
    }

    /**
     * 结合三层记忆库 (L1 多轮上下文 + L2 核心档案 + L3 情景日记) 发起智能对话，并在回复后自动记录到 L1 滑动窗口
     */
    public static CompletableFuture<String> chatWithMemoryAsync(
            String rawUserText,
            String assembledUserPrompt,
            String memoryContextBlock) {

        CompanionConfig config = CompanionConfig.get();
        List<ChatMessage> list = new ArrayList<>();

        // 1. 组装 System Prompt + L2/L3 记忆档案
        StringBuilder sysBuilder = new StringBuilder();
        if (config.systemPrompt != null && !config.systemPrompt.isBlank()) {
            sysBuilder.append(config.systemPrompt).append("\n\n");
        }
        if (memoryContextBlock != null && !memoryContextBlock.isBlank()) {
            sysBuilder.append(memoryContextBlock);
        }
        if (!sysBuilder.isEmpty()) {
            list.add(new ChatMessage("system", sysBuilder.toString().trim()));
        }

        // 2. 注入 L1 短期多轮对话滑动窗口
        LocalMemoryManager memMgr = LocalMemoryManager.getCurrent();
        if (memMgr != null) {
            list.addAll(memMgr.getRecentChatHistory());
        }

        // 3. 压入本轮组装后的用户提问
        list.add(new ChatMessage("user", assembledUserPrompt));

        return chatAsync(list).thenApply(reply -> {
            // 若成功获得正常回复（非报错提示），则记入 L1 工作记忆滑动窗口
            if (memMgr != null && rawUserText != null && !rawUserText.isBlank() && !reply.startsWith("【")) {
                memMgr.recordChatTurn(rawUserText, reply);
            }
            return reply;
        });
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

    /**
     * 从 OpenAI 标准响应体提取 AI 回复正文
     */
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
