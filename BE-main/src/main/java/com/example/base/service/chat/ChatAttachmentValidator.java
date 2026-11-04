package com.example.base.service.chat;

import com.example.base.config.chat.ChatProperties;
import com.example.base.exception.*;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.util.*;

@Component @RequiredArgsConstructor
public class ChatAttachmentValidator {
    private final ChatProperties limits;
    public record Upload(byte[] bytes, String mime, String extension, String name, String hash) {}
    public List<Upload> validate(List<MultipartFile> files) {
        if (files == null || files.isEmpty() || files.size() > limits.getAttachmentMaxCount()) throw invalid();
        long total = 0;
        for (var file : files) {
            if (file.isEmpty() || file.getSize() > Math.max(limits.getImageMaxBytes(), limits.getPdfMaxBytes())) throw invalid();
            total += file.getSize();
            if (total > limits.getTotalMaxBytes()) throw invalid();
        }
        return files.stream().map(this::one).toList();
    }
    private Upload one(MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            String mime, extension;
            if (bytes.length >= 5 && bytes[0]=='%' && bytes[1]=='P' && bytes[2]=='D' && bytes[3]=='F' && bytes[4]=='-') {
                if (bytes.length > limits.getPdfMaxBytes()) throw invalid();
                try (var pdf = Loader.loadPDF(bytes)) {
                    if (pdf.isEncrypted() || pdf.getNumberOfPages() < 1) throw invalid();
                }
                mime = "application/pdf"; extension = "pdf";
            } else {
                if (bytes.length > limits.getImageMaxBytes()) throw invalid();
                try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                    var readers = ImageIO.getImageReaders(input);
                    if (!readers.hasNext()) throw invalid();
                    var reader = readers.next();
                    try {
                        String format = reader.getFormatName();
                        if (!format.equalsIgnoreCase("JPEG") && !format.equalsIgnoreCase("PNG")) throw invalid();
                        reader.setInput(input);
                        int width = reader.getWidth(0), height = reader.getHeight(0);
                        if (width <= 0 || height <= 0 || (long) width * height > 20_000_000) throw invalid();
                        var image = reader.read(0);
                        if (image == null) throw invalid();
                        image.flush();
                        extension = format.equalsIgnoreCase("PNG") ? "png" : "jpg";
                        mime = "image/" + (extension.equals("png") ? "png" : "jpeg");
                    } finally { reader.dispose(); }
                }
            }
            String name = file.getOriginalFilename() == null ? "attachment." + extension
                    : file.getOriginalFilename().replaceAll("[\\p{Cntrl}\\\\/]", "_");
            if (name.isBlank()) name = "attachment." + extension;
            if (name.length() > 255) name = name.substring(name.length() - 255);
            return new Upload(bytes, mime, extension, name, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        } catch (AppException ex) { throw ex; }
        catch (Exception ex) { throw invalid(); }
    }
    private AppException invalid() { return new AppException(ErrorCode.CHAT_ATTACHMENT_INVALID); }
}
