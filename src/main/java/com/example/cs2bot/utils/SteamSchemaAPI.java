package com.example.cs2bot.utils;

import io.github.cdimascio.dotenv.Dotenv;
import java.util.HashMap;
import java.util.Map;

public class SteamSchemaAPI {

    private static final Map<String, String> rarities = new HashMap<>();
    private static String STEAM_API_KEY;

    public static void loadRarities() {
        try {
            Dotenv dotenv = Dotenv.configure()
                    .directory(System.getProperty("user.dir"))
                    .ignoreIfMalformed()
                    .ignoreIfMissing()
                    .load();

            STEAM_API_KEY = dotenv.get("STEAM_API_KEY");

            System.out.println("🔑 Using Steam API Key: " +
                    (STEAM_API_KEY != null && !STEAM_API_KEY.isBlank()
                            ? STEAM_API_KEY.substring(0, 6) + "********"
                            : "❌ None (using local rarities)"));

            // 🚨 Steam API schema removed — using local rarity map
            setupLocalRarities();

            System.out.println("✅ Loaded " + rarities.size() + " local item rarity rules.");

        } catch (Exception e) {
            System.err.println("❌ SteamSchemaAPI Error: " + e.getMessage());
            setupLocalRarities();
        }
    }

    private static void setupLocalRarities() {
        rarities.put("★", "Covert");               // Knives / Gloves
        rarities.put("Knife", "Covert");
        rarities.put("Gloves", "Extraordinary");
        rarities.put("AWP", "Covert");
        rarities.put("AK-47", "Classified");
        rarities.put("M4A4", "Classified");
        rarities.put("M4A1-S", "Classified");
        rarities.put("Desert Eagle", "Restricted");
        rarities.put("USP-S", "Restricted");
        rarities.put("Five-SeveN", "Restricted");
        rarities.put("Glock-18", "Mil-Spec");
        rarities.put("P250", "Mil-Spec");
        rarities.put("MP9", "Mil-Spec");
        rarities.put("MP7", "Mil-Spec");
        rarities.put("P90", "Restricted");
        rarities.put("FAMAS", "Mil-Spec");
        rarities.put("AUG", "Restricted");
        rarities.put("SCAR-20", "Mil-Spec");
        rarities.put("G3SG1", "Mil-Spec");
        rarities.put("Nova", "Mil-Spec");
        rarities.put("XM1014", "Restricted");
        rarities.put("Sawed-Off", "Mil-Spec");
        rarities.put("MAC-10", "Mil-Spec");
        rarities.put("Tec-9", "Mil-Spec");
        rarities.put("CZ75-Auto", "Mil-Spec");
    }

    public static String getRarity(String name) {
        if (name == null || name.isBlank()) return "Unknown";

        for (Map.Entry<String, String> entry : rarities.entrySet()) {
            if (name.contains(entry.getKey())) return entry.getValue();
        }

        return "Unknown";
    }
}
