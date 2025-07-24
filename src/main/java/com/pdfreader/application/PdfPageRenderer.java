package com.pdfreader.application;

import javafx.scene.image.Image;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Service for rendering PDF pages as images for display in JavaFX.
 * Uses PDFBox for PDF rendering capabilities.
 */
@Service
public class PdfPageRenderer {
    
    private static final float DEFAULT_DPI = 150f; // Good balance between quality and performance
    private static final String IMAGE_FORMAT = "PNG";
    
    /**
     * Render a specific page of a PDF document as a JavaFX Image
     */
    public Image renderPage(String filePath, int pageNumber) throws IOException {
        return renderPage(filePath, pageNumber, DEFAULT_DPI);
    }
    
    /**
     * Render a specific page of a PDF document as a JavaFX Image with custom DPI
     */
    public Image renderPage(String filePath, int pageNumber, float dpi) throws IOException {
        try (PDDocument document = PDDocument.load(new File(filePath))) {
            if (pageNumber < 0 || pageNumber >= document.getNumberOfPages()) {
                throw new IllegalArgumentException("Page number " + pageNumber + " is out of range. Document has " + document.getNumberOfPages() + " pages.");
            }
            
            PDFRenderer renderer = new PDFRenderer(document);
            BufferedImage bufferedImage = renderer.renderImageWithDPI(pageNumber, dpi);
            
            return convertToJavaFXImage(bufferedImage);
        }
    }
    
    /**
     * Get the number of pages in a PDF document
     */
    public int getPageCount(String filePath) throws IOException {
        try (PDDocument document = PDDocument.load(new File(filePath))) {
            return document.getNumberOfPages();
        }
    }
    
    /**
     * Check if a file is a valid PDF
     */
    public boolean isValidPdf(String filePath) {
        try (PDDocument document = PDDocument.load(new File(filePath))) {
            return document.getNumberOfPages() > 0;
        } catch (IOException e) {
            return false;
        }
    }
    
    /**
     * Convert BufferedImage to JavaFX Image
     */
    private Image convertToJavaFXImage(BufferedImage bufferedImage) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(bufferedImage, IMAGE_FORMAT, outputStream);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        return new Image(inputStream);
    }
    
    /**
     * Render multiple pages for preloading (useful for smooth scrolling)
     */
    public Image[] renderPages(String filePath, int startPage, int endPage) throws IOException {
        return renderPages(filePath, startPage, endPage, DEFAULT_DPI);
    }
    
    /**
     * Render multiple pages with custom DPI
     */
    public Image[] renderPages(String filePath, int startPage, int endPage, float dpi) throws IOException {
        try (PDDocument document = PDDocument.load(new File(filePath))) {
            int pageCount = document.getNumberOfPages();
            startPage = Math.max(0, startPage);
            endPage = Math.min(pageCount - 1, endPage);
            
            if (startPage > endPage) {
                return new Image[0];
            }
            
            PDFRenderer renderer = new PDFRenderer(document);
            Image[] images = new Image[endPage - startPage + 1];
            
            for (int i = startPage; i <= endPage; i++) {
                BufferedImage bufferedImage = renderer.renderImageWithDPI(i, dpi);
                images[i - startPage] = convertToJavaFXImage(bufferedImage);
            }
            
            return images;
        }
    }
}
