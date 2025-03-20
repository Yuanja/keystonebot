package com.gw.services.jomashop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.gw.domain.FeedItem;
import com.gw.services.ISyncService;
import com.gw.services.LogService;
import com.gw.services.jomashop.InventoryUpdateRequest.Status;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.*;

/**
 * @author jyuan
 *
 */
@Component
@Profile({"jomashop-prod", "jomashop-dev"})
public class JomashopSyncService implements ISyncService {
    
    final static Logger logger = LogManager.getLogger(JomashopSyncService.class);

    private @Value("${MAX_TO_DELETE_COUNT}") int maxDeletePerFeed;
    private @Value("${image.store.dir}") String imageStore;

    @Autowired
    private LogService logService;
    
    @Autowired
    private JomaShopApiService apiService;
    
    @Autowired(required=false)
    private JomaShopFeedService feedService;
    
    @Autowired
    private JomaShopProductFactory productFactory;
    
    public void sync(String sku) throws Exception{
        
        Map<String, FeedItem> itemsBySku = new HashMap<>();
        Map<String, InventoriedProduct> inventoriedProductBySku = new HashMap<>();
        
        //Get all of the item from feed;
        Map<String, FeedItem> allItemBySku = getItemsBySku();
        Map<String, InventoriedProduct> allInventoriedProductBySku = getInventoriedProductBySku();
        
        if(allItemBySku.containsKey(sku)) {
           itemsBySku.put(sku, allItemBySku.get(sku));
           if (allInventoriedProductBySku.containsKey(sku)) {
        	   inventoriedProductBySku.put(sku, allInventoriedProductBySku.get(sku));
           } 
        } else if (allInventoriedProductBySku.containsKey(sku)) {
        	inventoriedProductBySku.put(sku, allInventoriedProductBySku.get(sku));
        }
         
        if (itemsBySku.isEmpty() && inventoriedProductBySku.isEmpty()) {
        	logger.error("Sku is not found in the feed or on jomashop");
        }
        
        doSync(itemsBySku, inventoriedProductBySku);
        
        logger.info("Finished feed processing. Waiting for the next schedule");
    }
    
    private Map<String, InventoriedProduct> getInventoriedProductBySku() throws Exception {
        logger.info("Getting all inventory with skus." );
        Map<String,Inventory> inventoryBySkuMap = apiService.getInventoryBySkuMap();
        
        logger.info("Getting all products with skus." );
        Map<String, SingleProductResponse> allProductsBySku = apiService.getAllProductBySkuMap();

        Set<String> badSkusFromJomaShop = new HashSet<String>();
        Map<String, InventoriedProduct> inventoriedProductBySku = new HashMap<>();
        for (Inventory inventory : inventoryBySkuMap.values()) {
            SingleProductResponse product = null;
            if (allProductsBySku.containsKey(inventory.sku)) {
                product = allProductsBySku.get(inventory.sku); 
            } else if (allProductsBySku.containsKey(inventory.product.jomashop_sku)) {
                product = allProductsBySku.get(inventory.product.jomashop_sku);
            } else {
                logger.error("No product for corresponding inventory : " + inventory.sku);
                badSkusFromJomaShop.add(inventory.sku);
            }
            inventoriedProductBySku.put(inventory.sku, new InventoriedProduct(inventory, product));
        }
        return inventoriedProductBySku;
    }
    
    private Map<String, FeedItem> getItemsBySku() throws IOException, ParserConfigurationException, SAXException{
        List<FeedItem> listFromFeed = feedService.getItemsFromFeed();
        
        if (listFromFeed.size() == 0) {
            logger.error("Feed is temporarily offline.  Nothing is read: Skipping this schedule.");
            return Collections.emptyMap();
        }
    	
        //Check to see if there are dupes.
        Map<String, FeedItem> itemsBySku = new HashMap<String, FeedItem>();
        List<FeedItem> dupes = new ArrayList<FeedItem>();
        listFromFeed.stream().forEach(c->{
            if (itemsBySku.containsKey(c.getWebTagNumber())) {
                dupes.add(c);
            } else {
                itemsBySku.put(c.getWebTagNumber(), c);
            }
        });
        
        if (dupes.size() > 0) {
            logger.error("Feed has dupes: Skipping dupes:");
            for (FeedItem dupeSku : dupes) {
            	logger.error("dupe: "+ dupeSku.getWebTagNumber());
            	itemsBySku.remove(dupeSku.getWebTagNumber());
            }
        }
        
        //Remove certain skus that are known to fail.
        itemsBySku.remove("146221");
        itemsBySku.remove("109470");
                
        return itemsBySku;
    }
    
