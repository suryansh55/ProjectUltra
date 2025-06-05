package billiards.viewer;

import billiards.codeseq.ClassifiedCodeSequence;
import billiards.codeseq.InvalidCodeSequence;
import billiards.codeseq.Storage;
import billiards.geometry.ConvexPolygon;
import billiards.utils.BatchLoadStorage;
import billiards.utils.Polygon;
import billiards.wrapper.ConnectionPool;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javaslang.Tuple3;
import javaslang.control.Either;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.impl.list.mutable.FastList;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;

import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class IterateToLimitWindow {
    private final TextArea polygonTextArea = new TextArea();
    private final TextArea codePatternTextArea = new TextArea();

    private final Button lookupButton = new Button();

    private final TextField limitTextField = new TextField();

    private final CheckBox drawCheckbox = new CheckBox();
    private final CheckBox coverCheckbox = new CheckBox();

    private final Button runButton = new Button();

    private final Stage stage = new Stage();

    private final ConnectionPool pool;

    private boolean run = false;

    public IterateToLimitWindow(ConnectionPool pool) {
        this.pool = pool;

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
        limitTextField.setPromptText("Limit");

        drawCheckbox.setSelected(true);
        drawCheckbox.setText("Draw");

        coverCheckbox.setSelected(true);
        coverCheckbox.setText("Add To Cover");

        runButton.setText("Run");
        runButton.setOnAction(event -> {
            this.run = true;
            stage.close();
        });
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

    private Tuple3<
            ArrayList<Storage>,
            ArrayList<ArrayList<Storage>>,
            ArrayList<ArrayList<Storage>>
            > iterateTask(
                    String codePattern,
                    ConvexPolygon polygon,
                    int limit
    ) {
        String trimmedCodePattern = codePattern.trim();

        // Ignore comments and empty lines
        if (trimmedCodePattern.startsWith("//") || trimmedCodePattern.isEmpty()) return null;

        String[] codeAndPattern = trimmedCodePattern.split(";");

        // The line must be the code and pattern separated by a semicolon
        if (codeAndPattern.length != 2) return null;

        // Assume the section before the semicolon is the code sequence, and the one after is the iteration pattern
        String codeString = codeAndPattern[0].trim();
        String patternString = codeAndPattern[1].trim();

        String[] codes = codeString.split(",");
        String[] patterns = patternString.split(",");

        if (codes.length != 1 && codes.length != 3) return null;
        if (codes.length != patterns.length) return null;

        ArrayList<ImmutableIntList> codeNumbersList = new ArrayList<>();
        ArrayList<ImmutableIntList> patternNumbersList = new ArrayList<>();

        for (int i = 0; i < codes.length; i++) {
            Optional<ImmutableIntList> codeOptional = Utils.splitString(codes[i]);

            codeOptional.ifPresent(codeNumbersList::add);

            Optional<ImmutableIntList> patternOptional = Utils.splitString(patterns[i]);

            patternOptional.ifPresent(patternNumbersList::add);
        }

        if (codeNumbersList.size() != codes.length || patternNumbersList.size() != patterns.length) return null;

        // We only iterate if the original code sequence is valid, and intersects with the polygon.
        ArrayList<ClassifiedCodeSequence> classCodeSequences = new ArrayList<>();

        for (ImmutableIntList immutableIntList : codeNumbersList) {
            IntArrayList codeNumbers = IntArrayList.newList(immutableIntList);

            Either<InvalidCodeSequence, ClassifiedCodeSequence> classCodeSequence = ClassifiedCodeSequence.create(codeNumbers);

            if (classCodeSequence.isRight()) classCodeSequences.add(classCodeSequence.get());
        }

        if (classCodeSequences.size() != codeNumbersList.size()) return null;

        // Check if this is a valid triple.
        if (classCodeSequences.size() == 3) {
            if (!(classCodeSequences.get(0).stable && !classCodeSequences.get(1).stable && classCodeSequences.get(2).stable)) return null;
        }

        ArrayList<Storage> originalStorages = BatchLoadStorage.batchLoadStorage(classCodeSequences, pool);

        int numOfIntersects = 0;
        for (Storage storage : originalStorages) {
            if (storage.intersects(polygon)) numOfIntersects++;
        }

        if (numOfIntersects != originalStorages.size()) return null;

        // At this point, the original code sequence is valid, the iteration pattern is valid given the original code
        // sequence, and the original code sequence intersects with the polygon. Thus, we can iterate.
        ArrayList<ArrayList<Storage>> forwardResult = iterate(codeNumbersList, patternNumbersList, polygon, 1, limit);
        ArrayList<ArrayList<Storage>> backwardResult = iterate(codeNumbersList, patternNumbersList, polygon, -1, limit);

        return new Tuple3<>(originalStorages, forwardResult, backwardResult);
    }

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
            if (++iterationCount >= limit) break;

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
            codeNumbers.set(index, codeNumbers.get(index) + value);
        }
        return codeNumbers;
    }

    private ArrayList<Tuple3<ArrayList<Storage>, ArrayList<ArrayList<Storage>>, ArrayList<ArrayList<Storage>>>> run() {
        if (polygonTextArea.getText().trim().isEmpty() || codePatternTextArea.getText().trim().isEmpty()) return null;

        String cleanedPolygonString = Polygon.cleanPolygon(this.polygonTextArea.getText());
        ConvexPolygon polygon = Polygon.createConvexPolygon(cleanedPolygonString);

        int limit;

        // Iteration can go on indefinitely, so the user must enter a limit
        if (limitTextField.getText().trim().isEmpty()) return null;
        else {
            try {
                limit = Integer.parseInt(limitTextField.getText());
            } catch (NumberFormatException e) {
                throw new RuntimeException(e);
            }

            if (limit <= 0) return null;
        }

        String[] codePatterns = codePatternTextArea.getText().trim().split("\n");

        final MutableList<
                Future<
                        Tuple3<ArrayList<Storage>, ArrayList<ArrayList<Storage>>, ArrayList<ArrayList<Storage>>>
                        >
                > futures = new FastList<>();

        ExecutorService executor = Executors.newFixedThreadPool(Utils.numThreads);

        for (String codePattern : codePatterns) {
            futures.add(executor.submit(() -> iterateTask(codePattern, polygon, limit)));
        }

        ArrayList<Tuple3<ArrayList<Storage>, ArrayList<ArrayList<Storage>>, ArrayList<ArrayList<Storage>>>> result = new ArrayList<>();

        for (Future<Tuple3<ArrayList<Storage>, ArrayList<ArrayList<Storage>>, ArrayList<ArrayList<Storage>>>> future : futures) {
            try {
                result.add(future.get());
            } catch (InterruptedException | ExecutionException ignored) {
            }
        }

        return result;
    }

    public ArrayList<Tuple3<ArrayList<Storage>, ArrayList<ArrayList<Storage>>, ArrayList<ArrayList<Storage>>>> execute() {
        stage.showAndWait();

        ArrayList<Tuple3<ArrayList<Storage>, ArrayList<ArrayList<Storage>>, ArrayList<ArrayList<Storage>>>> result = null;

        if (this.run) {
            result = this.run();
        }

        this.run = false;

        return result;
    }
}
