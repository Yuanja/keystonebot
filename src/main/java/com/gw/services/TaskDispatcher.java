package com.gw.services;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
    private ISyncService feedSync;

    @Autowired
    private FeedReadynessService feedReadynessService;
    
    /**
     * Scheduler that uses Spring Batch to launch the feed sync job.
     */
    @Scheduled(cron="${cron.schedule}", zone="${cron.zone}")
    public void scheduledTaskCheck() {
        try {
            feedSync.sync(feedReadynessService.isFeedReady());
        } catch (Exception e) {
            logService.emailError(logger, "Error in scheduledTaskCheck", null, e);
        }
    }
}