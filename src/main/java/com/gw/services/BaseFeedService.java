package com.gw.services;

import com.gw.domain.FeedItem;
import com.gw.ssl.SSLUtilities;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Enhanced BaseFeedService with simple file-based caching for development/testing
 * 
 * Dev Mode Caching Features:
 * - Reuses existing tmpFeed files if they're less than 1 day old
 * - Creates a trimmed top 100 file sorted by highest webTagNumber for faster testing
 * - Only downloads fresh data when files are missing or older than 1 day
 * 
 * @author jyuan
 */
public class BaseFeedService implements IFeedService {
    
    static {
        SSLUtilities.disableSslVerification();
    }
    
    private static Logger logger = LogManager.getLogger(BaseFeedService.class);
    private static String TMP_FEED_FILE_NAME = "tmpFeed";
    private static String TOP_100_FEED_FILE_NAME = "top100Feed.txt";
    
    @Autowired
    protected LogService logService;
    
    protected @Value("${GW_FEED_URL}") String feedUrl;
    protected @Value("${TMPFEED_FILE_FOLDER}") String tempFeedFileFolder;
    protected @Value("${READ_FEED}") boolean readFeed;

    protected @Value("${dev.mode:false}")
    boolean devMode;

    @Value("${dev.mode.maxReadCount:10}")
    int devModeMaxReadCount;

    @Value("${dev.mode.specificSku:}")
    String devModeSpecificSku;

    @Value("${GW_FEED_READYNESS_URL}") String feedReadynessUrl;
    
    @Autowired
    protected FeedItemService feedItemService;
    
    @Override
    public List<FeedItem> getItemsFromFeed() throws IOException, ParserConfigurationException, SAXException{
        List<FeedItem> rawLoad = new ArrayList<>();
        
        if (readFeed) {
            if (devMode && canReuseExistingFeedFiles()) {
                logger.info("🗄️ Dev mode: Reusing existing feed files (less than 1 day old)");
                rawLoad = getItemsFromTempFiles();
            } else {
                logger.info("📥 Downloading fresh feed data...");
                rawLoad = downloadFreshFeed();
                
                // Generate top 100 file for faster testing
                if (devMode && !rawLoad.isEmpty()) {
                    generateTop100FeedFile(rawLoad);
                }
            }
        } else {
            logger.info("Not configured to read from feed.");
        }
        
        return applyDevModeFiltering(rawLoad);
    }
    
    /**
     * Checks if existing feed files can be reused (less than 1 day old)
     */
    private boolean canReuseExistingFeedFiles() {
        File directory = new File(tempFeedFileFolder);
        File[] feedFiles = directory.listFiles((dir, name) -> name.startsWith(TMP_FEED_FILE_NAME));
        
        if (feedFiles == null || feedFiles.length == 0) {
            logger.debug("No existing feed files found");
            return false;
        }
        
        // Check if the newest file is less than 1 day old
        long oneDayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000);
        long newestFileTime = Arrays.stream(feedFiles)
                .mapToLong(File::lastModified)
                .max()
                .orElse(0);
        
        boolean canReuse = newestFileTime > oneDayAgo;
        