    /* (non-Javadoc)
     * @see com.gw.components.IGWEbayBotService#readFeedAndPost()
     */
    @Override
    public void sync(boolean feedReady) throws Exception{
        if (feedReady){
    		doSync(getItemsBySku(), getInventoriedProductBySku());
	    	logger.info("Finished feed processing. Waiting for the next schedule.");
        }
    }

    void doSync(Map<String, FeedItem>itemBySku, Map<String, InventoriedProduct> inventoriedProductBySku) 
        throws Exception {
        
        Set <Pair<String,Double>> skusToInactivate = new HashSet<Pair<String, Double>>();
        Set <Pair<String,Double>> skusToReactivate = new HashSet<Pair<String,Double>>();
        Set <String> skusActive = new HashSet<String>();
        Set <String> newSkus = new HashSet<String>();
        
        for (String sku : itemBySku.keySet()) {
            FeedItem feedItem = itemBySku.get(sku);
            if (inventoriedProductBySku.containsKey(sku)) {
                Inventory inventory = inventoriedProductBySku.get(sku).getInventory();
                SingleProductResponse product = inventoriedProductBySku.get(sku).getProduct();
                
                if ( (feedItem.getWebStatus().equalsIgnoreCase("SOLD") || feedItem.getWebStatus().equalsIgnoreCase("On Memo")) && 
                       (inventory.status.equalsIgnoreCase(Status.Active.getStringVal()) ||
                        inventory.status.equalsIgnoreCase(Status.Sold.getStringVal()))) {
                    skusToInactivate.add(new ImmutablePair<String, Double>(sku, Double.parseDouble(feedItem.getWebPriceWholesale())));
                } else if ((!feedItem.getWebStatus().equalsIgnoreCase("SOLD") && !feedItem.getWebStatus().equalsIgnoreCase("On Memo")) &&
                        product.status.equalsIgnoreCase("APPROVED") &&
                        (inventory.status.equalsIgnoreCase(Status.Inactive.getStringVal()) &&
                         !inventory.status.equalsIgnoreCase(Status.Sold.getStringVal()))) {
                    skusToReactivate.add(new ImmutablePair<String, Double>(sku, Double.parseDouble(feedItem.getWebPriceWholesale())));
                } else if ((!feedItem.getWebStatus().equalsIgnoreCase("SOLD") && !feedItem.getWebStatus().equalsIgnoreCase("On Memo")) &&
                        inventory.status.equalsIgnoreCase(Status.Active.getStringVal())) {
                    skusActive.add(feedItem.getWebTagNumber());
                } 
            } else {
                newSkus.add(sku);
            }
        }
        
        //The reverse lookup, start with inventories look for sku that fell off the feed.
        for (String sku:inventoriedProductBySku.keySet()) {
        	if (!itemBySku.containsKey(sku)) {
        		Inventory inventory = inventoriedProductBySku.get(sku).getInventory();
        		if (inventory.status.equalsIgnoreCase(Status.Active.getStringVal())) {
        			skusToInactivate.add(new ImmutablePair<String, Double>(sku, inventory.price));
        			logger.info("Found sku Active on Jomashop but not on feed: " + sku);
        		}
        	}
        }
        
        doInactivation(skusToInactivate);
        skusActive.addAll(doReactivation(skusToReactivate));
        doInsertNew(newSkus, itemBySku);
        doUpdateActiveSkus(skusActive, itemBySku, inventoriedProductBySku);
        
    }
    private void doUpdateActiveSkus(Set<String> skusActive, Map<String, FeedItem> itemBySku, 
            Map<String, InventoriedProduct> inventoriedProductBySku) throws Exception {
        
        logger.info("Active Sku that needs to check for updates count: " + skusActive.size() );
        for(String sku : skusActive ) {
            FeedItem feedItem = itemBySku.get(sku);
            
            if (feedItem == null) {
                logger.debug("Sku found active but missing from feed: " + sku);
            } else {
                SingleProductResponse spr = inventoriedProductBySku.get(sku).getProduct();
                Inventory inv = inventoriedProductBySku.get(sku).getInventory();
                
                //See if it's changed.  if so update it.
                SingleProductRequest sp = productFactory.makeProduct(feedItem);
                if (sp.product.hasErrors) {
                    String tmpErrorStr = "FIX DATA: FeedItem has error can't update for sku: " + sku;
                    logService.emailError(logger, tmpErrorStr, sp.product.getErrorMsg(), null);
                } else {
                    doPriceChange(sp, inv);
                    doChanges(sp, spr);
                }
            }
        }
    }
    
