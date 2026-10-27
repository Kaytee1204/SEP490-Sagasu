package com.example.base.config;

import com.example.base.storage.R2Properties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(R2Properties.class)
@ConditionalOnProperty(name = "app.storage.r2.enabled", havingValue = "true")
public class R2Config {
    private StaticCredentialsProvider credentials(R2Properties p) {
        Assert.hasText(p.endpoint(), "R2_ENDPOINT is required");
        Assert.isTrue(p.endpoint().startsWith("https://"), "R2_ENDPOINT must use HTTPS");
        Assert.hasText(p.bucket(), "R2_BUCKET is required");
        Assert.hasText(p.accessKeyId(), "R2_ACCESS_KEY_ID is required");
        Assert.hasText(p.secretAccessKey(), "R2_SECRET_ACCESS_KEY is required");
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(p.accessKeyId(), p.secretAccessKey()));
    }

    @Bean(destroyMethod = "close")
    S3Client r2Client(R2Properties p) {
        return S3Client.builder().endpointOverride(URI.create(p.endpoint()))
                .credentialsProvider(credentials(p)).region(Region.of("auto"))
                .httpClientBuilder(UrlConnectionHttpClient.builder().connectionTimeout(Duration.ofSeconds(5))
                        .socketTimeout(Duration.ofSeconds(30)))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(Duration.ofSeconds(45)).build())
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true)
                        .chunkedEncodingEnabled(false).build())
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED).build();
    }

    @Bean(destroyMethod = "close")
    S3Presigner r2Presigner(R2Properties p) {
        return S3Presigner.builder().endpointOverride(URI.create(p.endpoint()))
                .credentialsProvider(credentials(p)).region(Region.of("auto"))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build()).build();
    }
}
