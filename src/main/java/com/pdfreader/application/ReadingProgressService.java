package com.pdfreader.application;

import com.pdfreader.domain.model.ReadingProgress;
import com.pdfreader.domain.repository.ReadingProgressRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Application service for managing reading progress.
 * Implements business logic for bookmark and resume functionality.
 */
@Service
public class ReadingProgressService {
    
    private final ReadingProgressRepository progressRepository;
    
    @Autowired
    public ReadingProgressService(ReadingProgressRepository progressRepository) {
        this.progressRepository = progressRepository;
    }
    
    /**
     * Update reading progress for a document
     */
    public ReadingProgress updateProgress(String documentId, int currentPage, int totalPages, double scrollPosition) {
        ReadingProgress progress = progressRepository.findByDocumentId(documentId)
                .orElse(new ReadingProgress());
        
        progress.setDocumentId(documentId);
        progress.setCurrentPage(Math.max(1, currentPage)); // Ensure page is at least 1
        progress.setTotalPages(totalPages);
        progress.setScrollPosition(scrollPosition);
        progress.setLastReadAt(LocalDateTime.now());
        
        return progressRepository.save(progress);
    }
    
    /**
     * Get reading progress for a document
     */
    public Optional<ReadingProgress> getProgress(String documentId) {
        return progressRepository.findByDocumentId(documentId);
    }
    
    /**
     * Get or create reading progress for a document
     */
    public ReadingProgress getOrCreateProgress(String documentId, int totalPages) {
        return progressRepository.findByDocumentId(documentId)
                .orElse(createNewProgress(documentId, totalPages));
    }
    
    /**
     * Create new reading progress starting from page 1
     */
    public ReadingProgress createNewProgress(String documentId, int totalPages) {
        ReadingProgress progress = new ReadingProgress();
        progress.setDocumentId(documentId);
        progress.setCurrentPage(1);
        progress.setTotalPages(totalPages);
        progress.setScrollPosition(0.0);
        progress.setLastReadAt(LocalDateTime.now());
        
        return progressRepository.save(progress);
    }
    
    /**
     * Get recently read documents
     */
    public List<ReadingProgress> getRecentlyRead(int limit) {
        return progressRepository.findRecentlyRead(limit);
    }
    
    /**
     * Delete reading progress for a document
     */
    public void deleteProgress(String documentId) {
        progressRepository.deleteByDocumentId(documentId);
    }
    
    /**
     * Check if document has reading progress
     */
    public boolean hasProgress(String documentId) {
        return progressRepository.existsByDocumentId(documentId);
    }
    
    /**
     * Get all reading progress records
     */
    public List<ReadingProgress> getAllProgress() {
        return progressRepository.findAll();
    }
}
