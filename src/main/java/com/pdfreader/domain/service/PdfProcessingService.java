package com.pdfreader.domain.service;

import com.pdfreader.domain.model.PdfDocument;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Calendar;

@Service
public class PdfProcessingService {

    public PdfDocument processPdfFile(String filePath) throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IllegalArgumentException("File does not exist: " + filePath);
        }

        try (PDDocument document = PDDocument.load(file)) {
            PDDocumentInformation info = document.getDocumentInformation();
            
            return PdfDocument.builder()
                    .fileName(file.getName())
                    .filePath(filePath)
                    .fileSize(file.length())
                    .pageCount(document.getNumberOfPages())
                    .title(info.getTitle())
                    .author(info.getAuthor())
                    .subject(info.getSubject())
                    .keywords(info.getKeywords())
                    .createdAt(convertCalendarToLocalDateTime(info.getCreationDate()))
                    .modifiedAt(convertCalendarToLocalDateTime(info.getModificationDate()))
                    .build();
        }
    }

    public String extractTextFromPdf(String filePath) throws IOException {
        try (PDDocument document = PDDocument.load(new File(filePath))) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    public String extractTextFromPdf(String filePath, int startPage, int endPage) throws IOException {
        try (PDDocument document = PDDocument.load(new File(filePath))) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(startPage);
            stripper.setEndPage(endPage);
            return stripper.getText(document);
        }
    }

    private LocalDateTime convertCalendarToLocalDateTime(Calendar calendar) {
        if (calendar == null) {
            return LocalDateTime.now();
        }
        return LocalDateTime.ofInstant(calendar.toInstant(), ZoneId.systemDefault());
    }
}
