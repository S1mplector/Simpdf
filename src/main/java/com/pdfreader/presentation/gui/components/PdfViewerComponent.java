package com.pdfreader.presentation.gui.components;

import com.pdfreader.application.PdfPageRenderer;
import com.pdfreader.application.ReadingProgressService;
import com.pdfreader.application.DocumentSearchService;
import com.pdfreader.application.LibrarySearchService;
import com.pdfreader.domain.model.PdfDocument;
import com.pdfreader.domain.model.ReadingProgress;
import com.pdfreader.domain.model.SearchResult;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Modular PDF viewer component that handles PDF rendering, navigation, and progress tracking.
 * Supports both single page and infinite scrolling view modes.
 * Uses Observer pattern for page change notifications and Command pattern for navigation actions.
 */
public class PdfViewerComponent extends BorderPane {
    
    public enum ViewMode {
        SINGLE_PAGE, INFINITE_SCROLL
    }
    
    private final PdfPageRenderer pageRenderer;
    private final ReadingProgressService progressService;
    private final DocumentSearchService documentSearchService;
    private final LibrarySearchService librarySearchService;
    private final ExecutorService renderingExecutor;
    
    // UI Components
    private ImageView pageImageView;
    private ScrollPane scrollPane;
    private VBox infiniteScrollContainer;
    private Label pageLabel;
    private Label progressLabel;
    private Button prevButton;
    private Button nextButton;
    private TextField pageField;
    private ProgressBar progressBar;
    private Slider zoomSlider;
    private ToggleButton viewModeToggle;
    private SearchComponent documentSearchComponent;
    
    // State
    private PdfDocument currentDocument;
    private int currentPage = 1;
    private int totalPages = 0;
    private double currentZoom = 1.0;
    private ReadingProgress readingProgress;
    private ViewMode currentViewMode = ViewMode.SINGLE_PAGE;
    
    // Infinite scroll state
    private Map<Integer, ImageView> renderedPages = new HashMap<>();
    private boolean isLoadingPages = false;
    
    // Observers for page changes
    private final List<PageChangeListener> pageChangeListeners = new ArrayList<>();
    
    public interface PageChangeListener {
        void onPageChanged(int newPage, int totalPages);
    }
    
