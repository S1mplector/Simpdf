package com.pdfreader.presentation.gui.components;

import com.pdfreader.application.DocumentSearchService;
import com.pdfreader.application.LibrarySearchService;
import com.pdfreader.domain.model.LibrarySearchCriteria;
import com.pdfreader.domain.model.PdfDocument;
import com.pdfreader.domain.model.SearchResult;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

/**
 * Reusable search component for both library and document search
 */
public class SearchComponent extends VBox {
    
    public enum SearchMode {
        LIBRARY_SEARCH,
        DOCUMENT_SEARCH
    }
    
    private final SearchMode searchMode;
    private final LibrarySearchService librarySearchService;
    private final DocumentSearchService documentSearchService;
    
    // UI Components
    private TextField searchField;
    private Button searchButton;
    private Button clearButton;
    private CheckBox caseSensitiveCheckBox;
    private CheckBox searchContentCheckBox;
    private Label statusLabel;
    private TableView<SearchResult> resultsTable;
    private ObservableList<SearchResult> searchResults;
    private ProgressIndicator progressIndicator;
    
    // Search options for library search
    private CheckBox searchTitleCheckBox;
    private CheckBox searchAuthorCheckBox;
    private CheckBox searchFilenameCheckBox;
    
    // Callbacks
    private Consumer<SearchResult> onResultSelected;
    private Consumer<PdfDocument> onDocumentSelected;
    private Runnable onSearchStarted;
    private Runnable onSearchCompleted;
    
    // Current search context
    private PdfDocument currentDocument;
    private String currentPageText;
    private int currentPageNumber;
    
    public SearchComponent(SearchMode searchMode, LibrarySearchService librarySearchService, 
                          DocumentSearchService documentSearchService) {
        this.searchMode = searchMode;
        this.librarySearchService = librarySearchService;
        this.documentSearchService = documentSearchService;
        this.searchResults = FXCollections.observableArrayList();
        
        initializeUI();
        setupEventHandlers();
    }
    
    private void initializeUI() {
        setSpacing(10);
        setPadding(new Insets(10));
        
        // Title
        Label titleLabel = new Label(searchMode == SearchMode.LIBRARY_SEARCH ? "Library Search" : "Document Search");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        
        // Search input row
        HBox searchInputRow = createSearchInputRow();
        
        // Search options (different for each mode)
        VBox searchOptions = createSearchOptions();
        
        // Results table
        TableView<SearchResult> resultsTable = createResultsTable();
        
        // Status row
        HBox statusRow = createStatusRow();
        
        getChildren().addAll(titleLabel, searchInputRow, searchOptions, resultsTable, statusRow);
        VBox.setVgrow(resultsTable, Priority.ALWAYS);
    }
    
    private HBox createSearchInputRow() {
        HBox searchRow = new HBox(10);
        searchRow.setAlignment(Pos.CENTER_LEFT);
        
        searchField = new TextField();
        searchField.setPromptText(searchMode == SearchMode.LIBRARY_SEARCH ? 
            "Search library..." : "Search in document...");
        searchField.setPrefWidth(300);
        
        searchButton = new Button("🔍 Search");
        searchButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
        
        clearButton = new Button("✕ Clear");
        clearButton.setStyle("-fx-background-color: #f44336; -fx-text-fill: white;");
        clearButton.setDisable(true);
        
        progressIndicator = new ProgressIndicator();
        progressIndicator.setMaxSize(20, 20);
        progressIndicator.setVisible(false);
        
        searchRow.getChildren().addAll(searchField, searchButton, clearButton, progressIndicator);
        HBox.setHgrow(searchField, Priority.ALWAYS);
        
        return searchRow;
    }
    
