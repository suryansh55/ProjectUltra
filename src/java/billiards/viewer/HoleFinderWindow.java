package billiards.viewer;

import static billiards.utils.Polygon.cleanPolygon;

import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;

import billiards.patch.CoverableRegion;
import billiards.wrapper.Wrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import javaslang.Tuple2;

/**
 * <b>Jeff Khuu</b><br>
 * <b>Aug 10, 2026</b>
 * <p>
 * <code>HoleFinderWindow</code> is a JavaFX window that allows users to find holes in a cover polygon based on the
 * provided coordinates, stables, and triples. It provides a user interface for inputting parameters such as digits,
 * magnifications, and empty squares, and displays the resulting coordinates in a code area. The window also allows
 * users to copy, clear, or load the coordinates as OBO.
 * </p>
 */
public class HoleFinderWindow {

    private String polygonString = "";
    private final ObservableList<CoverableRegion> coverableRegions = FXCollections.observableArrayList();

    private final VBox root = new VBox(10);
    public final Stage stage = new Stage();
    private final Scene scene = new Scene(root);
    private final Button findHolesButton = new Button("Find Holes");
    private final Viewer viewer;

    private final TextField magnificationsTextField = new TextField("22");
    private final TextField digitsTextField = new TextField("22");
    private final TextField emptySquaresTextField = new TextField("100");
    private final CodeArea coordinateCodeArea = new CodeArea();
    private final ComboBox<CoverableRegion> polygonBox = new ComboBox<>(coverableRegions);

    public HoleFinderWindow(final Viewer viewer) {
        this.viewer = viewer;

        stage.setTitle("Hole Finder");
        stage.setScene(scene);

        // Set up the coordinate code area
        coordinateCodeArea.setWrapText(true);
        coordinateCodeArea.setStyle("-fx-font-family: 'monospace'; -fx-font-size: 16px;");
        coordinateCodeArea.setPrefHeight(300);

        coordinateCodeArea.setParagraphGraphicFactory(LineNumberFactory.get(coordinateCodeArea));

        final VirtualizedScrollPane<CodeArea> vsPane = new VirtualizedScrollPane<>(coordinateCodeArea);

        // Set up the find holes button
        Utils.colorButton(findHolesButton, Color.LIGHTGRAY, Color.DARKGRAY);
        findHolesButton.setOnAction(this::onFindHolesAction);

        polygonBox.setConverter(new StringConverter<CoverableRegion>() {
            @Override
            public String toString(final CoverableRegion object) {
                return object == null ? "" : object.name;
            }

            @Override
            public CoverableRegion fromString(final String string) {
                return coverableRegions.stream()
                        .filter(region -> region.name.equals(string))
                        .findFirst().orElse(null);
            }
        });
        polygonBox.setOnAction(event -> {
            final CoverableRegion selectedRegion = polygonBox.getSelectionModel().getSelectedItem();
            if (selectedRegion != null) {
                polygonString = selectedRegion.polygon;
            }
        });

        refreshPolygonBox();

        root.setPrefHeight(400);
        root.setPrefWidth(800);
        root.setPadding(new Insets(10));
        root.getChildren().addAll(buildCoordinatesRow(), vsPane, buildButtonRow());
    }

