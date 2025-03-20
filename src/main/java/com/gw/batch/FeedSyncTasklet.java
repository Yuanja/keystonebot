package com.gw.batch;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.gw.services.FeedReadynessService;
import com.gw.services.ISyncService;
import com.gw.services.LogService;

@Component
public class FeedSyncTasklet implements Tasklet {
    
    private static final Logger logger = LogManager.getLogger(FeedSyncTasklet.class);
    
    @Autowired
    private LogService logService;
    
    @Autowired
    private ISyncService syncService;
    
    @Autowired
    private FeedReadynessService feedReadynessService;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        logger.info("Feed Processing started!");
        try {
            if (feedReadynessService.isFeedReady()) {
                syncService.sync(true);
            } else {
                logger.info("Feed isn't ready skipping.");
                syncService.sync(false);
            }
        } catch (Exception e) {
            logService.emailError(logger, "General Error when processing feed", null, e);
            throw e;
        }
        logger.info("Feed Processing Ended!");
        return RepeatStatus.FINISHED;
    }
} 