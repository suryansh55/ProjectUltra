package billiards.viewer;

import billiards.codeseq.ClassifiedCodeSequence;
import billiards.codeseq.InvalidCodeSequence;
import billiards.codeseq.Storage;
import billiards.geometry.ConvexPolygon;
import billiards.utils.BatchLoadStorage;
import billiards.utils.Polygon;
import billiards.wrapper.ConnectionPool;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javaslang.Tuple3;
import javaslang.control.Either;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.impl.list.mutable.FastList;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Zhao Yu Li, Jun 06, 2025.
 * Iterate to the limit. Given a ConvexPolygon, for each code sequence (single or triple) - iteration pattern pair in a
 * list, we iterate forward (addition) and backward (subtraction) until one of the following two conditions occurred:
 *  1. The number of iterations reached a user specified limit.
 *  2. New code sequences produced from the iteration no longer intersect with the user specified polygon.
 * All produced codes are (optionally) drawn on the screen and added to the cover.
 */
public class IterateToLimitWindow {
    private final TextArea polygonTextArea = new TextArea();
    private final TextArea stablesTextArea = new TextArea();
    private final TextArea unstablesTextArea = new TextArea();
    private final TextArea triplesTextArea = new TextArea();

    private final HashMap<String, TextArea> textAreaMap = new HashMap<>();

    private final TextField limitTextField = new TextField();

    private final CheckBox drawCheckbox = new CheckBox();
    private final CheckBox coverCheckbox = new CheckBox();

    private final Stage stage = new Stage();

    private final ConnectionPool pool;

    // The IterateToLimitWindow uses this Observable Boolean Property to notify the Viewer when the iterate-to-limit
    // task is finished
    private SimpleBooleanProperty finish = null;

    // This array is used to store the result from the iterate-to-limit task
    private ArrayList<Tuple3<ArrayList<Storage>, ArrayList<ArrayList<Storage>>, ArrayList<ArrayList<Storage>>>> results = null;

    // This boolean value ensures that only one iterate-to-limit task is running at any time, for this task can be very
    // resource consuming
    private boolean running = false;

    public IterateToLimitWindow(ConnectionPool pool) {
        this.pool = pool;

        Button lookupButton = new Button();
        Button runButton = new Button();
        VBox root = new VBox(
                10,
                getRoot(),
                new HBox(10, lookupButton, limitTextField, drawCheckbox, coverCheckbox, runButton)
        );
        final Scene scene = new Scene(root);

        stage.setScene(scene);
        stage.setTitle("Iterate To Limit Window");
        stage.setHeight(600);
        stage.setOnCloseRequest(event -> {
            this.results = null;
            this.finish.set(true);
            stage.close();
        });

        root.setPadding(new Insets(10));

        polygonTextArea.setTooltip(new Tooltip(
                "Enter each (x,y) coordinate in a separate line, with a whitespace separating the x and y coordinates.")
        );
        polygonTextArea.setWrapText(true);
        polygonTextArea.setMaxHeight(130);

        stablesTextArea.setTooltip(getTooltip(
                "Enter each stable - iteration pattern pair in a separate line, with a semicolon " +
                        "separating the stable and the pattern."
        ));
        stablesTextArea.setWrapText(true);

        unstablesTextArea.setTooltip(getTooltip(
                "Enter each unstable - iteration pattern pair in a separate line, with a semicolon " +
                        "separating the unstable and the pattern."
        ));
        unstablesTextArea.setWrapText(true);

        triplesTextArea.setTooltip(getTooltip(
                "Enter each triple - iteration pattern pair in a separate line, with a semicolon " +
                        "separating the triple and the pattern. Different components of the triple should be" +
                        "separated by a comma."
        ));
        triplesTextArea.setWrapText(true);

        textAreaMap.put("Polygon", polygonTextArea);
        textAreaMap.put("Stables", stablesTextArea);
        textAreaMap.put("Unstables", unstablesTextArea);
        textAreaMap.put("Triples", triplesTextArea);

        lookupButton.setText("Lookup");

        limitTextField.setPrefColumnCount(4);
        limitTextField.setPromptText("Limit");
        limitTextField.setText("2");

        drawCheckbox.setSelected(true);
        drawCheckbox.setText("Draw");

        coverCheckbox.setSelected(true);
        coverCheckbox.setText("Add To Cover");

        runButton.setText("Run");
        runButton.setOnAction(event -> {
            if (run()) stage.close();
        });
    }

