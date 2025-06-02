package com.gw.services;

import java.io.IOException;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.gw.domain.FeedItem;

public interface IFeedService {

	/* (non-Javadoc)
	 * @see com.gw.components.IGWFeedService#get()
	 */
	List<FeedItem> getItemsFromFeed() throws IOException, ParserConfigurationException, SAXException;

	List<FeedItem> getItemsFromTempFiles() throws IOException, ParserConfigurationException, SAXException;

	List<FeedItem> loadFromXmlFile(String filePath) throws ParserConfigurationException, SAXException, IOException;

	boolean toAcceptFromFeed(FeedItem feedItem);

	List<FeedItem> getItemsFromDBNotInFeed(List<FeedItem> feedItems);

	/* (non-Javadoc)
	 * @see com.gw.components.IGWFeedService#getFeedItems()
	 */
	List<FeedItem> getFeedItemsToPublish();

	List<FeedItem> getFeedItemsToUpdate();

	boolean hasDupes(List<FeedItem> feedItems);

	List<FeedItem> trimDupes(List<FeedItem> feedItems);
	
	/**
	 * Enhanced caching methods for testing/development environments
	 */
	
	/**
	 * Forces a cache refresh by clearing current cache and loading fresh data
	 * Useful for testing or when fresh data is required
	 */
	List<FeedItem> refreshCache() throws IOException, ParserConfigurationException, SAXException;
	
	/**
	 * Clears the current cache
	 */
	void clearCache();
	
	/**
	 * Gets cache status information for debugging/monitoring
	 */
	String getCacheStatus();
	
	/**
	 * Loads items from the top 100 feed cache for faster testing
	 * Falls back to regular feed loading if cache is not available
	 */
	List<FeedItem> getItemsFromTop100Feed() throws IOException, ParserConfigurationException, SAXException;
}