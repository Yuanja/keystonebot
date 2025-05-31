package com.gw.services;

import java.util.List;
import com.gw.domain.FeedItem;
import com.gw.domain.FeedItemChangeSet;
import com.gw.domain.PredefinedCollection;

public interface IShopifySyncService extends ISyncService {

	void removeExtraItemsInDBAndInShopify();

	void updateDB(FeedItemChangeSet changeSet) throws Exception;

	FeedItemChangeSet compareFeedItemWithDB(final List<FeedItem> feedItems);

	PredefinedCollection[] getPredefinedCollections();

	/**
	 * Update an existing item on Shopify
	 * @param item FeedItem to update on Shopify
	 */
	void updateItemOnShopify(FeedItem item);

	/**
	 * Publish a new item to Shopify
	 * @param item FeedItem to publish to Shopify
	 */
	void publishItemToShopify(FeedItem item);

}