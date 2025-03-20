package com.gw.services;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;

import jakarta.annotation.PostConstruct;

@Service
public class GoogleSheetScheduleService {
    
    private static final Logger logger = LogManager.getLogger(GoogleSheetScheduleService.class);
    
    private static final String APPLICATION_NAME = "GW Bot Scheduler";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> SCOPES = List.of(SheetsScopes.SPREADSHEETS_READONLY);
    
    protected @Value("${google.service.credential.json.path}") String credFilePath;
    
    @Value("${google.sheet.schedule.url}")
    private String scheduleSheetUrl;

    @Value("${google.sheet.schedule.sheet.name}")
    private String scheduleSheetName;
    
    // Cache for the schedule - Map<Hour, Map<DayOfWeek, TimezoneScheduleEntry>>
    private final Map<Integer, Map<DayOfWeek, TimezoneScheduleEntry>> scheduleCache = new ConcurrentHashMap<>();
    
    // Checksum to detect changes
    private String lastScheduleChecksum = "";
    
    @PostConstruct
    public void init() {
        try {
            refreshSchedule();
        } catch (Exception e) {
            logger.error("Failed to initialize schedule from Google Sheet", e);
        }
    }
    
    /**
     * Refreshes the schedule from Google Sheets
     */
//    @Scheduled(cron = "0 * * * * *") // Run every minute
    public void refreshSchedule() {
        try {
            //logger.debug("Checking for schedule updates from Google Sheet");
            Map<Integer, Map<DayOfWeek, TimezoneScheduleEntry>> newSchedule = fetchScheduleFromSheet();
            
            String newChecksum = calculateChecksum(newSchedule);
            if (!newChecksum.equals(lastScheduleChecksum)) {
                logger.info("Schedule has changed, updating cached schedule");
                // Clear and update the cache
                scheduleCache.clear();
                scheduleCache.putAll(newSchedule);
                lastScheduleChecksum = newChecksum;
            } else {
                //logger.debug("No schedule changes detected");
            }
        } catch (Exception e) {
            logger.error("Failed to refresh schedule from Google Sheet", e);
            throw new RuntimeException("Failed to refresh schedule from Google Sheet", e);
        }
    }
    
    /**
     * Gets the execution frequency for the current time
     * @return Number of times to execute per hour
     */
    public int getCurrentFrequency() {
        // Get current time in UTC
        ZonedDateTime utcNow = ZonedDateTime.now(ZoneId.of("UTC"));
        //logger.debug("Current UTC time: {}", utcNow);
        
        // For each schedule entry, convert UTC time to the entry's timezone
        // and check if it matches
        for (Map.Entry<Integer, Map<DayOfWeek, TimezoneScheduleEntry>> hourEntry : scheduleCache.entrySet()) {
            Integer scheduleHour = hourEntry.getKey();
            Map<DayOfWeek, TimezoneScheduleEntry> dayMap = hourEntry.getValue();
            
            for (Map.Entry<DayOfWeek, TimezoneScheduleEntry> dayEntry : dayMap.entrySet()) {
                DayOfWeek scheduleDay = dayEntry.getKey();
                TimezoneScheduleEntry entry = dayEntry.getValue();
                
                // Convert the current UTC time to the schedule's timezone
                ZonedDateTime localTime = utcNow.withZoneSameInstant(
                    ZoneOffset.ofHours(entry.getUtcOffset()));
                
                // logger.debug("Checking schedule entry - UTC time: {}, Local time: {}, Schedule day: {}, " +
                //            "Schedule hour: {}, UTC offset: {}", 
                //            utcNow, localTime, scheduleDay, scheduleHour, entry.getUtcOffset());
                
                // Check if this entry matches the local time
                if (localTime.getDayOfWeek() == scheduleDay && 
                    localTime.getHour() == scheduleHour) {
                    
                    logger.debug("Found matching schedule: day={}, hour={}, frequency={}, " +
                              "utcOffset={}, localTime={}", 
                              scheduleDay, scheduleHour, entry.getFrequency(), 
                              entry.getUtcOffset(), localTime);
                    return entry.getFrequency();
                }
            }
        }
        
        logger.debug("No matching schedule found for current time");
        return 0;
    }
    
