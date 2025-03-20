package com.gw.services.gruenbergwatches;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.gw.services.BaseShopifyProductFactory;

@Component
@Profile({"gw-prod", "gw-dev"})
public class GWShopifyProductFactoryService extends BaseShopifyProductFactory {


}
