package com.gw.services;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for fetching and mapping Google Sheet data to FeedItem objects
 */
@Component
public class GoogleSheetService {
    
    private static final Logger logger = LogManager.getLogger(GoogleSheetService.class);
    
    private static final String APPLICATION_NAME = "WhatsApp Poster";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> SCOPES = Collections.singletonList(SheetsScopes.SPREADSHEETS_READONLY);
   
    protected @Value("${google.service.credential.json.path}") String credFilePath;
    
    /**
     * Extracts the sheet ID from a Google Sheets URL
     * @param url Google Sheets URL
     * @return Sheet ID
     */
    private String extractSheetId(String url) {
        // Pattern to extract sheet id from URLs like:
        // https://docs.google.com/spreadsheets/d/1abc123def456/edit#gid=0
        Pattern pattern = Pattern.compile("spreadsheets/d/([a-zA-Z0-9-_]+)");
        Matcher matcher = pattern.matcher(url);
        
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        throw new IllegalArgumentException("Invalid Google Sheets URL: " + url);
    }
    
    /**
     * Fetches data from a Google Sheet and maps it to a list of FeedItem objects
     * @param sheetUrl The URL of the Google Sheet
     * @return List of FeedItem objects
     * @throws IOException If an I/O error occurs
     * @throws GeneralSecurityException If a security error occurs
     */
    public List<List<Object>> fetchAndMapSheetData(String sheetUrl) throws IOException, GeneralSecurityException {
        // Build a service to interact with the Google Sheets API
        HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        
        // Load credentials from file
        InputStream in = GoogleSheetService.class.getResourceAsStream(credFilePath);
        if (in == null) {
            in = new FileInputStream(credFilePath);
        }
        GoogleCredentials credentials = GoogleCredentials.fromStream(in).createScoped(SCOPES);
        
        // Build the Sheets service
        Sheets service = new Sheets.Builder(httpTransport, JSON_FACTORY, new HttpCredentialsAdapter(credentials))
                .setApplicationName(APPLICATION_NAME)
                .build();
        
        // Extract the sheet ID from the URL
        String spreadsheetId = extractSheetId(sheetUrl);
        
        // Get the spreadsheet to find available sheets
        Spreadsheet spreadsheet = service.spreadsheets().get(spreadsheetId).execute();
        List<Sheet> sheets = spreadsheet.getSheets();
        
        if (sheets == null || sheets.isEmpty()) {
            throw new IOException("No sheets found in the spreadsheet");
        }
        
        // Use the first sheet by default
        String range = sheets.get(0).getProperties().getTitle();
        
        // Get all values from the sheet
        ValueRange response = service.spreadsheets().values()
                .get(spreadsheetId, range)
                .execute();
        
        List<List<Object>> values = response.getValues();
        
        if (values == null || values.isEmpty()) {
            logger.warn("No data found in the sheet");
            return Collections.emptyList();
        }
        
        return values;
    }
    
    
} 