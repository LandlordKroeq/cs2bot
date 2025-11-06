package com.example.cs2bot.listeners;

import com.example.cs2bot.db.MongoUtil;
import com.example.cs2bot.models.User;
import com.example.cs2bot.utils.PriceUpdater;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.bson.Document;

public class SlashCommandListener extends ListenerAdapter {

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        switch (event.getName()) {
            case "case" -> handleCaseCommand(event);
            case "inventory" -> handleInventoryCommand(event);
            case "trade" -> handleTradeCommand(event);
            case "refreshprices" -> handleRefreshPrices(event);
        }
    }

    private void handleCaseCommand(SlashCommandInteractionEvent event) {
        var embed = new net.dv8tion.jda.api.EmbedBuilder()
                .setTitle("🎁 CS2 Case Menu")
                .setDescription("Choose a case to open below! Each case contains random skins.")
                .setColor(0x00BFFF)
                .build();

        var buttons = net.dv8tion.jda.api.interactions.components.ActionRow.of(
                net.dv8tion.jda.api.interactions.components.buttons.Button.primary("open_prisma2", "🎨 Prisma 2 Case"),
                net.dv8tion.jda.api.interactions.components.buttons.Button.primary("open_revolution", "⚡ Revolution Case"),
                net.dv8tion.jda.api.interactions.components.buttons.Button.primary("open_dreams", "💤 Dreams & Nightmares")
        );

        event.replyEmbeds(embed)
                .addActionRow(buttons.getButtons())
                .queue();
    }

    private void handleInventoryCommand(SlashCommandInteractionEvent event) {
        MongoCollection<Document> users = MongoUtil.getDB().getCollection("users");
        Document userDoc = users.find(Filters.eq("_id", event.getUser().getId())).first();
        if (userDoc == null) {
            event.reply("You have no items in your inventory.").setEphemeral(true).queue();
            return;
        }
        User user = new User(userDoc);
        event.reply("You own " + user.inventory.size() + " item(s).").setEphemeral(true).queue();
    }

    private void handleTradeCommand(SlashCommandInteractionEvent event) {
        event.reply("Trade system coming soon!").setEphemeral(true).queue();
    }

    private void handleRefreshPrices(SlashCommandInteractionEvent event) {
        if (event.getMember() == null || !event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            event.reply("You need **Manage Server** to run this command.").setEphemeral(true).queue();
            return;
        }

        int numThreads = 10;
        for (int i = 0; i < numThreads; i++) {
            new Thread(new PriceUpdater(350, numThreads, i)).start();
        }

        event.reply("⏳ Price refresh started. Check console logs for progress.").setEphemeral(true).queue();
    }
}