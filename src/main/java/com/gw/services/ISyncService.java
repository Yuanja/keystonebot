package com.gw.services;

public interface ISyncService {

	/* (non-Javadoc)
	 * @see com.gw.components.IGWEbayBotService#readFeedAndPost()
	 */
	void sync(boolean feedReady) throws Exception;

}