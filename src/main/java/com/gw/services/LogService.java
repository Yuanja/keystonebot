package com.gw.services;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

@Component
public class LogService {
    
    @Autowired 
    private EmailService emailer;
    
    public void emailError(Logger delegatedLogger, String subject, String body, Throwable e) {
        String logMsgs = getLogMsgs(subject, body, e);
        delegatedLogger.error(logMsgs);
        emailer.sendMessage(subject, logMsgs.toString());
    }
    
    public void emailInfo(Logger delegatedLogger, String subject, String body) {
        String logMsgs = getLogMsgs(subject, body, null);
        delegatedLogger.info(logMsgs);
        emailer.sendMessage(subject, logMsgs.toString());
    }
    
    private String getLogMsgs(String subject, String body, Throwable cause){
        StringBuffer logMsgs = new StringBuffer();
        if (!StringUtils.isEmpty(subject)){
            logMsgs.append(subject);
        }
        
        if (!StringUtils.isEmpty(body)) {
            logMsgs.append(" : \n").append(body);
        }
        
        if (cause != null) {
            logMsgs.append("---------------StackTrace---------\n");
            logMsgs.append(ExceptionUtils.getStackTrace(cause));
        }
        return logMsgs.toString();
    }
    
    public static String toJson(Object o) {
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        return gson.toJson(o);
    }
    
}
