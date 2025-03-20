package com.gw.services.chronos24;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.gw.domain.FeedItem;
import com.gw.services.FeedItemService;
import com.gw.services.IFeedService;

@Component
public class Chronos24Service {
    
    @Value("${web.driver.type}")
    private String webDriverType;
    
    @Value("${web.driver.path}")
    private String webDriverPath;
    
    private IFeedService feedService;
    
    @Autowired
    private FeedItemService feedItemService;
    
    final static Logger logger = LogManager.getLogger(Chronos24Service.class);
    
//    public void verifyChronos24AgainstFeed(BaseFeedService feedService) throws Exception
//    {   
//        this.feedService = feedService;
//        
//        StringBuffer reportTxtBuff = new StringBuffer();
//        reportTxtBuff.append("############ REPORT FOR CHRONOS24 FEED vs. CHRONOS24 LISTINGS ##############\n\n");
//
//        Map<String, FeedItem> fromFeed = getFeedItemBySkuFromDb();
//        reportTxtBuff.append("Number of listings found on feed : " + fromFeed.size() + "\n");
//        
//        if (fromFeed.size() == 0) {
//            reportTxtBuff.append("Feed might be having problems. Skipping compare.\n");
//            logger.error(reportTxtBuff.toString());
//            emailService.sendMessage("Chronos24 error report", reportTxtBuff.toString());
//        }else {
//            Map<String, FeedItem> fromSite = getItemBySkuFromSite();
//            reportTxtBuff.append("Number of listings found on site : " + fromSite.size() + "\n");
//            
//            List<String> sortedSkus = new ArrayList<String>(fromFeed.keySet());
//            Collections.sort(sortedSkus);
//            reportTxtBuff.append("\n\n############ Skus missing or price mismatch ##############\n\n");
//            for (String skuFromFeed : sortedSkus) {
//                FeedItem itemFromFeed = fromFeed.get(skuFromFeed);
//                if (!fromSite.containsKey(skuFromFeed)) {
//                    reportTxtBuff.append(
//                            "Sku: "+ skuFromFeed
//                            + " : " + itemFromFeed.getWebDescriptionShort()
//                            + "\n");
//                } else {
//                    FeedItem itemFromSite = fromSite.get(skuFromFeed); 
//                    if (itemFromFeed.getWebPriceChronos() != null) {
//                        String priceFromSite = itemFromSite.getWebPriceChronos();
//                        if (priceFromSite != null && !priceFromSite.equals(itemFromFeed.getWebPriceChronos())){
//                            reportTxtBuff.append(itemFromFeed.getWebTagNumber() + " price doesnt match! site: " + priceFromSite 
//                            + " vs. feed:"+  itemFromFeed.getWebPriceChronos() + "\n");
//                            
//                        }
//                    }
//                }
//            }
//            
//            reportTxtBuff.append("\n\n############ Skus shouldn't be listed ##############\n\n");
//            List<String> sortedSkuFromSite = new ArrayList<String>(fromSite.keySet());
//            Collections.sort(sortedSkuFromSite);
//            for (String skuFromSite : sortedSkuFromSite) {
//                FeedItem feedItemfromSite = fromSite.get(skuFromSite);
//                FeedItem fromDb = feedItemService.findByWebTagNumber(skuFromSite);
//                if (fromDb == null) {
//                    reportTxtBuff.append("Sku: " + skuFromSite +
//                            " : "+ feedItemfromSite.getWebDescriptionShort() + "\n");
//                }
//            }
//            
//            logger.info(reportTxtBuff.toString());
//            emailService.sendMessage("Chronos24 error report", reportTxtBuff.toString());
//        } 
//    }
    
