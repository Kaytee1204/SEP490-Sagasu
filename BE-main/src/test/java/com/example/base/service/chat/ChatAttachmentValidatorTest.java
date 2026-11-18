package com.example.base.service.chat;

import com.example.base.config.chat.ChatProperties;
import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatAttachmentValidatorTest {

    // Supplementary (ChatProperties): verifies default max length 4000 chars, max 5 files, 25MB total
    @Test
    void utc074_defaultChatProperties_usesFourThousandCharsAndTwentyFiveMegabytesTotal() {
        ChatProperties defaults = new ChatProperties();
        assertEquals(4000, defaults.getMessageMaxLength());
        assertEquals(5, defaults.getAttachmentMaxCount());
        assertEquals(25L * 1024 * 1024, defaults.getTotalMaxBytes());
    }

    @Test
    void utc061_validate_pngDetectedAndHashed() throws IOException {
        List<ChatAttachmentValidator.Upload> uploads = validator().validate(List.of(
                new MockMultipartFile("files", "photo.png", "application/octet-stream", createImageBytes("png"))));
        assertEquals("image/png", uploads.get(0).mime());
        assertEquals("png", uploads.get(0).extension());
        assertEquals(64, uploads.get(0).hash().length());
    }

    @Test
    void utc062_validate_onePagePdfDetected() throws IOException {
        List<ChatAttachmentValidator.Upload> uploads = validator().validate(List.of(
                new MockMultipartFile("files", "doc.pdf", "application/pdf", createPdfBytes(false))));
        assertEquals("application/pdf", uploads.get(0).mime());
        assertEquals("pdf", uploads.get(0).extension());
    }

    // UTC068 - validate rejects encrypted PDF (plus Supplementary corrupted PDF and unsupported .txt)
    @Test
    void validate_whenEncryptedOrCorruptedPdfOrUnsupportedFile_throwsChatAttachmentInvalid() throws IOException {
        ChatAttachmentValidator validator = new ChatAttachmentValidator(new ChatProperties());

        // UTC068 - encrypted PDF throws CHAT_ATTACHMENT_INVALID
        MultipartFile encryptedPdf = new MockMultipartFile(
                "files", "secret.pdf", "application/pdf", createPdfBytes(true));
        assertEquals(ErrorCode.CHAT_ATTACHMENT_INVALID,
                assertThrows(AppException.class, () -> validator.validate(List.of(encryptedPdf))).getErrorCode());

        // Supplementary (ChatAttachmentValidator.validate): corrupted PDF throws CHAT_ATTACHMENT_INVALID
        MultipartFile fakePdf = new MockMultipartFile(
                "files", "fake.pdf", "application/pdf", "%PDF-1.4\ncorrupted-body".getBytes(StandardCharsets.UTF_8));
        assertEquals(ErrorCode.CHAT_ATTACHMENT_INVALID,
                assertThrows(AppException.class, () -> validator.validate(List.of(fakePdf))).getErrorCode());

        // Supplementary (ChatAttachmentValidator.validate): unsupported text file throws CHAT_ATTACHMENT_INVALID
        MultipartFile text = new MockMultipartFile(
                "files", "note.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));
        assertEquals(ErrorCode.CHAT_ATTACHMENT_INVALID,
                assertThrows(AppException.class, () -> validator.validate(List.of(text))).getErrorCode());
    }

    // UTC065 - validate rejects attachment count exceeding configured maximum
    @Test
    void validate_whenCountOrIndividualOrTotalSizeExceeded_throwsChatAttachmentInvalid() throws IOException {
        ChatProperties props = new ChatProperties();
        props.setAttachmentMaxCount(1);
        props.setTotalMaxBytes(500);
        props.setImageMaxBytes(400);
        ChatAttachmentValidator validator = new ChatAttachmentValidator(props);

        byte[] smallPng = createImageBytes("png");
        assertTrue(smallPng.length > 50);

        // UTC065 - file count exceeding attachmentMaxCount throws CHAT_ATTACHMENT_INVALID
        MultipartFile f1 = new MockMultipartFile("files", "1.png", "image/png", smallPng);
        MultipartFile f2 = new MockMultipartFile("files", "2.png", "image/png", smallPng);

        assertEquals(ErrorCode.CHAT_ATTACHMENT_INVALID,
                assertThrows(AppException.class, () -> validator.validate(List.of(f1, f2))).getErrorCode());

        props.setAttachmentMaxCount(5);
        props.setTotalMaxBytes(250);
        props.setImageMaxBytes(250);
        byte[] largePng = createImageBytes("png");
        if (largePng.length > 250) {
            MultipartFile oversized = new MockMultipartFile("files", "big.png", "image/png", largePng);
            assertEquals(ErrorCode.CHAT_ATTACHMENT_INVALID,
                    assertThrows(AppException.class, () -> validator.validate(List.of(oversized))).getErrorCode());
        }
    }

    private static byte[] createImageBytes(String format) throws IOException {
        BufferedImage img = new BufferedImage(12, 12, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    private static byte[] createPdfBytes(boolean encrypted) throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.addPage(new PDPage());
            if (encrypted) {
                AccessPermission ap = new AccessPermission();
                StandardProtectionPolicy policy = new StandardProtectionPolicy("owner", "user", ap);
                doc.protect(policy);
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    @Test
    void utc060_validate_jpeg800x600Accepted() throws IOException {
        List<ChatAttachmentValidator.Upload> uploads = validator().validate(List.of(
                new MockMultipartFile("files", "photo.jpg", "image/jpeg", createImageBytes("jpg", 800, 600))));
        assertEquals("image/jpeg", uploads.get(0).mime());
        assertEquals("jpg", uploads.get(0).extension());
        assertEquals(64, uploads.get(0).hash().length());
    }

    @Test
    void utc063_validate_nullFilesThrowsInvalid() {
        assertInvalid(validator(), null);
    }

    @Test
    void utc064_validate_emptyFilesThrowsInvalid() {
        assertInvalid(validator(), List.of());
    }

    @Test
    void utc066_validate_gifRejected() throws IOException {
        assertInvalid(validator(), List.of(new MockMultipartFile("files", "anim.gif", "image/gif", createImageBytes("gif"))));
    }

    @Test
    void utc067_validate_textRenamedToJpgRejected() {
        assertInvalid(validator(), List.of(new MockMultipartFile("files", "fake.jpg", "image/jpeg",
                "not a jpeg".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void utc069_validate_imageOverImageMaxBytesRejected() throws IOException {
        byte[] png = createImageBytes("png");
        ChatProperties props = new ChatProperties();
        props.setImageMaxBytes(Math.max(1L, png.length - 1L));
        assertInvalid(new ChatAttachmentValidator(props), List.of(new MockMultipartFile("files", "a.png", "image/png", png)));
    }

    @Test
    void utc070_validate_totalOverTotalMaxBytesRejected() throws IOException {
        byte[] png = createImageBytes("png");
        ChatProperties props = new ChatProperties();
        props.setImageMaxBytes(png.length + 100L);
        props.setTotalMaxBytes(png.length * 2L - 1L);
        assertInvalid(new ChatAttachmentValidator(props), List.of(
                new MockMultipartFile("files", "1.png", "image/png", png),
                new MockMultipartFile("files", "2.png", "image/png", png)));
    }

    @Test
    void utc071_validate_over20MegapixelsRejected() throws IOException {
        BufferedImage huge = new BufferedImage(5000, 5000, BufferedImage.TYPE_BYTE_BINARY);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(huge, "png", out);
        assertInvalid(validator(), List.of(new MockMultipartFile("files", "huge.png", "image/png", out.toByteArray())));
    }

    @Test
    void utc072_validate_slashesInFilenameSanitized() throws IOException {
        List<ChatAttachmentValidator.Upload> uploads = validator().validate(List.of(
                new MockMultipartFile("files", "a/b\\c.jpg", "image/jpeg", createImageBytes("jpg"))));
        assertEquals("a_b_c.jpg", uploads.get(0).name());
    }

    @Test
    void utc073_validate_nullFilenameDefaultsToAttachmentExt() throws IOException {
        byte[] png = createImageBytes("png");
        MultipartFile nullNamePng = new MultipartFile() {
            @Override public String getName() { return "files"; }
            @Override public String getOriginalFilename() { return null; }
            @Override public String getContentType() { return "image/png"; }
            @Override public boolean isEmpty() { return false; }
            @Override public long getSize() { return png.length; }
            @Override public byte[] getBytes() { return png; }
            @Override public InputStream getInputStream() { return new ByteArrayInputStream(png); }
            @Override public void transferTo(File dest) {}
        };
        assertEquals("attachment.png", validator().validate(List.of(nullNamePng)).get(0).name());
    }

    private static ChatAttachmentValidator validator() {
        return new ChatAttachmentValidator(new ChatProperties());
    }

    private static void assertInvalid(ChatAttachmentValidator validator, List<MultipartFile> files) {
        assertEquals(ErrorCode.CHAT_ATTACHMENT_INVALID,
                assertThrows(AppException.class, () -> validator.validate(files)).getErrorCode());
    }

    private static byte[] createImageBytes(String format, int width, int height) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }
}