        if (canReuse) {
            LocalDateTime fileTime = LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(newestFileTime), 
                ZoneId.systemDefault()
            );
            logger.info("✅ Feed files are recent (newest: {}), reusing for dev mode", fileTime);
        } else {
            logger.info("⏰ Feed files are older than 1 day, will download fresh data");
        }
        
        return canReuse;
    }
    
    /**
     * Downloads fresh feed data using the original logic
     */
    @SuppressWarnings("deprecation")
	private List<FeedItem> downloadFreshFeed() throws IOException, ParserConfigurationException, SAXException {
        List<FeedItem> rawLoad = new ArrayList<>();
        int skipCounter = 0;
        int batchSize = 2000;
        deleteTmpFiles();
        
        do {
            String paginatedUrl = feedUrl + "&-max=" + batchSize + "&-skip=" + skipCounter * batchSize;
            logger.info("Reading feed from: " + paginatedUrl);
            
            // Add custom ssl trust manager to trust all ssl certs.
            SSLUtilities.trustAllHostnames();
            SSLUtilities.trustAllHttpsCertificates();
            String tempFeedFilePath = tempFeedFileFolder + "/" + TMP_FEED_FILE_NAME + skipCounter + ".xml";

            FileUtils.copyURLToFile(
                    new URL(paginatedUrl),
                    new File(tempFeedFilePath),
                    60 * 5 * 1000,
                    60 * 5 * 1000);

            logger.info("Feed download success! Saved to: " + tempFeedFilePath);
            List<FeedItem> tmpLoadedItems = loadFromXmlFile(tempFeedFilePath);
            if (!tmpLoadedItems.isEmpty()) {
                rawLoad.addAll(tmpLoadedItems);
                skipCounter++;
            } else {
                skipCounter = -1;
            }

        } while (skipCounter > 0);

        logger.info("All feed downloaded successfully. Total read feedItem count: " + rawLoad.size());
        return rawLoad;
    }
    
    /**
     * Generates a simple text file with top 100 SKUs sorted by highest webTagNumber
     */
    private void generateTop100FeedFile(List<FeedItem> allItems) {
        try {
            // Sort by webTagNumber descending and take top 100
            List<String> top100Skus = allItems.stream()
                .filter(item -> item.getWebTagNumber() != null && !item.getWebTagNumber().trim().isEmpty())
                .sorted((a, b) -> {
                    try {
                        Integer aNum = Integer.parseInt(a.getWebTagNumber());
                        Integer bNum = Integer.parseInt(b.getWebTagNumber());
                        return bNum.compareTo(aNum); // Descending order (highest first)
                    } catch (NumberFormatException e) {
                        return b.getWebTagNumber().compareTo(a.getWebTagNumber());
                    }
                })
                .limit(100)
                .map(FeedItem::getWebTagNumber)
                .collect(Collectors.toList());
            
            String top100FilePath = tempFeedFileFolder + "/" + TOP_100_FEED_FILE_NAME;
            
            // Write SKUs as simple text file, one per line
            try (FileWriter writer = new FileWriter(top100FilePath)) {
                for (String sku : top100Skus) {
                    writer.write(sku + "\n");
                }
            }
            
            logger.info("📝 Generated top 100 SKUs file: {} (highest: {} to {})", 
                top100FilePath,
                top100Skus.get(0),
                top100Skus.get(top100Skus.size() - 1));
            
        } catch (Exception e) {
            logger.warn("⚠️ Failed to generate top 100 SKUs file: {}", e.getMessage());
        }
    }
    
    /**
     * Gets the list of top 100 SKUs from the cached file
     */
    public List<String> getTop100Skus() {
        String top100FilePath = tempFeedFileFolder + "/" + TOP_100_FEED_FILE_NAME;
        File top100File = new File(top100FilePath);
        
        if (!top100File.exists()) {
            logger.debug("📂 Top 100 SKUs file not found");
            return new ArrayList<>();
        }
        
        // Check if file is recent (less than 1 day old)
        long oneDayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000);
        if (top100File.lastModified() < oneDayAgo) {
            logger.debug("⏰ Top 100 SKUs file is older than 1 day");
            return new ArrayList<>();
        }
        
        try {
            List<String> skus = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new FileReader(top100File))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    skus.add(line.trim());
                }
            }
            logger.debug("📖 Loaded {} top SKUs from cache file", skus.size());
            return skus;
        } catch (IOException e) {
            logger.debug("⚠️ Failed to read top 100 SKUs file: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * Loads items from the top 100 feed file for faster testing
     */
    public List<FeedItem> getItemsFromTop100Feed() throws IOException, ParserConfigurationException, SAXException {
        // Get the top 100 SKUs
        List<String> top100Skus = getTop100Skus();
        
        if (top100Skus.isEmpty()) {
            logger.info("📂 Top 100 SKUs not available, generating from available data...");
            
            // Try to load from temp files first
            List<FeedItem> allItems = getItemsFromTempFiles();
            
            // If no temp files, download fresh data 
            if (allItems.isEmpty()) {
                logger.info("📥 No temp files available, downloading fresh data to generate top100...");
                allItems = downloadFreshFeed();
            }
            
            // If we have data, generate the top100 file
            if (!allItems.isEmpty()) {
                generateTop100FeedFile(allItems);
                // Now try to get the SKUs again
                top100Skus = getTop100Skus();
            }
            
            // If still empty, return all items sorted
            if (top100Skus.isEmpty()) {
                logger.info("⚠️ Could not generate top 100 SKUs, returning all items sorted by highest SKU");
                return allItems.stream()
                    .filter(item -> item.getWebTagNumber() != null && !item.getWebTagNumber().trim().isEmpty())
                    .sorted((a, b) -> {
                        try {
                            Integer aNum = Integer.parseInt(a.getWebTagNumber());
                            Integer bNum = Integer.parseInt(b.getWebTagNumber());
                            return bNum.compareTo(aNum); // Descending order (highest first)
                        } catch (NumberFormatException e) {
                            return b.getWebTagNumber().compareTo(a.getWebTagNumber());
                        }
                    })
                    .limit(100)
                    .collect(Collectors.toList());
            }
        }
        
        logger.info("🚀 Loading top {} items for fast testing", top100Skus.size());
        
        // Load all items and filter to top 100
        List<FeedItem> allItems = getItemsFromTempFiles();
        if (allItems.isEmpty()) {
            logger.info("📥 No temp files available, loading fresh data");
            allItems = downloadFreshFeed();
        }
        
        // Filter to just the top 100 SKUs
        Set<String> top100SkuSet = new HashSet<>(top100Skus);
        List<FeedItem> top100Items = allItems.stream()
            .filter(item -> top100SkuSet.contains(item.getWebTagNumber()))
            .collect(Collectors.toList());
        
        logger.info("✅ Loaded {} items from top 100 for testing", top100Items.size());
        
        return applyDevModeFiltering(top100Items);
    }
    
    /**
     * Applies dev mode filtering to the feed items
     */
    private List<FeedItem> applyDevModeFiltering(List<FeedItem> feedItems) {
        if (devMode) {
            logger.info("Dev Mode is ON!!! TRUNCATING THE FEED TO TOP: " + devModeMaxReadCount);
            logger.info("Ordering feed by sku descending. Processing newly listed first.");
            feedItems.sort(FeedItem.FeedItemSortBySkuDscComparator);
            feedItems = feedItems.stream().limit(devModeMaxReadCount).collect(Collectors.toList());

            if (devModeSpecificSku != null && !devModeSpecificSku.trim().isEmpty()) {
                logger.info("Dev Mode specificSku is ON!!! TRUNCATING THE FEED TO SKU: " + devModeSpecificSku);
                feedItems = feedItems.stream()
                        .filter(c -> c.getWebTagNumber().equals(devModeSpecificSku))
                        .collect(Collectors.toList());
                if (feedItems.isEmpty()) {
                    throw new RuntimeException("Sku not found: " + devModeSpecificSku);
                }
            }
        }
        return feedItems;
    }

    public List<FeedItem> getItemsFromTempFiles() throws IOException, ParserConfigurationException, SAXException{
        File directory = new File(tempFeedFileFolder);
        File[] files = directory.listFiles((dir, name) -> name.contains(TMP_FEED_FILE_NAME));

        List<FeedItem> rawLoad = new ArrayList<>();
        if (files != null && files.length > 0) {
            for (File file : files) {
                logger.info("Reading feed file: " + file.getAbsolutePath());
                rawLoad.addAll(loadFromXmlFile(file.getAbsolutePath()));
            }
        } else {
            logger.info("No temp files found to read at: " + tempFeedFileFolder);
        }
        return rawLoad;
    }

    private void deleteTmpFiles(){
        File directory = new File(tempFeedFileFolder);
        File[] files = directory.listFiles((dir, name) -> name.startsWith(TMP_FEED_FILE_NAME));

            if (files != null) {
                for (File file : files) {
                    if (file.delete()) {
                        logger.info("Deleted: " + file.getName());
                    } else {
                        logger.info("Failed to delete: " + file.getName());
                    }
                }
            } else {
                logger.info("No matching files found.");
            }
    }
    
    @Override
    public List<FeedItem> loadFromXmlFile(String filePath) throws ParserConfigurationException, SAXException, IOException{
        logger.info("Reading from: " + filePath);
        Document doc = getDocument(filePath);
        List<FeedItem> feedItems = new ArrayList<FeedItem>();
        NodeList recordNodeList = doc.getElementsByTagName("record");
        List<FeedItem> emptyWebTagItems = new ArrayList<FeedItem>();

        for (int i = 0; i<recordNodeList.getLength(); i++){
            Node recordNode = recordNodeList.item(i);
            FeedItem newItem = FeedItem.fromRecordNode(recordNode);
            //Feed can be really corrupted where the SKU is null.  If that happens abandone this process.
            if (newItem.getWebTagNumber() == null || newItem.getWebTagNumber().isEmpty()) {
                emptyWebTagItems.add(newItem);
            } else {
                if (toAcceptFromFeed(newItem)) {
                    feedItems.add(newItem);
                }
            }
        }
        logger.info("Read feedItem count: " + feedItems.size());
        if (!emptyWebTagItems.isEmpty()){
            logger.info("Abandoning feed processing due to empty Web_Tag_Numer in items: ");
            for (FeedItem emptyItem : emptyWebTagItems){
                logger.info(emptyItem);
                logger.info("---");
            }
            throw new IOException("Feed has records with null webTagNumber.  Abandone this scheduled execution.");
        }
        
        //Sort it by webTagNumber;
        feedItems.sort(FeedItem.FeedItemSortBySkuAscComparator);
        
        return feedItems;
    }

    private Document getDocument(String filePath) throws ParserConfigurationException, SAXException, IOException {
        //populate the list of items.
        File xmlFeedFile = new File(filePath);
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setValidating(false);
        dbFactory.setNamespaceAware(true);
        dbFactory.setFeature("http://xml.org/sax/features/namespaces", false);
        dbFactory.setFeature("http://xml.org/sax/features/validation", false);
        dbFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
        dbFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        dbFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);


        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(xmlFeedFile);
        return doc;
    }

    protected List<FeedItem> getAccepted(List<FeedItem> rawItems){
        return rawItems.stream()
                       .filter(this::toAcceptFromFeed)
                       .collect(Collectors.toList());
    }
    
    @Override
	public boolean toAcceptFromFeed(FeedItem feedItem) {
        return true;
    }
    
    @Override
	public List<FeedItem> getItemsFromDBNotInFeed(final List<FeedItem> feedItems) {
        return feedItemService.getItemsFromDBNotInFeed(feedItems);
    }
        
    /* (non-Javadoc)
     * @see com.gw.components.IGWFeedService#getFeedItems()
     */
    @Override
	public List<FeedItem> getFeedItemsToPublish(){
        List<FeedItem> toInsert = feedItemService.findByStatus(FeedItem.STATUS_NEW_WAITING_PUBLISH);
        //Retry the previously failed to insert ones.
        List<FeedItem> failedToInsert= feedItemService.findByStatus(FeedItem.STATUS_PUBLISHED_FAILED);
        toInsert.addAll(failedToInsert);
        
        return toInsert;
    }
    
    @Override
	public List<FeedItem> getFeedItemsToUpdate(){
        List<FeedItem> toUpdate =  feedItemService.findByStatus(FeedItem.STATUS_CHANGED_WAITING_UPDATE);
        List<FeedItem> failedToUpdated= feedItemService.findByStatus(FeedItem.STATUS_UPDATE_FAILED);
        toUpdate.addAll(failedToUpdated);
        
        return toUpdate;
    }

    @Override
    public boolean hasDupes(List<FeedItem> feedItems){
        //Check to see if there are dupes.
        Map<String, FeedItem> itemsBySku = new HashMap<String, FeedItem>();
        List<FeedItem> dupes = new ArrayList<FeedItem>();
        feedItems.stream().forEach(c->{
            if (itemsBySku.containsKey(c.getWebTagNumber())) {
                dupes.add(c);
            } else {
                itemsBySku.put(c.getWebTagNumber(), c);
            }
        });
        
        return dupes.size() > 0;
    }

    @Override
    public List<FeedItem> trimDupes(List<FeedItem> feedItems){
        Map<String, FeedItem> itemsBySku = new HashMap<String, FeedItem>();
        List<FeedItem> noDupeListItems = new ArrayList<FeedItem>();
        feedItems.stream().forEach(c -> {
            if (!itemsBySku.containsKey(c.getWebTagNumber())) {
                itemsBySku.put(c.getWebTagNumber(), c);
                noDupeListItems.add(c);
            } else 
                logger.error("dupe: " + c.getWebTagNumber());

        });
        return noDupeListItems;
    }

    /**
     * Creates a FeedItem object from a row of sheet data
     * 
     * @param row The data row
     * @param columnMap Map of column names to indices
     * @return A populated FeedItem
     */
    public FeedItem createFeedItemFromRow(List<Object> row, Map<String, Integer> columnMap) {
        FeedItem item = new FeedItem();
        return item;
    }
    
    @Override
    public List<FeedItem> refreshCache() throws IOException, ParserConfigurationException, SAXException {
        logger.info("🔄 Force refreshing feed files...");
        
        // Delete existing temp files to force fresh download
        deleteTmpFiles();
        
        // Also delete top 100 file (force refresh should get latest data)
        String top100FilePath = tempFeedFileFolder + "/" + TOP_100_FEED_FILE_NAME;
        File top100File = new File(top100FilePath);
        if (top100File.exists()) {
            top100File.delete();
            logger.info("🗑️ Deleted top 100 feed file (force refresh)");
        }
        
        // Load fresh data
        return getItemsFromFeed();
    }
    
    @Override
    public void clearCache() {
        logger.info("🗑️ Clearing feed cache files...");
        
        // Delete temp feed files
        deleteTmpFiles();
        
        // Only delete top 100 file if it's older than 1 day (preserve for performance between tests)
        String top100FilePath = tempFeedFileFolder + "/" + TOP_100_FEED_FILE_NAME;
        File top100File = new File(top100FilePath);
        if (top100File.exists()) {
            long oneDayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000);
            if (top100File.lastModified() < oneDayAgo) {
                top100File.delete();
                logger.info("🗑️ Deleted old top 100 feed file (older than 1 day)");
            } else {
                logger.info("📋 Keeping recent top 100 feed file for performance (less than 1 day old)");
            }
        }
    }
    
    @Override
    public String getCacheStatus() {
        StringBuilder status = new StringBuilder();
        
        // Check temp feed files
        File directory = new File(tempFeedFileFolder);
        File[] feedFiles = directory.listFiles((dir, name) -> name.startsWith(TMP_FEED_FILE_NAME));
        
        if (feedFiles != null && feedFiles.length > 0) {
            long newestFileTime = Arrays.stream(feedFiles)
                    .mapToLong(File::lastModified)
                    .max()
                    .orElse(0);
            
            LocalDateTime fileTime = LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(newestFileTime), 
                ZoneId.systemDefault()
            );
            
            long ageHours = (System.currentTimeMillis() - newestFileTime) / (60 * 60 * 1000);
            status.append(String.format("Feed files: %d files, newest: %s (%d hours old)", 
                feedFiles.length, fileTime, ageHours));
        } else {
            status.append("Feed files: No temp files found");
        }
        
        // Check top 100 file
        String top100FilePath = tempFeedFileFolder + "/" + TOP_100_FEED_FILE_NAME;
        File top100File = new File(top100FilePath);
        
        if (top100File.exists()) {
            long ageHours = (System.currentTimeMillis() - top100File.lastModified()) / (60 * 60 * 1000);
            status.append(String.format(", Top 100 file: exists (%d hours old)", ageHours));
        } else {
            status.append(", Top 100 file: not found");
        }
        
        if (!devMode) {
            status.append(" [Dev mode OFF - caching disabled]");
        }
        
        return status.toString();
    }
}
