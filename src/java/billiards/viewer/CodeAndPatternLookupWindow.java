package billiards.viewer;

import billiards.database.Admin;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Callback;

import java.sql.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CodeAndPatternLookupWindow {
    private static final int INITIAL_LOAD = 20;
    private static final int BATCH_SIZE = 5;
    private final ObservableList<CodeAndPattern> data = FXCollections.observableArrayList();
    private boolean isLoading = false;
    private int currentOffset = 0;
    private boolean scrollBarInitialized = false;

    private final Stage stage = new Stage();
    TableView<CodeAndPattern> tableView = new TableView<>(data);

    private static final int CELL_WIDTH = 200;
    private static final int EXPANDED_WIDTH = 400;

    public static class ScrollableTableCell<S, T> extends TextFieldTableCell<S, T> {
        private final Tooltip tooltip = new Tooltip();
        private final ScrollPane scrollPane = new ScrollPane();
        private final Label contentLabel = new Label();

        // Jun 12, 2025. DeepSeek, a table cell that is horizontally scrollable and shows full content on mouse hover
        public ScrollableTableCell() {
            scrollPane.setContent(contentLabel);
            scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            scrollPane.setMaxHeight(30);
            scrollPane.setPrefViewportWidth(CELL_WIDTH);
            scrollPane.getStyleClass().add("cell-scroll-pane");

            HBox.setHgrow(scrollPane, Priority.ALWAYS);
            this.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);

            // Show full content on hover
            this.addEventHandler(MouseEvent.MOUSE_ENTERED, e -> {
                String text = getItem() != null ? getItem().toString() : "";
                tooltip.setText(text);
                Tooltip.install(this, tooltip);
                scrollPane.setPrefViewportWidth(EXPANDED_WIDTH);
            });

            this.addEventHandler(MouseEvent.MOUSE_EXITED, e -> {
                Tooltip.uninstall(this, tooltip);
                scrollPane.setPrefViewportWidth(CELL_WIDTH);
            });
        }

        @Override
        public void updateItem(T item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                contentLabel.setText(null);
                this.setGraphic(null);
            } else {
                contentLabel.setText(item.toString());
                this.setGraphic(scrollPane);
            }
        }
    }

    public static class CodeAndPattern {
        private final String codeSequence;
        private final String iterationPattern;

        public CodeAndPattern(String column1, String column2) {
            this.codeSequence = column1;
            this.iterationPattern = column2;
        }

        public String getCodeSequence() { return codeSequence; }
        public String getIterationPattern() { return iterationPattern; }
    }

    public CodeAndPatternLookupWindow(IterateToLimitWindow iterateToLimitWindow) {
        // Jun 12, 2025. DeepSeek, create columns with custom cell factory
        Callback<TableColumn<CodeAndPattern, String>, TableCell<CodeAndPattern, String>> cellFactory =
                col -> new ScrollableTableCell<>();

        // Create columns
        TableColumn<CodeAndPattern, String> codeCol = new TableColumn<>("Code Sequence");
        codeCol.setCellValueFactory(new PropertyValueFactory<>("codeSequence"));
        codeCol.setCellFactory(cellFactory);
        codeCol.setPrefWidth(CELL_WIDTH);

        TableColumn<CodeAndPattern, String> patternCol = new TableColumn<>("Iteration Pattern");
        patternCol.setCellValueFactory(new PropertyValueFactory<>("iterationPattern"));
        patternCol.setCellFactory(cellFactory);
        patternCol.setPrefWidth(CELL_WIDTH);

        tableView.getColumns().add(codeCol);
        tableView.getColumns().add(patternCol);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tableView.setPrefSize(CELL_WIDTH * 2, CELL_WIDTH * 2);

        // Set up the scene
        VBox vbox = new VBox(tableView);
        Scene scene = new Scene(vbox, CELL_WIDTH * 2, CELL_WIDTH * 2);
        stage.setScene(scene);

        stage.initModality(Modality.NONE);
        stage.initOwner(iterateToLimitWindow.getStage());
    }

    private void lookUpIterPat(int limit, int offset) {
        isLoading = true;
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            final String selectPatternQuery = "SELECT code_sequence,iter_pattern FROM main.iteration_pattern ORDER BY last_used DESC LIMIT ? OFFSET ?;";

            String dbName = "garbage";
            try (Connection conn = DriverManager.getConnection(Admin.getUrl(dbName));
                 PreparedStatement stmt = conn.prepareStatement(selectPatternQuery)) {
                stmt.setInt(1, limit);
                stmt.setInt(2, offset);

                ResultSet rs = stmt.executeQuery();

                ObservableList<CodeAndPattern> newCodesAndPatterns = FXCollections.observableArrayList();

                while (rs.next()) {
                    newCodesAndPatterns.add(new CodeAndPattern(rs.getString(1), rs.getString(2)));
                }

                // Update UI on JavaFX Application Thread
                javafx.application.Platform.runLater(() -> {
                    data.addAll(newCodesAndPatterns);
                    currentOffset += newCodesAndPatterns.size();
                    isLoading = false;
                });
            } catch (SQLException e) {
                javafx.application.Platform.runLater(() -> isLoading = false);
                throw new RuntimeException(e);
            }

            executor.shutdown();
        });
    }

    private ScrollBar getVerticalScrollbar(TableView<?> tableView) {
        for (Node n : tableView.lookupAll(".scroll-bar")) {
            if (n instanceof ScrollBar) {
                ScrollBar bar = (ScrollBar) n;
                if (bar.getOrientation() == Orientation.VERTICAL) {
                    return bar;
                }
            }
        }
        return null;
    }

    public void show() {
        if (data.isEmpty()) lookUpIterPat(INITIAL_LOAD, 0);  // Load data from database

        this.stage.show();

        if (!scrollBarInitialized) {
            Platform.runLater(() -> {
                ScrollBar scrollBar = getVerticalScrollbar(tableView);

                if (scrollBar != null) {
                    scrollBar.valueProperty().addListener((obs, oldVal, newVal) -> {
                        if (newVal.doubleValue() >= 0.95 && !isLoading) {
                            lookUpIterPat(BATCH_SIZE, currentOffset);
                        }
                    });
                }

                scrollBarInitialized = true;
            });
        }
    }
}
