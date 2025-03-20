package com.gw.services.gruenbergwatches;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.gw.domain.PredefinedCollection;
import com.gw.domain.gruenbergwatches.GruenbergWatchesCollections;
import com.gw.services.BaseShopifySyncService;

@Component
@Profile({"gw-prod", "gw-dev"})
public class GWShopifySyncService extends BaseShopifySyncService{

	@Override 
    public PredefinedCollection[] getPredefinedCollections() {
		return GruenbergWatchesCollections.values();
	}
	
}