    private static Tooltip getTooltip(String content) {
        Tooltip tooltip = new Tooltip(content);
        tooltip.setWrapText(true);
        tooltip.setPrefWidth(400);
        return tooltip;
    }

    private HBox getTextAreaHBox(String labelText) {
        final Label label = new Label(labelText + ":");
        final Button clearButton = new Button("Clear");

        label.setFont(Font.font("Verdana", FontWeight.BOLD, 16));
        clearButton.setOnAction(event -> textAreaMap.get(labelText).setText(""));

        return new HBox(10, label, clearButton);
    }

    private ScrollPane getRoot() {
        final HBox polygonHBox = getTextAreaHBox("Polygon");
        final HBox stablesHBox = getTextAreaHBox("Stables");
        final HBox unstablesHBox = getTextAreaHBox("Unstables");
        final HBox triplesHBox = getTextAreaHBox("Triples");

        VBox vbox = new VBox(10,
                polygonHBox, polygonTextArea,
                stablesHBox, stablesTextArea,
                unstablesHBox, unstablesTextArea,
                triplesHBox, triplesTextArea
        );

        ScrollPane scrollPane = new ScrollPane(vbox);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(false);

        return scrollPane;
    }

    /**
     * The per code sequence - iteration pattern pair iterate-to-limit task to be submitted to the executor.
     * @param codePattern The code sequence - iteration pattern pair as a string.
     * @param polygon The ConvexPolygon to check for intersection.
     * @param limit The user specified limit of how many codes to produce (separately for each direction)
     * @return A 3-tuple: the Storages of the base code sequence, the list of Storages from iterating forward, and the list of Storages from iterating backward.
     */
    private Tuple3<
            ArrayList<Storage>,
            ArrayList<ArrayList<Storage>>,
            ArrayList<ArrayList<Storage>>
            > iterateTask(
                    String codePattern,
                    ConvexPolygon polygon,
                    int limit
    ) {
        // The code sequence - iteration pattern pairs are entered by the user and retrieved as strings. Users can make
        // mistakes, so we must check very carefully to make sure that everything is okay before we proceed.
        String trimmedCodePattern = codePattern.trim();

        // Ignore comments and empty lines
        if (trimmedCodePattern.startsWith("//") || trimmedCodePattern.isEmpty()) return null;

        String[] codeAndPattern = trimmedCodePattern.split(";");

        // The line must be the code and pattern separated by a semicolon
        if (codeAndPattern.length != 2) {
            System.out.println("Skipping '" + trimmedCodePattern + "' because it is not a valid code-pattern pair.");
            return null;
        }

        // Assume the section before the semicolon is the code sequence, and the one after is the iteration pattern
        String codeString = codeAndPattern[0].trim();
        String patternString = codeAndPattern[1].trim();

        String[] codes = codeString.split(",");
        String[] patterns = patternString.split(",");

        if (codes.length != 1 && codes.length != 3) {
            System.out.println("Skipping '" + trimmedCodePattern + "' because it is not a single nor a triple.");
            return null;
        }

        if (codes.length != patterns.length) {
            System.out.println("Skipping '" + trimmedCodePattern + "' because its code and iteration pattern are not both singles or not both triples.");
            return null;
        }

        ArrayList<ImmutableIntList> codeNumbersList = new ArrayList<>();
        ArrayList<ImmutableIntList> patternNumbersList = new ArrayList<>();

        for (int i = 0; i < codes.length; i++) {
            Optional<ImmutableIntList> codeOptional = Utils.splitString(codes[i]);

            codeOptional.ifPresent(codeNumbersList::add);

            Optional<ImmutableIntList> patternOptional = Utils.splitString(patterns[i]);

            patternOptional.ifPresent(patternNumbersList::add);
        }

        if (codeNumbersList.size() != codes.length || patternNumbersList.size() != patterns.length) {
            System.out.println("Skipping '" + trimmedCodePattern + "' because its code sequence or iteration pattern contains invalid values.");
            return null;
        }

        // We only iterate if the original code sequence is valid, and intersects with the polygon.
        ArrayList<ClassifiedCodeSequence> classCodeSequences = new ArrayList<>();

        for (ImmutableIntList immutableIntList : codeNumbersList) {
            IntArrayList codeNumbers = IntArrayList.newList(immutableIntList);

            Either<InvalidCodeSequence, ClassifiedCodeSequence> classCodeSequence = ClassifiedCodeSequence.create(codeNumbers);

            if (classCodeSequence.isRight()) classCodeSequences.add(classCodeSequence.get());
        }

        if (classCodeSequences.size() != codeNumbersList.size()) {
            System.out.println("Skipping '" + trimmedCodePattern + "' because it contains invalid code sequences.");
            return null;
        }

        // Check if this is a valid triple.
        if (classCodeSequences.size() == 3) {
            if (!(classCodeSequences.get(0).stable && !classCodeSequences.get(1).stable && classCodeSequences.get(2).stable)) {
                System.out.println("Skipping '" + trimmedCodePattern + "' because it is not a valid triple.");
                return null;
            }
        }

        ArrayList<Storage> originalStorages = BatchLoadStorage.batchLoadStorage(classCodeSequences, pool);

        int numOfIntersects = 0;
        for (Storage storage : originalStorages) {
            if (storage.intersects(polygon)) numOfIntersects++;
        }

        if (numOfIntersects != originalStorages.size()) {
            System.out.println("Skipping '" + trimmedCodePattern + "' because the base code sequence does not intersection with the polygon.");
            return null;
        }

        // At this point, the original code sequence is valid, the iteration pattern is valid given the original code
        // sequence, and the original code sequence intersects with the polygon. Thus, we can iterate.
        ArrayList<ArrayList<Storage>> forwardResult = iterate(codeNumbersList, patternNumbersList, polygon, 1, limit);
        ArrayList<ArrayList<Storage>> backwardResult = iterate(codeNumbersList, patternNumbersList, polygon, -1, limit);

        return new Tuple3<>(originalStorages, forwardResult, backwardResult);
    }

