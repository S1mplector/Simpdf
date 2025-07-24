package com.pdfreader.domain.model;

import java.time.LocalDateTime;

/**
 * Domain model representing a user's reading progress for a PDF document.
 * Tracks the current page and last reading session.
 */
public class ReadingProgress {
    private String id;
    private String documentId;
    private int currentPage;
    private int totalPages;
    private double scrollPosition; // For fine-grained position within a page
    private LocalDateTime lastReadAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public ReadingProgress() {}

    public ReadingProgress(String id, String documentId, int currentPage, int totalPages, 
                          double scrollPosition, LocalDateTime lastReadAt, 
                          LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.documentId = documentId;
        this.currentPage = currentPage;
        this.totalPages = totalPages;
        this.scrollPosition = scrollPosition;
        this.lastReadAt = lastReadAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }

    public int getCurrentPage() { return currentPage; }
    public void setCurrentPage(int currentPage) { this.currentPage = currentPage; }

    public int getTotalPages() { return totalPages; }
    public void setTotalPages(int totalPages) { this.totalPages = totalPages; }

    public double getScrollPosition() { return scrollPosition; }
    public void setScrollPosition(double scrollPosition) { this.scrollPosition = scrollPosition; }

    public LocalDateTime getLastReadAt() { return lastReadAt; }
    public void setLastReadAt(LocalDateTime lastReadAt) { this.lastReadAt = lastReadAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    /**
     * Calculate reading progress as a percentage
     */
    public double getProgressPercentage() {
        if (totalPages <= 0) return 0.0;
        return ((double) currentPage / totalPages) * 100.0;
    }

    /**
     * Check if this is a new reading session (no previous progress)
     */
    public boolean isNewReading() {
        return currentPage <= 1 && scrollPosition == 0.0;
    }

    @Override
    public String toString() {
        return String.format("ReadingProgress{documentId='%s', page=%d/%d (%.1f%%), lastRead=%s}", 
                           documentId, currentPage, totalPages, getProgressPercentage(), lastReadAt);
    }
}
