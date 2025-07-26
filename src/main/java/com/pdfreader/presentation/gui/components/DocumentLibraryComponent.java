package com.pdfreader.presentation.gui.components;

import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.pdfreader.application.PdfApplicationService;
import com.pdfreader.application.PdfFolderScannerService;
import com.pdfreader.application.ReadingProgressService;
import com.pdfreader.application.LibrarySearchService;
import com.pdfreader.application.DocumentSearchService;
import com.pdfreader.domain.model.PdfDocument;
import com.pdfreader.domain.model.ReadingProgress;
import com.pdfreader.domain.model.SearchResult;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

/**
 * Document library component for managing and selecting PDF documents.
 * Shows recently read documents with reading progress.
 */
public class DocumentLibraryComponent extends VBox {

    private static final Logger logger = LoggerFactory.getLogger(DocumentLibraryComponent.class);
    
    private final PdfApplicationService documentService;
    private final ReadingProgressService progressService;
    private final PdfFolderScannerService folderScannerService;
    private final LibrarySearchService librarySearchService;
    private final DocumentSearchService documentSearchService;

    // UI Components
    private VBox root;
    private TableView<DocumentWithProgress> documentsTable;
    private Button selectFolderButton;
    private Button deleteButton;
    private Button refreshButton;
    private Label statusLabel;
    private Label folderPathLabel;
    private SearchComponent searchComponent;

    // Data
    private ObservableList<DocumentWithProgress> documentsList = FXCollections.observableArrayList();

    // Event handler for document selection
    private DocumentSelectionListener selectionListener;

    public interface DocumentSelectionListener {
        void onDocumentSelected(PdfDocument document);
    }

    /**
     * Wrapper class to combine PdfDocument with ReadingProgress for table display
     */
    public static class DocumentWithProgress {
        private final PdfDocument document;
        private final ReadingProgress progress;

        public DocumentWithProgress(PdfDocument document, ReadingProgress progress) {
            this.document = document;
            this.progress = progress;
        }

        public PdfDocument getDocument() {
            return document;
        }

        public ReadingProgress getProgress() {
            return progress;
        }

        // Table column getters
        public String getFileName() {
            return document.getFileName();
        }

        public String getTitle() {
            return document.getTitle() != null ? document.getTitle() : "N/A";
        }

        public int getPageCount() {
            return document.getPageCount();
        }

        public String getProgressText() {
            if (progress != null) {
                return String.format("%d/%d (%.1f%%)",
                        progress.getCurrentPage(),
                        progress.getTotalPages(),
                        progress.getProgressPercentage());
            }
            return "Not started";
        }

        public String getLastRead() {
            if (progress != null && progress.getLastReadAt() != null) {
                return progress.getLastReadAt().format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm"));
            }
            return "Never";
        }
    }

    @Autowired
    public DocumentLibraryComponent(PdfApplicationService documentService,
                                     ReadingProgressService progressService,
                                     PdfFolderScannerService folderScannerService,
                                     LibrarySearchService librarySearchService,
                                     DocumentSearchService documentSearchService) {
        this.documentService = documentService;
        this.progressService = progressService;
        this.folderScannerService = folderScannerService;
        this.librarySearchService = librarySearchService;
        this.documentSearchService = documentSearchService;
        initializeComponent();
    }

    private void initializeComponent() {
        root = new VBox(10);
        root.setPadding(new Insets(20));

        // Create header
        HBox headerBox = new HBox(10);
        headerBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        
        Label titleLabel = new Label("Document Library");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        
        headerBox.getChildren().add(titleLabel);

        // Create folder path display
        folderPathLabel = new Label("No PDF folder selected");
        folderPathLabel.setStyle("-fx-text-fill: #666; -fx-font-style: italic;");

        // Create buttons
        createButtons();

        // Create search component
        searchComponent = new SearchComponent(SearchComponent.SearchMode.LIBRARY_SEARCH, 
                                            librarySearchService, documentSearchService);
        searchComponent.setOnResultSelected(this::handleSearchResultSelected);
        
        // Create table
        createDocumentsTable();

        // Create status label
        statusLabel = new Label("Ready - Select a PDF folder to start scanning");
        statusLabel.setStyle("-fx-text-fill: #666;");

        // Layout
        HBox buttonBox = new HBox(10, selectFolderButton, deleteButton, refreshButton);
        root.getChildren().addAll(headerBox, folderPathLabel, buttonBox, searchComponent, documentsTable, statusLabel);
        
        // Add the root VBox to this component
        this.getChildren().add(root);

        // Load initial data
        refreshDocuments();
    }

