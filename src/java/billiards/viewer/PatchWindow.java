package billiards.viewer;

import java.util.ArrayList;

import billiards.patch.CoverableRegion;
import billiards.utils.Polygon;
import billiards.wrapper.ConnectionPool;
import billiards.wrapper.Wrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventType;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javaslang.Tuple2;

/**
 * <b>Jeff Khuu</b><br>
 * <b>Aug 11, 2026</b>
 * <p>
 * Lets the user carve the cover region into named "patches", each with its own
 * digits / magnification / empties, and cover them one at a time. Patches that
 * come back fully covered are drawn as a grey overlay so it is obvious which
 * parts of the region are already done.
 * </p><p>
 * Patches persist between sessions in {@code tmp/patches.txt} (geometry) and
 * {@code tmp/patch_parameters.txt} (per-patch settings), one blank-line-separated
 * record each, kept index-aligned.
 * </p>
 */
public class PatchWindow {

    // One window per Viewer, so this is instance state rather than the static
    // field upstream uses; a static list would outlive the Viewer that owns it.
    private final ObservableList<PatchInfo> patches = FXCollections.observableArrayList();

    private final VBox root = new VBox(10);
    private final Stage stage = new Stage();
    private final Scene scene = new Scene(root);

    private final TextArea polygonField = new TextArea();
    private final TextField magnificationField = new TextField();
    private final TextField digitsField = new TextField();
    private final TextField emptiesField = new TextField();

    private int selectedPatchIndex = -1;
    private final ConnectionPool pool;
    private final Runnable drawPatch;
    private final Runnable loadPatch;
    private final Viewer viewer;

    public PatchWindow(final ConnectionPool pool, final Runnable drawPatch, final Runnable loadPatch,
            final Viewer viewer) {
        this.pool = pool;
        this.drawPatch = drawPatch;
        this.loadPatch = loadPatch;
        this.viewer = viewer;
        stage.setTitle("Patch Window");
        stage.setScene(scene);

        root.setPrefHeight(600);
        root.setPrefWidth(400);
        root.setPadding(new Insets(10));
        root.getChildren().addAll(getPatchesMenu(), getPatchControls(), getPatchFields());

        loadFromFiles();
    }

    void show() {
        this.stage.show();
        this.stage.toFront();
    }

    private ScrollPane getPatchesMenu() {
        final HBox container = new HBox(10);
        patches.addListener((ListChangeListener.Change<? extends PatchInfo> c) -> {
            while (c.next()) {
                if (c.wasAdded()) {
                    for (final PatchInfo p : c.getAddedSubList()) {
                        container.getChildren().add(makePatchButton(p));
                    }
                }
                if (c.wasRemoved()) {
                    container.getChildren().clear();
                    for (final PatchInfo p : patches) {
                        container.getChildren().add(makePatchButton(p));
                    }
                }
            }
        });

        root.addEventFilter(PatchCreateEvent.PATCH_CREATED, event -> {
            final PatchInfo patch = event.getNewPatch();
            patches.add(patch);
            container.fireEvent(new PatchSelectEvent(PatchSelectEvent.PATCH_UPDATED, patches.indexOf(patch)));
        });

        container.setPadding(new Insets(10));
        container.setPrefHeight(60);
        container.setAlignment(Pos.CENTER_LEFT);

        final ScrollPane scrollPane = new ScrollPane();
        scrollPane.setContent(container);
        scrollPane.setHbarPolicy(ScrollBarPolicy.AS_NEEDED);
        scrollPane.setVbarPolicy(ScrollBarPolicy.NEVER);
        scrollPane.setFitToHeight(true);

        return scrollPane;
    }

