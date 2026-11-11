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

    @Test
    void defaultChatProperties_usesFourThousandCharsAndTwentyFiveMegabytesTotal() {
        ChatProperties defaults = new ChatProperties();
        assertEquals(4000, defaults.getMessageMaxLength());
        assertEquals(5, defaults.getAttachmentMaxCount());
        assertEquals(25L * 1024 * 1024, defaults.getTotalMaxBytes());
    }

    @Test
    void validate_whenValidPngJpgAndPdf_detectsActualTypesAndComputesSha256() throws IOException {
        ChatAttachmentValidator validator = new ChatAttachmentValidator(new ChatProperties());
        List<MultipartFile> files = List.of(
                new MockMultipartFile("files", "photo.png", "application/octet-stream", createImageBytes("png")),
                new MockMultipartFile("files", "doc.pdf", "application/pdf", createPdfBytes(false))
        );

        List<ChatAttachmentValidator.Upload> uploads = validator.validate(files);

        assertEquals(2, uploads.size());
        assertEquals("image/png", uploads.get(0).mime());
        assertEquals("png", uploads.get(0).extension());
        assertEquals(64, uploads.get(0).hash().length());
        assertEquals("application/pdf", uploads.get(1).mime());
        assertEquals("pdf", uploads.get(1).extension());
    }

    @Test
    void validate_whenEncryptedOrCorruptedPdfOrUnsupportedFile_throwsChatAttachmentInvalid() throws IOException {
        ChatAttachmentValidator validator = new ChatAttachmentValidator(new ChatProperties());

        MultipartFile encryptedPdf = new MockMultipartFile(
                "files", "secret.pdf", "application/pdf", createPdfBytes(true));
        assertEquals(ErrorCode.CHAT_ATTACHMENT_INVALID,
                assertThrows(AppException.class, () -> validator.validate(List.of(encryptedPdf))).getErrorCode());

        MultipartFile fakePdf = new MockMultipartFile(
                "files", "fake.pdf", "application/pdf", "%PDF-1.4\ncorrupted-body".getBytes(StandardCharsets.UTF_8));
        assertEquals(ErrorCode.CHAT_ATTACHMENT_INVALID,
                assertThrows(AppException.class, () -> validator.validate(List.of(fakePdf))).getErrorCode());

        MultipartFile text = new MockMultipartFile(
                "files", "note.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));
        assertEquals(ErrorCode.CHAT_ATTACHMENT_INVALID,
                assertThrows(AppException.class, () -> validator.validate(List.of(text))).getErrorCode());
    }

    @Test
    void validate_whenCountOrIndividualOrTotalSizeExceeded_throwsChatAttachmentInvalid() throws IOException {
        ChatProperties props = new ChatProperties();
        props.setAttachmentMaxCount(1);
        props.setTotalMaxBytes(500);
        props.setImageMaxBytes(400);
        ChatAttachmentValidator validator = new ChatAttachmentValidator(props);

        byte[] smallPng = createImageBytes("png");
        assertTrue(smallPng.length > 50);

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
    void validate_whenValidJpegAndFilenamesWithSlashesOrNull_returnsUploadWithSanitizedName() throws IOException {
        ChatAttachmentValidator validator = new ChatAttachmentValidator(new ChatProperties());
        byte[] jpeg = createImageBytes("jpg", 800, 600);
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

        List<ChatAttachmentValidator.Upload> uploads = validator.validate(List.of(
                new MockMultipartFile("files", "a/b\\c.jpg", "image/jpeg", jpeg),
                nullNamePng));

        assertEquals(2, uploads.size());
        assertEquals("image/jpeg", uploads.get(0).mime());
        assertEquals("jpg", uploads.get(0).extension());
        assertEquals(64, uploads.get(0).hash().length());
        assertEquals("a_b_c.jpg", uploads.get(0).name());
        assertEquals("attachment.png", uploads.get(1).name());
    }

    @Test
    void validate_whenNullEmptyGifTextAsJpgSizeExceededOrHugePixels_throwsChatAttachmentInvalid()
            throws IOException {
        ChatAttachmentValidator validator = new ChatAttachmentValidator(new ChatProperties());

        assertEquals(ErrorCode.CHAT_ATTACHMENT_INVALID,
                assertThrows(AppException.class, () -> validator.validate(null)).getErrorCode());
        assertEquals(ErrorCode.CHAT_ATTACHMENT_INVALID,
                assertThrows(AppException.class, () -> validator.validate(List.of())).getErrorCode());

        byte[] gif = createImageBytes("gif");
        assertEquals(ErrorCode.CHAT_ATTACHMENT_INVALID,
                assertThrows(AppException.class, () -> validator.validate(List.of(
                        new MockMultipartFile("files", "anim.gif", "image/gif", gif)))).getErrorCode());

        assertEquals(ErrorCode.CHAT_ATTACHMENT_INVALID,
                assertThrows(AppException.class, () -> validator.validate(List.of(
                        new MockMultipartFile("files", "fake.jpg", "image/jpeg",
                                "not a jpeg".getBytes(StandardCharsets.UTF_8))))).getErrorCode());

        byte[] png = createImageBytes("png");
        ChatProperties smallImageLimit = new ChatProperties();
        smallImageLimit.setImageMaxBytes(Math.max(1L, png.length - 1L));
        ChatAttachmentValidator imageLimitValidator = new ChatAttachmentValidator(smallImageLimit);
        assertEquals(ErrorCode.CHAT_ATTACHMENT_INVALID,
                assertThrows(AppException.class, () -> imageLimitValidator.validate(List.of(
                        new MockMultipartFile("files", "a.png", "image/png", png)))).getErrorCode());

        ChatProperties totalLimit = new ChatProperties();
        totalLimit.setImageMaxBytes(png.length + 100L);
        totalLimit.setTotalMaxBytes(png.length * 2L - 1L);
        ChatAttachmentValidator totalLimitValidator = new ChatAttachmentValidator(totalLimit);
        assertEquals(ErrorCode.CHAT_ATTACHMENT_INVALID,
                assertThrows(AppException.class, () -> totalLimitValidator.validate(List.of(
                        new MockMultipartFile("files", "1.png", "image/png", png),
                        new MockMultipartFile("files", "2.png", "image/png", png)))).getErrorCode());

        BufferedImage huge = new BufferedImage(5000, 5000, BufferedImage.TYPE_BYTE_BINARY);
        ByteArrayOutputStream hugeOut = new ByteArrayOutputStream();
        ImageIO.write(huge, "png", hugeOut);
        assertEquals(ErrorCode.CHAT_ATTACHMENT_INVALID,
                assertThrows(AppException.class, () -> validator.validate(List.of(
                        new MockMultipartFile("files", "huge.png", "image/png", hugeOut.toByteArray()))))
                        .getErrorCode());
    }

    private static byte[] createImageBytes(String format, int width, int height) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }
}

