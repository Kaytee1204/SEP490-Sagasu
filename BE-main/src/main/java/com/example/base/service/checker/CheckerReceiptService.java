package com.example.base.service.checker;

import com.example.base.dto.PageResponse;
import com.example.base.dto.checker.*;
import com.example.base.entity.*;
import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import com.example.base.repository.*;
import com.example.base.security.CustomUserDetails;
import com.example.base.mapper.ReceiptMapper;
import com.example.base.service.receipt.ReceiptLocationService;
import com.example.base.storage.ImageStorage;
import com.example.base.storage.ImageValidator;
import com.example.base.storage.ImageValidator.ValidatedImage;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CheckerReceiptService {
    private final ItemReceiptRepository receipts;
    private final CategoryRepository categories;
    private final AccountRepository accounts;
    private final ImageStorage storage;
    private final ImageValidator validator;
    private final PlatformTransactionManager transactionManager;
    private final EntityManager entityManager;
    private final ReceiptLocationService locations;
    private final ReceiptMapper mapper;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<ReceiptSummaryDto> list(int page, int size, String keyword, String filter, CustomUserDetails user) {
        long owner = checkerId(user);
        String kw = keyword == null ? "" : keyword.strip();
        if (page < 1 || size < 1 || size > 100 || kw.length() > 200 || !Set.of("ALL", "DRAFT", "SUBMITTED", "NEEDS_INFO", "ACCEPTED", "REJECTED").contains(filter)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Bộ lọc hoặc phân trang không hợp lệ");
        }
        var ids = receipts.findMyIds(owner, kw, filter, PageRequest.of(page - 1, size));
        var byId = ids.isEmpty() ? Map.<Long, ItemReceipt>of() : receipts.findMyDetails(ids.getContent(), owner).stream()
                .collect(Collectors.toMap(ItemReceipt::getId, r -> r));
        var items = ids.stream().map(id -> summary(byId.get(id))).toList();
        return new PageResponse<>(items, page, size, ids.getTotalElements(), ids.getTotalPages(), ids.isFirst(), ids.isLast());
    }

    @Transactional(readOnly = true)
    public ReceiptDto detail(long id, CustomUserDetails user) { return dto(owned(id, checkerId(user))); }

    public ReceiptDto create(SaveReceiptRequest request, List<MultipartFile> files, CustomUserDetails user) {
        long owner = checkerId(user);
        if (!request.keptImageIds().isEmpty()) throw invalidImages();
        validateInput(request);
        List<ValidatedImage> images = validator.validate(files);
        requireImageCount(images.size());
        List<String> keys = new ArrayList<>();
        try {
            upload(owner, images, keys);
            return new TransactionTemplate(transactionManager).execute(tx -> {
                Account account = activeChecker(owner);
                ItemReceipt receipt = ItemReceipt.draft("REC-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT), account);
                editFields(receipt, request);
                attach(receipt, images, keys, 0);
                receipts.saveAndFlush(receipt);
                entityManager.refresh(receipt);
                return dto(receipt);
            });
        } catch (RuntimeException ex) { cleanup(keys); throw ex; }
    }

    public ReceiptDto update(long id, SaveReceiptRequest request, List<MultipartFile> files, CustomUserDetails user) {
        long owner = checkerId(user);
        validateInput(request);
        List<ValidatedImage> images = validator.validate(files);
        requireImageCount(request.keptImageIds().size() + images.size());
        // Short preflight avoids uploading files for an already invalid/stale request.
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            ItemReceipt current = owned(id, owner);
            requireDraft(current);
            requireCurrent(current, request.expectedUpdatedAt());
            validateKept(current, request.keptImageIds());
        });
        List<String> keys = new ArrayList<>();
        Saved saved;
        try {
            upload(owner, images, keys);
            saved = new TransactionTemplate(transactionManager).execute(tx -> {
                activeChecker(owner);
                ItemReceipt receipt = lock(id, owner);
                requireDraft(receipt);
                requireCurrent(receipt, request.expectedUpdatedAt());
                validateKept(receipt, request.keptImageIds());
                List<String> removed = receipt.getImages().stream().filter(i -> !request.keptImageIds().contains(i.getId()))
                        .map(ItemReceiptImage::getStorageKey).toList();
                // Append above every old position: IDENTITY inserts can precede orphan deletes.
                int nextOrder = receipt.getImages().stream().mapToInt(ItemReceiptImage::getSortOrder).max().orElse(-1) + 1;
                editFields(receipt, request);
                receipt.retainImages(request.keptImageIds());
                attach(receipt, images, keys, nextOrder);
                receipts.flush();
                entityManager.refresh(receipt);
                return new Saved(dto(receipt), removed);
            });
        } catch (RuntimeException ex) { cleanup(keys); throw ex; }
        // DB commit has succeeded. Never delete existing objects before this point.
        cleanup(saved.removedKeys());
        return saved.receipt();
    }

    @Transactional
    public ReceiptDto submit(long id, SubmitReceiptRequest request, CustomUserDetails user) {
        long owner = checkerId(user);
        activeChecker(owner);
        ItemReceipt receipt = lock(id, owner);
        if ("SUBMITTED".equals(receipt.getStatus()) && "PENDING".equals(receipt.getReviewStatus())) return dto(receipt);
        requireDraft(receipt);
        requireCurrent(receipt, request.expectedUpdatedAt());
        activeCategory(receipt.getCategory().getCategoryId());
        if (receipt.getTitle().isBlank() || receipt.getDescription().isBlank() || receipt.getFoundLocation().isBlank()
                || receipt.getConditionAtReceipt() == null || receipt.getConditionAtReceipt().isBlank()
                || receipt.getFoundAt().isAfter(OffsetDateTime.now())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Vui lòng hoàn thiện thông tin và tình trạng đồ vật trước khi gửi");
        }
        requireImageCount(receipt.getImages().size());
        receipt.submit();
        receipts.flush();
        entityManager.refresh(receipt);
        return dto(receipt);
    }

    private long checkerId(CustomUserDetails user) {
        if (user == null) throw new AppException(ErrorCode.UNAUTHORIZED);
        if (user.getRole() != AccountRole.CHECKER || user.getStatus() != AccountStatus.ACTIVE
                || !user.isAccountNonExpired() || !user.isAccountNonLocked()) throw new AppException(ErrorCode.FORBIDDEN);
        return user.getId();
    }

    private Account activeChecker(long id) {
        Account account = accounts.findActiveAccountForPostCreation(id).orElseThrow(() -> new AppException(ErrorCode.FORBIDDEN));
        entityManager.refresh(account, LockModeType.PESSIMISTIC_READ);
        checkerId(new CustomUserDetails(account));
        return account;
    }

    private Category activeCategory(Long id) {
        Category category = categories.findActiveForPostCreation(id).orElseThrow(() -> new AppException(ErrorCode.POST_CATEGORY_UNAVAILABLE));
        entityManager.refresh(category, LockModeType.PESSIMISTIC_READ);
        if (!category.isActive()) throw new AppException(ErrorCode.POST_CATEGORY_UNAVAILABLE);
        return category;
    }

    private ItemReceipt owned(long id, long owner) {
        if (id < 1) throw new AppException(ErrorCode.BAD_REQUEST, "Mã phiếu không hợp lệ");
        return receipts.findOwned(id, owner).orElseThrow(() -> new AppException(ErrorCode.RECEIPT_NOT_FOUND));
    }

    private ItemReceipt lock(long id, long owner) {
        ItemReceipt receipt = receipts.lockOwned(id, owner).orElseThrow(() -> new AppException(ErrorCode.RECEIPT_NOT_FOUND));
        // OSIV may still contain the pre-upload entity, even after acquiring a lock.
        entityManager.refresh(receipt, LockModeType.PESSIMISTIC_WRITE);
        if (!Objects.equals(receipt.getRecordedBy().getId(), owner) || !"CHECKER".equals(receipt.getSource())) {
            throw new AppException(ErrorCode.RECEIPT_NOT_FOUND);
        }
        return receipt;
    }

    private void requireDraft(ItemReceipt receipt) {
        if (!("DRAFT".equals(receipt.getStatus()) && "PENDING".equals(receipt.getReviewStatus()))
                && !("SUBMITTED".equals(receipt.getStatus()) && "NEEDS_INFO".equals(receipt.getReviewStatus()))) {
            throw new AppException(ErrorCode.RECEIPT_NOT_EDITABLE);
        }
    }
    private void requireCurrent(ItemReceipt receipt, OffsetDateTime expected) {
        if (expected == null || !receipt.getUpdatedAt().isEqual(expected)) throw new AppException(ErrorCode.RECEIPT_STALE);
    }
    private void requireImageCount(int count) {
        if (count < 1 || count > 5) throw new AppException(ErrorCode.BAD_REQUEST, "Hồ sơ cần từ 1 đến 5 ảnh thực tế");
    }
    private AppException invalidImages() { return new AppException(ErrorCode.BAD_REQUEST, "Danh sách ảnh giữ lại không hợp lệ. Vui lòng tải lại phiếu."); }
    private void validateKept(ItemReceipt receipt, List<Long> ids) {
        Set<Long> unique = new HashSet<>(ids);
        if (unique.size() != ids.size() || !receipt.getImages().stream().map(ItemReceiptImage::getId).toList().containsAll(ids)) throw invalidImages();
    }
    private void validateInput(SaveReceiptRequest request) {
        if (request.foundAt().isAfter(OffsetDateTime.now())) throw new AppException(ErrorCode.BAD_REQUEST, "Thời gian nhặt không được ở tương lai");
        if (!categories.existsByCategoryIdAndActiveTrue(request.categoryId())) throw new AppException(ErrorCode.POST_CATEGORY_UNAVAILABLE);
    }
    private void editFields(ItemReceipt receipt, SaveReceiptRequest request) {
        receipt.assignLocation(locations.resolve(request.storageLocationId(), receipt.getStorageLocation()));
        receipt.edit(activeCategory(request.categoryId()), request.title(), request.description(), request.conditionAtReceipt(),
                request.foundLocation(), request.foundAt(), request.privateIdentifiers(), request.note());
    }
    private void upload(long owner, List<ValidatedImage> images, List<String> keys) {
        for (ValidatedImage image : images) {
            String key = "receipts/" + owner + "/" + UUID.randomUUID() + "." + image.extension();
            keys.add(key);
            storage.put(key, image.bytes(), image.mime());
        }
    }
    private void attach(ItemReceipt receipt, List<ValidatedImage> images, List<String> keys, int start) {
        for (int i = 0; i < images.size(); i++) receipt.addImage(new ItemReceiptImage(keys.get(i), start + i, images.get(i).mime(), images.get(i).bytes().length));
    }
    private void cleanup(List<String> keys) {
        for (String key : keys) try { storage.delete(key); } catch (RuntimeException ex) { log.warn("Could not clean up receipt image {}", key); }
    }
    private String imageUrl(ItemReceiptImage image) { return storage.readUrl(image.getStorageKey(), image.getImageUrl()); }
    private ReceiptSummaryDto summary(ItemReceipt r) {
        return new ReceiptSummaryDto(r.getId(), r.getReceiptCode(), r.getTitle(), r.getCategory().getName(),
                r.getFoundLocation(), r.getFoundAt(), r.getRecordedAt(), r.getStatus(), r.getReviewStatus(),
                r.getImages().isEmpty() ? null : imageUrl(r.getImages().getFirst()),
                r.getStorageLocation() == null ? null : r.getStorageLocation().getName());
    }
    private ReceiptDto dto(ItemReceipt r) {
        return mapper.detail(r);
    }
    @Transactional
    public ReceiptDto updateLocation(long id, UpdateReceiptLocationRequest request, CustomUserDetails user) {
        long owner = checkerId(user);
        activeChecker(owner);
        ItemReceipt receipt = lock(id, owner);
        if (receipt.getReceivedAt() != null || !(Set.of("DRAFT", "SUBMITTED").contains(receipt.getStatus())
                && Set.of("PENDING", "NEEDS_INFO", "ACCEPTED").contains(receipt.getReviewStatus()))) {
            throw new AppException(ErrorCode.RECEIPT_NOT_EDITABLE, "Phiếu đã bị từ chối hoặc bàn giao; không thể đổi vị trí.");
        }
        requireCurrent(receipt, request.expectedUpdatedAt());
        receipt.assignLocation(locations.resolve(request.storageLocationId(), receipt.getStorageLocation()));
        receipts.flush();
        entityManager.refresh(receipt);
        return dto(receipt);
    }
    private record Saved(ReceiptDto receipt, List<String> removedKeys) {}
}
