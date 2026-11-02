package com.example.base.dto.post;

import java.util.Map;

public class SmartMatchItemDto {
    private Long id;
    private String title;
    private String postType;
    private String categoryName;
    private String locationText;
    private String eventAt;
    private String imageUrl;
    private String authorName;
    private String status;
    private double matchScore;
    private Map<String, Object> matchBreakdown;
    private String matchReason;

    public SmartMatchItemDto() {}

    public SmartMatchItemDto(Long id, String title, String postType, String categoryName,
                             String locationText, String eventAt, String imageUrl,
                             String authorName, String status, double matchScore,
                             Map<String, Object> matchBreakdown, String matchReason) {
        this.id = id;
        this.title = title;
        this.postType = postType;
        this.categoryName = categoryName;
        this.locationText = locationText;
        this.eventAt = eventAt;
        this.imageUrl = imageUrl;
        this.authorName = authorName;
        this.status = status;
        this.matchScore = matchScore;
        this.matchBreakdown = matchBreakdown;
        this.matchReason = matchReason;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getPostType() { return postType; }
    public void setPostType(String postType) { this.postType = postType; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public String getLocationText() { return locationText; }
    public void setLocationText(String locationText) { this.locationText = locationText; }

    public String getEventAt() { return eventAt; }
    public void setEventAt(String eventAt) { this.eventAt = eventAt; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public double getMatchScore() { return matchScore; }
    public void setMatchScore(double matchScore) { this.matchScore = matchScore; }

    public Map<String, Object> getMatchBreakdown() { return matchBreakdown; }
    public void setMatchBreakdown(Map<String, Object> matchBreakdown) { this.matchBreakdown = matchBreakdown; }

    public String getMatchReason() { return matchReason; }
    public void setMatchReason(String matchReason) { this.matchReason = matchReason; }
}
