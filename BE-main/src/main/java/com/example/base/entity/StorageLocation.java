package com.example.base.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;

@Entity
@Table(name = "storage_location")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StorageLocation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "storage_location_id") private Long id;
    @Column(nullable = false, length = 40) private String code;
    @Column(nullable = false, length = 150) private String name;
    @Column(name = "is_active", nullable = false) private boolean active;
    @Column(name = "parent_location_id") private Long parentId;
    public static StorageLocation create(String code, String name, Long parentId) {
        StorageLocation location = new StorageLocation();
        location.update(code, name, parentId, true); return location;
    }
    public void update(String code, String name, Long parentId, boolean active) {
        this.code = code; this.name = name; this.parentId = parentId; this.active = active;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public boolean isActive() { return active; }
}
