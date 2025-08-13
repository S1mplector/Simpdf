package com.pdfreader.application;

import com.pdfreader.domain.model.PdfDocument;
import com.pdfreader.domain.repository.PdfDocumentRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class PdfFolderScannerService {
    private static final Logger logger = LoggerFactory.getLogger(PdfFolderScannerService.class);
    
    private final PdfDocumentRepository documentRepository;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private ExecutorService scanExecutor;
    
    private Path watchedFolder;
    private WatchService watchService;
    private boolean isScanning = false;
    
    @Value("${pdf.allowed-extensions:.pdf}")
    private String allowedExtensionsProp;
    
    @Value("${pdf.max-file-size:10MB}")
    private String maxFileSizeProp;
    
    @Value("${pdf.scan.max-threads:4}")
    private int maxScanThreads;
    
    @Value("${pdf.scan.ignore-hidden:true}")
    private boolean ignoreHidden;
    
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
                
                // Initialize executor respecting configuration
                if (scanExecutor == null || scanExecutor.isShutdown()) {
                    scanExecutor = Executors.newFixedThreadPool(Math.max(1, maxScanThreads));
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
            
            long maxSizeBytes = parseSizeToBytes(maxFileSizeProp);
            var allowedExts = parseExtensions(allowedExtensionsProp);
            
            List<Path> files = Files.walk(watchedFolder)
                    .filter(p -> !ignoreHidden || !isHiddenPath(p))
                    .filter(Files::isRegularFile)
                    .filter(path -> hasAllowedExtension(path, allowedExts))
                    .filter(path -> sizeWithinLimit(path, maxSizeBytes))
                    .collect(Collectors.toList());

            // Process concurrently with bounded pool
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (Path p : files) {
                futures.add(CompletableFuture.runAsync(() -> processPdfFile(p), scanExecutor));
            }
            // Wait for completion
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                     
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
                                
                                if (hasAllowedExtension(fullPath, parseExtensions(allowedExtensionsProp))) {
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
            String fileId = generateStableFileId(pdfPath);
            
            long fileSize = Files.size(pdfPath);
            
            // Dedup/update by file path
            var existingByPath = documentRepository.findByFilePath(pdfPath.toString());
            if (existingByPath.isPresent()) {
                PdfDocument existing = existingByPath.get();
                // If size changed or we detect modification newer than our record, refresh metadata
                if (existing.getFileSize() != fileSize) {
                    logger.debug("Updating existing PDF metadata (size changed): {}", fileName);
                    refreshAndSavePdf(existing.getId(), pdfPath, fileName);
                } else {
                    logger.trace("No changes detected for: {}", fileName);
                }
                return;
            }
            
            // Extract PDF metadata
            saveNewPdf(fileId, pdfPath, fileName);
        } catch (Exception e) {
            logger.error("Failed to process PDF file: {}", pdfPath, e);
        }
    }
    
    private void saveNewPdf(String fileId, Path pdfPath, String fileName) throws IOException {
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
    }

    private void refreshAndSavePdf(String id, Path pdfPath, String fileName) throws IOException {
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
                id,
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
            logger.info("Updated PDF in library: {} ({} pages)", fileName, pageCount);
        }
    }
    
    /**
     * Generate a unique ID for a PDF file based on its path and last modified time
     */
    private String generateStableFileId(Path pdfPath) {
        // Stable ID based on normalized absolute path
        try {
            Path abs = pdfPath.toAbsolutePath().normalize();
            return Integer.toHexString(abs.toString().hashCode());
        } catch (Exception e) {
            return Integer.toHexString(pdfPath.toString().hashCode());
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
        return CompletableFuture.runAsync(this::scanFolderForPdfs, scanExecutor != null ? scanExecutor : Executors.newSingleThreadExecutor());
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
        // Do not shutdown scheduler to allow reuse; shutdown scan executor if present
        if (scanExecutor != null && !scanExecutor.isShutdown()) {
            scanExecutor.shutdown();
        }
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

    // Helpers
    private boolean hasAllowedExtension(Path path, Collection<String> allowed) {
        String name = path.getFileName().toString().toLowerCase();
        for (String ext : allowed) {
            if (name.endsWith(ext)) return true;
        }
        return false;
    }

    private boolean sizeWithinLimit(Path path, long maxBytes) {
        if (maxBytes <= 0) return true;
        try { return Files.size(path) <= maxBytes; } catch (IOException e) { return false; }
    }

    private boolean isHiddenPath(Path p) {
        try { return Files.isHidden(p) || p.getFileName().toString().startsWith("."); } catch (IOException e) { return false; }
    }

    private List<String> parseExtensions(String prop) {
        if (prop == null || prop.isBlank()) return List.of(".pdf");
        return List.of(prop.split(","))
                .stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .map(s -> s.startsWith(".") ? s : "." + s)
                .collect(Collectors.toList());
    }

    private long parseSizeToBytes(String prop) {
        if (prop == null || prop.isBlank()) return 0L;
        String s = prop.trim().toUpperCase();
        try {
            if (s.endsWith("KB")) return (long)(Double.parseDouble(s.replace("KB","")) * 1024);
            if (s.endsWith("MB")) return (long)(Double.parseDouble(s.replace("MB","")) * 1024 * 1024);
            if (s.endsWith("GB")) return (long)(Double.parseDouble(s.replace("GB","")) * 1024 * 1024 * 1024);
            if (s.endsWith("B")) return (long)Double.parseDouble(s.replace("B",""));
            return Long.parseLong(s);
        } catch (NumberFormatException ex) {
            logger.warn("Invalid size format '{}', ignoring limit", prop);
            return 0L;
        }
    }
}
