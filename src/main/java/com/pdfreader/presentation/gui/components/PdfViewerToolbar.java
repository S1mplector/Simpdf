package com.pdfreader.presentation.gui.components;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;

/**
 * Lightweight toolbar component for the PDF viewer.
 * Encapsulates navigation, view-mode toggle, bookmarking, progress and zoom controls.
 */
public class PdfViewerToolbar extends HBox {

    private static final String UNIFORM_BUTTON_STYLE = "-fx-font-size: 13; -fx-padding: 6 12 6 12;";

    private final ToggleButton viewModeToggle;
    private final Button prevButton;
    private final Button nextButton;
    private final TextField pageField;
    private final Label pageLabel;
    private final ToggleButton bookmarkButton;
    private final ProgressBar progressBar;
    private final Label progressLabel;
    private final Slider zoomSlider;

    public PdfViewerToolbar() {
        super(10);
        setAlignment(Pos.CENTER_LEFT);

        // View-mode toggle
        viewModeToggle = new ToggleButton("📄 Single Page");
        viewModeToggle.setPrefWidth(120);
        viewModeToggle.setSelected(true);
        viewModeToggle.setStyle(UNIFORM_BUTTON_STYLE);

        // Navigation buttons
        prevButton = new Button("◀ Previous");
        prevButton.setStyle(UNIFORM_BUTTON_STYLE);

        nextButton = new Button("Next ▶");
        nextButton.setStyle(UNIFORM_BUTTON_STYLE);

        // Page controls
        pageField = new TextField();
        pageField.setPrefWidth(60);
        pageLabel = new Label("Page 0 of 0");

        // Bookmark
        bookmarkButton = new ToggleButton("🔖");
        bookmarkButton.setStyle(UNIFORM_BUTTON_STYLE);
        bookmarkButton.setTooltip(new Tooltip("Add Bookmark"));

        // Progress
        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(200);
        progressLabel = new Label("0%");

        // Zoom
        zoomSlider = new Slider(0.5, 3.0, 1.0);
        zoomSlider.setShowTickLabels(true);
        zoomSlider.setShowTickMarks(true);
        zoomSlider.setMajorTickUnit(0.5);
        zoomSlider.setPrefWidth(150);

        // Assemble toolbar
        getChildren().addAll(
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
        setStyle("-fx-padding: 10; -fx-background-color: #f0f0f0;");
    }

    // --- getters ----------------------------------------------------------
    public ToggleButton getViewModeToggle() { return viewModeToggle; }
    public Button getPrevButton()          { return prevButton; }
    public Button getNextButton()          { return nextButton; }
    public TextField getPageField()        { return pageField; }
    public Label getPageLabel()            { return pageLabel; }
    public ToggleButton getBookmarkButton(){ return bookmarkButton; }
    public ProgressBar getProgressBar()    { return progressBar; }
    public Label getProgressLabel()        { return progressLabel; }
    public Slider getZoomSlider()          { return zoomSlider; }
} 