package com.gw.service;

import com.gw.Config;
import com.gw.domain.FeedItem;
import com.gw.services.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.List;

@RunWith(SpringRunner.class)
@Import(Config.class)
@SpringBootTest
@ActiveProfiles(profiles = "keystone-dev")
public class KeystoneFeedPaginationTest {
	private static Logger logger = LogManager.getLogger(KeystoneFeedPaginationTest.class);


    @Autowired
    private IFeedService keyStoneFeedService;

    @Test
    public void testPagination() throws Exception {
        List<FeedItem> allFeedItems = keyStoneFeedService.getItemsFromFeed();



        FileWriter fileWriter = new FileWriter("KeyStone.csv");
        PrintWriter printWriter = new PrintWriter(fileWriter);
       
        for (FeedItem aItem : allFeedItems) {
            printWriter.printf("%s,%s,%s", aItem.getWebTagNumber(), aItem.getWebStatus(), aItem.getWebDescriptionShort());
            printWriter.println();
        }
        printWriter.close();
    }
}


