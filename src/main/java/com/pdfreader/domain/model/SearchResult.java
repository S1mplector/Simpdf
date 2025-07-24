package com.pdfreader.domain.model;

/**
 * Represents a search result with context information
 */
public class SearchResult {
    private final String documentId;
    private final String documentTitle;
    private final String documentPath;
    private final int pageNumber;
    private final String matchedText;
    private final String contextBefore;
    private final String contextAfter;
    private final int startIndex;
    private final int endIndex;
    private final double relevanceScore;

    public SearchResult(String documentId, String documentTitle, String documentPath, 
                       int pageNumber, String matchedText, String contextBefore, 
                       String contextAfter, int startIndex, int endIndex, double relevanceScore) {
        this.documentId = documentId;
        this.documentTitle = documentTitle;
        this.documentPath = documentPath;
        this.pageNumber = pageNumber;
        this.matchedText = matchedText;
        this.contextBefore = contextBefore;
        this.contextAfter = contextAfter;
        this.startIndex = startIndex;
        this.endIndex = endIndex;
        this.relevanceScore = relevanceScore;
    }

    // Getters
    public String getDocumentId() { return documentId; }
    public String getDocumentTitle() { return documentTitle; }
    public String getDocumentPath() { return documentPath; }
    public int getPageNumber() { return pageNumber; }
    public String getMatchedText() { return matchedText; }
    public String getContextBefore() { return contextBefore; }
    public String getContextAfter() { return contextAfter; }
    public int getStartIndex() { return startIndex; }
    public int getEndIndex() { return endIndex; }
    public double getRelevanceScore() { return relevanceScore; }

    public String getFullContext() {
        return contextBefore + matchedText + contextAfter;
    }

    @Override
    public String toString() {
        return String.format("SearchResult{document='%s', page=%d, text='%s'}", 
                           documentTitle, pageNumber, matchedText);
    }
}