    private HBox getPatchControls() {
        final HBox controls = new HBox(10);
        controls.setAlignment(Pos.CENTER_LEFT);

        final Button addPatchButton = new Button("Add Patch");
        addPatchButton.setOnAction((final ActionEvent e) -> {
            final int newId = patches.stream().mapToInt(p -> p.id).max().orElse(0) + 1;
            final PatchInfo newPatch = new PatchInfo(newId, CoverWindow.polygonString, 24, 24, 24);
            addPatchButton.fireEvent(new PatchCreateEvent(PatchCreateEvent.PATCH_CREATED, newPatch));
        });

        final Button removePatchButton = new Button("Remove Patch");
        removePatchButton.setOnAction((final ActionEvent e) -> {
            if (selectedPatchIndex >= 0) {
                final PatchInfo patch = patches.remove(selectedPatchIndex);
                removePatchButton.fireEvent(new PatchRemoveEvent(PatchRemoveEvent.PATCH_REMOVED, patch,
                        selectedPatchIndex));
                removePatchButton.fireEvent(new PatchSelectEvent(PatchSelectEvent.PATCH_UPDATED,
                        patches.isEmpty() ? -1 : Math.min(selectedPatchIndex, patches.size() - 1)));
                savePolygonsToFile();
                saveParametersToFile();
            }
        });

        final Label selectedPatchLabel = new Label("Selected Patch: None");
        root.addEventFilter(PatchSelectEvent.PATCH_UPDATED, event -> {
            selectedPatchIndex = event.getSelectedPatchIndex();
            if (selectedPatchIndex == -1) {
                selectedPatchLabel.setText("Selected Patch: None");
            } else {
                final PatchInfo selectedPatch = patches.get(selectedPatchIndex);
                selectedPatchLabel.setText("Selected Patch: " + selectedPatch.id);
            }
        });

        controls.getChildren().addAll(addPatchButton, removePatchButton, selectedPatchLabel);
        return controls;
    }

    private VBox getPatchFields() {
        final VBox fields = new VBox(10);
        fields.setAlignment(Pos.CENTER_LEFT);

        final Label magnificationLabel = new Label("Magnification:");
        final Label digitsLabel = new Label("Digits:");
        final Label emptiesLabel = new Label("Empties:");

        final HBox patchBox = new HBox(10);
        final Button updatePatchButton = new Button("Update Patch");

        updatePatchButton.setOnAction((final ActionEvent e) -> {
            if (selectedPatchIndex == -1) {
                new Alert(AlertType.ERROR, "No patch selected.").showAndWait();
                return;
            }
            if (savePatch()) {
                drawPatch.run();
                new Alert(AlertType.INFORMATION, "Patch updated.").showAndWait();
            }
        });

        final Button calculatePatchButton = new Button("Calculate Patch");
        calculatePatchButton.setOnAction((final ActionEvent e) -> {
            if (selectedPatchIndex == -1) {
                new Alert(AlertType.ERROR, "No patch selected.").showAndWait();
                return;
            }

            if (!savePatch()) { // Save the current patch before calculating
                new Alert(AlertType.ERROR, "Failed to save patch.").showAndWait();
                return;
            }

            calculatePatchButton.setDisable(true);
            final PatchInfo patch = patches.get(selectedPatchIndex);

            final Task<String> task = new Task<String>() {
                @Override
                protected String call() {
                    final String cleanedStablesPre = CoverWindow.cleanStables(CoverWindow.stablesString, pool);
                    final Tuple2<String, String> cleanedTriplesPre =
                            CoverWindow.cleanTriples(CoverWindow.triplesString, pool);
                    final String cleanedTriples = cleanedTriplesPre._1;
                    final String cleanedStables = (cleanedStablesPre + '\n' + cleanedTriplesPre._2).trim();

                    return Wrapper.coverWrapper(patch.polygonString, cleanedStables, cleanedTriples,
                            patch.digits, patch.magnification, patch.empties, true, pool);
                }
            };

            task.setOnSucceeded(ev -> {
                calculatePatchButton.setDisable(false);

                // An empty result means no uncovered squares were reported back.
                final boolean isCovered = task.getValue().isEmpty();
                if (isCovered) {
                    viewer.coveredPatchAreas.add(Polygon.createConvexPolygon(patch.polygonString));
                }

                drawPatch.run();
                loadPatch.run();

                new Alert(AlertType.INFORMATION, isCovered
                        ? "Patch " + patch.id + " is covered."
                        : "Patch " + patch.id + " is NOT covered. Holes were written to tmp/holes.txt.")
                        .showAndWait();
                stage.close();
            });

            task.setOnFailed(ev -> {
                final Throwable failure = task.getException();
                final Alert alert = new Alert(AlertType.ERROR);
                alert.setTitle("Cover Failed");
                alert.setHeaderText("Cover calculation failed");
                alert.setContentText(failure == null ? "Unknown cover error" : failure.getMessage());
                alert.show();
                if (failure != null) {
                    failure.printStackTrace();
                }
                calculatePatchButton.setDisable(false);
            });

            final Thread thread = new Thread(task, "cover-calculation");
            thread.setDaemon(true);
            thread.start();
        });

        final Button clearPatches = new Button("Clear Visible Patches");
        clearPatches.setOnAction((final ActionEvent e) -> {
            viewer.coveredPatchAreas.clear();
            viewer.patchAreas.clear();
            viewer.patchImageView.setImage(new WritableImage(Viewer.SIDE, Viewer.SIDE));
            new Alert(AlertType.INFORMATION, "Visible patches cleared.").showAndWait();
        });

        patchBox.setAlignment(Pos.CENTER_LEFT);
        patchBox.getChildren().addAll(updatePatchButton, calculatePatchButton, clearPatches);

        polygonField.setPromptText("Polygon");
        magnificationField.setPromptText("Magnification");
        digitsField.setPromptText("Digits");
        emptiesField.setPromptText("Empties");
        fields.getChildren().addAll(polygonField, digitsLabel, digitsField, magnificationLabel, magnificationField,
                emptiesLabel, emptiesField, patchBox);

        root.addEventFilter(PatchSelectEvent.PATCH_UPDATED, event -> {
            final int index = event.getSelectedPatchIndex();
            if (index == -1) {
                polygonField.clear();
                magnificationField.clear();
                digitsField.clear();
                emptiesField.clear();
                return;
            }
            final PatchInfo patch = patches.get(index);
            polygonField.setText(patch.polygonString);
            magnificationField.setText(String.valueOf(patch.magnification));
            digitsField.setText(String.valueOf(patch.digits));
            emptiesField.setText(String.valueOf(patch.empties));
        });

        return fields;
    }

