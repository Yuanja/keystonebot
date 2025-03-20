package com.gw.services.shopifyapi;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gw.domain.PredefinedCollection;
import com.gw.services.BaseRESTAPIService;
import com.gw.services.CollectionUtility;
import com.gw.services.LogService;
import com.gw.services.shopifyapi.objects.Collect;
import com.gw.services.shopifyapi.objects.CollectVo;
import com.gw.services.shopifyapi.objects.Collects;
import com.gw.services.shopifyapi.objects.CustomCollection;
import com.gw.services.shopifyapi.objects.CustomCollectionVo;
import com.gw.services.shopifyapi.objects.CustomCollections;
import com.gw.services.shopifyapi.objects.Image;
import com.gw.services.shopifyapi.objects.Images;
import com.gw.services.shopifyapi.objects.InventoryLevel;
import com.gw.services.shopifyapi.objects.InventoryLevels;
import com.gw.services.shopifyapi.objects.Location;
import com.gw.services.shopifyapi.objects.Locations;
import com.gw.services.shopifyapi.objects.Product;
import com.gw.services.shopifyapi.objects.ProductVo;
import com.gw.services.shopifyapi.objects.Products;
import com.gw.services.shopifyapi.objects.Variant;

/**
 * @author jyuan
 *
 */
@Component
public class ShopifyAPIService extends BaseRESTAPIService{
    
    private static Logger logger = LogManager.getLogger(ShopifyAPIService.class);

    @Autowired 
    LogService logService;
    
    
    public void updateInventoryLevels(InventoryLevels inventoryLevels) throws Exception{
        URI productURI = new URI(getShopifyBaseAdminRESTUrl()+"inventory_levels/set.json");
        RestTemplate restTemplate = createRestTemplate();
        try {
	        for (InventoryLevel level : inventoryLevels.get()) {
	        	restTemplate.postForEntity(productURI, level, ProductVo.class);
	        }
    	} catch (HttpClientErrorException e) {
            logger.error(e.getResponseBodyAsString());
            logger.error(e);
            throw e;
        }
    }
    
    public void updateProduct(Product mergedProduct) throws Exception{
        if (mergedProduct != null && mergedProduct.getId() != null) {
            URI productURI = new URI(getShopifyBaseAdminRESTUrl()+"products/"+mergedProduct.getId()+".json");
            RestTemplate restTemplate = createRestTemplate();
            
            try {
                restTemplate.put(productURI, new ProductVo(mergedProduct));
            } catch (HttpClientErrorException e) {
                logger.error(e.getResponseBodyAsString());
                logger.error(e);
                throw e;
            }    
        } else {
            logger.error("Item can't be updated because the id is null!");
        }
    }
    
    public Product addProduct(Product product) {
        try {
            String productUri = getShopifyBaseAdminRESTUrl()+"products.json";
            RestTemplate restTemplate = createRestTemplate();
            
            ProductVo result = restTemplate.postForObject(productUri, new ProductVo(product), ProductVo.class);
            return result.get();
        } catch (HttpClientErrorException e) {
            ObjectMapper mapper = new ObjectMapper();
            try {
                logger.error(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(product));
            }
            catch (JsonProcessingException e1) {
                e1.printStackTrace();
            }
            logger.error(e.getResponseBodyAsString());
            logger.error(e);
            
            throw e;
        }
    }
    
    public int getProductCount() {
        RestTemplate restTemplate = createRestTemplate();
        String totalCountUrl = getShopifyBaseAdminRESTUrl()+"products/count.json";
        HashMap<String, Integer> countMap = restTemplate.getForObject(totalCountUrl, HashMap.class);
        
        return countMap.get("count");
    }
    
