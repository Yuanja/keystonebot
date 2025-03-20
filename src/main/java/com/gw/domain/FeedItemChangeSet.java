package com.gw.domain;

import java.util.List;

public class FeedItemChangeSet {
    private List<FeedItem> newItems;
    private List<FeedItemChange> changedItems;
    private List<FeedItem> deletedItems;
    
    public FeedItemChangeSet(final List<FeedItem> newItems, final List<FeedItemChange> changedItems, final List<FeedItem> deletedItems) {
        this.newItems = newItems;
        this.changedItems = changedItems;
        this.deletedItems = deletedItems;
    }

    public List<FeedItem> getNewItems() {
        return newItems;
    }

    public List<FeedItemChange> getChangedItems() {
        return changedItems;
    }

    public List<FeedItem> getDeletedItems() {
        return deletedItems;
    }
}