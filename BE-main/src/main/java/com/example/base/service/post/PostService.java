package com.example.base.service.post;

import com.example.base.dto.post.CategoryDto;
import com.example.base.dto.post.PostDetailDto;
import com.example.base.dto.post.PostPageDto;
import com.example.base.dto.post.PostSummaryDto;
import com.example.base.dto.post.PostSearchFilter;
import com.example.base.entity.Post;
import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import com.example.base.mapper.PostMapper;
import com.example.base.repository.CategoryRepository;
import com.example.base.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PostService {
    private final PostRepository posts;
    private final PostMapper mapper;
    private final CategoryRepository categories;

    @Transactional(readOnly = true)
    public List<CategoryDto> getActiveCategories() {
        return categories.findByActiveTrueOrderByNameAscCategoryIdAsc().stream()
                .map(category -> new CategoryDto(category.getCategoryId(), category.getName()))
                .toList();
    }

    /** UC13: only VISIBLE posts that have not been soft-deleted. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PostPageDto listVisible(int page, int size) {
        return listVisible(page, size, PostSearchFilter.empty());
    }

    /** UC13/21: both LOST and FOUND are visible by default; filters never include unpublished posts. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PostPageDto listVisible(int page, int size, PostSearchFilter filter) {
        if (page < 1 || size < 1 || size > 100) {
            throw new AppException(ErrorCode.POST_PAGINATION_INVALID);
        }

        // Page the IDs first; fetching a collection of images directly would break DB pagination.
        var pageable = PageRequest.of(page - 1, size);
        var pageOfIds = filter.isEmpty() ? posts.findVisibleIds(pageable)
                : posts.searchVisibleIds(filter.keyword(), filter.categoryId(), filter.postType(),
                        filter.location(), filter.fromTime(), filter.untilTime(), pageable);
        Map<Long, Post> postsById = new HashMap<>();
        if (!pageOfIds.isEmpty()) {
            for (Post post : posts.findVisibleByIds(pageOfIds.getContent())) {
                postsById.put(post.getId(), post);
            }
        }

        List<PostSummaryDto> content = new ArrayList<>();
        for (Long postId : pageOfIds.getContent()) {
            content.add(mapper.toSummary(postsById.get(postId)));
        }
        return new PostPageDto(content, page, size, pageOfIds.getTotalElements(),
                pageOfIds.getTotalPages(), pageOfIds.isFirst(), pageOfIds.isLast());
    }

    /** UC14: others see VISIBLE posts; the owner can also review their own unpublished post. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PostDetailDto getDetailForViewer(long postId, Long viewerId) {
        PostAccessPolicy.requireValidPostId(postId);
        var result = posts.findVisibleDetail(postId);
        if (result.isEmpty() && viewerId != null) {
            result = posts.findOwnDetail(postId, viewerId);
        }
        Post post = result.orElseThrow(() -> new AppException(ErrorCode.POST_NOT_VISIBLE));
        return mapper.toDetail(post, viewerId);
    }
}
