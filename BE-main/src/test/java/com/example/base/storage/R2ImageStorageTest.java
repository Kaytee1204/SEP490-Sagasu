package com.example.base.storage;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import java.net.URI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class R2ImageStorageTest {
    @Test
    void signsPrivateObjectLocallyWithoutSavingAnExpiringUrl() {
        String endpoint = "https://test-account.r2.cloudflarestorage.com";
        try (var signer = S3Presigner.builder().endpointOverride(URI.create(endpoint)).region(Region.of("auto"))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test-key", "test-secret")))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build()).build()) {
            var storage = new R2ImageStorage(mock(S3Client.class), signer,
                    new R2Properties(true, endpoint, "test-bucket", "test-key", "test-secret"));
            String url = storage.readUrl("posts/1/photo.png", null);
            assertThat(url).startsWith(endpoint + "/test-bucket/posts/1/photo.png?")
                    .contains("X-Amz-Signature=", "X-Amz-Expires=900").doesNotContain("test-secret");
        }
    }
}
