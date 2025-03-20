package com.gw.service;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import com.gw.Config;
import com.gw.services.chronos24.Chronos24Service;
import com.gw.services.chronos24.ChronosFeedService;

@RunWith(SpringRunner.class)
@Import(Config.class)
@SpringBootTest
@ActiveProfiles(profiles = "chronos24-prod")
public class Chronos24 {

    
    @Autowired
    Chronos24Service service;
    
    @Autowired
    ChronosFeedService chronosFeedService; 
   
    @Test
    public void testService() throws Exception{
        //service.verifyChronos24AgainstFeed(chronosFeedService);
    }
    
    
}
