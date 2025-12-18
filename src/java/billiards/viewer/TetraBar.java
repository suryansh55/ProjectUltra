package billiards.viewer;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javaslang.Tuple2;
import javaslang.Tuple3;
import javaslang.Tuple6;

import java.util.ArrayList;
import java.util.List;

/**
 * Zhao Yu Li, May 15, 2025.
 * Opens a new window that allows the input of the (x, y) coordinates of a list of point, and calculates the
 * tetrahedrons created from each point.
 * Updated May 16, 2025.
 * Allows the input of multiple coordinates. The x and y values of each coordinate should be in the same line and
 * separated by a singular whitespace character. Different coordinates should be separated by a newline character.
 * Updated May 20, 2025.
 * Added new fields for how many of the results to print, and whether we are calculating a Bar or Tetrahedron.
 * Updated Jun 4, 2025.
 * When this window is opened, can close main window without causing a exception.
 * Added an option to add codes to cover.
 */
public final class TetraBar {
    // WARNING: Global mutable state
    // ------------------------------------------------------------
    private static String coordsDefault = "";
    private static String epsDefault = "0.00000001";
    private static String printCountDefault = "1";
    // ------------------------------------------------------------

    private final TextArea coordsTextArea = new TextArea();
    private final TextField epsTextField = new TextField();
    private final TextField printCountTextField = new TextField();

    private final RadioButton tetraRadio = new RadioButton("Tetra");
    private final RadioButton barRadio = new RadioButton("Bar");

    private final CheckBox drawCheckBox = new CheckBox("Draw");
    private final CheckBox addToCoverCheckBox = new CheckBox("Add to cover");

    private final CheckBox addToAllPositiveCB = new CheckBox();
    private final CheckBox addToPlusMinusCB = new CheckBox();
    final TextField lineNumTextField = new TextField();
    private final String windowTitle = "TetraBar";
    private Integer lineNumber = null;
    private final Viewer viewer;

    private final Stage stage = new Stage();

    private final List<Tuple2<Double, Double>> originalPoints = new ArrayList<>();

    // Just keep tetrahedron points for all the coordinates as a list of tuples. Every three consecutive points belong
    // to a different coordinate.
    private final List<Tuple2<Double, Double>> points = new ArrayList<>();

    private boolean clickedLoad = false;

