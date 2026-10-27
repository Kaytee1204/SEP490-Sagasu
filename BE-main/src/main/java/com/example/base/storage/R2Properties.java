package com.example.base.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.storage.r2")
public record R2Properties(boolean enabled, String endpoint, String bucket,
        String accessKeyId, String secretAccessKey) {
}
