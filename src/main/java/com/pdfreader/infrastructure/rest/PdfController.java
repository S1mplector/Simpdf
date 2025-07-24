package com.pdfreader.infrastructure.rest;

import com.pdfreader.application.PdfApplicationService;
import com.pdfreader.domain.model.PdfDocument;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/pdf")
public class PdfController {

    private final PdfApplicationService pdfApplicationService;

    public PdfController(PdfApplicationService pdfApplicationService) {
        this.pdfApplicationService = pdfApplicationService;
    }

    @GetMapping
    public ResponseEntity<List<PdfDocument>> getAllPdfDocuments() {
        return ResponseEntity.ok(pdfApplicationService.getAllDocuments());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PdfDocument> getPdfDocumentById(@PathVariable String id) {
        try {
            return ResponseEntity.ok(pdfApplicationService.getDocumentById(id));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/load")
    public ResponseEntity<PdfDocument> loadPdfDocument(@RequestParam String filePath) {
        try {
            PdfDocument document = pdfApplicationService.loadAndStorePdfDocument(filePath);
            return ResponseEntity.ok(document);
        } catch (IOException e) {
            System.err.println("Error loading PDF document: " + e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{id}/text")
    public ResponseEntity<String> extractTextFromDocument(@PathVariable String id) {
        try {
            String text = pdfApplicationService.extractTextFromDocument(id);
            return ResponseEntity.ok(text);
        } catch (IOException e) {
            System.err.println("Error extracting text from PDF: " + e.getMessage());
            return ResponseEntity.internalServerError().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{id}/text/pages")
    public ResponseEntity<String> extractTextFromDocumentPages(
            @PathVariable String id,
            @RequestParam int startPage,
            @RequestParam int endPage) {
        try {
            String text = pdfApplicationService.extractTextFromDocument(id, startPage, endPage);
            return ResponseEntity.ok(text);
        } catch (IOException e) {
            System.err.println("Error extracting text from PDF pages: " + e.getMessage());
            return ResponseEntity.internalServerError().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePdfDocument(@PathVariable String id) {
        try {
            pdfApplicationService.deleteDocument(id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
