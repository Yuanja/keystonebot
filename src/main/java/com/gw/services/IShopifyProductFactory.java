package com.gw.services;

import java.util.List;

import com.gw.domain.FeedItem;
import com.gw.services.shopifyapi.objects.InventoryLevels;
import com.gw.services.shopifyapi.objects.Location;
import com.gw.services.shopifyapi.objects.Product;

public interface IShopifyProductFactory {

	void mergeInventoryLevels(InventoryLevels existingInventoryLevels, InventoryLevels newInventoryLevels);

	void mergeExistingDescription(String exstingDescriptionHtml, String toBeUpdatedDescriptionHtml);

	void mergeProduct(Product existing, Product toBeUpdatedProduct);

	Product createProduct(FeedItem feedItem) throws Exception;

	List<Location> getLocations();

}