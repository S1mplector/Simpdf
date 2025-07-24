package com.pdfreader.application;

import com.pdfreader.domain.model.LibrarySearchCriteria;
import com.pdfreader.domain.model.PdfDocument;
import com.pdfreader.domain.model.SearchResult;
import com.pdfreader.domain.service.PdfService;
import com.pdfreader.domain.service.PdfProcessingService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for searching PDF documents in the library
 */
@Service
public class LibrarySearchService {
    
    private final PdfService pdfService;
    private final PdfProcessingService pdfProcessingService;
    
    public LibrarySearchService(PdfService pdfService, PdfProcessingService pdfProcessingService) {
        this.pdfService = pdfService;
        this.pdfProcessingService = pdfProcessingService;
    }
    
    /**
     * Search PDF documents in the library based on criteria
     */
    public List<PdfDocument> searchLibrary(LibrarySearchCriteria criteria) {
        List<PdfDocument> allDocuments = pdfService.getAllPdfDocuments();
        
        if (!criteria.hasQuery()) {
            return applyFilters(allDocuments, criteria);
        }
        
        List<PdfDocument> matchingDocuments = new ArrayList<>();
        String query = criteria.isCaseSensitive() ? criteria.getQuery() : criteria.getQuery().toLowerCase();
        
        for (PdfDocument document : allDocuments) {
            if (documentMatches(document, query, criteria)) {
                matchingDocuments.add(document);
            }
        }
        
        // Apply additional filters
        matchingDocuments = applyFilters(matchingDocuments, criteria);
        
        // Sort by relevance (filename matches first, then title, then author)
        matchingDocuments.sort((d1, d2) -> {
            int score1 = calculateRelevanceScore(d1, query, criteria);
            int score2 = calculateRelevanceScore(d2, query, criteria);
            return Integer.compare(score2, score1); // Descending order
        });
        
        // Limit results
        return matchingDocuments.stream()
                .limit(criteria.getMaxResults())
                .collect(Collectors.toList());
    }
    
    /**
     * Search for text content across all documents in the library
     */
    public List<SearchResult> searchContentInLibrary(String query, boolean caseSensitive, int maxResults) throws IOException {
        List<SearchResult> allResults = new ArrayList<>();
        List<PdfDocument> allDocuments = pdfService.getAllPdfDocuments();
        
        if (query == null || query.trim().isEmpty()) {
            return allResults;
        }
        
        for (PdfDocument document : allDocuments) {
            try {
                // Search only first few pages for performance in library-wide search
                int maxPagesToSearch = Math.min(5, document.getPageCount());
                for (int pageNum = 1; pageNum <= maxPagesToSearch; pageNum++) {
                    String pageText = pdfProcessingService.extractTextFromPdf(document.getFilePath(), pageNum, pageNum);
                    List<SearchResult> pageResults = searchInText(document, pageText, query, pageNum, caseSensitive);
                    allResults.addAll(pageResults);
                    
                    // Stop if we have enough results for this document
                    if (pageResults.size() > 0 && allResults.size() >= maxResults) {
                        break;
                    }
                }
            } catch (IOException e) {
                System.err.println("Error searching in document: " + document.getFileName() + " - " + e.getMessage());
                // Continue with other documents
            }
            
            if (allResults.size() >= maxResults) {
                break;
            }
        }
        
        // Sort by relevance score
        allResults.sort(Comparator.comparingDouble(SearchResult::getRelevanceScore).reversed());
        
        return allResults.stream()
                .limit(maxResults)
                .collect(Collectors.toList());
    }
    
    /**
     * Check if a document matches the search criteria
     */
    private boolean documentMatches(PdfDocument document, String query, LibrarySearchCriteria criteria) {
        // Search in filename
        if (criteria.isSearchFilename()) {
            String filename = criteria.isCaseSensitive() ? document.getFileName() : document.getFileName().toLowerCase();
            if (filename.contains(query)) {
                return true;
            }
        }
        
        // Search in title
        if (criteria.isSearchTitle() && document.getTitle() != null) {
            String title = criteria.isCaseSensitive() ? document.getTitle() : document.getTitle().toLowerCase();
            if (title.contains(query)) {
                return true;
            }
        }
        
        // Search in author
        if (criteria.isSearchAuthor() && document.getAuthor() != null) {
            String author = criteria.isCaseSensitive() ? document.getAuthor() : document.getAuthor().toLowerCase();
            if (author.contains(query)) {
                return true;
            }
        }
        
        // Content search is expensive, so it's optional
        if (criteria.isSearchContent()) {
            try {
                // Search only first page for performance in library search
                String firstPageText = pdfProcessingService.extractTextFromPdf(document.getFilePath(), 1, 1);
                String textToSearch = criteria.isCaseSensitive() ? firstPageText : firstPageText.toLowerCase();
                if (textToSearch.contains(query)) {
                    return true;
                }
            } catch (IOException e) {
                System.err.println("Error searching content in document: " + document.getFileName());
            }
        }
        
        return false;
    }
    
