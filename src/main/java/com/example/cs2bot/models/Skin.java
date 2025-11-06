package com.example.cs2bot.models;

import org.bson.Document;

public class Skin {
    public String id;
    public String name;
    public String condition;
    public double price;
    public String image;
    public String rarity;

    public Skin() {}

    public Skin(Document d) {
        this.id = d.getString("_id");
        this.name = d.getString("name");
        this.condition = d.getString("condition");
        this.price = d.getDouble("price");
        this.image = d.getString("image");
        this.rarity = d.getString("rarity");
    }

    public Document toDocument() {
        Document d = new Document();
        d.append("_id", id);
        d.append("name", name);
        d.append("condition", condition);
        d.append("price", price);
        d.append("image", image);
        d.append("rarity", rarity);
        return d;
    }
}
