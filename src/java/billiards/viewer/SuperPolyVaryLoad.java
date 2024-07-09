package billiards.viewer;

import billiards.geometry.Vector2;

import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.list.mutable.FastList;

import java.util.Optional;

import billiards.geometry.ConvexPolygon;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javaslang.Tuple;
import javaslang.Tuple7;

public class SuperPolyVaryLoad {
	// WARNING: Global mutable state
    // ------------------------------------------------------------
    public static String fullContent = "";
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
    public static Boolean Override = false;
    public static Boolean AutoCover = true;
    // ------------------------------------------------------------

    private final TextArea text = new TextArea();
    private final Label codel = new Label();
    private final Label CSl = new Label();
    private final Label OSOl = new Label();
    private final Label OSNOl = new Label();
    private final TextField CSbox = new TextField();
    private final TextField OSObox = new TextField();
    private final TextField OSNObox = new TextField();
    private final Label ssuml = new Label();
    private final Label CSsl = new Label();
    private final Label OSOsl = new Label();
    private final Label OSNOsl = new Label();
    private final TextField CSsbox = new TextField();
    private final TextField OSOsbox = new TextField();
    private final TextField OSNOsbox = new TextField();
    private final Label stepl = new Label();
    private final Label CSstepl = new Label();
    private final Label OSOstepl = new Label();
    private final Label OSNOstepl = new Label();
    private final TextField CSstepbox = new TextField();
    private final TextField OSOstepbox = new TextField();
    private final TextField OSNOstepbox = new TextField();
    //private final CheckBox overrideBox = new CheckBox();
    private final CheckBox autoCoverBox = new CheckBox();
    private final Label repl = new Label();
    private final TextField repBox = new TextField();
    private final Button loadButton = new Button();
    private final VBox root = new VBox();
    private final VBox maxVBox = new VBox(10);
    private final VBox controlVBox = new VBox(20);
    private final VBox repVBox = new VBox(10);
    private final HBox instructHBox = new HBox();
    private final HBox bottomHBox = new HBox();
    private final HBox maxHBox = new HBox(10);
    private final HBox maxOptHBox = new HBox(10);
    private final HBox maxStepHBox = new HBox(10);
    private final HBox loadHBox = new HBox(10);
    public final Stage stage = new Stage();
    private final Scene scene = new Scene(root);
    private final Label instruct = new Label();
    
    private Optional<Tuple7<ConvexPolygon, Integer, Integer, Integer, Integer, Integer, Integer>> result;
    
