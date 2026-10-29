package com.example.base.service.approver;

import com.example.base.dto.PageResponse;
import com.example.base.dto.approver.*;
import com.example.base.dto.checker.ReceiptDto;
import com.example.base.entity.*;
import com.example.base.exception.*;
import com.example.base.mapper.ReceiptMapper;
import com.example.base.repository.ItemReceiptRepository;
import com.example.base.security.CustomUserDetails;
import com.example.base.storage.ImageStorage;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApproverReceiptService {
    private final ItemReceiptRepository receipts;
    private final EntityManager entityManager;
    private final ReceiptMapper mapper;
    private final ImageStorage storage;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<ReceiptQueueDto> list(int page, int size, String keyword, String filter, String source, CustomUserDetails user) {
        approverId(user);
        String kw = keyword == null ? "" : keyword.strip();
        if (page < 1 || size < 1 || size > 100 || kw.length() > 200
                || !Set.of("ALL", "PENDING", "NEEDS_INFO", "ACCEPTED", "REJECTED").contains(filter)
                || !Set.of("ALL", "CHECKER", "WALK_IN").contains(source)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Bộ lọc hoặc phân trang không hợp lệ");
        }
        var ids = receipts.findQueueIds(kw, filter, source, PageRequest.of(page - 1, size));
        var details = ids.isEmpty() ? Map.<Long, ItemReceipt>of() : receipts.findQueueDetails(ids.getContent()).stream()
                .collect(Collectors.toMap(ItemReceipt::getId, r -> r));
        var content = ids.stream().map(id -> {
            var r = details.get(id);
            var image = r.getImages().isEmpty() ? null : r.getImages().getFirst();
            return new ReceiptQueueDto(r.getId(), r.getReceiptCode(), r.getTitle(), r.getCategory().getName(),
                    r.getSource(), r.getRecordedBy().getFullName(), r.getSubmittedAt() == null ? r.getRecordedAt() : r.getSubmittedAt(),
                    r.getStatus(), r.getReviewStatus(), r.getStorageLocation() == null ? null : r.getStorageLocation().getName(),
                    image == null ? null : storage.readUrl(image.getStorageKey(), image.getImageUrl()));
        }).toList();
        return new PageResponse<>(content, page, size, ids.getTotalElements(), ids.getTotalPages(), ids.isFirst(), ids.isLast());
    }

    @Transactional(readOnly = true)
    public ReceiptDto detail(long id, CustomUserDetails user) {
        approverId(user);
        return mapper.detail(receipts.findForReview(id).orElseThrow(() -> new AppException(ErrorCode.RECEIPT_NOT_FOUND)));
    }

    @Transactional
    public ReceiptDto review(long id, ReviewReceiptRequest request, CustomUserDetails user) {
        long actor = approverId(user);
        Account approver = entityManager.find(Account.class, actor, LockModeType.PESSIMISTIC_READ);
        if (approver == null) throw new AppException(ErrorCode.FORBIDDEN);
        entityManager.refresh(approver, LockModeType.PESSIMISTIC_READ);
        approverId(new CustomUserDetails(approver));
        if (request.decision() != ReviewReceiptRequest.Decision.ACCEPTED && (request.note() == null || request.note().isBlank())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Vui lòng ghi rõ lý do từ chối hoặc nội dung cần bổ sung.");
        }
        ItemReceipt receipt = receipts.lockForReview(id).orElseThrow(() -> new AppException(ErrorCode.RECEIPT_NOT_FOUND));
        entityManager.refresh(receipt, LockModeType.PESSIMISTIC_WRITE);
        if (Objects.equals(receipt.getRecordedBy().getId(), actor)) throw new AppException(ErrorCode.FORBIDDEN, "Không thể tự xét duyệt phiếu của mình.");
        if (!"CHECKER".equals(receipt.getSource()) || !"SUBMITTED".equals(receipt.getStatus()) || !"PENDING".equals(receipt.getReviewStatus())) {
            throw new AppException(ErrorCode.RECEIPT_NOT_EDITABLE, "Phiếu không còn chờ duyệt. Vui lòng tải lại để xem trạng thái mới.");
        }
        if (!receipt.getUpdatedAt().isEqual(request.expectedUpdatedAt())) throw new AppException(ErrorCode.RECEIPT_STALE);
        receipt.review(request.decision().name(), request.note(), approver);
        receipts.flush();
        entityManager.refresh(receipt);
        return mapper.detail(receipt);
    }

    private long approverId(CustomUserDetails user) {
        if (user == null) throw new AppException(ErrorCode.UNAUTHORIZED);
        if (user.getRole() != AccountRole.APPROVER || user.getStatus() != AccountStatus.ACTIVE
                || !user.isAccountNonExpired() || !user.isAccountNonLocked()) throw new AppException(ErrorCode.FORBIDDEN);
        return user.getId();
    }
}