    private void doInsertNew(Set<String> newSkus, Map<String, FeedItem> itemBySku) throws Exception {
        for (String sku : newSkus) {
            logger.info("Sku is new, inserting : " + sku );
            SingleProductRequest sp = productFactory.makeProduct(itemBySku.get(sku));
            if (sp.product.hasErrors) {
                String tmpErrorStr = "FIX DATA: FeedItem has error can't insert sku: " + sku ;
                logService.emailError(logger, tmpErrorStr, sp.product.getErrorMsg(), null);
            } else {
                boolean success = apiService.createProduct(sp);
                if (!success) {
                    String tmpErrorStr = "FIX DATA: FeedItem has error insert api failed for sku: " + sku;
                    Gson gson = new GsonBuilder().setPrettyPrinting().create();
                    String spString = gson.toJson(sp);
                    logService.emailError(logger, tmpErrorStr, sp.product.getErrorMsg() + "\n RAW JSON:\n" + spString, null);
                } else 
                    logger.info("sku: " + sku +" inserted!");
            }
        }
    }
    
    private void doInactivation(Set<Pair<String, Double>> skusToInactivate) throws Exception {
        if (!skusToInactivate.isEmpty()) {
            for (Pair<String, Double> p : skusToInactivate) {
                logger.info("Inactivating Sku : " + p.getKey() );
                boolean success = apiService.markSkuInactive(p.getLeft(), p.getRight().doubleValue());
                if (!success) {
                    String tmpErrorStr = "CHECK LOG: Failed to inactivate sku: " + p.getLeft();
                    logService.emailError(logger, tmpErrorStr, tmpErrorStr, null);
                }
            }
        } else {
            logger.info("No sku to mark inactive." );
        }
    }
    
    private Set<String> doReactivation(Set<Pair<String, Double>> skusToReactivate) throws Exception {
        Set<String> skusActive = new HashSet<String>();
        if (!skusToReactivate.isEmpty()) {
            for (Pair<String, Double> p : skusToReactivate) {
                logger.info("Reactivating Sku : " + p.getLeft() );
                boolean success = apiService.markSkuActive(p.getLeft(), p.getRight().doubleValue());
                if (!success) {
                    String tmpErrorStr = "CHECK LOG: Failed to reactivate sku: " + p.getLeft();
                    logService.emailError(logger, tmpErrorStr, tmpErrorStr, null);
                } else {
                    skusActive.add(p.getLeft());
                }
            }
        } else {
            logger.info("No sku to reactivate." );
        }
        return skusActive;
    }
    
    private void doPriceChange(SingleProductRequest sp, Inventory inv) throws Exception {
        String priceChangeMessage = getInventoryPriceChange(sp,inv);
        String sku = inv.sku;
        if (StringUtils.isNotEmpty(priceChangeMessage)) {
            logger.info("Price Change detected, updating : " + sku + "\n" + priceChangeMessage );
            //Get inventory to get status.
            
            boolean success = false;
            success = apiService.updatePrice(sku, inv, sp.inventory.price);
            
            if (!success) {
                String tmpErrorStr = "CHECK LOG: Failed to update price for sku: " + sku;
                logService.emailError(logger, tmpErrorStr, tmpErrorStr, null);
            } else 
                logger.info("price: " + sku +" updated!");
        }
    }
    
