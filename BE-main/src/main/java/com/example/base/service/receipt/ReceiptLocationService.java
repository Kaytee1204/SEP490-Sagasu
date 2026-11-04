package com.example.base.service.receipt;

import com.example.base.entity.StorageLocation;
import com.example.base.exception.*;
import com.example.base.repository.StorageLocationRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class ReceiptLocationService {
    private final StorageLocationRepository locations;
    private final EntityManager entityManager;

    public ReceiptLocationService(StorageLocationRepository locations, EntityManager entityManager) {
        this.locations = locations;
        this.entityManager = entityManager;
    }

    public record Option(Long id, String code, String name) {}

    @Transactional(readOnly = true)
    public List<Option> options() {
        return locations.findByActiveTrueOrderByNameAsc().stream().map(l -> new Option(l.getId(), l.getCode(), l.getName())).toList();
    }

    // Called within the receipt write transaction. Historical inactive assignments may be kept.
    public StorageLocation resolve(Long id, StorageLocation existing) {
        if (id == null) return null;
        var all = locations.lockAll();
        StorageLocation location = all.stream().filter(l -> l.getId().equals(id)).findFirst().orElse(null);
        if (location == null) throw unavailable();
        entityManager.refresh(location, LockModeType.PESSIMISTIC_WRITE);
        if (!location.isActive()) throw unavailable();
        var visited = new java.util.HashSet<Long>();
        StorageLocation ancestor = location;
        while (ancestor.getParentId() != null) {
            if (!visited.add(ancestor.getId())) throw unavailable();
            Long parent = ancestor.getParentId();
            ancestor = all.stream().filter(l -> l.getId().equals(parent)).findFirst().orElseThrow(this::unavailable);
            if (!ancestor.isActive()) throw unavailable();
        }
        return location;
    }

    private AppException unavailable() {
        return new AppException(ErrorCode.BAD_REQUEST, "Vị trí lưu giữ không tồn tại hoặc đã ngừng sử dụng. Vui lòng tải lại danh sách.");
    }
}
