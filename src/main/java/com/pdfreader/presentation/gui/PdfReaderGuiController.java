package com.pdfreader.presentation.gui;

import com.pdfreader.application.BookmarkService;
import com.pdfreader.application.DocumentSearchService;
import com.pdfreader.application.LibrarySearchService;
import com.pdfreader.application.PdfApplicationService;
import com.pdfreader.application.PdfFolderScannerService;
import com.pdfreader.application.PdfPageRenderer;
import com.pdfreader.application.ReadingProgressService;
import com.pdfreader.domain.model.PdfDocument;
import com.pdfreader.presentation.gui.components.DocumentLibraryComponent;
import com.pdfreader.presentation.gui.components.PdfViewerComponent;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * Main controller for the PDF Reader GUI application.
 * Uses modular components for document library and PDF viewing.
 * Follows MVC pattern with clear separation of concerns.
 */
@Component
public class PdfReaderGuiController implements Initializable {

    @Autowired
    private PdfApplicationService pdfApplicationService;
    
    @Autowired
    private final PdfPageRenderer pdfPageRenderer;
    private final ReadingProgressService readingProgressService;
    private final DocumentSearchService documentSearchService;
    private final LibrarySearchService librarySearchService;
    private final BookmarkService bookmarkService;
    
    @Autowired
    private PdfFolderScannerService pdfFolderScannerService;

    @FXML
    private BorderPane rootPane;
    
    @FXML
    private SplitPane mainSplitPane;
    
    @FXML
    private Label statusLabel;
    
    @FXML
    private Button backToLibraryButton;
    
    @FXML
    private ImageView mainLogoView;

    // Modular components
    private DocumentLibraryComponent documentLibrary;
    private PdfViewerComponent pdfViewer;
    
    // Current state
    private boolean isViewingDocument = false;

    @Autowired
    public PdfReaderGuiController(PdfPageRenderer pdfPageRenderer, ReadingProgressService readingProgressService,
                                  DocumentSearchService documentSearchService, LibrarySearchService librarySearchService,
                                  BookmarkService bookmarkService) {
        this.pdfPageRenderer = pdfPageRenderer;
        this.readingProgressService = readingProgressService;
        this.documentSearchService = documentSearchService;
        this.librarySearchService = librarySearchService;
        this.bookmarkService = bookmarkService;
    }
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initializeComponents();
        setupLayout();
        setupEventHandlers();
        showDocumentLibrary();
    }
    
    private void initializeComponents() {
        // Initialize modular components
        documentLibrary = new DocumentLibraryComponent(pdfApplicationService, readingProgressService, pdfFolderScannerService, librarySearchService, documentSearchService);
        pdfViewer = new PdfViewerComponent(pdfPageRenderer, readingProgressService, documentSearchService, librarySearchService, bookmarkService);
        
        // Configure main logo
        mainLogoView.setImage(createIcon("logo.png", 24, 24).getImage());
        
        // Configure back button with icon
        backToLibraryButton.setGraphic(createIcon("back_library.png", 20, 20));
        backToLibraryButton.setStyle(getUniformButtonStyle());
        backToLibraryButton.setVisible(false);
        backToLibraryButton.setManaged(false);
    }
    
    private void setupLayout() {
        // The main split pane will be managed programmatically
        mainSplitPane.getItems().clear();
    }
    
    private void setupEventHandlers() {
        // Document selection handler
        documentLibrary.setDocumentSelectionListener(this::openDocument);
        
        // Back to library button
        backToLibraryButton.setOnAction(e -> showDocumentLibrary());
        
        // PDF viewer page change listener
        pdfViewer.addPageChangeListener((page, total) -> {
            updateStatus(String.format("Page %d of %d", page, total));
        });
    }
    
    /**
     * Show the document library view
     */
    private void showDocumentLibrary() {
        mainSplitPane.getItems().clear();
        mainSplitPane.getItems().add(documentLibrary);
        
        backToLibraryButton.setVisible(false);
        backToLibraryButton.setManaged(false);
        
        isViewingDocument = false;
        updateStatus("Select a document to read");
        
        // Refresh the library when returning to it
        documentLibrary.refresh();
    }
    
    /**
     * Open a document for reading
     */
    private void openDocument(PdfDocument document) {
        try {
            mainSplitPane.getItems().clear();
            mainSplitPane.getItems().add(pdfViewer);
            
            // Load the document in the viewer
            pdfViewer.loadDocument(document);
            
            backToLibraryButton.setVisible(true);
            backToLibraryButton.setManaged(true);
            
            isViewingDocument = true;
            updateStatus("Reading: " + document.getFileName());
            
            // Focus the viewer for keyboard navigation
            pdfViewer.requestFocus();
            
        } catch (Exception e) {
            updateStatus("Error opening document: " + e.getMessage());
            showErrorAlert("Open Error", "Failed to open document", e.getMessage());
        }
    }
    
    /**
     * Update the status label
     */
    private void updateStatus(String message) {
        Platform.runLater(() -> statusLabel.setText(message));
    }
    
    /**
     * Helper method to create icons from the assets/icons directory
     */
    private ImageView createIcon(String iconName, int width, int height) {
        try {
            String iconPath = "/com/pdfreader/presentation/gui/components/assets/icons/" + iconName;
            Image icon = new Image(getClass().getResourceAsStream(iconPath));
            ImageView imageView = new ImageView(icon);
            imageView.setFitWidth(width);
            imageView.setFitHeight(height);
            imageView.setPreserveRatio(true);
            imageView.setSmooth(true);
            return imageView;
        } catch (Exception e) {
            System.err.println("Could not load icon: " + iconName + ", " + e.getMessage());
            return new ImageView(); // Return empty ImageView if icon fails to load
        }
    }
    
    /**
     * Get uniform button styling for consistent appearance
     */
    private String getUniformButtonStyle() {
        return "-fx-background-color: #C0C0C0; " +
               "-fx-text-fill: #333333; " +
               "-fx-border-color: #999999; " +
               "-fx-border-width: 1px; " +
               "-fx-border-radius: 4px; " +
               "-fx-background-radius: 4px; " +
               "-fx-padding: 8px 12px; " +
               "-fx-font-size: 12px; " +
               "-fx-cursor: hand;";
    }
    
    /**
     * Show error alert dialog
     */
    private void showErrorAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
    
    /**
     * Cleanup resources when the controller is destroyed
     */
    public void cleanup() {
        if (pdfViewer != null) {
            pdfViewer.cleanup();
        }
    }
    
    // Public API for external access
    public boolean isViewingDocument() {
        return isViewingDocument;
    }
    
    public PdfDocument getCurrentDocument() {
        return isViewingDocument ? pdfViewer.getCurrentDocument() : null;
    }
}