    public PdfViewerComponent(PdfPageRenderer pageRenderer, ReadingProgressService progressService, 
                             DocumentSearchService documentSearchService, LibrarySearchService librarySearchService) {
        this.pageRenderer = pageRenderer;
        this.progressService = progressService;
        this.documentSearchService = documentSearchService;
        this.librarySearchService = librarySearchService;
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
        // Main image view for PDF pages (single page mode)
        pageImageView = new ImageView();
        pageImageView.setPreserveRatio(true);
        pageImageView.setSmooth(true);
        
        // Container for infinite scroll mode
        infiniteScrollContainer = new VBox(10);
        infiniteScrollContainer.setAlignment(Pos.CENTER);
        infiniteScrollContainer.setStyle("-fx-padding: 20;");
        
        // Scroll pane for navigation with centered content
        scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setPannable(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        
        // Initialize with single page mode
        setupSinglePageMode();
        
        // Navigation controls
        prevButton = new Button("◀ Previous");
        nextButton = new Button("Next ▶");
        pageField = new TextField();
        pageField.setPrefWidth(60);
        pageLabel = new Label("Page 0 of 0");
        
        // View mode toggle
        viewModeToggle = new ToggleButton("📄 Single Page");
        viewModeToggle.setSelected(true);
        viewModeToggle.setPrefWidth(120);
        
        // Document search component
        documentSearchComponent = new SearchComponent(SearchComponent.SearchMode.DOCUMENT_SEARCH, 
                                                    librarySearchService, documentSearchService);
        documentSearchComponent.setOnResultSelected(this::handleSearchResultSelected);
        
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
    
    private void setupSinglePageMode() {
        VBox centeringContainer = new VBox();
        centeringContainer.setAlignment(Pos.CENTER);
        centeringContainer.getChildren().add(pageImageView);
        scrollPane.setContent(centeringContainer);
    }
    
    private void setupInfiniteScrollMode() {
        scrollPane.setContent(infiniteScrollContainer);
    }
    
    private void setupLayout() {
        // Top toolbar
        HBox toolbar = new HBox(10);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.getChildren().addAll(
            viewModeToggle,
            new Separator(),
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
        // View mode toggle
        viewModeToggle.setOnAction(e -> toggleViewMode());
        
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
        
        // Keyboard navigation (only in single page mode)
        setOnKeyPressed(e -> {
            if (currentViewMode == ViewMode.SINGLE_PAGE) {
                if (e.getCode() == KeyCode.LEFT || e.getCode() == KeyCode.PAGE_UP) {
                    navigateToPreviousPage();
                } else if (e.getCode() == KeyCode.RIGHT || e.getCode() == KeyCode.PAGE_DOWN) {
                    navigateToNextPage();
                }
            }
        });
        
        // Mouse wheel zoom (Ctrl + wheel)
        scrollPane.setOnScroll(this::handleScrollZoom);
        
        // Auto-save progress on scroll
        scrollPane.vvalueProperty().addListener((obs, oldVal, newVal) -> {
            if (currentDocument != null) {
                if (currentViewMode == ViewMode.INFINITE_SCROLL) {
                    updateInfiniteScrollProgress();
                }
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
    
    private void toggleViewMode() {
        if (currentViewMode == ViewMode.SINGLE_PAGE) {
            switchToInfiniteScrollMode();
        } else {
            switchToSinglePageMode();
        }
    }
    
    private void switchToSinglePageMode() {
        currentViewMode = ViewMode.SINGLE_PAGE;
        viewModeToggle.setText("📄 Single Page");
        viewModeToggle.setSelected(true);
        
        // Update current page based on infinite scroll position before switching
        if (currentDocument != null && totalPages > 0) {
            double scrollPos = scrollPane.getVvalue();
            int pageFromScroll = Math.max(1, Math.min(totalPages, (int) Math.ceil(scrollPos * totalPages)));
            currentPage = pageFromScroll;
        }
        
        // Preserve current page image if it exists in rendered pages
        Image currentPageImage = null;
        if (renderedPages.containsKey(currentPage)) {
            currentPageImage = renderedPages.get(currentPage).getImage();
        }
        
        // Clear infinite scroll container
        infiniteScrollContainer.getChildren().clear();
        renderedPages.clear();
        
        // Setup single page mode
        setupSinglePageMode();
        
        // If we have the current page image, set it immediately to avoid blank page
        if (currentPageImage != null) {
            pageImageView.setImage(currentPageImage);
            updatePageDisplay();
        }
        
        // Enable navigation controls
        prevButton.setDisable(false);
        nextButton.setDisable(false);
        pageField.setDisable(false);
        
        // Update navigation controls with current page
        updateNavigationControls();
        
        // Render current page (this will refresh the image if needed, but don't clear it first)
        if (currentDocument != null) {
            renderCurrentPage(false);
        }
    }
    
    private void switchToInfiniteScrollMode() {
        currentViewMode = ViewMode.INFINITE_SCROLL;
        viewModeToggle.setText("📋 Infinite Scroll");
        viewModeToggle.setSelected(false);
        
        // Setup infinite scroll mode
        setupInfiniteScrollMode();
        
        // Disable page navigation controls (not needed in infinite scroll)
        prevButton.setDisable(true);
        nextButton.setDisable(true);
        pageField.setDisable(true);
        
        // Load all pages for infinite scroll
        if (currentDocument != null) {
            loadAllPagesForInfiniteScroll();
        }
    }
    
    private void loadAllPagesForInfiniteScroll() {
        if (isLoadingPages || currentDocument == null) return;
        
        isLoadingPages = true;
        infiniteScrollContainer.getChildren().clear();
        renderedPages.clear();
        
        Task<Void> loadTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                for (int pageNum = 1; pageNum <= totalPages; pageNum++) {
                    final int currentPageNum = pageNum;
                    
                    // Render page
                    Image pageImage = pageRenderer.renderPage(currentDocument.getFilePath(), pageNum - 1);
                    
                    Platform.runLater(() -> {
                        // Create image view for this page
                        ImageView pageView = new ImageView(pageImage);
                        pageView.setPreserveRatio(true);
                        pageView.setSmooth(true);
                        
                        // Apply zoom
                        double imageWidth = pageImage.getWidth() * currentZoom;
                        double imageHeight = pageImage.getHeight() * currentZoom;
                        pageView.setFitWidth(imageWidth);
                        pageView.setFitHeight(imageHeight);
                        
                        // Add page number label
                        Label pageNumLabel = new Label("Page " + currentPageNum);
                        pageNumLabel.setStyle("-fx-font-weight: bold; -fx-padding: 5; -fx-background-color: rgba(0,0,0,0.7); -fx-text-fill: white;");
                        
                        VBox pageContainer = new VBox(5);
                        pageContainer.setAlignment(Pos.CENTER);
                        pageContainer.getChildren().addAll(pageNumLabel, pageView);
                        
                        infiniteScrollContainer.getChildren().add(pageContainer);
                        renderedPages.put(currentPageNum, pageView);
                        
                        // Update progress as pages load
                        updateInfiniteScrollProgress();
                    });
                }
                return null;
            }
            
            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    isLoadingPages = false;
                    // Scroll to current page position
                    scrollToPageInInfiniteMode(currentPage);
                });
            }
            
            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    isLoadingPages = false;
                    showError("Failed to load pages for infinite scroll: " + getException().getMessage());
                });
            }
        };
        
