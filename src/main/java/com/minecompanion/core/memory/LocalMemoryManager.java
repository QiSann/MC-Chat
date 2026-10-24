package com.minecompanion.core.memory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.minecompanion.core.llm.LlmClient;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 本地持久化三层记忆管理器 (LocalMemoryManager)
 * 借鉴“结构化小文档 + 只追加日志”落盘哲学，零外部数据库依赖实现：
 * - L1 短期工作记忆 (recent_session_context.json): 保存最近 16 条对话滑动窗口，实现多轮上下文连贯与重进无缝续聊
 * - L2 核心档案记忆 (core_memory.json): 常驻保存基地地标坐标、共同约定、玩家偏好与上次离线时间戳
 * - L3 历史情景日记 (episodic_log.jsonl): 只追加写入的历史共同经历摘要，支持关键词与空间坐标双重检索
 */
public class LocalMemoryManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Gson COMPACT_GSON = new Gson();
    private static final int MAX_L1_MESSAGES = 16;

    private static LocalMemoryManager currentInstance;

    private final Path worldMemoryDir;
    private final Path coreMemoryPath;
    private final Path episodicLogPath;
    private final Path recentContextPath;

    private CoreMemoryDoc coreMemory;
    private final List<LlmClient.ChatMessage> l1WorkingMemory = new ArrayList<>();
    // 记录地标触发冷却，防止玩家在基地里走动时反复触发“故地重游”
    private final Map<String, Long> landmarkTriggerCooldowns = new HashMap<>();

    public LocalMemoryManager(Path baseConfigDir, String worldIdentifier) {
        String safeWorldId = sanitizeFileName(worldIdentifier);
        this.worldMemoryDir = baseConfigDir.resolve("memories").resolve("worlds").resolve(safeWorldId);
        this.coreMemoryPath = worldMemoryDir.resolve("core_memory.json");
        this.episodicLogPath = worldMemoryDir.resolve("episodic_log.jsonl");
        this.recentContextPath = worldMemoryDir.resolve("recent_session_context.json");
        loadOrInitAll();
    }

    public static synchronized void initForWorld(Path baseConfigDir, String worldIdentifier) {
        currentInstance = new LocalMemoryManager(baseConfigDir, worldIdentifier);
    }

    public static synchronized LocalMemoryManager getCurrent() {
        return currentInstance;
    }

    /**
     * 加载或初始化三层记忆文件
     */
    public synchronized void loadOrInitAll() {
        try {
            Files.createDirectories(worldMemoryDir);
            // 1. 加载 L2 核心记忆
            if (Files.exists(coreMemoryPath)) {
                String json = Files.readString(coreMemoryPath, StandardCharsets.UTF_8);
                this.coreMemory = GSON.fromJson(json, CoreMemoryDoc.class);
            }
            if (this.coreMemory == null) {
                this.coreMemory = new CoreMemoryDoc();
            }

            // 2. 加载 L1 最近对话上下文
            l1WorkingMemory.clear();
            if (Files.exists(recentContextPath)) {
                String json = Files.readString(recentContextPath, StandardCharsets.UTF_8);
                Type listType = new TypeToken<List<LlmClient.ChatMessage>>() {}.getType();
                List<LlmClient.ChatMessage> loaded = GSON.fromJson(json, listType);
                if (loaded != null) {
                    l1WorkingMemory.addAll(loaded);
                }
            }
        } catch (Exception e) {
            if (this.coreMemory == null) {
                this.coreMemory = new CoreMemoryDoc();
            }
        }
    }

    public CoreMemoryDoc getCoreMemory() {
        return coreMemory;
    }

    /**
     * 向 L1 短期记忆滑动窗口追加一轮对话
     */
    public synchronized void recordChatTurn(String userText, String assistantReply) {
        l1WorkingMemory.add(new LlmClient.ChatMessage("user", userText));
        l1WorkingMemory.add(new LlmClient.ChatMessage("assistant", assistantReply));
        while (l1WorkingMemory.size() > MAX_L1_MESSAGES) {
            l1WorkingMemory.remove(0);
        }
        // 更新最后一次互动的简要记录
        this.coreMemory.lastLogoutSummary = "与主人聊了关于「" + truncate(userText, 25) + "」的话题";
    }

    /**
     * 获取 L1 短期对话历史副本
     */
    public synchronized List<LlmClient.ChatMessage> getRecentChatHistory() {
        return new ArrayList<>(l1WorkingMemory);
    }

    /**
     * 原子化保存 L2 核心档案小文档
     */
    public synchronized void saveCoreMemory() {
        try {
            Files.createDirectories(worldMemoryDir);
            Path tempFile = worldMemoryDir.resolve("core_memory.json.tmp");
            Files.writeString(tempFile, GSON.toJson(this.coreMemory), StandardCharsets.UTF_8);
            Files.move(tempFile, coreMemoryPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
        }
    }

    /**
     * 保存 L1 短期上下文快照
     */
    public synchronized void saveRecentContext() {
        try {
            Files.createDirectories(worldMemoryDir);
            Files.writeString(recentContextPath, GSON.toJson(this.l1WorkingMemory), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    /**
     * 在玩家退出存档时调用：非阻塞快速落盘 L1、L2 与 L3 日记
     */
    public synchronized void onWorldDisconnect(long currentGameDay, String dimension, int x, int y, int z) {
        this.coreMemory.lastPlayedRealTime = Instant.now().toString();
        this.coreMemory.lastPlayedGameDay = currentGameDay;
        if (this.coreMemory.lastLogoutSummary == null || this.coreMemory.lastLogoutSummary.isBlank()) {
            this.coreMemory.lastLogoutSummary = String.format("在 %s (X:%d, Y:%d, Z:%d) 附近探索生存", dimension, x, y, z);
        }
        saveCoreMemory();
        saveRecentContext();

        // 若本次会话有过聊天互动，则向 L3 情景日记追加一条会话记录
        if (!l1WorkingMemory.isEmpty()) {
            EpisodicEntry entry = new EpisodicEntry();
            entry.ts = Instant.now().toString();
            entry.gameDay = currentGameDay;
            entry.dim = dimension;
            entry.pos = new int[]{x, y, z};
            entry.tags = List.of("会话记录", dimension);
            entry.summary = this.coreMemory.lastLogoutSummary;
            appendEpisodicEntry(entry);
        }
    }

    /**
     * 添加或更新空间地标（如基地、矿洞、樱花木屋）
     */
    public synchronized void addLandmark(String name, String dimension, int x, int y, int z, String description) {
        Landmark lm = new Landmark();
        lm.id = "lm_" + System.currentTimeMillis();
        lm.name = name;
        lm.dimension = dimension;
        lm.x = x;
        lm.y = y;
        lm.z = z;
        lm.radius = 32;
        lm.description = description;
        this.coreMemory.landmarks.removeIf(existing -> existing.name.equals(name));
        this.coreMemory.landmarks.add(lm);
        saveCoreMemory();

        // 同步记入 L3 情景日记
        EpisodicEntry entry = new EpisodicEntry();
        entry.ts = Instant.now().toString();
        entry.gameDay = this.coreMemory.lastPlayedGameDay;
        entry.dim = dimension;
        entry.pos = new int[]{x, y, z};
        entry.tags = List.of("地标建立", name);
        entry.summary = String.format("与主人共同标记了重要地点【%s】(坐标 X:%d, Y:%d, Z:%d)：%s", name, x, y, z, description);
        appendEpisodicEntry(entry);
    }

    /**
     * 添加共同约定或核心事实
     */
    public synchronized void addPromiseOrFact(String content, boolean isPromise) {
        if (isPromise) {
            if (!this.coreMemory.activePromises.contains(content)) {
                this.coreMemory.activePromises.add(content);
            }
        } else {
            if (!this.coreMemory.playerFacts.contains(content)) {
                this.coreMemory.playerFacts.add(content);
            }
        }
        saveCoreMemory();
    }

    /**
     * 向 L3 情景日记流追加单行记录 (Append-Only JSONL)
     */
    public synchronized void appendEpisodicEntry(EpisodicEntry entry) {
        try {
            Files.createDirectories(worldMemoryDir);
            try (BufferedWriter writer = Files.newBufferedWriter(
                    episodicLogPath,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND)) {
                writer.write(COMPACT_GSON.toJson(entry));
                writer.newLine();
            }
        } catch (IOException ignored) {
        }
    }

    /**
     * 检查玩家是否走到了某个记忆地标附近（故地重游触发器，每个地标 15 分钟内最多触发一次）
     */
    public synchronized Landmark checkNearbyLandmarkForAwakening(String dimension, int playerX, int playerY, int playerZ) {
        if (coreMemory == null || coreMemory.landmarks == null) return null;
        long now = System.currentTimeMillis();

        for (Landmark lm : coreMemory.landmarks) {
            if (!dimension.equals(lm.dimension)) continue;
            double distSq = Math.pow(playerX - lm.x, 2) + Math.pow(playerY - lm.y, 2) + Math.pow(playerZ - lm.z, 2);
            if (distSq <= (double) (lm.radius * lm.radius)) {
                Long lastTriggered = landmarkTriggerCooldowns.get(lm.name);
                if (lastTriggered == null || (now - lastTriggered) > 15 * 60_000L) {
                    landmarkTriggerCooldowns.put(lm.name, now);
                    return lm;
                }
            }
        }
        return null;
    }

    /**
     * 从 L3 历史日记流中按关键词重合度与空间距离检索 Top-K 相关回忆
     */
    public List<EpisodicEntry> retrieveRelevantMemories(String queryText, int playerX, int playerZ, int topK) {
        if (!Files.exists(episodicLogPath)) return List.of();
        try {
            List<String> lines = Files.readAllLines(episodicLogPath, StandardCharsets.UTF_8);
            List<ScoredEntry> scored = new ArrayList<>();

            for (String line : lines) {
                if (line.isBlank()) continue;
                EpisodicEntry entry = COMPACT_GSON.fromJson(line, EpisodicEntry.class);
                double score = 0.0;

                if (entry.tags != null) {
                    for (String tag : entry.tags) {
                        if (queryText.contains(tag)) score += 3.0;
                    }
                }
                if (entry.summary != null) {
                    score += calculateBigramOverlap(queryText, entry.summary) * 2.5;
                }
                if (entry.pos != null && entry.pos.length >= 3) {
                    double dist = Math.hypot(playerX - entry.pos[0], playerZ - entry.pos[2]);
                    if (dist < 64.0) {
                        score += (64.0 - dist) / 32.0;
                    }
                }
                if (score > 0.8) {
                    scored.add(new ScoredEntry(entry, score));
                }
            }

            return scored.stream()
                    .sorted(Comparator.comparingDouble((ScoredEntry s) -> s.score).reversed())
                    .limit(topK)
                    .map(s -> s.entry)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * 组装常驻注入 System Prompt 尾部的 L2 核心档案与关联 L3 日记摘要
     */
    public synchronized String buildMemoryContextBlock(String userQuery, int playerX, int playerZ) {
        StringBuilder sb = new StringBuilder();
        sb.append("【长期核心记忆档案 (L2)】\n");

        if (!coreMemory.playerFacts.isEmpty()) {
            sb.append("- 玩家偏好与事实: ").append(String.join("；", coreMemory.playerFacts)).append("\n");
        }
        if (!coreMemory.activePromises.isEmpty()) {
            sb.append("- 共同约定与待办: ").append(String.join("；", coreMemory.activePromises)).append("\n");
        }
        if (!coreMemory.landmarks.isEmpty()) {
            String lmStr = coreMemory.landmarks.stream()
                    .limit(5)
                    .map(l -> String.format("%s(%s X:%d,Y:%d,Z:%d)", l.name, l.dimension, l.x, l.y, l.z))
                    .collect(Collectors.joining("、"));
            sb.append("- 已标记重要地标: ").append(lmStr).append("\n");
        }

        // 动态检索 L3 历史日记
        List<EpisodicEntry> relatedLogs = retrieveRelevantMemories(userQuery, playerX, playerZ, 3);
        if (!relatedLogs.isEmpty()) {
            sb.append("【唤醒的历史情景日记 (L3)】\n");
            for (EpisodicEntry e : relatedLogs) {
                sb.append("  * [第").append(e.gameDay).append("天] ").append(e.summary).append("\n");
            }
        }

        return sb.toString();
    }

    private double calculateBigramOverlap(String a, String b) {
        if (a == null || b == null || a.length() < 2 || b.length() < 2) return 0.0;
        int hits = 0;
        for (int i = 0; i < a.length() - 1; i++) {
            String sub = a.substring(i, i + 2);
            if (b.contains(sub)) hits++;
        }
        return (double) hits / (a.length() - 1);
    }

    private String sanitizeFileName(String name) {
        if (name == null || name.isBlank()) return "default_world";
        return name.replaceAll("[^a-zA-Z0-9_\\-\\u4e00-\\u9fa5]", "_");
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    // ==================== 数据结构定义 ====================

    public static class CoreMemoryDoc {
        public String worldName = "";
        public String lastPlayedRealTime = "";
        public long lastPlayedGameDay = 0;
        public String lastLogoutSummary = "";
        public List<String> playerFacts = new ArrayList<>();
        public List<String> activePromises = new ArrayList<>();
        public List<Landmark> landmarks = new ArrayList<>();
        public List<String> milestones = new ArrayList<>();
    }

    public static class Landmark {
        public String id;
        public String name;
        public String dimension;
        public int x, y, z;
        public int radius = 32;
        public String description;
    }

    public static class EpisodicEntry {
        public String ts;
        public long gameDay;
        public String dim;
        public int[] pos;
        public List<String> tags;
        public String summary;
    }

    private record ScoredEntry(EpisodicEntry entry, double score) {}
}
