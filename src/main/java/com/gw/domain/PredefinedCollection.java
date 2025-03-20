package com.gw.domain;

public interface PredefinedCollection {
    
    String getTitle();
    
    boolean isBrand();
    
    boolean accepts(FeedItem feedItem);
}
