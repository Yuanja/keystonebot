package com.gw.services.jwt;

import org.apache.http.Header;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

@Component
public class JWTBasedAuthenticationService {
    final static Logger logger = LogManager.getLogger(JWTBasedAuthenticationService.class);
    
    @Value("${jwt.token_request_url}")
    private String tokenRequestUrl;
    
    @Value("${jwt.user}")
    private String user;
    
    @Value("${jwt.password}")
    private String password;
    
    public String getToken() throws Exception {
        CloseableHttpClient httpclient = HttpClients.custom().build();
        try {
            User userO = new User(user, password);
            
            StringEntity sessionBody =
                    new StringEntity(getJsonString(userO), 
                            ContentType.create("application/json", "UTF-8"));
            
            HttpPost httpPost = new HttpPost(tokenRequestUrl);
            httpPost.setHeader("User-Agent", "GruenbergAPI");  
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setEntity(sessionBody);
            
            logger.info("Executing request " + httpPost.toString());
            for (Header header : httpPost.getAllHeaders()) {
                logger.info("Header: " 
                        + header.getName() + "=" + header.getValue() );
            }
            
            CloseableHttpResponse response = httpclient.execute(httpPost);

            try {
                if (response.getStatusLine().getStatusCode() == 200) {
                    logger.info(EntityUtils.toString(response.getEntity()));
                    Header[] authHeaders = response.getHeaders("Authorization");
                    if (authHeaders == null || authHeaders.length == 0) {
                        throw new Exception ("Auth Header not found!");
                    }
                    
                    return authHeaders[0].getValue();
                    
                } else {
                    throw new Exception ("Error getting token: Response is bad: "+ response.toString());
                }
            } finally {
                response.close();
            }
        } finally {
            httpclient.close();
        }
    }
    
    private String getJsonString(Object o) {
        GsonBuilder builder = new GsonBuilder();
        builder.serializeNulls();
        Gson gson = builder.create();
        String json = gson.toJson(o);
        
        return json;
    }
    
    
    class User {
        InnerUser user;
        
        User(String email, String password) {
            user = new InnerUser(email, password);
        }
        
        class InnerUser {
            InnerUser(String email, String password){
                this.email = email;
                this.password = password;
            }
            String email;
            String password;
        }
    }
    
    class SessionResponse {
        String email, role, first_name, last_name, phone, last_sign_in, confirmed_at;
        Vendor vendor;
    }
    
    class Vendor {
        String name;
        boolean confirmed;
    }
}
    
