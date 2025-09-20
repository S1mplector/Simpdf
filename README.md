# Quill 

A lightweight JavaFX-based PDF reader built with Spring Boot and Apache PDFBox. It supports single-page and infinite-scroll modes, zooming, reading progress, search, and bookmarks.

## Features

- **Viewer modes**: Single Page ↔ Infinite Scroll toggle
- **Navigation**: Prev/Next buttons, jump-to-page, Page Up/Down keys
- **Zoom**: Slider and Ctrl + Mouse Wheel
- **Reading progress**: Auto-saves current page and scroll position
- **Search**: In-document and library search
- **Bookmarks**: Add and display bookmarks with overlay
- **Lazy loading**: Efficient page rendering in infinite scroll
- **JavaFX UI**: Toolbar with status/progress display

## Tech Stack

- **Java**: 17
- **Build**: Maven
- **UI**: JavaFX (controls, FXML)
- **PDF**: Apache PDFBox
- **Framework**: Spring Boot 3
- **JSON**: Jackson

See `pom.xml` versions:
- Spring Boot 3.1.2
- PDFBox 2.0.29
- JavaFX 21.0.1

## Requirements

- Java 17 (ensure `JAVA_HOME` points to JDK 17)
- Maven 3.8+
- macOS/Linux/Windows
- Internet for first dependency resolution

## Quick Start

1) Build
```bash
mvn clean package
```

2) Run the GUI
```bash
mvn -DskipTests javafx:run
```
This uses the configured main class `com.pdfreader.presentation.gui.PdfReaderGuiApplication`.

3) Run tests
```bash
mvn test
```

## Entry Point

- `src/main/java/com/pdfreader/presentation/gui/PdfReaderGuiApplication.java`
  - Boots Spring context
  - Loads `resources/fxml/pdf-reader-gui.fxml`
  - Shows JavaFX stage
  - Sets profile `gui` and `spring.main.web-application-type=none`

## Key Components

- `com.pdfreader.presentation.gui.components.PdfViewerComponent`
  - Renders pages via `PdfPageRenderer`
  - Single Page / Infinite Scroll modes
  - Zoom, keyboard navigation
  - Progress via `ReadingProgressService`
  - Search via `DocumentSearchService` and `LibrarySearchService`
  - Bookmarks via `BookmarkService` and `BookmarkOverlay`
  - Toolbar via `PdfViewerToolbar`

## Project Structure

- `src/main/java/` — Application, GUI, components, services, domain
- `src/main/resources/`
  - `fxml/` — FXML layouts (e.g., `pdf-reader-gui.fxml`)
- `pom.xml` — Dependencies and plugins (Spring Boot, JavaFX)

## Usage Tips

- Toggle view mode with the toolbar button
- Use the zoom slider or Ctrl + Mouse Wheel
- Enter a page number and press Enter to jump
- Use the search panel for document/library search
- Add bookmarks and view them on the overlay

## Troubleshooting

- **Java version**: `java -version` should be 17
- **JavaFX runtime flags**: Prefer `mvn javafx:run` to avoid manual module args
- **FXML not found**: Ensure `src/main/resources/fxml/pdf-reader-gui.fxml` exists and is on the classpath
- **Rendering issues**: Verify PDF path and PDFBox dependency
- **Slow first scroll**: Infinite scroll lazily renders; first loads may take time

## Development

- Run from IDE using `PdfReaderGuiApplication`
- Controllers/components can use Spring DI (`springContext::getBean`)
- Add new FXML under `resources/fxml/` and controllers under `presentation/gui/`

## License

MIT - see [LICENSE.md](LICENSE.md)
