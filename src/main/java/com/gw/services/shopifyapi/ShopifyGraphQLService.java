package com.gw.services.shopifyapi;

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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gw.domain.PredefinedCollection;
import com.gw.services.CollectionUtility;
import com.gw.services.LogService;
import com.gw.services.shopifyapi.objects.Collect;
import com.gw.services.shopifyapi.objects.CustomCollection;
import com.gw.services.shopifyapi.objects.Image;
import com.gw.services.shopifyapi.objects.InventoryLevel;
import com.gw.services.shopifyapi.objects.Location;
import com.gw.services.shopifyapi.objects.Product;
import com.gw.services.shopifyapi.objects.Variant;
import com.gw.services.shopifyapi.objects.Metafield;

/**
 * Shopify GraphQL API Service
 * 
 * This service provides complete integration with Shopify's GraphQL API,
 * following the official documentation at https://shopify.dev/docs/api/admin-graphql
 * 
 * Key Benefits:
 * - More efficient data fetching with precise field selection
 * - Better pagination using cursor-based pagination
 * - Reduced API calls through nested queries
 * - Type-safe queries and mutations
 * - Modern GraphQL error handling
 * 
 * Authentication:
 * - Uses X-Shopify-Access-Token header
 * - Endpoint: {shop}.myshopify.com/admin/api/{version}/graphql.json
 * - Supports API version 2025-04 and newer
 * 
 * GraphQL Features:
 * - Global IDs (GIDs) are used instead of numeric IDs (e.g., "gid://shopify/Product/123")
 * - Pagination uses cursor-based approach
 * - Error handling includes both HTTP errors and GraphQL user errors
 * - Efficient collection management with automatic publication
 * 
 * @author jyuan
 * @since 2025
 */
@Component
public class ShopifyGraphQLService {
    
    private static Logger logger = LogManager.getLogger(ShopifyGraphQLService.class);

    @Autowired 
    LogService logService;
    
    @Value("${SHOPIFY_AUTH_PASSWD}") 
    private String shopifyAccessToken;
    
    @Value("${SHOPIFY_REST_URL}") 
    private String shopifyStoreUrl;
    
    @Value("${SHOPIFY_ADMIN_API_VERSION:2025-04}") 
    private String apiVersion;
    
    private ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Get the GraphQL endpoint URL
     */
    private String getGraphQLEndpoint() {
        return shopifyStoreUrl + "/admin/api/" + apiVersion + "/graphql.json";
    }
    
