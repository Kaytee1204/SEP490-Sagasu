package com.example.base.dto.news;

public record NewsArticleRequest(
        String title,
        String summary,
        String content,
        String category,
        Boolean published,
        Boolean removeCoverImage
) {
}
