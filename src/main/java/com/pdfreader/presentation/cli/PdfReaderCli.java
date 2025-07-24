package com.pdfreader.presentation.cli;

import com.pdfreader.application.PdfApplicationService;
import com.pdfreader.domain.model.PdfDocument;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

@Component
public class PdfReaderCli implements CommandLineRunner {

    private final PdfApplicationService pdfApplicationService;
    private final Environment environment;
    private final Scanner scanner;

    public PdfReaderCli(PdfApplicationService pdfApplicationService, Environment environment) {
        this.pdfApplicationService = pdfApplicationService;
        this.environment = environment;
        this.scanner = new Scanner(System.in);
    }

    @Override
    public void run(String... args) throws Exception {
        // Only run CLI if not in GUI mode
        String[] activeProfiles = environment.getActiveProfiles();
        if (Arrays.asList(activeProfiles).contains("gui")) {
            return; // Skip CLI when GUI profile is active
        }
        
        System.out.println("=== PDF Reader Application ===");
        System.out.println("Welcome to the PDF Reader CLI!");
        
        while (true) {
            showMenu();
            String choice = scanner.nextLine().trim();
            
            switch (choice) {
                case "1":
                    loadPdfDocument();
                    break;
                case "2":
                    listAllDocuments();
                    break;
                case "3":
                    extractTextFromDocument();
                    break;
                case "4":
                    extractTextFromPages();
                    break;
                case "5":
                    deleteDocument();
                    break;
                case "6":
                    System.out.println("Goodbye!");
                    return;
                default:
                    System.out.println("Invalid choice. Please try again.");
            }
        }
    }

    private void showMenu() {
        System.out.println("\n--- PDF Reader Menu ---");
        System.out.println("1. Load PDF Document");
        System.out.println("2. List All Documents");
        System.out.println("3. Extract Text from Document");
        System.out.println("4. Extract Text from Specific Pages");
        System.out.println("5. Delete Document");
        System.out.println("6. Exit");
        System.out.print("Choose an option: ");
    }

    private void loadPdfDocument() {
        System.out.print("Enter PDF file path: ");
        String filePath = scanner.nextLine().trim();
        
        try {
            PdfDocument document = pdfApplicationService.loadAndStorePdfDocument(filePath);
            System.out.println("PDF loaded successfully!");
            printDocumentInfo(document);
        } catch (Exception e) {
            System.err.println("Error loading PDF: " + e.getMessage());
        }
    }

    private void listAllDocuments() {
        List<PdfDocument> documents = pdfApplicationService.getAllDocuments();
        
        if (documents.isEmpty()) {
            System.out.println("No documents found.");
            return;
        }
        
        System.out.println("\n--- All Documents ---");
        for (PdfDocument doc : documents) {
            printDocumentInfo(doc);
            System.out.println("---");
        }
    }

    private void extractTextFromDocument() {
        System.out.print("Enter document ID: ");
        String id = scanner.nextLine().trim();
        
        try {
            String text = pdfApplicationService.extractTextFromDocument(id);
            System.out.println("\n--- Extracted Text ---");
            System.out.println(text.length() > 500 ? text.substring(0, 500) + "..." : text);
        } catch (Exception e) {
            System.err.println("Error extracting text: " + e.getMessage());
        }
    }

    private void extractTextFromPages() {
        System.out.print("Enter document ID: ");
        String id = scanner.nextLine().trim();
        System.out.print("Enter start page: ");
        int startPage = Integer.parseInt(scanner.nextLine().trim());
        System.out.print("Enter end page: ");
        int endPage = Integer.parseInt(scanner.nextLine().trim());
        
        try {
            String text = pdfApplicationService.extractTextFromDocument(id, startPage, endPage);
            System.out.println("\n--- Extracted Text (Pages " + startPage + "-" + endPage + ") ---");
            System.out.println(text.length() > 500 ? text.substring(0, 500) + "..." : text);
        } catch (Exception e) {
            System.err.println("Error extracting text: " + e.getMessage());
        }
    }

    private void deleteDocument() {
        System.out.print("Enter document ID: ");
        String id = scanner.nextLine().trim();
        
        try {
            pdfApplicationService.deleteDocument(id);
            System.out.println("Document deleted successfully!");
        } catch (Exception e) {
            System.err.println("Error deleting document: " + e.getMessage());
        }
    }

    private void printDocumentInfo(PdfDocument document) {
        System.out.println("ID: " + document.getId());
        System.out.println("File Name: " + document.getFileName());
        System.out.println("File Path: " + document.getFilePath());
        System.out.println("File Size: " + document.getFileSize() + " bytes");
        System.out.println("Page Count: " + document.getPageCount());
        System.out.println("Title: " + (document.getTitle() != null ? document.getTitle() : "N/A"));
        System.out.println("Author: " + (document.getAuthor() != null ? document.getAuthor() : "N/A"));
        System.out.println("Created: " + document.getCreatedAt());
    }
}
