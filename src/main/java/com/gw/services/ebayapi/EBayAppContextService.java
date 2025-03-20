package com.gw.services.ebayapi;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.ebay.sdk.ApiContext;
import com.ebay.sdk.ApiCredential;

/**
 * @author jyuan
 *
 */
@Component
public class EBayAppContextService{
    //reference gw.properties
    private @Value("${SERVER_KEY}") String serverKey;
    private @Value("${SERVER_URL}") String serverUrl;
    
    
    /* (non-Javadoc)
     * @see com.gw.components.IEbayAppContextService#getApiContext()
     */
    public ApiContext getApiContext() throws IOException {
        
        ApiContext apiContext = new ApiContext();
        ApiCredential cred = apiContext.getApiCredential();
        cred.seteBayToken(serverKey);
        apiContext.setApiServerUrl(serverUrl);
        
        return apiContext;
    }
}
