package com.example.cs2bot;

import com.example.cs2bot.db.MongoUtil;
import com.example.cs2bot.listeners.ButtonListener;
import com.example.cs2bot.listeners.SlashCommandListener;
import com.example.cs2bot.utils.PriceUpdater;
import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.requests.GatewayIntent;

public class Main {
    public static void main(String[] args) {
        // 🔧 Load .env file
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

        String token = dotenv.get("BOT_TOKEN");
        String mongoUri = dotenv.get("MONGO_URI");
        String steamKey = dotenv.get("STEAM_API_KEY");

        System.out.println("🔧 Environment check:");
        System.out.println(" - BOT_TOKEN loaded? " + (token != null && !token.isBlank()));
        System.out.println(" - MONGO_URI loaded? " + (mongoUri != null && !mongoUri.isBlank()));
        System.out.println(" - STEAM_API_KEY: " + (steamKey != null ? steamKey : "❌ MISSING"));

        // 🧩 Connect to MongoDB
        MongoUtil.init(mongoUri, "cs2_case_bot");

        // 💬 Setup Discord bot
        try {
            JDABuilder.createDefault(token)
                    .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                    .setStatus(OnlineStatus.ONLINE)
                    .addEventListeners(
                            new SlashCommandListener(), // /case, /inventory, /refreshprices, etc.
                            new ButtonListener()         // case open button interactions
                    )
                    .build();

            System.out.println("✅ Bot started successfully.");

        } catch (Exception e) {
            System.err.println("❌ Failed to start bot: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        // 💸 Start the price updater in the background
        try {
            // Load refresh interval from .env or default to 300000 ms (5 minutes)
            int refreshInterval = 300000; // 5 minutes
            String envValue = dotenv.get("PRICE_REFRESH_MS");

            if (envValue != null && !envValue.isBlank()) {
                try {
                    refreshInterval = Integer.parseInt(envValue.trim());
                } catch (NumberFormatException ignored) {
                    System.err.println("⚠️ Invalid PRICE_REFRESH_MS in .env, using default 300000 ms (5 min)");
                }
            }

            System.out.printf("🌀 Starting single-threaded PriceUpdater (%d ms delay between calls)%n", refreshInterval);
            new Thread(new PriceUpdater(refreshInterval, 1, 0)).start();

        } catch (Exception e) {
            System.err.println("❌ Failed to start PriceUpdater: " + e.getMessage());
        }
    }
}
