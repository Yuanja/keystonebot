package com.gw.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.support.BasicAuthenticationInterceptor;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * @author jyuan
 *
 */
@Component
public class BaseRESTAPIService{
    
    //reference gw.properties
    protected @Value("${SHOPIFY_AUTH_USER}") String shopifyAuthUser;
    protected @Value("${SHOPIFY_AUTH_PASSWD}") String shopifyAuthPassword;

    protected @Value("${SHOPIFY_REST_URL}") String adminBaseURl;
    protected @Value("${SHOPIFY_ADMIN_API_VERSION}") String SHOPIFY_ADMIN_API_VERSION; 
    
    public String getShopifyBaseAdminRESTUrl() {
    	return adminBaseURl + "/admin/api/"+SHOPIFY_ADMIN_API_VERSION+"/";
    }
    
    protected RestTemplate createRestTemplate(){
        RestTemplate restTemplate = new RestTemplate();
        
        restTemplate.getInterceptors().add(
                new BasicAuthenticationInterceptor(shopifyAuthUser, shopifyAuthPassword));
       
        restTemplate.getMessageConverters().add(
                0, new MappingJackson2HttpMessageConverter());
        
        return restTemplate;
     }
    
    protected RestTemplate createRestTemplateForString(){
        RestTemplate restTemplate = new RestTemplate();
        
        restTemplate.getInterceptors().add(
                new BasicAuthenticationInterceptor(shopifyAuthUser, shopifyAuthPassword));
       
        return restTemplate;
     }
    
}
