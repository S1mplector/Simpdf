package com.pdfreader.application;

import com.pdfreader.domain.model.PdfDocument;
import com.pdfreader.domain.model.SearchResult;
import com.pdfreader.domain.service.PdfProcessingService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for searching text within PDF documents
 */
@Service
public class DocumentSearchService {
    
    private final PdfProcessingService pdfProcessingService;
    private static final int CONTEXT_LENGTH = 50; // Characters before and after match
    
    public DocumentSearchService(PdfProcessingService pdfProcessingService) {
        this.pdfProcessingService = pdfProcessingService;
    }
    
    /**
     * Search for text within a specific PDF document
     */
    public List<SearchResult> searchInDocument(PdfDocument document, String query, boolean caseSensitive) throws IOException {
        List<SearchResult> results = new ArrayList<>();
        
        if (query == null || query.trim().isEmpty()) {
            return results;
        }
        
        // Search page by page for better performance and page number tracking
        for (int pageNum = 1; pageNum <= document.getPageCount(); pageNum++) {
            String pageText = pdfProcessingService.extractTextFromPdf(document.getFilePath(), pageNum, pageNum);
            List<SearchResult> pageResults = searchInText(document, pageText, query, pageNum, caseSensitive);
            results.addAll(pageResults);
        }
        
        return results;
    }
    
    /**
     * Search for text within a specific page range of a PDF document
     */
    public List<SearchResult> searchInDocumentRange(PdfDocument document, String query, int startPage, int endPage, boolean caseSensitive) throws IOException {
        List<SearchResult> results = new ArrayList<>();
        
        if (query == null || query.trim().isEmpty()) {
            return results;
        }
        
        for (int pageNum = startPage; pageNum <= Math.min(endPage, document.getPageCount()); pageNum++) {
            String pageText = pdfProcessingService.extractTextFromPdf(document.getFilePath(), pageNum, pageNum);
            List<SearchResult> pageResults = searchInText(document, pageText, query, pageNum, caseSensitive);
            results.addAll(pageResults);
        }
        
        return results;
    }
    
    /**
     * Search for text within the currently displayed page
     */
    public List<SearchResult> searchInCurrentPage(PdfDocument document, String pageText, String query, int pageNumber, boolean caseSensitive) {
        if (query == null || query.trim().isEmpty() || pageText == null) {
            return new ArrayList<>();
        }
        
        return searchInText(document, pageText, query, pageNumber, caseSensitive);
    }
    
    /**
     * Internal method to search within text and create SearchResult objects
     */
    private List<SearchResult> searchInText(PdfDocument document, String text, String query, int pageNumber, boolean caseSensitive) {
        List<SearchResult> results = new ArrayList<>();
        
        if (text == null || text.isEmpty()) {
            return results;
        }
        
        // Create pattern for searching
        int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
        Pattern pattern = Pattern.compile(Pattern.quote(query), flags);
        Matcher matcher = pattern.matcher(text);
        
        while (matcher.find()) {
            int startIndex = matcher.start();
            int endIndex = matcher.end();
            String matchedText = matcher.group();
            
            // Extract context around the match
            String contextBefore = extractContextBefore(text, startIndex);
            String contextAfter = extractContextAfter(text, endIndex);
            
            // Calculate relevance score (simple implementation)
            double relevanceScore = calculateRelevanceScore(matchedText, query, contextBefore + contextAfter);
            
            SearchResult result = new SearchResult(
                document.getId(),
                document.getTitle() != null ? document.getTitle() : document.getFileName(),
                document.getFilePath(),
                pageNumber,
                matchedText,
                contextBefore,
                contextAfter,
                startIndex,
                endIndex,
                relevanceScore
            );
            
            results.add(result);
        }
        
        return results;
    }
    
    /**
     * Extract context before the match
     */
    private String extractContextBefore(String text, int startIndex) {
        int contextStart = Math.max(0, startIndex - CONTEXT_LENGTH);
        String context = text.substring(contextStart, startIndex);
        
        // Try to start at word boundary
        int spaceIndex = context.indexOf(' ');
        if (spaceIndex > 0 && contextStart > 0) {
            context = context.substring(spaceIndex + 1);
        }
        
        return context;
    }
    
    /**
     * Extract context after the match
     */
    private String extractContextAfter(String text, int endIndex) {
        int contextEnd = Math.min(text.length(), endIndex + CONTEXT_LENGTH);
        String context = text.substring(endIndex, contextEnd);
        
        // Try to end at word boundary
        int spaceIndex = context.lastIndexOf(' ');
        if (spaceIndex > 0 && contextEnd < text.length()) {
            context = context.substring(0, spaceIndex);
        }
        
        return context;
    }
    
    /**
     * Calculate relevance score for search result
     */
    private double calculateRelevanceScore(String matchedText, String query, String context) {
        double score = 1.0;
        
        // Exact match gets higher score
        if (matchedText.equalsIgnoreCase(query)) {
            score += 0.5;
        }
        
        // Matches in context get bonus
        String lowerContext = context.toLowerCase();
        String lowerQuery = query.toLowerCase();
        long contextMatches = lowerContext.split(lowerQuery, -1).length - 1;
        score += contextMatches * 0.1;
        
        return Math.min(score, 2.0); // Cap at 2.0
    }
    
    /**
     * Count total matches in document
     */
    public int countMatchesInDocument(PdfDocument document, String query, boolean caseSensitive) throws IOException {
        if (query == null || query.trim().isEmpty()) {
            return 0;
        }
        
        int totalMatches = 0;
        for (int pageNum = 1; pageNum <= document.getPageCount(); pageNum++) {
            String pageText = pdfProcessingService.extractTextFromPdf(document.getFilePath(), pageNum, pageNum);
            totalMatches += countMatchesInText(pageText, query, caseSensitive);
        }
        
        return totalMatches;
    }
    
    /**
     * Count matches in text
     */
    private int countMatchesInText(String text, String query, boolean caseSensitive) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        
        int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
        Pattern pattern = Pattern.compile(Pattern.quote(query), flags);
        Matcher matcher = pattern.matcher(text);
        
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        
        return count;
    }
}
