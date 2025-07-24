package com.pdfreader.presentation.gui.components;

import com.pdfreader.application.PdfPageRenderer;
import com.pdfreader.application.ReadingProgressService;
import com.pdfreader.domain.model.PdfDocument;
import com.pdfreader.domain.model.ReadingProgress;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Modular PDF viewer component that handles PDF rendering, navigation, and progress tracking.
 * Uses Observer pattern for page change notifications and Command pattern for navigation actions.
 */
public class PdfViewerComponent extends BorderPane {
    
    private final PdfPageRenderer pageRenderer;
    private final ReadingProgressService progressService;
    private final ExecutorService renderingExecutor;
    
    // UI Components
    private ImageView pageImageView;
    private ScrollPane scrollPane;
    private Label pageLabel;
    private Label progressLabel;
    private Button prevButton;
    private Button nextButton;
    private TextField pageField;
    private ProgressBar progressBar;
    private Slider zoomSlider;
    
    // State
    private PdfDocument currentDocument;
    private int currentPage = 1;
    private int totalPages = 0;
    private double currentZoom = 1.0;
    private ReadingProgress readingProgress;
    
    // Observers for page changes
    private final List<PageChangeListener> pageChangeListeners = new ArrayList<>();
    
    public interface PageChangeListener {
        void onPageChanged(int newPage, int totalPages);
    }
    
