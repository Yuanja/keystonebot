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
import com.gw.services.shopifyapi.objects.Option;
import com.gw.services.shopifyapi.objects.InventoryLevels;
import com.gw.domain.FeedItem;
import com.gw.domain.EbayMetafieldDefinition;

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
                    options {
                        id
                        name
                        position
                        optionValues {
                            id
                            name
                        }
                    }
                    metafields(first: 50) {
                        edges {
                            node {
                                id
                                namespace
                                key
                                value
                                type
                                description
                            }
                        }
                    }
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
                                selectedOptions {
                                    name
                                    value
                                }
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
                                options {
                                    id
                                    name
                                    position
                                    optionValues {
                                        id
                                        name
                                    }
                                }
                                metafields(first: 50) {
                                    edges {
                                        node {
                                            id
                                            namespace
                                            key
                                            value
                                            type
                                            description
                                        }
                                    }
                                }
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
                                            selectedOptions {
                                                name
                                                value
                                            }
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
     * Get the most recently added products with a specified limit
     * Products are ordered by creation date descending (newest first)
     * 
     * @param limit Maximum number of products to return
     * @return List of recent products
     */
    public List<Product> getRecentProducts(int limit) {
        List<Product> recentProducts = new ArrayList<>();
        
        String query = """
            query getRecentProducts($first: Int!) {
                products(first: $first, sortKey: CREATED_AT, reverse: true) {
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
                            options {
                                id
                                name
                                position
                                optionValues {
                                    id
                                    name
                                }
                            }
                            metafields(first: 50) {
                                edges {
                                    node {
                                        id
                                        namespace
                                        key
                                        value
                                        type
                                        description
                                    }
                                }
                            }
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
                                        selectedOptions {
                                            name
                                            value
                                        }
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
        variables.put("first", limit);
        
        try {
            JsonNode data = executeGraphQLQuery(query, variables);
            JsonNode productsNode = data.get("products");
            JsonNode edges = productsNode.get("edges");
            
            for (JsonNode edge : edges) {
                JsonNode productNode = edge.get("node");
                Product product = convertJsonToProduct(productNode);
                recentProducts.add(product);
            }
            
            logger.info("✅ Retrieved " + recentProducts.size() + " recent products (limit: " + limit + ")");
            
        } catch (Exception e) {
            logger.error("Error getting recent products", e);
            throw new RuntimeException("Failed to get recent products", e);
        }
        
        return recentProducts;
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
                        updatedAt
                        metafields(first: 50) {
                            edges {
                                node {
                                    id
                                    namespace
                                    key
                                    value
                                    type
                                    description
                                }
                            }
                        }
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
                        updatedAt
                        images(first: 10) {
                            edges {
                                node {
                                    id
                                    url
                                }
                            }
                        }
                        variants(first: 100) {
                            edges {
                                node {
                                    id
                                    title
                                    price
                                    sku
                                    selectedOptions {
                                        name
                                        value
                                    }
                                }
                            }
                        }
                        metafields(first: 100) {
                            edges {
                                node {
                                    id
                                    namespace
                                    key
                                    value
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
        
        Map<String, Object> variables = new HashMap<>();
        variables.put("input", createProductInput(product, true)); // true = isUpdate
        
        logger.info("Updating product with ID: {}", product.getId());
        logger.debug("Update mutation variables: {}", variables);
        
        try {
            JsonNode data = executeGraphQLQuery(mutation, variables);
            JsonNode productUpdate = data.get("productUpdate");
            
            // Check for user errors
            JsonNode userErrors = productUpdate.get("userErrors");
            if (userErrors != null && userErrors.size() > 0) {
                logger.error("Product update failed with user errors: " + userErrors.toString());
                throw new RuntimeException("Product update failed: " + userErrors.toString());
            }
            
            logger.info("Product updated successfully: {}", product.getId());
            
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
        if (productNode.has("updatedAt") && !productNode.get("updatedAt").isNull()) {
            product.setUpdatedAt(productNode.get("updatedAt").asText());
        }
        if (productNode.has("status") && !productNode.get("status").isNull()) {
            product.setStatus(productNode.get("status").asText());
        }
        
        // Convert metafields (including eBay metafields)
        if (productNode.has("metafields")) {
            JsonNode metafieldsNode = productNode.get("metafields").get("edges");
            List<Metafield> metafields = new ArrayList<>();
            for (JsonNode edge : metafieldsNode) {
                JsonNode metafieldNode = edge.get("node");
                Metafield metafield = convertJsonToMetafield(metafieldNode);
                metafields.add(metafield);
            }
            product.setMetafields(metafields);
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
        
        // Convert options
        if (productNode.has("options")) {
            JsonNode optionsNode = productNode.get("options");
            List<Option> options = new ArrayList<>();
            for (JsonNode optionNode : optionsNode) {
                Option option = convertJsonToOption(optionNode);
                options.add(option);
            }
            product.setOptions(options);
        }
        
        return product;
    }
    
    /**
     * Convert JSON metafield node to Metafield object
     */
    private Metafield convertJsonToMetafield(JsonNode metafieldNode) {
        Metafield metafield = new Metafield();
        
        if (metafieldNode.has("id")) {
            String gid = metafieldNode.get("id").asText();
            metafield.setId(extractIdFromGid(gid));
        }
        if (metafieldNode.has("namespace")) {
            metafield.setNamespace(metafieldNode.get("namespace").asText());
        }
        if (metafieldNode.has("key")) {
            metafield.setKey(metafieldNode.get("key").asText());
        }
        if (metafieldNode.has("value")) {
            metafield.setValue(metafieldNode.get("value").asText());
        }
        if (metafieldNode.has("type")) {
            metafield.setType(metafieldNode.get("type").asText());
        }
        if (metafieldNode.has("description") && !metafieldNode.get("description").isNull()) {
            metafield.setDescription(metafieldNode.get("description").asText());
        }
        
        return metafield;
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
            String inventoryItemId = extractIdFromGid(inventoryItemGid);
            variant.setInventoryItemId(inventoryItemId);
            
            // CRITICAL FIX: Populate inventory levels when retrieving existing variants
            // This ensures that mergeInventoryLevels receives proper existing inventory data
            try {
                List<InventoryLevel> inventoryLevelsList = getInventoryLevelByInventoryItemId(inventoryItemId);
                if (inventoryLevelsList != null && !inventoryLevelsList.isEmpty()) {
                    // Convert List<InventoryLevel> to InventoryLevels wrapper for compatibility
                    InventoryLevels inventoryLevels = new InventoryLevels();
                    for (InventoryLevel level : inventoryLevelsList) {
                        inventoryLevels.addInventoryLevel(level);
                    }
                    variant.setInventoryLevels(inventoryLevels);
                    
                    logger.debug("✅ Populated " + inventoryLevelsList.size() + " inventory levels for variant SKU: " + variant.getSku());
                } else {
                    logger.warn("⚠️ No inventory levels found for inventory item: " + inventoryItemId + " (SKU: " + variant.getSku() + ")");
                }
            } catch (Exception e) {
                logger.error("❌ Failed to retrieve inventory levels for inventory item: " + inventoryItemId + " (SKU: " + variant.getSku() + ")", e);
            }
        }
        
        if (variantNode.has("position")) {
            variant.setPosition(String.valueOf(variantNode.get("position").asInt()));
        }
        
        // Parse selectedOptions to populate option1, option2, option3 fields
        if (variantNode.has("selectedOptions")) {
            JsonNode selectedOptionsNode = variantNode.get("selectedOptions");
            if (selectedOptionsNode.isArray()) {
                for (int i = 0; i < selectedOptionsNode.size() && i < 3; i++) {
                    JsonNode optionNode = selectedOptionsNode.get(i);
                    if (optionNode.has("value")) {
                        String optionValue = optionNode.get("value").asText();
                        switch (i) {
                            case 0:
                                variant.setOption1(optionValue);
                                break;
                            case 1:
                                variant.setOption2(optionValue);
                                break;
                            case 2:
                                variant.setOption3(optionValue);
                                break;
                        }
                    }
                }
            }
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
        if (imageNode.has("altText") && !imageNode.get("altText").isNull()) {
            // Convert altText to metafield format for compatibility
            Metafield altMetafield = new Metafield();
            altMetafield.setNamespace("tags");
            altMetafield.setKey("alt");
            altMetafield.setValue(imageNode.get("altText").asText());
            altMetafield.setType("single_line_text_field");
            
            List<Metafield> metafields = new ArrayList<>();
            metafields.add(altMetafield);
            image.setMetafields(metafields);
        }
        
        return image;
    }
    
    private Image convertJsonToMediaImage(JsonNode mediaImageNode) {
        Image image = new Image();
        
        // Extract the media ID (this will be in MediaImage format)
        String gid = mediaImageNode.get("id").asText();
        image.setId(extractIdFromGid(gid));
        
        // Get image data from nested image object
        if (mediaImageNode.has("image")) {
            JsonNode imageData = mediaImageNode.get("image");
            
            if (imageData.has("url")) {
                image.setSrc(imageData.get("url").asText());
            }
            if (imageData.has("altText") && !imageData.get("altText").isNull()) {
                // Convert altText to metafield format for compatibility
                Metafield altMetafield = new Metafield();
                altMetafield.setNamespace("tags");
                altMetafield.setKey("alt");
                altMetafield.setValue(imageData.get("altText").asText());
                altMetafield.setType("single_line_text_field");
                
                List<Metafield> metafields = new ArrayList<>();
                metafields.add(altMetafield);
                image.setMetafields(metafields);
            }
        }
        
        return image;
    }
    
    private Option convertJsonToOption(JsonNode optionNode) {
        Option option = new Option();
        
        String gid = optionNode.get("id").asText();
        option.setId(extractIdFromGid(gid));
        
        if (optionNode.has("name")) {
            option.setName(optionNode.get("name").asText());
        }
        if (optionNode.has("position")) {
            option.setPosition(String.valueOf(optionNode.get("position").asInt()));
        }
        
        // Handle both GraphQL response formats for option values
        List<String> values = new ArrayList<>();
        
        // GraphQL API format: optionValues array with objects containing id and name
        if (optionNode.has("optionValues")) {
            JsonNode optionValuesNode = optionNode.get("optionValues");
            if (optionValuesNode.isArray()) {
                for (JsonNode valueNode : optionValuesNode) {
                    if (valueNode.has("name")) {
                        values.add(valueNode.get("name").asText());
                    }
                }
            }
        }
        // REST API format: values array with string values
        else if (optionNode.has("values")) {
            JsonNode valuesNode = optionNode.get("values");
            if (valuesNode.isArray()) {
                for (JsonNode valueNode : valuesNode) {
                    values.add(valueNode.asText());
                }
            }
        }
        
        option.setValues(values);
        
        logger.debug("Converted option: {} with {} values: {}", 
            option.getName(), values.size(), values);
        
        return option;
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
    
    /**
     * Creates ProductInput for GraphQL mutations (both create and update)
     * For create operations, variant option values are excluded (will be set via productOptionsCreate)
     * For update operations, variant option values are excluded (handled separately via remove/recreate options)
     */
    private Map<String, Object> createProductInput(Product product, boolean isUpdate) {
        Map<String, Object> input = new HashMap<>();
        
        if (product.getTitle() != null) {
            input.put("title", product.getTitle());
        }
        if (product.getHandle() != null) {
            input.put("handle", product.getHandle());
        }
        if (product.getBodyHtml() != null) {
            input.put("bodyHtml", product.getBodyHtml());
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
        if (product.getStatus() != null) {
            input.put("status", product.getStatus());
        }
        
        if (product.getId() != null) {
            input.put("id", "gid://shopify/Product/" + product.getId());
        }
        
        // Add metafields if present
        // Add metafields
        if (product.getMetafields() != null && !product.getMetafields().isEmpty()) {
            List<Map<String, Object>> metafields = new ArrayList<>();
            for (Metafield metafield : product.getMetafields()) {
                Map<String, Object> metafieldInput = new HashMap<>();
                metafieldInput.put("namespace", metafield.getNamespace());
                metafieldInput.put("key", metafield.getKey());
                metafieldInput.put("value", metafield.getValue());
                metafieldInput.put("type", metafield.getType());
                if (metafield.getDescription() != null) {
                    metafieldInput.put("description", metafield.getDescription());
                }
                metafields.add(metafieldInput);
            }
            input.put("metafields", metafields);
        }
        
        // Note: Images cannot be added directly in ProductInput via GraphQL
        // They must be added separately using productImageCreate mutation after product creation
        
        // CRITICAL: Do NOT include options in productCreate/productUpdate
        // Options must be created separately using productOptionsCreate mutation
        // For updates, options are managed via updateProductOptions (remove & recreate)
        
        // Handle variants with COMPLETE inventory management settings
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
                
                // CRITICAL DIFFERENCE: Include option values during UPDATE but not during CREATE
                if (false) {  // DISABLED: Never include option values - they are handled separately
                    // During UPDATE: Include option values to match the recreated options
                    if (variant.getOption1() != null) {
                        variantInput.put("option1", variant.getOption1());
                    }
                    if (variant.getOption2() != null) {
                        variantInput.put("option2", variant.getOption2());
                    }
                    if (variant.getOption3() != null) {
                        variantInput.put("option3", variant.getOption3());
                    }
                } else {
                    // OPTIONS ARE ALWAYS HANDLED SEPARATELY via updateProductOptions (remove & recreate)
                    // Do NOT include option1, option2, option3 in ProductInput - this causes GraphQL errors
                    // Options must be created separately using productOptionsCreate mutation
                }
                
                // Include inventory management settings for proper inventory tracking
                if (variant.getInventoryManagement() != null) {
                    variantInput.put("inventoryManagement", variant.getInventoryManagement().toUpperCase());
                }
                if (variant.getInventoryPolicy() != null) {
                    variantInput.put("inventoryPolicy", variant.getInventoryPolicy().toUpperCase());
                }
                if (variant.getTaxable() != null) {
                    variantInput.put("taxable", Boolean.parseBoolean(variant.getTaxable()));
                }
                
                // Additional inventory-related fields
                if (variant.getRequiresShipping() != null) {
                    variantInput.put("requiresShipping", Boolean.parseBoolean(variant.getRequiresShipping()));
                }
                if (variant.getWeight() != null) {
                    variantInput.put("weight", Double.parseDouble(variant.getWeight()));
                }
                if (variant.getWeightUnit() != null) {
                    variantInput.put("weightUnit", variant.getWeightUnit().toUpperCase());
                }
                
                variants.add(variantInput);
            }
            input.put("variants", variants);
        }
        
        return input;
    }
    
    /**
     * Backward compatibility: createProductInput without isUpdate parameter (defaults to create mode)
     */
    private Map<String, Object> createProductInput(Product product) {
        return createProductInput(product, false);
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
     * Get images by product ID using GraphQL (media query for compatibility with productDeleteMedia)
     */
    public List<Image> getImagesByProduct(String productId) {
        String query = """
            query getProductMedia($id: ID!) {
                product(id: $id) {
                    media(first: 250) {
                        edges {
                            node {
                                ... on MediaImage {
                                    id
                                    image {
                                        url
                                        altText
                                    }
                                }
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
            
            JsonNode mediaNode = productNode.get("media").get("edges");
            List<Image> images = new ArrayList<>();
            for (JsonNode edge : mediaNode) {
                JsonNode mediaImageNode = edge.get("node");
                if (mediaImageNode.has("image")) {
                    Image image = convertJsonToMediaImage(mediaImageNode);
                    images.add(image);
                }
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
            mutation productDeleteMedia($productId: ID!, $mediaIds: [ID!]!) {
                productDeleteMedia(productId: $productId, mediaIds: $mediaIds) {
                    deletedProductImageIds
                    userErrors {
                        field
                        message
                    }
                }
            }
            """;
        
        Map<String, Object> variables = new HashMap<>();
        variables.put("productId", "gid://shopify/Product/" + productId);
        variables.put("mediaIds", List.of("gid://shopify/MediaImage/" + imageId));
        
        try {
            JsonNode data = executeGraphQLQuery(mutation, variables);
            JsonNode mediaDelete = data.get("productDeleteMedia");
            
            // Check for user errors
            JsonNode userErrors = mediaDelete.get("userErrors");
            if (userErrors != null && userErrors.size() > 0) {
                logger.error("Media deletion failed with user errors: " + userErrors.toString());
            } else {
                JsonNode deletedIds = mediaDelete.get("deletedProductImageIds");
                if (deletedIds != null && deletedIds.size() > 0) {
                    logger.info("Successfully deleted image ID: " + deletedIds.get(0).asText());
                }
            }
            
        } catch (Exception e) {
            logger.error("Got error while trying to delete Media By Product Id:" 
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
        logger.debug("Getting inventory levels for inventory item ID: " + id);
        
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
                logger.warn("❌ Inventory item not found with ID: " + id);
                logger.warn("❌ This may indicate the inventory item was created with REST API and has compatibility issues");
                return new ArrayList<>();
            }
            
            JsonNode inventoryLevelsNode = itemNode.get("inventoryLevels");
            if (inventoryLevelsNode == null || inventoryLevelsNode.isNull()) {
                logger.warn("❌ No inventory levels node found for inventory item: " + id);
                logger.warn("❌ This may indicate inventory was not properly set up during product creation");
                return new ArrayList<>();
            }
            
            JsonNode levelsNode = inventoryLevelsNode.get("edges");
            if (levelsNode == null || levelsNode.isNull()) {
                logger.warn("❌ No inventory level edges found for inventory item: " + id);
                logger.warn("❌ This indicates the inventory item exists but has no location-specific levels");
                return new ArrayList<>();
            }
            
            List<InventoryLevel> levels = new ArrayList<>();
            for (JsonNode edge : levelsNode) {
                JsonNode levelNode = edge.get("node");
                InventoryLevel level = convertJsonToInventoryLevel(levelNode);
                
                // Ensure the inventory item ID is set correctly (since we know it from the query parameter)
                level.setInventoryItemId(id);
                
                levels.add(level);
                
                logger.debug("✅ Retrieved inventory level - LocationId: " + level.getLocationId() + 
                           ", InventoryItemId: " + level.getInventoryItemId() + 
                           ", Available: " + level.getAvailable());
            }
            
            logger.debug("Successfully retrieved " + levels.size() + " inventory levels for inventory item: " + id);
            return levels;
        } catch (Exception e) {
            logger.error("❌ Error getting inventory levels for inventory item: " + id, e);
            logger.error("❌ This error may indicate REST vs GraphQL API compatibility issues");
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
    
    /**
     * Create metafield definition to make metafields visible in Shopify admin
     */
    public void createMetafieldDefinition(String namespace, String key, String name, String description, String type, String ownerType) throws Exception {
        createMetafieldDefinition(namespace, key, name, description, type, ownerType, null);
    }
    
    /**
     * Create metafield definition with optional category constraint to make metafields visible in Shopify admin
     */
    public void createMetafieldDefinition(String namespace, String key, String name, String description, String type, String ownerType, String categoryId) throws Exception {
        String mutation = """
            mutation metafieldDefinitionCreate($definition: MetafieldDefinitionInput!) {
                metafieldDefinitionCreate(definition: $definition) {
                    createdDefinition {
                        id
                        key
                        namespace
                        name
                        description
                        type {
                            name
                        }
                        ownerType
                        visibleToStorefrontApi
                    }
                    userErrors {
                        field
                        message
                    }
                }
            }
            """;
        
        Map<String, Object> definition = new HashMap<>();
        definition.put("namespace", namespace);
        definition.put("key", key);
        definition.put("name", name);
        definition.put("description", description);
        definition.put("ownerType", ownerType);
        definition.put("visibleToStorefrontApi", true);
        
        // Set type as string directly (not as an object)
        definition.put("type", type);
        
        // Note: Category constraints are not supported through GraphQL metafield validations
        // The validation structure attempted here is not compatible with Shopify's schema
        if (categoryId != null && !categoryId.trim().isEmpty()) {
            logger.info("🏷️ Note: Category constraint requested for " + namespace + "." + key + " but not supported via GraphQL validations");
        }
        
        Map<String, Object> variables = new HashMap<>();
        variables.put("definition", definition);
        
        try {
            JsonNode data = executeGraphQLQuery(mutation, variables);
            JsonNode definitionCreate = data.get("metafieldDefinitionCreate");
            
            // Check for user errors
            JsonNode userErrors = definitionCreate.get("userErrors");
            if (userErrors != null && userErrors.size() > 0) {
                String errorMessage = userErrors.toString();
                if (errorMessage.contains("already exists")) {
                    logger.info("Metafield definition already exists: " + namespace + "." + key);
                } else {
                    logger.error("Metafield definition creation failed with user errors: " + errorMessage);
                    throw new RuntimeException("Metafield definition creation failed: " + errorMessage);
                }
            } else {
                JsonNode createdDefinition = definitionCreate.get("createdDefinition");
                if (createdDefinition != null) {
                    logger.info("✅ Created metafield definition: " + namespace + "." + key + " (" + name + ")");
                }
            }
            
        } catch (Exception e) {
            logger.error("Error creating metafield definition: " + namespace + "." + key, e);
            throw e;
        }
    }
    
    /**
     * Create eBay metafield definitions for product metadata
     * Uses EbayMetafieldDefinition enum as the single source of truth
     */
    public void createEbayMetafieldDefinitions() throws Exception {
        logger.info("🏷️ Creating eBay metafield definitions for admin visibility...");
        
        // Note: Category constraints are not supported through GraphQL metafield validations
        // Metafields will be created without category restrictions
        logger.info("ℹ️ Creating metafield definitions without category constraints (not supported in GraphQL)");
        
        // Get all eBay metafield definitions from enum
        EbayMetafieldDefinition[] definitions = EbayMetafieldDefinition.values();
        
        // Create all definitions and collect their IDs for pinning
        int created = 0;
        int existing = 0;
        List<String> definitionIds = new ArrayList<>();
        
        for (EbayMetafieldDefinition definition : definitions) {
            try {
                String definitionId = createMetafieldDefinitionWithId("ebay", 
                    definition.getKey(), 
                    definition.getName(), 
                    definition.getDescription(), 
                    definition.getType(), 
                    "PRODUCT");
                    
                if (definitionId != null) {
                    definitionIds.add(definitionId);
                    created++;
                } else {
                    existing++;
                }
            } catch (Exception e) {
                if (e.getMessage().contains("already exists")) {
                    existing++;
                } else {
                    logger.error("Failed to create definition for ebay." + definition.getKey() + ": " + e.getMessage());
                }
            }
        }
        
        logger.info("🎉 eBay metafield definitions setup complete:");
        logger.info("  ✅ Created: " + created + " new definitions");
        logger.info("  ℹ️ Already existed: " + existing + " definitions");
        logger.info("  📊 Total eBay definitions: " + definitions.length);
        
        // Pin all newly created metafield definitions for better admin UX
        if (!definitionIds.isEmpty()) {
            logger.info("📌 Pinning " + definitionIds.size() + " newly created eBay metafield definitions...");
            int pinnedCount = 0;
            for (String definitionId : definitionIds) {
                try {
                    pinMetafieldDefinition(definitionId);
                    pinnedCount++;
                } catch (Exception e) {
                    logger.warn("Failed to pin metafield definition ID " + definitionId + ": " + e.getMessage());
                }
            }
            logger.info("📌 Successfully pinned " + pinnedCount + " out of " + definitionIds.size() + " eBay metafield definitions");
        } else {
            logger.info("📌 No new definitions to pin (all definitions already existed)");
        }
        
        logger.info("🖥️ eBay metafields should now be visible and pinned in Shopify Admin under Products > [Product] > Metafields");
    }
    
    /**
     * Create metafield definition and return the definition ID for further operations like pinning
     */
    private String createMetafieldDefinitionWithId(String namespace, String key, String name, String description, String type, String ownerType) throws Exception {
        String mutation = """
            mutation metafieldDefinitionCreate($definition: MetafieldDefinitionInput!) {
                metafieldDefinitionCreate(definition: $definition) {
                    createdDefinition {
                        id
                        key
                        namespace
                        name
                        description
                        type {
                            name
                        }
                        ownerType
                        visibleToStorefrontApi
                    }
                    userErrors {
                        field
                        message
                    }
                }
            }
            """;
        
        Map<String, Object> definition = new HashMap<>();
        definition.put("namespace", namespace);
        definition.put("key", key);
        definition.put("name", name);
        definition.put("description", description);
        definition.put("ownerType", ownerType);
        definition.put("visibleToStorefrontApi", true);
        
        // Set type as string directly (not as an object)
        definition.put("type", type);
        
        Map<String, Object> variables = new HashMap<>();
        variables.put("definition", definition);
        
        try {
            JsonNode data = executeGraphQLQuery(mutation, variables);
            JsonNode definitionCreate = data.get("metafieldDefinitionCreate");
            
            // Check for user errors
            JsonNode userErrors = definitionCreate.get("userErrors");
            if (userErrors != null && userErrors.size() > 0) {
                String errorMessage = userErrors.toString();
                if (errorMessage.contains("already exists")) {
                    logger.info("Metafield definition already exists: " + namespace + "." + key);
                    return null; // Return null to indicate existing definition (not newly created)
                } else {
                    logger.error("Metafield definition creation failed with user errors: " + errorMessage);
                    throw new RuntimeException("Metafield definition creation failed: " + errorMessage);
                }
            } else {
                JsonNode createdDefinition = definitionCreate.get("createdDefinition");
                if (createdDefinition != null) {
                    String definitionId = extractIdFromGid(createdDefinition.get("id").asText());
                    logger.info("✅ Created metafield definition: " + namespace + "." + key + " (" + name + ") with ID: " + definitionId);
                    return definitionId;
                }
            }
            
        } catch (Exception e) {
            logger.error("Error creating metafield definition: " + namespace + "." + key, e);
            throw e;
        }
        
        return null;
    }
    
    /**
     * Pin a metafield definition to make it prominently visible in Shopify admin
     */
    public void pinMetafieldDefinition(String definitionId) throws Exception {
        String mutation = """
            mutation metafieldDefinitionPin($definitionId: ID!) {
                metafieldDefinitionPin(definitionId: $definitionId) {
                    pinnedDefinition {
                        id
                        key
                        namespace
                        name
                        pinnedPosition
                    }
                    userErrors {
                        field
                        message
                    }
                }
            }
            """;
        
        Map<String, Object> variables = new HashMap<>();
        variables.put("definitionId", "gid://shopify/MetafieldDefinition/" + definitionId);
        
        try {
            JsonNode data = executeGraphQLQuery(mutation, variables);
            JsonNode definitionPin = data.get("metafieldDefinitionPin");
            
            // Check for user errors
            JsonNode userErrors = definitionPin.get("userErrors");
            if (userErrors != null && userErrors.size() > 0) {
                String errorMessage = userErrors.toString();
                logger.error("Metafield definition pinning failed with user errors: " + errorMessage);
                throw new RuntimeException("Metafield definition pinning failed: " + errorMessage);
            } else {
                JsonNode pinnedDefinition = definitionPin.get("pinnedDefinition");
                if (pinnedDefinition != null) {
                    String namespace = pinnedDefinition.get("namespace").asText();
                    String key = pinnedDefinition.get("key").asText();
                    int pinnedPosition = pinnedDefinition.get("pinnedPosition").asInt();
                    logger.info("📌 Pinned metafield definition: " + namespace + "." + key + " at position " + pinnedPosition);
                }
            }
            
        } catch (Exception e) {
            logger.error("Error pinning metafield definition ID: " + definitionId, e);
            throw e;
        }
    }
    
    /**
     * Get all metafield definitions for a specific namespace
     */
    public List<Map<String, String>> getMetafieldDefinitions(String namespace) throws Exception {
        String query = """
            query getMetafieldDefinitions($namespace: String!, $first: Int!) {
                metafieldDefinitions(ownerType: PRODUCT, namespace: $namespace, first: $first) {
                    edges {
                        node {
                            id
                            key
                            namespace
                            name
                            description
                            pinnedPosition
                            type {
                                name
                            }
                        }
                    }
                }
            }
            """;
        
        Map<String, Object> variables = new HashMap<>();
        variables.put("namespace", namespace);
        variables.put("first", 250);
        
        try {
            JsonNode data = executeGraphQLQuery(query, variables);
            JsonNode definitionsNode = data.get("metafieldDefinitions");
            JsonNode edges = definitionsNode.get("edges");
            
            List<Map<String, String>> definitions = new ArrayList<>();
            for (JsonNode edge : edges) {
                JsonNode defNode = edge.get("node");
                Map<String, String> definition = new HashMap<>();
                definition.put("id", extractIdFromGid(defNode.get("id").asText()));
                definition.put("key", defNode.get("key").asText());
                definition.put("namespace", defNode.get("namespace").asText());
                definition.put("name", defNode.get("name").asText());
                
                // Include pinnedPosition if it exists (pinned metafields have a position, unpinned do not)
                if (defNode.has("pinnedPosition") && !defNode.get("pinnedPosition").isNull()) {
                    definition.put("pinnedPosition", defNode.get("pinnedPosition").asText());
                }
                
                definitions.add(definition);
            }
            
            return definitions;
        } catch (Exception e) {
            logger.error("Error getting metafield definitions for namespace: " + namespace, e);
            throw e;
        }
    }
    
    /**
     * Delete metafield definition by ID
     */
    public void deleteMetafieldDefinition(String definitionId) throws Exception {
        String mutation = """
            mutation metafieldDefinitionDelete($id: ID!) {
                metafieldDefinitionDelete(id: $id) {
                    deletedDefinitionId
                    userErrors {
                        field
                        message
                    }
                }
            }
            """;
        
        Map<String, Object> variables = new HashMap<>();
        variables.put("id", "gid://shopify/MetafieldDefinition/" + definitionId);
        
        try {
            JsonNode data = executeGraphQLQuery(mutation, variables);
            JsonNode definitionDelete = data.get("metafieldDefinitionDelete");
            
            // Check for user errors
            JsonNode userErrors = definitionDelete.get("userErrors");
            if (userErrors != null && userErrors.size() > 0) {
                logger.error("Metafield definition deletion failed with user errors: " + userErrors.toString());
                throw new RuntimeException("Metafield definition deletion failed: " + userErrors.toString());
            } else {
                logger.info("✅ Deleted metafield definition ID: " + definitionId);
            }
            
        } catch (Exception e) {
            logger.error("Error deleting metafield definition: " + definitionId, e);
            throw e;
        }
    }
    
    /**
     * Remove all eBay metafield definitions
     */
    public void removeEbayMetafieldDefinitions() throws Exception {
        logger.info("🧹 Removing existing eBay metafield definitions...");
        
        List<Map<String, String>> existingDefinitions = getMetafieldDefinitions("ebay");
        
        if (existingDefinitions.isEmpty()) {
            logger.info("ℹ️ No existing eBay metafield definitions found to remove");
            return;
        }
        
        int removed = 0;
        for (Map<String, String> definition : existingDefinitions) {
            try {
                deleteMetafieldDefinition(definition.get("id"));
                removed++;
            } catch (Exception e) {
                logger.warn("Failed to delete metafield definition: " + definition.get("key") + " - " + e.getMessage());
            }
        }
        
        logger.info("🗑️ Removed " + removed + " existing eBay metafield definitions");
    }
    
    /**
     * Get all taxonomy categories from Shopify's product taxonomy
     */
    public List<Map<String, Object>> getAllTaxonomyCategories() throws Exception {
        String query = """
            query {
                productTaxonomyNodes(first: 250) {
                    edges {
                        node {
                            id
                            name
                            fullName
                            isLeaf
                            isRoot
                        }
                    }
                }
            }
            """;
        
        try {
            JsonNode data = executeGraphQLQuery(query);
            JsonNode taxonomyNodes = data.get("productTaxonomyNodes");
            JsonNode edges = taxonomyNodes.get("edges");
            
            List<Map<String, Object>> categories = new ArrayList<>();
            for (JsonNode edge : edges) {
                JsonNode node = edge.get("node");
                Map<String, Object> category = new HashMap<>();
                category.put("id", node.get("id").asText());
                category.put("name", node.get("name").asText());
                category.put("fullName", node.has("fullName") ? node.get("fullName").asText() : null);
                category.put("isLeaf", node.get("isLeaf").asBoolean());
                category.put("isRoot", node.get("isRoot").asBoolean());
                categories.add(category);
            }
            
            return categories;
        } catch (Exception e) {
            logger.error("Error getting taxonomy categories", e);
            throw e;
        }
    }
    
    /**
     * Get children categories of a specific taxonomy category
     */
    public List<Map<String, Object>> getTaxonomyCategoryChildren(String categoryId) throws Exception {
        String query = """
            query getTaxonomyChildren($id: ID!) {
                productTaxonomyNode(id: $id) {
                    children(first: 100) {
                        edges {
                            node {
                                id
                                name
                                fullName
                                isLeaf
                            }
                        }
                    }
                }
            }
            """;
        
        Map<String, Object> variables = new HashMap<>();
        variables.put("id", categoryId);
        
        try {
            JsonNode data = executeGraphQLQuery(query, variables);
            JsonNode taxonomyNode = data.get("productTaxonomyNode");
            
            if (taxonomyNode == null || taxonomyNode.isNull()) {
                return new ArrayList<>();
            }
            
            JsonNode children = taxonomyNode.get("children");
            JsonNode edges = children.get("edges");
            
            List<Map<String, Object>> childCategories = new ArrayList<>();
            for (JsonNode edge : edges) {
                JsonNode node = edge.get("node");
                Map<String, Object> category = new HashMap<>();
                category.put("id", node.get("id").asText());
                category.put("name", node.get("name").asText());
                category.put("fullName", node.has("fullName") ? node.get("fullName").asText() : null);
                category.put("isLeaf", node.get("isLeaf").asBoolean());
                childCategories.add(category);
            }
            
            return childCategories;
        } catch (Exception e) {
            logger.error("Error getting taxonomy category children for ID: " + categoryId, e);
            throw e;
        }
    }
    
    /**
     * Search for a taxonomy category by name using Shopify's official Taxonomy API
     * Based on https://shopify.dev/docs/api/admin-graphql/latest/objects/Taxonomy
     */
    public String searchTaxonomyCategory(String searchTerm) throws Exception {
        String query = """
            query searchTaxonomyCategory($search: String!) {
                taxonomy {
                    categories(search: $search, first: 10) {
                        edges {
                            node {
                                id
                                name
                                fullName
                                isLeaf
                                isRoot
                            }
                        }
                    }
                }
            }
            """;
        
        Map<String, Object> variables = new HashMap<>();
        variables.put("search", searchTerm);
        
        try {
            JsonNode data = executeGraphQLQuery(query, variables);
            JsonNode taxonomyNode = data.get("taxonomy");
            
            if (taxonomyNode == null || taxonomyNode.isNull()) {
                logger.warn("No taxonomy data returned");
                return null;
            }
            
            JsonNode categoriesNode = taxonomyNode.get("categories");
            if (categoriesNode == null) {
                logger.warn("No categories found in taxonomy");
                return null;
            }
            
            JsonNode edges = categoriesNode.get("edges");
            if (edges == null || edges.size() == 0) {
                logger.info("No categories found matching search term: " + searchTerm);
                return null;
            }
            
            // Look for exact match first, then partial match
            String exactMatchId = null;
            String partialMatchId = null;
            
            for (JsonNode edge : edges) {
                JsonNode node = edge.get("node");
                String name = node.get("name").asText();
                String fullName = node.has("fullName") && !node.get("fullName").isNull() ? 
                                 node.get("fullName").asText() : "";
                String id = node.get("id").asText();
                
                logger.info("Found category: " + name + " (Full: " + fullName + ") ID: " + id);
                
                if (searchTerm.equalsIgnoreCase(name)) {
                    exactMatchId = id;
                    break;
                } else if (name.toLowerCase().contains(searchTerm.toLowerCase()) || 
                          fullName.toLowerCase().contains(searchTerm.toLowerCase())) {
                    if (partialMatchId == null) {
                        partialMatchId = id;
                    }
                }
            }
            
            return exactMatchId != null ? exactMatchId : partialMatchId;
            
        } catch (Exception e) {
            logger.error("Error searching taxonomy category: " + searchTerm, e);
            throw e;
        }
    }
    
    /**
     * Get detailed information about a specific taxonomy category
     */
    public Map<String, Object> getTaxonomyCategoryDetails(String categoryId) throws Exception {
        String query = """
            query getTaxonomyCategoryDetails($id: ID!) {
                taxonomyCategory(id: $id) {
                    id
                    name
                    fullName
                    isLeaf
                    isRoot
                    attributes {
                        id
                        name
                    }
                }
            }
            """;
        
        Map<String, Object> variables = new HashMap<>();
        variables.put("id", categoryId);
        
        try {
            JsonNode data = executeGraphQLQuery(query, variables);
            JsonNode categoryNode = data.get("taxonomyCategory");
            
            if (categoryNode == null || categoryNode.isNull()) {
                logger.warn("Category not found with ID: " + categoryId);
                return null;
            }
            
            Map<String, Object> details = new HashMap<>();
            details.put("id", categoryNode.get("id").asText());
            details.put("name", categoryNode.get("name").asText());
            details.put("fullName", categoryNode.has("fullName") && !categoryNode.get("fullName").isNull() ? 
                                   categoryNode.get("fullName").asText() : "");
            details.put("isLeaf", categoryNode.get("isLeaf").asBoolean());
            details.put("isRoot", categoryNode.get("isRoot").asBoolean());
            
            // Handle attributes if present
            if (categoryNode.has("attributes") && !categoryNode.get("attributes").isNull()) {
                List<Map<String, String>> attributes = new ArrayList<>();
                JsonNode attributesNode = categoryNode.get("attributes");
                for (JsonNode attrNode : attributesNode) {
                    Map<String, String> attr = new HashMap<>();
                    attr.put("id", attrNode.get("id").asText());
                    attr.put("name", attrNode.get("name").asText());
                    attributes.add(attr);
                }
                details.put("attributes", attributes);
            }
            
            return details;
            
        } catch (Exception e) {
            logger.error("Error getting taxonomy category details: " + categoryId, e);
            throw e;
        }
    }
    
    /**
     * Get all top-level taxonomy categories
     */
    public List<Map<String, Object>> getTaxonomyTopLevelCategories() throws Exception {
        String query = """
            query {
                taxonomy {
                    categories(first: 50) {
                        edges {
                            node {
                                id
                                name
                                fullName
                                isLeaf
                                isRoot
                            }
                        }
                    }
                }
            }
            """;
        
        try {
            JsonNode data = executeGraphQLQuery(query);
            JsonNode taxonomyNode = data.get("taxonomy");
            
            if (taxonomyNode == null) {
                return new ArrayList<>();
            }
            
            JsonNode categoriesNode = taxonomyNode.get("categories");
            if (categoriesNode == null) {
                return new ArrayList<>();
            }
            
            JsonNode edges = categoriesNode.get("edges");
            List<Map<String, Object>> categories = new ArrayList<>();
            
            for (JsonNode edge : edges) {
                JsonNode node = edge.get("node");
                Map<String, Object> category = new HashMap<>();
                category.put("id", node.get("id").asText());
                category.put("name", node.get("name").asText());
                category.put("fullName", node.has("fullName") && !node.get("fullName").isNull() ? 
                                        node.get("fullName").asText() : "");
                category.put("isLeaf", node.get("isLeaf").asBoolean());
                category.put("isRoot", node.get("isRoot").asBoolean());
                categories.add(category);
            }
            
            return categories;
            
        } catch (Exception e) {
            logger.error("Error getting taxonomy top-level categories", e);
            throw e;
        }
    }
    
    /**
     * Get the Shopify taxonomy category ID for Watches
     * This ID was discovered via the taxonomy search API: gid://shopify/TaxonomyCategory/aa-6-11
     */
    public static String getWatchesCategoryId() {
        return "gid://shopify/TaxonomyCategory/aa-6-11";
    }
    
    /**
     * Create or update product options using GraphQL productOptionsCreate mutation
     * This is used when adding options to existing products or when options change during updates
     * 
     * @param productId The Shopify product ID
     * @param feedItem The feed item containing the option data
     * @return true if options were successfully created/updated
     */
    public boolean createProductOptions(String productId, FeedItem feedItem) {
        try {
            // Build the GraphQL mutation for productOptionsCreate
            String mutation = """
                mutation productOptionsCreate($productId: ID!, $options: [OptionCreateInput!]!) {
                  productOptionsCreate(productId: $productId, options: $options) {
                    userErrors {
                      field
                      message
                      code
                    }
                    product {
                      id
                      title
                      options {
                        id
                        name
                        position
                        optionValues {
                          id
                          name
                        }
                      }
                    }
                  }
                }
                """;
            
            // Build the variables
            Map<String, Object> variables = new HashMap<>();
            variables.put("productId", "gid://shopify/Product/" + productId);
            
            // Build options array based on feedItem attributes
            List<Map<String, Object>> options = new ArrayList<>();
            
            // Option 1: Color (from webWatchDial)
            if (feedItem.getWebWatchDial() != null && !feedItem.getWebWatchDial().trim().isEmpty()) {
                Map<String, Object> colorOption = new HashMap<>();
                colorOption.put("name", "Color");
                colorOption.put("position", 1);
                List<Map<String, Object>> colorValues = new ArrayList<>();
                Map<String, Object> colorValue = new HashMap<>();
                colorValue.put("name", feedItem.getWebWatchDial());
                colorValues.add(colorValue);
                colorOption.put("values", colorValues);
                options.add(colorOption);
            }
            
            // Option 2: Size (from webWatchDiameter)
            if (feedItem.getWebWatchDiameter() != null && !feedItem.getWebWatchDiameter().trim().isEmpty()) {
                Map<String, Object> sizeOption = new HashMap<>();
                sizeOption.put("name", "Size");
                sizeOption.put("position", 2);
                List<Map<String, Object>> sizeValues = new ArrayList<>();
                Map<String, Object> sizeValue = new HashMap<>();
                sizeValue.put("name", feedItem.getWebWatchDiameter());
                sizeValues.add(sizeValue);
                sizeOption.put("values", sizeValues);
                options.add(sizeOption);
            }
            
            // Option 3: Material (from webMetalType)
            if (feedItem.getWebMetalType() != null && !feedItem.getWebMetalType().trim().isEmpty()) {
                Map<String, Object> materialOption = new HashMap<>();
                materialOption.put("name", "Material");
                materialOption.put("position", 3);
                List<Map<String, Object>> materialValues = new ArrayList<>();
                Map<String, Object> materialValue = new HashMap<>();
                materialValue.put("name", feedItem.getWebMetalType());
                materialValues.add(materialValue);
                materialOption.put("values", materialValues);
                options.add(materialOption);
            }
            
            if (options.isEmpty()) {
                logger.debug("No options to create for product ID: {}", productId);
                return true; // No options needed is considered success
            }
            
            variables.put("options", options);
            
            logger.info("Creating {} options for product ID: {}", options.size(), productId);
            for (Map<String, Object> option : options) {
                logger.debug("  Option: {} = {}", option.get("name"), option.get("values"));
            }
            
            // Execute the GraphQL mutation
            JsonNode response = executeGraphQLQuery(mutation, variables);
            
            if (response != null) {
                JsonNode productOptionsCreate = response.get("productOptionsCreate");
                if (productOptionsCreate != null) {
                    JsonNode userErrors = productOptionsCreate.get("userErrors");
                    if (userErrors != null && userErrors.isArray() && userErrors.size() > 0) {
                        logger.error("ProductOptionsCreate failed with user errors:");
                        for (JsonNode error : userErrors) {
                            logger.error("  Error: {}", error.toString());
                        }
                        return false;
                    } else {
                        logger.info("✅ Successfully created {} options for product ID: {}", options.size(), productId);
                        return true;
                    }
                } else {
                    logger.error("No productOptionsCreate field found in response");
                }
            }
            
            logger.error("Unexpected GraphQL response structure for productOptionsCreate");
            return false;
            
        } catch (Exception e) {
            logger.error("Failed to create product options for product ID: " + productId, e);
            return false;
        }
    }

    /**
     * Update existing product options using GraphQL by removing all existing options and recreating them
     * This is the most reliable approach since there's always exactly one variant per product
     * and all option values belong to that single variant
     * 
     * @param productId The Shopify product ID
     * @param feedItem The feed item containing the updated option data
     * @return true if options were successfully updated
     */
    public boolean updateProductOptions(String productId, FeedItem feedItem) {
        try {
            logger.info("🔄 Updating product options for product ID: {} by removing and recreating all options", productId);
            
            // Step 1: Remove all existing options
            boolean optionsRemoved = removeProductOptions(productId);
            if (!optionsRemoved) {
                logger.error("Failed to remove existing options for product ID: {}", productId);
                return false;
            }
            
            // Step 2: Create new options using the same logic as product creation
            boolean optionsCreated = createProductOptions(productId, feedItem);
            if (!optionsCreated) {
                logger.error("Failed to create new options for product ID: {}", productId);
                return false;
            }
            
            logger.info("✅ Successfully updated product options for product ID: {} (removed and recreated)", productId);
            return true;
            
        } catch (Exception e) {
            logger.error("Failed to update product options for product ID: " + productId, e);
            return false;
        }
    }
    
    /**
     * Remove all options from a product using GraphQL productOptionsDelete mutation
     * This is used when updating products where options need to be recreated
     * 
     * @param productId The Shopify product ID
     * @return true if options were successfully removed
     */
    public boolean removeProductOptions(String productId) {
        try {
            // First get current product to find existing options
            Product currentProduct = getProductByProductId(productId);
            if (currentProduct == null || currentProduct.getOptions() == null || currentProduct.getOptions().isEmpty()) {
                logger.debug("No existing options to remove for product ID: {}", productId);
                return true; // No options to remove is considered success
            }
            
            logger.info("Removing {} existing options for product ID: {}", currentProduct.getOptions().size(), productId);
            
            // Build list of option IDs to delete
            List<String> optionIds = new ArrayList<>();
            for (Option option : currentProduct.getOptions()) {
                optionIds.add("gid://shopify/ProductOption/" + option.getId());
                logger.debug("  Will remove option: {} (ID: {})", option.getName(), option.getId());
            }
            
            // Use productOptionsDelete mutation to remove all options at once
            String mutation = """
                mutation productOptionsDelete($productId: ID!, $options: [ID!]!) {
                  productOptionsDelete(productId: $productId, options: $options) {
                    userErrors {
                      field
                      message
                      code
                    }
                    product {
                      id
                      options {
                        id
                        name
                      }
                    }
                  }
                }
                """;
            
            Map<String, Object> variables = new HashMap<>();
            variables.put("productId", "gid://shopify/Product/" + productId);
            variables.put("options", optionIds);
            
            // Execute the GraphQL mutation
            JsonNode response = executeGraphQLQuery(mutation, variables);
            
            if (response != null) {
                JsonNode productOptionsDelete = response.get("productOptionsDelete");
                if (productOptionsDelete != null) {
                    JsonNode userErrors = productOptionsDelete.get("userErrors");
                    if (userErrors != null && userErrors.isArray() && userErrors.size() > 0) {
                        logger.error("ProductOptionsDelete failed with user errors:");
                        for (JsonNode error : userErrors) {
                            logger.error("  Error: {}", error.toString());
                        }
                        return false;
                    } else {
                        logger.info("✅ Successfully removed {} options from product ID: {}", optionIds.size(), productId);
                        return true;
                    }
                } else {
                    logger.error("No productOptionsDelete field found in response");
                }
            }
            
            logger.error("Unexpected GraphQL response structure for productOptionsDelete");
            return false;
            
        } catch (Exception e) {
            logger.error("Failed to remove product options for product ID: " + productId, e);
            return false;
        }
    }
    
    /**
     * Delete a single product option using GraphQL productOptionDelete mutation
     * NOTE: This method is deprecated - option deletion is not supported
     */
    private boolean deleteProductOption(String productId, String optionId) {
        logger.warn("deleteProductOption is deprecated - option deletion is not supported by Shopify GraphQL API");
        logger.warn("Use updateProductOptions instead to modify existing option values");
        return false;
    }
    
    /**
     * Get the new value for an option based on the option name and feed item
     */
    private String getNewValueForOption(String optionName, FeedItem feedItem) {
        if ("Color".equalsIgnoreCase(optionName)) {
            return feedItem.getWebWatchDial();
        } else if ("Size".equalsIgnoreCase(optionName)) {
            return feedItem.getWebWatchDiameter();
        } else if ("Material".equalsIgnoreCase(optionName)) {
            return feedItem.getWebMetalType();
        }
        return null;
    }
    
    /**
     * Update product metafields separately using GraphQL
     * This is useful when product update is skipped but metafields still need to be updated
     */
    public void updateProductMetafields(String productId, List<Metafield> metafields) throws Exception {
        if (productId == null || metafields == null || metafields.isEmpty()) {
            logger.debug("No metafields to update for product ID: {}", productId);
            return;
        }
        
        logger.info("Updating {} metafields for product ID: {}", metafields.size(), productId);
        
        for (Metafield metafield : metafields) {
            if (metafield.getNamespace() != null && metafield.getKey() != null && metafield.getValue() != null) {
                updateSingleMetafield(productId, metafield);
            }
        }
        
        logger.info("✅ Successfully updated {} metafields for product ID: {}", metafields.size(), productId);
    }
    
    /**
     * Update a single metafield for a product
     */
    private void updateSingleMetafield(String productId, Metafield metafield) throws Exception {
        String mutation = """
            mutation metafieldSet($metafields: [MetafieldsSetInput!]!) {
                metafieldsSet(metafields: $metafields) {
                    metafields {
                        id
                        namespace
                        key
                        value
                    }
                    userErrors {
                        field
                        message
                    }
                }
            }
            """;
        
        Map<String, Object> metafieldInput = new HashMap<>();
        metafieldInput.put("ownerId", "gid://shopify/Product/" + productId);
        metafieldInput.put("namespace", metafield.getNamespace());
        metafieldInput.put("key", metafield.getKey());
        metafieldInput.put("value", metafield.getValue());
        metafieldInput.put("type", metafield.getType() != null ? metafield.getType() : "single_line_text_field");
        
        Map<String, Object> variables = new HashMap<>();
        variables.put("metafields", List.of(metafieldInput));
        
        try {
            JsonNode data = executeGraphQLQuery(mutation, variables);
            JsonNode metafieldsSet = data.get("metafieldsSet");
            
            // Check for user errors
            JsonNode userErrors = metafieldsSet.get("userErrors");
            if (userErrors != null && userErrors.size() > 0) {
                logger.error("Metafield update failed for {}.{}: {}", 
                    metafield.getNamespace(), metafield.getKey(), userErrors.toString());
                throw new RuntimeException("Metafield update failed: " + userErrors.toString());
            }
            
            logger.debug("Updated metafield: {}.{} = {}", 
                metafield.getNamespace(), metafield.getKey(), metafield.getValue());
            
        } catch (Exception e) {
            logger.error("Error updating metafield {}.{} for product {}", 
                metafield.getNamespace(), metafield.getKey(), productId, e);
            throw e;
        }
    }
    
    /**
     * Update product title separately using GraphQL
     * This is useful when product update is skipped but title still needs to be updated
     */
    public void updateProductTitle(String productId, String title) throws Exception {
        if (productId == null || title == null) {
            logger.debug("No title update needed for product ID: {} (title: {})", productId, title);
            return;
        }
        
        logger.info("Updating title for product ID: {} to: {}", productId, title);
        
        String mutation = """
            mutation productUpdate($input: ProductInput!) {
                productUpdate(input: $input) {
                    product {
                        id
                        title
                        updatedAt
                    }
                    userErrors {
                        field
                        message
                    }
                }
            }
            """;
        
        Map<String, Object> input = new HashMap<>();
        input.put("id", "gid://shopify/Product/" + productId);
        input.put("title", title);
        
        Map<String, Object> variables = new HashMap<>();
        variables.put("input", input);
        
        try {
            JsonNode data = executeGraphQLQuery(mutation, variables);
            JsonNode productUpdate = data.get("productUpdate");
            
            // Check for user errors
            JsonNode userErrors = productUpdate.get("userErrors");
            if (userErrors != null && userErrors.size() > 0) {
                logger.error("Product title update failed for product {}: {}", productId, userErrors.toString());
                throw new RuntimeException("Product title update failed: " + userErrors.toString());
            }
            
            logger.debug("Updated title for product: {} to: {}", productId, title);
            
        } catch (Exception e) {
            logger.error("Error updating title for product {}", productId, e);
            throw e;
        }
    }
} 