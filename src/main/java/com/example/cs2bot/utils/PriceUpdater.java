package com.example.cs2bot.utils;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import io.github.cdimascio.dotenv.Dotenv;
import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOneModel;
import com.mongodb.client.model.ReplaceOptions;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.brotli.BrotliInterceptor;
import org.bson.Document;
import org.brotli.dec.BrotliInputStream;

import java.net.ProxySelector;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPInputStream;
import java.nio.charset.StandardCharsets;

public class PriceUpdater implements Runnable {

    private static final String SKINPORT_DIRECT_URL =
            "https://api.skinport.com/v1/items?app_id=730&currency=EUR&tradable=1";

    private static final String SKINPORT_PROXY_URL =
            "https://api.allorigins.win/raw?url=https://api.skinport.com/v1/items?app_id=730&currency=EUR&tradable=1";

    private static MongoCollection<Document> priceCollection;

    private static final Map<String, Double> skinportMap = new ConcurrentHashMap<>();
    private static volatile long skinportLastLoad = 0L;
    private static final long SKINPORT_TTL_MS = 10 * 60 * 1000; // 10 min cache

    private static final OkHttpClient httpClient;

    static {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .proxySelector(ProxySelector.getDefault())
                .connectTimeout(java.time.Duration.ofSeconds(60))
                .readTimeout(java.time.Duration.ofSeconds(120))
                .retryOnConnectionFailure(true);

        try {
            builder.addInterceptor(BrotliInterceptor.INSTANCE);
            System.out.println("🌐 BrotliInterceptor loaded: true");
        } catch (Throwable t) {
            System.err.println("[PriceUpdater] ⚠️ Failed to load BrotliInterceptor: " + t.getMessage());
        }

        httpClient = builder.build();

        try {
            Dotenv dotenv = Dotenv.configure()
                    .directory(System.getProperty("user.dir"))
                    .ignoreIfMissing()
                    .ignoreIfMalformed()
                    .load();

            String mongoUri = dotenv.get("MONGO_URI");
            if (mongoUri != null && !mongoUri.isBlank()) {
                MongoClient client = MongoClients.create(mongoUri);
                MongoDatabase db = client.getDatabase("cs2_case_bot");
                priceCollection = db.getCollection("prices");
                System.out.println("🗄️ Connected to MongoDB for price cache");
            }

            System.out.println("🌐 System proxy support enabled (matches Chrome)");

        } catch (Exception e) {
            System.err.println("[PriceUpdater] ⚠️ Could not initialize MongoDB or .env: " + e.getMessage());
        }
    }

    private final int refreshInterval;
    private final int totalThreads;
    private final int threadIndex;

    public PriceUpdater(int refreshInterval, int totalThreads, int threadIndex) {
        this.refreshInterval = refreshInterval;
        this.totalThreads = totalThreads;
        this.threadIndex = threadIndex;
    }

    public PriceUpdater() {
        this(600, 1, 0);
    }

    @Override
    public void run() {
        try {
            System.out.printf("[Thread-%d] 🌀 Starting price updater (interval=%d ms)%n",
                    threadIndex, refreshInterval);

            while (true) {
                loadSkinportIfStale();
                Thread.sleep(refreshInterval);
            }

        } catch (InterruptedException e) {
            System.err.printf("[Thread-%d] ⚠️ Interrupted%n", threadIndex);
        } catch (Exception e) {
            System.err.printf("[Thread-%d] ❌ Unexpected error: %s%n", threadIndex, e.getMessage());
        }
    }

