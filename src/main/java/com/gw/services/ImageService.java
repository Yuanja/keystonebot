package com.gw.services;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.convertapi.client.Config;
import com.convertapi.client.ConvertApi;
import com.convertapi.client.Param;
import com.gw.domain.FeedItem;
import com.gw.ssl.SSLUtilities;

/**
 * Centralized Image Processing Service
 * 
 * Handles all image-related operations for the application:
 * - Image downloading and validation
 * - Format conversion (HEIC to JPG)
 * - Image compression and optimization
 * - URL processing and correction
 * 
 * Benefits:
 * - Single Responsibility: All image logic in one place
 * - Reusable: Can be used by any service needing image processing
 * - Consistent: Standardized image handling across the application
 */
@Component
public class ImageService {
    protected @Value("${image.source.ip}") String imageSourceIp;
    protected @Value("${css.hosting.url.base}") String cssHostingUrlBase;
    private @Value("${image.store.dir}") String imageStore;
    private @Value("${skip.image.download:false}") boolean skipImageDownload;

    static {
        SSLUtilities.disableSslVerification();
    }
    
    private static Logger logger = LogManager.getLogger(ImageService.class);

    /**
     * Unified Image Processing Pipeline
     * 
     * Handles the complete image processing workflow with graceful error handling:
     * - Downloads images if not configured to skip
     * - Validates and converts formats
     * - Compresses images as needed
     * - Provides detailed logging
     * 
     * @param item The feed item containing image URLs
     * @param itemIdentifier Identifier for logging (e.g., SKU)
     * @return ImageProcessingResult with success status and details
     */
    public ImageProcessingResult handleImageProcessing(FeedItem item, String itemIdentifier) {
        if (skipImageDownload) {
            logger.debug("⏭️ Skipping image processing for {} (skip.image.download=true)", itemIdentifier);
            return ImageProcessingResult.skipped();
        }

        logger.info("📸 Processing images for {}", itemIdentifier);
        try {
            downloadImages(item);
            logger.debug("✅ Images processed successfully for {}", itemIdentifier);
            return ImageProcessingResult.success();
        } catch (Exception e) {
            logger.warn("⚠️ Failed to process images for {} - {}", itemIdentifier, e.getMessage());
            return ImageProcessingResult.failure(e);
        }
    }

    /**
     * Download images for a feed item with comprehensive validation
     */
    public void downloadImages(FeedItem feedItem) throws Exception {
        //Ensure folder exists
        File imageStoreFolder = new File(imageStore);
        if (!imageStoreFolder.exists()) {
            imageStoreFolder.mkdir();
        }
        
        if (!imageStoreFolder.canWrite()) {
            throw new Exception("Can't write to image store folder." + imageStore);
        }

        String image1FileName = imageStore+File.separator+feedItem.getWebTagNumber() + "-1.jpg";
        String image2FileName = imageStore+File.separator+feedItem.getWebTagNumber() + "-2.jpg";
        String image3FileName = imageStore+File.separator+feedItem.getWebTagNumber() + "-3.jpg";
        String image4FileName = imageStore+File.separator+feedItem.getWebTagNumber() + "-4.jpg";
        String image5FileName = imageStore+File.separator+feedItem.getWebTagNumber() + "-5.jpg";
        String image6FileName = imageStore+File.separator+feedItem.getWebTagNumber() + "-6.jpg";
        String image7FileName = imageStore+File.separator+feedItem.getWebTagNumber() + "-7.jpg";
        String image8FileName = imageStore+File.separator+feedItem.getWebTagNumber() + "-8.jpg";
        String image9FileName = imageStore+File.separator+feedItem.getWebTagNumber() + "-9.jpg";
        
        downloadImagesIfAvailable(feedItem.getWebImagePath1(), image1FileName);
        downloadImagesIfAvailable(feedItem.getWebImagePath2(), image2FileName);
        downloadImagesIfAvailable(feedItem.getWebImagePath3(), image3FileName);
        downloadImagesIfAvailable(feedItem.getWebImagePath4(), image4FileName);
        downloadImagesIfAvailable(feedItem.getWebImagePath5(), image5FileName);
        downloadImagesIfAvailable(feedItem.getWebImagePath6(), image6FileName);
        downloadImagesIfAvailable(feedItem.getWebImagePath7(), image7FileName);
        downloadImagesIfAvailable(feedItem.getWebImagePath8(), image8FileName);
        downloadImagesIfAvailable(feedItem.getWebImagePath9(), image9FileName);
    }
    