    /**
     * Create HTTP headers with authentication
     */
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Shopify-Access-Token", shopifyAccessToken);
        return headers;
    }
    
    /**
     * Execute GraphQL query
     */
    private JsonNode executeGraphQLQuery(String query) throws Exception {
        return executeGraphQLQuery(query, null);
    }
    
    /**
     * Execute GraphQL query with variables
     */
    private JsonNode executeGraphQLQuery(String query, Map<String, Object> variables) throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("query", query);
        if (variables != null) {
            requestBody.put("variables", variables);
        }
        
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, createHeaders());
        
        try {
            String response = restTemplate.postForObject(getGraphQLEndpoint(), entity, String.class);
            JsonNode jsonResponse = objectMapper.readTree(response);
            
            // Check for GraphQL errors
            if (jsonResponse.has("errors")) {
                JsonNode errors = jsonResponse.get("errors");
                logger.error("GraphQL errors: " + errors.toString());
                throw new RuntimeException("GraphQL query failed: " + errors.toString());
            }
            
            return jsonResponse.get("data");
        } catch (HttpClientErrorException e) {
            logger.error("HTTP error: " + e.getResponseBodyAsString());
            throw e;
        }
    }
    
    /**
     * Get product count using GraphQL
     */
    public int getProductCount() {
        String query = """
            query {
                products {
                    totalCount
                }
            }
            """;
        
        try {
            JsonNode data = executeGraphQLQuery(query);
            return data.get("products").get("totalCount").asInt();
        } catch (Exception e) {
            logger.error("Error getting product count", e);
            throw new RuntimeException("Failed to get product count", e);
        }
    }
    
    /**
     * Get product by ID using GraphQL
     */
    public Product getProductByProductId(String id) {
        String query = """
            query getProduct($id: ID!) {
                product(id: $id) {
                    id
                    title
                    handle
                    description
                    status
                    vendor
                    productType
                    tags
                    createdAt
                    updatedAt
                    publishedAt
                    images(first: 10) {
                        edges {
                            node {
                                id
                                url
                                altText
                            }
                        }
                    }
                    variants(first: 100) {
                        edges {
                            node {
                                id
                                title
                                sku
                                price
                                compareAtPrice
                                weight
                                weightUnit
                                inventoryItem {
                                    id
                                }
                                inventoryPolicy
                                requiresShipping
                                taxable
                                barcode
                                position
                            }
                        }
                    }
                    metafields(first: 50) {
                        edges {
                            node {
                                id
                                key
                                value
                                namespace
                                type
                            }
                        }
                    }
                }
            }
            """;
        
        Map<String, Object> variables = new HashMap<>();
        variables.put("id", "gid://shopify/Product/" + id);
        
        try {
            JsonNode data = executeGraphQLQuery(query, variables);
            JsonNode productNode = data.get("product");
            
            if (productNode == null || productNode.isNull()) {
                logger.error("Product not found with ID: " + id);
                return null;
            }
            
            return convertJsonToProduct(productNode);
        } catch (Exception e) {
            logger.error("Error getting product by ID: " + id, e);
            return null;
        }
    }
    
    /**
     * Get all products using GraphQL with pagination
     */
    public List<Product> getAllProducts() {
        List<Product> allProducts = new ArrayList<>();
        String cursor = null;
        boolean hasNextPage = true;
        
        while (hasNextPage) {
            String query = """
                query getProducts($cursor: String) {
                    products(first: 250, after: $cursor) {
                        edges {
                            node {
                                id
                                title
                                handle
                                description
                                status
                                vendor
                                productType
                                tags
                                createdAt
                                updatedAt
                                publishedAt
                                variants(first: 100) {
                                    edges {
                                        node {
                                            id
                                            title
                                            sku
                                            price
                                            inventoryItem {
                                                id
                                            }
                                        }
                                    }
                                }
                            }
                            cursor
                        }
                        pageInfo {
                            hasNextPage
                            endCursor
                        }
                    }
                }
                """;
            
            Map<String, Object> variables = new HashMap<>();
            if (cursor != null) {
                variables.put("cursor", cursor);
            }
            
            try {
                JsonNode data = executeGraphQLQuery(query, variables);
                JsonNode productsNode = data.get("products");
                JsonNode edges = productsNode.get("edges");
                
                for (JsonNode edge : edges) {
                    JsonNode productNode = edge.get("node");
                    Product product = convertJsonToProduct(productNode);
                    allProducts.add(product);
                }
                
                JsonNode pageInfo = productsNode.get("pageInfo");
                hasNextPage = pageInfo.get("hasNextPage").asBoolean();
                if (hasNextPage) {
                    cursor = pageInfo.get("endCursor").asText();
                }
                
            } catch (Exception e) {
                logger.error("Error getting all products", e);
                break;
            }
        }
        
        return allProducts;
    }
    
    /**
     * Add/Create a new product using GraphQL
     */
    public Product addProduct(Product product) {
        String mutation = """
            mutation productCreate($input: ProductInput!) {
                productCreate(input: $input) {
                    product {
                        id
                        title
                        handle
                        description
                        status
                        vendor
                        productType
                        tags
                        images(first: 10) {
                            edges {
                                node {
                                    id
                                    url
                                    altText
                                }
                            }
                        }
                        variants(first: 1) {
                            edges {
                                node {
                                    id
                                    title
                                    sku
                                    price
                                    inventoryItem {
                                        id
                                    }
                                }
                            }
                        }
                    }
                    userErrors {
                        field
                        message
                    }
                }
            }
            """;
        
        Map<String, Object> input = createProductInput(product);
        Map<String, Object> variables = new HashMap<>();
        variables.put("input", input);
        
        try {
            JsonNode data = executeGraphQLQuery(mutation, variables);
            JsonNode productCreate = data.get("productCreate");
            
            // Check for user errors
            JsonNode userErrors = productCreate.get("userErrors");
            if (userErrors != null && userErrors.size() > 0) {
                logger.error("Product creation failed with user errors: " + userErrors.toString());
                throw new RuntimeException("Product creation failed: " + userErrors.toString());
            }
            
            JsonNode productNode = productCreate.get("product");
            return convertJsonToProduct(productNode);
            
        } catch (Exception e) {
            logger.error("Error creating product", e);
            throw new RuntimeException("Failed to create product", e);
        }
    }
    
    /**
     * Update product using GraphQL
     */
    public void updateProduct(Product product) throws Exception {
        if (product == null || product.getId() == null) {
            throw new IllegalArgumentException("Product ID cannot be null for update");
        }
        
        String mutation = """
            mutation productUpdate($input: ProductInput!) {
                productUpdate(input: $input) {
                    product {
                        id
                        title
                        images(first: 10) {
                            edges {
                                node {
                                    id
                                    url
                                    altText
                                }
                            }
                        }
                    }
                    userErrors {
                        field
                        message
                    }
                }
            }
            """;
        
        Map<String, Object> input = createProductInput(product);
        input.put("id", "gid://shopify/Product/" + product.getId());
        
        Map<String, Object> variables = new HashMap<>();
        variables.put("input", input);
        
        try {
            JsonNode data = executeGraphQLQuery(mutation, variables);
            JsonNode productUpdate = data.get("productUpdate");
            
            // Check for user errors
            JsonNode userErrors = productUpdate.get("userErrors");
            if (userErrors != null && userErrors.size() > 0) {
                logger.error("Product update failed with user errors: " + userErrors.toString());
                throw new RuntimeException("Product update failed: " + userErrors.toString());
            }
            
        } catch (Exception e) {
            logger.error("Error updating product: " + product.getId(), e);
            throw e;
        }
    }
    
    /**
     * Delete product by ID using GraphQL
     */
    public void deleteProductById(String id) throws Exception {
        String mutation = """
            mutation productDelete($input: ProductDeleteInput!) {
                productDelete(input: $input) {
                    deletedProductId
                    userErrors {
                        field
                        message
                    }
                }
            }
            """;
        
        Map<String, Object> input = new HashMap<>();
        input.put("id", "gid://shopify/Product/" + id);
        
        Map<String, Object> variables = new HashMap<>();
        variables.put("input", input);
        
        try {
            JsonNode data = executeGraphQLQuery(mutation, variables);
            JsonNode productDelete = data.get("productDelete");
            
            // Check for user errors
            JsonNode userErrors = productDelete.get("userErrors");
            if (userErrors != null && userErrors.size() > 0) {
                logger.error("Product deletion failed with user errors: " + userErrors.toString());
                throw new RuntimeException("Product deletion failed: " + userErrors.toString());
            }
            
        } catch (Exception e) {
            logger.error("Error deleting product: " + id, e);
            throw e;
        }
    }
    
    /**
     * Get all locations using GraphQL
     */
    public List<Location> getAllLocations() {
        String query = """
            query {
                locations(first: 250) {
                    edges {
                        node {
                            id
                            name
                            address {
                                address1
                                address2
                                city
                                province
                                country
                                zip
                            }
                        }
                    }
                }
            }
            """;
        
        try {
            JsonNode data = executeGraphQLQuery(query);
            JsonNode locationsNode = data.get("locations");
            JsonNode edges = locationsNode.get("edges");
            
            List<Location> locations = new ArrayList<>();
            for (JsonNode edge : edges) {
                JsonNode locationNode = edge.get("node");
                Location location = convertJsonToLocation(locationNode);
                locations.add(location);
            }
            
            return locations;
        } catch (Exception e) {
            logger.error("Error getting all locations", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Get inventory levels by location ID using GraphQL
     */
    public List<InventoryLevel> getAllInventoryLevelByLocation(String locationId) {
        List<InventoryLevel> allLevels = new ArrayList<>();
        String cursor = null;
        boolean hasNextPage = true;
        
        while (hasNextPage) {
            String query = """
                query getInventoryLevels($locationId: ID!, $cursor: String) {
                    location(id: $locationId) {
                        inventoryLevels(first: 250, after: $cursor) {
                            edges {
                                node {
                                    id
                                    quantities(names: ["available"]) {
                                        name
                                        quantity
                                    }
                                    item {
                                        id
                                        sku
                                    }
                                }
                                cursor
                            }
                            pageInfo {
                                hasNextPage
                                endCursor
                            }
                        }
                    }
                }
                """;
            
            Map<String, Object> variables = new HashMap<>();
            variables.put("locationId", "gid://shopify/Location/" + locationId);
            if (cursor != null) {
                variables.put("cursor", cursor);
            }
            
            try {
                JsonNode data = executeGraphQLQuery(query, variables);
                JsonNode locationNode = data.get("location");
                JsonNode inventoryLevelsNode = locationNode.get("inventoryLevels");
                JsonNode edges = inventoryLevelsNode.get("edges");
                
                for (JsonNode edge : edges) {
                    JsonNode levelNode = edge.get("node");
                    InventoryLevel level = convertJsonToInventoryLevel(levelNode);
                    allLevels.add(level);
                }
                
                JsonNode pageInfo = inventoryLevelsNode.get("pageInfo");
                hasNextPage = pageInfo.get("hasNextPage").asBoolean();
                if (hasNextPage) {
                    cursor = pageInfo.get("endCursor").asText();
                }
                
            } catch (Exception e) {
                logger.error("Error getting inventory levels for location: " + locationId, e);
                break;
            }
        }
        
        return allLevels;
    }
    
    /**
     * Update inventory levels using GraphQL
     */
    public void updateInventoryLevels(List<InventoryLevel> inventoryLevels) throws Exception {
        for (InventoryLevel level : inventoryLevels) {
            updateInventoryLevel(level);
        }
    }
    
    /**
     * Update single inventory level using GraphQL
     */
    private void updateInventoryLevel(InventoryLevel level) throws Exception {
        String mutation = """
            mutation inventoryAdjustQuantities($input: InventoryAdjustQuantitiesInput!) {
                inventoryAdjustQuantities(input: $input) {
                    inventoryAdjustmentGroup {
                        reason
                        changes {
                            name
                            delta
                        }
                    }
                    userErrors {
                        field
                        message
                    }
                }
            }
            """;
        
        Map<String, Object> input = new HashMap<>();
        input.put("reason", "correction");
        input.put("name", "available");
        
        List<Map<String, Object>> changes = new ArrayList<>();
        Map<String, Object> change = new HashMap<>();
        change.put("delta", Integer.valueOf(level.getAvailable()));
        change.put("inventoryItemId", "gid://shopify/InventoryItem/" + level.getInventoryItemId());
        change.put("locationId", "gid://shopify/Location/" + level.getLocationId());
        changes.add(change);
        
        input.put("changes", changes);
        
        Map<String, Object> variables = new HashMap<>();
        variables.put("input", input);
        
        try {
            JsonNode data = executeGraphQLQuery(mutation, variables);
            JsonNode inventoryAdjust = data.get("inventoryAdjustQuantities");
            
            // Check for user errors
            JsonNode userErrors = inventoryAdjust.get("userErrors");
            if (userErrors != null && userErrors.size() > 0) {
                logger.error("Inventory update failed with user errors: " + userErrors.toString());
                throw new RuntimeException("Inventory update failed: " + userErrors.toString());
            }
            
        } catch (Exception e) {
            logger.error("Error updating inventory level", e);
            throw e;
        }
    }
    
    /**
     * Get all custom collections using GraphQL
     */
    public List<CustomCollection> getAllCustomCollections() {
        String query = """
            query {
                collections(first: 250) {
                    edges {
                        node {
                            id
                            title
                            handle
                            description
                            sortOrder
                            updatedAt
                        }
                    }
                }
            }
            """;
        
        try {
            JsonNode data = executeGraphQLQuery(query);
            JsonNode collectionsNode = data.get("collections");
            JsonNode edges = collectionsNode.get("edges");
            
            List<CustomCollection> collections = new ArrayList<>();
            for (JsonNode edge : edges) {
                JsonNode collectionNode = edge.get("node");
                CustomCollection collection = convertJsonToCustomCollection(collectionNode);
                collections.add(collection);
            }
            
            return collections;
        } catch (Exception e) {
            logger.error("Error getting all custom collections", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Create custom collection using GraphQL
     */
    public CustomCollection createCustomCollection(PredefinedCollection collectionEnum) {
        String mutation = """
            mutation collectionCreate($input: CollectionInput!) {
                collectionCreate(input: $input) {
                    collection {
                        id
                        title
                        handle
                        description
                        sortOrder
                    }
                    userErrors {
                        field
                        message
                    }
                }
            }
            """;
        
        Map<String, Object> input = new HashMap<>();
        input.put("title", collectionEnum.getTitle());
        input.put("sortOrder", "CREATED_DESC");
        
        Map<String, Object> variables = new HashMap<>();
        variables.put("input", input);
        
        try {
            JsonNode data = executeGraphQLQuery(mutation, variables);
            JsonNode collectionCreate = data.get("collectionCreate");
            
            // Check for user errors
            JsonNode userErrors = collectionCreate.get("userErrors");
            if (userErrors != null && userErrors.size() > 0) {
                logger.error("Collection creation failed with user errors: " + userErrors.toString());
                throw new RuntimeException("Collection creation failed: " + userErrors.toString());
            }
            
            JsonNode collectionNode = collectionCreate.get("collection");
            return convertJsonToCustomCollection(collectionNode);
            
        } catch (Exception e) {
            logger.error("Error creating custom collection", e);
            throw new RuntimeException("Failed to create custom collection", e);
        }
    }
    
    /**
     * Delete custom collection by ID using GraphQL
     */
    public void deleteCustomCollectionsById(String id) throws Exception {
        String mutation = """
            mutation collectionDelete($input: CollectionDeleteInput!) {
                collectionDelete(input: $input) {
                    deletedCollectionId
                    userErrors {
                        field
                        message
                    }
                }
            }
            """;
        
        Map<String, Object> input = new HashMap<>();
        input.put("id", "gid://shopify/Collection/" + id);
        
        Map<String, Object> variables = new HashMap<>();
        variables.put("input", input);
        
        try {
            JsonNode data = executeGraphQLQuery(mutation, variables);
            JsonNode collectionDelete = data.get("collectionDelete");
            
            // Check for user errors
            JsonNode userErrors = collectionDelete.get("userErrors");
            if (userErrors != null && userErrors.size() > 0) {
                logger.error("Collection deletion failed with user errors: " + userErrors.toString());
                throw new RuntimeException("Collection deletion failed: " + userErrors.toString());
            }
            
        } catch (Exception e) {
            logger.error("Error deleting collection: " + id, e);
            throw e;
        }
    }
    
    // Helper methods for conversion
    
    private Product convertJsonToProduct(JsonNode productNode) {
        Product product = new Product();
        
        String gid = productNode.get("id").asText();
        product.setId(extractIdFromGid(gid));
        product.setTitle(productNode.get("title").asText());
        
        if (productNode.has("handle")) {
            product.setHandle(productNode.get("handle").asText());
        }
        if (productNode.has("description")) {
            product.setBodyHtml(productNode.get("description").asText());
        }
        if (productNode.has("vendor")) {
            product.setVendor(productNode.get("vendor").asText());
        }
        if (productNode.has("productType")) {
            product.setProductType(productNode.get("productType").asText());
        }
        if (productNode.has("tags")) {
            product.setTags(productNode.get("tags").asText());
        }
        if (productNode.has("publishedAt") && !productNode.get("publishedAt").isNull()) {
            product.setPublishedAt(productNode.get("publishedAt").asText());
        }
        if (productNode.has("status") && !productNode.get("status").isNull()) {
            product.setStatus(productNode.get("status").asText());
        }
        
        // Convert variants
        if (productNode.has("variants")) {
            JsonNode variantsNode = productNode.get("variants").get("edges");
            List<Variant> variants = new ArrayList<>();
            for (JsonNode edge : variantsNode) {
                JsonNode variantNode = edge.get("node");
                Variant variant = convertJsonToVariant(variantNode);
                variants.add(variant);
            }
            product.setVariants(variants);
        }
        
        // Convert images
        if (productNode.has("images")) {
            JsonNode imagesNode = productNode.get("images").get("edges");
            List<Image> images = new ArrayList<>();
            for (JsonNode edge : imagesNode) {
                JsonNode imageNode = edge.get("node");
                Image image = convertJsonToImage(imageNode);
                images.add(image);
            }
            product.setImages(images);
        }
        
        return product;
    }
    
    private Variant convertJsonToVariant(JsonNode variantNode) {
        Variant variant = new Variant();
        
        String gid = variantNode.get("id").asText();
        variant.setId(extractIdFromGid(gid));
        
        if (variantNode.has("title")) {
            variant.setTitle(variantNode.get("title").asText());
        }
        if (variantNode.has("sku")) {
            variant.setSku(variantNode.get("sku").asText());
        }
        if (variantNode.has("price")) {
            variant.setPrice(variantNode.get("price").asText());
        }
        if (variantNode.has("inventoryItem") && variantNode.get("inventoryItem").has("id")) {
            String inventoryItemGid = variantNode.get("inventoryItem").get("id").asText();
            variant.setInventoryItemId(extractIdFromGid(inventoryItemGid));
        }
        
        return variant;
    }
    
    private Image convertJsonToImage(JsonNode imageNode) {
        Image image = new Image();
        
        String gid = imageNode.get("id").asText();
        image.setId(extractIdFromGid(gid));
        
        if (imageNode.has("url")) {
            image.setSrc(imageNode.get("url").asText());
        }
        if (imageNode.has("altText")) {
            image.addAltTag(imageNode.get("altText").asText());
        }
        
        return image;
    }
    
    private Location convertJsonToLocation(JsonNode locationNode) {
        Location location = new Location();
        
        String gid = locationNode.get("id").asText();
        location.setId(extractIdFromGid(gid));
        location.setName(locationNode.get("name").asText());
        
        // Handle address
        if (locationNode.has("address")) {
            JsonNode addressNode = locationNode.get("address");
            if (addressNode.has("address1")) {
                location.setAddress1(addressNode.get("address1").asText());
            }
            if (addressNode.has("city")) {
                location.setCity(addressNode.get("city").asText());
            }
            if (addressNode.has("province")) {
                location.setProvince(addressNode.get("province").asText());
            }
            if (addressNode.has("country")) {
                location.setCountry(addressNode.get("country").asText());
            }
            if (addressNode.has("zip")) {
                location.setZip(addressNode.get("zip").asText());
            }
        }
        
        return location;
    }
    
    private InventoryLevel convertJsonToInventoryLevel(JsonNode levelNode) {
        InventoryLevel level = new InventoryLevel();
        
        // Extract inventory item ID from the item field, not the level's ID
        if (levelNode.has("item") && levelNode.get("item").has("id")) {
            String inventoryItemGid = levelNode.get("item").get("id").asText();
            level.setInventoryItemId(extractIdFromGid(inventoryItemGid));
        }
        
        // Extract location ID if available
        if (levelNode.has("location") && levelNode.get("location").has("id")) {
            String locationGid = levelNode.get("location").get("id").asText();
            level.setLocationId(extractIdFromGid(locationGid));
        }
        
        if (levelNode.has("quantities")) {
            JsonNode quantities = levelNode.get("quantities");
            for (JsonNode quantity : quantities) {
                if ("available".equals(quantity.get("name").asText())) {
                    level.setAvailable(String.valueOf(quantity.get("quantity").asInt()));
                    break;
                }
            }
        }
        
        return level;
    }
    
    private CustomCollection convertJsonToCustomCollection(JsonNode collectionNode) {
        CustomCollection collection = new CustomCollection();
        
        String gid = collectionNode.get("id").asText();
        collection.setId(extractIdFromGid(gid));
        collection.setTitle(collectionNode.get("title").asText());
        
        if (collectionNode.has("handle")) {
            collection.setHandle(collectionNode.get("handle").asText());
        }
        if (collectionNode.has("description")) {
            collection.setBodyHtml(collectionNode.get("description").asText());
        }
        if (collectionNode.has("sortOrder")) {
            collection.setSortOrder(collectionNode.get("sortOrder").asText().toLowerCase().replace("_", "-"));
        }
        
        return collection;
    }
    
    private Map<String, Object> createProductInput(Product product) {
        Map<String, Object> input = new HashMap<>();
        
        if (product.getTitle() != null) {
            input.put("title", product.getTitle());
        }
        if (product.getBodyHtml() != null) {
            input.put("descriptionHtml", product.getBodyHtml());
        }
        if (product.getHandle() != null) {
            input.put("handle", product.getHandle());
        }
        if (product.getVendor() != null) {
            input.put("vendor", product.getVendor());
        }
        if (product.getProductType() != null) {
            input.put("productType", product.getProductType());
        }
        if (product.getTags() != null) {
            input.put("tags", product.getTags());
        }
        
        // Note: Images cannot be added directly in ProductInput via GraphQL
        // They must be added separately using productImageCreate mutation after product creation
        
        // Handle variants
        if (product.getVariants() != null && !product.getVariants().isEmpty()) {
            List<Map<String, Object>> variants = new ArrayList<>();
            for (Variant variant : product.getVariants()) {
                Map<String, Object> variantInput = new HashMap<>();
                
                if (variant.getTitle() != null) {
                    variantInput.put("title", variant.getTitle());
                }
                if (variant.getSku() != null) {
                    variantInput.put("sku", variant.getSku());
                }
                if (variant.getPrice() != null) {
                    variantInput.put("price", variant.getPrice());
                }
                
                variants.add(variantInput);
            }
            input.put("variants", variants);
        }
        
        return input;
    }
    
    /**
     * Extract numeric ID from Shopify GraphQL Global ID (GID)
     * Example: "gid://shopify/Product/123456" -> "123456"
     */
    private String extractIdFromGid(String gid) {
        if (gid == null) return null;
        String[] parts = gid.split("/");
        return parts[parts.length - 1];
    }
    
    // Migration helper methods to maintain compatibility
    
    /**
     * Delete product by ID with error logging (compatibility method)
     */
    public void deleteProductByIdOrLogFailure(String id) {
        try {
            deleteProductById(id);
        } catch (Exception e) {
            logService.emailError(logger, "Shopify Bot: Error while removing product id: " + id, null, e);
        }
    }
    
    /**
     * Remove all products (compatibility method)
     */
    public void removeAllProducts() throws Exception {
        List<Product> allProducts = getAllProducts();
        for (Product product : allProducts) {
            deleteProductById(product.getId());
        }
    }
    
    /**
     * Remove all collections (compatibility method)
     */
    public void removeAllCollections() throws Exception {
        List<CustomCollection> allCollections = getAllCustomCollections();
        for (CustomCollection collection : allCollections) {
            deleteCustomCollectionsById(collection.getId());
        }
    }
    
    /**
     * Find and remove duplicate listings (compatibility method)
     */
    public Map<String, Product> unlistDupeListings() {
        List<Product> allProducts = getAllProducts();
        Map<String, Product> cleanMapOfProductBySku = new HashMap<>();
        
        for (Product currentProduct : allProducts) {
            List<Variant> variants = currentProduct.getVariants();
            if (variants == null || variants.isEmpty() || StringUtils.isEmpty(variants.get(0).getSku())) {
                logger.error("Product id: " + currentProduct.getId() + " has no Variant! Can't resolve Sku deleting from shopify.");
                deleteProductByIdOrLogFailure(currentProduct.getId());
            } else {
                String currentProductSku = variants.get(0).getSku();
                if (cleanMapOfProductBySku.containsKey(currentProductSku)) {
                    logger.error("Found duplicate product ID: " + currentProduct.getId() 
                        + " SKU: " + currentProductSku + " Removing from shopify!");
                    deleteProductByIdOrLogFailure(currentProduct.getId());
                } else {
                    cleanMapOfProductBySku.put(currentProductSku, currentProduct);
                }
            }
        }
        
        return cleanMapOfProductBySku;
    }
    
    /**
     * Ensure configured collections exist (compatibility method)
     */
    public HashMap<PredefinedCollection, CustomCollection> ensureConfiguredCollections(PredefinedCollection[] predefinedCollections) {
        List<CustomCollection> allCollectionsFromShopify = getAllCustomCollections();
        Map<String, CustomCollection> collectionByTitleFromShopify = 
                allCollectionsFromShopify.stream().collect(Collectors.toMap(CustomCollection::getTitle, Function.identity()));
        
        HashMap<PredefinedCollection, CustomCollection> customCollectionByEnum 
            = new HashMap<>(); 
        
        for (PredefinedCollection collectionEnum : predefinedCollections) {
            if (!collectionByTitleFromShopify.containsKey(collectionEnum.getTitle())) {
                logger.info("Creating custom collection name: " + collectionEnum.getTitle());
                // Use the new method that ensures publication to all channels
                CustomCollection result = createCustomCollectionWithPublication(collectionEnum);
                customCollectionByEnum.put(collectionEnum, result);
            } else {
                CustomCollection existingCollection = collectionByTitleFromShopify.get(collectionEnum.getTitle());
                // Ensure existing collection is published to all channels
                try {
                    publishCollectionToAllChannels(existingCollection.getId());
                    logger.info("Ensured existing collection '" + existingCollection.getTitle() + "' is published to all channels");
                } catch (Exception e) {
                    logger.warn("Failed to ensure existing collection is published to all channels (may already be published): " + e.getMessage());
                }
                customCollectionByEnum.put(collectionEnum, existingCollection);
            }
        }
        
        return customCollectionByEnum;
    }
    
    /**
     * Get images by product ID using GraphQL
     */
    public List<Image> getImagesByProduct(String productId) {
        String query = """
            query getProductImages($id: ID!) {
                product(id: $id) {
                    images(first: 250) {
                        edges {
                            node {
                                id
                                url
                                altText
                            }
                        }
                    }
                }
            }
            """;
        
        Map<String, Object> variables = new HashMap<>();
        variables.put("id", "gid://shopify/Product/" + productId);
        
        try {
            JsonNode data = executeGraphQLQuery(query, variables);
            JsonNode productNode = data.get("product");
            
            if (productNode == null) {
                return new ArrayList<>();
            }
            
            JsonNode imagesNode = productNode.get("images").get("edges");
            List<Image> images = new ArrayList<>();
            for (JsonNode edge : imagesNode) {
                JsonNode imageNode = edge.get("node");
                Image image = convertJsonToImage(imageNode);
                images.add(image);
            }
            
            return images;
        } catch (Exception e) {
            logger.error("Got error while trying to get Images By Product Id:" + productId, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Delete images using GraphQL
     */
    public void deleteImages(String productId, List<Image> images) {
        for (Image img : images) {
            deleteImageById(productId, img.getId());
        }
    }
    
    /**
     * Delete a single image by ID using GraphQL
     */
    private void deleteImageById(String productId, String imageId) {
        String mutation = """
            mutation productImageDelete($productId: ID!, $id: ID!) {
                productImageDelete(productId: $productId, id: $id) {
                    deletedImageId
                    userErrors {
                        field
                        message
                    }
                }
            }
            """;
        
        Map<String, Object> variables = new HashMap<>();
        variables.put("productId", "gid://shopify/Product/" + productId);
        variables.put("id", "gid://shopify/ProductImage/" + imageId);
        
        try {
            JsonNode data = executeGraphQLQuery(mutation, variables);
            JsonNode imageDelete = data.get("productImageDelete");
            
            // Check for user errors
            JsonNode userErrors = imageDelete.get("userErrors");
            if (userErrors != null && userErrors.size() > 0) {
                logger.error("Image deletion failed with user errors: " + userErrors.toString());
            }
            
        } catch (Exception e) {
            logger.error("Got error while trying to delete Images By Product Id:" 
                + productId + " Image Id: " + imageId, e);
        }
    }
    
    /**
     * Delete all images by product ID (compatibility method)
     */
    public void deleteAllImageByProductId(String productId) {
        deleteImages(productId, getImagesByProduct(productId));
    }
    
    /**
     * Add images to an existing product using GraphQL
     */
    public void addImagesToProduct(String productId, List<Image> images) {
        for (Image image : images) {
            addImageToProduct(productId, image);
        }
    }
    
    /**
     * Add a single image to an existing product using GraphQL
     */
    private void addImageToProduct(String productId, Image image) {
        String mutation = """
            mutation productAppendImages($input: ProductAppendImagesInput!) {
                productAppendImages(input: $input) {
                    newImages {
                        id
                        url
                        altText
                    }
                    product {
                        id
                    }
                    userErrors {
                        field
                        message
                    }
                }
            }
            """;
        
        Map<String, Object> imageInput = new HashMap<>();
        if (image.getSrc() != null) {
            imageInput.put("src", image.getSrc());
        }
        
        // Handle alt text from metafields
        if (image.getMetafields() != null && !image.getMetafields().isEmpty()) {
            for (Metafield metafield : image.getMetafields()) {
                if ("tags".equals(metafield.getNamespace()) && "alt".equals(metafield.getKey())) {
                    imageInput.put("altText", metafield.getValue());
                    break;
                }
            }
        }
        
        Map<String, Object> input = new HashMap<>();
        input.put("id", "gid://shopify/Product/" + productId);
        input.put("images", List.of(imageInput));
        
        Map<String, Object> variables = new HashMap<>();
        variables.put("input", input);
        
        try {
            JsonNode data = executeGraphQLQuery(mutation, variables);
            JsonNode imageAppend = data.get("productAppendImages");
            
            // Check for user errors
            JsonNode userErrors = imageAppend.get("userErrors");
            if (userErrors != null && userErrors.size() > 0) {
                logger.error("Image append failed with user errors: " + userErrors.toString());
                throw new RuntimeException("Image append failed: " + userErrors.toString());
            }
            
        } catch (Exception e) {
            logger.error("Error adding image to product: " + productId + ", image src: " + image.getSrc(), e);
            throw new RuntimeException("Failed to add image to product", e);
        }
    }
    
    /**
     * Get inventory level by inventory item ID using GraphQL
     */
    public List<InventoryLevel> getInventoryLevelByInventoryItemId(String id) {
        String query = """
            query getInventoryLevels($inventoryItemId: ID!) {
                inventoryItem(id: $inventoryItemId) {
                    inventoryLevels(first: 250) {
                        edges {
                            node {
                                id
                                quantities(names: ["available"]) {
                                    name
                                    quantity
                                }
                                item {
                                    id
                                }
                                location {
                                    id
                                }
                            }
                        }
                    }
                }
            }
            """;
        
        Map<String, Object> variables = new HashMap<>();
        variables.put("inventoryItemId", "gid://shopify/InventoryItem/" + id);
        
        try {
            JsonNode data = executeGraphQLQuery(query, variables);
            JsonNode itemNode = data.get("inventoryItem");
            
            if (itemNode == null || itemNode.isNull()) {
                logger.warn("Inventory item not found with ID: " + id);
                return new ArrayList<>();
            }
            
            JsonNode inventoryLevelsNode = itemNode.get("inventoryLevels");
            if (inventoryLevelsNode == null || inventoryLevelsNode.isNull()) {
                logger.warn("No inventory levels found for inventory item: " + id);
                return new ArrayList<>();
            }
            
            JsonNode levelsNode = inventoryLevelsNode.get("edges");
            if (levelsNode == null || levelsNode.isNull()) {
                logger.warn("No inventory level edges found for inventory item: " + id);
                return new ArrayList<>();
            }
            
            List<InventoryLevel> levels = new ArrayList<>();
            for (JsonNode edge : levelsNode) {
                JsonNode levelNode = edge.get("node");
                InventoryLevel level = convertJsonToInventoryLevel(levelNode);
                
                // Ensure the inventory item ID is set correctly (since we know it from the query parameter)
                level.setInventoryItemId(id);
                
                levels.add(level);
            }
            
            return levels;
        } catch (Exception e) {
            logger.error("Error getting inventory levels for inventory item: " + id, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Get collects for product ID using GraphQL
     * Note: In GraphQL, product-collection relationships are handled differently
     * This method provides compatibility with the REST API approach
     */
    public List<Collect> getCollectsForProductId(String productId) {
        String query = """
            query getProductCollections($id: ID!) {
                product(id: $id) {
                    collections(first: 250) {
                        edges {
                            node {
                                id
                                title
                            }
                        }
                    }
                }
            }
            """;
        
        Map<String, Object> variables = new HashMap<>();
        variables.put("id", "gid://shopify/Product/" + productId);
        
        try {
            JsonNode data = executeGraphQLQuery(query, variables);
            JsonNode productNode = data.get("product");
            
            if (productNode == null) {
                return new ArrayList<>();
            }
            
            JsonNode collectionsNode = productNode.get("collections").get("edges");
            List<Collect> collects = new ArrayList<>();
            
            for (JsonNode edge : collectionsNode) {
                JsonNode collectionNode = edge.get("node");
                Collect collect = new Collect();
                collect.setProductId(productId);
                collect.setCollectionId(extractIdFromGid(collectionNode.get("id").asText()));
                collects.add(collect);
            }
            
            return collects;
        } catch (Exception e) {
            logger.error("Error getting collects for product: " + productId, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Add product and collections associations using GraphQL
     */
    public void addProductAndCollectionsAssociations(List<Collect> collects) {
        for (Collect collect : collects) {
            addProductToCollection(collect.getProductId(), collect.getCollectionId());
        }
    }
    
    /**
     * Add a product to a collection using GraphQL
     */
    private void addProductToCollection(String productId, String collectionId) {
        String mutation = """
            mutation collectionAddProducts($id: ID!, $productIds: [ID!]!) {
                collectionAddProducts(id: $id, productIds: $productIds) {
                    collection {
                        id
                        title
                    }
                    userErrors {
                        field
                        message
                    }
                }
            }
            """;
        
        Map<String, Object> variables = new HashMap<>();
        variables.put("id", "gid://shopify/Collection/" + collectionId);
        variables.put("productIds", List.of("gid://shopify/Product/" + productId));
        
        try {
            JsonNode data = executeGraphQLQuery(mutation, variables);
            JsonNode collectionAdd = data.get("collectionAddProducts");
            
            // Check for user errors
            JsonNode userErrors = collectionAdd.get("userErrors");
            if (userErrors != null && userErrors.size() > 0) {
                logger.error("Adding product to collection failed with user errors: " + userErrors.toString());
                throw new RuntimeException("Adding product to collection failed: " + userErrors.toString());
            }
            
        } catch (Exception e) {
            logger.error("Error adding product " + productId + " to collection " + collectionId, e);
            throw new RuntimeException("Failed to add product to collection", e);
        }
    }
    
    /**
     * Delete collect (remove product from collection) using GraphQL
     * Note: This method is deprecated - use deleteCollectByProductAndCollection instead
     */
    public void deleteCollect(String collectId) throws Exception {
        logger.warn("deleteCollect method is deprecated - use deleteCollectByProductAndCollection instead");
        throw new UnsupportedOperationException("Use deleteCollectByProductAndCollection with product and collection IDs");
    }
    
    /**
     * Remove product from collection using GraphQL
     */
    public void deleteCollectByProductAndCollection(String productId, String collectionId) throws Exception {
        String mutation = """
            mutation collectionRemoveProducts($id: ID!, $productIds: [ID!]!) {
                collectionRemoveProducts(id: $id, productIds: $productIds) {
                    userErrors {
                        field
                        message
                    }
                }
            }
            """;
        
        Map<String, Object> variables = new HashMap<>();
        variables.put("id", "gid://shopify/Collection/" + collectionId);
        variables.put("productIds", List.of("gid://shopify/Product/" + productId));
        
        try {
            JsonNode data = executeGraphQLQuery(mutation, variables);
            JsonNode collectionRemove = data.get("collectionRemoveProducts");
            
            // Check for user errors
            JsonNode userErrors = collectionRemove.get("userErrors");
            if (userErrors != null && userErrors.size() > 0) {
                logger.error("Removing product from collection failed with user errors: " + userErrors.toString());
                throw new RuntimeException("Removing product from collection failed: " + userErrors.toString());
            }
            
        } catch (Exception e) {
            logger.error("Error removing product " + productId + " from collection " + collectionId, e);
            throw e;
        }
    }
    
    /**
     * Delete all collects for product ID (compatibility method)
     */
    public void deleteAllCollectForProductId(String productId) {
        List<Collect> allCollects = getCollectsForProductId(productId);
        allCollects.stream().forEach(c -> {
            try {
                deleteCollectByProductAndCollection(productId, c.getCollectionId());
            }
            catch (Throwable e) {
                logger.warn("Failed to remove product " + productId + " from collection " + c.getCollectionId() + ": " + e.getMessage());
            }
        });
    }
    
    /**
     * Get predefined collections for product ID (compatibility method)
     */
    public List<PredefinedCollection> getPredefinedCollectionsForProductId(String productId,
            Map<PredefinedCollection, CustomCollection> collectionByEnum) {
        return CollectionUtility.getPredefinedCollectionFromCollect(getCollectsForProductId(productId), collectionByEnum);
    }
    
    /**
     * Get collection with basic information using GraphQL (simplified version)
     */
    public CustomCollection getCollectionWithPublications(String id) {
        String query = """
            query getCollection($id: ID!) {
                collection(id: $id) {
                    id
                    title
                    handle
                    description
                    sortOrder
                    updatedAt
                }
            }
            """;
        
        Map<String, Object> variables = new HashMap<>();
        variables.put("id", "gid://shopify/Collection/" + id);
        
        try {
            JsonNode data = executeGraphQLQuery(query, variables);
            JsonNode collectionNode = data.get("collection");
            
            if (collectionNode == null || collectionNode.isNull()) {
                logger.error("Collection not found with ID: " + id);
                return null;
            }
            
            CustomCollection collection = convertJsonToCustomCollection(collectionNode);
            
            logger.info("Collection '" + collection.getTitle() + "' retrieved successfully");
            logger.info("- ID: " + collection.getId());
            logger.info("- Handle: " + collection.getHandle());
            logger.info("- Collections created via GraphQL are automatically published to the Online Store");
            
            return collection;
        } catch (Exception e) {
            logger.error("Error getting collection: " + id, e);
            return null;
        }
    }
    
    /**
     * Get all available publications/sales channels using GraphQL
     */
    public List<Map<String, String>> getAllPublications() {
        String query = """
            query {
                publications(first: 250) {
                    edges {
                        node {
                            id
                            name
                            supportsFuturePublishing
                        }
                    }
                }
            }
            """;
        
        try {
            JsonNode data = executeGraphQLQuery(query);
            JsonNode publicationsNode = data.get("publications");
            JsonNode edges = publicationsNode.get("edges");
            
            List<Map<String, String>> publications = new ArrayList<>();
            for (JsonNode edge : edges) {
                JsonNode publicationNode = edge.get("node");
                Map<String, String> publication = new HashMap<>();
                publication.put("id", extractIdFromGid(publicationNode.get("id").asText()));
                publication.put("name", publicationNode.get("name").asText());
                publication.put("supportsFuturePublishing", publicationNode.get("supportsFuturePublishing").asText());
                publications.add(publication);
            }
            
            return publications;
        } catch (Exception e) {
            logger.error("Error getting all publications", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Get collection publication status using GraphQL
     */
    public Map<String, Object> getCollectionPublicationStatus(String id) {
        String query = """
            query getCollectionPublications($id: ID!) {
                collection(id: $id) {
                    id
                    title
                    handle
                    updatedAt
                    productsCount {
                        count
                    }
                }
            }
            """;
        
        Map<String, Object> variables = new HashMap<>();
        variables.put("id", "gid://shopify/Collection/" + id);
        
        try {
            JsonNode data = executeGraphQLQuery(query, variables);
            JsonNode collectionNode = data.get("collection");
            
            if (collectionNode == null || collectionNode.isNull()) {
                logger.error("Collection not found with ID: " + id);
                return null;
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("id", extractIdFromGid(collectionNode.get("id").asText()));
            result.put("title", collectionNode.get("title").asText());
            result.put("handle", collectionNode.get("handle").asText());
            result.put("updatedAt", collectionNode.get("updatedAt").asText());
            
            // Get products count if available
            if (collectionNode.has("productsCount")) {
                result.put("productsCount", collectionNode.get("productsCount").get("count").asInt());
            }
            
            // Mark as accessible since we can retrieve it
            result.put("isAccessible", true);
            
            logger.info("Collection '" + result.get("title") + "' status:");
            logger.info("- ID: " + result.get("id"));
            logger.info("- Handle: " + result.get("handle"));
            logger.info("- Updated at: " + result.get("updatedAt"));
            logger.info("- Products count: " + result.get("productsCount"));
            logger.info("- Accessible: " + result.get("isAccessible"));
            
            return result;
            
        } catch (Exception e) {
            logger.error("Error getting collection publication status: " + id, e);
            return null;
        }
    }
    
    /**
     * Create custom collection and publish to all available channels using GraphQL
     */
    public CustomCollection createCustomCollectionWithPublication(PredefinedCollection collectionEnum) {
        // First create the collection
        CustomCollection collection = createCustomCollection(collectionEnum);
        
        // Then attempt to publish it to all available channels
        try {
            publishCollectionToAllChannels(collection.getId());
        } catch (Exception e) {
            logger.warn("Failed to explicitly publish collection to all channels (this may be expected if collections are auto-published): " + e.getMessage());
            // Continue - collections created via GraphQL are typically auto-published to Online Store
        }
        
        return collection;
    }
    
    /**
     * Publish collection to all available sales channels
     */
    private void publishCollectionToAllChannels(String collectionId) throws Exception {
        // Get all available publications/sales channels
        List<Map<String, String>> publications = getAllPublications();
        
        for (Map<String, String> publication : publications) {
            String publicationId = publication.get("id");
            String publicationName = publication.get("name");
            
            try {
                publishCollectionToChannel(collectionId, publicationId);
                logger.info("Published collection " + collectionId + " to channel: " + publicationName);
            } catch (Exception e) {
                logger.warn("Failed to publish collection " + collectionId + " to channel " + publicationName + ": " + e.getMessage());
                // Continue with other channels
            }
        }
    }
    
    /**
     * Publish a collection to a specific sales channel
     */
    private void publishCollectionToChannel(String collectionId, String publicationId) throws Exception {
        String mutation = """
            mutation publishablePublish($id: ID!, $input: [PublicationInput!]!) {
                publishablePublish(id: $id, input: $input) {
                    publishable {
                        ... on Collection {
                            id
                            title
                        }
                    }
                    userErrors {
                        field
                        message
                    }
                }
            }
            """;
        
        Map<String, Object> publicationInput = new HashMap<>();
        publicationInput.put("publicationId", "gid://shopify/Publication/" + publicationId);
        
        Map<String, Object> variables = new HashMap<>();
        variables.put("id", "gid://shopify/Collection/" + collectionId);
        variables.put("input", List.of(publicationInput));
        
        try {
            JsonNode data = executeGraphQLQuery(mutation, variables);
            JsonNode publishResult = data.get("publishablePublish");
            
            // Check for user errors
            JsonNode userErrors = publishResult.get("userErrors");
            if (userErrors != null && userErrors.size() > 0) {
                logger.error("Collection publication failed with user errors: " + userErrors.toString());
                throw new RuntimeException("Collection publication failed: " + userErrors.toString());
            }
            
        } catch (Exception e) {
            logger.error("Error publishing collection " + collectionId + " to publication " + publicationId, e);
            throw e;
        }
    }
    
    /**
     * Get product publication status across all sales channels using GraphQL
     */
    public Map<String, Object> getProductPublicationStatus(String productId) {
        String query = """
            query getProductPublications($id: ID!) {
                product(id: $id) {
                    id
                    title
                    handle
                    status
                    publishedAt
                    publishedOnCurrentPublication
                    availablePublicationsCount {
                        count
                    }
                    resourcePublicationsCount {
                        count
                    }
                    resourcePublications(first: 50) {
                        edges {
                            node {
                                publication {
                                    id
                                    name
                                }
                                publishDate
                                isPublished
                            }
                        }
                    }
                }
            }
            """;
        
        Map<String, Object> variables = new HashMap<>();
        variables.put("id", "gid://shopify/Product/" + productId);
        
        try {
            JsonNode data = executeGraphQLQuery(query, variables);
            JsonNode productNode = data.get("product");
            
            if (productNode == null || productNode.isNull()) {
                logger.error("Product not found with ID: " + productId);
                return null;
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("id", extractIdFromGid(productNode.get("id").asText()));
            result.put("title", productNode.get("title").asText());
            result.put("handle", productNode.get("handle").asText());
            result.put("status", productNode.get("status").asText());
            
            if (productNode.has("publishedAt") && !productNode.get("publishedAt").isNull()) {
                result.put("publishedAt", productNode.get("publishedAt").asText());
            }
            
            if (productNode.has("publishedOnCurrentPublication")) {
                result.put("publishedOnCurrentPublication", productNode.get("publishedOnCurrentPublication").asBoolean());
            }
            
            if (productNode.has("availablePublicationsCount")) {
                result.put("availablePublicationsCount", productNode.get("availablePublicationsCount").get("count").asInt());
            }
            
            if (productNode.has("resourcePublicationsCount")) {
                result.put("resourcePublicationsCount", productNode.get("resourcePublicationsCount").get("count").asInt());
            }
            
            // Parse resource publications to get detailed channel information
            List<Map<String, Object>> publications = new ArrayList<>();
            if (productNode.has("resourcePublications")) {
                JsonNode resourcePublications = productNode.get("resourcePublications").get("edges");
                for (JsonNode edge : resourcePublications) {
                    JsonNode publicationNode = edge.get("node");
                    Map<String, Object> publication = new HashMap<>();
                    
                    JsonNode pubInfo = publicationNode.get("publication");
                    publication.put("id", extractIdFromGid(pubInfo.get("id").asText()));
                    publication.put("name", pubInfo.get("name").asText());
                    publication.put("isPublished", publicationNode.get("isPublished").asBoolean());
                    
                    if (publicationNode.has("publishDate") && !publicationNode.get("publishDate").isNull()) {
                        publication.put("publishDate", publicationNode.get("publishDate").asText());
                    }
                    
                    publications.add(publication);
                }
            }
            result.put("publications", publications);
            
            logger.info("Product '" + result.get("title") + "' publication status:");
            logger.info("- ID: " + result.get("id"));
            logger.info("- Status: " + result.get("status"));
            logger.info("- Published on current publication: " + result.get("publishedOnCurrentPublication"));
            logger.info("- Available publications count: " + result.get("availablePublicationsCount"));
            logger.info("- Resource publications count: " + result.get("resourcePublicationsCount"));
            logger.info("- Published to " + publications.size() + " specific channels");
            
            return result;
            
        } catch (Exception e) {
            logger.error("Error getting product publication status: " + productId, e);
            return null;
        }
    }
    
    /**
     * Publish a product to all available sales channels
     */
    public void publishProductToAllChannels(String productId) throws Exception {
        // Get all available publications/sales channels
        List<Map<String, String>> publications = getAllPublications();
        
        logger.info("Publishing product " + productId + " to all " + publications.size() + " sales channels");
        
        for (Map<String, String> publication : publications) {
            String publicationId = publication.get("id");
            String publicationName = publication.get("name");
            
            try {
                publishProductToChannel(productId, publicationId);
                logger.info("Published product " + productId + " to channel: " + publicationName);
            } catch (Exception e) {
                logger.warn("Failed to publish product " + productId + " to channel " + publicationName + ": " + e.getMessage());
                // Continue with other channels - don't fail the entire operation
            }
        }
    }
    
    /**
     * Publish a product to a specific sales channel
     */
    private void publishProductToChannel(String productId, String publicationId) throws Exception {
        String mutation = """
            mutation publishablePublish($id: ID!, $input: [PublicationInput!]!) {
                publishablePublish(id: $id, input: $input) {
                    publishable {
                        ... on Product {
                            id
                            title
                        }
                    }
                    userErrors {
                        field
                        message
                    }
                }
            }
            """;
        
        Map<String, Object> publicationInput = new HashMap<>();
        publicationInput.put("publicationId", "gid://shopify/Publication/" + publicationId);
        
        Map<String, Object> variables = new HashMap<>();
        variables.put("id", "gid://shopify/Product/" + productId);
        variables.put("input", List.of(publicationInput));
        
        try {
            JsonNode data = executeGraphQLQuery(mutation, variables);
            JsonNode publishResult = data.get("publishablePublish");
            
            // Check for user errors
            JsonNode userErrors = publishResult.get("userErrors");
            if (userErrors != null && userErrors.size() > 0) {
                logger.error("Product publication failed with user errors: " + userErrors.toString());
                throw new RuntimeException("Product publication failed: " + userErrors.toString());
            }
            
        } catch (Exception e) {
            logger.error("Error publishing product " + productId + " to publication " + publicationId, e);
            throw e;
        }
    }
} 