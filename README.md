# CS2 Java Bot (JDA + MongoDB) - Scaffold

## What this package contains
- A Maven Java project using JDA (Discord) + MongoDB sync driver.
- Slash commands: `/case`, `/inventory`, `/trade` (basic).
- Buttons: Get Key, Open Case, Inventory, Trade Start.
- DB-backed inventories and seed data with rarity-based odds.

## Quick start
1. Install Java 17 and Maven.
2. Unzip project.
3. Copy `.env.example` to `.env` or set environment variables:
   - `BOT_TOKEN` - your Discord bot token
   - `MONGO_URI` - MongoDB connection string (include credentials)
   - `GUILD_ID` - (optional) dev guild id to register commands quickly
4. Build:
   ```bash
   mvn package
   ```
5. Seed skins:
   ```bash
   export MONGO_URI="your_mongo_uri"
   java -cp target/cs2-java-bot-1.0-SNAPSHOT-jar-with-dependencies.jar com.example.cs2bot.seed.SeedSkins
   ```
6. Run:
   ```bash
   export BOT_TOKEN="your_bot_token"
   export MONGO_URI="your_mongo_uri"
   java -jar target/cs2-java-bot-1.0-SNAPSHOT-jar-with-dependencies.jar
   ```

## Notes
- The Open Case chooses a rarity based on simple odds (configured in ButtonListener).
- You can expand the rarity odds and skin pool by editing `seed/skins.json`.
- This scaffold focuses on wiring and demonstrates how to integrate with MongoDB, JDA buttons, and embeds.


## Steam Price Updater
- Uses Steam Community Market (EUR) to fetch prices.
- Runs every 6 hours automatically.
- Manual trigger: `/refreshprices` (requires Manage Server).
