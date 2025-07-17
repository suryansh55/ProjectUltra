package billiards.viewer;

import javafx.scene.control.*;
import javafx.scene.text.Text;

import java.io.File;
import java.util.ArrayList;

import billiards.geometry.ConvexPolygon;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javaslang.Tuple;
import javaslang.Tuple3;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;

import static billiards.utils.Polygon.cleanPolygon;
import static billiards.utils.Polygon.createConvexPolygon;
import static billiards.viewer.Viewer.parseOBOFile;

public class CycleVaryWindow {
    // WARNING: Global mutable state
    // ------------------------------------------------------------
    public static String polygonString = "";
    public static Integer BoundCSMax = 300;
    public static Integer BoundOSOMax = 50;
    public static Integer BoundOSNOMax = 36;
    public static Integer BoundCSMaxSS = 800;
    public static Integer BoundOSOMaxSS = 300;
    public static Integer BoundOSNOMaxSS = 150;
    public static Integer BoundCSstep = 0;
    public static Integer BoundOSOstep = 0;
    public static Integer BoundOSNOstep = 0;
    public static Integer Reps = 0;
    public static Boolean ColorCycle = true;
    public static Boolean AutoCover = true;
    // ------------------------------------------------------------

    private final TextArea polygonText = new TextArea();
    private final TextField CSbox = new TextField();
    private final TextField OSObox = new TextField();
    private final TextField OSNObox = new TextField();
    private final TextField CSsbox = new TextField();
    private final TextField OSOsbox = new TextField();
    private final TextField OSNOsbox = new TextField();
    private final TextField CSstepbox = new TextField();
    private final TextField OSOstepbox = new TextField();
    private final TextField OSNOstepbox = new TextField();
    private final CheckBox colorCycleBox = new CheckBox();
    private final CheckBox autoCoverBox = new CheckBox();
    private final TextField repBox = new TextField();
    public final Stage stage = new Stage();
    private final CheckBox magnifyCheckBox = new CheckBox();
    private final TextField magnifyTextField = new TextField();
    private final CheckBox useRepsCheckBox = new CheckBox();

    private final RadioButton regularModeRadioButton = new RadioButton("Regular");
    private final RadioButton middleModeRadioButton = new RadioButton("Middle");
    private final RadioButton firstMidLastModeRadioButton = new RadioButton("First, Middle, Last");
    private final ToggleGroup modesToggleGroup = new ToggleGroup();
    private final TextField numToPrintTextField = new TextField();

    private final CheckBox addToAllPositiveCheckbox = new CheckBox();
    private final CheckBox addToPlusMinusCheckbox = new CheckBox();

    private final CodeArea coordinateCodeArea = new CodeArea();

    private final TextField lineNumTextField = new TextField();
    private Integer lineNumber = null;

    private final TextField startTextField = new TextField();
    private final TextField stepTextField = new TextField();
    private final TextField endTextField = new TextField();

    private final TextField cyclesTextfield = new TextField();

    private final Viewer viewer;

