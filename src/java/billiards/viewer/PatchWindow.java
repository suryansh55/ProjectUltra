package billiards.viewer;

import java.util.ArrayList;
import java.util.Optional;

import billiards.patch.Patch;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventType;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class PatchWindow {
	private final VBox root = new VBox(10);
	private final Stage stage = new Stage();
	private final Scene scene = new Scene(root);

    private final TextArea polygonField = new TextArea();
    private final TextField magnificationField = new TextField();
    private final TextField digitsField = new TextField();
    private final TextField emptiesField = new TextField();
    
    private ObservableList<PatchInfo> patches = FXCollections.observableArrayList();

    public PatchWindow() {
        stage.setTitle("Patch Window");
        stage.setScene(scene);


        root.setPrefHeight(600);
        root.setPrefWidth(400);
        root.setPadding(new Insets(10));
        root.getChildren().addAll(getPatchesMenu(), getPatchControls(), getPatchFields());
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
                        container.getChildren().add(new Button("Patch " + patches.indexOf(p)));
                    }
                }
            }
        });

        container.addEventHandler(PatchCreateEvent.PATCH_CREATED, event -> { 
            patches.add(event.getNewPatch());
            container.getChildren().add(new Button("Patch " + patches.size()));
            container.fireEvent(new PatchSelectEvent(PatchSelectEvent.PATCH_UPDATED, event.getNewPatch()));
        });

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
            PatchInfo newPatch = new PatchInfo("1 0\n0 0\n0 1\n 1 1", 1, 1, 1);
            patches.add(newPatch);
            addPatchButton.fireEvent(new PatchCreateEvent(PatchCreateEvent.PATCH_CREATED, newPatch));
        });
        Button removePatchButton = new Button("Remove Patch");
        removePatchButton.setOnAction((ActionEvent e) -> { });

        controls.getChildren().addAll(addPatchButton, removePatchButton);
        return controls;
    }
    
    private VBox getPatchFields() {
        VBox fields = new VBox(10);
        fields.setAlignment(Pos.CENTER_LEFT);

        polygonField.setPromptText("Polygon");
        magnificationField.setPromptText("Magnification");
        digitsField.setPromptText("Digits");
        emptiesField.setPromptText("Empties");
        fields.getChildren().addAll(polygonField, magnificationField, digitsField, emptiesField);

        fields.addEventHandler(PatchSelectEvent.PATCH_UPDATED, event -> {
            PatchInfo patch = event.getSelectedPatch();
            polygonField.setText(patch.polygonString);
            magnificationField.setText(String.valueOf(patch.magnification));
            digitsField.setText(String.valueOf(patch.digits));
            emptiesField.setText(String.valueOf(patch.empties));
        });
        return fields;
    }
}

class PatchInfo {
    String polygonString;
    int magnification;
    int digits;
    int empties;
    PatchInfo(String polygonString, int magnification, int digits, int empties) {
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
    

    private final PatchInfo selectedPatch;

    public PatchSelectEvent(EventType<? extends PatchSelectEvent> eventType, PatchInfo selectedPatch) {
        super(eventType);
        this.selectedPatch = selectedPatch;
    }

    public PatchInfo getSelectedPatch() {
        return selectedPatch;
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
    private final Patch removedPatch;

    public PatchRemoveEvent(EventType<? extends PatchRemoveEvent> eventType, Patch removedPatch) {
        super(eventType);
        this.removedPatch = removedPatch;
    }

    public Patch getRemovedPatch() {
        return removedPatch;
    }
}