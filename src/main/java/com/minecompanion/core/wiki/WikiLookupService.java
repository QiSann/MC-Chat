package com.minecompanion.core.wiki;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自主联网百科检索与本地缓存服务 (WikiLookupService)
 * 负责在玩家询问复杂模组机制、BOSS 打法、多方块结构或物品攻略时，
 * 优先检索本地缓存 (wiki_cache.json)，未命中则异步检索：
 * 1. 中文 Minecraft Wiki (zh.minecraft.wiki MediaWiki API)
 * 2. MC百科 (search.mcmod.cn Jsoup 摘要提取)
 * 3. Modrinth 官方模组库 API (api.modrinth.com/v2/search)
 */
public class WikiLookupService {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static Path cacheFilePath;
    private static final Map<String, String> WIKI_CACHE = new ConcurrentHashMap<>();

    /**
     * 初始化本地百科缓存文件 (.minecraft/config/minecompanion/memories/wiki_cache.json)
     */
    public static synchronized void initCache(Path baseConfigDir) {
        try {
            Path memoriesDir = baseConfigDir.resolve("memories");
            Files.createDirectories(memoriesDir);
            cacheFilePath = memoriesDir.resolve("wiki_cache.json");
            if (Files.exists(cacheFilePath)) {
                String json = Files.readString(cacheFilePath, StandardCharsets.UTF_8);
                Type mapType = new TypeToken<Map<String, String>>() {}.getType();
                Map<String, String> loaded = GSON.fromJson(json, mapType);
                if (loaded != null) {
                    WIKI_CACHE.clear();
                    WIKI_CACHE.putAll(loaded);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static synchronized void saveCache() {
        if (cacheFilePath == null) return;
        try {
            Files.writeString(cacheFilePath, GSON.toJson(WIKI_CACHE), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    /**
     * 异步检索外部百科（优先命中本地缓存，0 延迟；未命中则联网检索并自动写入缓存）
     */
    public static CompletableFuture<String> searchWikiAsync(String queryKeyword) {
        String normalizedKey = queryKeyword == null ? "" : queryKeyword.trim().toLowerCase();
        if (normalizedKey.isEmpty()) {
            return CompletableFuture.completedFuture("未提供有效的百科检索词条。");
        }

        // 1. 优先检查本地持久化缓存
        if (WIKI_CACHE.containsKey(normalizedKey)) {
            return CompletableFuture.completedFuture("【本地百科缓存命中 (" + queryKeyword + ")】\n" + WIKI_CACHE.get(normalizedKey));
        }

        // 2. 后台守护线程异步联网检索
        return CompletableFuture.supplyAsync(() -> {
            StringBuilder aggregated = new StringBuilder();

            // 渠道 A：中文 Minecraft Wiki 官方 MediaWiki 接口
            String zhWikiResult = queryZhMinecraftWiki(queryKeyword);
            if (!zhWikiResult.isBlank()) {
                aggregated.append("【中文 Minecraft Wiki 检索摘要】\n").append(zhWikiResult).append("\n");
            }

            // 渠道 B：MC百科 (mcmod.cn) 搜索摘要解析
            String mcmodResult = queryMcmodCn(queryKeyword);
            if (!mcmodResult.isBlank()) {
                aggregated.append("【MC百科 (mcmod.cn) 模组资料摘要】\n").append(mcmodResult).append("\n");
            }

            // 渠道 C：若前两者均无结果，尝试 Modrinth 官方 API
            if (aggregated.isEmpty()) {
                String modrinthResult = queryModrinthApi(queryKeyword);
                if (!modrinthResult.isBlank()) {
                    aggregated.append("【Modrinth 官方模组库摘要】\n").append(modrinthResult).append("\n");
                }
            }

            if (aggregated.isEmpty()) {
                return "【百科检索提示】未在外部百科中找到「" + queryKeyword + "」的专门词条，请结合游戏内已提取的 Tooltip 与配方数据进行解答。";
            }

            String finalSnippet = truncate(aggregated.toString().trim(), 700);
            WIKI_CACHE.put(normalizedKey, finalSnippet);
            saveCache();
            return finalSnippet;
        });
    }

    /**
     * 渠道 A：查询中文 Minecraft Wiki (MediaWiki API)
     */
    private static String queryZhMinecraftWiki(String keyword) {
        try {
            String encoded = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            String url = "https://zh.minecraft.wiki/api.php?action=query&list=search&srsearch="
                    + encoded + "&srlimit=3&utf8=1&format=json";

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "MineCompanion-Mod/1.0 (Minecraft 1.20.1 AI Companion)")
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() == 200) {
                JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
                JsonObject query = root.getAsJsonObject("query");
                if (query != null && query.has("search")) {
                    JsonArray searchArr = query.getAsJsonArray("search");
                    StringBuilder sb = new StringBuilder();
                    for (JsonElement el : searchArr) {
                        JsonObject item = el.getAsJsonObject();
                        String title = item.has("title") ? item.get("title").getAsString() : "";
                        String snippet = item.has("snippet") ? item.get("snippet").getAsString()
                                .replaceAll("<[^>]*>", "")
                                .replace("&quot;", "\"")
                                .replace("&amp;", "&") : "";
                        if (!title.isEmpty() && !snippet.isEmpty()) {
                            sb.append("- 《").append(title).append("》: ").append(snippet).append("\n");
                        }
                    }
                    return sb.toString().trim();
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    /**
     * 渠道 B：通过 Jsoup 检索 MC百科 (search.mcmod.cn) 词条摘要
     */
    private static String queryMcmodCn(String keyword) {
        try {
            String encoded = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            String searchUrl = "https://search.mcmod.cn/s?key=" + encoded;

            Document doc = Jsoup.connect(searchUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) MineCompanion/1.0")
                    .timeout(5000)
                    .get();

            Elements items = doc.select(".result-item-list . search-result-item, .result-item");
            if (items.isEmpty()) {
                items = doc.select(".search-result-list > div");
            }

            StringBuilder sb = new StringBuilder();
            int count = 0;
            for (Element item : items) {
                String head = item.select(".head, h3, a").first() != null
                        ? item.select(".head, h3, a").first().text().trim() : "";
                String body = item.select(".body, p").text().trim();
                if (!head.isEmpty() && !body.isEmpty()) {
                    sb.append("- ").append(head).append("：").append(truncate(body, 160)).append("\n");
                    count++;
                    if (count >= 2) break;
                }
            }
            return sb.toString().trim();
        } catch (Exception ignored) {
        }
        return "";
    }

    /**
     * 渠道 C：查询 Modrinth 官方 API
     */
    private static String queryModrinthApi(String keyword) {
        try {
            String encoded = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            String url = "https://api.modrinth.com/v2/search?query=" + encoded + "&limit=2";

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "MineCompanion-Mod/1.0")
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() == 200) {
                JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
                JsonArray hits = root.getAsJsonArray("hits");
                if (hits != null && !hits.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (JsonElement el : hits) {
                        JsonObject hit = el.getAsJsonObject();
                        String title = hit.has("title") ? hit.get("title").getAsString() : "";
                        String desc = hit.has("description") ? hit.get("description").getAsString() : "";
                        sb.append("- ").append(title).append(": ").append(desc).append("\n");
                    }
                    return sb.toString().trim();
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private static String truncate(String text, int maxLen) {
        if (text == null || text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
    }
}