    public void savePolygonsToFile() {
        if (patches.isEmpty()) {
            Utils.writeToFile(Viewer.tmpDir + "patches.txt", "");
            return;
        }
        final StringBuilder sb = new StringBuilder();
        for (final PatchInfo p : patches) {
            if (sb.length() > 0) {
                sb.append(System.lineSeparator()).append(System.lineSeparator());
            }
            sb.append(p.polygonString);
        }

        Utils.writeToFile(Viewer.tmpDir + "patches.txt", sb.toString());
    }

    public void saveParametersToFile() {
        if (patches.isEmpty()) {
            Utils.writeToFile(Viewer.tmpDir + "patch_parameters.txt", "");
            return;
        }
        final StringBuilder sb = new StringBuilder();
        for (final PatchInfo p : patches) {
            if (sb.length() > 0) {
                sb.append(System.lineSeparator()).append(System.lineSeparator());
            }
            sb.append(String.format("%d %d %d %d", p.id, p.magnification, p.digits, p.empties));
        }

        Utils.writeToFile(Viewer.tmpDir + "patch_parameters.txt", sb.toString());
    }

    public void loadFromFiles() {
        final String patchesString = Utils.readFromFile(Viewer.tmpDir + "patches.txt").trim();
        final String parametersString = Utils.readFromFile(Viewer.tmpDir + "patch_parameters.txt").trim();

        if (patchesString.isEmpty() || parametersString.isEmpty()) {
            return;
        }

        final String[] patchStrings = patchesString.split("\\R\\R");
        final String[] parameterStrings = parametersString.split("\\R\\R");

        if (patchStrings.length != parameterStrings.length) {
            new Alert(AlertType.ERROR, "Mismatch between number of patches and number of parameter sets.")
                    .showAndWait();
            return;
        }

        patches.clear();
        for (int i = 0; i < patchStrings.length; i++) {
            final String patchString = patchStrings[i].trim();
            final String parameterString = parameterStrings[i].trim();
            final String[] params = parameterString.split(" ");
            if (params.length != 4) {
                new Alert(AlertType.ERROR, "Invalid parameter set: " + parameterString).showAndWait();
                return;
            }
            try {
                final int id = Integer.parseInt(params[0]);
                final int magnification = Integer.parseInt(params[1]);
                final int digits = Integer.parseInt(params[2]);
                final int empties = Integer.parseInt(params[3]);
                patches.add(new PatchInfo(id, patchString, magnification, digits, empties));
            } catch (final NumberFormatException ex) {
                new Alert(AlertType.ERROR, "Error parsing parameters: " + ex.getMessage()).showAndWait();
                return;
            }
        }
    }

