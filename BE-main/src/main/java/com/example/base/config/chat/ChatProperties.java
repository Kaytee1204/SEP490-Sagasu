package com.example.base.config.chat;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component @ConfigurationProperties("app.chat") @Data
public class ChatProperties {
    private int messageMaxLength = 4000;
    private int attachmentMaxCount = 5;
    private long imageMaxBytes = 5 * 1024 * 1024;
    private long pdfMaxBytes = 10 * 1024 * 1024;
    private long totalMaxBytes = 25 * 1024 * 1024;
}
