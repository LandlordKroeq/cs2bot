package com.example.cs2bot.commands;

import com.example.cs2bot.db.MongoUtil;
import com.mongodb.client.MongoCollection;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.bson.Document;

import java.awt.*;
import java.util.List;

public class InventoryCommand extends ListenerAdapter {
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("inventory")) return;

        // ✅ Use the correct collection name: "inventory"
        MongoCollection<Document> inventory = MongoUtil.getDB().getCollection("inventory");

        String userId = event.getUser().getId();

        // ✅ Find all items owned by this user, sorted newest first
        List<Document> items = inventory.find(new Document("user_id", userId))
                .sort(new Document("opened_at", -1))
                .limit(10)
                .into(new java.util.ArrayList<>());

        if (items.isEmpty()) {
            event.reply("📦 You have no items in your inventory yet! Try opening a case.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(event.getUser().getName() + "'s Inventory 🎒")
                .setColor(Color.ORANGE);

        StringBuilder desc = new StringBuilder();

        for (Document item : items) {
            String name = item.getString("name");
            String rarity = item.getString("rarity");
            String wear = item.getString("wear");
            Double price = item.getDouble("price");
            Double fl = item.getDouble("float");

            desc.append(String.format(
                    "🎯 **%s** (%s)\n⭐ %s | 💧 Float: %.4f | 💶 €%.2f\n\n",
                    name != null ? name : "Unknown",
                    wear != null ? wear : "Unknown",
                    rarity != null ? rarity : "Unknown",
                    fl != null ? fl : 0.0,
                    price != null ? price : 0.0
            ));
        }

        embed.setDescription(desc.toString());
        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }
}
