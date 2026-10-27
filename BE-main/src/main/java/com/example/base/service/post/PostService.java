package com.example.base.service.post;

import com.example.base.dto.post.PostDetailDto;
import com.example.base.dto.post.CategoryDto;
import com.example.base.repository.CategoryRepository;
import com.example.base.dto.post.PostPageDto;
import com.example.base.entity.Post;
import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import com.example.base.repository.PostRepository;
import com.example.base.mapper.PostMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PostPageDto listVisible(int page, int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw new AppException(ErrorCode.POST_PAGINATION_INVALID);
        }
        var ids = posts.findVisibleIds(PageRequest.of(page - 1, size));
        if (ids.isEmpty()) {
            return new PostPageDto(List.of(), page, size, ids.getTotalElements(), ids.getTotalPages(), ids.isFirst(),
                    ids.isLast());
        }
        var byId = posts.findVisibleByIds(ids.getContent()).stream()
                .collect(Collectors.toMap(Post::getId, Function.identity()));
        var summaries = ids.getContent().stream().map(byId::get).map(mapper::toSummary).toList();
        return new PostPageDto(summaries, page, size, ids.getTotalElements(), ids.getTotalPages(), ids.isFirst(),
                ids.isLast());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PostDetailDto getVisibleDetail(long id, Long viewerId) {
        if (id <= 0) {
            throw new AppException(ErrorCode.POST_ID_INVALID);
        }
        var post = posts.findVisibleDetail(id)
                .or(() -> viewerId == null ? java.util.Optional.empty() : posts.findOwnDetail(id, viewerId))
                .orElseThrow(() -> new AppException(ErrorCode.POST_NOT_VISIBLE));
        return mapper.toDetail(post, viewerId);
    }
}