    public PdfViewerComponent(PdfPageRenderer pageRenderer, ReadingProgressService progressService) {
        this.pageRenderer = pageRenderer;
        this.progressService = progressService;
        this.renderingExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "PDF-Renderer");
            t.setDaemon(true);
            return t;
        });
        
        initializeComponents();
        setupLayout();
        setupEventHandlers();
    }
    
    private void initializeComponents() {
        // Main image view for PDF pages
        pageImageView = new ImageView();
        pageImageView.setPreserveRatio(true);
        pageImageView.setSmooth(true);
        
        // Scroll pane for navigation with centered content
        scrollPane = new ScrollPane(pageImageView);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setPannable(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        
        // Center the image view within the scroll pane
        VBox centeringContainer = new VBox();
        centeringContainer.setAlignment(Pos.CENTER);
        centeringContainer.getChildren().add(pageImageView);
        scrollPane.setContent(centeringContainer);
        
        // Navigation controls
        prevButton = new Button("◀ Previous");
        nextButton = new Button("Next ▶");
        pageField = new TextField();
        pageField.setPrefWidth(60);
        pageLabel = new Label("Page 0 of 0");
        
        // Progress controls
        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(200);
        progressLabel = new Label("0%");
        
        // Zoom controls
        zoomSlider = new Slider(0.5, 3.0, 1.0);
        zoomSlider.setShowTickLabels(true);
        zoomSlider.setShowTickMarks(true);
        zoomSlider.setMajorTickUnit(0.5);
        zoomSlider.setPrefWidth(150);
    }
    
    private void setupLayout() {
        // Top toolbar
        HBox toolbar = new HBox(10);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.getChildren().addAll(
            prevButton, nextButton,
            new Separator(),
            new Label("Page:"), pageField, pageLabel,
            new Separator(),
            new Label("Progress:"), progressBar, progressLabel,
            new Separator(),
            new Label("Zoom:"), zoomSlider
        );
        toolbar.setStyle("-fx-padding: 10; -fx-background-color: #f0f0f0;");
        
        // Center content
        VBox centerContent = new VBox();
        centerContent.getChildren().add(scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        
        setTop(toolbar);
        setCenter(centerContent);
    }
    
    private void setupEventHandlers() {
        // Navigation buttons
        prevButton.setOnAction(e -> navigateToPreviousPage());
        nextButton.setOnAction(e -> navigateToNextPage());
        
        // Page field for direct navigation
        pageField.setOnAction(e -> navigateToPage());
        
        // Zoom slider
        zoomSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            currentZoom = newVal.doubleValue();
            updatePageDisplay();
        });
        
        // Keyboard navigation
        setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.LEFT || e.getCode() == KeyCode.PAGE_UP) {
                navigateToPreviousPage();
            } else if (e.getCode() == KeyCode.RIGHT || e.getCode() == KeyCode.PAGE_DOWN) {
                navigateToNextPage();
            }
        });
        
        // Mouse wheel zoom (Ctrl + wheel)
        scrollPane.setOnScroll(this::handleScrollZoom);
        
        // Auto-save progress on scroll
        scrollPane.vvalueProperty().addListener((obs, oldVal, newVal) -> {
            if (currentDocument != null) {
                saveCurrentProgress();
            }
        });
        
        // Handle scroll pane resize to maintain centering
        scrollPane.widthProperty().addListener((obs, oldVal, newVal) -> {
            updatePageDisplay();
        });
        
        scrollPane.heightProperty().addListener((obs, oldVal, newVal) -> {
            updatePageDisplay();
        });
        
        // Focus for keyboard events
        setFocusTraversable(true);
    }
    
    private void handleScrollZoom(ScrollEvent event) {
        if (event.isControlDown()) {
            event.consume();
            double deltaY = event.getDeltaY();
            if (deltaY > 0) {
                zoomSlider.setValue(Math.min(3.0, zoomSlider.getValue() + 0.1));
            } else {
                zoomSlider.setValue(Math.max(0.5, zoomSlider.getValue() - 0.1));
            }
        }
    }
    
    /**
     * Load a PDF document for viewing
     */
    public void loadDocument(PdfDocument document) {
        this.currentDocument = document;
        
        try {
            this.totalPages = pageRenderer.getPageCount(document.getFilePath());
            
            // Load or create reading progress
            this.readingProgress = progressService.getOrCreateProgress(document.getId(), totalPages);
            this.currentPage = readingProgress.getCurrentPage();
            
            updateNavigationControls();
            renderCurrentPage();
            
            // Restore scroll position
            Platform.runLater(() -> {
                scrollPane.setVvalue(readingProgress.getScrollPosition());
            });
            
        } catch (IOException e) {
            showError("Failed to load PDF: " + e.getMessage());
        }
    }
    
    private void navigateToPreviousPage() {
        if (currentPage > 1) {
            navigateToPage(currentPage - 1);
        }
    }
    
    private void navigateToNextPage() {
        if (currentPage < totalPages) {
            navigateToPage(currentPage + 1);
        }
    }
    
    private void navigateToPage() {
        try {
            int page = Integer.parseInt(pageField.getText().trim());
            navigateToPage(page);
        } catch (NumberFormatException e) {
            pageField.setText(String.valueOf(currentPage));
        }
    }
    
    private void navigateToPage(int page) {
        if (page >= 1 && page <= totalPages && page != currentPage) {
            currentPage = page;
            updateNavigationControls();
            renderCurrentPage();
            saveCurrentProgress();
            notifyPageChangeListeners();
        }
    }
    
    private void renderCurrentPage() {
        if (currentDocument == null) return;
        
        // Show loading indicator
        pageImageView.setImage(null);
        
        Task<Image> renderTask = new Task<Image>() {
            @Override
            protected Image call() throws Exception {
                return pageRenderer.renderPage(currentDocument.getFilePath(), currentPage - 1);
            }
            
            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    pageImageView.setImage(getValue());
                    updatePageDisplay();
                });
            }
            
            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    showError("Failed to render page: " + getException().getMessage());
                });
            }
        };
        
        renderingExecutor.submit(renderTask);
    }
    
    private void updatePageDisplay() {
        if (pageImageView.getImage() != null) {
            double imageWidth = pageImageView.getImage().getWidth() * currentZoom;
            double imageHeight = pageImageView.getImage().getHeight() * currentZoom;
            
            pageImageView.setFitWidth(imageWidth);
            pageImageView.setFitHeight(imageHeight);
            
            // Ensure the centering container maintains proper sizing
            VBox centeringContainer = (VBox) scrollPane.getContent();
            if (centeringContainer != null) {
                // Set minimum size to ensure centering works properly
                centeringContainer.setMinWidth(scrollPane.getWidth());
                centeringContainer.setMinHeight(scrollPane.getHeight());
                
                // Force layout update to ensure proper centering
                Platform.runLater(() -> {
                    centeringContainer.requestLayout();
                });
            }
        }
    }
    
    private void updateNavigationControls() {
        pageLabel.setText(String.format("of %d", totalPages));
        pageField.setText(String.valueOf(currentPage));
        
        prevButton.setDisable(currentPage <= 1);
        nextButton.setDisable(currentPage >= totalPages);
        
        // Update progress
        double progress = totalPages > 0 ? (double) currentPage / totalPages : 0;
        progressBar.setProgress(progress);
        progressLabel.setText(String.format("%.1f%%", progress * 100));
    }
    
    private void saveCurrentProgress() {
        if (currentDocument != null && readingProgress != null) {
            progressService.updateProgress(
                currentDocument.getId(),
                currentPage,
                totalPages,
                scrollPane.getVvalue()
            );
        }
    }
    
    private void notifyPageChangeListeners() {
        for (PageChangeListener listener : pageChangeListeners) {
            listener.onPageChanged(currentPage, totalPages);
        }
    }
    
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("PDF Viewer Error");
        alert.setHeaderText("Error");
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    // Public API
    public void addPageChangeListener(PageChangeListener listener) {
        pageChangeListeners.add(listener);
    }
    
    public void removePageChangeListener(PageChangeListener listener) {
        pageChangeListeners.remove(listener);
    }
    
    public int getCurrentPage() {
        return currentPage;
    }
    
    public int getTotalPages() {
        return totalPages;
    }
    
    public PdfDocument getCurrentDocument() {
        return currentDocument;
    }
    
    public void cleanup() {
        renderingExecutor.shutdown();
    }
}
