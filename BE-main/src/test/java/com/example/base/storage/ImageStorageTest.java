package com.example.base.storage;

import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URL;
import java.time.Duration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImageStorageTest {

    @Mock S3Client client;
    @Mock S3Presigner presigner;
    @Mock JdbcTemplate jdbc;
    R2ImageStorage r2;

    @BeforeEach
    void setUp() {
        r2 = new R2ImageStorage(client, presigner, new R2Properties(true, "https://r2", "bucket", "id", "secret"), jdbc);
    }

    @Test
    void utc022_r2Put_uploadsToBucketWithContentType() {
        r2.put("posts/1.jpg", new byte[]{1, 2}, "image/jpeg");

        ArgumentCaptor<PutObjectRequest> cap = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client).putObject(cap.capture(), any(RequestBody.class));
        assertThat(cap.getValue().bucket()).isEqualTo("bucket");
        assertThat(cap.getValue().key()).isEqualTo("posts/1.jpg");
        assertThat(cap.getValue().contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void utc023_r2Put_sdkFailure_imageUploadFailed() {
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenThrow(SdkClientException.create("down"));
        assertThatThrownBy(() -> r2.put("k", new byte[]{1}, "image/png"))
                .isInstanceOf(AppException.class).extracting("errorCode").isEqualTo(ErrorCode.IMAGE_UPLOAD_FAILED);
    }

    @Test
    void utc024_r2Delete_unretainedKey_deleted() {
        when(jdbc.queryForObject(contains("report_asset"), eq(Boolean.class), eq("k"))).thenReturn(false);
        r2.delete("k");
        verify(client).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void utc025_r2Delete_keyRetainedByReport_skipped() {
        when(jdbc.queryForObject(contains("report_asset"), eq(Boolean.class), eq("k"))).thenReturn(true);
        r2.delete("k");
        verify(client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void utc026_r2ReadUrl_presigns15Minutes() throws Exception {
        PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(new URL("https://r2/bucket/k?sig"));
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);

        assertThat(r2.readUrl("k", "fallback")).isEqualTo("https://r2/bucket/k?sig");
        ArgumentCaptor<GetObjectPresignRequest> cap = ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(presigner).presignGetObject(cap.capture());
        assertThat(cap.getValue().signatureDuration()).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void utc027_r2ReadUrl_blankKey_returnsFallback() {
        assertThat(r2.readUrl(null, "legacy-url")).isEqualTo("legacy-url");
        assertThat(r2.readUrl(" ", "legacy-url")).isEqualTo("legacy-url");
        verifyNoInteractions(presigner);
    }

    @Test
    void utc028_disabledStorage_putRejected_readReturnsFallback() {
        DisabledImageStorage disabled = new DisabledImageStorage();
        assertThatThrownBy(() -> disabled.put("k", new byte[0], "image/png"))
                .isInstanceOf(AppException.class).extracting("errorCode").isEqualTo(ErrorCode.IMAGE_STORAGE_NOT_CONFIGURED);
        assertThatCode(() -> disabled.delete("k")).doesNotThrowAnyException();
        assertThat(disabled.readUrl("k", "fb")).isEqualTo("fb");
    }
}