    public ProductVo getProductByProductId(String id) {
        RestTemplate restTemplate = createRestTemplate();
        String singleProductUri = getShopifyBaseAdminRESTUrl()+"products/"+id+".json";
        ProductVo result = null;
        try {
            result = restTemplate.getForObject(singleProductUri, ProductVo.class);
        } catch (Exception e) {
            logger.error("Got error while trying to get shopify product: "+ id +" : " +e.getMessage());
        }
        
        //Get the itemInventoryLevel
        String inventoryItemId = result.get().getVariants().get(0).getInventoryItemId();
        if (inventoryItemId != null) {
        	InventoryLevels levels = getInventoryLevelByInventoryItemId(inventoryItemId);
        	if (!levels.get().isEmpty()) {
        		if (levels.get().size() > 1) {
        			logger.error("Got more than one inventoryLevels while trying to get inventoryItemId: "+ inventoryItemId);
        		} else {
        			//We assume there is only one location.  If not this needs to be addressed.
        			result.get().getVariants().get(0).setInventoryLevels(levels);
        		}
        	} else {
        		logger.error("Got null inventoryLevels while trying to get inventoryItemId: "+ inventoryItemId);
        	}
        } else {
            logger.error("Got null inventory_item_id while trying to get shopify product id: "+ id);
        }
        
        return result;
    }
    
    public InventoryLevels getInventoryLevelByInventoryItemId(String id) {
        String singleInvUri = getShopifyBaseAdminRESTUrl()+"inventory_levels.json?inventory_item_ids="+id;
		RestTemplate restTemplate = createRestTemplateForString();
        HttpEntity<String> response = restTemplate.getForEntity(singleInvUri, String.class);

        String resultString = response.getBody();
        ObjectMapper mapper = new ObjectMapper();
        
        InventoryLevels inventoryLevels = null;
        try {
        	inventoryLevels = mapper.readValue(resultString, InventoryLevels.class);
        } catch (Exception e) {
        	logger.error("Can't map json to collection of InventoryLevels: " + resultString);
        }
        return inventoryLevels;
    }
    
    public List<Image> getImagesByProduct(String productId){
        RestTemplate restTemplate = createRestTemplate();
        String url = getShopifyBaseAdminRESTUrl()+"products/"+productId+"/images.json";
        Images result = null;
        try {
            result = restTemplate.getForObject(url, Images.class);
        } catch (Exception e) {
            logger.error("Got error while trying to get Images By Product Id:" + productId);
        }
        
        return result == null ? new ArrayList<Image>() : result.get();
    }
    
    public void deleteImages(String productId, List<Image> images) {
        RestTemplate restTemplate = createRestTemplate();
        for (Image img: images) {
            String deleteUri = getShopifyBaseAdminRESTUrl()+"products/" + productId + "/images/" + img.getId() +".json";
            try {
                restTemplate.delete(new URI(deleteUri));
            }
            catch (RestClientException | URISyntaxException e) {
                logger.error("Got error while trying to delete Images By Product Id:" 
                    + productId + " Image Id: " + img.getId());
            }
        }
    }
    
    public void deleteAllImageByProductId (String productId) {
        deleteImages(productId, getImagesByProduct(productId));
    }
    
    public void deleteProductById(String id) throws RestClientException, URISyntaxException {
        RestTemplate restTemplate = createRestTemplate();
        String deleteUri = getShopifyBaseAdminRESTUrl()+"products/" + id + ".json";
        restTemplate.delete(new URI(deleteUri));
    }
    
    public void deleteCustomCollectionsById(String id) throws RestClientException, URISyntaxException {
        RestTemplate restTemplate = createRestTemplate();
        String deleteUri = getShopifyBaseAdminRESTUrl()+"custom_collections/" + id + ".json";
        restTemplate.delete(new URI(deleteUri));
    }
    