    /**
     * Apply additional filters to documents
     */
    private List<PdfDocument> applyFilters(List<PdfDocument> documents, LibrarySearchCriteria criteria) {
        return documents.stream()
                .filter(doc -> {
                    // Date filters
                    if (criteria.getCreatedAfter() != null && doc.getCreatedAt().isBefore(criteria.getCreatedAfter())) {
                        return false;
                    }
                    if (criteria.getCreatedBefore() != null && doc.getCreatedAt().isAfter(criteria.getCreatedBefore())) {
                        return false;
                    }
                    
                    // Page count filters
                    if (criteria.getMinPages() != null && doc.getPageCount() < criteria.getMinPages()) {
                        return false;
                    }
                    if (criteria.getMaxPages() != null && doc.getPageCount() > criteria.getMaxPages()) {
                        return false;
                    }
                    
                    // File size filters
                    if (criteria.getMinFileSize() != null && doc.getFileSize() < criteria.getMinFileSize()) {
                        return false;
                    }
                    if (criteria.getMaxFileSize() != null && doc.getFileSize() > criteria.getMaxFileSize()) {
                        return false;
                    }
                    
                    return true;
                })
                .collect(Collectors.toList());
    }
    
    /**
     * Calculate relevance score for sorting
     */
    private int calculateRelevanceScore(PdfDocument document, String query, LibrarySearchCriteria criteria) {
        int score = 0;
        
        // Filename match gets highest score
        if (criteria.isSearchFilename()) {
            String filename = criteria.isCaseSensitive() ? document.getFileName() : document.getFileName().toLowerCase();
            if (filename.contains(query)) {
                score += 100;
                if (filename.startsWith(query)) {
                    score += 50; // Bonus for prefix match
                }
            }
        }
        
        // Title match gets high score
        if (criteria.isSearchTitle() && document.getTitle() != null) {
            String title = criteria.isCaseSensitive() ? document.getTitle() : document.getTitle().toLowerCase();
            if (title.contains(query)) {
                score += 80;
                if (title.startsWith(query)) {
                    score += 40; // Bonus for prefix match
                }
            }
        }
        
        // Author match gets medium score
        if (criteria.isSearchAuthor() && document.getAuthor() != null) {
            String author = criteria.isCaseSensitive() ? document.getAuthor() : document.getAuthor().toLowerCase();
            if (author.contains(query)) {
                score += 60;
            }
        }
        
        return score;
    }
    
    /**
     * Search within text content (reused from DocumentSearchService logic)
     */
    private List<SearchResult> searchInText(PdfDocument document, String text, String query, int pageNumber, boolean caseSensitive) {
        List<SearchResult> results = new ArrayList<>();
        
        if (text == null || text.isEmpty()) {
            return results;
        }
        
        String searchText = caseSensitive ? text : text.toLowerCase();
        String searchQuery = caseSensitive ? query : query.toLowerCase();
        
        int index = 0;
        while ((index = searchText.indexOf(searchQuery, index)) != -1) {
            String matchedText = text.substring(index, index + query.length());
            
            // Extract context
            int contextStart = Math.max(0, index - 50);
            int contextEnd = Math.min(text.length(), index + query.length() + 50);
            String contextBefore = text.substring(contextStart, index);
            String contextAfter = text.substring(index + query.length(), contextEnd);
            
            SearchResult result = new SearchResult(
                document.getId(),
                document.getTitle() != null ? document.getTitle() : document.getFileName(),
                document.getFilePath(),
                pageNumber,
                matchedText,
                contextBefore,
                contextAfter,
                index,
                index + query.length(),
                1.0 // Simple relevance score for library search
            );
            
            results.add(result);
            index += query.length();
        }
        
        return results;
    }
}