    private void createButtons() {
        // Select folder button - icon only with tooltip
        selectFolderButton = new Button();
        selectFolderButton.setGraphic(createIcon("Select.png", 24, 24));
        selectFolderButton.setStyle(getIconOnlyButtonStyle());
        selectFolderButton.setTooltip(new Tooltip("Select PDF Folder"));
        selectFolderButton.setOnAction(e -> selectPdfFolder());

        // Delete button - icon only with tooltip
        deleteButton = new Button();
        deleteButton.setGraphic(createIcon("delete.png", 24, 24));
        deleteButton.setStyle(getIconOnlyButtonStyle());
        deleteButton.setTooltip(new Tooltip("Delete Selected Document"));
        deleteButton.setOnAction(e -> deleteSelectedDocument());
        deleteButton.setDisable(true);

        // Rescan button - icon only with tooltip
        refreshButton = new Button();
        refreshButton.setGraphic(createIcon("rescan.png", 24, 24));
        refreshButton.setStyle(getIconOnlyButtonStyle());
        refreshButton.setTooltip(new Tooltip("Rescan Folder for PDFs"));
        refreshButton.setOnAction(e -> rescanFolder());
        refreshButton.setDisable(true);
    }

    private void createDocumentsTable() {
        documentsTable = new TableView<>();
        documentsTable.setRowFactory(tv -> {
            TableRow<DocumentWithProgress> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    openSelectedDocument();
                }
            });
            return row;
        });

        // Table columns
        TableColumn<DocumentWithProgress, String> fileNameColumn = new TableColumn<>("File Name");
        fileNameColumn.setCellValueFactory(new PropertyValueFactory<>("fileName"));
        fileNameColumn.setPrefWidth(200);

        TableColumn<DocumentWithProgress, String> titleColumn = new TableColumn<>("Title");
        titleColumn.setCellValueFactory(new PropertyValueFactory<>("title"));
        titleColumn.setPrefWidth(150);

        TableColumn<DocumentWithProgress, Integer> pageCountColumn = new TableColumn<>("Pages");
        pageCountColumn.setCellValueFactory(new PropertyValueFactory<>("pageCount"));
        pageCountColumn.setPrefWidth(80);

        TableColumn<DocumentWithProgress, String> progressColumn = new TableColumn<>("Progress");
        progressColumn.setCellValueFactory(new PropertyValueFactory<>("progressText"));
        progressColumn.setPrefWidth(120);

        TableColumn<DocumentWithProgress, String> lastReadColumn = new TableColumn<>("Last Read");
        lastReadColumn.setCellValueFactory(new PropertyValueFactory<>("lastRead"));
        lastReadColumn.setPrefWidth(150);

        documentsTable.getColumns().addAll(fileNameColumn, titleColumn, pageCountColumn, progressColumn, lastReadColumn);
    }

    private void selectPdfFolder() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select PDF Folder");

        Stage stage = (Stage) root.getScene().getWindow();
        File selectedFolder = directoryChooser.showDialog(stage);

        if (selectedFolder != null) {
            try {
                updateStatus("Setting up PDF folder scanning...");

                folderScannerService.setMasterFolder(selectedFolder.getAbsolutePath())
                        .thenRun(() -> Platform.runLater(() -> {
                            folderPathLabel.setText("Scanning: " + selectedFolder.getAbsolutePath());
                            folderPathLabel.setStyle("-fx-text-fill: #4CAF50; -fx-font-style: normal;");
                            refreshButton.setDisable(false);
                            updateStatus("PDF folder scanning started - PDFs will be automatically detected");
                            refreshDocuments();
                        }))
                        .exceptionally(throwable -> {
                            Platform.runLater(() -> {
                                Logger logger = LoggerFactory.getLogger(DocumentLibraryComponent.class);
                                logger.error("Failed to set PDF folder", throwable);
                                showErrorAlert("Folder Error", "Failed to set PDF folder", throwable.getMessage());
                                updateStatus("Failed to set PDF folder");
                            });
                            return null;
                        });

            } catch (Exception e) {
                Logger logger = LoggerFactory.getLogger(DocumentLibraryComponent.class);
                logger.error("Failed to set PDF folder", e);
                showErrorAlert("Folder Error", "Failed to set PDF folder", e.getMessage());
                updateStatus("Failed to set PDF folder");
            }
        }
    }

    private void rescanFolder() {
        if (!folderScannerService.isScanning()) {
            updateStatus("No folder is currently being scanned");
            return;
        }

        updateStatus("Rescanning PDF folder...");
        folderScannerService.rescanFolder()
                .thenRun(() -> Platform.runLater(() -> {
                    updateStatus("Folder rescan completed");
                    refreshDocuments();
                }))
                .exceptionally(throwable -> {
                    Platform.runLater(() -> {
                        Logger logger = LoggerFactory.getLogger(DocumentLibraryComponent.class);
                        logger.error("Failed to rescan folder", throwable);
                        updateStatus("Failed to rescan folder");
                    });
                    return null;
                });
    }

    private void deleteSelectedDocument() {
        DocumentWithProgress selected = documentsTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        PdfDocument document = selected.getDocument();
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Confirm Deletion");
        confirmAlert.setHeaderText("Delete Document");
        confirmAlert.setContentText("Are you sure you want to delete '" + document.getFileName() + "'?\nThis will also remove your reading progress.");

        if (confirmAlert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            try {
                documentService.deleteDocument(document.getId());
                progressService.deleteProgress(document.getId());
                updateStatus("Document deleted successfully: " + document.getFileName());
                refreshDocuments();
            } catch (Exception e) {
                Logger logger = LoggerFactory.getLogger(DocumentLibraryComponent.class);
                logger.error("Failed to delete document", e);
                updateStatus("Error deleting document: " + e.getMessage());
                showErrorAlert("Delete Error", "Failed to delete document", e.getMessage());
            }
        }
    }

    private void openSelectedDocument() {
        DocumentWithProgress selected = documentsTable.getSelectionModel().getSelectedItem();
        if (selected != null && selectionListener != null) {
            selectionListener.onDocumentSelected(selected.getDocument());
        }
    }

    private void refreshDocuments() {
        try {
            // Get documents from the folder scanner service instead of document service
            List<PdfDocument> documents = folderScannerService.getAllPdfs();
            
            // Create DocumentWithProgress objects for table display
            documentsList.clear();
            for (PdfDocument document : documents) {
                ReadingProgress progress = progressService.getProgress(document.getId()).orElse(null);
                documentsList.add(new DocumentWithProgress(document, progress));
            }
            
            documentsTable.setItems(documentsList);
            
            // Enable/disable delete button based on selection
            documentsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
                deleteButton.setDisable(newSelection == null);
            });

            String statusText = "Found " + documents.size() + " PDF documents";
            if (folderScannerService.isScanning()) {
                statusText += " (auto-scanning active)";
            }
            updateStatus(statusText);
        } catch (Exception e) {
            updateStatus("Error refreshing documents: " + e.getMessage());
            showErrorAlert("Refresh Error", "Failed to refresh document list", e.getMessage());
        }
    }
    
    private void updateStatus(String message) {
        statusLabel.setText(message);
    }
    
    private void showErrorAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
    
    /**
     * Handle search result selection from the search component
     */
    private void handleSearchResultSelected(SearchResult searchResult) {
        try {
            // Find the document by ID and open it
            PdfDocument document = documentService.getDocumentById(searchResult.getDocumentId());
            if (document != null && selectionListener != null) {
                selectionListener.onDocumentSelected(document);
            }
        } catch (Exception e) {
            showErrorAlert("Search Error", "Failed to open selected document", e.getMessage());
        }
    }
    
    // Public API
    public void setDocumentSelectionListener(DocumentSelectionListener listener) {
        this.selectionListener = listener;
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
            logger.warn("Could not load icon: " + iconName, e);
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
     * Get icon-only button styling for a cleaner appearance
     */
    private String getIconOnlyButtonStyle() {
        return "-fx-background-color: #C0C0C0; " +
               "-fx-border-color: #999999; " +
               "-fx-border-width: 1px; " +
               "-fx-border-radius: 6px; " +
               "-fx-background-radius: 6px; " +
               "-fx-padding: 8px; " +
               "-fx-cursor: hand; " +
               "-fx-min-width: 40px; " +
               "-fx-min-height: 40px; " +
               "-fx-max-width: 40px; " +
               "-fx-max-height: 40px;";
    }
    
    public void refresh() {
        refreshDocuments();
    }
}