    public CycleVaryWindow(final String windowTitle, final String buttonText, final String fileName, final String boundsFileName, final String stepFileName, final String coordsFileName, final Viewer viewer) {
        this.viewer = viewer;
        polygonString = Utils.readFromFile(fileName);
        String[] boundTokens = Utils.readFromFile(boundsFileName).trim().split(" ");
        String[] stepTokens = Utils.readFromFile(stepFileName).trim().split(" ");
        if (boundTokens.length >= 9) {
            try {
                BoundCSstep = Integer.parseInt(boundTokens[6]);
                BoundOSOstep = Integer.parseInt(boundTokens[7]);
                BoundOSNOstep = Integer.parseInt(boundTokens[8]);
            } catch (NumberFormatException e) {
                BoundCSstep = 0;
                BoundOSOstep = 0;
                BoundOSNOstep = 0;
            }
        }
        if (boundTokens.length >= 6) {
            try {
                BoundCSMaxSS = Integer.parseInt(boundTokens[3]);
                BoundOSOMaxSS = Integer.parseInt(boundTokens[4]);
                BoundOSNOMaxSS = Integer.parseInt(boundTokens[5]);
            } catch (NumberFormatException e) {
                BoundCSMaxSS = 222;
                BoundOSOMaxSS = 222;
                BoundOSNOMaxSS = 222;
            }
        }
        if (stepTokens.length >= 3) {
            try {
                BoundCSstep = Integer.parseInt(boundTokens[0]);
                BoundOSOstep = Integer.parseInt(boundTokens[1]);
                BoundOSNOstep = Integer.parseInt(boundTokens[2]);
            } catch (NumberFormatException e) {
                BoundCSstep = 0;
                BoundOSOstep = 0;
                BoundOSNOstep = 0;
            }
        }

        VBox root = new VBox();
        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.setTitle(windowTitle);

        polygonText.setPrefColumnCount(40);
        polygonText.setPrefRowCount(5);
        polygonText.setWrapText(true);
        polygonText.setEditable(false);
        polygonText.setFont(Font.font("Monaco", 16));
        polygonText.setText(polygonString);
        VBox.setVgrow(polygonText, Priority.ALWAYS);

        HBox coordinatesHBox = getCoordinatesHBox();

        coordinateCodeArea.setWrapText(true);
        coordinateCodeArea.setStyle("-fx-font-family: 'monospace'; -fx-font-size: 16px;");
        coordinateCodeArea.replaceText(Utils.readFromFile(coordsFileName));
        coordinateCodeArea.setPrefHeight(300);

        coordinateCodeArea.setParagraphGraphicFactory(LineNumberFactory.get(coordinateCodeArea));

        VirtualizedScrollPane<CodeArea> vsPane = new VirtualizedScrollPane<>(coordinateCodeArea);

        // Sync the polygon with the cover polygon
        CoverWindow.polyStringProperty.addListener((o, oldValue, newValue) -> {
            polygonString = newValue;
            polygonText.setText(polygonString);
        });

        Label instruct = new Label();
        instruct.setText("The following polygon is synced with the current cover");
        instruct.setPadding(new Insets(5, 5, 5, 10));

        Label codel = new Label();
        codel.setText("Code length:");
        CSbox.setPrefWidth(150);
        CSbox.setText(BoundCSMax.toString());
        Label CSl = new Label();
        CSl.setText("CS max:");
        OSObox.setPrefWidth(150);
        OSObox.setText(BoundOSOMax.toString());
        Label OSOl = new Label();
        OSOl.setText("OSO max:");
        OSNObox.setPrefWidth(150);
        OSNObox.setText(BoundOSNOMax.toString());
        Label OSNOl = new Label();
        OSNOl.setText("OSNO max:");

        Label ssuml = new Label();
        ssuml.setText("Side sum:");
        CSsbox.setPrefWidth(150);
        CSsbox.setText(BoundCSMaxSS.toString());
        Label CSsl = new Label();
        CSsl.setText("CS max:");
        OSOsbox.setPrefWidth(150);
        OSOsbox.setText(BoundOSOMaxSS.toString());
        Label OSOsl = new Label();
        OSOsl.setText("OSO max:");
        OSNOsbox.setPrefWidth(150);
        OSNOsbox.setText(BoundOSNOMaxSS.toString());
        Label OSNOsl = new Label();
        OSNOsl.setText("OSNO max:");

        Label stepl = new Label();
        stepl.setText("SS step:");
        CSstepbox.setPrefWidth(150);
        CSstepbox.setText(BoundCSstep.toString());
        Label CSstepl = new Label();
        CSstepl.setText("CS step:");
        OSOstepbox.setPrefWidth(150);
        OSOstepbox.setText(BoundOSOstep.toString());
        Label OSOstepl = new Label();
        OSOstepl.setText("OSO step:");
        OSNOstepbox.setPrefWidth(150);
        OSNOstepbox.setText(BoundOSNOstep.toString());
        Label OSNOstepl = new Label();
        OSNOstepl.setText("OSNO step:");

        Label repl = new Label();
        repl.setText("Reps");
        repBox.setPrefWidth(50);
        repBox.setText(Reps.toString());

        colorCycleBox.setIndeterminate(false);
        colorCycleBox.setAllowIndeterminate(false);
        colorCycleBox.setSelected(ColorCycle);
        colorCycleBox.setText("Cycle colors");

        autoCoverBox.setIndeterminate(false);
        autoCoverBox.setAllowIndeterminate(false);
        autoCoverBox.setSelected(AutoCover);
        autoCoverBox.setText("Add codes to cover");

        addToAllPositiveCheckbox.setIndeterminate(false);
        addToAllPositiveCheckbox.setAllowIndeterminate(false);
        addToAllPositiveCheckbox.setSelected(false);
        addToAllPositiveCheckbox.setText("Add to all-positive");

        addToPlusMinusCheckbox.setIndeterminate(false);
        addToPlusMinusCheckbox.setAllowIndeterminate(false);
        addToPlusMinusCheckbox.setSelected(false);
        addToPlusMinusCheckbox.setText("Add to plus/minus");

        HBox instructHBox = new HBox();
        instructHBox.getChildren().add(instruct);

        HBox maxHBox = new HBox(10);
        maxHBox.getChildren().addAll(codel, CSl, CSbox, OSOl, OSObox, OSNOl, OSNObox);
        maxHBox.setPadding(new Insets(0, 10, 10, 0));
        maxHBox.setAlignment(Pos.CENTER);

        HBox maxOptHBox = new HBox(10);
        maxOptHBox.getChildren().addAll(ssuml, CSsl, CSsbox, OSOsl, OSOsbox, OSNOsl, OSNOsbox);
        maxOptHBox.setPadding(new Insets(0, 10, 10, 0));
        maxOptHBox.setAlignment(Pos.CENTER);

        useRepsCheckBox.setIndeterminate(false);
        useRepsCheckBox.setAllowIndeterminate(false);
        useRepsCheckBox.setSelected(false);
        useRepsCheckBox.setText("Use Reps");

        HBox maxStepHBox = new HBox(10);
        maxStepHBox.getChildren().addAll(stepl, CSstepl, CSstepbox, OSOstepl, OSOstepbox, OSNOstepl, OSNOstepbox);
        maxStepHBox.setPadding(new Insets(0, 10, 10, 0));
        maxStepHBox.setAlignment(Pos.CENTER);

        // Zhao Yu Li, Jul 8, 2025.
        // Optional magnification after every rep, and arbitrary magnification
        magnifyTextField.setPrefColumnCount(3);
        magnifyTextField.setText("2");

        magnifyCheckBox.setText("Magnification:");

        HBox repsHBox = new HBox(10, useRepsCheckBox, repl, repBox, magnifyCheckBox, magnifyTextField);
        repsHBox.setAlignment(Pos.CENTER_LEFT);

        Label superAustinControlLabel = new Label("Super Austin Vary Controls");
        VBox superAustinControlVBox = new VBox(10, superAustinControlLabel, repsHBox, maxStepHBox);

        VBox maxVBox = new VBox(10);
        maxVBox.getChildren().addAll(maxHBox, maxOptHBox, superAustinControlVBox);
        Button loadButton = new Button();

        //controlVBox.getChildren().addAll(loadHBox, overrideBox, autoCoverBox);
        HBox controlHBox = new HBox(20);
        controlHBox.getChildren().addAll(autoCoverBox, colorCycleBox, addToAllPositiveCheckbox, addToPlusMinusCheckbox);
        controlHBox.setPadding(new Insets(0, 10, 10, 0));
        controlHBox.setAlignment(Pos.CENTER_LEFT);

        Label cyclesLabel = new Label("Cycles:");
        cyclesTextfield.setPromptText("Cycles");
        cyclesTextfield.setPrefWidth(60);
        cyclesTextfield.setText("1");

        HBox cyclesHBox = new  HBox(10, cyclesLabel, cyclesTextfield, loadButton);
        cyclesHBox.setAlignment(Pos.CENTER_LEFT);

        root.getChildren().addAll(instructHBox, polygonText, coordinatesHBox, vsPane, getLineNavigateHBox(), maxVBox, getModesHBox(), controlHBox, cyclesHBox);
        root.setSpacing(10);
        root.setPadding(new Insets(10));

        loadButton.setText(buttonText);
        Utils.colorButton(loadButton, Color.SKYBLUE, Color.GOLD);
        loadButton.setOnAction(event -> {
            ColorCycle = colorCycleBox.isSelected();
            AutoCover = autoCoverBox.isSelected();
            try {
                Reps = Integer.parseInt(repBox.getText().trim());
                BoundCSMax = Integer.parseInt(CSbox.getText().trim());
                BoundOSOMax = Integer.parseInt(OSObox.getText().trim());
                BoundOSNOMax = Integer.parseInt(OSNObox.getText().trim());
                BoundCSMaxSS = Integer.parseInt(CSsbox.getText().trim());
                BoundOSOMaxSS = Integer.parseInt(OSOsbox.getText().trim());
                BoundOSNOMaxSS = Integer.parseInt(OSNOsbox.getText().trim());
                BoundCSstep = Integer.parseInt(CSstepbox.getText().trim());
                BoundOSOstep = Integer.parseInt(OSOstepbox.getText().trim());
                BoundOSNOstep = Integer.parseInt(OSNOstepbox.getText().trim());
            } catch (NumberFormatException e) {
                final Alert alert = new Alert(AlertType.ERROR);
                alert.setTitle("AutoPolyVary Error");
                alert.setHeaderText("Non-integer value in input box");
                alert.setContentText("Please enter a single integer into each of the '[SequenceType] Max' boxes.");
                alert.showAndWait();
                return;
            }
            polygonString = polygonText.getText();
            final String lines = cleanPolygon(polygonString);
            final ConvexPolygon poly = createConvexPolygon(lines);
            //Utils.writeToFile(fileName, polygonString);
            Utils.writeToFile(boundsFileName, String.format("%d %d %d %d %d %d %d %d %d", BoundCSMax, BoundOSOMax, BoundOSNOMax, BoundCSMaxSS, BoundOSOMaxSS, BoundOSNOMaxSS, BoundCSstep, BoundOSOstep, BoundOSNOstep));
            Utils.writeToFile(coordsFileName, coordinateCodeArea.getText());
            stage.close();
        });
    }

