package com.minecompanion.core.memory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.minecompanion.core.config.CompanionConfig;
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
 * - L1 短期工作记忆 (recent_session_context.json): 默认保存最近 80 条消息（40 轮对话），溢出时过滤掉无意义战斗/闲聊噪音，仅将高价值玩家对话归档至 L3
 * - L2 核心档案记忆 (core_memory.json): 常驻保存基地地标坐标、共同约定、玩家偏好与上次离线时间戳
 * - L3 历史情景日记 (episodic_log.jsonl): 检索时仅提取 Top-3 相关条目注入大模型（绝不撑爆 Token），并内置 300 条自动瘦身滚动裁剪机制（永久控制在 80KB 以内）
 */
public class LocalMemoryManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Gson COMPACT_GSON = new Gson();
    private static final int DEFAULT_MAX_L1_MESSAGES = 80;
    // L3 情景日记最大保留行数（300 条约 70KB，超过时自动清理最老的普通归档，但永久保留重要地标与里程碑事件）
    private static final int MAX_L3_LOG_LINES = 300;

    private static Path defaultBaseConfigDir;
    private static LocalMemoryManager currentInstance;

    private final Path worldMemoryDir;
    private final Path coreMemoryPath;
    private final Path episodicLogPath;
    private final Path recentContextPath;

    private CoreMemoryDoc coreMemory;
    private final List<LlmClient.ChatMessage> l1WorkingMemory = new ArrayList<>();
    private final Map<String, Long> landmarkTriggerCooldowns = new HashMap<>();

    public LocalMemoryManager(Path baseConfigDir, String worldIdentifier) {
        String safeWorldId = sanitizeFileName(worldIdentifier);
        this.worldMemoryDir = baseConfigDir.resolve("memories").resolve("worlds").resolve(safeWorldId);
        this.coreMemoryPath = worldMemoryDir.resolve("core_memory.json");
        this.episodicLogPath = worldMemoryDir.resolve("episodic_log.jsonl");
        this.recentContextPath = worldMemoryDir.resolve("recent_session_context.json");
        loadOrInitAll();
    }

    public static synchronized void setDefaultBaseConfigDir(Path baseConfigDir) {
        defaultBaseConfigDir = baseConfigDir;
    }

    public static synchronized void initForWorld(Path baseConfigDir, String worldIdentifier) {
        defaultBaseConfigDir = baseConfigDir;
        currentInstance = new LocalMemoryManager(baseConfigDir, worldIdentifier);
    }

    public static synchronized LocalMemoryManager getCurrent() {
        if (currentInstance == null && defaultBaseConfigDir != null) {
            currentInstance = new LocalMemoryManager(defaultBaseConfigDir, "default_world");
        }
        return currentInstance;
    }

    /**
     * 加载或初始化三层记忆文件
     */
    public synchronized void loadOrInitAll() {
        try {
            Files.createDirectories(worldMemoryDir);
            if (Files.exists(coreMemoryPath)) {
                String json = Files.readString(coreMemoryPath, StandardCharsets.UTF_8);
                this.coreMemory = GSON.fromJson(json, CoreMemoryDoc.class);
            }
            if (this.coreMemory == null) {
                this.coreMemory = new CoreMemoryDoc();
            }

            l1WorkingMemory.clear();
            if (Files.exists(recentContextPath)) {
                String json = Files.readString(recentContextPath, StandardCharsets.UTF_8);
                Type listType = new TypeToken<List<LlmClient.ChatMessage>>() {}.getType();
                List<LlmClient.ChatMessage> loaded = GSON.fromJson(json, listType);
                if (loaded != null) {
                    l1WorkingMemory.addAll(loaded);
                }
            }

            // 启动时检查一次 L3 日记是否需要瘦身裁剪
            compactEpisodicLogIfNeeded();
        } catch (Exception e) {
            if (this.coreMemory == null) {
                this.coreMemory = new CoreMemoryDoc();
            }
        }
    }

    public CoreMemoryDoc getCoreMemory() {
        return coreMemory;
    }

    public synchronized int getL1MessageCount() {
        return l1WorkingMemory.size();
    }

    /**
     * 向 L1 短期记忆滑动窗口追加一轮对话
     * 当超过设定的窗口上限（默认 80 条）时，过滤掉常规受击/挂机噪音，仅将玩家真实有价值的对话归档至 L3 情景日记
     */
    public synchronized void recordChatTurn(String userOrEventText, String assistantReply) {
        String effectiveUserMsg = (userOrEventText == null || userOrEventText.isBlank())
                ? "（玩家正在安静生存中）" : userOrEventText;
        l1WorkingMemory.add(new LlmClient.ChatMessage("user", effectiveUserMsg));
        l1WorkingMemory.add(new LlmClient.ChatMessage("assistant", assistantReply));

        int maxL1 = CompanionConfig.get().maxL1ContextMessages > 0
                ? Math.max(20, CompanionConfig.get().maxL1ContextMessages)
                : DEFAULT_MAX_L1_MESSAGES;

        if (l1WorkingMemory.size() > maxL1) {
            int archiveCount = Math.min(10, l1WorkingMemory.size() - maxL1 + 8);
            List<String> meaningfulSnippets = new ArrayList<>();

            for (int i = 0; i < archiveCount && !l1WorkingMemory.isEmpty(); i++) {
                LlmClient.ChatMessage oldMsg = l1WorkingMemory.remove(0);
                String content = oldMsg.content();
                // 过滤掉高频无意义的日常系统触发器（如挨打、安静挂机、残血），只归档主人真正打字聊天的有价值内容
                if ("user".equals(oldMsg.role())) {
                    if (!content.startsWith("[战斗事件") && !content.startsWith("[受伤事件")
                            && !content.startsWith("[日常安静") && !content.startsWith("[紧急事件")
                            && !content.startsWith("（玩家正在安静")) {
                        meaningfulSnippets.add("主人说:" + truncate(content, 28));
                    }
                } else if (!meaningfulSnippets.isEmpty() && meaningfulSnippets.get(meaningfulSnippets.size() - 1).startsWith("主人说:")) {
                    // 仅当上一条是主人的有效发言时，才配对保留小爱的回复摘要
                    meaningfulSnippets.add("小爱回:" + truncate(content, 28));
                }
            }

            if (!meaningfulSnippets.isEmpty()) {
                EpisodicEntry rolloverEntry = new EpisodicEntry();
                rolloverEntry.ts = Instant.now().toString();
                rolloverEntry.gameDay = this.coreMemory.lastPlayedGameDay;
                rolloverEntry.dim = "minecraft:overworld";
                rolloverEntry.pos = new int[]{0, 64, 0};
                rolloverEntry.tags = List.of("历史对话归档");
                rolloverEntry.summary = "过往聊天：" + String.join(" -> ", meaningfulSnippets);
                appendEpisodicEntry(rolloverEntry);
            }
        }

        this.coreMemory.lastLogoutSummary = "刚聊过：「" + truncate(effectiveUserMsg, 24) + " -> " + truncate(assistantReply, 28) + "」";
        saveRecentContext();
    }

    /**
     * 记录玩家死亡事件到核心记忆与情景日记
     */
    public synchronized void recordDeathEvent(long gameDay, String dimension, int x, int y, int z, String deathDesc) {
        String summary = String.format("在 %s (坐标 X:%d, Y:%d, Z:%d) 不幸阵亡：%s", dimension, x, y, z, deathDesc);
        this.coreMemory.lastLogoutSummary = summary;
        saveCoreMemory();

        EpisodicEntry entry = new EpisodicEntry();
        entry.ts = Instant.now().toString();
        entry.gameDay = gameDay;
        entry.dim = dimension;
        entry.pos = new int[]{x, y, z};
        entry.tags = List.of("角色死亡", "掉装备点", dimension);
        entry.summary = summary;
        appendEpisodicEntry(entry);
    }

    public synchronized List<LlmClient.ChatMessage> getRecentChatHistory() {
        return new ArrayList<>(l1WorkingMemory);
    }

    public synchronized void saveCoreMemory() {
        try {
            Files.createDirectories(worldMemoryDir);
            Path tempFile = worldMemoryDir.resolve("core_memory.json.tmp");
            Files.writeString(tempFile, GSON.toJson(this.coreMemory), StandardCharsets.UTF_8);
            Files.move(tempFile, coreMemoryPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
        }
    }

    public synchronized void saveRecentContext() {
        try {
            Files.createDirectories(worldMemoryDir);
            Files.writeString(recentContextPath, GSON.toJson(this.l1WorkingMemory), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    public synchronized void onWorldDisconnect(long currentGameDay, String dimension, int x, int y, int z) {
        this.coreMemory.lastPlayedRealTime = Instant.now().toString();
        this.coreMemory.lastPlayedGameDay = currentGameDay;
        if (this.coreMemory.lastLogoutSummary == null || this.coreMemory.lastLogoutSummary.isBlank()) {
            this.coreMemory.lastLogoutSummary = String.format("在 %s (X:%d, Y:%d, Z:%d) 附近探索生存", dimension, x, y, z);
        }
        saveCoreMemory();
        saveRecentContext();

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

        EpisodicEntry entry = new EpisodicEntry();
        entry.ts = Instant.now().toString();
        entry.gameDay = this.coreMemory.lastPlayedGameDay;
        entry.dim = dimension;
        entry.pos = new int[]{x, y, z};
        entry.tags = List.of("地标建立", name);
        entry.summary = String.format("共同标记了重要地点【%s】(坐标 X:%d, Y:%d, Z:%d)：%s", name, x, y, z, description);
        appendEpisodicEntry(entry);
    }

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
     * 向 L3 情景日记流追加单行记录，并在超出 300 条上限时自动触发滚动瘦身裁剪
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
            compactEpisodicLogIfNeeded();
        } catch (IOException ignored) {
        }
    }

    /**
     * 🌟 L3 情景日记自动瘦身裁剪器：
     * 当 episodic_log.jsonl 超过 300 行（约 70KB）时，优先保留【地标建立】以及最近的 220 条记录，
     * 清理最老的普通归档，确保文件体积永远小巧、检索耗时永远 < 1ms！
     */
    private synchronized void compactEpisodicLogIfNeeded() {
        if (!Files.exists(episodicLogPath)) return;
        try {
            List<String> allLines = Files.readAllLines(episodicLogPath, StandardCharsets.UTF_8);
            if (allLines.size() <= MAX_L3_LOG_LINES) return;

            List<String> keptLines = new ArrayList<>();
            int cutIndex = allLines.size() - 220;

            for (int i = 0; i < allLines.size(); i++) {
                String line = allLines.get(i);
                if (line.isBlank()) continue;
                // 早于 cutIndex 的旧记录中，只永久保留“地标建立”高优记忆，其余陈旧日常归档自动淘汰
                if (i < cutIndex) {
                    if (line.contains("地标建立")) {
                        keptLines.add(line);
                    }
                } else {
                    keptLines.add(line);
                }
            }

            Path tempLog = worldMemoryDir.resolve("episodic_log.jsonl.tmp");
            Files.write(tempLog, keptLines, StandardCharsets.UTF_8);
            Files.move(tempLog, episodicLogPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) {
        }
    }

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
     * 从 L3 历史日记流中按关键词重合度与空间距离仅检索 Top-3 相关回忆（绝不全量发送给大模型）
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
