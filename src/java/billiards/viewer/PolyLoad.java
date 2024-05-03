package billiards.viewer;

import billiards.geometry.ConvexPolygon;
import billiards.geometry.Rectangle;
import billiards.geometry.Vector2;

import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.list.mutable.FastList;

import java.util.Optional;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;

public final class PolyLoad {
    // WARNING: Global mutable state
    // ------------------------------------------------------------
    private static String fullContent = "";
    // ------------------------------------------------------------

    private final TextArea text = new TextArea();
    private final Button loadButton = new Button();
    private final VBox root = new VBox();
    private final HBox loadHBox = new HBox();
    private final Stage stage = new Stage();
    private final Scene scene = new Scene(root);
    private final Label instruct = new Label();

    private Optional<ConvexPolygon> result;

    public PolyLoad(final String windowTitle, final String buttonText, final String fileName,
                    final Rectangle fullScreen) {

        fullContent = Utils.readFromFile(fileName);

        stage.setScene(scene);

        stage.setTitle(windowTitle);
        stage.setOnCloseRequest(event -> {
            this.result = Optional.empty();
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

        loadHBox.getChildren().addAll(loadButton, instruct);

        root.getChildren().addAll(loadHBox, text);
        root.setSpacing(10);
        root.setPadding(new Insets(10));

        loadButton.setText(buttonText);
        Utils.colorButton(loadButton, Color.SKYBLUE, Color.GOLD);
        loadButton.setOnAction(event -> {

            fullContent = text.getText();
            if (fullContent.equals("")) {
                this.result = Optional.of(fullScreen.toConvexPolygon());

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
                this.result = Optional.of(poly);
            }
            Utils.writeToFile(fileName, fullContent);

            stage.close();
        });
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

    public Optional<ConvexPolygon> getPolyLoad() {
        stage.showAndWait();
        return this.result;
    }
}
