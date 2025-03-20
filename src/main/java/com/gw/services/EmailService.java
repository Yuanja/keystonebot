package com.gw.services;

import jakarta.mail.internet.MimeMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class EmailService {
    
    final static Logger logger = LogManager.getLogger(EmailService.class);
    
    @Autowired
    private Environment environment;
    
    @Autowired
    public JavaMailSender emailSender;
    
    @Value("${email.to}")
    private String[] toList;
    
    @Value("${email.from}")
    private String fromEmail;
    
    @Value("${email.alert.enabled}")
    private boolean emailEnabled;

    public void sendMessage(String subject, String text) {
        sendMessage(toList, subject, text);
    }
    public void sendMessage(String[] toList, String subject, String text)   {
        if (emailEnabled) {
        	String profileString = Arrays.toString(environment.getActiveProfiles());
            try {
                MimeMessage message = emailSender.createMimeMessage();
                
                MimeMessageHelper helper = new MimeMessageHelper(message, true);
                
                helper.setFrom(fromEmail);
                helper.setReplyTo(fromEmail);
                helper.setTo(toList);
                helper.setSubject(profileString +": "+subject);
                helper.setText(text);
                     
                emailSender.send(message);
            } catch (Exception e) {
                logger.error(e);
            }
        }
    }
}
