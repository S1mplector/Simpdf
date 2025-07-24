package com.pdfreader.domain.service;

import com.pdfreader.domain.model.PdfDocument;
import com.pdfreader.domain.repository.PdfDocumentRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PdfService {
    
    private final PdfDocumentRepository pdfDocumentRepository;

    public PdfService(PdfDocumentRepository pdfDocumentRepository) {
        this.pdfDocumentRepository = pdfDocumentRepository;
    }

    public PdfDocument savePdfDocument(PdfDocument document) {
        // Add any business logic here before saving
        return pdfDocumentRepository.save(document);
    }

    public List<PdfDocument> getAllPdfDocuments() {
        return pdfDocumentRepository.findAll();
    }

    public PdfDocument getPdfDocumentById(String id) {
        return pdfDocumentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("PDF document not found with id: " + id));
    }

    public void deletePdfDocument(String id) {
        if (!pdfDocumentRepository.existsById(id)) {
            throw new RuntimeException("PDF document not found with id: " + id);
        }
        pdfDocumentRepository.deleteById(id);
    }
}
