package com.gw.services;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.gw.domain.FeedItem;
import com.gw.services.shopifyapi.ShopifyGraphQLService;
import com.gw.services.shopifyapi.objects.GoogleMetafield;
import com.gw.services.shopifyapi.objects.Image;
import com.gw.services.shopifyapi.objects.InventoryLevel;
import com.gw.services.shopifyapi.objects.InventoryLevels;
import com.gw.services.shopifyapi.objects.Location;
import com.gw.services.shopifyapi.objects.Option;
import com.gw.services.shopifyapi.objects.Product;
import com.gw.services.shopifyapi.objects.Variant;

public class BaseShopifyProductFactory implements IShopifyProductFactory {
    
    private static final Logger logger = LogManager.getLogger(BaseShopifyProductFactory.class);
    
    @Value("${css.hosting.url.base}") 
    private String cssHostingUrlBase;
    
    @Value("${image.source.ip}")
    private String imageSourceIp; //Reason for this is that Hostname of the feed changes, but the ip in the image field is stale.
    
    @Autowired
    private FreeMakerService freeMakerService;
    
    @Autowired
    private ImageService imageService;
    
    @Autowired 
    private ShopifyGraphQLService shopifyApiService;
    
    private List<Location> locations;
    
    @Override
	public List<Location> getLocations(){
    	if (locations == null)
    		locations = shopifyApiService.getAllLocations();
    	return locations;
    }

    @Override
	public Product createProduct(FeedItem feedItem) throws Exception {
        Product product = new Product();
        product.setBodyHtml(freeMakerService.generateFromTemplate(feedItem));
        product.setTitle(feedItem.getWebDescriptionShort());
        product.setMetafieldsGlobalTitleTag(getMetaTitle(feedItem));
        product.setMetafieldsGlobalDescriptionTag(getMetaDescription(feedItem));
        
        product.setVendor(feedItem.getWebDesigner());
        product.setProductType(feedItem.getWebCategory());
        product.setPublishedScope("global");
        setProductImages(product, feedItem);
        setDefaultVariant(product, feedItem);
        setGoogleMerchantMetafields(product, feedItem);
        return product;
    }
    
    /**
     * In the case of watches, there is only one variant per product.  
     * This is only true when single product just one option to sell. 
     * 
     * InventoryLevel is not support by the product or variant API but we are using here as a
     * data structure for merge or update cases.
     */
    private void setDefaultVariant(Product product, FeedItem feedItem) {
        Variant variant = new Variant();
        
        //This is required to indicate this is the default variant.
        //https://help.shopify.com/themes/liquid/objects/product#product-has_only_default_variant
        variant.setTitle("Default Title"); 
      
        variant.setSku(feedItem.getWebTagNumber());
        variant.setPrice(getPrice(feedItem));

        InventoryLevels invLevels = new InventoryLevels();
        for (Location loc : getLocations()) {
        	InventoryLevel inventoryLevel = new InventoryLevel();
        	inventoryLevel.setLocationId(loc.getId());
            if (feedItem.getWebStatus().equalsIgnoreCase("SOLD")) {
            	inventoryLevel.setAvailable("0");
            } else {
            	inventoryLevel.setAvailable("1");
            }
            invLevels.addInventoryLevel(inventoryLevel);
        }
        variant.setInventoryLevels(invLevels);
        
        variant.setTaxable("true");
        variant.setInventoryManagement("shopify");
        variant.setInventoryPolicy("deny");
        setOptions(product, variant, feedItem);
        product.addVariant(variant);
    }
    
    protected String getPrice(FeedItem feedItem) {
        return feedItem.getWebPriceEbay();
    }
    
    private void setOptions(Product product, Variant variant, FeedItem feedItem) {
        int optionIndex = 1;
        if (feedItem.getWebWatchDial() != null) {
            setOptionValue(product, variant, "Color", feedItem.getWebWatchDial(), optionIndex++);
        }
        
        if (feedItem.getWebWatchDiameter() != null) {
            setOptionValue(product, variant, "Size", feedItem.getWebWatchDiameter(), optionIndex++);    
        }
        
        if (feedItem.getWebMetalType() != null) {
            setOptionValue(product, variant, "Material", feedItem.getWebMetalType(), optionIndex);
        }
        
    }
    
