package billiards.viewer;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javaslang.Tuple2;

public final class Tetrahedron {
    // WARNING: Global mutable state
    // ------------------------------------------------------------
    private static String xDefault = "";
    private static String yDefault = "";
    private static String epsDefault = "0.00000001";
    // ------------------------------------------------------------

    private final TextField xTextField = new TextField();
    private final TextField yTextField = new TextField();
    private final TextField epsTextField = new TextField();

    private final Stage stage = new Stage();

    private final Tuple2<Double, Double>[] points = new Tuple2[3];

    public Tetrahedron() {
        xTextField.setPromptText("X coordinate");
        yTextField.setPromptText("Y coordinate");
        epsTextField.setPromptText("epsilon");

        xTextField.setText(xDefault);
        yTextField.setText(yDefault);
        epsTextField.setText(epsDefault);

        final HBox root = new HBox();
        final Scene scene = new Scene(root);
        final Button loadButton = new Button();

        stage.setScene(scene);
        stage.setOnCloseRequest(event -> stage.close());

        root.getChildren().addAll(
                xTextField, yTextField, epsTextField,loadButton);
        root.setSpacing(10);
        root.setPadding(new Insets(10));

        loadButton.setText("Calculate");
        loadButton.setOnAction(event -> {
            xDefault = xTextField.getText();
            yDefault = yTextField.getText();
            epsDefault = epsTextField.getText();

            final double x = Double.parseDouble(xTextField.getText());
            final double y = Double.parseDouble(yTextField.getText());
            final double eps = Double.parseDouble(epsTextField.getText());

            final double x1_out = x - (eps * Math.sqrt(3)/2);
            final double y1_out = y + (eps / 2);
            final double x2_out = x + (eps * Math.sqrt(3)/2);
            final double y2_out = y + (eps / 2);
            final double y3_out = y - eps;

            points[0] = new Tuple2<>(x1_out, y1_out);
            points[1] = new Tuple2<>(x2_out, y2_out);
            points[2] = new Tuple2<>(x, y3_out);

            stage.close();
        });
    }

    public Tuple2<Double, Double>[] getTetrahedron() {
        // Wait till the stage is closed
        stage.showAndWait();
        return this.points;
    }
}
