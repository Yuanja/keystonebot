package com.gw.services;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.gw.domain.FeedItem;

import freemarker.core.ParseException;
import freemarker.template.MalformedTemplateNameException;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateNotFoundException;
import freemarker.template.Version;

@Component
public class FreeMakerService {

    @Value("${ftl.template.file}")
    private String descriptionBodyTemplateFileName;
    
    public String generateFromTemplate(FeedItem feedItem) throws 
        TemplateNotFoundException, MalformedTemplateNameException, ParseException, IOException, TemplateException {
    
        Template itemBodyTemplate = getConfiguration().getTemplate(descriptionBodyTemplateFileName);
        StringWriter strWriter = new StringWriter();
        itemBodyTemplate.process(feedItem, strWriter);
        return strWriter.toString();
    }

    private freemarker.template.Configuration getConfiguration() throws IOException {
        freemarker.template.Configuration cfg = new freemarker.template.Configuration(new Version(2, 3, 26));
        cfg.setClassForTemplateLoading(this.getClass(), "/");
        cfg.setDefaultEncoding("UTF-8");
        cfg.setLocale(Locale.US);
        return cfg;
    }
}
