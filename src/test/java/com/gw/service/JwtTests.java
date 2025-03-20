package com.gw.service;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import com.gw.Config;
import com.gw.services.jwt.JWTBasedAuthenticationService;

@RunWith(SpringRunner.class)
@Import(Config.class)
@SpringBootTest
@ActiveProfiles(profiles = "jomashop")
public class JwtTests {

    
    @Autowired
    JWTBasedAuthenticationService service;
   
    @Test
    public void testService() throws Exception{
        String token = service.getToken();
        Assert.assertTrue(token != null);
    }
}
