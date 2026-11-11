package com.example.base.service.receipt;

import com.example.base.entity.StorageLocation;
import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import com.example.base.repository.StorageLocationRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReceiptLocationServiceTest {

    @Mock
    StorageLocationRepository locations;

    @Mock
    EntityManager entityManager;

    @InjectMocks
    ReceiptLocationService service;

    private StorageLocation loc(Long id, String code, String name, Long parentId, boolean active) {
        StorageLocation l = StorageLocation.create(code, name, parentId);
        l.update(code, name, parentId, active);
        ReflectionTestUtils.setField(l, "id", id);
        return l;
    }

    @Test
    void utc034_options_returnsThreeActiveSortedByName() {
        List<StorageLocation> active = List.of(
                loc(1L, "A1", "A - Kệ 1", null, true),
                loc(2L, "B1", "B - Kệ 2", null, true),
                loc(3L, "C1", "C - Kệ 3", null, true)
        );
        when(locations.findByActiveTrueOrderByNameAsc()).thenReturn(active);

        List<ReceiptLocationService.Option> res = service.options();

        assertThat(res).hasSize(3)
                .extracting(ReceiptLocationService.Option::name)
                .containsExactly("A - Kệ 1", "B - Kệ 2", "C - Kệ 3");
    }

    @Test
    void utc035_resolve_nullId_returnsNull() {
        StorageLocation existing = loc(1L, "A1", "Kệ A", null, true);

        StorageLocation res = service.resolve(null, existing);

        assertThat(res).isNull();
        verifyNoInteractions(locations);
    }

    @Test
    void utc036_resolve_activeWithActiveParent_returnsLocation() {
        StorageLocation parent = loc(1L, "P1", "Kho 1", null, true);
        StorageLocation child = loc(3L, "C3", "Kệ 3", 1L, true);
        when(locations.lockAll()).thenReturn(List.of(parent, child));

        StorageLocation res = service.resolve(3L, null);

        assertThat(res).isSameAs(child);
    }

    @Test
    void utc037_resolve_activeWithInactiveParent_throwsBadRequest() {
        StorageLocation parent = loc(1L, "P1", "Kho 1", null, false);
        StorageLocation child = loc(3L, "C3", "Kệ 3", 1L, true);
        when(locations.lockAll()).thenReturn(List.of(parent, child));

        assertThatThrownBy(() -> service.resolve(3L, null))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void utc038_resolve_missingLocation_throwsBadRequest() {
        when(locations.lockAll()).thenReturn(List.of());

        assertThatThrownBy(() -> service.resolve(999L, null))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void utc039_resolve_cycleInParents_throwsBadRequest() {
        StorageLocation l3 = loc(3L, "L3", "Kệ 3", 4L, true);
        StorageLocation l4 = loc(4L, "L4", "Kệ 4", 3L, true);
        when(locations.lockAll()).thenReturn(List.of(l3, l4));

        assertThatThrownBy(() -> service.resolve(3L, null))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }
}

