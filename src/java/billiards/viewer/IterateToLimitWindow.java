package billiards.viewer;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

public class IterateToLimitWindow {
    private final TextArea polygonTextArea = new TextArea();
    private final TextArea codePatternTextArea = new TextArea();

    private final Button lookupButton = new Button();

    private final TextField limitTextField = new TextField();

    private final CheckBox drawCheckbox = new CheckBox();
    private final CheckBox coverCheckbox = new CheckBox();

    private final Button runButton = new Button();

    private final Stage stage = new Stage();

    public IterateToLimitWindow() {
        VBox root = getRoot();
        final Scene scene = new Scene(root);

        stage.setScene(scene);
        stage.setTitle("Iterate To Limit Window");
        stage.setOnCloseRequest(event -> stage.close());

        root.setPadding(new Insets(10));

        polygonTextArea.setTooltip(new Tooltip(
                "Enter each (x,y) coordinate in a separate line, with a whitespace separating the x and y coordinates.")
        );
        polygonTextArea.setWrapText(true);

        codePatternTextArea.setTooltip(getCodePatternTextAreaTooltip());
        codePatternTextArea.setWrapText(true);

        lookupButton.setText("Lookup");

        limitTextField.setPrefColumnCount(4);

        drawCheckbox.setSelected(true);
        drawCheckbox.setText("Draw");

        coverCheckbox.setSelected(true);
        coverCheckbox.setText("Add To Cover");

        runButton.setText("Run");
    }

    private static Tooltip getCodePatternTextAreaTooltip() {
        Tooltip codePatternTextAreaTooltip = new Tooltip(
                "Enter each code sequence - iteration pattern pair in a separate line, with a semicolon " +
                        "separating the code sequence and the pattern. If the code sequence is a triple, then " +
                        "separate the components with a comma. The code sequence and its iteration pattern should" +
                        "match in the number of components."
        );
        codePatternTextAreaTooltip.setWrapText(true);
        codePatternTextAreaTooltip.setPrefWidth(400);
        return codePatternTextAreaTooltip;
    }

    private VBox getRoot() {
        final Label polygonLabel = new Label("Polygon:");
        final Button clearPolygonButton = new Button("Clear");
        final HBox polygonHBox = new HBox(10, polygonLabel, clearPolygonButton);

        polygonLabel.setFont(Font.font("Verdana", FontWeight.BOLD, 16));
        clearPolygonButton.setOnAction(event -> polygonTextArea.setText(""));

        final Label codePatternLabel = new Label("Code and Iteration Pattern:");
        final Button clearCodePatternButton = new Button("Clear");
        final HBox codePatternHBox = new HBox(10, codePatternLabel, clearCodePatternButton);

        codePatternLabel.setFont(Font.font("Verdana", FontWeight.BOLD, 16));
        clearCodePatternButton.setOnAction(event -> codePatternTextArea.setText(""));

        HBox toolsHBox = new HBox(10, lookupButton, limitTextField, drawCheckbox, coverCheckbox, runButton);
        return new VBox(10, polygonHBox, polygonTextArea, codePatternHBox, codePatternTextArea, toolsHBox);
    }

    public void show() {
        stage.show();
    }
}
