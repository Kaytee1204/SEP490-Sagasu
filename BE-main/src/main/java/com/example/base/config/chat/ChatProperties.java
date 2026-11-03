package com.example.base.config.chat;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

@Component @ConfigurationProperties("app.chat") @Data @Validated
public class ChatProperties {
    @Min(1) private int messageMaxLength = 4000;
    @Min(1) @Max(32767) private int attachmentMaxCount = 5;
    @Min(1) private long imageMaxBytes = 5 * 1024 * 1024;
    @Min(1) private long pdfMaxBytes = 10 * 1024 * 1024;
    @Min(1) private long totalMaxBytes = 25 * 1024 * 1024;
}
