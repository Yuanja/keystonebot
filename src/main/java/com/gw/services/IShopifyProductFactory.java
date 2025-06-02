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

	/**
	 * Creates a product using the regular addProduct method with enhanced options support
	 * This method creates products with all 3 options and 1 variant, but uses the regular GraphQL mutation
	 * The variant options are now properly supported after the GraphQL fixes
	 * 
	 * @param feedItem The feed item with source data
	 * @return The created product with both product ID and variant ID
	 * @throws Exception if product creation fails
	 */
	Product createProductWithOptions(FeedItem feedItem) throws Exception;

	List<Location> getLocations();

}