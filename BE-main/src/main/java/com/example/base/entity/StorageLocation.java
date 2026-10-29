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
}
