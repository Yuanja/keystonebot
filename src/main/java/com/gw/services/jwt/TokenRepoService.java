package com.gw.services.jwt;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TokenRepoService {

    @Autowired
    JWTBasedAuthenticationService authService;
    
    //Keeping it simple, Static member
    private static Token tokenStatic;
    
    public void setToken(String token) {
        if (tokenStatic == null) {
            tokenStatic = new Token(token);
        }
    }
    
    public String getTokenString() throws Exception{
        if (tokenStatic == null) {
            tokenStatic = new Token(authService.getToken());
        }
  
        if (tokenStatic.expirationDate.before(new Date())) {
            tokenStatic = new Token(authService.getToken());
        }
        
        return tokenStatic.token;
    }
    
    public class Token {
        private String token;
        private Date expirationDate;
        
        public Token(String token) {
            this.token = token;
            
            Calendar expDate = new GregorianCalendar();
            expDate.add(Calendar.DATE, 4);
            
            this.expirationDate = expDate.getTime();
        }
    }
}
