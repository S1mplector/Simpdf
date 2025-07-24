package com.pdfreader.domain.model;

import java.time.LocalDateTime;

/**
 * Criteria for searching PDF documents in the library
 */
public class LibrarySearchCriteria {
    private String query;
    private boolean searchTitle;
    private boolean searchAuthor;
    private boolean searchFilename;
    private boolean searchContent;
    private LocalDateTime createdAfter;
    private LocalDateTime createdBefore;
    private Integer minPages;
    private Integer maxPages;
    private Long minFileSize;
    private Long maxFileSize;
    private boolean caseSensitive;
    private int maxResults;

    public LibrarySearchCriteria() {
        // Default search settings
        this.searchTitle = true;
        this.searchAuthor = true;
        this.searchFilename = true;
        this.searchContent = false; // Content search is more expensive
        this.caseSensitive = false;
        this.maxResults = 100;
    }

    public LibrarySearchCriteria(String query) {
        this();
        this.query = query;
    }

    // Getters and Setters
    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public boolean isSearchTitle() { return searchTitle; }
    public void setSearchTitle(boolean searchTitle) { this.searchTitle = searchTitle; }

    public boolean isSearchAuthor() { return searchAuthor; }
    public void setSearchAuthor(boolean searchAuthor) { this.searchAuthor = searchAuthor; }

    public boolean isSearchFilename() { return searchFilename; }
    public void setSearchFilename(boolean searchFilename) { this.searchFilename = searchFilename; }

    public boolean isSearchContent() { return searchContent; }
    public void setSearchContent(boolean searchContent) { this.searchContent = searchContent; }

    public LocalDateTime getCreatedAfter() { return createdAfter; }
    public void setCreatedAfter(LocalDateTime createdAfter) { this.createdAfter = createdAfter; }

    public LocalDateTime getCreatedBefore() { return createdBefore; }
    public void setCreatedBefore(LocalDateTime createdBefore) { this.createdBefore = createdBefore; }

    public Integer getMinPages() { return minPages; }
    public void setMinPages(Integer minPages) { this.minPages = minPages; }

    public Integer getMaxPages() { return maxPages; }
    public void setMaxPages(Integer maxPages) { this.maxPages = maxPages; }

    public Long getMinFileSize() { return minFileSize; }
    public void setMinFileSize(Long minFileSize) { this.minFileSize = minFileSize; }

    public Long getMaxFileSize() { return maxFileSize; }
    public void setMaxFileSize(Long maxFileSize) { this.maxFileSize = maxFileSize; }

    public boolean isCaseSensitive() { return caseSensitive; }
    public void setCaseSensitive(boolean caseSensitive) { this.caseSensitive = caseSensitive; }

    public int getMaxResults() { return maxResults; }
    public void setMaxResults(int maxResults) { this.maxResults = maxResults; }

    public boolean hasQuery() {
        return query != null && !query.trim().isEmpty();
    }
}