    private VBox createSearchOptions() {
        VBox optionsBox = new VBox(5);
        
        caseSensitiveCheckBox = new CheckBox("Case sensitive");
        
        if (searchMode == SearchMode.LIBRARY_SEARCH) {
            Label optionsLabel = new Label("Search in:");
            optionsLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
            
            searchFilenameCheckBox = new CheckBox("Filename");
            searchFilenameCheckBox.setSelected(true);
            
            searchTitleCheckBox = new CheckBox("Title");
            searchTitleCheckBox.setSelected(true);
            
            searchAuthorCheckBox = new CheckBox("Author");
            searchAuthorCheckBox.setSelected(true);
            
            searchContentCheckBox = new CheckBox("Content (slower)");
            searchContentCheckBox.setSelected(false);
            
            HBox libraryOptions = new HBox(15);
            libraryOptions.getChildren().addAll(searchFilenameCheckBox, searchTitleCheckBox, 
                                               searchAuthorCheckBox, searchContentCheckBox);
            
            optionsBox.getChildren().addAll(optionsLabel, libraryOptions, caseSensitiveCheckBox);
        } else {
            optionsBox.getChildren().add(caseSensitiveCheckBox);
        }
        
        return optionsBox;
    }
    
    private TableView<SearchResult> createResultsTable() {
        resultsTable = new TableView<>();
        resultsTable.setItems(searchResults);
        resultsTable.setRowFactory(tv -> {
            TableRow<SearchResult> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    SearchResult result = row.getItem();
                    if (onResultSelected != null) {
                        onResultSelected.accept(result);
                    }
                }
            });
            return row;
        });
        
        // Document column
        TableColumn<SearchResult, String> documentColumn = new TableColumn<>("Document");
        documentColumn.setCellValueFactory(new PropertyValueFactory<>("documentTitle"));
        documentColumn.setPrefWidth(200);
        
        // Page column
        TableColumn<SearchResult, Integer> pageColumn = new TableColumn<>("Page");
        pageColumn.setCellValueFactory(new PropertyValueFactory<>("pageNumber"));
        pageColumn.setPrefWidth(60);
        
        // Match column
        TableColumn<SearchResult, String> matchColumn = new TableColumn<>("Match");
        matchColumn.setCellValueFactory(new PropertyValueFactory<>("matchedText"));
        matchColumn.setPrefWidth(150);
        
        // Context column
        TableColumn<SearchResult, String> contextColumn = new TableColumn<>("Context");
        contextColumn.setCellValueFactory(cellData -> {
            SearchResult result = cellData.getValue();
            String context = result.getContextBefore() + "[" + result.getMatchedText() + "]" + result.getContextAfter();
            return new javafx.beans.property.SimpleStringProperty(context);
        });
        contextColumn.setPrefWidth(400);
        
        // Score column
        TableColumn<SearchResult, Double> scoreColumn = new TableColumn<>("Score");
        scoreColumn.setCellValueFactory(new PropertyValueFactory<>("relevanceScore"));
        scoreColumn.setPrefWidth(60);
        
        resultsTable.getColumns().addAll(documentColumn, pageColumn, matchColumn, contextColumn, scoreColumn);
        resultsTable.setPlaceholder(new Label("No search results"));
        
        return resultsTable;
    }
    
    private HBox createStatusRow() {
        HBox statusRow = new HBox(10);
        statusRow.setAlignment(Pos.CENTER_LEFT);
        
        statusLabel = new Label("Ready to search");
        statusLabel.setStyle("-fx-text-fill: #666666;");
        
        statusRow.getChildren().add(statusLabel);
        return statusRow;
    }
    
    private void setupEventHandlers() {
        // Search on button click or Enter key
        searchButton.setOnAction(e -> performSearch());
        searchField.setOnAction(e -> performSearch());
        
        // Clear results
        clearButton.setOnAction(e -> clearResults());
        
        // Enable/disable clear button based on results
        searchResults.addListener((javafx.collections.ListChangeListener<SearchResult>) change -> {
            clearButton.setDisable(searchResults.isEmpty());
        });
    }
    
    private void performSearch() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) {
            updateStatus("Please enter a search query");
            return;
        }
        
        // Show progress
        showProgress(true);
        updateStatus("Searching...");
        if (onSearchStarted != null) {
            onSearchStarted.run();
        }
        
        // Perform search in background thread
        Task<List<SearchResult>> searchTask = new Task<List<SearchResult>>() {
            @Override
            protected List<SearchResult> call() throws Exception {
                if (searchMode == SearchMode.LIBRARY_SEARCH) {
                    return performLibrarySearch(query);
                } else {
                    return performDocumentSearch(query);
                }
            }
            
            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    List<SearchResult> results = getValue();
                    searchResults.setAll(results);
                    updateStatus(String.format("Found %d results", results.size()));
                    showProgress(false);
                    if (onSearchCompleted != null) {
                        onSearchCompleted.run();
                    }
                });
            }
            
            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    Throwable exception = getException();
                    updateStatus("Search failed: " + exception.getMessage());
                    showProgress(false);
                    if (onSearchCompleted != null) {
                        onSearchCompleted.run();
                    }
                });
            }
        };
        
        Thread searchThread = new Thread(searchTask);
        searchThread.setDaemon(true);
        searchThread.start();
    }
    
    private List<SearchResult> performLibrarySearch(String query) throws IOException {
        if (searchContentCheckBox != null && searchContentCheckBox.isSelected()) {
            // Content search across library
            return librarySearchService.searchContentInLibrary(query, caseSensitiveCheckBox.isSelected(), 100);
        } else {
            // Metadata search - convert to SearchResult format
            LibrarySearchCriteria criteria = new LibrarySearchCriteria(query);
            criteria.setCaseSensitive(caseSensitiveCheckBox.isSelected());
            criteria.setSearchTitle(searchTitleCheckBox.isSelected());
            criteria.setSearchAuthor(searchAuthorCheckBox.isSelected());
            criteria.setSearchFilename(searchFilenameCheckBox.isSelected());
            criteria.setSearchContent(false); // Handled separately above
            
            List<PdfDocument> documents = librarySearchService.searchLibrary(criteria);
            
            // Convert to SearchResult format for consistent display
            return documents.stream()
                    .map(doc -> new SearchResult(
                            doc.getId(),
                            doc.getTitle() != null ? doc.getTitle() : doc.getFileName(),
                            doc.getFilePath(),
                            1, // Page 1 for metadata matches
                            query, // The search query as matched text
                            "", // No context for metadata search
                            String.format("Author: %s, Pages: %d", 
                                        doc.getAuthor() != null ? doc.getAuthor() : "Unknown", 
                                        doc.getPageCount()),
                            0, 0, 1.0
                    ))
                    .collect(java.util.stream.Collectors.toList());
        }
    }
    
    private List<SearchResult> performDocumentSearch(String query) throws IOException {
        if (currentDocument == null) {
            throw new IllegalStateException("No document set for search");
        }
        
        if (currentPageText != null && currentPageNumber > 0) {
            // Search in current page only
            return documentSearchService.searchInCurrentPage(
                    currentDocument, currentPageText, query, currentPageNumber, caseSensitiveCheckBox.isSelected());
        } else {
            // Search in entire document
            return documentSearchService.searchInDocument(currentDocument, query, caseSensitiveCheckBox.isSelected());
        }
    }
    
    private void clearResults() {
        searchResults.clear();
        searchField.clear();
        updateStatus("Results cleared");
    }
    
    private void showProgress(boolean show) {
        progressIndicator.setVisible(show);
        searchButton.setDisable(show);
    }
    
    private void updateStatus(String message) {
        statusLabel.setText(message);
    }
    
    // Public methods for configuration
    public void setCurrentDocument(PdfDocument document) {
        this.currentDocument = document;
    }
    
    public void setCurrentPage(String pageText, int pageNumber) {
        this.currentPageText = pageText;
        this.currentPageNumber = pageNumber;
    }
    
    public void setOnResultSelected(Consumer<SearchResult> callback) {
        this.onResultSelected = callback;
    }
    
    public void setOnDocumentSelected(Consumer<PdfDocument> callback) {
        this.onDocumentSelected = callback;
    }
    
    public void setOnSearchStarted(Runnable callback) {
        this.onSearchStarted = callback;
    }
    
    public void setOnSearchCompleted(Runnable callback) {
        this.onSearchCompleted = callback;
    }
    
    public List<SearchResult> getCurrentResults() {
        return searchResults;
    }
    
    public void focusSearchField() {
        searchField.requestFocus();
    }
}