    private void downloadImagesIfAvailable(String httpUrl, String imageFileName) throws Exception{
        File imageFile = new File(imageFileName);

        if (httpUrl != null) {
            String urlToUse = replaceIPAddressInUrl(httpUrl, imageSourceIp);
            
            logger.info("Downloading image from: " + urlToUse);
            
            URL imageUrl = new URL(urlToUse);
            HttpURLConnection connection = (HttpURLConnection) imageUrl.openConnection();
            final int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                InputStream input = new BufferedInputStream(imageUrl.openStream());
                Files.copy(input, imageFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                logger.info("Saved image " + imageFileName);
            } else {
                logger.error("Image URL returned non 200 code.  Skipping. ");
                throw new Exception("Image failed to download: " + urlToUse);
            }
        }
    }




    /**
     * Uses Apache Tika to check if a given file is a HEIC image by analyzing its MIME type.
     * This provides a more robust detection compared to checking file signatures manually.
     * 
     * @param file Image file to check
     * @return true if the file is HEIC, false otherwise
     */
    public boolean isHeicImage(File file) {
        try {
            Tika tika = new Tika();
            String mimeType = tika.detect(file);
            
            // Check for HEIC MIME types
            return mimeType != null && (
                mimeType.equals("image/heic") || 
                mimeType.equals("image/heif") ||
                mimeType.equals("image/heic-sequence") ||
                mimeType.equals("image/heif-sequence")
            );
            
        } catch (IOException e) {
            logger.error("Error checking if file is HEIC using Tika: " + file.getName(), e);
            return false;
        }
    }

    /**
     * Uses Apache Tika to check if a given file is a JPG/JPEG image by analyzing its MIME type.
     * 
     * @param file Image file to check
     * @return true if the file is JPG/JPEG, false otherwise
     */
    public boolean isJpg(File file) {
        try {
            Tika tika = new Tika();
            String mimeType = tika.detect(file);
            
            // Check for JPG/JPEG MIME types
            return mimeType != null && (
                mimeType.equals("image/jpeg") ||
                mimeType.equals("image/jpg")
            );
            
        } catch (IOException e) {
            logger.error("Error checking if file is JPG using Tika: " + file.getName(), e);
            return false;
        }
    }

    /**
     * Compresses an image file if it is larger than 1MB.
     * The compression is done by reducing the JPEG quality iteratively until the file size
     * is below 1MB or the minimum quality threshold (0.1) is reached.
     * 
     * @param imageFilePath The full path to the image file to compress
     */
    public void assertImageUnder1MB(String imageFilePath){
        try {
            File imageFile = new File(imageFilePath);
            if (!imageFile.exists()) {
                logger.warn("Image file does not exist: " + imageFilePath);
                return;
            }

            // Check if file size is greater than 1MB
            long fileSize = imageFile.length();
            if (fileSize > 1024 * 1024) { // 1MB = 1024 * 1024 bytes
                logger.info("Image size is " + (fileSize/1024/1024) + "MB, compressing: " + imageFilePath);
                
                float quality = 0.7f;
                byte[] compressedBytes;
                
                do {
                    // Read the image
                    BufferedImage originalImage = ImageIO.read(imageFile);
                    
                    // Create output stream
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    
                    // Compress and write to output stream
                    Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
                    ImageWriter writer = writers.next();
                    ImageWriteParam params = writer.getDefaultWriteParam();
                    params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    params.setCompressionQuality(quality);
                    
                    ImageOutputStream imageOutputStream = ImageIO.createImageOutputStream(outputStream);
                    writer.setOutput(imageOutputStream);
                    writer.write(null, new IIOImage(originalImage, null, null), params);
                    
                    // Clean up
                    writer.dispose();
                    imageOutputStream.close();
                    
                    compressedBytes = outputStream.toByteArray();
                    
                    // Reduce quality if still too large
                    if (compressedBytes.length > 1024 * 1024) {
                        quality -= 0.1f;
                        if (quality < 0.1f) {
                            logger.warn("Could not compress image below 1MB even at lowest quality: " + imageFilePath);
                            return;
                        }
                    }
                    logger.info("Compressed to: " + ((double)compressedBytes.length/1024/1024));
                } while (compressedBytes.length > 1024 * 1024);
                
                // Write compressed image back to file
                Files.write(imageFile.toPath(), compressedBytes);
                
                logger.info("Successfully compressed image to " + ((double)compressedBytes.length/1024/1024) + "MB with quality " + quality + ": " + imageFilePath);
            }
        } catch (Exception e) {
            logger.error("Error compressing image: " + imageFilePath, e);
        }
    }
    