    private void setOptionValue(Product product, Variant variant, String key, String value, int optionIndex) {
        //Shopify Google merchant sync app requires a color to be set.  
        //This requires we create an option thats named color
        //But limit the choice to the color of the dial.
        if (value != null) {
            Option option = new Option();
            option.setName(key);
            option.setPosition(Integer.toString(optionIndex));
            option.setValues(Arrays.asList(value));
            product.addOption(option);
            
            if (optionIndex == 1)
                variant.setOption1(value);
            else if (optionIndex == 2)
                variant.setOption2(value);
            else 
                variant.setOption3(value);
        }
    }
    
    private void setGoogleMerchantMetafields(Product product, FeedItem feedItem) {

        product.addMetafield(new GoogleMetafield("custom_product", "true"));
        product.addMetafield(new GoogleMetafield("age_group", "Adult"));
        product.addMetafield(new GoogleMetafield("google_product_type", "apparel & accessories > jewelry > watches"));
        
        if (feedItem.getWebStyle() != null) {
            if ("Unisex".equalsIgnoreCase(feedItem.getWebStyle())){
                product.addMetafield(new GoogleMetafield("gender", "Unisex"));
            } else if ("Gents".equalsIgnoreCase(feedItem.getWebStyle())) {
                product.addMetafield(new GoogleMetafield("gender", "Male"));
            } else {
                product.addMetafield(new GoogleMetafield("gender", "Female"));
            }
        }
        product.addMetafield(new GoogleMetafield("condition", 
                "New".equalsIgnoreCase(feedItem.getWebWatchCondition()) ? "New" : "Used")
        );
        
        product.addMetafield(new GoogleMetafield("adwords_grouping", feedItem.getWebDesigner()));
        
        if (feedItem.getWebWatchModel() != null)
            product.addMetafield(new GoogleMetafield("adwords_labels", feedItem.getWebWatchModel()));
    }
    
    private String getMetaTitle(FeedItem feedItem) {
        //Assemble the title with vendor, model, ref, 
        StringBuffer metaTitleBuff = new StringBuffer();
        appendIfNotNull(feedItem.getWebDesigner(), metaTitleBuff);
        appendIfNotNull(feedItem.getWebWatchModel(), metaTitleBuff);
        appendIfNotNull(feedItem.getWebWatchManufacturerReferenceNumber(), metaTitleBuff);
        appendIfNotNull(feedItem.getWebMetalType(), metaTitleBuff);
        
        return metaTitleBuff.toString();
    }

    protected String getMetaDescription(FeedItem feedItem) {
        StringBuffer metaDescriptionBuff = new StringBuffer();
        metaDescriptionBuff.append(getMetaTitle(feedItem)).append(" ");
        appendIfNotNull(feedItem.getWebWatchCondition(), metaDescriptionBuff);
        appendIfNotNull(feedItem.getWebStyle(), metaDescriptionBuff);
        appendIfNotNull(feedItem.getWebMetalType(), metaDescriptionBuff);
        appendIfNotNull(feedItem.getWebWatchDial(), metaDescriptionBuff);
        appendIfNotNull(feedItem.getWebWatchDiameter(), metaDescriptionBuff);
        appendIfNotNull(feedItem.getWebWatchMovement(), metaDescriptionBuff);
        appendIfNotNull(feedItem.getWebWatchYear(), metaDescriptionBuff);
        appendIfNotNull(feedItem.getWebWatchStrap(), metaDescriptionBuff);
        appendIfNotNull(feedItem.getWebWatchBoxPapers(), metaDescriptionBuff);
        
        return metaDescriptionBuff.toString().replaceAll("[\\t\\n\\r]+"," ");
    }
    
    private StringBuffer appendIfNotNull(String str, StringBuffer inBuff) {
        if (str != null)
            inBuff.append(str).append(" ");
        return inBuff;
    }
    
    protected void setProductImages(Product product, FeedItem feedItem) {
        String[] externalImageUrls = imageService.getAvailableExternalImagePathByCSS(feedItem);
        if (externalImageUrls != null) {
        	List<Image> images = new ArrayList<Image>();
            String altText = getMetaDescription(feedItem);
        	for (int i=0; i<externalImageUrls.length; i++) {
        		images.add(createImage(externalImageUrls[i], altText, i+1));
        	}
        	product.setImages(images);
        }
    }
    