    private void onFindHolesAction(final ActionEvent event) {
        final int digits;
        final int magnifications;
        final int empties;
        try {
            digits = Integer.parseInt(digitsTextField.getText().trim());
            magnifications = Integer.parseInt(magnificationsTextField.getText().trim());
            empties = Integer.parseInt(emptySquaresTextField.getText().trim());
        } catch (final NumberFormatException e) {
            System.err.println("Error parsing digits or magnifications: " + e.getMessage());
            final Alert alert = new Alert(AlertType.ERROR);
            alert.setTitle("Find Holes");
            alert.setHeaderText("Error parsing digits or magnifications");
            alert.setContentText(e.getMessage());
            alert.showAndWait();
            return;
        }

        if (polygonString.trim().isEmpty()) {
            final Alert alert = new Alert(AlertType.ERROR);
            alert.setTitle("Find Holes");
            alert.setHeaderText("No polygon selected");
            alert.setContentText("Pick a cover polygon or patch, then press Refresh if the list looks stale.");
            alert.showAndWait();
            return;
        }

        final String cleanedPolygon = cleanPolygon(polygonString);
        final String cleanedStablesPre = CoverWindow.cleanStables(CoverWindow.stablesString, viewer.pool);
        final Tuple2<String, String> cleanedTriplesPre =
                CoverWindow.cleanTriples(CoverWindow.triplesString, viewer.pool);

        final String cleanedTriples = cleanedTriplesPre._1;
        final String cleanedStables = (cleanedStablesPre + '\n' + cleanedTriplesPre._2).trim();

        final Task<String> task = new Task<String>() {
            @Override
            protected String call() throws Exception {
                return Wrapper.getNotFilledCoordinates(cleanedPolygon, cleanedStables, cleanedTriples,
                        digits, magnifications, empties, true, viewer.pool.pointer, false);
            }
        };

        final Thread progressThread = new Thread(task, "hole-finder");
        progressThread.setDaemon(true);
        final Progress progressWindow = new Progress(task);

        task.setOnSucceeded(success -> {
            final String newCoordinates = task.getValue();
            if (newCoordinates != null && !newCoordinates.isEmpty()) {
                // Drop the trailing newline so the code area does not show a blank last line.
                coordinateCodeArea.replaceText(newCoordinates.endsWith("\n")
                        ? newCoordinates.substring(0, newCoordinates.length() - 1)
                        : newCoordinates);
            } else {
                coordinateCodeArea.replaceText("");
            }
            progressWindow.close();
        });

        task.setOnCancelled(cancelled -> {
            System.err.println("Finding Holes cancelled");
            progressWindow.close();
        });

        task.setOnFailed(failed -> {
            final Throwable failure = task.getException();
            System.err.println("Error finding holes: " + (failure == null ? "unknown" : failure.getMessage()));
            final Alert alert = new Alert(AlertType.ERROR);
            alert.setTitle("Find Holes");
            alert.setHeaderText("Error finding holes");
            alert.setContentText(failure == null ? "Unknown error" : failure.getMessage());
            alert.showAndWait();
            progressWindow.close();
        });

        // Start the task
        progressThread.start();
        progressWindow.show();
    }

    private HBox buildButtonRow() {
        final Label emptySquaresLabel = new Label("Empty Squares:");
        final Label magnificationsLabel = new Label("Magnifications:");
        final Label digitsLabel = new Label("Digits:");

        final HBox buttonRow = new HBox(10);
        buttonRow.getChildren().addAll(digitsLabel, digitsTextField, magnificationsLabel, magnificationsTextField,
                emptySquaresLabel, emptySquaresTextField, findHolesButton);
        buttonRow.setAlignment(Pos.CENTER_LEFT);
        return buttonRow;
    }

    private HBox buildCoordinatesRow() {
        final Label coordinateLabel = new Label("Coordinates:");
        final Button copyButton = new Button("Copy");
        copyButton.setOnAction(event -> Utils.copyToClipboard(coordinateCodeArea.getText()));

        final Button clearCoordinatesButton = new Button("Clear");
        clearCoordinatesButton.setOnAction(event -> coordinateCodeArea.clear());

        final Button loadCoordinatesButton = new Button("Load as OBO");
        loadCoordinatesButton.setOnAction(event -> {
            final String coordinates = coordinateCodeArea.getText();
            if (coordinates.trim().isEmpty()) {
                final Alert alert = new Alert(AlertType.ERROR);
                alert.setTitle("Load Holes");
                alert.setHeaderText("Nothing to load");
                alert.setContentText("Run Find Holes first, or paste coordinates into the box.");
                alert.showAndWait();
                return;
            }
            this.viewer.loadHolesAsOBO(coordinates);
            final Alert alert = new Alert(AlertType.INFORMATION);
            alert.setTitle("Load Holes");
            alert.setHeaderText(null);
            alert.setContentText("Holes loaded successfully as OBO.");
            alert.showAndWait();
        });

        final Label polygonLabel = new Label("Polygon: ");
        final Button polygonRefreshButton = new Button("Refresh");
        polygonRefreshButton.setOnAction(event -> refreshPolygonBox());

        final HBox coordinatesHBox = new HBox(10, coordinateLabel, copyButton, clearCoordinatesButton,
                loadCoordinatesButton, polygonLabel, polygonBox, polygonRefreshButton);
        coordinatesHBox.setAlignment(Pos.CENTER_LEFT);
        return coordinatesHBox;
    }

    /**
     * Rebuilds the polygon choices from the current cover polygon plus whatever
     * patches exist right now. The list backs the ComboBox directly, so it has to
     * be cleared first or every refresh would duplicate every entry.
     */
    private void refreshPolygonBox() {
        coverableRegions.clear();
        coverableRegions.add(new CoverableRegion("Cover Polygon", CoverWindow.polygonString));
        coverableRegions.addAll(viewer.patchWindow.getCoverableRegions());
        polygonBox.getSelectionModel().selectFirst();

        final CoverableRegion selected = polygonBox.getSelectionModel().getSelectedItem();
        polygonString = selected == null ? "" : selected.polygon;
    }

    public void show() {
        stage.show();
        stage.toFront();
    }
}
