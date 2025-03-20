package com.gw;
import java.util.Map;
import javax.naming.Context;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.Import;

import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.gw.services.chronos24.Chronos24Service;
import com.gw.services.chronos24.ChronosFeedService;

/**
 * Main class for running the chronos feed and shopify item services.
 * 
 * @author jyuan
 */
@Import(Config.class)
public class Chronos24AwsLambda implements CommandLineRunner, RequestHandler<Map<String, Object>, String> {

    //final static Logger logger = LogManager.getLogger(Chronos24AwsLambda.class);

    @Autowired
    Chronos24Service chronosService;
    
    @Autowired
    ChronosFeedService chronosFeedService;
    
    public void run(String... args) throws Exception {
        //chronosService.verifyChronos24AgainstFeed(chronosFeedService);
    }

    public String handleRequest(Map<String, Object> arg, Context context) {
        //LambdaLogger logger = context.getLogger();
        //logger.log("started!");
        
        SpringApplication app = new SpringApplication(Chronos24AwsLambda.class);
        app.setAdditionalProfiles("chronos24-prod");
        app.run(new String());
        
        //SpringApplication.run(Chronos24AwsLambda.class, new String());

        return "Done";
    }

	@Override
	public String handleRequest(Map<String, Object> input, com.amazonaws.services.lambda.runtime.Context context) {
		// TODO Auto-generated method stub
		return null;
	}
}
