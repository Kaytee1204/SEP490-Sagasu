package com.example.base.storage;

public interface ImageStorage {
    void put(String key, byte[] bytes, String contentType);
    void delete(String key);
    String readUrl(String key, String fallbackUrl);
}
