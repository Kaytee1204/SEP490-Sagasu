package com.example.base.mapper;

import com.example.base.dto.checker.ReceiptDto;
import com.example.base.entity.ItemReceipt;
import com.example.base.storage.ImageStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReceiptMapper {
    private final ImageStorage storage;
    public ReceiptDto detail(ItemReceipt r) {
        var location = r.getStorageLocation();
        return new ReceiptDto(r.getId(), r.getReceiptCode(), r.getStatus(), r.getReviewStatus(),
                r.getCategory().getCategoryId(), r.getCategory().getName(), r.getCategory().isActive(), r.getTitle(), r.getDescription(),
                r.getConditionAtReceipt(), r.getFoundLocation(), r.getFoundAt(), r.getPrivateIdentifiers(), r.getNote(),
                r.getRecordedAt(), r.getUpdatedAt(), r.getSubmittedAt(),
                r.getReviewedBy() == null ? null : r.getReviewedBy().getFullName(), r.getReviewedAt(), r.getReviewNote(),
                r.getImages().stream().map(i -> new ReceiptDto.ImageDto(i.getId(), storage.readUrl(i.getStorageKey(), i.getImageUrl()), i.isSensitive())).toList(),
                r.getSource(), r.getRecordedBy().getFullName(), location == null ? null : location.getId(),
                location == null ? null : location.getName(), location != null && location.isActive(),
                r.getReceivedAt(), r.getFinderName(), r.getFinderContact());
    }
}
