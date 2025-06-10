package billiards.viewer;

import billiards.database.Admin;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.sql.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CodeAndPatternLookupWindow {
    private static final int INITIAL_LOAD = 10;
    private static final int BATCH_SIZE = 5;
    private final ObservableList<CodeAndPattern> data = FXCollections.observableArrayList();
    private boolean isLoading = false;
    private int currentOffset = 0;
    private boolean scrollBarInitialized = false;

    private final Stage stage = new Stage();
    TableView<CodeAndPattern> tableView = new TableView<>();

    public static class CodeAndPattern {
        private final String codeSequence;
        private final String iterationPattern;

        public CodeAndPattern(String column1, String column2) {
            this.codeSequence = column1;
            this.iterationPattern = column2;
        }

        public String getCodeSequence() {
            return codeSequence;
        }

        public String getIterationPattern() {
            return iterationPattern;
        }
    }

    public CodeAndPatternLookupWindow(IterateToLimitWindow iterateToLimitWindow) {
        // Create columns
        TableColumn<CodeAndPattern, String> col1 = new TableColumn<>("Code Sequence");
        col1.setCellValueFactory(new PropertyValueFactory<>("codeSequence"));
        col1.setPrefWidth(150);

        TableColumn<CodeAndPattern, String> col2 = new TableColumn<>("Iteration Pattern");
        col2.setCellValueFactory(new PropertyValueFactory<>("iterationPattern"));
        col2.setPrefWidth(150);

        tableView.getColumns().add(col1);
        tableView.getColumns().add(col2);
        tableView.setPrefSize(300, 250);

        tableView.setItems(data);

        // Set up the scene
        VBox vbox = new VBox(tableView);
        Scene scene = new Scene(vbox, 300, 250);
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
