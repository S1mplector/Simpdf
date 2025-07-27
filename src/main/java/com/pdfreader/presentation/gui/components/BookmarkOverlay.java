package com.pdfreader.presentation.gui.components;

import javafx.scene.layout.StackPane;

/**
 * Lightweight overlay pane used to display bookmark icons on top of the PDF page(s).
 * Currently acts as a plain StackPane but centralises any bookmark-specific styling or behaviour
 * so it can evolve independently of {@link PdfViewerComponent}.
 */
public class BookmarkOverlay extends StackPane {

    public BookmarkOverlay() {
        // Allow mouse events for bookmark placement & context menu
        setMouseTransparent(false);
        setPickOnBounds(false);
    }
} 