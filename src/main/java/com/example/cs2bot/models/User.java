package com.example.cs2bot.models;

import org.bson.Document;
import java.util.ArrayList;
import java.util.List;

public class User {
    public String id;
    public int keys;
    public List<InventoryItem> inventory = new ArrayList<>();

    public User() {
        this.keys = 0;
    }

    // ✅ constructor from user ID (for new users)
    public User(String id) {
        this.id = id;
        this.keys = 0;
        this.inventory = new ArrayList<>();
    }

    // ✅ constructor from MongoDB document
    public User(Document doc) {
        this.id = doc.getString("_id");
        this.keys = doc.getInteger("keys", 0);
        this.inventory = new ArrayList<>();
        List<Document> items = (List<Document>) doc.get("inventory", List.class);
        if (items != null) {
            for (Document d : items) {
                this.inventory.add(InventoryItem.fromDocument(d));
            }
        }
    }

    public Document toDocument() {
        List<Document> invDocs = new ArrayList<>();
        for (InventoryItem i : inventory) {
            invDocs.add(i.toDocument());
        }
        return new Document("_id", id)
                .append("keys", keys)
                .append("inventory", invDocs);
    }

    // nested class for inventory items
    public static class InventoryItem {
        public String skin_id;
        public long acquiredAt;
        public boolean statTrak;

        public InventoryItem() {}
        public InventoryItem(String skin_id, long acquiredAt, boolean statTrak) {
            this.skin_id = skin_id;
            this.acquiredAt = acquiredAt;
            this.statTrak = statTrak;
        }

        public Document toDocument() {
            return new Document("skin_id", skin_id)
                    .append("acquiredAt", acquiredAt)
                    .append("statTrak", statTrak);
        }

        public static InventoryItem fromDocument(Document d) {
            return new InventoryItem(
                    d.getString("skin_id"),
                    d.getLong("acquiredAt"),
                    d.getBoolean("statTrak", false)
            );
        }
    }
}
