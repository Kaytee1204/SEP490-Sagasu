package com.example.base.service.news;

import com.example.base.dto.PageResponse;
import com.example.base.dto.news.NewsArticleDto;
import com.example.base.dto.news.NewsArticleRequest;
import com.example.base.entity.Account;
import com.example.base.entity.AccountRole;
import com.example.base.entity.NewsArticle;
import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import com.example.base.repository.AccountRepository;
import com.example.base.repository.NewsArticleRepository;
import com.example.base.security.CustomUserDetails;
import com.example.base.storage.ImageStorage;
import com.example.base.storage.ImageValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NewsArticleService {

    private static final Set<String> VALID_CATEGORIES = Set.of("UPDATE", "FEATURE", "GUIDE", "ANNOUNCEMENT", "FEEDBACK");
    private static final Map<String, String> CATEGORY_LABELS = Map.of(
            "UPDATE", "Cập nhật hệ thống",
            "FEATURE", "Tính năng mới",
            "GUIDE", "Hướng dẫn",
            "ANNOUNCEMENT", "Thông báo",
            "FEEDBACK", "Cảm nhận & Phản hồi"
    );

    private final NewsArticleRepository repository;
    private final AccountRepository accounts;
    private final ImageStorage imageStorage;
    private final ImageValidator imageValidator;

    @Transactional(readOnly = true)
    public PageResponse<NewsArticleDto> listPublished(int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(24, size));
        Page<NewsArticle> result = repository.findPublished(PageRequest.of(safePage - 1, safeSize));
        return toPageResponse(result, safePage);
    }

    @Transactional(readOnly = true)
    public NewsArticleDto getPublished(Long id) {
        NewsArticle article = repository.findPublishedById(id)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy bài tin tức."));
        return toDto(article);
    }

    @Transactional(readOnly = true)
    public PageResponse<NewsArticleDto> listForStaff(int page, int size, String keyword, String category, String status, CustomUserDetails user) {
        requireStaff(user);
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(50, size));

        String normalizedCategory = "";
        if (StringUtils.hasText(category) && !"ALL".equalsIgnoreCase(category.trim())) {
            String upper = category.trim().toUpperCase(Locale.ROOT);
            if (VALID_CATEGORIES.contains(upper)) {
                normalizedCategory = upper;
            }
        }

        String normalizedStatus = "ALL";
        if ("PUBLISHED".equalsIgnoreCase(status)) normalizedStatus = "PUBLISHED";
        else if ("DRAFT".equalsIgnoreCase(status)) normalizedStatus = "DRAFT";

        String normalizedPattern = StringUtils.hasText(keyword)
                ? "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%"
                : "";
        Page<NewsArticle> result = repository.searchForStaff(
                normalizedCategory,
                normalizedStatus,
                normalizedPattern,
                PageRequest.of(safePage - 1, safeSize)
        );
        return toPageResponse(result, safePage);
    }

    @Transactional
    public NewsArticleDto create(NewsArticleRequest request, MultipartFile coverImage, CustomUserDetails user) {
        Account author = requireStaff(user);
        ValidatedFields fields = validateRequest(request);

        if (repository.existsDuplicateTitle(fields.title())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Tiêu đề bài tin tức đã tồn tại. Vui lòng chọn tiêu đề khác.");
        }

        NewsArticle article = new NewsArticle();
        article.setTitle(fields.title());
        article.setSummary(fields.summary());
        article.setContent(fields.content());
        article.setCategory(fields.category());
        article.setPublished(request.published() == null || request.published());
        article.setAuthor(author);

        String uploadedKey = null;
        if (coverImage != null && !coverImage.isEmpty()) {
            uploadedKey = uploadCoverImage(coverImage);
            article.setCoverImageKey(uploadedKey);
        }

        try {
            NewsArticle saved = repository.save(article);
            return toDto(saved);
        } catch (RuntimeException ex) {
            if (uploadedKey != null) {
                safeDeleteKey(uploadedKey);
            }
            throw ex;
        }
    }

    @Transactional
    public NewsArticleDto update(Long id, NewsArticleRequest request, MultipartFile coverImage, CustomUserDetails user) {
        Account currentAccount = requireStaff(user);
        ValidatedFields fields = validateRequest(request);

        NewsArticle article = repository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy bài tin tức."));

        requireCanModifyArticle(currentAccount, article, "chỉnh sửa");

        if (repository.existsDuplicateTitleExcludingId(fields.title(), id)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Tiêu đề bài tin tức đã tồn tại. Vui lòng chọn tiêu đề khác.");
        }

        article.setTitle(fields.title());
        article.setSummary(fields.summary());
        article.setContent(fields.content());
        article.setCategory(fields.category());
        if (request.published() != null) {
            article.setPublished(request.published());
        }

        String oldKey = article.getCoverImageKey();
        String newUploadedKey = null;
        if (coverImage != null && !coverImage.isEmpty()) {
            newUploadedKey = uploadCoverImage(coverImage);
            article.setCoverImageKey(newUploadedKey);
            article.setCoverImageUrl(null);
        } else if (Boolean.TRUE.equals(request.removeCoverImage())) {
            article.setCoverImageKey(null);
            article.setCoverImageUrl(null);
        }

        try {
            NewsArticle saved = repository.save(article);
            if (oldKey != null && ((newUploadedKey != null) || Boolean.TRUE.equals(request.removeCoverImage()))) {
                safeDeleteKey(oldKey);
            }
            return toDto(saved);
        } catch (RuntimeException ex) {
            if (newUploadedKey != null) {
                safeDeleteKey(newUploadedKey);
            }
            throw ex;
        }
    }

    @Transactional
    public void delete(Long id, CustomUserDetails user) {
        Account currentAccount = requireStaff(user);
        NewsArticle article = repository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy bài tin tức."));
        requireCanModifyArticle(currentAccount, article, "xóa");
        String oldKey = article.getCoverImageKey();
        repository.delete(article);
        if (oldKey != null) {
            safeDeleteKey(oldKey);
        }
    }

    private void requireCanModifyArticle(Account currentAccount, NewsArticle article, String actionLabel) {
        // Manager có toàn quyền xem, sửa, xóa mọi bài tin tức
        if (currentAccount.getRole() == AccountRole.MANAGER) {
            return;
        }
        // Các tài khoản khác chỉ được sửa/xóa bài do chính mình đăng
        boolean isAuthor = article.getAuthor() != null
                && Objects.equals(article.getAuthor().getId(), currentAccount.getId());
        if (!isAuthor) {
            throw new AppException(
                    ErrorCode.FORBIDDEN,
                    "Chỉ người đăng bài viết hoặc Manager mới có quyền " + actionLabel + " bài tin tức này."
            );
        }
    }

    private Account requireStaff(CustomUserDetails user) {
        if (user == null || user.getId() == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Bạn cần đăng nhập để thực hiện thao tác này.");
        }
        Account account = accounts.findById(user.getId())
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED, "Tài khoản không tồn tại."));
        AccountRole role = account.getRole();
        if (role != AccountRole.APPROVER && role != AccountRole.MANAGER && role != AccountRole.ADMIN) {
            throw new AppException(ErrorCode.FORBIDDEN, "Chỉ Approver hoặc quản trị viên mới có quyền quản lý bài tin tức.");
        }
        return account;
    }

    private ValidatedFields validateRequest(NewsArticleRequest request) {
        if (request == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Dữ liệu bài tin tức không được để trống.");
        }
        String title = request.title() == null ? "" : request.title().trim();
        String summary = request.summary() == null ? "" : request.summary().trim();
        String content = request.content() == null ? "" : request.content().trim();
        String category = request.category() == null ? "UPDATE" : request.category().trim().toUpperCase();

        if (title.isBlank()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Tiêu đề bài tin tức không được để trống.");
        }
        if (title.length() > 200) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Tiêu đề bài tin tức tối đa 200 ký tự.");
        }
        if (summary.isBlank()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Tóm tắt ngắn không được để trống.");
        }
        if (summary.length() > 500) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Tóm tắt ngắn tối đa 500 ký tự.");
        }
        if (content.isBlank()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Nội dung chi tiết không được để trống.");
        }
        if (!VALID_CATEGORIES.contains(category)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Danh mục tin tức không hợp lệ.");
        }
        return new ValidatedFields(title, summary, content, category);
    }

    private String uploadCoverImage(MultipartFile file) {
        List<ImageValidator.ValidatedImage> validated = imageValidator.validate(List.of(file));
        if (validated.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Ảnh bìa không hợp lệ.");
        }
        ImageValidator.ValidatedImage img = validated.get(0);
        String key = "news/" + UUID.randomUUID() + "." + img.extension();
        imageStorage.put(key, img.bytes(), img.mime());
        return key;
    }

    private void safeDeleteKey(String key) {
        try {
            imageStorage.delete(key);
        } catch (RuntimeException ex) {
            log.warn("Could not delete news cover image key {}: {}", key, ex.getMessage());
        }
    }

    private PageResponse<NewsArticleDto> toPageResponse(Page<NewsArticle> pageResult, int oneBasedPage) {
        List<NewsArticleDto> items = pageResult.getContent().stream().map(this::toDto).toList();
        return new PageResponse<>(
                items,
                oneBasedPage,
                pageResult.getSize(),
                pageResult.getTotalElements(),
                pageResult.getTotalPages(),
                pageResult.isFirst(),
                pageResult.isLast()
        );
    }

    private NewsArticleDto toDto(NewsArticle article) {
        String resolvedCover = article.getCoverImageUrl();
        if (StringUtils.hasText(article.getCoverImageKey())) {
            try {
                resolvedCover = imageStorage.readUrl(article.getCoverImageKey(), article.getCoverImageUrl());
            } catch (RuntimeException ex) {
                log.warn("Could not resolve cover image URL for news {}: {}", article.getId(), ex.getMessage());
            }
        }
        String authorName = "Ban quản trị Sagasu";
        Long authorId = null;
        if (article.getAuthor() != null) {
            authorId = article.getAuthor().getId();
            if (StringUtils.hasText(article.getAuthor().getFullName())) {
                authorName = article.getAuthor().getFullName();
            } else if (StringUtils.hasText(article.getAuthor().getEmail())) {
                authorName = article.getAuthor().getEmail();
            }
        }
        String cat = article.getCategory() != null ? article.getCategory() : "UPDATE";
        return new NewsArticleDto(
                article.getId(),
                article.getTitle(),
                article.getSummary(),
                article.getContent(),
                cat,
                CATEGORY_LABELS.getOrDefault(cat, "Cập nhật hệ thống"),
                resolvedCover,
                article.isPublished(),
                authorId,
                authorName,
                article.getCreatedAt(),
                article.getUpdatedAt()
        );
    }

    private record ValidatedFields(String title, String summary, String content, String category) {
    }
}
