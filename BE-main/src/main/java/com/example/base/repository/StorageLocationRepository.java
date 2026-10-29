package com.example.base.repository;

import com.example.base.entity.StorageLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StorageLocationRepository extends JpaRepository<StorageLocation, Long> {
    List<StorageLocation> findByActiveTrueOrderByNameAsc();
}
