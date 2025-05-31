package com.gw.services.pricing;

import com.gw.domain.FeedItem;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Keystone-specific pricing strategy that uses Keystone price field
 * Active only for keystone profiles
 */
@Component
@Profile({"keystone-prod", "keystone-dev"})
public class KeystonePricingStrategy implements PricingStrategy {
    
    @Override
    public String getPrice(FeedItem feedItem) {
        return feedItem.getWebPriceKeystone();
    }
} 