    public List<Location> getAllLocations(){
        int maxResultPerPage = 250;
        String getAll = getShopifyBaseAdminRESTUrl()+"locations.json?limit="+maxResultPerPage;
        List<Location> result = new ArrayList<Location>();
        String url = getAll;
    	do {
			RestTemplate restTemplate = createRestTemplateForString();
            HttpEntity<String> response = restTemplate.getForEntity(url, String.class);

            String resultString = response.getBody();
            url = getNextPageUrl(response.getHeaders());
            
            ObjectMapper mapper = new ObjectMapper();
            
            Locations locations = null;
            try {
	            locations = mapper.readValue(resultString, Locations.class);
	            if (locations == null || locations.get().size() < maxResultPerPage) {
	                url = null;
	            } 
	            
	            if (locations != null)
	            	result.addAll(locations.get());
	            
            } catch (Exception e) {
            	logger.error("Can't map json to collection of locations: " + resultString);
            }
        } while (url != null);
    	
        return result;
    }
    
    
    public List<InventoryLevel> getAllInvetoryLevelByLocation(String locationId){
        int maxResultPerPage = 250;
        String getAll = getShopifyBaseAdminRESTUrl()+"inventory_levels.json?location_ids="+locationId+"&limit="+maxResultPerPage;
        List<InventoryLevel> result = new ArrayList<InventoryLevel>();
        String url = getAll;
    	do {
			RestTemplate restTemplate = createRestTemplateForString();
            HttpEntity<String> response = restTemplate.getForEntity(url, String.class);

            String resultString = response.getBody();
            url = getNextPageUrl(response.getHeaders());
            
            ObjectMapper mapper = new ObjectMapper();
            
            InventoryLevels listForThisPage = null;
            try {
            	listForThisPage = mapper.readValue(resultString, InventoryLevels.class);
	            if (listForThisPage == null || listForThisPage.get().size() < maxResultPerPage) {
	                url = null;
	            } 
	            
	            if (listForThisPage != null)
	            	result.addAll(listForThisPage.get());
	            
            } catch (Exception e) {
            	logger.error("Can't map json to collection of InventoryLevels: " + resultString);
            }
        } while (url != null);
    	
        return result;
    }
    
    public List<Product> getAllProducts(){
        int maxResultPerPage = 250;
        String getAll = getShopifyBaseAdminRESTUrl()+"products.json?limit="+maxResultPerPage;
        List<Product> result = new ArrayList<Product>();
        String url = getAll;
    	do {
			RestTemplate restTemplate = createRestTemplateForString();
            HttpEntity<String> response = restTemplate.getForEntity(url, String.class);

            String resultString = response.getBody();
            url = getNextPageUrl(response.getHeaders());
            
            ObjectMapper mapper = new ObjectMapper();
            
            Products products = null;
            try {
	            products = mapper.readValue(resultString, Products.class);
	            if (products == null || products.get().size() < maxResultPerPage) {
	                url = null;
	            } 
	            
	            if (products != null)
	            	result.addAll(products.get());
	            
            } catch (Exception e) {
            	logger.error("Can't map json to collection of products: " + resultString);
            }
        } while (url != null);
    	
        return result;
    }
    
    private String getNextPageUrl(HttpHeaders headers) {
    	List<String> links = headers.getValuesAsList("Link");
    	for (String alink : links) {
    		if (alink.contains("rel=\"next\"")) {
    			return alink.substring(alink.indexOf("<")+1, alink.indexOf(">"));
    		}
    	}
    	return null;
    }
    
    public void deleteProductByIdOrLogFailure(String id){
        try {
            deleteProductById(id);
        }
        catch (Exception e) {
            logService.emailError(logger, "Shopify Bot: Error while removing product id: " + id, null, e);
        }
    }
    
    public void removeAllProducts() throws RestClientException, URISyntaxException {
        for (Product todelete : getAllProducts()) {
            deleteProductById(todelete.getId());
        }
    }
    
    public void removeAllCollections() throws RestClientException, URISyntaxException {
        for (CustomCollection collection : getAllCustomCollections().getList()) {
            deleteCustomCollectionsById(collection.getId());
        }
    }
    
    public Map<String, Product> unlistDupelistings(){
        List<Product> allProducts = getAllProducts();
        Map<String, Product> cleanMapOfProductBySku = new HashMap<String, Product>();
        for (Product currentProduct: allProducts) {
            List<Variant> variant = currentProduct.getVariants();
            if (variant == null || variant.isEmpty() || StringUtils.isEmpty(variant.get(0).getSku())) {
                logger.error("Product id: " + currentProduct.getId() + " has no Variant! Can't resolve Sku deleting from shopify.");
                deleteProductByIdOrLogFailure(currentProduct.getId());
            } else {
                String currentProductSku = variant.get(0).getSku();
                if (cleanMapOfProductBySku.containsKey(currentProductSku)) {
                    logger.error("Found duplicate product ID: " + currentProduct.getId() 
                        + " SKU: " + currentProductSku +" Removing from shopify! ");
                    deleteProductByIdOrLogFailure(currentProduct.getId());
                } else {
                    cleanMapOfProductBySku.put(currentProductSku, currentProduct);
                }
            }
        }
        
        return cleanMapOfProductBySku;
    }
    
