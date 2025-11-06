package com.example.cs2bot.models;

import org.bson.Document;
import java.util.ArrayList;
import java.util.List;

public class Trade {
    public String id;
    public String from;
    public String to;
    public List<OfferItem> offer = new ArrayList<>();
    public List<OfferItem> request = new ArrayList<>();
    public String status = "pending";
    public boolean initiatorConfirmed = false;
    public boolean recipientConfirmed = false;

    public static class OfferItem {
        public String skin_id;
        public OfferItem() {}
        public OfferItem(String s){ this.skin_id = s; }
        public Document toDocument(){ return new Document("skin_id", skin_id); }
        public static OfferItem fromDocument(Document d){ OfferItem o = new OfferItem(); o.skin_id = d.getString("skin_id"); return o;}
    }

    public Document toDocument() {
        Document d = new Document();
        d.append("from", from);
        d.append("to", to);
        d.append("status", status);
        List<Document> off = new ArrayList<>();
        for (OfferItem o : offer) off.add(o.toDocument());
        List<Document> req = new ArrayList<>();
        for (OfferItem r : request) req.add(r.toDocument());
        d.append("offer", off);
        d.append("request", req);
        d.append("initiatorConfirmed", initiatorConfirmed);
        d.append("recipientConfirmed", recipientConfirmed);
        return d;
    }

    public static Trade fromDocument(Document d) {
        Trade t = new Trade();
        t.id = d.getObjectId("_id").toHexString();
        t.from = d.getString("from");
        t.to = d.getString("to");
        t.status = d.getString("status");
        t.initiatorConfirmed = d.getBoolean("initiatorConfirmed", false);
        t.recipientConfirmed = d.getBoolean("recipientConfirmed", false);
        List<Document> off = (List<Document>) d.get("offer");
        if (off != null) for (Document o : off) t.offer.add(OfferItem.fromDocument(o));
        List<Document> req = (List<Document>) d.get("request");
        if (req != null) for (Document r : req) t.request.add(OfferItem.fromDocument(r));
        return t;
    }
}
