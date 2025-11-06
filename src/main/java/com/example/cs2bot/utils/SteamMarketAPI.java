package com.example.cs2bot.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.cdimascio.dotenv.Dotenv;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Random;
import java.util.concurrent.Semaphore;

public class SteamMarketAPI {

    private static final Semaphore limiter = new Semaphore(1);
    private static final Random random = new Random();
    private static final String CSFLOAT_API = "https://api.csfloat.com/api/v1/listings";
    private static String CSFLOAT_KEY;

    static {
        Dotenv dotenv = Dotenv.configure()
                .directory(System.getProperty("user.dir"))
                .ignoreIfMalformed()
                .ignoreIfMissing()
                .load();
        CSFLOAT_KEY = dotenv.get("CSFLOAT_KEY");
        if (CSFLOAT_KEY == null || CSFLOAT_KEY.isBlank()) {
            // fallback if not in .env
            CSFLOAT_KEY = "tXgJgZqb_GA8KQiyBHFPjHkRxO9W2qUZ";
        }
        System.out.println("🔑 Using CSFloat API key: " + CSFLOAT_KEY.substring(0, 6) + "********");
    }

    /**
     * Fetches price and rarity for a CS2 item.
     * Returns formatted string for logs.
     */
    public static double getPriceEUR(String marketHashName) {
        if (marketHashName == null || marketHashName.isBlank()) return 0.0;

        String query = CSFLOAT_API + "?market_hash_name=" + encode(marketHashName);
        int attempts = 0;

        while (attempts < 3) {
            attempts++;
            try {
                limiter.acquire();
                Thread.sleep(200 + random.nextInt(300));

                HttpURLConnection conn = (HttpURLConnection) new URL(query).openConnection();
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (CS2PriceBot)");
                conn.setRequestProperty("Authorization", "Bearer " + CSFLOAT_KEY);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                int code = conn.getResponseCode();

                if (code == 429) {
                    int wait = 6000 + random.nextInt(4000);
                    System.err.printf("[CSFloatAPI] ⚠️  429 Rate limit hit for %s — waiting %ds%n",
                            marketHashName, wait / 1000);
                    Thread.sleep(wait);
                    continue;
                }

                if (code != 200) {
                    System.err.printf("[CSFloatAPI] ⚠️  HTTP %d for %s%n", code, marketHashName);
                    return 0.0;
                }

                JsonObject json = JsonParser.parseReader(new InputStreamReader(conn.getInputStream())).getAsJsonObject();

                if (!json.has("listings") || !json.get("listings").isJsonArray()
                        || json.getAsJsonArray("listings").size() == 0) {
                    System.err.printf("[CSFloatAPI] ⚠️  No listings for %s%n", marketHashName);
                    return 0.0;
                }

                JsonObject firstListing = json.getAsJsonArray("listings").get(0).getAsJsonObject();
                double priceUsd = firstListing.get("price").getAsDouble() / 100.0;
                double eur = priceUsd * 0.93;

                // 🧩 Get rarity using local schema
                String rarity = SteamSchemaAPI.getRarity(marketHashName);

                System.out.printf("[CSFloatAPI] ✅ %s → %.2f EUR (%s)%n", marketHashName, eur, rarity);
                return eur;

            } catch (Exception e) {
                System.err.printf("[CSFloatAPI] ❌ Error fetching %s: %s%n", marketHashName, e.getMessage());
                try {
                    Thread.sleep(2000 + random.nextInt(2000));
                } catch (InterruptedException ignored) {}
            } finally {
                new Thread(() -> {
                    try {
                        Thread.sleep(1000);
                        limiter.release();
                    } catch (InterruptedException ignored) {}
                }).start();
            }
        }

        System.err.printf("[CSFloatAPI] ⚠️  Skipped %s after 3 failed attempts.%n", marketHashName);
        return 0.0;
    }

    private static String encode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }
}