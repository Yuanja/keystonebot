package com.gw.services;

import com.gw.domain.FeedItem;
import com.gw.ssl.SSLUtilities;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author jyuan
 *
 */
public class BaseFeedService implements IFeedService {
    
    static {
        SSLUtilities.disableSslVerification();
    }
    
    private static Logger logger = LogManager.getLogger(BaseFeedService.class);
    private static String TMP_FEED_FILE_NAME = "tmpFeed";
    
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
    
    /* (non-Javadoc)
     * @see com.gw.components.IGWFeedService#get()
     */
    @Override
	public List<FeedItem> getItemsFromFeed() throws IOException, ParserConfigurationException, SAXException{
        List<FeedItem> rawLoad = new ArrayList<>();
        if (readFeed ){
            int skipCounter = 0;
            int batchSize = 2000;
            deleteTmpFiles();
            do {
                String paginatedUrl = feedUrl + "&-max="+batchSize+"&-skip=" + skipCounter * batchSize;
                logger.info("Reading feed from:" + paginatedUrl);
                //Add custom ssl trust manager to trust all ssl certs.
                SSLUtilities.trustAllHostnames();
                SSLUtilities.trustAllHttpsCertificates();
                String tempFeedFilePath = tempFeedFileFolder+"/"+TMP_FEED_FILE_NAME+skipCounter+".xml";

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
                } else
                    skipCounter = -1;

            } while (skipCounter > 0);

            logger.info("All feed downloaded successfully.  Total read feedItem count: " + rawLoad.size());
            
        } else {
            logger.info("Not configured to read from feed.");
        }
        return rawLoad;
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
        
        //if in dev Mode truncate things.
        if (devMode) {
            logger.info("Dev Mode is ON!!! TRUNCATING THE FEED TO TOP: " + devModeMaxReadCount);
            logger.info("Ordering feed by sku descending.  Processing newly listed first.");
            feedItems.sort(FeedItem.FeedItemSortBySkuDscComparator);
            feedItems = feedItems.stream().limit(devModeMaxReadCount).collect(Collectors.toList());

            if (devModeSpecificSku!=null){
                logger.info("Dev Mode isdevModeSpecificSku is ON!!! TRUNCATING THE FEED TO SKU: " + devModeSpecificSku);
                feedItems = feedItems.stream().filter(c->c.getWebTagNumber().equals(devModeSpecificSku)).collect(Collectors.toList());
                if (feedItems.isEmpty()) {
                    throw new IOException("Sku not found.");
                }
            }
        }
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
    
}