    public TetraBar(Stage parentStage, Viewer viewer) {
        if (coordsDefault.isEmpty()) coordsDefault = Utils.readFromFile("tetrahedron.txt");

        this.viewer = viewer;

        epsTextField.setPromptText("epsilon");
        epsTextField.setMaxWidth(100);

        printCountTextField.setPromptText("print count");
        printCountTextField.setMaxWidth(50);
        printCountTextField.setText(printCountDefault);

        coordsTextArea.setPromptText("Coordinates");
        coordsTextArea.setText(coordsDefault);
        coordsTextArea.setPrefHeight(300);
        epsTextField.setText(epsDefault);

        ToggleGroup group = new ToggleGroup();
        tetraRadio.setToggleGroup(group);
        tetraRadio.setSelected(true);
        barRadio.setToggleGroup(group);

        drawCheckBox.setSelected(true);
        addToCoverCheckBox.setSelected(true);

        addToAllPositiveCB.setIndeterminate(false);
        addToAllPositiveCB.setAllowIndeterminate(false);
        addToAllPositiveCB.setSelected(false);
        addToAllPositiveCB.setText("Add to all-positive");

        addToPlusMinusCB.setIndeterminate(false);
        addToPlusMinusCB.setAllowIndeterminate(false);
        addToPlusMinusCB.setSelected(false);
        addToPlusMinusCB.setText("Add to plus-minus");

        final VBox root = new VBox(10);
        final Scene scene = new Scene(root);
        final Button loadButton = new Button();
        final HBox hbox = new HBox(10, epsTextField, printCountTextField, tetraRadio, barRadio, drawCheckBox, addToCoverCheckBox, loadButton);
        final HBox bottomHbox = getBottomHBox();

        stage.setScene(scene);

        // Zhao Yu Li, Jun 4, 2025.
        // When closing the main window with this window open, close this window first.
        // Without these two lines, JavaFX will throw an IllegalStateException.
        stage.initModality(Modality.NONE);
        stage.initOwner(parentStage);

        stage.setOnCloseRequest(event -> stage.close());

        Label label = new Label("Enter coordinates on each line. The x and y coordinates should be separated by a whitespace.");
        root.getChildren().addAll(
                label, coordsTextArea, hbox, bottomHbox);
        root.setSpacing(10);
        root.setPadding(new Insets(10));

        loadButton.setText("Calculate");
        loadButton.setOnAction(event -> {
            int printCount;

            try {
                printCount = Integer.parseInt(printCountTextField.getText());
            } catch (Exception e) {
                printCount = -1;
            }

            if (printCount < 0) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("TetraBar Invalid Print Count");
                alert.setHeaderText(null);
                alert.setContentText("Print count must be a non-negative integer.");
                alert.show();
                return;
            }

            originalPoints.clear();
            points.clear();

            coordsDefault = coordsTextArea.getText();
            epsDefault = epsTextField.getText();
            printCountDefault = printCountTextField.getText();

            final String[] coords = coordsTextArea.getText().split("\n");
            final double eps = Double.parseDouble(epsTextField.getText());

            for (String coord : coords) {
                if (coord.startsWith("//") || coord.trim().isEmpty()) continue;

                String[] coordValue = coord.trim().split(" ");

                assert coordValue.length == 2;

                final double x = Double.parseDouble(coordValue[0]);
                final double y = Double.parseDouble(coordValue[1]);

                originalPoints.add(new Tuple2<>(x, y));

                // Coordinate calculations for Tetrahedron
                if (tetraRadio.isSelected()) {
                    final double x1_out = x - (eps * Math.sqrt(3)/2);
                    final double y1_out = y + (eps / 2);
                    final double x2_out = x + (eps * Math.sqrt(3)/2);
                    final double y2_out = y + (eps / 2);
                    final double y3_out = y - eps;

                    points.add(new Tuple2<>(x1_out, y1_out));
                    points.add(new Tuple2<>(x2_out, y2_out));
                    points.add(new Tuple2<>(x, y3_out));
                }

                // Coordinate calculations for Bar
                if (barRadio.isSelected()) {
                    final double x1_out = x - eps;
                    final double x2_out = x + eps;

                    points.add(new Tuple2<>(x1_out, y));
                    points.add(new Tuple2<>(x2_out, y));
                }
            }

            Utils.writeToFile("tetrabar.txt", coordsDefault);

            this.clickedLoad = true;
            stage.close();
        });
    }

    public Tuple6<List<Tuple2<Double, Double>>, List<Tuple2<Double, Double>>, Integer, Integer, Boolean, Boolean> getVaryParams() {
        // Wait till the stage is closed
        stage.showAndWait();
        int step = 0;

        if (clickedLoad) {
            if (tetraRadio.isSelected()) step = 3;
            if (barRadio.isSelected()) step = 2;
            this.clickedLoad = false;
            return new Tuple6<>(this.originalPoints, this.points, step,
                    Integer.parseInt(printCountTextField.getText()), drawCheckBox.isSelected(), addToCoverCheckBox.isSelected());
        } else {
            return new Tuple6<>(this.originalPoints, this.points, -1, -1, false, false);
        }
    }

    private HBox getBottomHBox() {
        final Button backwardButton = new Button("Backward");
        final Button forwardButton = new Button("Forward");
        final Button goToLineButton = new Button("Go");

        lineNumTextField.setPromptText("Line");
        lineNumTextField.setPrefColumnCount(3);

        backwardButton.setOnAction(event -> {
            if (coordsTextArea.getText().trim().isEmpty()) {
                showMoveScreenAlert("Please enter at least one coordinate.");
                return;
            }

            if (lineNumber == null) lineNumber = 1;
            else {
                Tuple3<Integer, Integer, Integer> startStepEnd = viewer.getStartStepEnd(getCoordinatesListLength());
                Integer start = startStepEnd._1;
                Integer step = startStepEnd._2;
                Integer end = startStepEnd._3;

                if (start == null || step == null || end == null) return;

                lineNumber -= step;

                if (lineNumber < start) lineNumber = end;
            }

            lineNumTextField.setText(Integer.toString(lineNumber));
            moveScreenToLine(lineNumber - 1);
        });

        forwardButton.setOnAction(event -> {
            if (coordsTextArea.getText().trim().isEmpty()) {
                showMoveScreenAlert("Please enter at least one coordinate.");
                return;
            }

            if (lineNumber == null) lineNumber = 1;
            else {
                Tuple3<Integer, Integer, Integer> startStepEnd = viewer.getStartStepEnd(getCoordinatesListLength());
                Integer start = startStepEnd._1;
                Integer step = startStepEnd._2;
                Integer end = startStepEnd._3;

                if (start == null || step == null || end == null) return;

                lineNumber += step;

                if (lineNumber > end) lineNumber = start;
            }

            lineNumTextField.setText(Integer.toString(lineNumber));
            moveScreenToLine(lineNumber - 1);
        });

        goToLineButton.setOnAction(event -> {
            if (coordsTextArea.getText().trim().isEmpty()) {
                showMoveScreenAlert("Please enter at least one coordinate.");
                return;
            }

            int userLineNumber = getLineNumber();

            lineNumber = userLineNumber;

            lineNumTextField.setText(Integer.toString(userLineNumber));
            moveScreenToLine(userLineNumber - 1);
        });

        return new HBox(10, backwardButton, lineNumTextField, goToLineButton, forwardButton, addToAllPositiveCB, addToPlusMinusCB);
    }

    public boolean getAddToAllPositiveSelected() {
        return this.addToAllPositiveCB.isSelected();
    }

    public boolean getAddToPlusMinusSelected() {
        return this.addToPlusMinusCB.isSelected();
    }

    /**
     * <b>Zhao Yu Li</b><br>
     * <b>Jul 2, 2025</b>
     * <p>
     *     Extracts the integer entered in <code>textField</code>. Throws a <code>NumberFormatException</code> if what
     *     was entered cannot be parsed as an integer.
     * </p>
     * @param textField The <code>TextField</code> to extract the integer from.
     * @return The extracted integer from <code>textField</code>.
     */
    private int extractNumberFromTextField(TextField textField) {
        int userNumber;
        String numberString = textField.getText().trim();

        if (numberString.isEmpty()) numberString = "1";

        try {
            userNumber = Integer.parseInt(numberString);
        } catch (NumberFormatException e) {
            throw new NumberFormatException();
        }

        return userNumber;
    }

    /**
     * <b>Zhao Yu Li</b><br>
     * <b>Jun 29, 2025</b>
     * <p>
     *     Gets the user entered line number. Defaults to 1 if the user did not enter anything.
     * </p>
     * @return The line number entered by the user, or 1 if the user did not enter anything.
     */
    private int getLineNumber() {
        return extractNumberFromTextField(lineNumTextField);
    }

    /**
     * <b>Zhao Yu Li</b><br>
     * <b>Jun 29, 2025</b>
     * <p>
     *     Displays an information alert dialogue with <code>content</code>.
     * </p>
     * @param content The message to display to the user.
     */
    private void showMoveScreenAlert(String content) {
        final Text alertText = new  Text(content);
        alertText.setWrappingWidth(350);

        final Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle(windowTitle);
        alert.setHeaderText("Move Screen");
        alert.getDialogPane().setContent(alertText);
        alert.getDialogPane().setPadding(new Insets(10));
        alert.getDialogPane().setMaxWidth(400);

        alert.showAndWait();
    }

    /**
     * <b>Zhao Yu Li</b><br>
     * <b>Jun 29, 2025</b>
     * <p>
     *     Center the screen to the <code>index</code>'th coordinate.
     * </p>
     * @param index The index of the coordinate to move to.
     */
    private void moveScreenToLine(int index) {
        if (index < 0) {
            showMoveScreenAlert("Line number must be a positive, non-zero integer.");
            return;
        }

        String[] coordinateStrings =  coordsTextArea.getText().trim().split("\n");

        if (coordinateStrings.length <= index) {
            showMoveScreenAlert("Line number must be between 1 and " + coordinateStrings.length);
            return;
        }

        String[] coordinateString = coordinateStrings[index].trim().split(" ");

        viewer.moveScreen(coordinateString[0], coordinateString[1]);
    }

    /**
     * <b>Zhao Yu Li</b><br>
     * <b>Jun 29, 2025</b>
     * <p>
     *     Sets the line number text field and the internal state to <code>lineNumber</code>.
     * </p>
     * @param lineNumber The line number to set to.
     */
    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
        lineNumTextField.setText(Integer.toString(lineNumber));
    }

    private int getCoordinatesListLength() {
        return coordsTextArea.getText().trim().split("\n").length;
    }
}
