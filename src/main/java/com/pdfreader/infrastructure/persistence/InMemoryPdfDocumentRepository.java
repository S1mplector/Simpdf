package com.pdfreader.infrastructure.persistence;

import com.pdfreader.domain.model.PdfDocument;
import com.pdfreader.domain.repository.PdfDocumentRepository;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryPdfDocumentRepository implements PdfDocumentRepository {
    
    private final Map<String, PdfDocument> documents = new ConcurrentHashMap<>();

    @Override
    public Optional<PdfDocument> findById(String id) {
        return Optional.ofNullable(documents.get(id));
    }

    @Override
    public List<PdfDocument> findAll() {
        return new ArrayList<>(documents.values());
    }

    @Override
    public PdfDocument save(PdfDocument document) {
        if (document.getId() == null) {
            document.setId(UUID.randomUUID().toString());
        }
        documents.put(document.getId(), document);
        return document;
    }

    @Override
    public void deleteById(String id) {
        documents.remove(id);
    }

    @Override
    public boolean existsById(String id) {
        return documents.containsKey(id);
    }
}
