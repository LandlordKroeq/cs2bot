package com.example.cs2bot.seed;

import com.example.cs2bot.db.MongoUtil;
import com.google.gson.*;
import com.mongodb.client.MongoCollection;
import org.bson.Document;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class SeedSkins {
    public static void main(String[] args) throws Exception {
        String mongoUri = System.getenv("MONGO_URI");
        if (mongoUri == null) {
            System.err.println("Set MONGO_URI env variable.");
            System.exit(1);
        }
        MongoUtil.init(mongoUri, "cs2bot");
        MongoCollection<Document> skins = MongoUtil.getDB().getCollection("skins");
        String json = Files.readString(Path.of("seed/skins.json"));
        JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
        for (JsonElement e : arr) {
            Document d = Document.parse(e.toString());
            skins.replaceOne(new Document("_id", d.getString("_id")), d, new com.mongodb.client.model.ReplaceOptions().upsert(true));
            System.out.println("Upserted skin: " + d.getString("_id"));
        }
        System.out.println("Seed complete.");
        System.exit(0);
    }
}
