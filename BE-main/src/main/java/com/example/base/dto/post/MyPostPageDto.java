package com.example.base.dto.post;

import java.util.List;

public record MyPostPageDto(List<MyPostDto> content, int page, int size, long totalElements,
        int totalPages, boolean first, boolean last) {}
