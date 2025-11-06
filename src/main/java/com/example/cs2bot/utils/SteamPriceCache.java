package com.example.cs2bot.utils;

import com.example.cs2bot.db.MongoUtil;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import org.bson.Document;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SteamPriceCache {

    private static final Map<String, Double> localCache = new ConcurrentHashMap<>();
    private static final MongoCollection<Document> dbCache =
            MongoUtil.getDB().getCollection("price_cache");

    // Cache validity in seconds (24h)
    private static final long CACHE_TTL = 24 * 60 * 60;

    /** Get cached price (memory -> MongoDB) */
    public static Double get(String name) {
        if (name == null || name.isBlank()) return null;

        // 1️⃣ Check in-memory cache
        if (localCache.containsKey(name)) {
            return localCache.get(name);
        }

        // 2️⃣ Check MongoDB cache
        Document doc = dbCache.find(Filters.eq("_id", name)).first();
        if (doc == null) return null;

        long timestamp = doc.getLong("timestamp");
        if (Instant.now().getEpochSecond() - timestamp > CACHE_TTL) {
            // expired
            return null;
        }

        Double price = doc.getDouble("price");
        if (price != null) {
            localCache.put(name, price);
            System.out.printf("[SteamPriceCache] 💾 Loaded cached price for %s: €%.2f%n", name, price);
        }
        return price;
    }

    /** Save price to memory and MongoDB */
    public static void put(String name, double price) {
        if (name == null || name.isBlank() || price <= 0) return;

        localCache.put(name, price);

        Document doc = new Document("_id", name)
                .append("price", price)
                .append("timestamp", Instant.now().getEpochSecond());

        dbCache.replaceOne(Filters.eq("_id", name), doc, new com.mongodb.client.model.ReplaceOptions().upsert(true));

        System.out.printf("[SteamPriceCache] ✅ Saved %s -> €%.2f%n", name, price);
    }
}
