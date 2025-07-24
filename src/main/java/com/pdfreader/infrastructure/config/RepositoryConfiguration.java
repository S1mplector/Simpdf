package com.pdfreader.infrastructure.config;

import com.pdfreader.domain.repository.PdfDocumentRepository;
import com.pdfreader.domain.repository.ReadingProgressRepository;
import com.pdfreader.infrastructure.repository.FilePdfDocumentRepository;
import com.pdfreader.infrastructure.repository.FileReadingProgressRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configuration class for repository beans.
 * Ensures file-based repositories are used for persistence between sessions.
 */
@Configuration
public class RepositoryConfiguration {
    
    @Bean
    @Primary
    public PdfDocumentRepository pdfDocumentRepository() {
        return new FilePdfDocumentRepository();
    }
    
    @Bean
    @Primary
    public ReadingProgressRepository readingProgressRepository() {
        return new FileReadingProgressRepository();
    }
}
