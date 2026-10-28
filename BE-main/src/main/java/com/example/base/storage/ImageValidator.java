package com.example.base.storage;

import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Component
public class ImageValidator {
    public record ValidatedImage(byte[] bytes, String mime, String extension, int width, int height, String hash) {}

    public List<ValidatedImage> validate(List<MultipartFile> files) {
        if (files == null) return List.of();
        if (files.size() > 5) throw new AppException(ErrorCode.IMAGE_COUNT_EXCEEDED);
        return files.stream().map(this::validateOne).toList();
    }

    private ValidatedImage validateOne(MultipartFile file) {
        if (file.isEmpty() || file.getSize() > 5 * 1024 * 1024) throw new AppException(ErrorCode.IMAGE_SIZE_INVALID);
        try {
            byte[] bytes = file.getBytes();
            try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw new AppException(ErrorCode.IMAGE_CONTENT_INVALID);
                var reader = readers.next();
                try {
                    String format = reader.getFormatName();
                    boolean jpeg = format.equalsIgnoreCase("JPEG");
                    if (!jpeg && !format.equalsIgnoreCase("PNG")) throw new AppException(ErrorCode.IMAGE_FORMAT_UNSUPPORTED);
                    reader.setInput(input);
                    int width = reader.getWidth(0), height = reader.getHeight(0);
                    if (width <= 0 || height <= 0 || (long) width * height > 20_000_000) throw new AppException(ErrorCode.IMAGE_DIMENSIONS_INVALID);
                    var decoded = reader.read(0);
                    if (decoded == null) throw new AppException(ErrorCode.IMAGE_READ_FAILED);
                    decoded.flush();
                    return new ValidatedImage(bytes, jpeg ? "image/jpeg" : "image/png", jpeg ? "jpg" : "png",
                            width, height, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
                } finally { reader.dispose(); }
            }
        } catch (IOException ex) {
            throw new AppException(ErrorCode.IMAGE_CORRUPTED);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
