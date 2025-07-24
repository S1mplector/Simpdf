package com.pdfreader.domain.repository;

import com.pdfreader.domain.model.ReadingProgress;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for managing reading progress data.
 * Follows Repository pattern for data access abstraction.
 */
public interface ReadingProgressRepository {
    
    /**
     * Save or update reading progress
     */
    ReadingProgress save(ReadingProgress progress);
    
    /**
     * Find reading progress by document ID
     */
    Optional<ReadingProgress> findByDocumentId(String documentId);
    
    /**
     * Find all reading progress records
     */
    List<ReadingProgress> findAll();
    
    /**
     * Find recently read documents (ordered by last read time)
     */
    List<ReadingProgress> findRecentlyRead(int limit);
    
    /**
     * Delete reading progress by document ID
     */
    void deleteByDocumentId(String documentId);
    
    /**
     * Check if reading progress exists for a document
     */
    boolean existsByDocumentId(String documentId);
}
