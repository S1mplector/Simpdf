package com.pdfreader.application;

import com.pdfreader.domain.model.PdfDocument;
import com.pdfreader.domain.repository.PdfDocumentRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class PdfFolderScannerService {
    private static final Logger logger = LoggerFactory.getLogger(PdfFolderScannerService.class);
    
    private final PdfDocumentRepository documentRepository;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    private Path watchedFolder;
    private WatchService watchService;
    private boolean isScanning = false;
    
    @Autowired
    public PdfFolderScannerService(PdfDocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }
    
    /**
     * Set the master PDF folder to scan and start monitoring
     */
    public CompletableFuture<Void> setMasterFolder(String folderPath) {
        return CompletableFuture.runAsync(() -> {
            try {
                stopScanning();
                
                this.watchedFolder = Paths.get(folderPath);
                if (!Files.exists(watchedFolder) || !Files.isDirectory(watchedFolder)) {
                    throw new IllegalArgumentException("Invalid folder path: " + folderPath);
                }
                
                // Initial scan
                scanFolderForPdfs();
                
                // Start monitoring for changes
                startFolderMonitoring();
                
                logger.info("Started scanning PDF folder: {}", folderPath);
            } catch (Exception e) {
                logger.error("Failed to set master folder: {}", folderPath, e);
                throw new RuntimeException("Failed to set master folder", e);
            }
        });
    }
    
    /**
     * Perform initial scan of the folder for existing PDFs
     */
    private void scanFolderForPdfs() {
        if (watchedFolder == null) return;
        
        try {
            logger.info("Scanning folder for PDFs: {}", watchedFolder);
            
            Files.walk(watchedFolder)
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().toLowerCase().endsWith(".pdf"))
                    .forEach(this::processPdfFile);
                    
        } catch (IOException e) {
            logger.error("Error scanning folder for PDFs", e);
        }
    }
    
    /**
     * Start monitoring the folder for new/changed PDF files
     */
    private void startFolderMonitoring() {
        if (watchedFolder == null) return;
        
        try {
            watchService = FileSystems.getDefault().newWatchService();
            watchedFolder.register(watchService, 
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY);
            
            isScanning = true;
            
            // Start watching in a separate thread
            CompletableFuture.runAsync(() -> {
                while (isScanning) {
                    try {
                        WatchKey key = watchService.poll(1, TimeUnit.SECONDS);
                        if (key != null) {
                            for (WatchEvent<?> event : key.pollEvents()) {
                                Path changed = (Path) event.context();
                                Path fullPath = watchedFolder.resolve(changed);
                                
                                if (changed.toString().toLowerCase().endsWith(".pdf")) {
                                    // Delay processing to ensure file is fully written
                                    scheduler.schedule(() -> processPdfFile(fullPath), 2, TimeUnit.SECONDS);
                                }
                            }
                            key.reset();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        logger.error("Error monitoring folder", e);
                    }
                }
            });
            
        } catch (IOException e) {
            logger.error("Failed to start folder monitoring", e);
        }
    }
    
    /**
     * Process a single PDF file and add it to the repository if not already present
     */
    private void processPdfFile(Path pdfPath) {
        try {
            if (!Files.exists(pdfPath) || !Files.isRegularFile(pdfPath)) {
                return;
            }
            
            String fileName = pdfPath.getFileName().toString();
            String fileId = generateFileId(pdfPath);
            
            // Check if already exists
            if (documentRepository.existsById(fileId)) {
                logger.debug("PDF already exists in repository: {}", fileName);
                return;
            }
            
            // Extract PDF metadata
            try (PDDocument document = PDDocument.load(pdfPath.toFile())) {
                PDDocumentInformation info = document.getDocumentInformation();
                
                String title = info.getTitle();
                if (title == null || title.trim().isEmpty()) {
                    title = fileName.replaceAll("\\.pdf$", "");
                }
                
                String author = info.getAuthor();
                if (author == null) author = "Unknown";
                
                int pageCount = document.getNumberOfPages();
                
                long fileSize = Files.size(pdfPath);
                
                PdfDocument pdfDocument = new PdfDocument(
                    fileId,
                    fileName,
                    pdfPath.toString(),
                    fileSize,
                    pageCount,
                    title,
                    author,
                    info.getSubject(),
                    info.getKeywords(),
                    LocalDateTime.now(),
                    LocalDateTime.now()
                );
                
                documentRepository.save(pdfDocument);
                logger.info("Added new PDF to library: {} ({} pages)", fileName, pageCount);
                
            }
        } catch (Exception e) {
            logger.error("Failed to process PDF file: {}", pdfPath, e);
        }
    }
    
    /**
     * Generate a unique ID for a PDF file based on its path and last modified time
     */
    private String generateFileId(Path pdfPath) {
        try {
            long lastModified = Files.getLastModifiedTime(pdfPath).toMillis();
            return pdfPath.toString().hashCode() + "_" + lastModified;
        } catch (IOException e) {
            return pdfPath.toString().hashCode() + "_" + System.currentTimeMillis();
        }
    }
    
    /**
     * Get all PDFs currently in the repository
     */
    public List<PdfDocument> getAllPdfs() {
        return documentRepository.findAll();
    }
    
    /**
     * Manually trigger a rescan of the folder
     */
    public CompletableFuture<Void> rescanFolder() {
        return CompletableFuture.runAsync(this::scanFolderForPdfs);
    }
    
    /**
     * Stop scanning and monitoring
     */
    public void stopScanning() {
        isScanning = false;
        
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                logger.error("Error closing watch service", e);
            }
        }
        
        scheduler.shutdown();
    }
    
    /**
     * Get the currently watched folder path
     */
    public String getWatchedFolderPath() {
        return watchedFolder != null ? watchedFolder.toString() : null;
    }
    
    /**
     * Check if currently scanning a folder
     */
    public boolean isScanning() {
        return isScanning && watchedFolder != null;
    }
}