    /**
     * Iterate, either forwards or backwards for a single code sequence - iteration pattern pair.
     * @param codeNumbersList The code numbers of the code sequence to iterate.
     * @param patternNumbersList The iteration pattern.
     * @param polygon The ConvexPolygon to check for intersection.
     * @param direction 1 to iterate forwards; -1 to iterate backwards.
     * @param limit The user-specified maximum number of codes to produce.
     * @return A list of Storages which represents all the produced code sequences that intersect with the polygon.
     */
    private ArrayList<ArrayList<Storage>> iterate(
            ArrayList<ImmutableIntList> codeNumbersList,
            ArrayList<ImmutableIntList> patternNumbersList,
            ConvexPolygon polygon,
            int direction,
            int limit
    ) {
        boolean limitNotReached = true;
        int iterationCount = 0;
        ArrayList<ArrayList<Storage>> iterationResults = new ArrayList<>();

        // First, iteration forward
        while (limitNotReached) {
            if (++iterationCount > limit) break;

            ArrayList<ClassifiedCodeSequence> classCodeSequences = new ArrayList<>();

            for (int i = 0; i < codeNumbersList.size(); i++) {
                IntArrayList codeNumbers = calcCodeNumbers(codeNumbersList, patternNumbersList, i, iterationCount, direction);

                Either<InvalidCodeSequence, ClassifiedCodeSequence> classCodeSequence = ClassifiedCodeSequence.create(codeNumbers);

                if (classCodeSequence.isRight()) classCodeSequences.add(classCodeSequence.get());
            }

            // This code sequence is invalid, we can assume the rest of the code sequences in the iteration will also be
            // invalid.
            if (classCodeSequences.size() != codeNumbersList.size()) break;

            // Check if this is a valid triple.
            if (classCodeSequences.size() == 3) {
                if (!(classCodeSequences.get(0).stable && !classCodeSequences.get(1).stable && classCodeSequences.get(2).stable)) break;
            }

            ArrayList<Storage> storages = BatchLoadStorage.batchLoadStorage(classCodeSequences, pool);

            // ClassifiedCodeSequence reduced to empty set, we have possibly reached the limit.
            if (storages.size() != classCodeSequences.size()) limitNotReached = false;

            int numOfIntersects = 0;
            for (Storage storage : storages) {
                if (storage.intersects(polygon)) numOfIntersects++;
            }

            if (numOfIntersects == storages.size()) {
                iterationResults.add(storages);
            } else {
                limitNotReached = false;
            }
        }

        return iterationResults;
    }

