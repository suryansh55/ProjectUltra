package billiards.viewer;

import static billiards.utils.Polygon.cleanPolygon;

import java.util.ArrayList;

import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;

import billiards.wrapper.Wrapper;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javaslang.Tuple2;

public class HoleFinderWindow {
    // Global mutable state
    public static String polygonString = "";

	private final VBox root = new VBox(10);
	public final Stage stage = new Stage();
	private final Scene scene = new Scene(root);
	private final Button findHolesButton = new Button("Find Holes");
    private final Viewer viewer;

    private final TextField magnificationsTextField = new TextField("22");
    private final TextField digitsTextField = new TextField("22");
    private final TextField emptySquaresTextField = new TextField("100");
    private final CodeArea coordinateCodeArea = new CodeArea();


    public HoleFinderWindow(final String cover, final Viewer viewer) {
        this.viewer = viewer;

        HoleFinderWindow.polygonString = Utils.readFromFile(cover);
        // Sync the polygon with the cover polygon
        CoverWindow.polyStringProperty.addListener((o, oldValue, newValue) -> {
            polygonString = newValue;
        });

        stage.setTitle("Hole Finder");
        stage.setScene(scene);


        // Set up the coordinate code area
        coordinateCodeArea.setWrapText(true);
        coordinateCodeArea.setStyle("-fx-font-family: 'monospace'; -fx-font-size: 16px;");
        coordinateCodeArea.setPrefHeight(300);

        coordinateCodeArea.setParagraphGraphicFactory(LineNumberFactory.get(coordinateCodeArea));

        VirtualizedScrollPane<CodeArea> vsPane = new VirtualizedScrollPane<>(coordinateCodeArea);

        // Set up the find holes button
        Utils.colorButton(findHolesButton, Color.LIGHTGRAY, Color.DARKGRAY);
        findHolesButton.setOnAction(this::onFindHolesAction);

        root.setPrefHeight(400);
        root.setPrefWidth(800);
        root.setPadding(new Insets(10));
        root.getChildren().addAll(buildCoordinatesRow(), vsPane, buildButtonRow());
    }

    private void onFindHolesAction(ActionEvent event) {
        // Implement the logic to find holes in the cover polygon here
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

        final String cleanedPolygon = cleanPolygon(polygonString);
        final String cleanedStablesPre = CoverWindow.cleanStables(CoverWindow.stablesString, viewer.pool);
        final Tuple2<String, String> cleanedTriplesPre = CoverWindow.cleanTriples(CoverWindow.triplesString, viewer.pool);

        final String cleanedTriples = cleanedTriplesPre._1;
        final String cleanedStables = (cleanedStablesPre + '\n' + cleanedTriplesPre._2).trim();

        final Task<String> task = new Task<String>() {
            @Override
            protected String call() throws Exception {
                return Wrapper.getNotFilledCoordinates(cleanedPolygon, cleanedStables, cleanedTriples, digits, magnifications, empties, true, viewer.pool.pointer, false);
            }
        };

        final Thread progressThread = new Thread(task);
        final Progress progressWindow = new Progress(task);

        task.setOnSucceeded(success -> {
            final String newCoordinates = task.getValue();
            if(!newCoordinates.isEmpty()) {
                coordinateCodeArea.replaceText(newCoordinates.substring(0, newCoordinates.length() - 1)); // Remove the last newline character
            }
			progressWindow.close();
        });

        task.setOnCancelled(cancelled -> {
            System.err.println("Finding Holes cancelled");
			progressWindow.close();
        });

        task.setOnFailed(failed -> {
            System.err.println("Error finding holes: " + failed.getSource().getException().getMessage());
            final Alert alert = new Alert(AlertType.ERROR);
            alert.setTitle("Find Holes");
            alert.setHeaderText("Error finding holes");
            alert.setContentText(failed.getSource().getException().getMessage());
            alert.showAndWait();
			progressWindow.close();
        });

        // Start the task
        progressThread.start();
        progressWindow.show();
    }

    private HBox buildButtonRow() {
        Label emptySquaresLabel = new Label("Empty Squares:");
        Label magnificationsLabel = new Label("Magnifications:");
        Label digitsLabel = new Label("Digits:");


        HBox buttonRow = new HBox(10);
        buttonRow.getChildren().addAll(magnificationsLabel, magnificationsTextField, digitsLabel, digitsTextField, emptySquaresLabel, emptySquaresTextField, findHolesButton);
        buttonRow.setAlignment(Pos.CENTER_LEFT);
        return buttonRow;
    }

    private HBox buildCoordinatesRow() {
        Label coordinateLabel = new Label("Coordinates:");
        Button copyButton = new Button("Copy");
        copyButton.setOnAction(event -> {
            String coordinates = coordinateCodeArea.getText();
            Utils.copyToClipboard(coordinates);
        });

        Button clearCoordinatesButton = new Button("Clear");
        clearCoordinatesButton.setOnAction(event -> {
            coordinateCodeArea.clear();
        });

        Button loadCoordinatesButton = new Button("Export as OBO");
        loadCoordinatesButton.setOnAction(event -> {
            String coordinates = coordinateCodeArea.getText();
            this.viewer.loadHolesAsOBO(coordinates);
            Alert alert = new Alert(AlertType.INFORMATION);
            alert.setTitle("Load Holes");
            alert.setHeaderText(null);
            alert.setContentText("Holes loaded successfully as OBO.");
            alert.showAndWait();
        });

        HBox coordinatesHBox = new HBox(10, coordinateLabel, copyButton, clearCoordinatesButton, loadCoordinatesButton);
        coordinatesHBox.setAlignment(Pos.CENTER_LEFT);
        return coordinatesHBox;
    }

    public void show() {
        stage.show();
    }
}
