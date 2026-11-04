package com.example.base.repository;

import com.example.base.entity.StorageLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface StorageLocationRepository extends JpaRepository<StorageLocation, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select l from StorageLocation l order by l.id")
    List<StorageLocation> lockAll();
    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
    List<StorageLocation> findByActiveTrueOrderByNameAsc();
}
