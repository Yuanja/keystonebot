package com.gw.services.jomashop;

import com.gw.services.HeaderRequestInterceptor;
import com.gw.services.LogService;
import com.gw.services.jomashop.InventoryUpdateRequest.Status;
import com.gw.services.jwt.TokenRepoService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Component
public class JomaShopApiService {
    
    private static Logger logger = LogManager.getLogger(JomaShopApiService.class);
    
    @Value("${jomashop.api.baseurl}")
    private String apiBaseUrl;
    
    @Autowired
    private TokenRepoService accessTokenService;
    
    protected RestTemplate createRestTemplate() throws Exception{
        RestTemplate restTemplate = new RestTemplate();
        
        restTemplate.getInterceptors().add(
                new HeaderRequestInterceptor(HttpHeaders.USER_AGENT, "GruenbergWatch Bot"));
        restTemplate.getInterceptors().add(
                new HeaderRequestInterceptor(HttpHeaders.AUTHORIZATION, accessTokenService.getTokenString()));
        
        return restTemplate;
     }
    
    protected RestTemplate createRestTemplateForPost() throws Exception{
        RestTemplate restTemplate = new RestTemplate();
        
        restTemplate.getInterceptors().add(
                new HeaderRequestInterceptor(HttpHeaders.USER_AGENT, "GruenbergWatch Bot"));
        restTemplate.getInterceptors().add(
                new HeaderRequestInterceptor(HttpHeaders.AUTHORIZATION, accessTokenService.getTokenString()));
        restTemplate.getInterceptors().add(
                new HeaderRequestInterceptor(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));
        
        return restTemplate;
    }
    
    protected RestTemplate createRestTemplateForPostMultipart() throws Exception{
        RestTemplate restTemplate = new RestTemplate();
        
        restTemplate.getInterceptors().add(
                new HeaderRequestInterceptor(HttpHeaders.USER_AGENT, "GruenbergWatch Bot"));
        restTemplate.getInterceptors().add(
                new HeaderRequestInterceptor(HttpHeaders.AUTHORIZATION, accessTokenService.getTokenString()));
        restTemplate.getInterceptors().add(
                new HeaderRequestInterceptor(HttpHeaders.CONTENT_TYPE, MediaType.MULTIPART_FORM_DATA_VALUE));
        
        return restTemplate;
    }
    