    private boolean savePatch() {
        final PatchInfo patch = patches.get(selectedPatchIndex);
        try {
            patch.polygonString = Polygon.cleanPolygon(polygonField.getText());
        } catch (final Exception ex) {
            new Alert(AlertType.ERROR, "Error in polygon: " + ex.getMessage()).showAndWait();
            return false;
        }

        try {
            patch.magnification = Integer.parseInt(magnificationField.getText());
            patch.digits = Integer.parseInt(digitsField.getText());
            patch.empties = Integer.parseInt(emptiesField.getText());
        } catch (final NumberFormatException ex) {
            new Alert(AlertType.ERROR, "Error parsing magnification, digits, or empties: " + ex.getMessage())
                    .showAndWait();
            return false;
        }

        savePolygonsToFile();
        saveParametersToFile();
        return true;
    }

    private Button makePatchButton(final PatchInfo patch) {
        final Button button = new Button("Patch " + patch.id);
        button.setOnAction((final ActionEvent e) -> {
            button.fireEvent(new PatchSelectEvent(PatchSelectEvent.PATCH_UPDATED, patches.indexOf(patch)));
        });
        return button;
    }

    public ArrayList<CoverableRegion> getCoverableRegions() {
        final ArrayList<CoverableRegion> coverableRegions = new ArrayList<>();
        for (final PatchInfo patch : patches) {
            coverableRegions.add(new CoverableRegion("Patch " + patch.id, patch.polygonString));
        }
        return coverableRegions;
    }
}


class PatchInfo {
    String polygonString;
    int magnification;
    int digits;
    int empties;
    int id;

    PatchInfo(final int id, final String polygonString, final int magnification, final int digits, final int empties) {
        this.id = id;
        this.polygonString = polygonString;
        this.magnification = magnification;
        this.digits = digits;
        this.empties = empties;
    }
}

class PatchSelectEvent extends Event {
    public static final EventType<PatchSelectEvent> PATCH_UPDATED =
            new EventType<>(Event.ANY, "PATCH_UPDATED");
    private static final long serialVersionUID = 1L;

    private final int selectedPatchIndex;

    public PatchSelectEvent(final EventType<? extends PatchSelectEvent> eventType, final int patchIndex) {
        super(eventType);
        this.selectedPatchIndex = patchIndex;
    }

    public int getSelectedPatchIndex() {
        return selectedPatchIndex;
    }
}

class PatchCreateEvent extends Event {
    public static final EventType<PatchCreateEvent> PATCH_CREATED =
            new EventType<>(Event.ANY, "PATCH_CREATED");
    private static final long serialVersionUID = 1L;

    private final PatchInfo newPatch;

    public PatchCreateEvent(final EventType<? extends PatchCreateEvent> eventType, final PatchInfo newPatch) {
        super(eventType);
        this.newPatch = newPatch;
    }

    public PatchInfo getNewPatch() {
        return newPatch;
    }
}

class PatchRemoveEvent extends Event {
    public static final EventType<PatchRemoveEvent> PATCH_REMOVED =
            new EventType<>(Event.ANY, "PATCH_REMOVED");
    private static final long serialVersionUID = 1L;
    private final PatchInfo removedPatch;
    private final int removedPatchIndex;

    public PatchRemoveEvent(final EventType<? extends PatchRemoveEvent> eventType, final PatchInfo removedPatch,
            final int removedPatchIndex) {
        super(eventType);
        this.removedPatch = removedPatch;
        this.removedPatchIndex = removedPatchIndex;
    }

    public PatchInfo getRemovedPatch() {
        return removedPatch;
    }

    public int getRemovedPatchIndex() {
        return removedPatchIndex;
    }
}
