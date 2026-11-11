package com.example.base.storage;

import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import com.example.base.storage.ImageValidator.ValidatedImage;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class ImageValidatorTest {

    private final ImageValidator validator = new ImageValidator();

    private byte[] createImageBytes(String format, int width, int height, int imageType) throws Exception {
        BufferedImage img = new BufferedImage(width, height, imageType);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    private MockMultipartFile validJpeg(String name, int width, int height) throws Exception {
        return new MockMultipartFile("files", name, "image/jpeg",
                createImageBytes("jpeg", width, height, BufferedImage.TYPE_INT_RGB));
    }

    private MockMultipartFile validPng(String name, int width, int height) throws Exception {
        return new MockMultipartFile("files", name, "image/png",
                createImageBytes("png", width, height, BufferedImage.TYPE_INT_RGB));
    }

    @Test
    void utc040_validate_nullFiles_returnsEmptyList() {
        assertThat(validator.validate(null)).isEmpty();
    }

    @Test
    void utc041_validate_validJpeg1024x768_returnsValidatedImage() throws Exception {
        MockMultipartFile file = validJpeg("photo.jpg", 1024, 768);

        List<ValidatedImage> res = validator.validate(List.of(file));

        assertThat(res).hasSize(1);
        ValidatedImage img = res.getFirst();
        assertThat(img.mime()).isEqualTo("image/jpeg");
        assertThat(img.extension()).isEqualTo("jpg");
        assertThat(img.width()).isEqualTo(1024);
        assertThat(img.height()).isEqualTo(768);
        assertThat(img.hash()).hasSize(64);
    }

    @Test
    void utc042_validate_validPng_returnsPngMetadata() throws Exception {
        MockMultipartFile file = validPng("photo.png", 100, 100);

        List<ValidatedImage> res = validator.validate(List.of(file));

        assertThat(res).hasSize(1);
        assertThat(res.getFirst().mime()).isEqualTo("image/png");
        assertThat(res.getFirst().extension()).isEqualTo("png");
    }

    @Test
    void utc043_validate_sixFiles_throwsImageCountExceeded() throws Exception {
        MockMultipartFile one = validPng("p.png", 10, 10);
        List<MultipartFile> six = List.of(one, one, one, one, one, one);

        assertThatThrownBy(() -> validator.validate(six))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.IMAGE_COUNT_EXCEEDED);
    }

    @Test
    void utc044_validate_fiveValidFiles_returnsFive() throws Exception {
        MockMultipartFile one = validPng("p.png", 10, 10);
        List<MultipartFile> five = List.of(one, one, one, one, one);

        List<ValidatedImage> res = validator.validate(five);

        assertThat(res).hasSize(5);
    }

    @Test
    void utc045_validate_emptyFile_throwsImageSizeInvalid() {
        MockMultipartFile empty = new MockMultipartFile("files", "empty.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> validator.validate(List.of(empty)))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.IMAGE_SIZE_INVALID);
    }

    @Test
    void utc046_validate_overFiveMb_throwsImageSizeInvalid() {
        MockMultipartFile huge = new MockMultipartFile("files", "big.jpg", "image/jpeg", new byte[5 * 1024 * 1024 + 1]);

        assertThatThrownBy(() -> validator.validate(List.of(huge)))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.IMAGE_SIZE_INVALID);
    }

    @Test
    void utc047_validate_textFile_throwsImageContentInvalid() {
        MockMultipartFile text = new MockMultipartFile("files", "fake.jpg", "text/plain",
                "not an image".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> validator.validate(List.of(text)))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.IMAGE_CONTENT_INVALID);
    }

    @Test
    void utc048_validate_gifImage_throwsImageFormatUnsupported() throws Exception {
        byte[] gifBytes = createImageBytes("gif", 20, 20, BufferedImage.TYPE_INT_RGB);
        MockMultipartFile gif = new MockMultipartFile("files", "anim.gif", "image/gif", gifBytes);

        assertThatThrownBy(() -> validator.validate(List.of(gif)))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.IMAGE_FORMAT_UNSUPPORTED);
    }

    @Test
    void utc049_validate_oversizedDimensions5000x4001_throwsImageDimensionsInvalid() throws Exception {
        byte[] bigPng = createImageBytes("png", 5000, 4001, BufferedImage.TYPE_BYTE_BINARY);
        MockMultipartFile png = new MockMultipartFile("files", "wide.png", "image/png", bigPng);

        assertThatThrownBy(() -> validator.validate(List.of(png)))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.IMAGE_DIMENSIONS_INVALID);
    }

    @Test
    void utc050_validate_truncatedJpeg_throwsImageCorruptedOrReadFailed() throws Exception {
        byte[] fullJpeg = createImageBytes("jpeg", 100, 100, BufferedImage.TYPE_INT_RGB);
        byte[] truncated = Arrays.copyOf(fullJpeg, fullJpeg.length / 2);
        MockMultipartFile bad = new MockMultipartFile("files", "bad.jpg", "image/jpeg", truncated);

        assertThatThrownBy(() -> validator.validate(List.of(bad)))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isIn(ErrorCode.IMAGE_CORRUPTED, ErrorCode.IMAGE_READ_FAILED);
    }
}