    private static void loadSkinportIfStale() {
        long now = Instant.now().toEpochMilli();
        if (now - skinportLastLoad < SKINPORT_TTL_MS && !skinportMap.isEmpty()) return;

        boolean usedProxy = false;

        try {
            Request request = new Request.Builder()
                    .url(SKINPORT_DIRECT_URL)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36")
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Accept-Encoding", "gzip, deflate, br")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Origin", "https://skinport.com")
                    .header("Referer", "https://skinport.com/")
                    .header("Connection", "keep-alive")
                    .header("Cache-Control", "no-cache")
                    .build();

            Response response = httpClient.newCall(request).execute();
            int code = response.code();

            if (code == 429 || code == 406 || code == 403) {
                System.err.println("[PriceUpdater] ⚠️ Skinport direct access blocked — using proxy...");
                usedProxy = true;
                response.close();

                request = new Request.Builder()
                        .url(SKINPORT_PROXY_URL)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36")
                        .header("Accept", "application/json, text/plain, */*")
                        .header("Accept-Encoding", "gzip, deflate, br")
                        .build();

                response = httpClient.newCall(request).execute();
            }

            ResponseBody body = response.body();
            if (body == null) {
                System.err.println("[PriceUpdater] ⚠️ Empty Skinport response");
                response.close();
                return;
            }

            // 🧩 Universal decompression and cleanup
            byte[] rawBytes = body.bytes();
            String jsonString = new String(rawBytes, StandardCharsets.UTF_8).trim();

            // Detect binary data and try Brotli/GZIP manually
            if (!jsonString.startsWith("[") && !jsonString.startsWith("{") &&
                    jsonString.chars().filter(c -> c < 32).count() > 10) {
                try {
                    String encoding = response.header("Content-Encoding", "").toLowerCase();

                    if (encoding.contains("br") || jsonString.contains(" ")) {
                        ByteArrayInputStream in = new ByteArrayInputStream(rawBytes);
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        BrotliInputStream brotli = new BrotliInputStream(in);
                        brotli.transferTo(out);
                        brotli.close();
                        jsonString = out.toString(StandardCharsets.UTF_8);
                        System.out.println("[PriceUpdater] ✅ Brotli decompressed manually");
                    } else if (encoding.contains("gzip")) {
                        ByteArrayInputStream in = new ByteArrayInputStream(rawBytes);
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        GZIPInputStream gzip = new GZIPInputStream(in);
                        gzip.transferTo(out);
                        gzip.close();
                        jsonString = out.toString(StandardCharsets.UTF_8);
                        System.out.println("[PriceUpdater] ✅ GZIP decompressed manually");
                    }
                } catch (Exception ex) {
                    System.err.println("[PriceUpdater] ⚠️ Manual decompression failed: " + ex.getMessage());
                }
            }

            // Extract proxy wrapper
            if (jsonString.startsWith("{") && jsonString.contains("\"contents\"")) {
                try {
                    JsonObject wrapper = JsonParser.parseString(jsonString).getAsJsonObject();
                    if (wrapper.has("contents")) {
                        jsonString = wrapper.get("contents").getAsString();
                        System.out.println("[PriceUpdater] 🪞 Extracted AllOrigins 'contents' wrapper");
                    }
                } catch (Exception ignored) {}
            }

            // Trim junk before JSON array
            if (!jsonString.startsWith("[") && jsonString.contains("[")) {
                jsonString = jsonString.substring(jsonString.indexOf('['));
            }
            jsonString = jsonString.trim();

            // ✅ Parse JSON safely
            JsonReader reader = new JsonReader(new java.io.StringReader(jsonString));
            reader.setLenient(true);
            JsonElement parsed;

            try {
                parsed = JsonParser.parseReader(reader);
            } catch (Exception parseEx) {
                System.err.println("[PriceUpdater] ⚠️ JSON parse issue: " + parseEx.getMessage());
                System.err.println("Snippet: " + jsonString.substring(0, Math.min(200, jsonString.length())));
                response.close();
                return;
            }

            if (!parsed.isJsonArray()) {
                System.err.println("[PriceUpdater] ⚠️ Unexpected response (not JSON array): " +
                        jsonString.substring(0, Math.min(120, jsonString.length())));
                response.close();
                return;
            }

            JsonArray arr = parsed.getAsJsonArray();
            Map<String, Double> temp = new HashMap<>();
            Map<String, Double> changed = new HashMap<>();

            for (JsonElement el : arr) {
                JsonObject o = el.getAsJsonObject();
                if (!o.has("market_hash_name")) continue;

                String name = o.get("market_hash_name").getAsString();
                String n = normalizeName(name);
                double price = safeDouble(o, "lowest_price");
                if (price <= 0) price = safeDouble(o, "min_price");
                if (price <= 0) continue;

                temp.put(n, price);
                if (hasPriceChanged(n, price)) {
                    changed.put(n, price);
                }
            }

            if (!changed.isEmpty()) {
                batchUpdatePrices(changed);
                System.out.printf("[Mongo] 💾 Updated %d changed prices%n", changed.size());
            }

            if (!temp.isEmpty()) {
                skinportMap.clear();
                skinportMap.putAll(temp);
                skinportLastLoad = now;
                System.out.printf("[PriceUpdater] ✅ Loaded %d Skinport prices (%s)%n",
                        temp.size(), usedProxy ? "via proxy" : "direct");
            }

            response.close();

        } catch (Exception e) {
            System.err.println("[PriceUpdater] ⚠️ Skinport fetch issue: " + e.getMessage());
        }
    }

    private static boolean hasPriceChanged(String name, double newPrice) {
        if (priceCollection == null) return false;
        try {
            Document existing = priceCollection.find(Filters.eq("_id", name)).first();
            if (existing == null || !existing.containsKey("price")) return true;
            Double oldPrice = existing.getDouble("price");
            if (oldPrice == null) return true;
            return Math.abs(oldPrice - newPrice) >= 0.01;
        } catch (Exception e) {
            return true;
        }
    }

    private static void batchUpdatePrices(Map<String, Double> changed) {
        if (priceCollection == null || changed.isEmpty()) return;
        List<ReplaceOneModel<Document>> ops = new ArrayList<>();

        for (Map.Entry<String, Double> entry : changed.entrySet()) {
            Document doc = new Document("_id", entry.getKey())
                    .append("price", entry.getValue())
                    .append("updated", new Date());
            ops.add(new ReplaceOneModel<>(
                    Filters.eq("_id", entry.getKey()),
                    doc,
                    new ReplaceOptions().upsert(true)
            ));
        }

        try {
            priceCollection.bulkWrite(ops);
        } catch (Exception e) {
            System.err.println("[PriceUpdater] ⚠️ Mongo bulk update failed: " + e.getMessage());
        }
    }

    private static double safeDouble(JsonObject o, String key) {
        try {
            return o.get(key).getAsDouble();
        } catch (Exception e) {
            return 0.0;
        }
    }

    private static String normalizeName(String name) {
        if (name == null) return "";
        String n = name.trim();
        if (n.startsWith("? ")) n = "★ " + n.substring(2);
        if (n.startsWith("?")) n = "★ " + n.substring(1).trim();
        if ((n.contains("Gloves") || n.contains("Knife") || n.contains("Hand Wraps"))
                && !n.startsWith("★ ")) {
            n = "★ " + n;
        }
        return n;
    }
}
