package com.gw.domain;

public class FeedItemChange {

    private FeedItem fromDb;
    private FeedItem fromFeed;

    public FeedItemChange(FeedItem fromDb, FeedItem fromFeed) {
        this.fromDb = fromDb;
        this.fromFeed = fromFeed;
    }

    public FeedItem getFromDb() {
        return fromDb;
    }

    public void setFromDb(FeedItem fromDb) {
        this.fromDb = fromDb;
    }

    public FeedItem getFromFeed() {
        return fromFeed;
    }

    public void setFromFeed(FeedItem fromFeed) {
        this.fromFeed = fromFeed;
    }
}
