package com.example.base.service.post;

import com.example.base.dto.post.OfficeReceiptDto;
import com.example.base.dto.post.PostPageDto;
import com.example.base.dto.post.PostSummaryDto;
import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import com.example.base.mapper.PostMapper;
import com.example.base.repository.ItemReceiptRepository;
import com.example.base.repository.PostRepository;
import com.example.base.repository.SavedPostRepository;
import com.example.base.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SavedPostService {
    private final SavedPostRepository saved;
    private final PostRepository posts;
    private final ItemReceiptRepository receipts;
    private final PostMapper mapper;

    @Transactional
    public void save(long postId, CustomUserDetails user) {
        long accountId = PostAccessPolicy.requireActiveAccount(user);
        PostAccessPolicy.requireValidPostId(postId);
        posts.findVisibleDetail(postId).orElseThrow(() -> new AppException(ErrorCode.POST_NOT_VISIBLE));
        saved.saveIfAbsent(accountId, postId);
    }

    @Transactional
    public void remove(long postId, CustomUserDetails user) {
        long accountId = PostAccessPolicy.requireActiveAccount(user);
        PostAccessPolicy.requireValidPostId(postId);
        // Removal remains possible after a post becomes unavailable, without exposing its content.
        saved.removeForAccount(accountId, postId);
    }

    @Transactional(readOnly = true)
    public List<Long> ids(CustomUserDetails user) {
        return saved.findVisiblePostIds(PostAccessPolicy.requireActiveAccount(user));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PostPageDto list(int page, int size, String postType, CustomUserDetails user) {
        long accountId = PostAccessPolicy.requireActiveAccount(user);
        if (page < 1 || size < 1 || size > 100) throw new AppException(ErrorCode.POST_PAGINATION_INVALID);
        if (postType == null || !List.of("", "LOST", "FOUND").contains(postType)) {
            throw new AppException(ErrorCode.POST_FILTER_INVALID);
        }
        var ids = saved.findVisiblePostIds(accountId, postType, PageRequest.of(page - 1, size));
        if (ids.isEmpty()) return new PostPageDto(List.of(), page, size, ids.getTotalElements(), ids.getTotalPages(), ids.isFirst(), ids.isLast());
        var byId = posts.findVisibleByIds(ids.getContent()).stream().collect(Collectors.toMap(p -> p.getId(), Function.identity()));
        Map<Long, OfficeReceiptDto> officeReceipts = new HashMap<>();
        for (var receipt : receipts.findPublicIntakes(ids.getContent())) {
            officeReceipts.put(receipt.getPostId(), OfficeReceiptDto.from(receipt));
        }
        List<PostSummaryDto> content = ids.getContent().stream()
                .map(id -> mapper.toSummary(byId.get(id), officeReceipts.get(id))).toList();
        return new PostPageDto(content, page, size, ids.getTotalElements(), ids.getTotalPages(), ids.isFirst(), ids.isLast());
    }
}
