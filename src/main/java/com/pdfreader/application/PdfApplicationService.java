package com.pdfreader.application;

import com.pdfreader.domain.model.PdfDocument;
import com.pdfreader.domain.service.PdfProcessingService;
import com.pdfreader.domain.service.PdfService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
public class PdfApplicationService {

    private final PdfService pdfService;
    private final PdfProcessingService pdfProcessingService;

    public PdfApplicationService(PdfService pdfService, PdfProcessingService pdfProcessingService) {
        this.pdfService = pdfService;
        this.pdfProcessingService = pdfProcessingService;
    }

    public PdfDocument loadAndStorePdfDocument(String filePath) throws IOException {
        System.out.println("Loading PDF document from: " + filePath);
        
        // Process the PDF file to extract metadata
        PdfDocument document = pdfProcessingService.processPdfFile(filePath);
        
        // Save to repository
        PdfDocument savedDocument = pdfService.savePdfDocument(document);
        
        System.out.println("Successfully loaded and stored PDF document with ID: " + savedDocument.getId());
        return savedDocument;
    }

    public String extractTextFromDocument(String documentId) throws IOException {
        PdfDocument document = pdfService.getPdfDocumentById(documentId);
        return pdfProcessingService.extractTextFromPdf(document.getFilePath());
    }

    public String extractTextFromDocument(String documentId, int startPage, int endPage) throws IOException {
        PdfDocument document = pdfService.getPdfDocumentById(documentId);
        return pdfProcessingService.extractTextFromPdf(document.getFilePath(), startPage, endPage);
    }

    public List<PdfDocument> getAllDocuments() {
        return pdfService.getAllPdfDocuments();
    }

    public PdfDocument getDocumentById(String id) {
        return pdfService.getPdfDocumentById(id);
    }

    public void deleteDocument(String id) {
        pdfService.deletePdfDocument(id);
    }
}
