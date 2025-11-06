package com.example.cs2bot.db;

import com.mongodb.client.*;
import org.bson.Document;
import com.mongodb.MongoClientSettings;
import com.mongodb.ConnectionString;

public class MongoUtil {
    private static MongoClient client;
    private static MongoDatabase db;

    public static void init(String uri, String dbName) {
        ConnectionString connString = new ConnectionString(uri);
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(connString)
                .build();
        client = MongoClients.create(settings);
        db = client.getDatabase(dbName);
        System.out.println("Connected to MongoDB: " + dbName);
    }

    public static MongoDatabase getDB() {
        return db;
    }
}