    protected HttpHeaders getHeadersForMultipartForm() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "GruenbergWatch Bot");
        headers.set("Content-Type", "multipart/form-data"); // we are sending a form
        headers.set("Authorization", accessTokenService.getTokenString());
        
        return headers;
    }
    
    public boolean createProduct(SingleProductRequest sp) throws Exception {
        String url=apiBaseUrl+"/products";
        RestTemplate restTemplate = createRestTemplateForPost();
        
        try {
            ResponseEntity<SingleProductResponse> response
                = restTemplate.postForEntity(url, sp, SingleProductResponse.class);
            SingleProductResponse productResponse = response.getBody();
            logger.debug("Inserting JomaShop Sku: " + productResponse.jomashop_sku);
            return true;
        }catch (HttpClientErrorException clientException) {
            logger.debug(clientException.getResponseBodyAsString());
            sp.product.addErrorMsg("JomaShopAPI Failed with: " + clientException.getResponseBodyAsString());
            return false;
        }
    }
    
    public boolean updateProduct(String jomashop_sku, SingleProductRequest sp) throws Exception {
        String url=apiBaseUrl+"/products/"+jomashop_sku;
        RestTemplate restTemplate = createRestTemplateForPost();
        
        try {
            //Critical, take out the inventory nested payload for update.
            //Inventory is updated via a different end point. 
            //TODO: Test this when inventory status is supported in product.inventory.
            sp.inventory = null;
            restTemplate.put(url, sp);
            return true;
        }catch (HttpClientErrorException clientException) {
            logger.debug(clientException.getResponseBodyAsString());
            sp.product.addErrorMsg(clientException.getResponseBodyAsString());
            return false;
        }
    }
    
    public Map<String, SingleProductResponse> getAllProductBySkuMap() throws Exception {
        RestTemplate restTemplate = createRestTemplate();
        
        Map<String, SingleProductResponse> productBySku = new HashMap<String, SingleProductResponse>();
        
        int currPage = 1, totalPages = 1;
        do {
            String url=apiBaseUrl+"/products"; 
            if (currPage > 1)
               url += "?page="+currPage;
            
            ResponseEntity<ProductsResponse> response
                = restTemplate.getForEntity(url, ProductsResponse.class);
            ProductsResponse productsResponse = response.getBody();
            
            if (productsResponse.products != null) {
                for (SingleProductResponse pr : productsResponse.products) {
                    if (pr.vendor != null) {
                        //Some of the product may have been hand created or simply missing vendor information.
                        productBySku.put(pr.vendor.sku, pr);
                    } else {
                        //Use the jomashop sku as key
                        productBySku.put(pr.jomashop_sku, pr);
                    }
                }
            }
            totalPages = productsResponse.pages;
        } while (currPage++ < totalPages);
                
        return productBySku;
    }
    
    public Map<String, Inventory> getInventoryBySkuMap() throws Exception {
        RestTemplate restTemplate = createRestTemplate();
        
        Map<String, Inventory> invMapBySku = new HashMap<String, Inventory>();
        
        int currPage = 1, totalPages = 1;
        do {
            String url=apiBaseUrl+"/inventory"; 
            if (currPage > 1)
               url += "?page="+currPage;
            
            ResponseEntity<InventoryResponse> response
                = restTemplate.getForEntity(url, InventoryResponse.class);
            InventoryResponse invResponse = response.getBody();
            
            if (invResponse.inventory != null) {
                for (Inventory inv : invResponse.inventory) {
                    invMapBySku.put(inv.sku, inv);
                }
            }
            totalPages = invResponse.pages;
        } while (currPage++ < totalPages);
                
        return invMapBySku;
    }
    
    public boolean updatePrice(String webTagNumber, Inventory inv, double price) throws Exception {
        String url=apiBaseUrl+"/inventory/"+ webTagNumber;
        RestTemplate restTemplate = createRestTemplateForPost();
        
        try {
            InventoryUpdateRequest request = 
                    new InventoryUpdateRequest(inv.status, webTagNumber, inv.quantity, price);
            restTemplate.put(url,request );
            return true;
        }catch (HttpClientErrorException clientException) {
            logger.error(clientException.getResponseBodyAsString());
            return false;
        }
    }
    
    public boolean markSkuSold(String webTagNumber, double price) throws Exception {
        String url=apiBaseUrl+"/inventory/"+ webTagNumber;
        RestTemplate restTemplate = createRestTemplateForPost();
        
        try {
            restTemplate.put(url, new InventoryUpdateRequest(Status.Sold, webTagNumber, 0, price));
            return true;
        }catch (HttpClientErrorException clientException) {
            logger.error(clientException.getResponseBodyAsString());
            return false;
        }
    }
    
    public boolean markSkuActive(String webTagNumber, double price) throws Exception {
        String url=apiBaseUrl+"/inventory/"+ webTagNumber;
        RestTemplate restTemplate = createRestTemplateForPost();
        
        try {
            InventoryUpdateRequest request = 
                    new InventoryUpdateRequest(Status.Active, webTagNumber, 1, price);

            logger.debug(LogService.toJson(request));
            
            restTemplate.put(url,request );
            return true;
        }catch (HttpClientErrorException clientException) {
            logger.error(clientException.getResponseBodyAsString());
            return false;
        }
    }
    
    public boolean markSkuInactive(String webTagNumber, double price) throws Exception {
        String url=apiBaseUrl+"/inventory/"+ webTagNumber;
        RestTemplate restTemplate = createRestTemplateForPost();
        
        try {
            InventoryUpdateRequest request = 
                    new InventoryUpdateRequest(Status.Inactive, webTagNumber, 0, price);
            
            restTemplate.put(url, request);
            logger.debug(LogService.toJson(request));
            return true;
        }catch (HttpClientErrorException clientException) {
            logger.error(clientException.getResponseBodyAsString());
            return false;
        }
    }
    
    public List<Brand> getBrands()throws Exception {
        
        RestTemplate restTemplate = createRestTemplate();
        ArrayList<Brand> allBrands = new ArrayList<Brand>();
        
        int currPage = 1, totalPages = 1;
        do {
            String url=apiBaseUrl+"/brands";
            if (currPage > 1)
               url += "?page="+currPage;
            
            ResponseEntity<BrandsResponse> response
                = restTemplate.getForEntity(url, BrandsResponse.class);
            BrandsResponse brandsR = response.getBody();
            
            if (brandsR.count > 0) {
                allBrands.addAll(Arrays.asList(brandsR.manufacturers));
            }
            
            totalPages = brandsR.pages;
            
        }while(currPage++ < totalPages);
        return allBrands;
    }
    
    public List<Category> getCategories()throws Exception {
        
        RestTemplate restTemplate = createRestTemplate();
        ArrayList<Category> allCategories = new ArrayList<Category>();
        
        int currPage = 1, totalPages = 1;
        do {
            String url=apiBaseUrl+"/categories";
            if (currPage > 1)
               url += "?page="+currPage;
            
            ResponseEntity<CategoriesResponse> response
                = restTemplate.getForEntity(url, CategoriesResponse.class);
            if(!HttpStatus.OK.equals(response.getStatusCode())){
                throw new RuntimeException("Failed to get response."+ response.getStatusCode());
            }
            
            CategoriesResponse categoriesR = response.getBody();
            
            if (categoriesR.count > 0) {
                allCategories.addAll(Arrays.asList(categoriesR.categories));
            }
            
            totalPages = categoriesR.total_count;
            
        }while(currPage++ < totalPages);
        return allCategories;
    }
    
    public Category getCategoryByName(String name) throws Exception {
        RestTemplate restTemplate = createRestTemplate();
        String url=apiBaseUrl+"/categories/"+ name ;
        ResponseEntity<Category> response = restTemplate.getForEntity(url, Category.class);
        if (!HttpStatus.OK.equals(response.getStatusCode())){
            throw new RuntimeException("getCategoryByName failed. Status code: "  + response.getStatusCode());
        }
        
        return response.getBody();
    }
}
