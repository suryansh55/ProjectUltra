package billiards.viewer;

import billiards.geometry.ConvexPolygon;
import billiards.geometry.Rectangle;
import billiards.geometry.Vector2;

import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.list.mutable.FastList;

import java.util.Optional;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javaslang.Tuple;
import javaslang.Tuple8;

public final class PolyVaryLoad {
    // WARNING: Global mutable state
    // ------------------------------------------------------------
    private static String fullContent = "";
    public static Integer BoundCSMax = 800;
    public static Integer BoundOSOMax = 300;
    public static Integer BoundOSNOMax = 150;
    public static Integer BoundCSMaxSS = 2000;
    public static Integer BoundOSOMaxSS = 300;
    public static Integer BoundOSNOMaxSS = 150;

    // ------------------------------------------------------------

    private final TextArea text = new TextArea();
    private final Button loadButton = new Button();
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
    private final Label overridel = new Label();
    private final CheckBox overrideBox = new CheckBox();
    private final VBox root = new VBox();
    private final VBox typeVBox = new VBox(30);
    private final VBox maxVBox = new VBox(10);
    private final VBox controlVBox = new VBox(20);
    private final HBox instructHBox = new HBox();
    private final HBox bottomHBox = new HBox();
    private final HBox maxHBox = new HBox(10);
    private final HBox maxOptHBox = new HBox(10);
    private final HBox overrideHBox = new HBox(10);
    private final Stage stage = new Stage();
    private final Scene scene = new Scene(root);
    private final Label instruct = new Label();

    private Optional<Tuple8<ConvexPolygon, Integer, Integer, Integer, Integer, Integer, Integer, Boolean>> result;

    public PolyVaryLoad(final String windowTitle, final String buttonText, final String fileName,
                    final String boundsFileName, final Rectangle fullScreen) {

        fullContent = Utils.readFromFile(fileName);
    	String[] boundTokens = Utils.readFromFile(boundsFileName).trim().split(" ");
    	if (boundTokens.length == 3) {
    		try {
    			BoundCSMax = Integer.parseInt(boundTokens[0]);
    			BoundOSOMax = Integer.parseInt(boundTokens[1]);
    			BoundOSNOMax = Integer.parseInt(boundTokens[2]);
    		} catch (NumberFormatException e) {
    			BoundCSMax = 800;
    			BoundOSOMax = 300;
    			BoundOSNOMax = 150;
    		}
    	}

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

        // Get seperate bounds on each code
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

        overrideBox.setIndeterminate(false);
        overrideBox.setAllowIndeterminate(false);
        overrideBox.setSelected(false);
        overridel.setText("Override side sum:");

        instructHBox.getChildren().add(instruct);

        typeVBox.getChildren().addAll(codel, ssuml);
        typeVBox.setPadding(new Insets(0, 10, 10, 0));
        typeVBox.setAlignment(Pos.CENTER);

        maxHBox.getChildren().addAll(CSl, CSbox, OSOl, OSObox, OSNOl, OSNObox, loadButton);
        maxHBox.setPadding(new Insets(0, 10, 10, 0));
        maxHBox.setAlignment(Pos.CENTER);

        maxOptHBox.getChildren().addAll(CSsl, CSsbox, OSOsl, OSOsbox, OSNOsl, OSNOsbox, overridel, overrideBox);
        maxOptHBox.setPadding(new Insets(0, 10, 10, 0));
        maxOptHBox.setAlignment(Pos.CENTER);

        maxVBox.getChildren().addAll(maxHBox, maxOptHBox);
        overrideHBox.getChildren().addAll(overridel, overrideBox);
        controlVBox.getChildren().addAll(loadButton, overrideHBox);
        controlVBox.setPadding(new Insets(0, 10, 10, 0));
        controlVBox.setAlignment(Pos.CENTER);

        bottomHBox.getChildren().addAll(typeVBox, maxVBox, controlVBox);


        root.getChildren().addAll(instructHBox, text, bottomHBox);
        root.setSpacing(10);
        root.setPadding(new Insets(10));

        loadButton.setText(buttonText);
        Utils.colorButton(loadButton, Color.SKYBLUE, Color.GOLD);
        loadButton.setOnAction(event -> {
    		try {
    			BoundCSMax = Integer.parseInt(CSbox.getText().trim());
            	BoundOSOMax = Integer.parseInt(OSObox.getText().trim());
            	BoundOSNOMax = Integer.parseInt(OSNObox.getText().trim());
    			BoundCSMaxSS = Integer.parseInt(CSsbox.getText().trim());
            	BoundOSOMaxSS = Integer.parseInt(OSOsbox.getText().trim());
            	BoundOSNOMaxSS = Integer.parseInt(OSNOsbox.getText().trim());
    		} catch (NumberFormatException e) {
    			final Alert alert = new Alert(AlertType.ERROR);
        		alert.setTitle("PolyVary Error");
        		alert.setHeaderText("Non-integer value in input box");
        		alert.setContentText("Please enter a single integer into each of the '[SequenceType] Max' boxes.");
                alert.showAndWait();
                return;
            }
            fullContent = text.getText();
            if (fullContent.equals("")) {
                this.result = Optional.of(Tuple.of(fullScreen.toConvexPolygon(), BoundCSMax, BoundOSOMax, BoundOSNOMax, BoundCSMaxSS, BoundOSOMaxSS, BoundOSNOMaxSS, overrideBox.isSelected()));

            } else {
                final String[] lines = cleanPolygon(fullContent).split("\n");
                final MutableList<Vector2> pointList = new FastList<>();

                for (final String line : lines) {
                    final String[] coords = line.split(" ");
                    final double x = Math.toRadians(Double.parseDouble(coords[0]));
                    final double y = Math.toRadians(Double.parseDouble(coords[1]));
                    pointList.add(Vector2.create(x, y));
                }
                final ConvexPolygon poly = ConvexPolygon.create(pointList.toImmutable());
                this.result = Optional.of(Tuple.of(poly, BoundCSMax, BoundOSOMax, BoundOSNOMax, BoundCSMaxSS, BoundOSOMaxSS, BoundOSNOMaxSS, overrideBox.isSelected()));
            }
            Utils.writeToFile(fileName, fullContent);
        	Utils.writeToFile(boundsFileName, String.format("%d %d %d", BoundCSMax, BoundOSOMax, BoundOSNOMax));
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

    public Optional<Tuple8<ConvexPolygon, Integer, Integer, Integer, Integer, Integer, Integer, Boolean>> getPolyVaryLoad() {
        stage.showAndWait();
        return this.result;
    }
}