    public HashMap<PredefinedCollection, CustomCollection> ensureConfiguredCollections(PredefinedCollection[] predefinedCollections){
        CustomCollections allCollectionsFromShopify = getAllCustomCollections();
        Map<String, CustomCollection> collectionByTitleFromShopify = 
                allCollectionsFromShopify.getList().stream().collect(Collectors.toMap(CustomCollection::getTitle, Function.identity()));
        
        HashMap<PredefinedCollection, CustomCollection> customCollectionByEnum 
            = new HashMap<PredefinedCollection, CustomCollection>(); 
        
        for(PredefinedCollection collectionEnum : predefinedCollections) {
            //Call api and ensure the creation of the collection.
            if (!collectionByTitleFromShopify.containsKey(collectionEnum.getTitle())) {
                logger.info("Creating custom collection name: " + collectionEnum.getTitle());
                CustomCollectionVo result = createCustomCollection(collectionEnum);
                customCollectionByEnum.put(collectionEnum, result.get());
            } else {
                customCollectionByEnum.put(collectionEnum, collectionByTitleFromShopify.get(collectionEnum.getTitle()) );
            }
        }
        return customCollectionByEnum;
    }
    
    public void addProductAndCollectionsAssociations(List<Collect> collects) {
        for (Collect collect : collects) {
            try {
                String collectionUri = getShopifyBaseAdminRESTUrl()+"collects.json";
                RestTemplate restTemplate = createRestTemplate();
                restTemplate.postForObject(collectionUri, 
                        new CollectVo(collect), CollectVo.class);
            } catch (HttpClientErrorException e) {
                logger.error(e.getResponseBodyAsString());
                logger.error(e);
                throw e;
            }
        }
    }
    
    public CustomCollectionVo createCustomCollection (PredefinedCollection collectionEnum) {
        try {
            String collectionUri = getShopifyBaseAdminRESTUrl()+"custom_collections.json";
            RestTemplate restTemplate = createRestTemplate();
            
            CustomCollection newCollection = new CustomCollection();
            newCollection.setTitle(collectionEnum.getTitle());
            newCollection.setSortOrder("created-desc");
            
            CustomCollectionVo result = restTemplate.postForObject(collectionUri, 
                    new CustomCollectionVo(newCollection), CustomCollectionVo.class);
            return result;
        } catch (HttpClientErrorException e) {
            logger.error(e.getResponseBodyAsString());
            logger.error(e);
            throw e;
        }
    }
    
    public List<PredefinedCollection> getPredefinedCollectionsForProductId(String productId,
            Map<PredefinedCollection, CustomCollection> collectionByEnum){
        return CollectionUtility.getPredefinedCollectionFromCollect(getCollectsForProductId(productId), collectionByEnum);
    }
    
    public List<Collect> getCollectsForProductId(String productId){
        try {
            String collectionUri = getShopifyBaseAdminRESTUrl()+"collects.json?product_id=" + productId;
            RestTemplate restTemplate = createRestTemplate();
            Collects result = restTemplate.getForObject(collectionUri, Collects.class);
            
            return result.getList();
        } catch (HttpClientErrorException e) {
            logger.error(e.getResponseBodyAsString());
            logger.error(e);
            throw e;
        }
    }
    
    public void deleteCollect(String collectId) throws RestClientException, URISyntaxException {
        RestTemplate restTemplate = createRestTemplate();
        String deleteUri = getShopifyBaseAdminRESTUrl()+"collects/" + collectId + ".json";
        restTemplate.delete(new URI(deleteUri));
    }
    
    public void deleteAllCollectForProductId(String productId) {
        List<Collect> allCollects = getCollectsForProductId(productId);
        allCollects.stream().forEach(c->{
            try {
                deleteCollect(c.getId());
            }
            catch (Throwable e) {
                logger.warn("Failed to delete collect: " + c.getId() + " might be a smart collection. " + e.getMessage());
            }
        });
    }

    public CustomCollections getAllCustomCollections() {
        RestTemplate restTemplate = createRestTemplate();
        String allCollectionUri = getShopifyBaseAdminRESTUrl()+"custom_collections.json";
        CustomCollections result = restTemplate.getForObject(allCollectionUri, CustomCollections.class);
        
        return result;
    }
}
