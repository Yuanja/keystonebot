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
 * @author jyuan
 *
 */
@Component
public class ImageService {
    protected @Value("${image.source.ip}") String imageSourceIp;
    protected @Value("${css.hosting.url.base}") String cssHostingUrlBase;
    protected @Value("${convert_api.secret}") String covertApiSecret;
    protected @Value("${convert_api.token}") String convertApiToken;
    protected @Value("${do_heic_to_jpg_convert}") boolean doConvertToJpg;
    private @Value("${image.store.dir}") String imageStore;

    static {
        SSLUtilities.disableSslVerification();
    }
    
    private static Logger logger = LogManager.getLogger(ImageService.class);

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

        // //Read the pre-downloaded versoin.
        // if (imageFile.exists()){
        //     //confirm it's a jpg and < 1mb.
        //     if (isJpg(imageFile)){
        //         assertImageUnder1MB(imageFileName);
        //         logger.info("Re-using previously downloaded image : "+imageFileName);
        //         return;
        //     } 
        // }

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
                assertImageIsJPG(imageFileName);
                assertImageUnder1MB(imageFileName);
            } else {
                logger.error("Image URL returned non 200 code.  Skipping. ");
                throw new Exception("Image failed to download: " + urlToUse);
            }
        }
    }

    public void assertImageIsJPG(String imageFileName) throws Exception{
        try {
            File imageFile = new File(imageFileName);
            if (!imageFile.exists()) {
                logger.warn("Image file does not exist: " + imageFileName);
                return;
            }

            // Check if file is HEIC
            if (isHeicImage(imageFile) && doConvertToJpg) {
                String tempHEICFilePath = imageFile.getAbsolutePath()+".heic";
                Files.copy(imageFile.toPath(), Paths.get(tempHEICFilePath), StandardCopyOption.REPLACE_EXISTING);
                convertToJPG(new File(tempHEICFilePath), imageFile);
                Files.delete(Paths.get(tempHEICFilePath));
            }

        } catch (Throwable e) {
            logger.error("Error processing image file: " + imageFileName, e);
            throw new Exception("Failed to process image file: " + e.getMessage());
        }
    }

    public void convertToJPG(File inHeic, File outJpg) throws InterruptedException, ExecutionException, IOException{
    
            logger.info("Converting HEIC to JPG: " + inHeic.getAbsolutePath());
            Config.setDefaultSecret(covertApiSecret);
            Config.setDefaultApiKey(convertApiToken);
            ConvertApi.convert("heic", "jpg",
                new Param("File", inHeic.toPath())
            ).get().saveFile(outJpg.toPath()).get();
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
    
    public String[] getAvailableDefaultImageUrl(FeedItem feedItem) {
        List<String> availableImagePath = new ArrayList<String>();
        if (feedItem.getWebImagePath1()!=null) {
            availableImagePath.add(replaceIPAddressInUrl(feedItem.getWebImagePath1(), imageSourceIp));
        }
        if (feedItem.getWebImagePath2()!=null) {
            availableImagePath.add(replaceIPAddressInUrl(feedItem.getWebImagePath2(), imageSourceIp));
        }
        if (feedItem.getWebImagePath3()!=null) {
            availableImagePath.add(replaceIPAddressInUrl(feedItem.getWebImagePath3(), imageSourceIp));
        }
        if (feedItem.getWebImagePath4()!=null) {
            availableImagePath.add(replaceIPAddressInUrl(feedItem.getWebImagePath4(), imageSourceIp));
        }
        if (feedItem.getWebImagePath5()!=null) {
            availableImagePath.add(replaceIPAddressInUrl(feedItem.getWebImagePath5(), imageSourceIp));
        }
        if (feedItem.getWebImagePath6()!=null) {
            availableImagePath.add(replaceIPAddressInUrl(feedItem.getWebImagePath6(), imageSourceIp));
        }
        if (feedItem.getWebImagePath7()!=null) {
            availableImagePath.add(replaceIPAddressInUrl(feedItem.getWebImagePath7(), imageSourceIp));
        }
        if (feedItem.getWebImagePath8()!=null) {
            availableImagePath.add(replaceIPAddressInUrl(feedItem.getWebImagePath8(), imageSourceIp));
        }
        if (feedItem.getWebImagePath9()!=null) {
            availableImagePath.add(replaceIPAddressInUrl(feedItem.getWebImagePath9(), imageSourceIp));
        }
        
        return availableImagePath.toArray(new String[availableImagePath.size()]);
    }
    
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

    public String[] getRawImageUrls(FeedItem feedItem) {
        List<String> availableImagePath = new ArrayList<String>();
        if (feedItem.getWebImagePath1()!=null) {
            availableImagePath.add(feedItem.getWebImagePath1());
        }
        if (feedItem.getWebImagePath2()!=null) {
            availableImagePath.add(feedItem.getWebImagePath2());
        }
        if (feedItem.getWebImagePath3()!=null) {
            availableImagePath.add(feedItem.getWebImagePath3());
        }
        if (feedItem.getWebImagePath4()!=null) {
            availableImagePath.add(feedItem.getWebImagePath4());
        }
        if (feedItem.getWebImagePath5()!=null) {
            availableImagePath.add(feedItem.getWebImagePath5());
        }
        if (feedItem.getWebImagePath6()!=null) {
            availableImagePath.add(feedItem.getWebImagePath6());
        }
        if (feedItem.getWebImagePath7()!=null) {
            availableImagePath.add(feedItem.getWebImagePath7());
        }
        if (feedItem.getWebImagePath8()!=null) {
            availableImagePath.add(feedItem.getWebImagePath8());
        }
        if (feedItem.getWebImagePath9()!=null) {
            availableImagePath.add(feedItem.getWebImagePath9());
        }

        return availableImagePath.toArray(new String[availableImagePath.size()]);
    }
    
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
}