        renderingExecutor.submit(loadTask);
    }
    
    private void scrollToPageInInfiniteMode(int pageNum) {
        if (currentViewMode != ViewMode.INFINITE_SCROLL || infiniteScrollContainer.getChildren().isEmpty()) {
            return;
        }
        
        // Calculate scroll position based on page number
        double scrollPosition = (double) (pageNum - 1) / totalPages;
        scrollPane.setVvalue(scrollPosition);
    }
    
    private void updateInfiniteScrollProgress() {
        // In infinite scroll mode, update progress based on scroll position
        if (currentViewMode == ViewMode.INFINITE_SCROLL) {
            double scrollPos = scrollPane.getVvalue();
            int estimatedPage = Math.max(1, Math.min(totalPages, (int) Math.ceil(scrollPos * totalPages)));
            
            // Update current page based on scroll position
            currentPage = estimatedPage;
            
            // Update progress display
            progressBar.setProgress(scrollPos);
            progressLabel.setText(String.format("%.1f%%", scrollPos * 100));
            pageLabel.setText(String.format("~%d of %d", estimatedPage, totalPages));
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
        renderCurrentPage(true);
    }
    
    private void renderCurrentPage(boolean showLoadingIndicator) {
        if (currentDocument == null) return;
        
        // Only show loading indicator if requested (avoid clearing image during mode switches)
        if (showLoadingIndicator) {
            pageImageView.setImage(null);
        }
        
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
        if (currentViewMode == ViewMode.SINGLE_PAGE) {
            updateSinglePageDisplay();
        } else {
            updateInfiniteScrollDisplay();
        }
    }
    
    private void updateSinglePageDisplay() {
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
    
    private void updateInfiniteScrollDisplay() {
        // Update zoom for all rendered pages in infinite scroll mode
        for (ImageView pageView : renderedPages.values()) {
            if (pageView.getImage() != null) {
                double imageWidth = pageView.getImage().getWidth() * currentZoom;
                double imageHeight = pageView.getImage().getHeight() * currentZoom;
                pageView.setFitWidth(imageWidth);
                pageView.setFitHeight(imageHeight);
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
    
    /**
     * Handle search result selection from the search component
     */
    private void handleSearchResultSelected(SearchResult searchResult) {
        try {
            // Navigate to the page containing the search result
            int targetPage = searchResult.getPageNumber();
            if (targetPage >= 1 && targetPage <= totalPages) {
                navigateToPage(targetPage);
                
                // Update search component with current document context
                if (currentDocument != null) {
                    documentSearchComponent.setCurrentDocument(currentDocument);
                }
            }
        } catch (Exception e) {
            showError("Failed to navigate to search result: " + e.getMessage());
        }
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