    public SuperPolyVaryLoad(final String windowTitle, final String buttonText, final String fileName, final String boundsFileName, final String stepFileName) {
    	fullContent = Utils.readFromFile(fileName);
    	String[] boundTokens = Utils.readFromFile(boundsFileName).trim().split(" ");
    	String[] stepTokens = Utils.readFromFile(stepFileName).trim().split(" ");
    	if (boundTokens.length >= 6) {
    		try {
    			BoundCSMax = Integer.parseInt(boundTokens[0]);
    			BoundOSOMax = Integer.parseInt(boundTokens[1]);
    			BoundOSNOMax = Integer.parseInt(boundTokens[2]);
    			BoundCSMaxSS = Integer.parseInt(boundTokens[3]);
    			BoundOSOMaxSS = Integer.parseInt(boundTokens[4]);
    			BoundOSNOMaxSS = Integer.parseInt(boundTokens[5]);
    		} catch (NumberFormatException e) {
    			BoundCSMax = 300;
    			BoundOSOMax = 100;
    			BoundOSNOMax = 36;
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

    	stage.setScene(scene);
    	stage.setTitle(windowTitle);
    	stage.setOnCloseRequest(e -> {
    		this.result = Optional.empty();
    		stage.close();
    	});
    	
    	text.setPrefColumnCount(40);
    	text.setPrefRowCount(10);
    	text.setWrapText(true);
    	text.setEditable(true);
    	text.setFont(Font.font("Monaco", 16));
    	text.setText(fullContent);
    	VBox.setVgrow(text, Priority.ALWAYS);
    	
    	instruct.setText("Enter points on separate lines, with the coordinates separated by a space.");
    	instruct.setPadding(new Insets(5, 5, 5, 10));
    	
        codel.setText("Code length:");
    	CSbox.setPrefWidth(150);
    	CSbox.setText(BoundCSMax.toString());
    	CSl.setText("CS max:");
    	OSObox.setPrefWidth(150);
    	OSObox.setText(BoundOSOMax.toString());
    	OSOl.setText("OSO max:");
    	OSNObox.setPrefWidth(150);
    	OSNObox.setText(BoundOSNOMax.toString());
    	OSNOl.setText("OSNO max:");

        ssuml.setText("Side sum:");
        CSsbox.setPrefWidth(150);
    	CSsbox.setText(BoundCSMaxSS.toString());
    	CSsl.setText("CS max:");
    	OSOsbox.setPrefWidth(150);
    	OSOsbox.setText(BoundOSOMaxSS.toString());
    	OSOsl.setText("OSO max:");
    	OSNOsbox.setPrefWidth(150);
    	OSNOsbox.setText(BoundOSNOMaxSS.toString());
    	OSNOsl.setText("OSNO max:");

        stepl.setText("SS step:");
        CSstepbox.setPrefWidth(150);
    	CSstepbox.setText(BoundCSstep.toString());
    	CSstepl.setText("CS step:");
    	OSOstepbox.setPrefWidth(150);
    	OSOstepbox.setText(BoundOSOstep.toString());
    	OSOstepl.setText("OSO step:");
    	OSNOstepbox.setPrefWidth(150);
    	OSNOstepbox.setText(BoundOSNOstep.toString());
    	OSNOstepl.setText("OSNO step:");

        repl.setText("Reps");
        repBox.setPrefWidth(50);
        repBox.setText(Reps.toString());


        /*
        overrideBox.setIndeterminate(false);
        overrideBox.setAllowIndeterminate(false);
        overrideBox.setSelected(Override);
        overrideBox.setText("Override side sum");
        */

        autoCoverBox.setIndeterminate(false);
        autoCoverBox.setAllowIndeterminate(false);
        autoCoverBox.setSelected(AutoCover);
        autoCoverBox.setText("Add codes to cover");

    	instructHBox.getChildren().add(instruct);

    	maxHBox.getChildren().addAll(codel, CSl, CSbox, OSOl, OSObox, OSNOl, OSNObox);
    	maxHBox.setPadding(new Insets(0, 10, 10, 0));
    	maxHBox.setAlignment(Pos.CENTER);

        maxOptHBox.getChildren().addAll(ssuml, CSsl, CSsbox, OSOsl, OSOsbox, OSNOsl, OSNOsbox);
        maxOptHBox.setPadding(new Insets(0, 10, 10, 0));
        maxOptHBox.setAlignment(Pos.CENTER);

        maxStepHBox.getChildren().addAll(stepl, CSstepl, CSstepbox, OSOstepl, OSOstepbox, OSNOstepl, OSNOstepbox);
        maxStepHBox.setPadding(new Insets(0, 10, 10, 0));
        maxStepHBox.setAlignment(Pos.CENTER);

        maxVBox.getChildren().addAll(maxHBox, maxOptHBox, maxStepHBox);
        repVBox.getChildren().addAll(repl, repBox);
        loadHBox.getChildren().addAll(loadButton, repVBox);
        loadHBox.setAlignment(Pos.CENTER);
        //controlVBox.getChildren().addAll(loadHBox, overrideBox, autoCoverBox);
        controlVBox.getChildren().addAll(loadHBox, autoCoverBox);
        controlVBox.setPadding(new Insets(0, 10, 10, 0));
        controlVBox.setAlignment(Pos.CENTER_LEFT);

        bottomHBox.getChildren().addAll(maxVBox, controlVBox);

    	root.getChildren().addAll(instructHBox, text, bottomHBox);
    	root.setSpacing(10);
    	root.setPadding(new Insets(10));
    
    	loadButton.setText(buttonText);
    	Utils.colorButton(loadButton, Color.SKYBLUE, Color.GOLD);
    	loadButton.setOnAction(event -> {
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
    		fullContent = text.getText();
    		final String[] lines = cleanPolygon(fullContent).split("\n");
    		final MutableList<Vector2> pointList = new FastList<>();
    		for (final String line : lines) {
    			final String[] coords = line.split(" ");
    			final double x = Math.toRadians(Double.parseDouble(coords[0]));
    			final double y = Math.toRadians(Double.parseDouble(coords[1]));
    			pointList.add(Vector2.create(x, y));
    		}
    		final ConvexPolygon poly = ConvexPolygon.create(pointList.toImmutable());
        	this.result = Optional.of(Tuple.of(poly, BoundCSMax, BoundOSOMax, BoundOSNOMax, BoundCSMaxSS, BoundOSOMaxSS, BoundOSNOMaxSS));
        	Utils.writeToFile(fileName, fullContent);
        	Utils.writeToFile(boundsFileName, String.format("%d %d %d %d %d %d", BoundCSMax, BoundOSOMax, BoundOSNOMax, BoundCSMaxSS, BoundOSOMaxSS, BoundOSNOMaxSS));
        	stage.close();
    	});
    }
    
    public Optional<Tuple7<ConvexPolygon, Integer, Integer, Integer, Integer, Integer, Integer>> getLoad() {
    	stage.showAndWait();
    	return this.result;
    }
    public Boolean getOverride() {
        return Override;
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
}