    /**
     * Generate CSS-hosted external image URLs for a feed item
     * Used by ProductImageService for Shopify image upload
     */
    public String[] getAvailableExternalImagePathByCSS(FeedItem feedItem) {
        List<String> availableImagePath = new ArrayList<String>();
        if (feedItem.getWebImagePath1()!=null) {
            availableImagePath.add(cssHostingUrlBase + "/images/watches/" + feedItem.getWebTagNumber() + "-1.jpg");
        }
        if (feedItem.getWebImagePath2()!=null) {
            availableImagePath.add(cssHostingUrlBase + "/images/watches/" + feedItem.getWebTagNumber() + "-2.jpg");
        }
        if (feedItem.getWebImagePath3()!=null) {
            availableImagePath.add(cssHostingUrlBase + "/images/watches/" + feedItem.getWebTagNumber() + "-3.jpg");
        }
        if (feedItem.getWebImagePath4()!=null) {
            availableImagePath.add(cssHostingUrlBase + "/images/watches/" + feedItem.getWebTagNumber() + "-4.jpg");
        }
        if (feedItem.getWebImagePath5()!=null) {
            availableImagePath.add(cssHostingUrlBase + "/images/watches/" + feedItem.getWebTagNumber() + "-5.jpg");
        }
        if (feedItem.getWebImagePath6()!=null) {
            availableImagePath.add(cssHostingUrlBase + "/images/watches/" + feedItem.getWebTagNumber() + "-6.jpg");
        }
        if (feedItem.getWebImagePath7()!=null) {
            availableImagePath.add(cssHostingUrlBase + "/images/watches/" + feedItem.getWebTagNumber() + "-7.jpg");
        }
        if (feedItem.getWebImagePath8()!=null) {
            availableImagePath.add(cssHostingUrlBase + "/images/watches/" + feedItem.getWebTagNumber() + "-8.jpg");
        }
        if (feedItem.getWebImagePath9()!=null) {
            availableImagePath.add(cssHostingUrlBase + "/images/watches/" + feedItem.getWebTagNumber() + "-9.jpg");
        }
        
        return availableImagePath.toArray(new String[availableImagePath.size()]);
    }

    /**
     * Correct escaped HTML and replace IP address in image URLs
     * Used by ProductImageService for URL processing
     */
    public String getCorrectedImageUrl(String feedImageUrl) {
    	//Image path from the feed contains escaped html. for example:
    	// https://104.173.246.236/fmi/xml/cnt/data.jpg?-db=DEG&amp;-lay=WEB_XML_IMAGE&amp;-recid=46304&amp;-field=image
    	// need to unescape it to:
    	// https://104.173.246.236/fmi/xml/cnt/data.jpg?-db=DEG&-lay=WEB_XML_IMAGE&-recid=29940&-field=image
    	String unescapedPath = StringEscapeUtils.unescapeHtml4(feedImageUrl);
        return replaceIPAddressInUrl(unescapedPath, imageSourceIp);
    }
    
    public String replaceIPAddressInUrl(String rawUrl, String newIp) {
        String ipRegex = "\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}";
        return rawUrl.replaceFirst(ipRegex, newIp);
    }
    
    /**
     * Result wrapper for image processing operations
     */
    public static class ImageProcessingResult {
        private final Exception error;
        private final boolean success;
        private final boolean skipped;
        
        private ImageProcessingResult(Exception error, boolean success, boolean skipped) {
            this.error = error;
            this.success = success;
            this.skipped = skipped;
        }
        
        public static ImageProcessingResult success() {
            return new ImageProcessingResult(null, true, false);
        }
        
        public static ImageProcessingResult failure(Exception error) {
            return new ImageProcessingResult(error, false, false);
        }
        
        public static ImageProcessingResult skipped() {
            return new ImageProcessingResult(null, true, true);
        }
        
        public boolean isSuccess() { return success; }
        public boolean isSkipped() { return skipped; }
        public Exception getError() { return error; }
    }
}
