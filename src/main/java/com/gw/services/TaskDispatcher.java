package com.gw.services;

import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * @author jyuan
 *
 */
@Component
public class TaskDispatcher {
    
    private static final Logger logger = LogManager.getLogger(TaskDispatcher.class);

    @Autowired
    private LogService logService;
    
    @Autowired
    private GoogleSheetScheduleService scheduleService;
    
    @Autowired
    private JobLauncher jobLauncher;
    
    @Autowired
    private Job feedSyncJob;
    
    // Track executions within the current hour
    private AtomicInteger executionsThisHour = new AtomicInteger(0);
    private int currentHour = -1;
    
    /**
     * Scheduler that uses Spring Batch to launch the feed sync job.
     */
    @Scheduled(cron="${cron.schedule}", zone="${cron.zone}")
    public void scheduledTaskCheck() {
        try {
            //logger.debug("Refreshing schedule before executing tasks");
            scheduleService.refreshSchedule();
            LocalDateTime now = LocalDateTime.now();
            int hour = now.getHour();
            
            // Reset counter if we've moved to a new hour
            if (hour != currentHour) {
                logger.info("Hour changed from {} to {}, resetting execution counter", currentHour, hour);
                executionsThisHour.set(0);
                currentHour = hour;
            }
            
            // Get the frequency for the current time
            int frequency = scheduleService.getCurrentFrequency();
            
            if (frequency <= 0) {
                logger.debug("No executions scheduled for current time slot");
                return;
            }
            
            // Calculate time spacing for even distribution
            int minutesPerExecution = 60 / frequency - 1; // minus 1 to deal with frequency == 1 case.
            int currentMinute = now.getMinute();
            
            // Check if this minute is a scheduled execution time
            if (currentMinute % minutesPerExecution == 0 && executionsThisHour.get() < frequency) {
                logger.info("Launching job based on schedule (execution {} of {} this hour)", 
                        executionsThisHour.incrementAndGet(), frequency);
                
                // Create job parameters with timestamp
                JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("timestamp", System.currentTimeMillis()) //This is to allow only one execution at a time.
                    .toJobParameters();
                
                if (minutesPerExecution > 1) {
                    // Add a random delay in seconds between 0 and minutesPerExecution - 1, capped at 3 min.
                    int randomDelay = ThreadLocalRandom.current().nextInt(
                        Math.min(3 * 60, (minutesPerExecution-1) * 60) 
                    );
                    logger.info("Adding a random delay of {} seconds before job execution", randomDelay);
                    Thread.sleep(randomDelay * 1000);
                }
                // Launch the job
                jobLauncher.run(feedSyncJob, jobParameters);
            
            } else {
                logger.info("Skipping. Current minute: {}, minutesPerExecution: {}, Current execution count: {}, executionsThisHour: {}", 
                currentMinute, minutesPerExecution, executionsThisHour.get(), executionsThisHour.get()   );
            }
            
        } catch (Exception e) {
            logService.emailError(logger, "Error in scheduledTaskCheck", null, e);
        }
    }
}
