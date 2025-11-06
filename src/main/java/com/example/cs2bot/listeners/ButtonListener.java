package com.example.cs2bot.listeners;

import com.example.cs2bot.db.MongoUtil;
import com.mongodb.client.MongoCollection;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.bson.Document;

import java.awt.*;
import java.util.Date;
import java.util.Random;

public class ButtonListener extends ListenerAdapter {

    private final Random random = new Random();

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();

        switch (id) {
            case "get_key" -> event.reply("🗝️ You received a key! Use it to open a case.")
                    .setEphemeral(true).queue();

            case "open_case", "open_prisma2", "open_revolution", "open_dreams" -> {
                String caseName = switch (id) {
                    case "open_prisma2" -> "🎨 Prisma 2 Case";
                    case "open_revolution" -> "⚡ Revolution Case";
                    case "open_dreams" -> "💤 Dreams & Nightmares Case";
                    default -> "Mystery Case";
                };

                MongoCollection<Document> skins = MongoUtil.getDB().getCollection("skins");
                MongoCollection<Document> prices = MongoUtil.getDB().getCollection("prices");
                MongoCollection<Document> inventory = MongoUtil.getDB().getCollection("inventory");

                long count = skins.countDocuments();
                if (count == 0) {
                    event.reply("⚠️ No skins available in the database!")
                            .setEphemeral(true).queue();
                    return;
                }

                int randomIndex = random.nextInt((int) count);
                Document skin = skins.find().skip(randomIndex).first();
                if (skin == null) {
                    event.reply("⚠️ Error fetching skin.")
                            .setEphemeral(true).queue();
                    return;
                }

                // ✅ Safe data extraction
                String name = safeString(skin, "name");
                String wear = safeString(skin, "wear");
                String rarity = safeString(skin, "rarity");
                String image = safeString(skin, "image");
                double wearFloat = skin.containsKey("float") ? safeDouble(skin, "float") : 0.0;

                // ✅ Try to get live price from PriceUpdater
                double price = safeDouble(skin, "price"); // fallback
                String normalized = normalizeName(name);

                Document priceDoc = prices.find(new Document("_id", normalized)).first();
                if (priceDoc == null) {
                    // Try again without the ★ prefix (fallback)
                    String relaxed = normalized.replace("★", "").trim();
                    priceDoc = prices.find(new Document("_id", relaxed)).first();
                }

                if (priceDoc != null && priceDoc.containsKey("price")) {
                    price = safeDouble(priceDoc, "price");
                }

                // 🧹 Clean up
                if (name != null) name = name.replace("?", "★").trim();
                if (rarity == null || rarity.isBlank()) rarity = "Unknown";

                Color embedColor = switch (rarity) {
                    case "Consumer Grade" -> new Color(211, 211, 211);
                    case "Industrial Grade" -> new Color(94, 152, 217);
                    case "Mil-Spec" -> new Color(75, 105, 255);
                    case "Restricted" -> new Color(136, 71, 255);
                    case "Classified" -> new Color(211, 44, 230);
                    case "Covert" -> new Color(235, 75, 75);
                    case "Extraordinary" -> new Color(255, 215, 0);
                    default -> Color.WHITE;
                };

                // 🧾 Save to user’s inventory
                Document item = new Document("user_id", event.getUser().getId())
                        .append("case", caseName)
                        .append("name", name)
                        .append("wear", wear)
                        .append("rarity", rarity)
                        .append("price", price)
                        .append("float", wearFloat)
                        .append("image", image)
                        .append("opened_at", new Date());

                inventory.insertOne(item);

                // 🎁 Build embed message
                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle("🎁 You opened a " + caseName + "!")
                        .setDescription("You unboxed a **" + rarity + "** skin:\n\n" +
                                "🪙 **" + name + "** (" + wear + ")\n" +
                                "💶 Price: €" + String.format("%.2f", price) + "\n" +
                                "🧮 Float: " + String.format("%.4f", wearFloat) + "\n\n" +
                                "📦 Added to your inventory ✅")
                        .setColor(embedColor);

                if (image != null && !image.isBlank())
                    embed.setThumbnail(image);

                event.replyEmbeds(embed.build()).queue();
            }

            case "inventory" -> {
                MongoCollection<Document> inventory = MongoUtil.getDB().getCollection("inventory");
                var items = inventory.find(new Document("user_id", event.getUser().getId()))
                        .limit(10)
                        .into(new java.util.ArrayList<>());

                if (items.isEmpty()) {
                    event.reply("📦 Your inventory is empty! Open some cases first.")
                            .setEphemeral(true).queue();
                    return;
                }

                StringBuilder sb = new StringBuilder("🎒 **Your Inventory:**\n\n");
                for (Document item : items) {
                    sb.append("• ").append(item.getString("name"))
                            .append(" — ").append(item.getString("rarity"))
                            .append(" (€").append(String.format("%.2f",
                                    item.getDouble("price") != null ? item.getDouble("price") : 0.0))
                            .append(")\n");
                }

                event.reply(sb.toString()).setEphemeral(true).queue();
            }

            case "trade" ->
                    event.reply("💱 Starting trade... (coming soon!)").setEphemeral(true).queue();

            default -> event.reply("⚠️ Unknown button action: `" + id + "`")
                    .setEphemeral(true).queue();
        }
    }

    private String safeString(Document doc, String key) {
        if (doc == null || !doc.containsKey(key)) return "Unknown";
        String value = doc.getString(key);
        return (value != null && !value.isBlank()) ? value : "Unknown";
    }

    private double safeDouble(Document doc, String key) {
        try {
            Object val = doc.get(key);
            if (val instanceof Number) {
                return ((Number) val).doubleValue();
            }
        } catch (Exception ignored) {}
        return 0.0;
    }

    private String normalizeName(String name) {
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
