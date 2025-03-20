package com.gw.services.whatsApp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.JsonNode;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.ArrayList;

@Service
public class WhatsAppService {
    private static final Logger logger = LogManager.getLogger(WhatsAppService.class);
    private static final String API_BASE_URL = "https://api.wassenger.com/v1";
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${wassenger.api.token}")
    private String apiToken;

    @Value("${whatsapp.group.id}")
    private String defaultGroupId; 

    public WhatsAppService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    public String getTargetGroupId(){
        return this.defaultGroupId;
    }

    /**
     * Sends a file message to a WhatsApp group
     * @param groupId The group ID (e.g., "123456789@g.us")
     * @param fileUrl The URL of the file to send
     * @param caption Optional caption for the file
     * @return boolean indicating if the message was sent successfully
     */
    public boolean sendGroupFileMessage(String groupId, String fileUrl, String caption) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Token", apiToken);

            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("group", groupId==null?defaultGroupId:groupId  );
            requestBody.put("file", fileUrl);
            if (caption != null && !caption.isEmpty()) {
                requestBody.put("caption", caption);
            }
            requestBody.put("priority", "normal");

            HttpEntity<String> request = new HttpEntity<>(requestBody.toString(), headers);
            
            ResponseEntity<String> response = restTemplate.postForEntity(
                API_BASE_URL + "/messages",
                request,
                String.class
            );

            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            logger.error("Error sending WhatsApp group file message", e);
            return false;
        }
    }

    /**
     * Lists all WhatsApp groups
     * @return List of group IDs or empty list if error occurs
     */
    public List<String> listGroups() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Token", apiToken);

            HttpEntity<String> request = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.getForEntity(
                API_BASE_URL + "/groups",
                String.class,
                request
            );

            JsonNode groupsNode = objectMapper.readTree(response.getBody());
            List<String> groups = new ArrayList<>();
            if (groupsNode.isArray()) {
                for (JsonNode group : groupsNode) {
                    groups.add(group.path("id").asText());
                }
            }
            return groups;
        } catch (Exception e) {
            logger.error("Error listing groups", e);
            return new ArrayList<>();
        }
    }

    public boolean sendGroupMultipleImages(String groupId, List<String> imageUrls, String caption) {
        return sendGroupMultipleImages(groupId, imageUrls, caption,false);
    }

    /**
     * Sends multiple images to a WhatsApp group with the same caption
     * @param groupId The group ID (e.g., "123456789@g.us")
     * @param imageUrls List of image URLs to send
     * @param caption Caption to use for all images
     * @return boolean indicating if all images were sent successfully
     */
    public boolean sendGroupMultipleImages(String groupId, List<String> imageUrls, String caption, boolean separateDescriptionMessage) {
        String targetGroupId = groupId==null?defaultGroupId:groupId;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Token", apiToken);

            boolean allSuccess = true;
            logger.debug("Preparing to send {} images with same caption to group: {}", imageUrls.size(), targetGroupId);
            
            // Set message content
            String messageText = "";
            if (caption != null && !caption.isEmpty()) {
                messageText = caption;
                logger.debug("Using caption for all images: {}", caption);
            }

            for (int i = 0; i < imageUrls.size(); i++) {
                ObjectNode requestBody = objectMapper.createObjectNode();
                requestBody.put("live", Boolean.TRUE);
                //requestBody.put("enqueue", "never");
                requestBody.put("group", targetGroupId);
                if (i == imageUrls.size() -1 && !separateDescriptionMessage){
                    //Only add the caption on the final image.
                    requestBody.put("message", messageText);
                }  
                // Create media object
                ObjectNode mediaObject = objectMapper.createObjectNode();
                mediaObject.put("url", imageUrls.get(i));
                requestBody.set("media", mediaObject);

                String requestBodyJson = requestBody.toString();
                logger.debug("Sending request to Wassenger API: {}", requestBodyJson);
                
                HttpEntity<String> request = new HttpEntity<>(requestBodyJson, headers);
                allSuccess = send(request);
                // Add a small delay between messages to prevent rate limiting
                try {
                    // Random delay between 2-6 seconds to prevent rate limiting
                    Thread.sleep(ThreadLocalRandom.current().nextInt(2000, 6001));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            if (separateDescriptionMessage) {
                ObjectNode requestBody = objectMapper.createObjectNode();
                requestBody.put("live", Boolean.TRUE);
                //requestBody.put("enqueue", "never");
                requestBody.put("group", targetGroupId);
                requestBody.put("message", messageText);
              
                String requestBodyJson = requestBody.toString();
                logger.debug("Sending request to Wassenger API: {}", requestBodyJson);
                
                HttpEntity<String> request = new HttpEntity<>(requestBodyJson, headers);
                allSuccess = send(request);
                // Add a small delay between messages to prevent rate limiting
                try {
                    // Random delay between 2-6 seconds to prevent rate limiting
                    Thread.sleep(ThreadLocalRandom.current().nextInt(2000, 6001));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return allSuccess;
        } catch (Exception e) {
            logger.error("Error sending multiple images to WhatsApp group", e);
            return false;
        }
    }

    private boolean send(HttpEntity<String> request){
        boolean isSuccess = true;
        int maxRetry = 5;
        int retry = 0;
        do {
            try {
                ResponseEntity<String> response = restTemplate.postForEntity(
                    API_BASE_URL + "/messages",
                    request,
                    String.class
                );
                
                logger.debug("API response status: {}", response.getStatusCode());
                logger.debug("API response body: {}", response.getBody());
    
                if (!response.getStatusCode().is2xxSuccessful()) {
                    logger.error("Failed to send: - Response: {}", response.getBody());
                    //No retry here.
                    retry = maxRetry;
                    isSuccess = false;
                } else {
                    logger.info("Successfully sent.");
                }
            } catch (Exception e) {
                logger.error("API request failed: ", e);
                if (e.getMessage().contains("Please try again in 30 seconds.")
                    || e.getMessage().contains("Failed to send message in real-time due to number session is not online: online.")
                    || e.getMessage().contains("Failed to download the file from remote URL, the server cannot be reached from the Internet, timed-out or returned an HTTP error")
                ){
                    logger.info("Retrying attemp: {}/{} ", retry, maxRetry);
                    retry++;
                    isSuccess = false;
                    try {
                        int randomSleepTime = ThreadLocalRandom.current().nextInt(15, 41);
                        logger.info("Sleeping for {} seconds before retrying...", randomSleepTime);
                        Thread.sleep(randomSleepTime * 1000);
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                } else {
                    //No retry
                    retry = maxRetry;
                    isSuccess = false;
                }
            }
        } while (!isSuccess && retry<maxRetry);

        // Add a small delay between messages to prevent rate limiting
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (!isSuccess){
            throw new RuntimeException("Not sent, max retry reached or no retry due to unhandled exceptions.");
        }

        return isSuccess;
    }
} 