    /**
     * Perform one iteration for the i-th component of a code sequence.
     * @param codeNumbersList The code numbers for the code sequence.
     * @param patternNumbersList The pattern numbers for the iteration pattern.
     * @param i The i-th component to iterate.
     * @param iteration The iteration number we are at. On the n-th iteration, we add/subtract n * 2 to the base code numbers.
     * @param direction The direction of iteration (forward/addition or backward/subtraction).
     * @return The code numbers which are the result of the iteration.
     */
    private static IntArrayList calcCodeNumbers(
            ArrayList<ImmutableIntList> codeNumbersList,
            ArrayList<ImmutableIntList> patternNumbersList,
            int i,
            int iteration,
            int direction
    ) {
        IntArrayList codeNumbers = IntArrayList.newList(codeNumbersList.get(i));
        ImmutableIntList patternNumbers = patternNumbersList.get(i);

        for (int j = 0; j < patternNumbers.size(); j++) {
            int index = patternNumbers.get(j);

            // Allows negative index notation, which means to subtract two instead of adding 2.
            int value = index < 0 ? -2 : 2;
            value = value * iteration * direction;
            index = index < 0 ? -index : index;
            index = index - 1;
            codeNumbers.set(index, codeNumbers.get(index) + value);
        }
        return codeNumbers;
    }

