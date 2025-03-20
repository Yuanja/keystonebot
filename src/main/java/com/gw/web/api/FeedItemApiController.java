package com.gw.web.api;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gw.domain.FeedItem;
import com.gw.services.FeedItemService;

@RestController
@RequestMapping("/api/feedItem")
public class FeedItemApiController {

	@Autowired
	private FeedItemService feedItemService;
	
	@GetMapping
	public Iterable<FeedItem> getAllFeedItem() {
		
		List<FeedItem> allFeedItems = feedItemService.findAll();
		
		return allFeedItems;
	}
}
