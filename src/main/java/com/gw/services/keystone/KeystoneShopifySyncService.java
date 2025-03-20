package com.gw.services.keystone;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.gw.domain.PredefinedCollection;
import com.gw.domain.keystone.KeyStoneCollections;
import com.gw.services.BaseShopifySyncService;

@Component
@Profile({"keystone-prod", "keystone-dev"})
public class KeystoneShopifySyncService extends BaseShopifySyncService{
	@Override 
    public PredefinedCollection[] getPredefinedCollections() {
		return KeyStoneCollections.values();
	}
}