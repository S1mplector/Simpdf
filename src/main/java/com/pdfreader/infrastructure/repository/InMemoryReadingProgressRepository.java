package com.pdfreader.infrastructure.repository;

import com.pdfreader.domain.model.ReadingProgress;
import com.pdfreader.domain.repository.ReadingProgressRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of ReadingProgressRepository.
 * Uses ConcurrentHashMap for thread-safety.
 * Note: Not used as primary repository - file-based repository is preferred.
 */
public class InMemoryReadingProgressRepository implements ReadingProgressRepository {
    
    private final Map<String, ReadingProgress> progressStore = new ConcurrentHashMap<>();
    
    @Override
    public ReadingProgress save(ReadingProgress progress) {
        if (progress.getId() == null) {
            progress.setId(UUID.randomUUID().toString());
        }
        
        LocalDateTime now = LocalDateTime.now();
        if (progress.getCreatedAt() == null) {
            progress.setCreatedAt(now);
        }
        progress.setUpdatedAt(now);
        
        progressStore.put(progress.getDocumentId(), progress);
        return progress;
    }
    
    @Override
    public Optional<ReadingProgress> findByDocumentId(String documentId) {
        return Optional.ofNullable(progressStore.get(documentId));
    }
    
    @Override
    public List<ReadingProgress> findAll() {
        return new ArrayList<>(progressStore.values());
    }
    
    @Override
    public List<ReadingProgress> findRecentlyRead(int limit) {
        return progressStore.values().stream()
                .sorted((p1, p2) -> p2.getLastReadAt().compareTo(p1.getLastReadAt()))
                .limit(limit)
                .collect(Collectors.toList());
    }
    
    @Override
    public void deleteByDocumentId(String documentId) {
        progressStore.remove(documentId);
    }
    
    @Override
    public boolean existsByDocumentId(String documentId) {
        return progressStore.containsKey(documentId);
    }
}