    /**
     * Fetches the schedule from Google Sheet
     * @return The schedule map structured by hour and day
     * @throws IOException If reading fails
     * @throws GeneralSecurityException If authentication fails
     */
    private Map<Integer, Map<DayOfWeek, TimezoneScheduleEntry>> fetchScheduleFromSheet() 
            throws IOException, GeneralSecurityException {
        
        // Build a service to interact with the Google Sheets API
        HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        
        // Load credentials from file
        InputStream in = GoogleSheetScheduleService.class.getResourceAsStream(credFilePath);
        if (in == null) {
            in = new FileInputStream(credFilePath);
        }
        GoogleCredentials credentials = GoogleCredentials.fromStream(in).createScoped(SCOPES);
        
        // Build the Sheets service
        Sheets service = new Sheets.Builder(httpTransport, JSON_FACTORY, new HttpCredentialsAdapter(credentials))
                .setApplicationName(APPLICATION_NAME)
                .build();
        
        // Extract the sheet ID from the URL
        String spreadsheetId = extractSheetId(scheduleSheetUrl);
        
        // Get values from the sheet (assuming first sheet)
        ValueRange response = service.spreadsheets().values()
                .get(spreadsheetId, scheduleSheetName)
                .execute();
        
        List<List<Object>> values = response.getValues();
        return parseScheduleValues(values);
    }
    
    /**
     * Parses the schedule values from the sheet with timezone support
     * @param values The raw values from the sheet
     * @return The parsed schedule with timezone entries
     */
    private Map<Integer, Map<DayOfWeek, TimezoneScheduleEntry>> parseScheduleValues(List<List<Object>> values) {
        Map<Integer, Map<DayOfWeek, TimezoneScheduleEntry>> schedule = new HashMap<>();
        
        if (values == null || values.isEmpty() || values.get(0).size() < 9) {
            logger.warn("Schedule sheet format is invalid");
            return schedule;
        }
        
        // First row contains day names (starting at column C - index 2)
        List<Object> headerRow = values.get(0);
        
        // Map column indices to DayOfWeek enum (days start at column C - index 2)
        Map<Integer, DayOfWeek> columnToDayMap = new HashMap<>();
        for (int i = 2; i < headerRow.size() && i <= 8; i++) {
            String dayName = headerRow.get(i).toString().trim();
            DayOfWeek day = mapDayNameToDayOfWeek(dayName);
            if (day != null) {
                columnToDayMap.put(i, day);
            }
        }
        
        // Process each row (after header row)
        for (int rowIndex = 1; rowIndex < values.size(); rowIndex++) {
            List<Object> row = values.get(rowIndex);
            if (row.size() <= 2) continue; // Need at least hour and timezone columns
            
            // First column is the hour
            String hourStr = row.get(0).toString().trim();
            Integer hour = parseHour(hourStr);
            if (hour == null) continue;
            
            // Second column is the UTC offset
            String utcOffsetStr = row.get(1).toString().trim();
            Integer utcOffset = parseUtcOffset(utcOffsetStr);
            if (utcOffset == null) {
                logger.warn("Invalid UTC offset value: {}", utcOffsetStr);
                continue;
            }
            
            // Process each day column (starting at column C - index 2)
            for (int colIndex = 2; colIndex < row.size() && colIndex <= 8; colIndex++) {
                if (!columnToDayMap.containsKey(colIndex)) continue;
                
                DayOfWeek day = columnToDayMap.get(colIndex);
                String freqStr = row.get(colIndex).toString().trim();
                if (freqStr.isEmpty()) freqStr = "0";
                
                try {
                    int frequency = Integer.parseInt(freqStr);
                    
                    // Initialize nested map structures if needed
                    if (!schedule.containsKey(hour)) {
                        schedule.put(hour, new HashMap<>());
                    }
                    
                    // Store the schedule entry with timezone information
                    schedule.get(hour).put(day, new TimezoneScheduleEntry(frequency, utcOffset));
                    
                } catch (NumberFormatException e) {
                    logger.warn("Invalid frequency value for hour {}, day {}: {}", hour, day, freqStr);
                }
            }
        }
        
        return schedule;
    }
    