    private HBox getCoordinatesHBox() {
        Label coordinateLabel = new Label("Coordinates:");
        Button loadOBO = new Button("Load OBO");
        loadOBO.setOnAction(event -> {
            final FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Load One By One File");
            final File file = fileChooser.showOpenDialog(stage);

            if (file != null) {
                ArrayList<String> fileCodeSequences = parseOBOFile(file.toPath());
                StringBuilder content = new StringBuilder();

                for (String line : fileCodeSequences) {
                    content.append(line).append('\n');
                }

                coordinateCodeArea.replaceText(content.toString() + coordinateCodeArea.getText());
            }
        });

        Button clearCoordinatesButton = new Button("Clear");
        clearCoordinatesButton.setOnAction(event -> {
            coordinateCodeArea.clear();
        });

        HBox coordinatesHBox = new HBox(10, coordinateLabel, loadOBO, clearCoordinatesButton);
        coordinatesHBox.setAlignment(Pos.CENTER_LEFT);
        return coordinatesHBox;
    }

    public void show() {
        stage.show();
    }

    public boolean getMagnificationIsSelected() {
        return this.magnifyCheckBox.isSelected();
    }

    public Double getMagnification() {
        double magnification;

        try {
            magnification = Double.parseDouble(magnifyTextField.getText().trim());
        } catch (NumberFormatException e) {
            throw new RuntimeException(e);
        }

        if (magnification <= 0) {
            Text alertText = new Text("Magnification must be a positive value.");
            alertText.setWrappingWidth(350);

            Alert alert = new Alert(AlertType.INFORMATION);
            alert.getDialogPane().setContent(alertText);
            alert.getDialogPane().setPadding(new Insets(10));
            alert.getDialogPane().setPrefWidth(400);
            alert.showAndWait();
            return null;
        }

        return magnification;
    }