    public Map<String, FeedItem>getFeedItemBySkuFromDb() throws Exception{
        feedItemService.deleteAllAutonomous();
        List<FeedItem> allItems = feedService.getItemsFromFeed();
        Map<String, FeedItem> bySku = new HashMap<String, FeedItem>();
        for (FeedItem aItem : allItems) {
            feedItemService.saveAutonomous(aItem);
            bySku.put(aItem.getWebTagNumber(), aItem);
        }
        return bySku;
    }
    
//    private WebDriver getDriver() {
//        WebDriver driver = null;
//        if (webDriverType.equalsIgnoreCase("chromedriver")) {
//            System.setProperty("webdriver.chrome.driver", webDriverPath);
//            driver = new ChromeDriver();
//        } else if (webDriverType.equalsIgnoreCase("htmlunitdriver")) {
//            driver = new HtmlUnitDriver();
//        }
//        return driver;
//    }
//    
//    public Map<String, FeedItem> getItemBySkuFromSite(){
//
//        WebDriver driver = getDriver();
//
//        Map<String, FeedItem> itemBySku = new HashMap<String, FeedItem>();
//        driver.get("http://www.chrono24.com/search/index.htm?customerId=13762&dosearch=true&pageSize=120&showpage=1");
//        try {
//            boolean atLastPage = false;
//            while (!atLastPage) {
//                
//                List<WebElement> watchContainerDivs = driver.findElements(By.className("article-item-container"));
//                for (WebElement watchContainerDiv : watchContainerDivs) {
//                    
//                    //Click into the detail page
//                    WebElement itemElement = watchContainerDiv.findElement(By.cssSelector("a.article-item"));
//                    String linkToDetail = itemElement.getAttribute("href");
//                    WebDriver subPageDriver = null;
//                    String sku = null;
//                    String title = null;
//                    String price = null;
//                    try {
//                        subPageDriver = getDriver();
//                        subPageDriver.get(linkToDetail);
//                        
//                        title = getTitle(subPageDriver);
//                        price = getPrice(subPageDriver);
//                        sku=getSku(subPageDriver);
//
//                        if (sku != null && !sku.isEmpty()) {
//                            FeedItem newItem = new FeedItem();
//                            newItem.setWebTagNumber(sku);
//                            newItem.setWebDescriptionShort(title);
//                            newItem.setWebPriceChronos(price);
//                            itemBySku.put(sku, newItem);
//                            logger.info("Sku: " + sku + " title: " + title + " Price: " + price);
//                        } else {
//                            logger.error("Can't parse out sku!");
//                        }
//                    } finally {
//                        subPageDriver.quit();
//                    }
//                }
//                WebElement paginationElement = null;
//                try {
//                    paginationElement = driver.findElement(By.cssSelector("ul.pagination"));
//                } catch (Exception e) {
//                    logger.error("Pagination control is missing! Might have met a bad page.");
//                    throw new IllegalArgumentException("Html is incorrect: Can't find ul.pagination element.");
//                }
//                if (paginationElement != null) {
//                    try {
//                        WebElement nextPageLink = paginationElement.findElement(By.linkText("Next"));
//                        //Doesn't work well as there might be modal popups that prevents the click.
//                        //nextPageLink.click();
//                        String nextPageUrl = nextPageLink.getAttribute("href");
//                        driver.get(nextPageUrl);
//                    } catch (Exception e) {
//                        atLastPage = true;
//                    }
//                }
//            }
//        } finally {
//            driver.quit();
//        }
//        return itemBySku;
//    }
//    
//    private String getTitle(WebDriver subPageDriver) {
//        String title = null;
//        try {
//            WebElement titleElement = subPageDriver.findElement(
//                    By.cssSelector("div.full-content > div.alternating > section.data > div.container > div.media > div.media-body > h1.m-b-4"));
//            title = titleElement.getText();
//            title = Normalizer.normalize(title,
//                    Normalizer.Form.NFKD).replaceAll("\\p{M}", "");
//            title = StringUtils.strip(title, "."); //Strip off "..."
//        } catch (Exception e) {
//            //Do nothing
//        }
//        return title;
//    }
//    
//    private String getPrice(WebDriver subPageDriver) {
//        String price=null;
//        try {
//            WebElement priceElement = subPageDriver.findElement(
//                    By.cssSelector("div.full-content > div > section.data > div.container > div.row > div.col-sm-12.col-md-10.m-b-3 > div:nth-child(1) > span.price-lg > div"));
//            String priceString = priceElement.getText();
//            if (priceString != null) {
//                price = priceString.replaceAll("[^0-9.]", "");
//            }
//        } catch (Exception e) {
//            //Do nothing
//        }
//        return price;
//    }
//    
//    private String getSku(WebDriver subPageDriver) {
//        String sku=null;
//        try {
//            List<WebElement> basicInfoTRElements = 
//                    subPageDriver.findElements(By.cssSelector("div.col-md-12 > table > tbody > tr"));
//            //Cycle through and get the "Code" field which is the sku.
//            for (WebElement tr : basicInfoTRElements) {
//                try {
//                    WebElement td1 = tr.findElement(By.cssSelector("td:nth-of-type(1) > strong"));
//                    if ("Code".equalsIgnoreCase(td1.getText())) {
//                        sku = tr.findElement(By.cssSelector("td:nth-of-type(2)")).getText();
//                        continue;
//                    }
//                } catch (Exception e) {
//                    //Do nothing.
//                }
//            }
//        } catch (Exception e) {
//            //Do nothing
//        }
//        return sku;
//    }
}