    private void doChanges(SingleProductRequest sp, SingleProductResponse spr) throws Exception {
        String changesMessage = getChanges(sp,spr);
        String propertyChangesMessage = getPropertyChanges(sp, spr);
        String sku = spr.inventory.sku;
        
        if (StringUtils.isNotEmpty(changesMessage) || StringUtils.isNotEmpty(propertyChangesMessage)) {
            boolean doUpdate=false;
            if (StringUtils.isNotEmpty(changesMessage) && spr.status.equalsIgnoreCase("APPROVED")) {
                //If the category has changed and watch is approved, Can't update anything;
                if (!sp.product.category.equalsIgnoreCase(spr.category)) {
                    String tmpErrorTitle = "Potential Dead Sku alert: " + sku + " is APPROVED as " + spr.category + " But Feed has it as " + sp.product.category;
                    String tmpErrorBody = "Call JomaShop to change the category, or create a new item to replace this sku on FM.";
                    logService.emailError(logger, tmpErrorTitle, tmpErrorBody, null);
                    doUpdate = false;
                } else {
                    //These are immutable once the product is in approved state.
                    sp.product.brand = spr.brand;
                    sp.product.name = spr.name;
                    sp.product.manufacturer_number = spr.manufacturer_number;
                    String tmpErrorTitle = "Sku: " + sku + " is APPROVED and these changes won't be updated";
                    String tmpErrorBody = changesMessage;
                    logger.debug(tmpErrorTitle + "\n" + tmpErrorBody);
                    
                    if (StringUtils.isNotEmpty(propertyChangesMessage))
                        doUpdate = true;
                    else 
                        doUpdate = false;
                }
            } else {
                doUpdate=true;
            }
            
            if (doUpdate) {
                logger.info("Change detected for sku : " + sku + "\n" + changesMessage + propertyChangesMessage);
                boolean success = apiService.updateProduct(spr.jomashop_sku, sp);
                if (!success) {
                    String tmpErrorStr = "FIX DATA: FeedItem has error update api failed for sku: " + sku;
                    logService.emailError(logger, tmpErrorStr, sp.product.getErrorMsg(), null);
                } else 
                    logger.info("sku: " + sku +" updated!");
            }
        }
    }
    
    private String getInventoryPriceChange(SingleProductRequest sp, Inventory inv) {
        //Compare inventory price
        StringBuffer changeMessageBuffer = new StringBuffer();
        if (Double.compare(sp.inventory.price, inv.price) != 0){
            changeMessageBuffer.append("Price changed from: " + inv.price + " to: " + sp.inventory.price + "\n");
        }
        return changeMessageBuffer.toString();
    }
    
    private String getChanges(SingleProductRequest sp, SingleProductResponse spr){
        
        Product sproduct = sp.product;
        StringBuffer changeMessageBuffer = new StringBuffer();
        
        if (!sproduct.category.equalsIgnoreCase(spr.category)) {
            changeMessageBuffer.append("Category changed from: " + spr.category + " to: " + sproduct.category + "\n");
        }
        
        if (!sproduct.manufacturer_number.equalsIgnoreCase(spr.manufacturer_number)) {
            changeMessageBuffer.append("Manufacturer number changed from: " + spr.manufacturer_number + " to: " + sproduct.manufacturer_number + "\n");
        }
        
        if (!sproduct.name.equalsIgnoreCase(spr.name)) {
            changeMessageBuffer.append("Name changed from: " + spr.name + " to: " + sproduct.name + "\n");
        }
        
        if (!sproduct.brand.equalsIgnoreCase(spr.brand)) {
            changeMessageBuffer.append("Brand changed from: " + spr.brand + " to: " + sproduct.brand + "\n");
        }
        
        return changeMessageBuffer.toString();
    }
    
    private String getPropertyChanges(SingleProductRequest sp, SingleProductResponse spr) {
        //Compare properties
        Product sproduct = sp.product;
        StringBuffer changeMessageBuffer = new StringBuffer();
        Map<String, String> newProperties = sproduct.properties;
        Map<String, String> existingProperties = spr.vendor != null ? spr.vendor.properties : new HashMap<String, String>();
        if (!newProperties.equals(existingProperties)) {
            //Cycle throw new keys and test new vs. old values
            for (String key : newProperties.keySet()) {
                String newVal = newProperties.get(key);
                String oldVal = existingProperties.get(key);
                if (oldVal == null) {
                    changeMessageBuffer.append("Property: "+ key +" Changed from null to: " + newVal + "\n");
                } else {
                    if (!newVal.equalsIgnoreCase(oldVal))
                        changeMessageBuffer.append("Property: "+ key +" changed from: " +existingProperties+ " to: " + newProperties + "\n");
                }
            } 
            Set<String> extraOldKeys = existingProperties.keySet();
            extraOldKeys.removeAll(newProperties.keySet());
            
            for (String extraKey : extraOldKeys) {
                String extraOldVal = existingProperties.get(extraKey);
                changeMessageBuffer.append("Property: "+ extraKey + " changed from: " +extraOldVal+ " to: null\n");
            }
        }

        return changeMessageBuffer.toString();
    }

}