    /**
     * Checks all the inputs from the window and runs the iterate-to-limit task for all the code sequence - iteration
     * pattern pairs.
     * @return True if user input have no errors (the task is running); false if there are errors in the user input.
     */
    private boolean run() {
        if (running) {
            Alert alert = getInfoAlertDialogue(
                    "Task already running",
                    "You can run only run a single instance of iterate-to-limit task at any time."
            );
            alert.showAndWait();

            return false;
        }

        running = true;

        if (polygonTextArea.getText().trim().isEmpty() ||
                (stablesTextArea.getText().trim().isEmpty()
                        && unstablesTextArea.getText().trim().isEmpty()
                        && triplesTextArea.getText().trim().isEmpty())
        ) {
            running = false;

            Alert alert = getInfoAlertDialogue(
                    "Empty polygon and/or code sequence - iteration pattern pairs",
                    "Please enter a valid polygon and and at least one code sequence - iteration pattern pair."
            );
            alert.showAndWait();

            return false;
        }

        String cleanedPolygonString = Polygon.cleanPolygon(this.polygonTextArea.getText());
        ConvexPolygon polygon = Polygon.createConvexPolygon(cleanedPolygonString);

        int limit;

        // Iteration can go on indefinitely, so the user must enter a limit
        if (limitTextField.getText().trim().isEmpty()) {
            running = false;

            Alert alert = getInfoAlertDialogue(
                    "Limit not provided",
                    "Iterations can go on indefinitely; Please enter a valid limit (a non-zero, positive integer)."
            );
            alert.showAndWait();

            return false;
        } else {
            try {
                limit = Integer.parseInt(limitTextField.getText());
            } catch (NumberFormatException e) {
                running = false;
                throw new RuntimeException(e);
            }

            if (limit <= 0) {
                running = false;

                Alert alert = getInfoAlertDialogue(
                        "Limit cannot be negative or zero",
                        "Limit must be a positive, non-zero integer."
                );
                alert.showAndWait();

                return false;
            }
        }

        String[] stablePatterns = stablesTextArea.getText().trim().split("\n");
        String[] unstablePatterns = unstablesTextArea.getText().trim().split("\n");
        String[] triplePatterns = triplesTextArea.getText().trim().split("\n");
        String[][] codePatterns = {stablePatterns, unstablePatterns, triplePatterns};

        ExecutorService executor = Executors.newFixedThreadPool(Utils.numThreads);
        ArrayList<Tuple3<ArrayList<Storage>, ArrayList<ArrayList<Storage>>, ArrayList<ArrayList<Storage>>>> results = new ArrayList<>();

        for (String[] codePattern : codePatterns) {
            if (codePattern[0].isEmpty() && codePattern.length == 1) continue;

            final MutableList<
                    Future<
                            Tuple3<ArrayList<Storage>, ArrayList<ArrayList<Storage>>, ArrayList<ArrayList<Storage>>>
                            >
                    > futures = new FastList<>();

            for (String pair : codePattern) {
                futures.add(executor.submit(() -> iterateTask(pair, polygon, limit)));
            }

            for (int i = 0; i < futures.size(); i++) {
                try {
                    Tuple3<ArrayList<Storage>, ArrayList<ArrayList<Storage>>, ArrayList<ArrayList<Storage>>> result = futures.get(i).get();
                    if (result != null) results.add(result);  // Only add results from tasks that ran to completion successfully
                } catch (InterruptedException | ExecutionException e) {
                    System.out.println("An exception occurred for the code sequence - iteration pattern pair '"
                            + codePattern[i] + "':  " + e.getMessage());
                }
            }
        }

        executor.shutdown();

        // We are done. Set the results, and notify the observer.
        running = false;
        this.results = results;
        this.finish.set(true);

        return true;
    }

    /**
     * Returns an observable boolean property for an observer that will use the result of the iterate-to-limit task.
     * @return A SimpleBooleanProperty that will be set true once the iterate-to-limit task is finished.
     */
    public SimpleBooleanProperty execute() {
        stage.show();
        this.finish = new SimpleBooleanProperty(false);
        return this.finish;
    }

    public boolean getDraw() {
        return this.drawCheckbox.isSelected();
    }

    public boolean getAddToCover() {
        return this.coverCheckbox.isSelected();
    }

    public ArrayList<Tuple3<ArrayList<Storage>, ArrayList<ArrayList<Storage>>, ArrayList<ArrayList<Storage>>>> getResults() {
        return this.results;
    }

    public void nullifyFinish() {
        this.finish = null;
    }

    public void nullifyResult() {
        this.results = null;
    }

    public boolean isShowing() {
        return this.stage.isShowing();
    }

    private Alert getInfoAlertDialogue(String header, String content) {
        Text alertText = new Text(content);
        alertText.setWrappingWidth(350);

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Iterate To Limit");
        alert.setHeaderText(header);
        alert.getDialogPane().setContent(alertText);
        alert.getDialogPane().setPadding(new Insets(10));
        alert.getDialogPane().setMaxWidth(400);
        return alert;
    }
}
