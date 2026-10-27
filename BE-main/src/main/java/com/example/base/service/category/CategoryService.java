package com.example.base.service.category;

import com.example.base.dto.PageResponse;
import com.example.base.dto.category.CategoryResponse;
import com.example.base.entity.Category;
import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import com.example.base.repository.CategoryRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class CategoryService {
    private final CategoryRepository repository;

    public CategoryService(CategoryRepository repository) { this.repository = repository; }

    public PageResponse<CategoryResponse> getCategories(int page, int size, String keyword, Boolean active) {
        if (page < 1 || size < 1 || size > 100) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Trang phải từ 1; kích thước trang từ 1 đến 100");
        }
        var result = repository.search(keyword == null ? "" : keyword.strip(), active,
                PageRequest.of(page - 1, size, Sort.by("categoryId").descending()));
        return new PageResponse<>(result.map(CategoryResponse::from).getContent(), page, size,
                result.getTotalElements(), result.getTotalPages(), result.isFirst(), result.isLast());
    }

    @Transactional
    public CategoryResponse createCategory(String rawName) {
        String name = normalize(rawName);
        if (repository.existsByNameIgnoreCase(name)) throw duplicateName();
        Category category = new Category();
        category.setName(name);
        return save(category);
    }

    @Transactional
    public CategoryResponse updateCategory(Long id, String rawName) {
        Category category = find(id);
        String name = normalize(rawName);
        if (repository.existsByNameIgnoreCaseAndCategoryIdNot(name, id)) throw duplicateName();
        category.setName(name);
        return save(category);
    }

    @Transactional
    public CategoryResponse updateCategoryStatus(Long id, boolean active) {
        Category category = find(id);
        category.setActive(active);
        return save(category);
    }

    private Category find(Long id) {
        return repository.findById(id).orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));
    }

    private String normalize(String rawName) {
        String name = rawName == null ? "" : rawName.strip().replaceAll("(?U)\\s+", " ");
        if (name.isBlank() || name.length() > 100) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Tên danh mục phải có từ 1 đến 100 ký tự");
        }
        return name;
    }

    private AppException duplicateName() {
        return new AppException(ErrorCode.CATEGORY_NAME_EXISTS,
                "Tên danh mục đã tồn tại, kể cả danh mục ngừng hoạt động. Hãy dùng hoặc kích hoạt lại danh mục đó.");
    }

    private CategoryResponse save(Category category) {
        try {
            return CategoryResponse.from(repository.saveAndFlush(category));
        } catch (DataIntegrityViolationException ex) {
            // Handle concurrent requests that pass the pre-check but hit the SQL unique index.
            if (ex.getMostSpecificCause().getMessage() != null
                    && ex.getMostSpecificCause().getMessage().toLowerCase(Locale.ROOT).contains("uq_category_name")) {
                throw duplicateName();
            }
            throw ex;
        }
    }
}
