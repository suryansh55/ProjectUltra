package billiards.viewer;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javaslang.Tuple2;

import java.util.ArrayList;
import java.util.List;

// Zhao Yu Li, May 15, 2025.
// Opens a new window that allows the input of the (x, y) coordinates of a point, and calculates a tetrahedron based
// on the point.
public final class Tetrahedron {
    // WARNING: Global mutable state
    // ------------------------------------------------------------
    private static String coordsDefault = "";
    private static String epsDefault = "0.00000001";
    // ------------------------------------------------------------

    private final TextArea coordsTextArea = new TextArea();
    private final TextField epsTextField = new TextField();

    private final Stage stage = new Stage();

    private final List<Tuple2<Double, Double>> originalPoints = new ArrayList<>();
    private final List<Tuple2<Double, Double>> points = new ArrayList<>();

    public Tetrahedron() {
        if (coordsDefault.isEmpty()) coordsDefault = Utils.readFromFile("tetrahedron.txt");

        epsTextField.setPromptText("epsilon");

        coordsTextArea.setPromptText("Coordinates");
        coordsTextArea.setText(coordsDefault);
        coordsTextArea.setPrefHeight(300);
        epsTextField.setText(epsDefault);

        final VBox root = new VBox(10);
        final Scene scene = new Scene(root);
        final Button loadButton = new Button();
        final HBox hbox = new HBox(10, epsTextField, loadButton);

        stage.setScene(scene);
        stage.setOnCloseRequest(event -> stage.close());

        Label label = new Label("Enter coordinates on each line. The x and y coordinates should be separated by a whitespace.");
        root.getChildren().addAll(
                label, coordsTextArea, hbox);
        root.setSpacing(10);
        root.setPadding(new Insets(10));

        loadButton.setText("Calculate");
        loadButton.setOnAction(event -> {
            coordsDefault = coordsTextArea.getText();

            epsDefault = epsTextField.getText();

            final String[] coords = coordsTextArea.getText().split("\n");
            final double eps = Double.parseDouble(epsTextField.getText());

            for (String coord : coords) {
                if (coord.startsWith("//") || coord.trim().isEmpty()) continue;

                String[] coordValue = coord.trim().split(" ");

                assert coordValue.length == 2;

                final double x = Double.parseDouble(coordValue[0]);
                final double y = Double.parseDouble(coordValue[1]);

                originalPoints.add(new Tuple2<>(x, y));

                final double x1_out = x - (eps * Math.sqrt(3)/2);
                final double y1_out = y + (eps / 2);
                final double x2_out = x + (eps * Math.sqrt(3)/2);
                final double y2_out = y + (eps / 2);
                final double y3_out = y - eps;

                points.add(new Tuple2<>(x1_out, y1_out));
                points.add(new Tuple2<>(x2_out, y2_out));
                points.add(new Tuple2<>(x, y3_out));
            }

            Utils.writeToFile("tetrahedron.txt", coordsDefault);
            stage.close();
        });
    }

    public Tuple2<List<Tuple2<Double, Double>>, List<Tuple2<Double, Double>>> getTetrahedron() {
        // Wait till the stage is closed
        stage.showAndWait();
        return new Tuple2<>(this.originalPoints, this.points);
    }
}
