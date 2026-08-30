package billiards.viewer;

import java.util.ArrayList;
import java.util.function.Consumer;

import billiards.geometry.ConvexPolygon;
import billiards.patch.CoverableRegion;
import billiards.utils.Polygon;
import billiards.wrapper.ConnectionPool;
import billiards.wrapper.Wrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
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

public class PatchWindow {
    // Global Mutable State
    public static ObservableList<PatchInfo> patches = FXCollections.observableArrayList();
    // End Global Mutable State
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

    public PatchWindow(ConnectionPool pool, final Runnable drawPatch, final Runnable loadPatch, final Viewer viewer) {
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

    private ScrollPane getPatchesMenu(){
        HBox container = new HBox(10);
        patches.addListener((ListChangeListener.Change<? extends PatchInfo> c) -> {
            while(c.next()) {
                if(c.wasAdded()) {
                    for(PatchInfo p : c.getAddedSubList()) {
                        container.getChildren().add(makePatchButton(p));
                    }
                }
                if(c.wasRemoved()) {
                    container.getChildren().clear();
                    for(PatchInfo p : patches) {
                        container.getChildren().add(makePatchButton(p));
                    }
                }
            }
        });

        root.addEventFilter(PatchCreateEvent.PATCH_CREATED, event -> { 
            PatchInfo patch = event.getNewPatch();
            patches.add(patch);
            container.fireEvent(new PatchSelectEvent(PatchSelectEvent.PATCH_UPDATED, patches.indexOf(patch)));
        });

        container.setPadding(new Insets(10));
        container.setPrefHeight(60);
        container.setAlignment(Pos.CENTER_LEFT);

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setContent(container);
        scrollPane.setHbarPolicy(ScrollBarPolicy.AS_NEEDED); 
        scrollPane.setVbarPolicy(ScrollBarPolicy.NEVER);
        scrollPane.setFitToHeight(true);

        return scrollPane;
    }

    private HBox getPatchControls() {
        HBox controls = new HBox(10);
        controls.setAlignment(Pos.CENTER_LEFT);

        Button addPatchButton = new Button("Add Patch");
        addPatchButton.setOnAction((ActionEvent e) -> { 
            int newId = patches.stream().mapToInt(p -> p.id).max().orElse(0) + 1;
            PatchInfo newPatch = new PatchInfo(newId, CoverWindow.polygonString, 24, 24, 24);
            addPatchButton.fireEvent(new PatchCreateEvent(PatchCreateEvent.PATCH_CREATED, newPatch));
        });
        Button removePatchButton = new Button("Remove Patch");
        removePatchButton.setOnAction((ActionEvent e) -> { 
            if (selectedPatchIndex >= 0) {
                PatchInfo patch = patches.remove(selectedPatchIndex);
                removePatchButton.fireEvent(new PatchRemoveEvent(PatchRemoveEvent.PATCH_REMOVED, patch, selectedPatchIndex));
                removePatchButton.fireEvent(new PatchSelectEvent(PatchSelectEvent.PATCH_UPDATED, patches.isEmpty() ? -1 : Math.min(selectedPatchIndex, patches.size() - 1)));
            }
        });

        Label selectedPatchLabel = new Label("Selected Patch: None");
        root.addEventFilter(PatchSelectEvent.PATCH_UPDATED, event -> {
            selectedPatchIndex = event.getSelectedPatchIndex();
            if(selectedPatchIndex == -1) {
                selectedPatchLabel.setText("Selected Patch: None");
            } else {
                PatchInfo selectedPatch = patches.get(selectedPatchIndex);
                selectedPatchLabel.setText("Selected Patch: " + selectedPatch.id);
            }
        });

        controls.getChildren().addAll(addPatchButton, removePatchButton, selectedPatchLabel);
        return controls;
    }
    
    private VBox getPatchFields() {
        VBox fields = new VBox(10);
        fields.setAlignment(Pos.CENTER_LEFT);

        Label magnificationLabel = new Label("Magnification:");
        Label digitsLabel = new Label("Digits:");
        Label emptiesLabel = new Label("Empties:");

        HBox patchBox = new HBox(10);
        Button updatePatchButton = new Button("Update Patch");

        updatePatchButton.setOnAction((ActionEvent e) -> {
            if (savePatch()) {
                drawPatch.run();
                new Alert(Alert.AlertType.INFORMATION, "Patch updated.").showAndWait();
            }
        });

        Button calculatePatchButton = new Button("Calculate Patch");
        calculatePatchButton.setOnAction((ActionEvent e) -> {
            calculatePatchButton.setDisable(true);
            if(selectedPatchIndex == -1) {
                new Alert(Alert.AlertType.ERROR, "No patch selected.").showAndWait();
                calculatePatchButton.setDisable(false);
                return;
            }

            if(!savePatch()) { // Save the current patch before calculating
                new Alert(Alert.AlertType.ERROR, "Failed to save patch.").showAndWait();
                calculatePatchButton.setDisable(false);
                return;
            }

            PatchInfo patch = patches.get(selectedPatchIndex);
            try {
                final javafx.concurrent.Task<String> task = new javafx.concurrent.Task<String>() {
                    @Override
                    protected String call() {
                        final String cleanedStablesPre = CoverWindow.cleanStables(CoverWindow.stablesString, pool);
                        final Tuple2<String, String> cleanedTriplesPre = CoverWindow.cleanTriples(CoverWindow.triplesString, pool);
                        final String cleanedTriples = cleanedTriplesPre._1;
                        final String cleanedStables = (cleanedStablesPre + '\n' + cleanedTriplesPre._2).trim();

                        return Wrapper.coverWrapper(patch.polygonString, cleanedStables, cleanedTriples,
                                patch.digits, patch.magnification, patch.empties, true, pool);
                    }
                };
                task.setOnSucceeded(ev -> {
                    calculatePatchButton.setDisable(false);

                    boolean isCovered = task.getValue().isEmpty();
                    if(isCovered) viewer.coveredPatchAreas.add(Polygon.createConvexPolygon(patch.polygonString));

                    drawPatch.run();
                    loadPatch.run();
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
                new Alert(Alert.AlertType.INFORMATION, "Patch calculated successfully.").showAndWait();
            } catch (Exception ex) {
                new Alert(Alert.AlertType.ERROR, "Error calculating patch: " + ex.getMessage()).showAndWait();
            }
        });


        Button clearPatches = new Button("Clear Visible Patches");
        clearPatches.setOnAction((ActionEvent e) -> {
            viewer.coveredPatchAreas.clear();
            viewer.patchAreas.clear();
            viewer.patchImageView.setImage(new WritableImage(Viewer.SIDE, Viewer.SIDE));
            new Alert(Alert.AlertType.INFORMATION, "Visible patches cleared.").showAndWait();
        });
        patchBox.setAlignment(Pos.CENTER_LEFT);
        patchBox.getChildren().addAll(updatePatchButton, calculatePatchButton, clearPatches);

        polygonField.setPromptText("Polygon");
        magnificationField.setPromptText("Magnification");
        digitsField.setPromptText("Digits");
        emptiesField.setPromptText("Empties");
        fields.getChildren().addAll(polygonField, digitsLabel, digitsField, magnificationLabel, magnificationField,  emptiesLabel, emptiesField, patchBox);

        root.addEventFilter(PatchSelectEvent.PATCH_UPDATED, event -> {
            int index = event.getSelectedPatchIndex();
            if (index == -1) {
                polygonField.clear();
                magnificationField.clear();
                digitsField.clear();
                emptiesField.clear();
                return;
            }
            PatchInfo patch = patches.get(event.getSelectedPatchIndex());
            polygonField.setText(patch.polygonString);
            magnificationField.setText(String.valueOf(patch.magnification));
            digitsField.setText(String.valueOf(patch.digits));
            emptiesField.setText(String.valueOf(patch.empties));
        });

        return fields;
    }

    public void savePolygonsToFile() {
        if(patches.isEmpty()) {
            Utils.writeToFile(Viewer.tmpDir + "/patches.txt", "");
            return;
        }
        String allPatches = patches.stream()
                .map(p -> p.polygonString)
                .reduce("", (a, b) -> a + System.lineSeparator() + System.lineSeparator() + b)
                .substring(2);

        Utils.writeToFile(Viewer.tmpDir + "/patches.txt", allPatches);
    }

    public void saveParametersToFile() {
        if(patches.isEmpty()) {
            Utils.writeToFile(Viewer.tmpDir + "/patch_parameters.txt", "");
            return;
        }
        String allParameters = patches.stream()
                .map(p -> String.format("%d %d %d %d", p.id, p.magnification, p.digits, p.empties))
                .reduce("", (a, b) -> a + System.lineSeparator() + System.lineSeparator() + b)
                .substring(2);

        Utils.writeToFile(Viewer.tmpDir + "/patch_parameters.txt", allParameters);
    }

    public void loadFromFiles() {
        String patchesString = Utils.readFromFile(Viewer.tmpDir + "/patches.txt").trim();
        String parametersString = Utils.readFromFile(Viewer.tmpDir + "/patch_parameters.txt").trim();

        if(patchesString.isEmpty() || parametersString.isEmpty()) {
            return;
        }

        String[] patchStrings = patchesString.split("\\R\\R");
        String[] parameterStrings = parametersString.split("\\R\\R");

        if (patchStrings.length != parameterStrings.length) {
            new Alert(Alert.AlertType.ERROR, "Mismatch between number of patches and number of parameter sets.").showAndWait();
            return;
        }

        patches.clear();
        for (int i = 0; i < patchStrings.length; i++) {
            String patchString = patchStrings[i].trim();
            String parameterString = parameterStrings[i].trim();
            String[] params = parameterString.split(" ");
            if (params.length != 4) {
                new Alert(Alert.AlertType.ERROR, "Invalid parameter set: " + parameterString).showAndWait();
                return;
            }
            try {
                int id = Integer.parseInt(params[0]);
                int magnification = Integer.parseInt(params[1]);
                int digits = Integer.parseInt(params[2]);
                int empties = Integer.parseInt(params[3]);
                PatchInfo patch = new PatchInfo(id, patchString, magnification, digits, empties);
                patches.add(patch);
            } catch (NumberFormatException ex) {
                new Alert(Alert.AlertType.ERROR, "Error parsing parameters: " + ex.getMessage()).showAndWait();
                return;
            }
        }
    }

    private boolean savePatch() {
        PatchInfo patch = patches.get(selectedPatchIndex);
        try {
            patch.polygonString = Polygon.cleanPolygon(polygonField.getText());
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Error in polygon: " + ex.getMessage()).showAndWait();
            return false;
        }

        try {
            patch.magnification = Integer.parseInt(magnificationField.getText());
            patch.digits = Integer.parseInt(digitsField.getText());
            patch.empties = Integer.parseInt(emptiesField.getText());
        } catch (NumberFormatException ex) {
            new Alert(Alert.AlertType.ERROR, "Error parsing magnification, digits, or empties: " + ex.getMessage()).showAndWait();
            return false;
        }

        savePolygonsToFile();
        saveParametersToFile();
        return true;
    }

    private Button makePatchButton(PatchInfo patch) {
        Button button = new Button("Patch " + patch.id);
        button.setOnAction((ActionEvent e) -> { 
            button.fireEvent(new PatchSelectEvent(PatchSelectEvent.PATCH_UPDATED, patches.indexOf(patch)));
        });
        return button;
    }

    public ArrayList<CoverableRegion> getCoverableRegions() {
        ArrayList<CoverableRegion> coverableRegions = new ArrayList<>();
        for (PatchInfo patch : patches) {
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
    PatchInfo(int id, String polygonString, int magnification, int digits, int empties) {
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

    public PatchSelectEvent(EventType<? extends PatchSelectEvent> eventType, int patchIndex) {
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

    public PatchCreateEvent(EventType<? extends PatchCreateEvent> eventType, PatchInfo newPatch) {
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

    public PatchRemoveEvent(EventType<? extends PatchRemoveEvent> eventType, PatchInfo removedPatch, int removedPatchIndex) {
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