    public boolean getUseReps() {
        return useRepsCheckBox.isSelected();
    }

    public boolean allPositiveIsSelected() {
        return this.addToAllPositiveCheckbox.isSelected();
    }

    public boolean plusMinusIsSelected() {
        return this.addToPlusMinusCheckbox.isSelected();
    }

    private HBox getModesHBox() {
        regularModeRadioButton.setToggleGroup(modesToggleGroup);
        middleModeRadioButton.setToggleGroup(modesToggleGroup);
        firstMidLastModeRadioButton.setToggleGroup(modesToggleGroup);

        regularModeRadioButton.setSelected(true);

        numToPrintTextField.setPrefColumnCount(3);
        numToPrintTextField.setText("2");

        return new HBox(10, regularModeRadioButton, middleModeRadioButton, firstMidLastModeRadioButton, numToPrintTextField);
    }

    public int getMode() {
        if (regularModeRadioButton.isSelected()) return 0;
        if (middleModeRadioButton.isSelected()) return 1;
        if (firstMidLastModeRadioButton.isSelected()) return 2;
        return -1;
    }

    public Integer getNumGroupToPrint() {
        if (numToPrintTextField.getText().trim().isEmpty()) {
            Text text = new Text("Please enter a non-negative integer");
            text.setWrappingWidth(350);

            Alert alert = new Alert(AlertType.INFORMATION);
            alert.setTitle("AutoPolyVary Error");
            alert.setHeaderText("Empty field");
            alert.getDialogPane().setContent(text);
            alert.getDialogPane().setPrefWidth(400);
            alert.showAndWait();
            return null;
        }

        int numGroupToPrint;

        try {
            numGroupToPrint =  Integer.parseInt(numToPrintTextField.getText());
        } catch (NumberFormatException e) {
            throw new NumberFormatException();
        }

        if (numGroupToPrint < 0) {
            Text text = new Text("Please enter a non-negative integer");
            text.setWrappingWidth(350);

            Alert alert = new Alert(AlertType.INFORMATION);
            alert.setTitle("AutoPolyVary Error");
            alert.setHeaderText("Negative integer value for the number of groups to print");
            alert.getDialogPane().setContent(text);
            alert.getDialogPane().setPrefWidth(400);
            alert.showAndWait();
            return null;
        }

        return numGroupToPrint;
    }

