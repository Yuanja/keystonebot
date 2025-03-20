package com.gw.services.whatsApp;

import com.gw.domain.FeedItem;
import com.gw.services.BaseFeedService;
import com.gw.services.FeedItemService;
import com.gw.services.GoogleSheetService;
import com.gw.services.LogService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * @author jyuan
 *
 */
@Service
@Profile({"bonetawhatsapp-prod", "bonetawhatsapp-dev", "mafiabonetawhatsapp-prod", "mafiabonetawhatsapp-dev"})
public class BonetaFeedService extends BaseFeedService {
        
    private static Logger logger = LogManager.getLogger(BonetaFeedService.class);
    
    @Autowired
    protected LogService logService;
    
    @Autowired
    protected GoogleSheetService gsheetService;
    
    @Autowired
    protected FeedItemService feedItemService;

    @Autowired
    protected WhatsAppService whatsAppService;
    
    /* (non-Javadoc)
     * @see com.gw.components.IGWFeedService#get()
     */
    @Override
	public List<FeedItem> getItemsFromFeed() throws IOException, ParserConfigurationException, SAXException{
        
        try {
            List<FeedItem> rawItems  = mapToFeedItems(gsheetService.fetchAndMapSheetData(feedUrl));
            
            return getAccepted(rawItems);
        } catch (IOException  e) {
            throw new RuntimeException("Got IOException while reading googlesheet: ", e); 
        } catch (GeneralSecurityException ge) {
            throw new RuntimeException("Got GeneralSecurityException while reading googlesheet: ", ge); 
        }
    }

    /**
     * Loading from csv file
     */
    @Override
    public List<FeedItem> loadFromXmlFile(String filePath) throws ParserConfigurationException, SAXException, IOException{
        logger.info("Not implemented, reading from feed.");
        return getItemsFromFeed();
    }
       
    @Override
	public boolean toAcceptFromFeed(FeedItem feedItem) {
        if (feedItem.getWebTagNumber() == null)
            return false;

        if (feedItem.getWebDescriptionShort() == null)
            return false;

        return true;
    }
    
    /**
     * Creates a FeedItem object from a row of sheet data
     * 
     * @param row The data row
     * @param columnMap Map of column names to indices
     * @return A populated FeedItem
     */
    @Override
    public FeedItem createFeedItemFromRow(List<Object> row, Map<String, Integer> columnMap) {
        FeedItem item = new FeedItem();
        
        // Map common column names to FeedItem properties
        mapIfExists(row, columnMap, "Item ID", item::setWebTagNumber);
        mapIfExists(row, columnMap, "Item Image", item::setWebImagePath1);

        item.setWebDescriptionShort(getFormatedDescription(row, columnMap));
        
        return item;
    }

    public String getFormatedDescription(List<Object> row, Map<String, Integer> columnMap){
        //getValue for retail price
        String retailPriceString = getValueAsString(row, columnMap, "Retail");
        String discountString = getValueAsString(row, columnMap, "Discount");
        String priceString = getValueAsString(row, columnMap, "Price");
        String descriptionString = getValueAsString(row, columnMap, "Description");
        StringBuffer descBuffer = new StringBuffer();
        if (descriptionString != null){
            /* sample
            *      Unworn Audemars Piguet 26381BC.ZZ.D312CR.01 White Gold With Box & Papers 
                    Retail: $556,000
                    Discount: 55%
                    Price: $250,470

                    Item ID: 1983288
            */
            descBuffer.append(descriptionString).append("\n\n");
            if (retailPriceString != null){
                descBuffer.append("Retail: ").append(retailPriceString).append("\n");
            }
            if (discountString != null){
                descBuffer.append("Discount: ").append(discountString).append("\n\n");
            }
            if (priceString != null){
                descBuffer.append("Price: *").append(priceString).append("*").append("\n");
            }

            descBuffer.append("\n").append("Item ID: ").append(getValueAsString(row, columnMap, "Item ID"));
            descBuffer.append("\n\n").append("<a href=\"https://bonetawholesale.com\">" + "BonetaWholesale.com" + "</a>");
        }
        return descBuffer.toString();
    }
    
    /**
     * Maps a value from a row to a property setter if the column exists
     * @param row The data row
     * @param columnMap Map of column names to indices
     * @param columnName The name of the column
     * @param setter The property setter method reference
     */
    private void mapIfExists(List<Object> row, Map<String, Integer> columnMap, String columnName, 
                             java.util.function.Consumer<String> setter) {
        String value = getValueAsString(row, columnMap, columnName);
        if (value != null) {
            setter.accept(value);
        }
    }
    
    /**
     * Gets a value from a row as a string if it exists
     * @param row The data row
     * @param columnMap Map of column names to indices
     * @param columnName The name of the column
     * @return The value as a string or null if it doesn't exist
     */
    private String getValueAsString(List<Object> row, Map<String, Integer> columnMap, String columnName) {
        Integer index = columnMap.get(columnName);
        if (index != null && index < row.size()) {
            Object value = row.get(index);
            if (value != null) {
                String strValue = String.valueOf(value).trim();
                return StringUtils.hasText(strValue) ? strValue : null;
            }
        }
        return null;
    }

    /**
     * Maps Google Sheet data to a list of FeedItem objects
     * @param sheetData List of rows from the Google Sheet
     * @return List of FeedItem objects
     */
    public List<FeedItem> mapToFeedItems(List<List<Object>> sheetData) {
        List<FeedItem> feedItems = new ArrayList<>();
        
        // Get headers from the first row
        List<Object> headers = sheetData.get(0);
        Map<String, Integer> columnIndexMap = createColumnIndexMap(headers);
        
        //Look for header column with matching whatsapp group id
        String targetGroupId = whatsAppService.getTargetGroupId();
        String groupIdColumnName = null;

        for (Object header : headers) {
            String headerStr = String.valueOf(header).trim();
            if (headerStr.toLowerCase().startsWith("group") && headerStr.contains("_")) {
                String[] parts = headerStr.split("_");
                String lastPart = parts[parts.length - 1];
                if (lastPart.equals(targetGroupId)) {
                    groupIdColumnName = headerStr;
                    break;
                }
            }
        }

        if (groupIdColumnName == null) {
            throw new RuntimeException("Cant' list any thing: find any column that matched target WhatsApp group id : " + targetGroupId);
        }

        // Process each data row (skip the header row)
        for (int i = 1; i < sheetData.size(); i++) {
            List<Object> row = sheetData.get(i);
            String toListString = getValueAsString(row, columnIndexMap, groupIdColumnName);
            if (toListString != null && toListString.equalsIgnoreCase("1")){
                FeedItem item = createFeedItemFromRow(row, columnIndexMap);
                feedItems.add(item);
            }
        }
        
        return feedItems;
    }
    
    /**
     * Creates a map of column names to their indices
     * @param headers The header row from the sheet
     * @return Map of column names to indices
     */
    private Map<String, Integer> createColumnIndexMap(List<Object> headers) {
        Map<String, Integer> columnMap = new HashMap<>();
        
        for (int i = 0; i < headers.size(); i++) {
            String header = String.valueOf(headers.get(i)).trim();
            columnMap.put(header, i);
        }
        
        return columnMap;
    }
}
