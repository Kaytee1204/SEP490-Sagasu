package com.example.base.dto.post;

import com.example.base.entity.ItemReceipt;
import java.time.OffsetDateTime;

/** Explicit public allowlist; no internal notes, finder contact or identifying secrets. */
public record OfficeReceiptDto(String storageLocationName, String receivedBy, OffsetDateTime receivedAt,
                               String status) {
    public static OfficeReceiptDto from(ItemReceipt receipt) {
        return new OfficeReceiptDto(receipt.getStorageLocation() == null ? null : receipt.getStorageLocation().getName(),
                receipt.getReceivedBy().getFullName(), receipt.getReceivedAt(), receipt.getStatus());
    }
}
