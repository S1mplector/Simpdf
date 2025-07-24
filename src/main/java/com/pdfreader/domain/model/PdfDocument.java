package com.pdfreader.domain.model;

import java.time.LocalDateTime;

public class PdfDocument {
    private String id;
    private String fileName;
    private String filePath;
    private long fileSize;
    private int pageCount;
    private String title;
    private String author;
    private String subject;
    private String keywords;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;

    public PdfDocument() {}

    public PdfDocument(String id, String fileName, String filePath, long fileSize, int pageCount,
                      String title, String author, String subject, String keywords,
                      LocalDateTime createdAt, LocalDateTime modifiedAt) {
        this.id = id;
        this.fileName = fileName;
        this.filePath = filePath;
        this.fileSize = fileSize;
        this.pageCount = pageCount;
        this.title = title;
        this.author = author;
        this.subject = subject;
        this.keywords = keywords;
        this.createdAt = createdAt;
        this.modifiedAt = modifiedAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    public int getPageCount() { return pageCount; }
    public void setPageCount(int pageCount) { this.pageCount = pageCount; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getKeywords() { return keywords; }
    public void setKeywords(String keywords) { this.keywords = keywords; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getModifiedAt() { return modifiedAt; }
    public void setModifiedAt(LocalDateTime modifiedAt) { this.modifiedAt = modifiedAt; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String fileName;
        private String filePath;
        private long fileSize;
        private int pageCount;
        private String title;
        private String author;
        private String subject;
        private String keywords;
        private LocalDateTime createdAt;
        private LocalDateTime modifiedAt;

        public Builder id(String id) { this.id = id; return this; }
        public Builder fileName(String fileName) { this.fileName = fileName; return this; }
        public Builder filePath(String filePath) { this.filePath = filePath; return this; }
        public Builder fileSize(long fileSize) { this.fileSize = fileSize; return this; }
        public Builder pageCount(int pageCount) { this.pageCount = pageCount; return this; }
        public Builder title(String title) { this.title = title; return this; }
        public Builder author(String author) { this.author = author; return this; }
        public Builder subject(String subject) { this.subject = subject; return this; }
        public Builder keywords(String keywords) { this.keywords = keywords; return this; }
        public Builder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public Builder modifiedAt(LocalDateTime modifiedAt) { this.modifiedAt = modifiedAt; return this; }

        public PdfDocument build() {
            return new PdfDocument(id, fileName, filePath, fileSize, pageCount, title, author, subject, keywords, createdAt, modifiedAt);
        }
    }
}