    private HBox getLineNavigateHBox() {
        final Button backwardButton = new Button("Backward");
        final Button forwardButton = new Button("Forward");
        final Button goToLineButton = new Button("Go");

        lineNumTextField.setPromptText("Line");
        lineNumTextField.setPrefColumnCount(3);

        backwardButton.setOnAction(event -> {
            if (coordinateCodeArea.getText().trim().isEmpty()) {
                showMoveScreenAlert("Please enter at least one coordinate.");
                return;
            }

            if (lineNumber == null) lineNumber = 1;
            else {
                Tuple3<Integer, Integer, Integer> startStepEnd = getStartStepEnd(getCoordinatesListLength());
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
            if (coordinateCodeArea.getText().trim().isEmpty()) {
                showMoveScreenAlert("Please enter at least one coordinate.");
                return;
            }

            if (lineNumber == null) lineNumber = 1;
            else {
                Tuple3<Integer, Integer, Integer> startStepEnd = getStartStepEnd(getCoordinatesListLength());
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
            if (coordinateCodeArea.getText().trim().isEmpty()) {
                showMoveScreenAlert("Please enter at least one coordinate.");
                return;
            }

            int userLineNumber = getLineNumber();

            lineNumber = userLineNumber;

            lineNumTextField.setText(Integer.toString(userLineNumber));
            moveScreenToLine(userLineNumber - 1);
        });

        startTextField.setPromptText("Start");
        startTextField.setPrefWidth(60);
        stepTextField.setPromptText("Step");
        stepTextField.setPrefWidth(60);
        endTextField.setPromptText("End");
        endTextField.setPrefWidth(60);

        return new HBox(10, startTextField, stepTextField, endTextField, backwardButton, lineNumTextField, goToLineButton, forwardButton);
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
        alert.setTitle("Cycle Vary");
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

        String[] coordinateStrings = coordinateCodeArea.getText().trim().split("\n");

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
        return coordinateCodeArea.getText().trim().split("\n").length;
    }

    public Tuple3<Integer, Integer, Integer> getStartStepEnd(int defaultEnd) {
        final String lineStartText = startTextField.getText();
        final String lineStepText = stepTextField.getText();
        final String lineEndText = endTextField.getText();
        // The indexes that the user sees (will be converted later)
        int startIdxUser;
        int stepIdxUser;
        int endIdxUser;
        // 2024-05-06 Fixed broken logic (Not all fields filled was not detected properly)
        if (lineStartText.isEmpty() && lineEndText.isEmpty() && lineStepText.isEmpty()) { // All fields empty
            startIdxUser = 1;
            stepIdxUser = 1;
            endIdxUser = defaultEnd;
        } else if (lineStartText.isEmpty() || lineEndText.isEmpty() || lineStepText.isEmpty()) { // At least 1, but not all fields are empty
            showEnterLineNumberErrorAutoVary();
            return Tuple.of(null, null, null);
        } else { // All fields filled
            try {
                startIdxUser = Integer.parseInt(lineStartText);
            } catch (final NumberFormatException e) {
                showInvalidNumberError(lineStartText);
                return Tuple.of(null, null, null);
            }
            try {
                endIdxUser = Integer.parseInt(lineEndText);
            } catch (final NumberFormatException e) {
                showInvalidNumberError(lineEndText);
                return Tuple.of(null, null, null);
            }
            try {
                stepIdxUser = Math.min(defaultEnd, Integer.parseInt(lineStepText)); // Max step is all elements
            } catch (final NumberFormatException e) {
                showInvalidNumberError(lineStepText);
                return Tuple.of(null, null, null);
            }
            if (!(1 <= startIdxUser && startIdxUser <= endIdxUser && endIdxUser <= defaultEnd)) {
                showInvalidLineRangeError(defaultEnd);
                return Tuple.of(null, null, null);
            }
            if(stepIdxUser < 1) {
                showStepErrorAutoVary();
                return Tuple.of(null, null, null);
            }
        }

        return Tuple.of(startIdxUser, stepIdxUser, endIdxUser);
    }

    private static void showEnterLineNumberErrorAutoVary() {
        final Alert alert = new Alert(AlertType.ERROR);

        alert.setTitle("Enter Line Numbers");
        alert.setHeaderText("Enter Line Numbers");
        alert.setContentText("Please enter start and end line numbers for AutoPolyVary.");
        alert.showAndWait();
    }

    private static void showStepErrorAutoVary() {
        final Alert alert = new Alert(AlertType.ERROR);

        alert.setTitle("Bad Step Value");
        alert.setHeaderText("Bad Step Value");
        alert.setContentText("AutoPolyVary step value must be >= 1");
        alert.showAndWait();

    }

    private static void showInvalidLineNumberError(final int max) {
        final Alert alert = new Alert(AlertType.ERROR);

        alert.setTitle("Invalid Line Number");
        alert.setHeaderText("Invalid Line Number");
        alert.setContentText(String.format("Line number must be between 1 and %d.", max));
        alert.showAndWait();
    }

    private static void showInvalidLineRangeError(final int max) {
        final Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle("Invalid Line Range");
        alert.setHeaderText("Invalid Line Range");
        alert.setContentText(String.format("Must have 1 <= Start <= End <= %d.", max));
        alert.showAndWait();
    }

    private static void showInvalidNumberError(final String invalidNumber) {
        final Alert alert = new Alert(AlertType.ERROR);

        alert.setTitle("Invalid Number");
        alert.setHeaderText("Invalid Number");
        alert.setContentText(String.format("Input %s is an invalid number.", invalidNumber));
        alert.showAndWait();
    }
}
