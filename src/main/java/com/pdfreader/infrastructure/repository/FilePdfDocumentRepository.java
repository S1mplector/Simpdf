package com.pdfreader.infrastructure.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pdfreader.domain.model.PdfDocument;
import com.pdfreader.domain.repository.PdfDocumentRepository;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * File-based implementation of PdfDocumentRepository using JSON storage.
 * Persists PDF document metadata to ensure data survives between application sessions.
 */
@Repository
public class FilePdfDocumentRepository implements PdfDocumentRepository {
    
    private static final String DATA_DIR = System.getProperty("user.home") + File.separator + ".pdfreader";
    private static final String DOCUMENTS_FILE = "documents.json";
    
    private final ObjectMapper objectMapper;
    private final ConcurrentMap<String, PdfDocument> documentCache;
    private final Path dataDirectory;
    private final Path documentsFile;
    
    public FilePdfDocumentRepository() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.documentCache = new ConcurrentHashMap<>();
        this.dataDirectory = Paths.get(DATA_DIR);
        this.documentsFile = dataDirectory.resolve(DOCUMENTS_FILE);
        
        initializeStorage();
        loadDocuments();
    }
    
    private void initializeStorage() {
        try {
            if (!Files.exists(dataDirectory)) {
                Files.createDirectories(dataDirectory);
            }
            if (!Files.exists(documentsFile)) {
                Files.createFile(documentsFile);
                // Initialize with empty array
                objectMapper.writeValue(documentsFile.toFile(), new ArrayList<PdfDocument>());
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize PDF document storage", e);
        }
    }
    
    private void loadDocuments() {
        try {
            if (Files.size(documentsFile) > 0) {
                List<PdfDocument> documents = objectMapper.readValue(
                    documentsFile.toFile(), 
                    new TypeReference<List<PdfDocument>>() {}
                );
                
                documentCache.clear();
                for (PdfDocument doc : documents) {
                    documentCache.put(doc.getId(), doc);
                }
            }
        } catch (IOException e) {
            System.err.println("Warning: Failed to load PDF documents from storage: " + e.getMessage());
            // Continue with empty cache
        }
    }
    
    private void saveDocuments() {
        try {
            List<PdfDocument> documents = new ArrayList<>(documentCache.values());
            objectMapper.writeValue(documentsFile.toFile(), documents);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save PDF documents to storage", e);
        }
    }
    
    @Override
    public PdfDocument save(PdfDocument document) {
        documentCache.put(document.getId(), document);
        saveDocuments();
        return document;
    }
    
    @Override
    public Optional<PdfDocument> findById(String id) {
        return Optional.ofNullable(documentCache.get(id));
    }
    
    @Override
    public List<PdfDocument> findAll() {
        return new ArrayList<>(documentCache.values());
    }
    
    @Override
    public void deleteById(String id) {
        documentCache.remove(id);
        saveDocuments();
    }
    
    @Override
    public boolean existsById(String id) {
        return documentCache.containsKey(id);
    }
    
    /**
     * Find document by file path (utility method, not part of interface)
     */
    @Override
    public Optional<PdfDocument> findByFilePath(String filePath) {
        return documentCache.values().stream()
                .filter(doc -> filePath.equals(doc.getFilePath()))
                .findFirst();
    }
    
    /**
     * Find documents by file name (utility method, not part of interface)
     */
    public List<PdfDocument> findByFileName(String fileName) {
        return documentCache.values().stream()
                .filter(doc -> fileName.equals(doc.getFileName()))
                .toList();
    }
    
    /**
     * Get document count (utility method, not part of interface)
     */
    public long count() {
        return documentCache.size();
    }
}
