package com.pdfreader.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Represents a bookmark placed by the user at a specific location on a PDF page
 */
public class Bookmark {
    private final String id;
    private final String documentId;
    private final int pageNumber;
    private final double x; // X coordinate relative to page (0-1)
    private final double y; // Y coordinate relative to page (0-1)
    private final String title;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public Bookmark(String id, String documentId, int pageNumber, double x, double y, String title, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.documentId = documentId;
        this.pageNumber = pageNumber;
        this.x = x;
        this.y = y;
        this.title = title;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // Getters
    public String getId() { return id; }
    public String getDocumentId() { return documentId; }
    public int getPageNumber() { return pageNumber; }
    public double getX() { return x; }
    public double getY() { return y; }
    public String getTitle() { return title; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Bookmark bookmark = (Bookmark) o;
        return Objects.equals(id, bookmark.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Bookmark{" +
                "id='" + id + '\'' +
                ", documentId='" + documentId + '\'' +
                ", pageNumber=" + pageNumber +
                ", x=" + x +
                ", y=" + y +
                ", title='" + title + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }

    /**
     * Builder for creating Bookmark instances
     */
    public static class Builder {
        private String id;
        private String documentId;
        private int pageNumber;
        private double x;
        private double y;
        private String title = "Bookmark";
        private LocalDateTime createdAt = LocalDateTime.now();
        private LocalDateTime updatedAt = LocalDateTime.now();

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder documentId(String documentId) {
            this.documentId = documentId;
            return this;
        }

        public Builder pageNumber(int pageNumber) {
            this.pageNumber = pageNumber;
            return this;
        }

        public Builder position(double x, double y) {
            this.x = x;
            this.y = y;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public Bookmark build() {
            return new Bookmark(id, documentId, pageNumber, x, y, title, createdAt, updatedAt);
        }
    }
}
