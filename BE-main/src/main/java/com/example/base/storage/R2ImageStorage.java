package com.example.base.storage;

import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import java.time.Duration;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.storage.r2.enabled", havingValue = "true")
public class R2ImageStorage implements ImageStorage {
    private final S3Client client;
    private final S3Presigner presigner;
    private final R2Properties properties;

    @Override
    public void put(String key, byte[] bytes, String contentType) {
        try {
            client.putObject(
                    PutObjectRequest.builder().bucket(properties.bucket()).key(key).contentType(contentType).build(),
                    RequestBody.fromBytes(bytes));
        } catch (SdkException ex) {
            throw new AppException(ErrorCode.IMAGE_UPLOAD_FAILED);
        }
    }

    @Override
    public void delete(String key) {
        client.deleteObject(DeleteObjectRequest.builder().bucket(properties.bucket()).key(key).build());
    }

    @Override
    public String readUrl(String key, String fallbackUrl) {
        if (key == null || key.isBlank()) {
            return fallbackUrl;
        }
        return presigner.presignGetObject(GetObjectPresignRequest.builder().signatureDuration(Duration.ofMinutes(15))
                .getObjectRequest(GetObjectRequest.builder().bucket(properties.bucket()).key(key).build()).build())
                .url().toString();
    }
}
