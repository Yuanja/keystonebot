package com.gw.services.pricing;

import com.gw.domain.FeedItem;

/**
 * Strategy interface for determining product pricing from feed items
 * Allows different implementations for different business contexts
 */
public interface PricingStrategy {
    
    /**
     * Gets the appropriate price for a feed item
     * 
     * @param feedItem The feed item to get price from
     * @return The price as a string
     */
    String getPrice(FeedItem feedItem);
} 