    private Image createImage(String path, String altText, int position) {
        Image newImage = new Image();
        newImage.setSrc(imageService.getCorrectedImageUrl(path));
        newImage.addAltTag(altText);
        newImage.setPosition(Integer.toString(position));
        return newImage;
    }
    
    @Override
	public void mergeProduct(Product existing, Product toBeUpdatedProduct) {
        toBeUpdatedProduct.setId(existing.getId());
        
        mergeVariant(existing.getVariants(), toBeUpdatedProduct.getVariants());
        mergeOptions(existing.getOptions(), toBeUpdatedProduct.getOptions());
        mergeImages(existing.getId(), existing.getImages(), toBeUpdatedProduct.getImages());
        //Can't update metafields for the time being. 
        toBeUpdatedProduct.setMetafields(null);
    }
    
    @Override
	public void mergeExistingDescription(String exstingDescriptionHtml, String toBeUpdatedDescriptionHtml) {
        //Do blanket override with new.
    }
    
    private void mergeVariant(List<Variant> existingVariants, List<Variant> newVariants) {
        Map<String, Variant> existingVariantsBySku = existingVariants.stream().collect(Collectors.toMap(Variant::getSku, c->c)); 
        newVariants.stream().forEach(aNewVar-> {
            if (existingVariantsBySku.containsKey(aNewVar.getSku())) {
                aNewVar.setId(existingVariantsBySku.get(aNewVar.getSku()).getId());
                aNewVar.setInventoryItemId(existingVariantsBySku.get(aNewVar.getSku()).getInventoryItemId());
                mergeInventoryLevels(existingVariantsBySku.get(aNewVar.getSku()).getInventoryLevels(), aNewVar.getInventoryLevels());
            }
        });
    }
    
    @Override
	public void mergeInventoryLevels(InventoryLevels existingInventoryLevels, InventoryLevels newInventoryLevels) {
		// Handle null existingInventoryLevels gracefully (GraphQL API doesn't always populate this)
		if (existingInventoryLevels == null || existingInventoryLevels.get() == null) {
			logger.warn("Existing inventory levels are null - this is expected with GraphQL API for some scenarios");
			return;
		}
		
		if (newInventoryLevels == null || newInventoryLevels.get() == null) {
			logger.warn("New inventory levels are null - skipping merge");
			return;
		}
		
    	for (InventoryLevel invLevel : existingInventoryLevels.get()) {
    		InventoryLevel aNewInvLevel = newInventoryLevels.getByLocationId(invLevel.getLocationId());
    		if (aNewInvLevel != null) {
    			aNewInvLevel.setInventoryItemId(invLevel.getInventoryItemId());
    		}
    	}
    }
    
    private void mergeOptions(List<Option> existingOptions, List<Option> newOptions) {
        // Handle null existingOptions gracefully (GraphQL API doesn't always populate this)
        if (existingOptions == null) {
            logger.warn("Existing options are null - this is expected with GraphQL API for some scenarios");
            return;
        }
        
        if (newOptions == null) {
            logger.warn("New options are null - skipping merge");
            return;
        }
        
        Map<String, Option> existingOptionsByName = existingOptions.stream().collect(Collectors.toMap(Option::getName, c->c)); 
        newOptions.stream().forEach(c-> {
            if (existingOptionsByName.containsKey(c.getName())) {
                c.setId(existingOptionsByName.get(c.getName()).getId());
            } 
        });
    }
    
    private void mergeImages(String productId, List<Image> existingImages, List<Image> newImages) {
    	newImages.stream().forEach(c->{c.setProductId(productId);});
    	
        Map<String, Image> existingImagesByName = existingImages.stream().collect(Collectors.toMap(Image::getSrc, c->c)); 
        newImages.stream().forEach(c-> {
            if (existingImagesByName.containsKey(c.getSrc())) {
                c.setId(existingImagesByName.get(c.getSrc()).getId());
                c.setProductId(productId);
            } 
        });
    }
}
