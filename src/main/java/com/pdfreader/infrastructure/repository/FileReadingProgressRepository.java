package com.pdfreader.infrastructure.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pdfreader.domain.model.ReadingProgress;
import com.pdfreader.domain.repository.ReadingProgressRepository;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * File-based implementation of ReadingProgressRepository using JSON storage.
 * Persists reading progress to ensure bookmarks survive between application sessions.
 */
@Repository
public class FileReadingProgressRepository implements ReadingProgressRepository {
    
    private static final String DATA_DIR = System.getProperty("user.home") + File.separator + ".pdfreader";
    private static final String PROGRESS_FILE = "reading_progress.json";
    
    private final ObjectMapper objectMapper;
    private final ConcurrentMap<String, ReadingProgress> progressCache;
    private final Path dataDirectory;
    private final Path progressFile;
    
    public FileReadingProgressRepository() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.progressCache = new ConcurrentHashMap<>();
        this.dataDirectory = Paths.get(DATA_DIR);
        this.progressFile = dataDirectory.resolve(PROGRESS_FILE);
        
        initializeStorage();
        loadProgress();
    }
    
    private void initializeStorage() {
        try {
            if (!Files.exists(dataDirectory)) {
                Files.createDirectories(dataDirectory);
            }
            if (!Files.exists(progressFile)) {
                Files.createFile(progressFile);
                // Initialize with empty array
                objectMapper.writeValue(progressFile.toFile(), new ArrayList<ReadingProgress>());
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize reading progress storage", e);
        }
    }
    
    private void loadProgress() {
        try {
            if (Files.size(progressFile) > 0) {
                List<ReadingProgress> progressList = objectMapper.readValue(
                    progressFile.toFile(), 
                    new TypeReference<List<ReadingProgress>>() {}
                );
                
                progressCache.clear();
                for (ReadingProgress progress : progressList) {
                    progressCache.put(progress.getDocumentId(), progress);
                }
            }
        } catch (IOException e) {
            System.err.println("Warning: Failed to load reading progress from storage: " + e.getMessage());
            // Continue with empty cache
        }
    }
    
    private void saveProgress() {
        try {
            List<ReadingProgress> progressList = new ArrayList<>(progressCache.values());
            objectMapper.writeValue(progressFile.toFile(), progressList);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save reading progress to storage", e);
        }
    }
    
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
        
        progressCache.put(progress.getDocumentId(), progress);
        saveProgress();
        return progress;
    }
    
    @Override
    public Optional<ReadingProgress> findByDocumentId(String documentId) {
        return Optional.ofNullable(progressCache.get(documentId));
    }
    
    @Override
    public List<ReadingProgress> findAll() {
        return new ArrayList<>(progressCache.values());
    }
    
    @Override
    public List<ReadingProgress> findRecentlyRead(int limit) {
        return progressCache.values().stream()
                .sorted((p1, p2) -> p2.getLastReadAt().compareTo(p1.getLastReadAt()))
                .limit(limit)
                .collect(Collectors.toList());
    }
    
    @Override
    public void deleteByDocumentId(String documentId) {
        progressCache.remove(documentId);
        saveProgress();
    }
    
    @Override
    public boolean existsByDocumentId(String documentId) {
        return progressCache.containsKey(documentId);
    }
}
