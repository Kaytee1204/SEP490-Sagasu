package com.example.base.service.report;

import com.example.base.storage.ImageStorage;
import com.example.base.storage.ImageValidator;
import com.example.base.storage.ImageValidator.ValidatedImage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkflowImagesTest {

    @Mock ImageValidator validator;
    @Mock ImageStorage storage;
    @InjectMocks WorkflowImages images;

    private static ValidatedImage img(String hash, String ext) {
        return new ValidatedImage(new byte[]{1, 2, 3}, "image/" + ext, ext, 10, 10, hash);
    }

    private static MultipartFile file(String name) {
        return new MockMultipartFile("files", name, "image/jpeg", new byte[]{1});
    }

    @Test
    void utc027_prepare_stripsPathsAndHashesDeterministically() {
        List<MultipartFile> files = List.of(file("C:\\a\\1.jpg"), file("2.png"));
        when(validator.validate(files)).thenReturn(List.of(img("h1", "jpg"), img("h2", "png")));

        WorkflowImages.Prepared p = images.prepare("POST:10:x", files);

        assertThat(p.names()).containsExactly("1.jpg", "2.png");
        assertThat(p.hash()).hasSize(64).matches("[0-9a-f]+");
    }

    @Test
    void utc028_prepare_sameInputTwice_sameHash() {
        List<MultipartFile> files = List.of(file("1.jpg"));
        when(validator.validate(files)).thenReturn(List.of(img("h1", "jpg")));

        assertThat(images.prepare("P", files).hash()).isEqualTo(images.prepare("P", files).hash());
        assertThat(images.prepare("P", files).hash()).isNotEqualTo(images.prepare("Q", files).hash());
    }

    @Test
    void utc029_prepare_nullFilename_defaultsToImage() {
        MultipartFile nullName = mock(MultipartFile.class);
        when(nullName.getOriginalFilename()).thenReturn(null);
        List<MultipartFile> files2 = List.of(nullName);
        when(validator.validate(files2)).thenReturn(List.of(img("h1", "jpg")));

        assertThat(images.prepare("P", files2).names()).containsExactly("image");
    }

    @Test
    void utc030_prepare_longFilename_truncatedTo255() {
        List<MultipartFile> files = List.of(file("a".repeat(300) + ".jpg"));
        when(validator.validate(files)).thenReturn(List.of(img("h1", "jpg")));

        assertThat(images.prepare("P", files).names().get(0)).hasSize(255);
    }

    @Test
    void utc031_upload_putsEachImageWithOrderedKeys() {
        var prepared = new WorkflowImages.Prepared(List.of(img("h1", "jpg"), img("h2", "png")), List.of("1.jpg", "2.png"), "hash");

        List<WorkflowImages.Image> out = images.upload("reports/7", prepared);

        assertThat(out).hasSize(2);
        assertThat(out.get(0).key()).startsWith("reports/7/").endsWith(".jpg");
        assertThat(out.get(1).key()).endsWith(".png");
        assertThat(out).extracting(WorkflowImages.Image::order).containsExactly(0, 1);
        verify(storage, times(2)).put(anyString(), any(), anyString());
    }

    @Test
    void utc032_upload_secondPutFails_firstKeyDeletedAndRethrown() {
        var prepared = new WorkflowImages.Prepared(List.of(img("h1", "jpg"), img("h2", "png")), List.of("1.jpg", "2.png"), "hash");
        doNothing().doThrow(new RuntimeException("R2 down")).when(storage).put(anyString(), any(), anyString());

        assertThatThrownBy(() -> images.upload("reports/7", prepared)).hasMessage("R2 down");
        verify(storage, times(2)).delete(startsWith("reports/7/"));
    }

    @Test
    void utc033_cleanup_firstDeleteThrows_secondStillDeleted() {
        var a = new WorkflowImages.Image("k1", "1.jpg", "image/jpeg", 1, "h", 0);
        var b = new WorkflowImages.Image("k2", "2.jpg", "image/jpeg", 1, "h", 1);
        doThrow(new RuntimeException("x")).when(storage).delete("k1");

        assertThatCode(() -> images.cleanup(List.of(a, b))).doesNotThrowAnyException();
        verify(storage).delete("k2");
    }

    @Test
    void utc034_read_delegatesToStorageUrl() {
        when(storage.readUrl("k", null)).thenReturn("https://cdn/k");
        assertThat(images.read("k")).isEqualTo("https://cdn/k");
    }
}