    /**
     * Parses UTC offset from string
     * @param utcOffsetStr UTC offset string (like "-8")
     * @return UTC offset as integer
     */
    private Integer parseUtcOffset(String utcOffsetStr) {
        try {
            return Integer.parseInt(utcOffsetStr);
        } catch (NumberFormatException e) {
            // Try handling patterns like "UTC-8" or "GMT+5"
            if (utcOffsetStr.toUpperCase().contains("UTC") || utcOffsetStr.toUpperCase().contains("GMT")) {
                String numericPart = utcOffsetStr.replaceAll("[^-+0-9]", "");
                if (!numericPart.isEmpty()) {
                    return Integer.parseInt(numericPart);
                }
            }
            return null;
        }
    }
    
    /**
     * Maps day name from sheet to DayOfWeek enum
     * @param dayName Day name from sheet
     * @return Corresponding DayOfWeek
     */
    private DayOfWeek mapDayNameToDayOfWeek(String dayName) {
        return switch (dayName.toLowerCase()) {
            case "monday" -> DayOfWeek.MONDAY;
            case "tuesday" -> DayOfWeek.TUESDAY;
            case "wednesday" -> DayOfWeek.WEDNESDAY;
            case "thursday" -> DayOfWeek.THURSDAY;
            case "friday" -> DayOfWeek.FRIDAY;
            case "saturday" -> DayOfWeek.SATURDAY;
            case "sunday" -> DayOfWeek.SUNDAY;
            default -> null;
        };
    }
    
    /**
     * Parses hour from various formats
     * @param hourStr Hour string from sheet
     * @return Hour as Integer (0-23)
     */
    private Integer parseHour(String hourStr) {
        try {
            // Handle various formats
            if (hourStr.contains(":")) {
                // Format like "6:00 AM"
                String[] parts = hourStr.split(":");
                int hour = Integer.parseInt(parts[0]);
                if (hourStr.toLowerCase().contains("pm") && hour < 12) {
                    hour += 12;
                } else if (hourStr.toLowerCase().contains("am") && hour == 12) {
                    hour = 0;
                }
                return hour;
            } else {
                // Simple number format
                return Integer.parseInt(hourStr);
            }
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    /**
     * Extracts the sheet ID from a Google Sheets URL
     * @param url Google Sheets URL
     * @return Sheet ID
     */
    private String extractSheetId(String url) {
        int start = url.indexOf("/d/") + 3;
        int end = url.indexOf("/", start);
        if (end == -1) {
            end = url.indexOf("?", start);
        }
        if (end == -1) {
            end = url.indexOf("#", start);
        }
        if (end == -1) {
            end = url.length();
        }
        
        return url.substring(start, end);
    }
    
    /**
     * Calculates a checksum for the schedule to detect changes
     * @param schedule The schedule map
     * @return A string checksum
     */
    private String calculateChecksum(Map<Integer, Map<DayOfWeek, TimezoneScheduleEntry>> schedule) {
        StringBuilder sb = new StringBuilder();
        
        for (int hour = 0; hour < 24; hour++) {
            if (!schedule.containsKey(hour)) continue;
            
            Map<DayOfWeek, TimezoneScheduleEntry> dayMap = schedule.get(hour);
            for (DayOfWeek day : DayOfWeek.values()) {
                if (!dayMap.containsKey(day)) continue;
                
                TimezoneScheduleEntry entry = dayMap.get(day);
                sb.append(hour).append(":").append(day).append(":")
                  .append(entry.getFrequency()).append(":")
                  .append(entry.getUtcOffset()).append(";");
            }
        }
        
        return sb.toString();
    }
    
    /**
     * Class to hold schedule entries with timezone information
     */
    private static class TimezoneScheduleEntry {
        private final int frequency;
        private final int utcOffset;
        
        public TimezoneScheduleEntry(int frequency, int utcOffset) {
            this.frequency = frequency;
            this.utcOffset = utcOffset;
        }
        
        public int getFrequency() {
            return frequency;
        }
        
        public int getUtcOffset() {
            return utcOffset;
        }
    }
} 