package com.gw.services;

import java.util.List;
import java.util.Map;

import com.gw.domain.FeedItem;
import com.gw.domain.FeedItemChangeSet;
import com.gw.domain.PredefinedCollection;
import com.gw.services.shopifyapi.objects.CustomCollection;

public interface IShopifySyncService extends ISyncService {

	void removeExtraItemsInDBAndInShopify(Map<PredefinedCollection, CustomCollection> collectionByEnum);

	void updateDB(FeedItemChangeSet changeSet) throws Exception;

	FeedItemChangeSet compareFeedItemWithDB(final List<FeedItem> feedItems);

	PredefinedCollection[] getPredefinedCollections();

}