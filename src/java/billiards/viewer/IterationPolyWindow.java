package billiards.viewer;

import billiards.geometry.ConvexPolygon;
import billiards.geometry.Vector2;

import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.list.mutable.FastList;

import java.util.Optional;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;

/**
 * Zhao Yu Li, May 27, 2025.
 * A window for entering the coordinates of a polygon. The polygon will be used to check for intersections with code
 * sequences that result from iteration calculations.
 */
public final class IterationPolyWindow {
    // WARNING: Global mutable state
    // ------------------------------------------------------------
    private static String fullContent = "";
    private static final String fileName = "iterationPoly.txt";
    // ------------------------------------------------------------

    private final TextArea text = new TextArea();
    private final VBox root = new VBox();
    private final HBox loadHBox = new HBox();
    private final Stage stage = new Stage();
    private final Scene scene = new Scene(root);
    private final Label instruct = new Label();

    public IterationPolyWindow() {
        fullContent = Utils.readFromFile(fileName);

        stage.setScene(scene);

        stage.setTitle("Iteration Polygon");
        stage.setOnCloseRequest(event -> {
            Utils.writeToFile(fileName, text.getText());
            stage.close();
        });

        text.setPrefColumnCount(40);
        text.setPrefRowCount(10);
        text.setWrapText(true);
        text.setEditable(true);
        text.setFont(Font.font("Monaco", 16));
        text.setText(fullContent);

        instruct.setText(
                "Enter points on separate lines, with the coordinates separated by a space.");
        instruct.setPadding(new Insets(5, 5, 5, 10));

        // We want the text to expand as we make the window bigger
        VBox.setVgrow(text, Priority.ALWAYS);

        loadHBox.getChildren().addAll(instruct);

        root.getChildren().addAll(loadHBox, text);
        root.setSpacing(10);
        root.setPadding(new Insets(10));
    }

    private static String cleanPolygon(final String polygonString) {
        final String[] lines = polygonString.split("\\R");

        final StringBuilder builder = new StringBuilder();
        for (final String line : lines) {

            final String[] coords = line.trim().replace("(", "").replace(")", "").replace(",", "").split(" ");

            if (coords.length != 2) {
                throw new RuntimeException("invalid polygon line: " + line);
            }

            final String x = coords[0].trim();
            final String y = coords[1].trim();

            builder.append(x).append(' ').append(y).append('\n');
        }

        return builder.toString().trim();
    }

    public void show() {
        this.stage.show();
    }

    public Optional<ConvexPolygon> getPolygon() {
        fullContent = text.getText();
        Optional<ConvexPolygon> result;

        if (fullContent.isEmpty()) {
            result = Optional.empty();
        } else {
            final String cleaned = cleanPolygon(fullContent);
            final String[] lines = cleaned.split("\n");
            final MutableList<Vector2> pointList = new FastList<>();

            for (final String line : lines) {
                final String[] coords = line.split(" ");
                final double x = Math.toRadians(Double.parseDouble(coords[0]));
                final double y = Math.toRadians(Double.parseDouble(coords[1]));
                pointList.add(Vector2.create(x, y));
            }
            final ConvexPolygon poly = ConvexPolygon.create(pointList.toImmutable());
            result = Optional.of(poly);
        }
        Utils.writeToFile(fileName, fullContent);

        return result;
    }
}
