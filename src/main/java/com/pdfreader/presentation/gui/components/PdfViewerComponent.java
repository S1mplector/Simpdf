package com.pdfreader.presentation.gui.components;

import com.pdfreader.application.BookmarkService;
import com.pdfreader.application.DocumentSearchService;
import com.pdfreader.application.LibrarySearchService;
import com.pdfreader.application.PdfPageRenderer;
import com.pdfreader.application.ReadingProgressService;
import com.pdfreader.domain.model.Bookmark;
import com.pdfreader.domain.model.PdfDocument;
import com.pdfreader.domain.model.ReadingProgress;
import com.pdfreader.domain.model.SearchResult;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.scene.Cursor;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
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
    private final BookmarkService bookmarkService;
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
    private ToggleButton bookmarkButton;
    private boolean bookmarkPlacementMode = false;
    private ImageView cursorBookmarkIcon;
    private StackPane bookmarkOverlay;
    
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
                             DocumentSearchService documentSearchService, LibrarySearchService librarySearchService,
                             BookmarkService bookmarkService) {
        this.pageRenderer = pageRenderer;
        this.progressService = progressService;
        this.documentSearchService = documentSearchService;
        this.librarySearchService = librarySearchService;
        this.bookmarkService = bookmarkService;
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
        scrollPane.setFitToWidth(false);  // Allow horizontal scrolling when zoomed
        scrollPane.setFitToHeight(false); // Allow vertical scrolling when zoomed
        scrollPane.setPannable(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        
        // Initialize with single page mode
        setupSinglePageMode();
        
        // Navigation controls
        prevButton = new Button("◀ Previous");
        prevButton.setStyle(getUniformButtonStyle());
        nextButton = new Button("Next ▶");
        nextButton.setStyle(getUniformButtonStyle());
        pageField = new TextField();
        pageField.setPrefWidth(60);
        pageLabel = new Label("Page 0 of 0");
        
        // View mode toggle
        viewModeToggle = new ToggleButton("📄 Single Page");
        viewModeToggle.setStyle(getUniformButtonStyle());
        viewModeToggle.setSelected(true);
        viewModeToggle.setPrefWidth(120);
        
        // Document search component
        documentSearchComponent = new SearchComponent(SearchComponent.SearchMode.DOCUMENT_SEARCH, 
                                                    librarySearchService, documentSearchService);
        documentSearchComponent.setOnResultSelected(this::handleSearchResultSelected);
        
        // Initialize bookmark overlay
        bookmarkOverlay = new StackPane();
        bookmarkOverlay.setMouseTransparent(true); // Allow clicks to pass through to content below
        bookmarkOverlay.setPickOnBounds(false);
        
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
        
        // Bookmark button
        bookmarkButton = new ToggleButton("🔖");
        bookmarkButton.setStyle(getUniformButtonStyle());
        bookmarkButton.setTooltip(new Tooltip("Add Bookmark"));
        bookmarkButton.setOnAction(e -> toggleBookmarkMode());
        
        // Initialize bookmark overlay
        bookmarkOverlay = new StackPane();
        bookmarkOverlay.setMouseTransparent(false);
        bookmarkOverlay.setPickOnBounds(false);
    }
    
    private void setupSinglePageMode() {
        VBox centeringContainer = new VBox();
        centeringContainer.setAlignment(Pos.CENTER);
        centeringContainer.getChildren().add(pageImageView);
        
        // Allow the container to grow beyond the scroll pane size for proper scrolling
        centeringContainer.setFillWidth(false);
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
            bookmarkButton,
            new Separator(),
            new Label("Progress:"), progressBar, progressLabel,
            new Separator(),
            new Label("Zoom:"), zoomSlider
        );
        toolbar.setStyle("-fx-padding: 10; -fx-background-color: #f0f0f0;");
        
        // Center content with bookmark overlay
        VBox centerContent = new VBox();
        
        // Create a stack pane to overlay bookmarks on the scroll pane
        StackPane contentWithBookmarks = new StackPane();
        contentWithBookmarks.getChildren().addAll(scrollPane, bookmarkOverlay);
        
        centerContent.getChildren().add(contentWithBookmarks);
        VBox.setVgrow(contentWithBookmarks, Priority.ALWAYS);
        
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
        
        // Setup lazy loading for infinite scroll
        if (currentDocument != null) {
            setupLazyLoadingForInfiniteScroll();
        }
    }
    
    private void setupLazyLoadingForInfiniteScroll() {
        if (isLoadingPages || currentDocument == null) return;
        
        isLoadingPages = true;
        infiniteScrollContainer.getChildren().clear();
        renderedPages.clear();
        
        // Create placeholder containers for all pages
        for (int pageNum = 1; pageNum <= totalPages; pageNum++) {
            VBox pageContainer = createPagePlaceholder(pageNum);
            infiniteScrollContainer.getChildren().add(pageContainer);
        }
        
        isLoadingPages = false;
        
        // Set up scroll listener for lazy loading
        setupScrollListener();
        
        // Load initial visible pages
        Platform.runLater(() -> {
            loadVisiblePages();
        });
    }
    
    /**
     * Create a placeholder container for a page that will be lazily loaded
     */
    private VBox createPagePlaceholder(int pageNum) {
        // Estimate page dimensions (will be adjusted when actually loaded)
        double estimatedWidth = 600 * currentZoom;
        double estimatedHeight = 800 * currentZoom;
        
        // Create placeholder rectangle
        Rectangle placeholder = new Rectangle(estimatedWidth, estimatedHeight);
        placeholder.setFill(Color.LIGHTGRAY);
        placeholder.setStroke(Color.GRAY);
        placeholder.setStrokeWidth(1);
        
        // Add loading text
        Text loadingText = new Text("Loading Page " + pageNum + "...");
        loadingText.setFill(Color.DARKGRAY);
        
        StackPane placeholderStack = new StackPane();
        placeholderStack.getChildren().addAll(placeholder, loadingText);
        
        // Add page number label
        Label pageNumLabel = new Label("Page " + pageNum);
        pageNumLabel.setStyle("-fx-font-weight: bold; -fx-padding: 5; -fx-background-color: rgba(0,0,0,0.7); -fx-text-fill: white;");
        
        VBox pageContainer = new VBox(5);
        pageContainer.setAlignment(Pos.CENTER);
        pageContainer.getChildren().addAll(pageNumLabel, placeholderStack);
        pageContainer.setUserData(pageNum); // Store page number for identification
        
        return pageContainer;
    }
    
    /**
     * Set up scroll listener to trigger lazy loading of visible pages
     */
    private void setupScrollListener() {
        // Remove existing listener if any to avoid duplicates
        // Note: In a production app, you'd want to store and manage listeners properly
        
        scrollPane.vvalueProperty().addListener((obs, oldVal, newVal) -> {
            if (currentViewMode == ViewMode.INFINITE_SCROLL) {
                loadVisiblePages();
                updateInfiniteScrollProgress();
            }
        });
    }
    
    /**
     * Load pages that are currently visible in the viewport
     */
    private void loadVisiblePages() {
        if (currentDocument == null || infiniteScrollContainer.getChildren().isEmpty()) {
            return;
        }
        
        double scrollPaneHeight = scrollPane.getHeight();
        double scrollValue = scrollPane.getVvalue();
        double contentHeight = infiniteScrollContainer.getHeight();
        
        // Calculate visible range with buffer
        double visibleTop = scrollValue * (contentHeight - scrollPaneHeight);
        double visibleBottom = visibleTop + scrollPaneHeight;
        double buffer = scrollPaneHeight; // Load pages one viewport ahead/behind
        
        double currentY = 0;
        for (int i = 0; i < infiniteScrollContainer.getChildren().size(); i++) {
            VBox pageContainer = (VBox) infiniteScrollContainer.getChildren().get(i);
            Integer pageNum = (Integer) pageContainer.getUserData();
            
            double pageHeight = pageContainer.getHeight();
            if (pageHeight == 0) {
                pageHeight = 800 * currentZoom; // Estimated height
            }
            
            // Check if page is in visible range (with buffer)
            if (currentY + pageHeight >= visibleTop - buffer && currentY <= visibleBottom + buffer) {
                if (!renderedPages.containsKey(pageNum)) {
                    loadPageAsync(pageNum, pageContainer);
                }
            } else if (renderedPages.containsKey(pageNum) && 
                      (currentY + pageHeight < visibleTop - buffer * 2 || currentY > visibleBottom + buffer * 2)) {
                // Unload pages that are far from view to save memory
                unloadPage(pageNum, pageContainer);
            }
            
            currentY += pageHeight + 10; // 10 is the spacing between pages
        }
    }
    
    /**
     * Asynchronously load a specific page
     */
    private void loadPageAsync(int pageNum, VBox pageContainer) {
        Task<Image> loadTask = new Task<Image>() {
            @Override
            protected Image call() throws Exception {
                return pageRenderer.renderPage(currentDocument.getFilePath(), pageNum - 1);
            }
            
            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    Image pageImage = getValue();
                    if (pageImage != null) {
                        // Replace placeholder with actual page
                        ImageView pageView = new ImageView(pageImage);
                        pageView.setPreserveRatio(true);
                        pageView.setSmooth(true);
                        
                        // Apply zoom
                        double imageWidth = pageImage.getWidth() * currentZoom;
                        double imageHeight = pageImage.getHeight() * currentZoom;
                        pageView.setFitWidth(imageWidth);
                        pageView.setFitHeight(imageHeight);
                        
                        // Replace the placeholder in the container
                        if (pageContainer.getChildren().size() > 1) {
                            pageContainer.getChildren().set(1, pageView); // Replace placeholder (index 1)
                        }
                        
                        // Store the rendered page
                        renderedPages.put(pageNum, pageView);
                    }
                });
            }
            
            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    // Show error in placeholder
                    if (pageContainer.getChildren().size() > 1) {
                        Text errorText = new Text("Failed to load page " + pageNum);
                        errorText.setFill(Color.RED);
                        pageContainer.getChildren().set(1, errorText);
                    }
                });
            }
        };
        
        renderingExecutor.submit(loadTask);
    }
    
    /**
     * Unload a page to save memory when it's far from the current view
     */
    private void unloadPage(Integer pageNum, VBox pageContainer) {
        // Remove from rendered pages cache
        renderedPages.remove(pageNum);
        
        // Replace the image with a placeholder
        if (pageContainer.getChildren().size() > 1) {
            VBox placeholder = createPagePlaceholder(pageNum);
            // Keep the label, replace the content
            pageContainer.getChildren().set(1, placeholder.getChildren().get(1)); // Get placeholder content
        }
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
                    // Load bookmarks for the current page
                    loadBookmarksForCurrentPage();
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
            
            // Update the centering container to allow proper scrolling
            VBox centeringContainer = (VBox) scrollPane.getContent();
            if (centeringContainer != null) {
                // For centering when image is smaller than viewport
                double scrollPaneWidth = scrollPane.getWidth();
                double scrollPaneHeight = scrollPane.getHeight();
                
                // Set container size to be at least as large as the scroll pane for centering,
                // but allow it to grow larger for scrolling when zoomed
                double containerWidth = Math.max(scrollPaneWidth, imageWidth + 20); // Add padding
                double containerHeight = Math.max(scrollPaneHeight, imageHeight + 20); // Add padding
                
                centeringContainer.setPrefWidth(containerWidth);
                centeringContainer.setPrefHeight(containerHeight);
                centeringContainer.setMinWidth(containerWidth);
                centeringContainer.setMinHeight(containerHeight);
                
                // Force layout update
                Platform.runLater(() -> {
                    centeringContainer.requestLayout();
                    // Reposition bookmarks after layout update
                    loadBookmarksForCurrentPage();
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
        
        // Update placeholder sizes for unloaded pages
        for (int i = 0; i < infiniteScrollContainer.getChildren().size(); i++) {
            VBox pageContainer = (VBox) infiniteScrollContainer.getChildren().get(i);
            Integer pageNum = (Integer) pageContainer.getUserData();
            
            if (!renderedPages.containsKey(pageNum) && pageContainer.getChildren().size() > 1) {
                // Update placeholder size
                if (pageContainer.getChildren().get(1) instanceof StackPane) {
                    StackPane placeholderStack = (StackPane) pageContainer.getChildren().get(1);
                    if (!placeholderStack.getChildren().isEmpty() && placeholderStack.getChildren().get(0) instanceof Rectangle) {
                        Rectangle placeholder = (Rectangle) placeholderStack.getChildren().get(0);
                        placeholder.setWidth(600 * currentZoom);
                        placeholder.setHeight(800 * currentZoom);
                    }
                }
            }
        }
        
        // Trigger reloading of visible pages with new zoom
        Platform.runLater(() -> loadVisiblePages());
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
     * Toggle bookmark placement mode
     */
    private void toggleBookmarkMode() {
        bookmarkPlacementMode = !bookmarkPlacementMode;
        
        if (bookmarkPlacementMode) {
            // Enter bookmark placement mode
            bookmarkButton.setText("✖");
            bookmarkButton.setTooltip(new Tooltip("Cancel Bookmark Placement"));
            enableBookmarkPlacement();
        } else {
            // Exit bookmark placement mode
            bookmarkButton.setText("🔖");
            bookmarkButton.setTooltip(new Tooltip("Add Bookmark"));
            disableBookmarkPlacement();
        }
    }
    
    /**
     * Enable bookmark placement mode - cursor follows mouse
     */
    private void enableBookmarkPlacement() {
        // Create cursor bookmark icon
        cursorBookmarkIcon = createBookmarkIcon(16, 16);
        cursorBookmarkIcon.setVisible(false);
        bookmarkOverlay.getChildren().add(cursorBookmarkIcon);
        
        // Add mouse event handlers to the content area
        scrollPane.setOnMouseMoved(e -> handleBookmarkCursorMove(e));
        scrollPane.setOnMouseClicked(e -> handleBookmarkPlacement(e));
        scrollPane.setOnMouseEntered(e -> cursorBookmarkIcon.setVisible(true));
        scrollPane.setOnMouseExited(e -> cursorBookmarkIcon.setVisible(false));
        
        // Change cursor style
        scrollPane.setCursor(Cursor.CROSSHAIR);
    }
    
    /**
     * Disable bookmark placement mode
     */
    private void disableBookmarkPlacement() {
        // Remove cursor bookmark icon
        if (cursorBookmarkIcon != null) {
            bookmarkOverlay.getChildren().remove(cursorBookmarkIcon);
            cursorBookmarkIcon = null;
        }
        
        // Remove mouse event handlers
        scrollPane.setOnMouseMoved(null);
        scrollPane.setOnMouseClicked(null);
        scrollPane.setOnMouseEntered(null);
        scrollPane.setOnMouseExited(null);
        
        // Reset cursor
        scrollPane.setCursor(Cursor.DEFAULT);
    }
    
    /**
     * Handle mouse movement in bookmark placement mode
     */
    private void handleBookmarkCursorMove(MouseEvent event) {
        if (bookmarkPlacementMode && cursorBookmarkIcon != null) {
            // Position the cursor bookmark icon at mouse location
            cursorBookmarkIcon.setLayoutX(event.getX() - 8); // Center the icon
            cursorBookmarkIcon.setLayoutY(event.getY() - 8);
        }
    }
    
    /**
     * Handle bookmark placement on click
     */
    private void handleBookmarkPlacement(MouseEvent event) {
        if (bookmarkPlacementMode && currentDocument != null) {
            // Get the actual click coordinates
            double clickX = event.getX();
            double clickY = event.getY();
            
            // Calculate relative position on the page (0-1 coordinates)
            double relativeX = clickX / scrollPane.getWidth();
            double relativeY = clickY / scrollPane.getHeight();
            
            // Ensure coordinates are within bounds
            relativeX = Math.max(0, Math.min(1, relativeX));
            relativeY = Math.max(0, Math.min(1, relativeY));
            
            // Create bookmark
            Bookmark bookmark = bookmarkService.createBookmark(
                currentDocument.getId(),
                currentPage,
                relativeX,
                relativeY,
                "Page " + currentPage + " Bookmark"
            );
            
            // Debug output
            System.out.println("Creating bookmark at click position: (" + clickX + ", " + clickY + ") relative: (" + relativeX + ", " + relativeY + ")");
            
            // Add bookmark visual to overlay at the exact click position
            addBookmarkVisual(bookmark, clickX, clickY);
            
            // Exit bookmark placement mode
            bookmarkButton.setSelected(false);
            toggleBookmarkMode();
            
            // Show confirmation
            Platform.runLater(() -> {
                Alert confirmation = new Alert(Alert.AlertType.INFORMATION);
                confirmation.setTitle("Bookmark Added");
                confirmation.setHeaderText(null);
                confirmation.setContentText("Bookmark placed successfully on page " + currentPage);
                confirmation.showAndWait();
            });
            
            event.consume();
        }
    }
    
    /**
     * Add a visual bookmark indicator to the overlay
     */
    private void addBookmarkVisual(Bookmark bookmark, double x, double y) {
        ImageView bookmarkVisual = createBookmarkIcon(24, 24);
        
        // Position the bookmark relative to the scroll pane content
        // Convert from relative coordinates to actual pixel coordinates on the current page
        double imageWidth = pageImageView.getBoundsInLocal().getWidth();
        double imageHeight = pageImageView.getBoundsInLocal().getHeight();
        
        // Calculate actual position on the image
        double actualX = bookmark.getX() * imageWidth;
        double actualY = bookmark.getY() * imageHeight;
        
        // Get the scroll pane's viewport bounds
        double scrollPaneX = scrollPane.getBoundsInParent().getMinX();
        double scrollPaneY = scrollPane.getBoundsInParent().getMinY();
        
        // Get the image's position within the scroll pane
        double imageX = pageImageView.getBoundsInParent().getMinX();
        double imageY = pageImageView.getBoundsInParent().getMinY();
        
        // Calculate final position in the overlay coordinate system
        double finalX = scrollPaneX + imageX + actualX - 12; // Center the bookmark
        double finalY = scrollPaneY + imageY + actualY - 12;
        
        bookmarkVisual.setLayoutX(finalX);
        bookmarkVisual.setLayoutY(finalY);
        
        // Make sure it's visible and clickable
        bookmarkVisual.setVisible(true);
        bookmarkVisual.setMouseTransparent(false);
        bookmarkVisual.setPickOnBounds(true);
        
        // Add a subtle drop shadow for better visibility
        DropShadow dropShadow = new DropShadow();
        dropShadow.setColor(Color.BLACK);
        dropShadow.setOffsetX(1);
        dropShadow.setOffsetY(1);
        dropShadow.setRadius(3);
        bookmarkVisual.setEffect(dropShadow);
        
        // Add tooltip with bookmark info
        Tooltip tooltip = new Tooltip(bookmark.getTitle() + "\nPage: " + bookmark.getPageNumber() + "\nClick to view, Right-click to delete");
        Tooltip.install(bookmarkVisual, tooltip);
        
        // Add click handler to show bookmark info
        bookmarkVisual.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                // Left click - show bookmark details
                Alert info = new Alert(Alert.AlertType.INFORMATION);
                info.setTitle("Bookmark Details");
                info.setHeaderText(bookmark.getTitle());
                info.setContentText("Page: " + bookmark.getPageNumber() + "\nCreated: " + bookmark.getCreatedAt().toString());
                info.showAndWait();
            }
            e.consume();
        });
        
        // Add context menu for bookmark operations
        ContextMenu contextMenu = new ContextMenu();
        MenuItem deleteItem = new MenuItem("Delete Bookmark");
        deleteItem.setOnAction(e -> {
            bookmarkService.deleteBookmark(bookmark.getId());
            bookmarkOverlay.getChildren().remove(bookmarkVisual);
        });
        contextMenu.getItems().add(deleteItem);
        bookmarkVisual.setOnContextMenuRequested(e -> 
            contextMenu.show(bookmarkVisual, e.getScreenX(), e.getScreenY()));
        
        // Add the bookmark to the overlay
        bookmarkOverlay.getChildren().add(bookmarkVisual);
        
        // Debug output
        System.out.println("Added bookmark visual at position: (" + x + ", " + y + ") with ID: " + bookmark.getId());
    }
    
    /**
     * Create a bookmark icon
     */
    private ImageView createBookmarkIcon(int width, int height) {
        try {
            String iconPath = "/com/pdfreader/presentation/gui/components/assets/icons/bookmark.png";
            Image icon = new Image(getClass().getResourceAsStream(iconPath));
            if (icon.isError()) {
                throw new Exception("Icon failed to load");
            }
            ImageView imageView = new ImageView(icon);
            imageView.setFitWidth(width);
            imageView.setFitHeight(height);
            imageView.setPreserveRatio(true);
            imageView.setSmooth(true);
            return imageView;
        } catch (Exception e) {
            // Create a fallback visual bookmark using a colored rectangle
            Rectangle bookmarkRect = new Rectangle(width, height);
            bookmarkRect.setFill(Color.GOLD);
            bookmarkRect.setStroke(Color.DARKGOLDENROD);
            bookmarkRect.setStrokeWidth(1);
            bookmarkRect.setArcWidth(4);
            bookmarkRect.setArcHeight(4);
            
            // Add bookmark symbol text
            Text bookmarkText = new Text("🔖");
            bookmarkText.setFill(Color.DARKRED);
            bookmarkText.setStyle("-fx-font-size: " + (width * 0.8) + "px;");
            
            StackPane bookmarkPane = new StackPane();
            bookmarkPane.getChildren().addAll(bookmarkRect, bookmarkText);
            bookmarkPane.setPrefSize(width, height);
            
            // Convert StackPane to ImageView for consistency
            ImageView fallbackView = new ImageView();
            fallbackView.setFitWidth(width);
            fallbackView.setFitHeight(height);
            
            // We'll return the StackPane as a Node, but we need ImageView for the interface
            // So let's create a simple colored rectangle as ImageView
            WritableImage fallbackImage = new WritableImage(width, height);
            PixelWriter pixelWriter = fallbackImage.getPixelWriter();
            
            // Draw a simple bookmark shape
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (x == 0 || x == width-1 || y == 0 || y == height-1) {
                        pixelWriter.setColor(x, y, Color.DARKGOLDENROD);
                    } else {
                        pixelWriter.setColor(x, y, Color.GOLD);
                    }
                }
            }
            
            fallbackView.setImage(fallbackImage);
            return fallbackView;
        }
    }
    
    /**
     * Load and display bookmarks for the current page
     */
    private void loadBookmarksForCurrentPage() {
        if (currentDocument == null || bookmarkService == null) {
            return;
        }
        
        // Clear existing bookmark visuals
        bookmarkOverlay.getChildren().clear();
        
        // Load bookmarks for current page
        List<Bookmark> pageBookmarks = bookmarkService.getBookmarksForPage(currentDocument.getId(), currentPage);
        
        // Add visual indicators for each bookmark using their stored relative coordinates
        for (Bookmark bookmark : pageBookmarks) {
            // Use the stored relative coordinates directly - addBookmarkVisual will handle the conversion
            addBookmarkVisual(bookmark, 0, 0); // x,y parameters not used anymore since we use relative coords
        }
    }
    
    public void cleanup() {
        renderingExecutor.shutdown();
    }
}
