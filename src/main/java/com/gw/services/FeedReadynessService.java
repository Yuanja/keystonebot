package com.gw.services;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.gw.ssl.SSLUtilities;

/**
 * @author jyuan
 *
 */
@Component
public class FeedReadynessService {
    
    static {
        SSLUtilities.disableSslVerification();
    }
    
    private static Logger logger = LogManager.getLogger(FeedReadynessService.class);
    
    @Autowired
    protected LogService logService;
    
    @Value("${TMPFEED_FILE_FOLDER}") String tempFeedFileFolder;
    @Value("${GW_FEED_READYNESS_URL}") String feedReadynessUrl;
    @Value("${SHOULD_CHECK_GW_FEED_READYNESS}") boolean doCheck;
    
    public boolean isFeedReady() throws IOException, ParserConfigurationException, SAXException{
        if (!doCheck){
            logger.info("Not checking GW Feed Readyness. Feed is ready.");
            return true;
        }
        
    	logger.info("Checking on feed readyness." + feedReadynessUrl);
        
        //Add custom ssl trust manager to trust all ssl certs.
        SSLUtilities.trustAllHostnames();
        SSLUtilities.trustAllHttpsCertificates();
        String readinessFilePath = tempFeedFileFolder+"/ready.xml";
        FileUtils.copyURLToFile(
                new URL(feedReadynessUrl), 
                new File(readinessFilePath), 
                60*5*1000, 
                60*5*1000);
        return pareseReadynessFromFile(readinessFilePath);
    }
    
    private boolean pareseReadynessFromFile(String filePath) throws ParserConfigurationException, SAXException, IOException {
    	 //populate the list of items.
        Document doc = getDocument(filePath);
        NodeList recordNodeList = doc.getElementsByTagName("record");
        if (recordNodeList.getLength() == 0) {
        	logger.error("Feed Readyness url returned 0 length records. Skipping.");
    		return false;
        }
        Node recordNode = recordNodeList.item(0);
        NodeList fieldNodeList = recordNode.getChildNodes();
        for (int i=0; i<fieldNodeList.getLength(); i++){
            //each field node has a data node
            Node fieldNode = fieldNodeList.item(i);
            //String fieldName = ((Element)fieldNode).getAttribute("name");
            if ("field".equals(fieldNode.getNodeName())) {
                String fieldName = fieldNode.getAttributes().getNamedItem("name").getNodeValue();
                Node dataChildNode  = fieldNode.getFirstChild();
                while (!"data".equals(dataChildNode.getNodeName())) {
                    dataChildNode = dataChildNode.getNextSibling();
                }
                String dataValue = dataChildNode.getTextContent(); 
                if (fieldName.equals("web_refresh_running")){
                	logger.info("web_refresh_running="+dataValue);
                	if (dataValue.equals("1")) { //1 is definitely running.  null or 0 is not.
                		return false;
                	}
                	return true;
                }
            }
        }
        
    	return false;
    }
    
    private Document getDocument(String filePath) throws ParserConfigurationException, SAXException, IOException {
        //populate the list of items.
        File xmlFeedFile = new File(filePath);
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setNamespaceAware(true);
        dbFactory.setValidating(false);
        dbFactory.setValidating(false);
        dbFactory.setNamespaceAware(true);
        dbFactory.setFeature("http://xml.org/sax/features/namespaces", false);
        dbFactory.setFeature("http://xml.org/sax/features/validation", false);
        dbFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
        dbFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(xmlFeedFile);
        return doc;
    }
}
