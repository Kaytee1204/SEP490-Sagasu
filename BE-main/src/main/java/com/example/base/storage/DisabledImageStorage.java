package com.example.base.storage;

import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.storage.r2.enabled", havingValue = "false", matchIfMissing = true)
public class DisabledImageStorage implements ImageStorage {
    public void put(String key, byte[] bytes, String contentType) {
        throw new AppException(ErrorCode.IMAGE_STORAGE_NOT_CONFIGURED);
    }
    public void delete(String key) {}
    public String readUrl(String key, String fallbackUrl) { return fallbackUrl; }
}
