package com.example.base.mapper;

import com.example.base.dto.post.PostDetailDto;
import com.example.base.dto.post.PostSummaryDto;
import com.example.base.entity.Post;
import com.example.base.storage.ImageStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostMapper {
    private final ImageStorage storage;

    public PostSummaryDto toSummary(Post post) {
        var category = post.getCategory();
        String thumbnail = null;
        if (!post.getImages().isEmpty()) {
            var image = post.getImages().getFirst();
            thumbnail = storage.readUrl(image.getStorageKey(), image.getImageUrl());
        }
        return new PostSummaryDto(post.getId(), post.getPostType(), post.getTitle(), category.getCategoryId(),
                category.getName(), post.getLocationText(), post.getEventAt(), post.getStatus(),
                post.getCreatedAt(), thumbnail);
    }

    public PostDetailDto toDetail(Post post) {
        return toDetail(post, null);
    }

    public PostDetailDto toDetail(Post post, Long viewerId) {
        var category = post.getCategory();
        var images = post.getImages().stream().map(image -> new PostDetailDto.ImageDto(image.getId(),
                storage.readUrl(image.getStorageKey(), image.getImageUrl()), image.getSortOrder())).toList();
        boolean isOwner = viewerId != null && viewerId.equals(post.getAccount().getId());
        return new PostDetailDto(post.getId(), post.getPostType(), post.getTitle(), post.getDescription(),
                category.getCategoryId(), category.getName(), post.getLocationText(), post.getLatitude(),
                post.getLongitude(),
                post.getEventAt(), post.getStatus(), post.getCustodyMode(), post.getCreatedAt(), images,
                isOwner, post.getModerationStatus(), isOwner ? post.getHandoverRequestedAt() : null);
    }
}
