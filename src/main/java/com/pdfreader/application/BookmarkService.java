package com.pdfreader.application;

import com.pdfreader.domain.model.Bookmark;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service for managing bookmarks in PDF documents
 */
@Service
public class BookmarkService {
    
    // In-memory storage for bookmarks (could be replaced with persistent storage later)
    private final Map<String, Bookmark> bookmarks = new ConcurrentHashMap<>();
    
    /**
     * Create a new bookmark
     */
    public Bookmark createBookmark(String documentId, int pageNumber, double x, double y, String title) {
        String bookmarkId = generateBookmarkId(documentId, pageNumber, x, y);
        
        Bookmark bookmark = new Bookmark.Builder()
                .id(bookmarkId)
                .documentId(documentId)
                .pageNumber(pageNumber)
                .position(x, y)
                .title(title != null ? title : "Bookmark")
                .build();
        
        bookmarks.put(bookmarkId, bookmark);
        return bookmark;
    }
    
    /**
     * Update an existing bookmark
     */
    public Bookmark updateBookmark(String bookmarkId, double x, double y, String title) {
        Bookmark existing = bookmarks.get(bookmarkId);
        if (existing == null) {
            throw new IllegalArgumentException("Bookmark not found: " + bookmarkId);
        }
        
        Bookmark updated = new Bookmark.Builder()
                .id(existing.getId())
                .documentId(existing.getDocumentId())
                .pageNumber(existing.getPageNumber())
                .position(x, y)
                .title(title != null ? title : existing.getTitle())
                .createdAt(existing.getCreatedAt())
                .updatedAt(LocalDateTime.now())
                .build();
        
        bookmarks.put(bookmarkId, updated);
        return updated;
    }
    
    /**
     * Delete a bookmark
     */
    public boolean deleteBookmark(String bookmarkId) {
        return bookmarks.remove(bookmarkId) != null;
    }
    
    /**
     * Get all bookmarks for a specific document
     */
    public List<Bookmark> getBookmarksForDocument(String documentId) {
        return bookmarks.values().stream()
                .filter(bookmark -> bookmark.getDocumentId().equals(documentId))
                .sorted(Comparator.comparing(Bookmark::getPageNumber)
                        .thenComparing(Bookmark::getY)
                        .thenComparing(Bookmark::getX))
                .collect(Collectors.toList());
    }
    
    /**
     * Get all bookmarks for a specific page of a document
     */
    public List<Bookmark> getBookmarksForPage(String documentId, int pageNumber) {
        return bookmarks.values().stream()
                .filter(bookmark -> bookmark.getDocumentId().equals(documentId) 
                        && bookmark.getPageNumber() == pageNumber)
                .sorted(Comparator.comparing(Bookmark::getY)
                        .thenComparing(Bookmark::getX))
                .collect(Collectors.toList());
    }
    
    /**
     * Get a specific bookmark by ID
     */
    public Optional<Bookmark> getBookmark(String bookmarkId) {
        return Optional.ofNullable(bookmarks.get(bookmarkId));
    }
    
    /**
     * Check if a bookmark exists at the specified location (within tolerance)
     */
    public Optional<Bookmark> findBookmarkAt(String documentId, int pageNumber, double x, double y, double tolerance) {
        return bookmarks.values().stream()
                .filter(bookmark -> bookmark.getDocumentId().equals(documentId) 
                        && bookmark.getPageNumber() == pageNumber
                        && Math.abs(bookmark.getX() - x) <= tolerance
                        && Math.abs(bookmark.getY() - y) <= tolerance)
                .findFirst();
    }
    
    /**
     * Get total number of bookmarks for a document
     */
    public int getBookmarkCount(String documentId) {
        return (int) bookmarks.values().stream()
                .filter(bookmark -> bookmark.getDocumentId().equals(documentId))
                .count();
    }
    
    /**
     * Clear all bookmarks for a document
     */
    public void clearBookmarksForDocument(String documentId) {
        bookmarks.entrySet().removeIf(entry -> 
                entry.getValue().getDocumentId().equals(documentId));
    }
    
    /**
     * Generate a unique bookmark ID
     */
    private String generateBookmarkId(String documentId, int pageNumber, double x, double y) {
        return String.format("bookmark_%s_%d_%.3f_%.3f_%d", 
                documentId, pageNumber, x, y, System.currentTimeMillis());
    }
}
