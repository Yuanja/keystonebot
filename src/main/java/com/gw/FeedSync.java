package com.gw;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main class for running the GW feed and shopify item services.
 * 
 * @author jyuan
 */
@SpringBootApplication
@Import(Config.class)
@EnableScheduling
public class FeedSync implements CommandLineRunner {

    final static Logger logger = LogManager.getLogger(FeedSync.class);
    
    public static void main(String[] args) throws Exception {
        SpringApplication.run(FeedSync.class, args);
    }
    
    public void run(String... args) throws Exception {
        logger.info("FeedSync started.");
    }
}
