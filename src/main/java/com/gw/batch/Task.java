package com.gw.batch;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;


@Configuration
@EnableBatchProcessing
public class Task {
    

    @Autowired
    private FeedSyncTasklet feedSyncTasklet;

    @Bean
    public Job feedSyncJob(JobRepository jobRepository, Step feedSyncStep) {
        return new JobBuilder("feedSyncJob", jobRepository)
                .start(feedSyncStep)
                .build();
    }

    @Bean
    public Step feedSyncStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("feedSyncStep", jobRepository)
                .tasklet(feedSyncTasklet, transactionManager)
                .build();
    }
} 