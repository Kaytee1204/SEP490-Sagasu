package com.example.base.service.category;

import com.example.base.dto.PageResponse;
import com.example.base.dto.category.CategoryResponse;
import com.example.base.entity.Category;
import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import com.example.base.repository.CategoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    CategoryRepository repository;

    @InjectMocks
    CategoryService service;

    private Category cat(Long id, String name, boolean active) {
        Category c = new Category();
        ReflectionTestUtils.setField(c, "categoryId", id);
        ReflectionTestUtils.setField(c, "createdAt", OffsetDateTime.now().minusDays(1));
        ReflectionTestUtils.setField(c, "updatedAt", OffsetDateTime.now());
        c.setName(name);
        c.setActive(active);
        return c;
    }

    @Test
    void utc001_getCategories_returnsTwoItemsSortedDesc() {
        List<Category> list = List.of(cat(2L, "Ví da", true), cat(1L, "Ví nhỏ", true));
        Page<Category> page = new PageImpl<>(list);
        when(repository.search(eq("Ví"), eq(true), any(Pageable.class))).thenReturn(page);

        PageResponse<CategoryResponse> res = service.getCategories(1, 10, " Ví ", true);

        assertThat(res.getContent()).hasSize(2);
        ArgumentCaptor<Pageable> cap = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).search(eq("Ví"), eq(true), cap.capture());
        assertThat(cap.getValue().getPageNumber()).isEqualTo(0);
        assertThat(cap.getValue().getPageSize()).isEqualTo(10);
        assertThat(cap.getValue().getSort().getOrderFor("categoryId").getDirection())
                .isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void utc002_getCategories_nullKeyword_passesEmptyString() {
        when(repository.search(eq(""), isNull(), any(Pageable.class))).thenReturn(Page.empty());

        PageResponse<CategoryResponse> res = service.getCategories(1, 10, null, null);

        assertThat(res.getContent()).isEmpty();
        verify(repository).search(eq(""), isNull(), any(Pageable.class));
    }

    @Test
    void utc003_getCategories_pageZero_throwsBadRequest() {
        assertThatThrownBy(() -> service.getCategories(0, 10, null, null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException app = (AppException) ex;
                    assertThat(app.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
                    assertThat(app.getMessage()).isEqualTo("Trang phải từ 1; kích thước trang từ 1 đến 100");
                });
    }

    @Test
    void utc004_getCategories_size101_throwsBadRequest() {
        assertThatThrownBy(() -> service.getCategories(1, 101, null, null))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void utc005_getCategories_size100_accepted() {
        when(repository.search(eq(""), isNull(), any(Pageable.class))).thenReturn(Page.empty());

        PageResponse<CategoryResponse> res = service.getCategories(1, 100, null, null);

        assertThat(res.getSize()).isEqualTo(100);
        verify(repository).search(eq(""), isNull(), any(Pageable.class));
    }

    @Test
    void utc006_createCategory_collapsesSpacesAndStrips() {
        when(repository.existsByNameIgnoreCase("Điện thoại")).thenReturn(false);
        when(repository.saveAndFlush(any(Category.class))).thenAnswer(inv -> {
            Category c = inv.getArgument(0);
            ReflectionTestUtils.setField(c, "categoryId", 5L);
            return c;
        });

        CategoryResponse res = service.createCategory("  Điện   thoại  ");

        assertThat(res.name()).isEqualTo("Điện thoại");
        assertThat(res.categoryId()).isEqualTo(5L);
    }

    @Test
    void utc007_createCategory_existingInactiveName_throwsCategoryNameExists() {
        when(repository.existsByNameIgnoreCase("ví")).thenReturn(true);

        assertThatThrownBy(() -> service.createCategory("ví"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException app = (AppException) ex;
                    assertThat(app.getErrorCode()).isEqualTo(ErrorCode.CATEGORY_NAME_EXISTS);
                    assertThat(app.getMessage()).contains("Tên danh mục đã tồn tại");
                });
    }

    @Test
    void utc008_createCategory_blankName_throwsBadRequest() {
        assertThatThrownBy(() -> service.createCategory("   "))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException app = (AppException) ex;
                    assertThat(app.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
                    assertThat(app.getMessage()).isEqualTo("Tên danh mục phải có từ 1 đến 100 ký tự");
                });
    }

    @Test
    void utc009_createCategory_nullName_throwsBadRequest() {
        assertThatThrownBy(() -> service.createCategory(null))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void utc010_createCategory_100Chars_accepted() {
        String name = "a".repeat(100);
        when(repository.existsByNameIgnoreCase(name)).thenReturn(false);
        when(repository.saveAndFlush(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        CategoryResponse res = service.createCategory(name);

        assertThat(res.name()).hasSize(100);
    }

    @Test
    void utc011_createCategory_101Chars_throwsBadRequest() {
        String name = "a".repeat(101);

        assertThatThrownBy(() -> service.createCategory(name))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void utc012_createCategory_uniqueConstraintRace_throwsCategoryNameExists() {
        when(repository.existsByNameIgnoreCase("Ví")).thenReturn(false);
        when(repository.saveAndFlush(any(Category.class)))
                .thenThrow(new DataIntegrityViolationException("dup", new RuntimeException("constraint [uq_category_name]")));

        assertThatThrownBy(() -> service.createCategory("Ví"))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CATEGORY_NAME_EXISTS);
    }

    @Test
    void utc013_createCategory_otherIntegrityViolation_rethrows() {
        when(repository.existsByNameIgnoreCase("Ví")).thenReturn(false);
        DataIntegrityViolationException ex = new DataIntegrityViolationException("other", new RuntimeException("other_constraint"));
        when(repository.saveAndFlush(any(Category.class))).thenThrow(ex);

        assertThatThrownBy(() -> service.createCategory("Ví"))
                .isSameAs(ex);
    }

    @Test
    void utc014_updateCategory_valid_updatesName() {
        Category existing = cat(1L, "Old", true);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.existsByNameIgnoreCaseAndCategoryIdNot("Laptop", 1L)).thenReturn(false);
        when(repository.saveAndFlush(existing)).thenReturn(existing);

        CategoryResponse res = service.updateCategory(1L, "Laptop");

        assertThat(res.name()).isEqualTo("Laptop");
    }

    @Test
    void utc015_updateCategory_sameOwnName_accepted() {
        Category existing = cat(1L, "Laptop", true);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.existsByNameIgnoreCaseAndCategoryIdNot("Laptop", 1L)).thenReturn(false);
        when(repository.saveAndFlush(existing)).thenReturn(existing);

        CategoryResponse res = service.updateCategory(1L, "Laptop");

        assertThat(res.name()).isEqualTo("Laptop");
        verify(repository).existsByNameIgnoreCaseAndCategoryIdNot("Laptop", 1L);
    }

    @Test
    void utc016_updateCategory_duplicateNameOtherCategory_throwsCategoryNameExists() {
        Category existing = cat(1L, "Laptop", true);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.existsByNameIgnoreCaseAndCategoryIdNot("VÍ", 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.updateCategory(1L, "VÍ"))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CATEGORY_NAME_EXISTS);
    }

    @Test
    void utc017_updateCategory_notFound_throwsCategoryNotFound() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateCategory(999L, "X"))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CATEGORY_NOT_FOUND);
    }

    @Test
    void utc018_updateCategoryStatus_deactivate_savesFalse() {
        Category existing = cat(1L, "Ví", true);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(existing)).thenReturn(existing);

        CategoryResponse res = service.updateCategoryStatus(1L, false);

        assertThat(res.active()).isFalse();
        verify(repository).saveAndFlush(existing);
    }

    @Test
    void utc019_updateCategoryStatus_activate_savesTrue() {
        Category existing = cat(1L, "Ví", false);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(existing)).thenReturn(existing);

        CategoryResponse res = service.updateCategoryStatus(1L, true);

        assertThat(res.active()).isTrue();
        verify(repository).saveAndFlush(existing);
    }

    @Test
    void utc020_updateCategoryStatus_notFound_throwsCategoryNotFound() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateCategoryStatus(999L, true))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CATEGORY_NOT_FOUND);
    }
}

