package com.example.base.repository;

import com.example.base.entity.ChatAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ChatAttachmentRepository extends JpaRepository<ChatAttachment, Long> {
    List<ChatAttachment> findByMessageIdOrderBySortOrderAsc(long messageId);
    List<ChatAttachment> findByMessageIdInOrderByMessageIdAscSortOrderAsc(List<Long> messageIds);
    boolean existsByStorageKey(String key);
}
