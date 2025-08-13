package com.pdfreader.domain.repository;

import com.pdfreader.domain.model.PdfDocument;

import java.util.List;
import java.util.Optional;

public interface PdfDocumentRepository {
    Optional<PdfDocument> findById(String id);
    List<PdfDocument> findAll();
    PdfDocument save(PdfDocument document);
    void deleteById(String id);
    boolean existsById(String id);
    
    /**
     * Find a document by its absolute file path, if the implementation supports it.
     */
    Optional<PdfDocument> findByFilePath(String filePath);
}
