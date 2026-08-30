package billiards.viewer;

import billiards.codeseq.CodePair;
import billiards.codeseq.CodeSequence;
import billiards.codeseq.CodeType;
import billiards.codeseq.InitialAngles;
import billiards.codeseq.TriplePair;
import billiards.geometry.ConvexPolygon;
import billiards.geometry.LineSegment;
import billiards.geometry.Location;
import billiards.geometry.Rectangle;
import billiards.geometry.Point;
import billiards.math.CosEquation;
import billiards.math.Equation;
import billiards.math.SinEquation;
import billiards.math.XYEta;
import billiards.math.XYZ;
import billiards.wrapper.CodeInfo;
import billiards.wrapper.Wrapper;

import javafx.scene.control.*;
import javafx.scene.text.Font;

import org.apache.commons.math3.util.FastMath;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.impl.list.mutable.FastList;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;

import java.awt.MouseInfo;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.DoubleUnaryOperator;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.transform.Affine;
import javafx.stage.Stage;

import javax.swing.plaf.basic.BasicInternalFrameTitlePane;

public final class Viewer {

    // the time the tool tips take to open and close, in seconds
    private static final double TipOpenDelay = 2;
    private static final double TipCloseDelay = 20;

    private static final int SIZE = (700);

    // IMPORTANT: This is the color of the 90 and 80 lines and any additional lines
    private static final Color lineColor = Color.MAGENTA;//MAGENTA george

    final Color clickColor = Color.GOLD;
    final String textBoxColor = Utils.hex(Color.MISTYROSE);
    final Color plusColor = Color.LIGHTGREEN;
    final Color minusColor = Color.LIGHTCORAL;
    final Color panColor = Color.MAROON;
    final Color polyBoundColor = Color.LIME;
    final Color fillBoundColor = Color.YELLOW;
    final Color coverPolyBoundColor0 = Color.LIME;
    final Color coverPolyBoundColor1 = Color.AQUA;
    final Color coverAreaColor = Color.DARKORANGE;


    // All the JavaFX gui components are global, but the arrays and maps and such
    // that we use to help them out are created in the constructor and then passed
    // to the handlers as necessary

    Optional<String> currentCover = Optional.empty();

    final PixelRadianMap map = new PixelRadianMap(SIZE);

    // The map has the default viewing rectangle, and that is the beginning place to go
    final BackwardForward<Rectangle> viewRectangleBF = BackwardForward.create(map.getViewRectangle());

    // these are the code sequences that are currently on screen
    // we want to remember the ordering everything gets

    // No files by default
    ArrayList<CodeSequence> fileCodeSequences = new ArrayList<>();

    ArrayList<HashTriple> coverRectList = new ArrayList<HashTriple>();

    Optional<ConvexPolygon> coverArea = Optional.empty();

    // GUI components

    // This gets passed in the constructor, and we initialize it there
    final Stage mainWindow;

    // main window
    final GridPane codeSequencesGPane = new GridPane();

    // the viewer image
    final StackPane imageStack = new StackPane();


    // static method, since the background is just white
    final ImageView backgroundImageView = renderColor(Color.WHITE);
    final ImageView guideLinesImageView = new ImageView();
    StackPane regionStack = new StackPane();
    ArrayList<String> regionsStackList = new ArrayList<String>();
    final ImageView boundsImageView = new ImageView();

    ArrayList<Storage> currentStorages = new ArrayList<>();
    ArrayList<ConvexPolygon> currentBounds = new ArrayList<>();
    Storage currentStorage;

    CodePair currentCodePair;

    Color currentColor = Color.RED;

    // the regionIV is kept separate, because it allows us to redraw this
    // one without redrawing everything else
    final ImageView regionIV = new ImageView();

    // This one is transparent, and goes on top to capture all the mouse events
    final ImageView topImageView = renderColor(Color.TRANSPARENT);
    ArrayList<CodePair> multiCodePairs = new ArrayList<>();

   /* final MutableList<Point> infPatPoints = FastList.newListWith(
    		//Point.create(0, Math.PI / 2),
    		Point.create(22.49 * Math.PI / 180, 22.61 * Math.PI / 180),
    		Point.create(22.55 * Math.PI / 180, 22.55 * Math.PI / 180),
    		Point.create(22.49 * Math.PI / 180, 22.49 * Math.PI / 180));
    		//Point.create(0, 67.5 * Math.PI / 180));
    final ConvexPolygon infPatternArea = ConvexPolygon.create(infPatPoints.toImmutable());
    */
    
  /*  final MutableList<Point> infPatPoints2 = FastList.newListWith(
    		
	Point.create(224998 * Math.PI / 1800000, 225002 * Math.PI / 1800000),
	Point.create(225001 * Math.PI / 1800000, 225023 * Math.PI / 1800000),
	Point.create(225012 * Math.PI / 1800000, 225012 * Math.PI / 1800000),
	Point.create(22499999 * Math.PI / 180000000, 22499999 * Math.PI / 180000000));
	
    final ConvexPolygon infPatternArea2 = ConvexPolygon.create(infPatPoints2.toImmutable());
*///note this will grey out george may 31,2020

    // Add star square here in future
    // Note: to add star square, copy and paste the following lines and enter the x, y, radius and extra magnifications.
  final Rectangle starPatternArea = Rectangle.createInsideArbitrary(Math.toRadians(10), Math.toRadians(20), 0.000000000000198, 0);
  //this is a 43 george aug22,2022 change the  0.000000000000198, 0); to  0.000000000000198, 1);is a 44,  0.000000000000198, 2); is a 45 and so forth
//    final Rectangle starPatternArea2 = Rectangle.createInsideArbitrary(Math.toRadians(15), Math.toRadians(30), 1.06465, 0);


    final MutableList<Point> infPatPoints = FastList.newListWith(//george
    		Point.create(0, Math.PI / 2),
    		Point.create(22.5 * Math.PI / 180, 67.5 * Math.PI / 180),
    		Point.create(0, 67.5 * Math.PI / 180));
    final ConvexPolygon infPatternArea = ConvexPolygon.create(infPatPoints.toImmutable());

    final MutableList<Point> infPatPoints2 = FastList.newListWith(//george
            Point.create(345 * Math.PI/2048, 679 * Math.PI/2048),
            Point.create(1380 * Math.PI/8192, 2712 * Math.PI/8192),
            Point.create(1352 * Math.PI/8192, 2740 * Math.PI / 8192),
            Point.create(169 * Math.PI/1024, 343 * Math.PI/1024));
    final ConvexPolygon infPatternArea2 = ConvexPolygon.create(infPatPoints2.toImmutable());
    final MutableList<Point> infPatPoints3 = FastList.newListWith(//george
            Point.create(1641 * Math.PI/8192, 2455 * Math.PI/8192),
            Point.create(1637 * Math.PI/8192, 2455 * Math.PI/8192),
            Point.create(1637 * Math.PI/8192, 2459 * Math.PI/8192));
    final ConvexPolygon infPatternArea3 = ConvexPolygon.create(infPatPoints3.toImmutable());

    final MutableList<Point> infPatPoints4 = FastList.newListWith(//george
            Point.create(63 * Math.PI/256, 65 * Math.PI/256),
            Point.create(65 * Math.PI/256, 63 * Math.PI/256),
            Point.create(63 * Math.PI/256, 63 * Math.PI/256));
    final ConvexPolygon infPatternArea4 = ConvexPolygon.create(infPatPoints4.toImmutable());

    // we use this list to keep track of which checkboxes are selected, since a new checkbox is
    // made each time we click somewhere.
    final int[] drawCBoxes = {-1, -1, -1, -1, -1, -1, -1};

    // right side box with mouse coordinates and code info
    // Degree
    final HBox textXHBox = new HBox();
    final Label textXLabel = new Label();
    final TextField textXField = new TextField();

    final HBox textYHBox = new HBox();
    final Label textYLabel = new Label();
    final TextField textYField = new TextField();

    // Lock
    final HBox textXLockHBox = new HBox();
    final Label textXLockLabel = new Label();
    final TextField textXLockField = new TextField();

    final HBox textYLockHBox = new HBox();
    final Label textYLockLabel = new Label();
    final TextField textYLockField = new TextField();

    final TextField xMinTextField = new TextField();
    final TextField xMaxTextField = new TextField();
    final TextField yMinTextField = new TextField();
    final TextField yMaxTextField = new TextField();

    final Button zoomButton = new Button();
    Color zoomColor = Color.RED;

    final RadioButton selectRdoBtn = new RadioButton();
    final RadioButton magnifyRdoBtn = new RadioButton();
    final RadioButton centerBtn = new RadioButton();
    final RadioButton demagnifyRdoBtn = new RadioButton();
    final ToggleGroup magnifyGroup = new ToggleGroup();

    final Label zoomScaleLabel = new Label();
    final TextField zoomScaleText = new TextField();
    final Button backwardSquareButton = new Button();
    final Button forwardSquareButton = new Button();

    final Button clearBtn = new Button();
    final Button resetBtn = new Button();
    final Button infoButton = new Button();

    final Button loadCoverBtn = new Button();

    final Button unloadCoverBtn = new Button();
    final ComboBox<String> coversBox = new ComboBox<>();

    final Button checkCoverBtn = new Button();

    final Button checkOneBtn = new Button();
    final VBox checkOneWrap = new VBox(10);
    final TextArea checkOneInfo = new TextArea();

    final TextField labelMainWindow = new TextField();
    final Button covRectsColorBox = new Button();
    final Map<ConvexPolygon, Color> mrrBounds = new HashMap<>();
    Rectangle selectedRect = null;
    Color coverColor = Color.BLACK;

    final CheckBox coverColorCycle = new CheckBox();

    final CheckBox showAllGuidelines = new CheckBox();
    final CheckBox showCoverGuidelines = new CheckBox();

    final CheckBox loadBtn2 = new CheckBox();

    private SortedSet<Storage> selectedStorages;

    private boolean starClicked = false;

    public Viewer(final Stage primaryStage) {
        // This gets passed in from the outside world
        mainWindow = primaryStage;

        //final String windowTitle = String.format("The Tokarsky-Marinov Covers Feb 22, 2026copy");//george dec 28,2020
        final String windowTitle = String.format("The Tokarsky-Marinov Covers Aug 30, 2026");//george dec 28,2020

        Utils.setupCustomTooltipBehavior((int) (TipOpenDelay * 1000), (int) (TipCloseDelay * 1000), 200);

        zoomScaleLabel.setText("Zoom Scale:");
        zoomScaleText.setText("2");
        zoomScaleText.setTooltip(Utils.toolTip("The scale that you magnify and demagnify by"));
        zoomScaleText.setPrefWidth(55);
        zoomScaleText.setStyle(textBoxColor);

        backwardSquareButton.setText("Backward");
        backwardSquareButton.setTooltip(Utils.toolTip("Go to the last screen view you were at"));

        Utils.colorButton(backwardSquareButton, Color.SKYBLUE, clickColor);

        backwardSquareButton.setOnAction(event -> {

            viewRectangleBF.backward().ifPresent(rect -> {
                map.setViewRectangle(rect);
                renderRegions(guideLinesImageView);
            });
        });

        forwardSquareButton.setText("Forward");
        forwardSquareButton.setTooltip(Utils.toolTip("Go to the next screen view you were at"));
        Utils.colorButton(forwardSquareButton, Color.SKYBLUE, clickColor);

        forwardSquareButton.setOnAction(event -> {

            viewRectangleBF.forward().ifPresent(rect -> {
                map.setViewRectangle(rect);
                renderRegions(guideLinesImageView);
            });
        });

        // Create a new info window and show it
        infoButton.setText("Info");
        infoButton.setTooltip(Utils.toolTip("Brings up a window that will show you information about"
                                            + " a code sequence"));
        Utils.colorButton(infoButton, Color.LIGHTPINK, clickColor);

        infoButton.setOnAction(event -> new InfoWindow(windowTitle).show());

        coversBox.getItems().addAll("100-105","105-110","110-112","112-112.4","G","10-12","Tas3", "Tas4","Tas5","Tas6","Tas7","Tas8", "Tas9", "Tas10","Tas11", "Tas12","nick1", "nick2", "nick3", "nick4", "nick5", "nick6","Mish1","Mish2", "Mish3", "Mish4", "Max", "Huan", "Angad","Geo1","Mai1","Mai2","Sur1","Sur2","Sur3","Tas1","Tas2") ;//george
       // coversBox.getItems().addAll( "G","10-12","Tas3", "Tas4","Tas5","Tas6","Tas7","Tas8", "Tas9", "Tas10","Tas11", "Tas12","nick1", "nick2", "nick3", "nick4", "nick5", "nick6","Mish1","Mish2", "Mish3", "Mish4", "Max", "Huan", "Angad", "G","90-100", "100-105", "105-110", "110-112", "112-112.1", "112.1-112.2", "112.2-112.3", "112.3-112.4", "112.3-112.4A", "112.3-112.4B", "112.3-112.4C", "112.3-112.4D", "112.3-112.4E", "112.3-112.4F", "112.3-112.4G", "112.3-112.4H", "112.3-112.4I", "112.3-112.4J", "112.3-112.4K", "112.3-112.4L", "12-14", "14-15", "15-17", "17-22.4988", "22.4988-33.8", "A24-56.6", "A19-24", "A16.225-19", "A14-16.225", "A13.75-14", "A13.5-13.75", "A12-13.5", "A11-12", "B24-58", "B19.9-24", "B18.65-19.9", "B16.33-18.65", "B16.1-16.33", "B15.9-16.1", "B15.7-15.9", "B15.5-15.7", "B15.35-15.5","B14.5-15.35","B13.99-14.5","B13.7-13.99","B13.6-13.7","B13.5-13.6","B13.4-13.5","B13.35-13.4","B13.3-13.35", "B13.2-13.3", "B13.125-13.2", "B12.6-13.125", "B12.5-12.6","B12.48-12.5", "B12.12-12.48", "B12.05-12.12s", "B11.915-12.05s", "B11.815-11.915s", "B11.663-11.815s", "B11.565-11.663s", "B11.52-11.565s", "B11.47-11.52s", "B11.335-11.47s", "B11.31-11.335", "B11.3-11.31", "B11.28-11.3", "B11.26-11.28", "B10.7-11.26", "B10.65-10.7", "B10.6-10.65", "B10.5-10.6", "B10.3-10.5", "B10.2-10.3", "B10-10.2",  "6-10", "7-10", "G1", "G2", "G3", "G4", "G5", "G6", "G7", "G8", "G9", "G10", "G11", "G12","G13","G14","G15","G16","G17","G18","G19","G20","G21","G22","G23","G24","G25","G26","G27","G28","G29","G30","G31","G32","G33","G34","G35","G36","G37","G38","G39","G40","G41","G42","G43","G44","G45","G46","G47","G48","G49","G50","G51","G52","G53","G54","G55","G56","G57","G58","G59","G60","G61","G62","G63","G64","G65","G66","G67","G68","G69","G70","G71","G72","G73","G74","G75","G76","GT1","GT2","GT3","GT4","GT5","GT6","GT7","GT8","GT9","GT10","GT11","GT12","GT13","GT14","GT15","GT16","GT17","GT18","GT19","GT20","GT21","GT22","GT23","GT24","GT25","GT26","GT27","GT28","GT29","GT30","GT31","GT32","GT33","GT34","GT35","Geo1","Mai1","Mai2","Sur1","Sur2","Sur3","Tas1","Tas2") ;//george

        coversBox.setTooltip(Utils.toolTip("Select which cover will be loaded."));
        //coversBox.setValue("90-100");
        coversBox.setValue("90-100");
        Utils.colorButton(coversBox, Color.SKYBLUE, clickColor);

        loadCoverBtn.setText("Load Cover");
        Utils.colorButton(loadCoverBtn, Color.LIGHTPINK, clickColor);
        loadCoverBtn.setOnAction(e -> {
            File dir = getDir(coversBox.getValue());
            if (dir != null) {
                if (! loadBtn2.isSelected()) {
                    clearBtn.fire();
                }
                currentCover = Optional.of(dir.getPath());
                loadCoverAction(dir);
            }
        });

        unloadCoverBtn.setText("Unload Cover");
        Utils.colorButton(unloadCoverBtn, Color.LIGHTPINK, clickColor);
        unloadCoverBtn.setOnAction(e -> {
            coverRectList.remove(regionsStackList.lastIndexOf(coversBox.getValue()));
            regionStack.getChildren().remove(regionsStackList.lastIndexOf(coversBox.getValue()));
            regionsStackList.remove(coversBox.getValue());
        });
        checkCoverBtn.setText("Check Cover");
        Utils.colorButton(checkCoverBtn, Color.LIGHTPINK, clickColor);
        checkCoverBtn.setTooltip(Utils.toolTip("Checks each square in the cover that is currently loaded, this could" +
                " take hours or even days."));
        checkCoverBtn.setOnAction(e -> {

            if (currentCover.isPresent()) {

            	final Alert alert = new Alert(AlertType.CONFIRMATION);

                alert.setTitle("Check Cover");
                alert.setHeaderText("Check Cover");
                alert.setContentText("Checking a cover can take several hours or\n"
                		+ "even days. Continue?");
                final Optional<ButtonType> response = alert.showAndWait();
                if (response.isPresent() && response.get() == ButtonType.OK) {

                	try {
		                // The external verifier reads the loose text files. If this cover has been
		                // reduced to just cover.pack, regenerate them into a temp dir and point the
		                // verifier there so nothing is lost by deleting the .txt files.
		                String coverArg = currentCover.get();
		                File coverDir = new File(coverArg);
		                if (!new File(coverDir, "cover.txt").exists()
		                        && new File(coverDir, "cover.pack").exists()) {
		                    File tmp = java.nio.file.Files.createTempDirectory("cover-verify").toFile();
		                    CoverPack.unpack(new File(coverDir, "cover.pack"), tmp);
		                    coverArg = tmp.getPath();
		                }
		                final ProcessBuilder builder = new ProcessBuilder("build/exe/cover/cover",
		                                                                  coverArg);
		                // Redirect the stdout and stderr so they are printed
		                builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
		                builder.redirectError(ProcessBuilder.Redirect.INHERIT);

		                final Process process = builder.start();

				        process.waitFor();


	                } catch (final Exception ex) {
	                    new ErrorAlert(ex).showAndWait();
	                    return;
	                }
                }

            } else {
            	final Alert alert = new Alert(AlertType.INFORMATION);

                alert.setTitle("Cover");
                alert.setHeaderText("No cover loaded");
                alert.setContentText("Please load a cover before pressing this button.");

                alert.showAndWait();
            }
        });

        checkOneBtn.setText("Check this square");
        checkOneBtn.setTooltip(Utils.toolTip("Run our proof on one square"));
        Utils.colorButton(checkOneBtn, Color.LIGHTPINK, clickColor);
        checkOneBtn.setOnAction(event -> {
            checkThisSquareAction(currentCodePair);
        });
        VBox.setVgrow(checkOneWrap, Priority.ALWAYS);

        labelMainWindow.setPrefWidth(80);

        labelMainWindow.setPromptText("Label");
        imageStack.getChildren().addAll(backgroundImageView, regionStack, guideLinesImageView,
                                        boundsImageView, regionIV, topImageView);

        // For input validation, you have two things to check
        // Check if the string is empty. This means the user entered no input
        // Then check if the string is valid

        resetBtn.setText("Reset");
        resetBtn.setTooltip(Utils.toolTip("Change the zoom level back to the default, like it was"
                                          + " when you first opened the program."));
        Utils.colorButton(resetBtn, Color.SKYBLUE, clickColor);

        resetBtn.setOnAction(event -> {
            map.reset();
            viewRectangleBF.add(map.getViewRectangle());
            renderRegions(guideLinesImageView);
        });

        clearBtn.setText("Clear");
        clearBtn.setTooltip(Utils.toolTip("Clear everything from the screen  (The guidelines will be all that remains)"));

        Utils.colorButton(clearBtn, Color.SKYBLUE, clickColor);

        clearBtn.setOnAction(event -> {
            // reset the boxes on the right
            regionStack.getChildren().clear();
            coverRectList.clear();
            regionsStackList.clear();
            codeSequencesGPane.getChildren().clear();

            drawCBoxes[0] = -1;
            drawCBoxes[1] = -1;
            drawCBoxes[2] = -1;
            currentStorages.clear();
            currentBounds.clear();
            selectedRect = null;
            checkOneWrap.getChildren().clear();
            coverArea = Optional.empty();
            boundsImageView.setImage(new WritableImage(SIZE, SIZE));

            regionIV.setImage(new WritableImage(SIZE, SIZE));
        });

        xMinTextField.setPromptText("X min");
        xMinTextField.setPrefColumnCount(8);
        xMinTextField.setStyle(textBoxColor);

        xMaxTextField.setPromptText("X max");
        xMaxTextField.setPrefColumnCount(8);
        xMaxTextField.setStyle(textBoxColor);

        yMinTextField.setPromptText("Y min");
        yMinTextField.setPrefColumnCount(8);
        yMinTextField.setStyle(textBoxColor);

        yMaxTextField.setPromptText("Y max");
        yMaxTextField.setPrefColumnCount(8);
        yMaxTextField.setStyle(textBoxColor);

        covRectsColorBox.setText("Black");
        covRectsColorBox.setTooltip(Utils.toolTip("The color used by the cover squares"));
        covRectsColorBox.setPrefWidth(120);
        covRectsColorBox.setOnAction(event -> {
            final int mouseY = MouseInfo.getPointerInfo().getLocation().y + 20;
            final int mouseX = MouseInfo.getPointerInfo().getLocation().x;
            final ColorPicker picker = new ColorPicker(mouseX, mouseY);
            final Optional<Color> opt = picker.pickColor();
            opt.ifPresent(color -> {
                coverColor = color;
                covRectsColorBox.setText(Colors.colorMap.get(color).get());
            });
        });

        zoomButton.setText("Zoom");
        zoomButton.setTooltip(Utils.toolTip(
            "Zoom to the interval specified. Note, if the interval is not a square, it will zoom"
            + " to the best fitting square of that interval. You may set the minX, maxX equal and"
            + " minY, maxY equal. If so, the program just centers those coordinates"));
        Utils.colorButton(zoomButton, Color.SKYBLUE, clickColor);

        zoomButton.setOnAction(event -> zoomAction());

        coverColorCycle.setText("Cycle ");
        coverColorCycle.setTooltip(Utils.toolTip("Cycle through the top row of colors when clicking "
                                                 + "on the cover rectangles"));
        coverColorCycle.setStyle(textBoxColor);

        loadBtn2.setText("Load Multiple Covers ");
        loadBtn2.setTooltip(Utils.toolTip("Load multiple covers at once"));
        loadBtn2.setStyle(textBoxColor);

        showAllGuidelines.setText("Show all guidelines ");
        showAllGuidelines.setTooltip(Utils.toolTip("Show all the lines"));
        showAllGuidelines.setStyle(textBoxColor);
        showAllGuidelines.setOnAction(e -> guideLinesImageView.setImage(renderGuideLines()));

        showCoverGuidelines.setText("Show cover guidelines ");
        showCoverGuidelines.setTooltip(Utils.toolTip("Show cover the lines"));
        showCoverGuidelines.setStyle(textBoxColor);
        showCoverGuidelines.setOnAction(e -> {
            final WritableImage boundsImage = new WritableImage(SIZE, SIZE);
            if (showCoverGuidelines.isSelected()) {
                if (coverArea.isPresent()) {
                    renderPolygon(coverArea.get(), boundsImage, coverAreaColor);
                }
            }
            boundsImageView.setImage(boundsImage);

        });

        selectRdoBtn.setText("Select");
        selectRdoBtn.setSelected(true);
        magnifyRdoBtn.setText("Magnify");
        demagnifyRdoBtn.setText("Demagnify");
        centerBtn.setText("Center");
        selectRdoBtn.setStyle(textBoxColor);
        magnifyRdoBtn.setStyle(textBoxColor);
        demagnifyRdoBtn.setStyle(textBoxColor);
        centerBtn.setStyle(textBoxColor);
        centerBtn.setTooltip(Utils.toolTip("If you click the screen while this is selected, it will "
                                           + "pan so that the point you clicked is the new center of the screen"));

        selectRdoBtn.setToggleGroup(magnifyGroup);
        magnifyRdoBtn.setToggleGroup(magnifyGroup);
        demagnifyRdoBtn.setToggleGroup(magnifyGroup);
        centerBtn.setToggleGroup(magnifyGroup);

        // The topImageView is transparent and intercepts all the mouse events. This method allows
        // us to click on the transparent parts
        topImageView.setPickOnBounds(true);

        // handle panning and clicking events here
        topImageView.setOnMousePressed(event -> {
            final double initX = event.getX();
            final double initY = event.getY();
            final ImageView initLine = new ImageView();
            imageStack.getChildren().add(4, initLine);

            topImageView.setOnMouseDragged(event2 -> {
                final double finX = event2.getX();
                final double finY = event2.getY();
                final Optional<Line> panOpt = smartLine(initX, initY, finX, finY);
                if (panOpt.isPresent()) {
                	final Line panLine = panOpt.get();
	                panLine.setStroke(panColor);
	                imageStack.getChildren().remove(4);
	                imageStack.getChildren().add(4, panLine);
	                imageStack.getChildren().get(4).setTranslateX((finX + initX - SIZE) / 2);
	                imageStack.getChildren().get(4).setTranslateY((finY + initY - SIZE) / 2);
                }
            });
            topImageView.setOnMouseReleased(event3 -> {
                imageStack.getChildren().remove(4);
                pan(initX, initY, event3.getX(), event3.getY());
            });
        });

        textXLabel.setText("X:");
        textXField.setEditable(true);
        textXField.setPrefWidth(130);

        textYLabel.setText("Y:");
        textYField.setEditable(true);
        textYField.setPrefWidth(130);

        // Lock
        textXLockLabel.setText("X:");
        textXLockField.setEditable(false);
        textXLockField.setPrefWidth(130);

        textYLockLabel.setText("Y:");
        textYLockField.setEditable(false);
        textYLockField.setPrefWidth(130);

        topImageView.setOnMouseMoved(event -> {

            final double radianX = map.radianX(event.getX() + 0.5);
            final double radianY = map.radianY(event.getY() + 0.5);

            final double degreeX = Math.toDegrees(radianX);
            final double degreeY = Math.toDegrees(radianY);

            textXField.setText(Double.toString(degreeX));
            textYField.setText(Double.toString(degreeY));
        });

        final HBox minHBox = new HBox();
        minHBox.setSpacing(0);
        minHBox.getChildren().addAll(xMinTextField, yMinTextField);
        minHBox.setPadding(new Insets(0, 8, 8, 0));
        minHBox.setAlignment(Pos.CENTER);

        final HBox maxHBox = new HBox();
        maxHBox.setSpacing(0);
        maxHBox.getChildren().addAll(xMaxTextField, yMaxTextField);
        maxHBox.setPadding(new Insets(0, 8, 0, 0));
        maxHBox.setAlignment(Pos.CENTER);

        final VBox zoomFeildsVBox = new VBox();
        zoomFeildsVBox.setSpacing(8);
        zoomFeildsVBox.getChildren().addAll(minHBox, maxHBox);
        zoomFeildsVBox.setPadding(new Insets(0, 0, 8, 0));
        zoomFeildsVBox.setAlignment(Pos.CENTER);

        final HBox zoomHBox = new HBox();
        zoomHBox.setSpacing(8);
        zoomHBox.getChildren().addAll(zoomButton, zoomFeildsVBox);
        zoomHBox.setPadding(new Insets(0, 8, 0, 0));
        zoomHBox.setAlignment(Pos.CENTER);

        final HBox clickActionHBox = new HBox();
        clickActionHBox.setSpacing(8);
        clickActionHBox.getChildren().addAll(selectRdoBtn, magnifyRdoBtn, demagnifyRdoBtn, centerBtn);
        clickActionHBox.setPadding(new Insets(0, 8, 8, 0));
        clickActionHBox.setAlignment(Pos.CENTER);

        final HBox backForthHBox = new HBox();
        backForthHBox.setSpacing(8);
        backForthHBox.getChildren().addAll(
            zoomScaleLabel, zoomScaleText, backwardSquareButton, forwardSquareButton);
        backForthHBox.setPadding(new Insets(0, 8, 8, 0));
        backForthHBox.setAlignment(Pos.CENTER);

        final HBox windowHBox1 = new HBox(
            8, infoButton, clearBtn, coversBox, loadCoverBtn);
        windowHBox1.setPadding(new Insets(8, 8, 8, 0));
        windowHBox1.setAlignment(Pos.CENTER);

        final HBox windowHBox2 = new HBox(loadBtn2, coverColorCycle, unloadCoverBtn);
        windowHBox2.setPadding(new Insets(0, 8, 8, 0));
        windowHBox2.setAlignment(Pos.CENTER);

        final HBox windowHBox3 = new HBox(showCoverGuidelines, showAllGuidelines);
        windowHBox3.setPadding(new Insets(0, 8, 8, 0));
        windowHBox3.setAlignment(Pos.CENTER);

        final HBox windowHBox4 = new HBox(8, resetBtn, covRectsColorBox);
        //final HBox windowHBox4 = new HBox(8, resetBtn, covRectsColorBox, checkCoverBtn);//george aug28,2025 this hides check cover
        windowHBox4.setPadding(new Insets(0, 8, 8, 0));
        windowHBox4.setAlignment(Pos.CENTER);

        final Label mouseCoordinatesLabel = new Label("Mouse Coordinates");
        mouseCoordinatesLabel.setPadding(new Insets(0, 8, 8, 8));

        final VBox leftVBox = new VBox(8, windowHBox1, windowHBox2, windowHBox3, windowHBox4, zoomHBox, clickActionHBox,
                backForthHBox, mouseCoordinatesLabel, textXHBox, textYHBox, textXLockHBox,
                textYLockHBox, codeSequencesGPane, checkOneWrap);
        leftVBox.setPadding(new Insets(0, 10, 10, 10));

        textXHBox.getChildren().addAll(textXLabel, textXField, textYLabel, textYField);
        textXHBox.setSpacing(10);
        textXHBox.setAlignment(Pos.CENTER);

        textXLockHBox.getChildren().addAll(textXLockLabel, textXLockField, textYLockLabel, textYLockField);
        textXLockHBox.setSpacing(10);
        textXLockHBox.setAlignment(Pos.CENTER);

        // There are sort of two layers. There are all the gui elements that the
        // user interacts with. However, you can't directly use these when programming
        // So, you have variables behind the scene that represent the state of the gui.
        // Whenever the gui changes, these variables are updated automatically. Note
        // that all these changes must occur in one thread, since gui elements can
        // only be modified within the application thread

        codeSequencesGPane.setPadding(new Insets(0, 10, 0, 5));
        codeSequencesGPane.setBackground(new Background(new BackgroundFill(Color.WHITE, CornerRadii.EMPTY, Insets.EMPTY)));
        codeSequencesGPane.setHgap(10);
        codeSequencesGPane.setVgap(10);
        codeSequencesGPane.setPrefHeight(27);

        final HBox bpane = new HBox(10, leftVBox, imageStack);
        bpane.setAlignment(Pos.CENTER);

        // reflect
        final Affine reflectTransform = new Affine();
        reflectTransform.setMyy(-1);
        reflectTransform.setTy(imageStack.getBoundsInLocal().getHeight());
        imageStack.getTransforms().add(reflectTransform);

        // Scene
        final Scene scene = new Scene(bpane);

        // Stage
        mainWindow.setTitle(windowTitle);
        mainWindow.setOnCloseRequest(event -> {
            // close all the windows
            Platform.exit();
        });
        mainWindow.setScene(scene);
    }

    // Do initial rendering
    public void start() {
        renderRegions(guideLinesImageView);
        mainWindow.show();
    }
    private File getDir(String cover){
        File dir;
        if (cover.equals("100-105")){
            dir = new File("coversfolder/105cover/");
            
        } else if (cover.equals("90-100")) {
            dir = new File("coversfolder/90-100cover/");
            
        } else if (cover.equals("G")) {
            dir = new File("coversfolder/Gcover/");
            
        } else if (cover.equals("10-12")) {
            dir = new File("coversfolder/10-12cover/");
            
        } else if (cover.equals("112-112.4")) {
            dir = new File("coversfolder/112.4cover/");
            
        } else if (cover.equals("Tas3")) {
            dir = new File("coversfolder/Tas3cover/");//62-62.6-5-6
            
        } else if (cover.equals("Tas4")) {
            dir = new File("coversfolder/Tas4cover/");//25-25.5-8-9
            
        } else if (cover.equals("Tas5")) {
            dir = new File("coversfolder/Tas5cover/");//24-24.5-7-8
            
        } else if (cover.equals("Tas6")) {
            dir = new File("coversfolder/Tas6cover/");//27-27.5-7-8
            
        } else if (cover.equals("Tas7")) {
            dir = new File("coversfolder/Tas7cover/");//28-28.5-5-6
            
        } else if (cover.equals("Tas8")) {
            dir = new File("coversfolder/Tas8cover/");//28.5-29-5-6
            
        } else if (cover.equals("Tas9")) {
            dir = new File("coversfolder/Tas9cover/");//29.5-30-5-6
            
        } else if (cover.equals("Tas10")) {
            dir = new File("coversfolder/Tas10cover/");//56-57-5-6
            
        } else if (cover.equals("Tas11")) {
            dir = new File("coversfolder/Tas11cover/");//61-62-5-6
            
        } else if (cover.equals("Tas12")) {
            dir = new File("coversfolder/Tas12cover/");//62-62.6-5-6
            
            
        } else if (cover.equals("nick1")) {
            dir = new File("coversfolder/nick1cover/");//24.5-25.0-7-8
            
        } else if (cover.equals("nick2")) {
            dir = new File("coversfolder/nick2cover/");//26.0-26.5-7-8
            
        } else if (cover.equals("nick3")) {
            dir = new File("coversfolder/nick3cover/");//25.5-26-8-9
            
        } else if (cover.equals("nick4")) {
            dir = new File("coversfolder/nick4cover/");//27-27.5-5-6
            
        } else if (cover.equals("nick5")) {
            dir = new File("coversfolder/nick5cover/");//29-29.5-5-6
            
        } else if (cover.equals("nick6")) {
            dir = new File("coversfolder/nick6cover/");//39-40-5-6
            
        } else if (cover.equals("Mish1")) {
            dir = new File("coversfolder/Mish1cover/");//30-31-5-6
            
        } else if (cover.equals("Mish2")) {
            dir = new File("coversfolder/Mish2cover/");//31-32-5-6
            
        } else if (cover.equals("Mish3")) {
            dir = new File("coversfolder/Mish3cover/");//33-34-5-6
            
        } else if (cover.equals("Mish4")) {
            dir = new File("coversfolder/Mish4cover/");//28-28.5-6-7
  
            
        } else if (cover.equals("Max")) {
            dir = new File("coversfolder/Maxcover/");//26.5-27-7-8
            
        } else if (cover.equals("Huan")) {
            dir = new File("coversfolder/Huancover/");//27.5-28-7-8
            
        } else if (cover.equals("Angad")) {
            dir = new File("coversfolder/Angadcover/");//23.5-24-8-9


        } else if (cover.equals("105-110")) {
            dir = new File("coversfolder/110cover/");

        } else if (cover.equals("110-112")) {
            dir = new File("coversfolder/112cover/");

        } else if (cover.equals("112-112.1")) {
            dir = new File("coversfolder/112_1cover/");

        } else if (cover.equals("112.1-112.2")) {
            dir = new File("coversfolder/112_2cover/");

        } else if (cover.equals("112.2-112.3")) {
            dir = new File("coversfolder/112_3cover/");

        } else if (cover.equals("112.3-112.4")) {//george
            dir = new File("coversfolder/112_4cover/");

        } else if (cover.equals("112.3-112.4A")) {//george
            dir = new File("coversfolder/112_4coverA/");

        } else if (cover.equals("112.3-112.4B")) {//george
            dir = new File("coversfolder/112_4coverB/");

        } else if (cover.equals("112.3-112.4C")) {//george
            dir = new File("coversfolder/112_4coverC/");

        } else if (cover.equals("112.3-112.4D")) {//george
            dir = new File("coversfolder/112_4coverD/");

        } else if (cover.equals("112.3-112.4E")) {//george
            dir = new File("coversfolder/112_4coverE/");

        } else if (cover.equals("112.3-112.4F")) {//george
            dir = new File("coversfolder/112_4coverF/");

        } else if (cover.equals("112.3-112.4G")) {//george
            dir = new File("coversfolder/112_4coverG/");

        } else if (cover.equals("112.3-112.4H")) {//george
            dir = new File("coversfolder/112_4coverH/");

        } else if (cover.equals("112.3-112.4I")) {//george
            dir = new File("coversfolder/112_4coverI/");

        } else if (cover.equals("112.3-112.4J")) {//george
            dir = new File("coversfolder/112_4coverJ/");

        } else if (cover.equals("112.3-112.4K")) {//george
            dir = new File("coversfolder/112_4coverK/");

        } else if (cover.equals("112.3-112.4L")) {//george
            dir = new File("coversfolder/112_4coverL/");

        } else if (cover.equals("12-14")) {//george
            dir = new File("coversfolder/12-14cover/");

        } else if (cover.equals("14-15")) {//george
            dir = new File("coversfolder/14-15cover/");

        } else if (cover.equals("15-17")) {//george
            dir = new File("coversfolder/15-17cover/");

        } else if (cover.equals("17-22.4988")) {//george
            dir = new File("coversfolder/17-22.4988cover/");

        } else if (cover.equals("22.4988-33.8")) {//george
            dir = new File("coversfolder/22.4988-33.8cover/");

        } else if (cover.equals("A24-56.6")) {//george
            dir = new File("coversfolder/A-11-12cover/");

        } else if (cover.equals("A19-24")) {//george
            dir = new File("coversfolder/B-11-12cover/");

        } else if (cover.equals("A16.225-19")) {//george
            dir = new File("coversfolder/C-11-12cover/");

        } else if (cover.equals("A14-16.225")) {//george
            dir = new File("coversfolder/D-11-12cover/");

        } else if (cover.equals("A13.75-14")) {//george
            dir = new File("coversfolder/E-11-12cover/");

        } else if (cover.equals("A13.5-13.75")) {//george
            dir = new File("coversfolder/F-11-12cover/");

        } else if (cover.equals("A12-13.5")) {//george
            dir = new File("coversfolder/G-11-12cover/");

        } else if (cover.equals("A11-12")) {//george
            dir = new File("coversfolder/H-11-12cover/");



        } else if (cover.equals("B24-58")) {//george 2022
            dir = new File("coversfolder/A-10-11cover/");

        } else if (cover.equals("B19.9-24")) {//george 2022
            dir = new File("coversfolder/B-10-11cover/");//this one has the golden square

        } else if (cover.equals("B18.65-19.9")) {//george 2022
            dir = new File("coversfolder/C-10-11cover/");

        } else if (cover.equals("B16.33-18.65")) {//george 2022
            dir = new File("coversfolder/D-10-11cover/");

        } else if (cover.equals("B16.1-16.33")) {//george 2022
            dir = new File("coversfolder/E-10-11cover/");

        } else if (cover.equals("B15.9-16.1")) {//george 2022
            dir = new File("coversfolder/F-10-11cover/");

        } else if (cover.equals("B15.7-15.9")) {//george 2022
            dir = new File("coversfolder/G-10-11cover/");

        } else if (cover.equals("B15.5-15.7")) {//george 2022
            dir = new File("coversfolder/H-10-11cover/");

        } else if (cover.equals("B15.35-15.5")) {//george 2022
            dir = new File("coversfolder/I-10-11cover/");

        } else if (cover.equals("B14.5-15.35")) {//george 2022
            dir = new File("coversfolder/J-10-11cover/");

        } else if (cover.equals("B13.99-14.5")) {//george 2022
            dir = new File("coversfolder/K-10-11cover/");

        } else if (cover.equals("B13.7-13.99")) {//george 2022
            dir = new File("coversfolder/L-10-11cover/");

        } else if (cover.equals("B13.6-13.7")) {//george 2022
            dir = new File("coversfolder/M-10-11cover/");

        } else if (cover.equals("B13.5-13.6")) {//george 2022
            dir = new File("coversfolder/N-10-11cover/");

        } else if (cover.equals("B13.4-13.5")) {//george 2022
            dir = new File("coversfolder/O-10-11cover/");

        } else if (cover.equals("B13.35-13.4")) {//george 2022
            dir = new File("coversfolder/P-10-11cover/");

        } else if (cover.equals("B13.3-13.35")) {//george 2022
            dir = new File("coversfolder/Q-10-11cover/");

        } else if (cover.equals("B13.2-13.3")) {//george 2022
            dir = new File("coversfolder/R-10-11cover/");

        } else if (cover.equals("B13.125-13.2")) {//george 2022
            dir = new File("coversfolder/S-10-11cover/");

        } else if (cover.equals("B12.6-13.125")) {//george 2022
            dir = new File("coversfolder/T-10-11cover/");

        } else if (cover.equals("B12.5-12.6")) {//george 2022
            dir = new File("coversfolder/U-10-11cover/");

        } else if (cover.equals("B12.48-12.5")) {//george 2022
            dir = new File("coversfolder/V-10-11cover/");

        } else if (cover.equals("B12.12-12.48")) {//george 2022
            dir = new File("coversfolder/W-10-11cover/");

        } else if (cover.equals("B12.05-12.12s")) {//george 2022
            dir = new File("coversfolder/X-10-11cover/");

        } else if (cover.equals("B11.915-12.05s")) {//george 2022
            dir = new File("coversfolder/XX-10-11cover/");

        } else if (cover.equals("B11.815-11.915s")) {//george 2022
            dir = new File("coversfolder/XXX-10-11cover/");

        } else if (cover.equals("B11.663-11.815s")) {//george 2022
            dir = new File("coversfolder/XXXX-10-11cover/");

        } else if (cover.equals("B11.565-11.663s")) {//george 2022
            dir = new File("coversfolder/XXXXX-10-11cover/");

        } else if (cover.equals("B11.52-11.565s")) {//george 2022
            dir = new File("coversfolder/XXXXXX-10-11cover/");

        } else if (cover.equals("B11.47-11.52s")) {//george 2022
            dir = new File("coversfolder/XXXXXXX-10-11cover/");

        } else if (cover.equals("B11.335-11.47s")) {//george 2022
            dir = new File("coversfolder/XXXXXXXX-10-11cover/");

        } else if (cover.equals("B11.31-11.335")) {//george 2022
            dir = new File("coversfolder/Y-10-11cover/");

        } else if (cover.equals("B11.3-11.31")) {//george 2022
            dir = new File("coversfolder/Z-10-11cover/");

        } else if (cover.equals("B11.28-11.3")) {//george 2022
            dir = new File("coversfolder/Z2-10-11cover/");

        } else if (cover.equals("B11.26-11.28")) {//george 2022
            dir = new File("coversfolder/Z3-10-11cover/");

        } else if (cover.equals("B10.7-11.26")) {//george 2022
            dir = new File("coversfolder/Z4-10-11cover/");

        } else if (cover.equals("B10.65-10.7")) {//george 2022
            dir = new File("coversfolder/Z5-10-11cover/");

        } else if (cover.equals("B10.6-10.65")) {//george 2022
            dir = new File("coversfolder/Z6-10-11cover/");

        } else if (cover.equals("B10.5-10.6")) {//george 2022
            dir = new File("coversfolder/Z7-10-11cover/");

        } else if (cover.equals("B10.3-10.5")) {//george 2022
            dir = new File("coversfolder/Z8-10-11cover/");

        } else if (cover.equals("B10.2-10.3")) {//george 2022
            dir = new File("coversfolder/Z9-10-11cover/");

        } else if (cover.equals("6-10")) {//george 2023
            dir = new File("coversfolder/6-10cover/");
            
        } else if (cover.equals("7-10")) {//george 2024
            dir = new File("coversfolder/7-10cover/");

        } else if (cover.equals("90-100")) {//george 2023
            dir = new File("coversfolder/90-100cover/");
            
        } else if (cover.equals("G1")) {//george oct27,2025 .600-diagonal
            dir = new File("coversfolder/G1cover/");
            
        } else if (cover.equals("G2")) {//george oct28,2025 .500-.600
            dir = new File("coversfolder/G2cover/");
            
        } else if (cover.equals("G3")) {//george oct28,2025 .420-.500
            dir = new File("coversfolder/G3cover/");
            
        } else if (cover.equals("G4")) {//george oct29,2025 .380-.420
            dir = new File("coversfolder/G4cover/");
            
        } else if (cover.equals("G5")) {//george oct29,2025 .350-.380
            dir = new File("coversfolder/G5cover/");
            
        } else if (cover.equals("G6")) {//george oct29,2025 .330-.350
            dir = new File("coversfolder/G6cover/");
            
        } else if (cover.equals("G7")) {//george oct29,2025 .310-.330
            dir = new File("coversfolder/G7cover/");
            
        } else if (cover.equals("G8")) {//george oct29,2025 .300-.310
            dir = new File("coversfolder/G8cover/");
            
        } else if (cover.equals("G9")) {//george oct29,2025 .290-.300
            dir = new File("coversfolder/G9cover/");
            
        } else if (cover.equals("G10")) {//george oct29,2025 .280-.290
            dir = new File("coversfolder/G10cover/");
            
        } else if (cover.equals("G11")) {//george oct29,2025 .270-.280
            dir = new File("coversfolder/G11cover/");
            
        } else if (cover.equals("G12")) {//george oct29,2025 .260-.270
            dir = new File("coversfolder/G12cover/");
            
        } else if (cover.equals("G13")) {//george oct29,2025 .255-.260
            dir = new File("coversfolder/G13cover/");
            
        } else if (cover.equals("G14")) {//george oct29,2025 .250-.255
            dir = new File("coversfolder/G14cover/");
            
        } else if (cover.equals("G15")) {//george oct29,2025 .245-.250
            dir = new File("coversfolder/G15cover/");
            
        } else if (cover.equals("G16")) {//george oct29,2025 .240-.245
            dir = new File("coversfolder/G16cover/");
            
        } else if (cover.equals("G17")) {//george oct29,2025 .235-.240
            dir = new File("coversfolder/G17cover/");
            
        } else if (cover.equals("G18")) {//george oct29,2025 .230-.235
            dir = new File("coversfolder/G18cover/");
            
        } else if (cover.equals("G19")) {//george oct29,2025 .226-.230
            dir = new File("coversfolder/G19cover/");
            
        } else if (cover.equals("G20")) {//george oct29,2025 .224-.226
            dir = new File("coversfolder/G20cover/");
            
        } else if (cover.equals("G21")) {//george oct29,2025 .222-.224
            dir = new File("coversfolder/G21cover/");
            
        } else if (cover.equals("G22")) {//george oct29,2025 .217-.222
            dir = new File("coversfolder/G22cover/");
            
        } else if (cover.equals("G23")) {//george oct29,2025 .214-.217
            dir = new File("coversfolder/G23cover/");
            
        } else if (cover.equals("G24")) {//george oct29,2025 .210-.214
            dir = new File("coversfolder/G24cover/");
            
        } else if (cover.equals("G25")) {//george oct29,2025 .205-.210
            dir = new File("coversfolder/G25cover/");
            
        } else if (cover.equals("G26")) {//george oct29,2025 .202-.205
            dir = new File("coversfolder/G26cover/");
            
        } else if (cover.equals("G27")) {//george oct29,2025 .199-.202
            dir = new File("coversfolder/G27cover/");
            
        } else if (cover.equals("G28")) {//george oct29,2025 .196-.199
            dir = new File("coversfolder/G28cover/");
            
        } else if (cover.equals("G29")) {//george oct29,2025 .194-.196
            dir = new File("coversfolder/G29cover/");
            
        } else if (cover.equals("G30")) {//george oct29,2025 .192-.194
            dir = new File("coversfolder/G30cover/");
            
        } else if (cover.equals("G31")) {//george oct29,2025 .190-.192
            dir = new File("coversfolder/G31cover/");
            
        } else if (cover.equals("G32")) {//george oct29,2025 .186-.190
            dir = new File("coversfolder/G32cover/");
            
        } else if (cover.equals("G33")) {//george oct29,2025 .182-.186
            dir = new File("coversfolder/G33cover/");
            
        } else if (cover.equals("G34")) {//george oct29,2025 .178-.182
            dir = new File("coversfolder/G34cover/");
            
        } else if (cover.equals("G35")) {//george oct29,2025 .174-.178
            dir = new File("coversfolder/G35cover/");
            
        } else if (cover.equals("G36")) {//george oct29,2025 .170-.174
            dir = new File("coversfolder/G36cover/");
            
        } else if (cover.equals("G37")) {//george oct29,2025 .166-.170
            dir = new File("coversfolder/G37cover/");
            
        } else if (cover.equals("G38")) {//george oct29,2025 .165-.166
            dir = new File("coversfolder/G38cover/");
            
        } else if (cover.equals("G39")) {//george oct29,2025 .164-.165
            dir = new File("coversfolder/G39cover/");
            
        } else if (cover.equals("G40")) {//george oct29,2025 .162-.164
            dir = new File("coversfolder/G40cover/");
            
        } else if (cover.equals("G41")) {//george oct29,2025 .160-.162
            dir = new File("coversfolder/G41cover/");
            
        } else if (cover.equals("G42")) {//george oct29,2025 .157-.160
            dir = new File("coversfolder/G42cover/");
            
        } else if (cover.equals("G43")) {//george oct29,2025 .153-.157
            dir = new File("coversfolder/G43cover/");
            
        } else if (cover.equals("G44")) {//george oct29,2025 .150-.153
            dir = new File("coversfolder/G44cover/");
            
        } else if (cover.equals("G45")) {//george oct29,2025 .147-.150
            dir = new File("coversfolder/G45cover/");
            
        } else if (cover.equals("G46")) {//george oct29,2025 .144-.147
            dir = new File("coversfolder/G46cover/");
            
        } else if (cover.equals("G47")) {//george oct29,2025 .141-.144
            dir = new File("coversfolder/G47cover/");
            
        } else if (cover.equals("G48")) {//george oct29,2025 .138-.141
            dir = new File("coversfolder/G48cover/");
            
        } else if (cover.equals("G49")) {//george oct29,2025 .136-.138
            dir = new File("coversfolder/G49cover/");
            
        } else if (cover.equals("G50")) {//george oct29,2025 .132-.136
            dir = new File("coversfolder/G50cover/");
            
        } else if (cover.equals("G51")) {//george oct29,2025 .1305-.132
            dir = new File("coversfolder/G51cover/");
            
        } else if (cover.equals("G52")) {//george oct29,2025 .130-.1305
            dir = new File("coversfolder/G52cover/");
            
        } else if (cover.equals("G53")) {//george oct29,2025 .128-.130
            dir = new File("coversfolder/G53cover/");
            
        } else if (cover.equals("G54")) {//george oct29,2025 .1275-.128
            dir = new File("coversfolder/G54cover/");
            
        } else if (cover.equals("G55")) {//george oct29,2025 .1265-.1275
            dir = new File("coversfolder/G55cover/");
            
        } else if (cover.equals("G56")) {//george oct29,2025 .126-.1265
            dir = new File("coversfolder/G56cover/");
            
        } else if (cover.equals("G57")) {//george oct29,2025 .1245-.126
            dir = new File("coversfolder/G57cover/");

        } else if (cover.equals("G58")) {//george oct29,2025 .124-.1245
            dir = new File("coversfolder/G58cover/"); 
            
        } else if (cover.equals("G59")) {//george oct29,2025 .1205-.124
            dir = new File("coversfolder/G59cover/");
            
        } else if (cover.equals("G60")) {//george oct29,2025 .120-.1205
            dir = new File("coversfolder/G60cover/");
            
        } else if (cover.equals("G61")) {//george oct29,2025 .117-.120
            dir = new File("coversfolder/G61cover/");
            
        } else if (cover.equals("G62")) {//george oct29,2025 .114-.117
            dir = new File("coversfolder/G62cover/");
            
        } else if (cover.equals("G63")) {//george oct29,2025 .111-.114
            dir = new File("coversfolder/G63cover/");
            
        } else if (cover.equals("G64")) {//george oct29,2025 .108-.111
            dir = new File("coversfolder/G64cover/");
            
        } else if (cover.equals("G65")) {//george oct29,2025 .107-.108
            dir = new File("coversfolder/G65cover/");
            
        } else if (cover.equals("G66")) {//george oct29,2025 .1066-.107
            dir = new File("coversfolder/G66cover/");
            
        } else if (cover.equals("G67")) {//george oct29,2025 .1065-.1066
            dir = new File("coversfolder/G67cover/");
            
        } else if (cover.equals("G68")) {//george oct29,2025 .1062-.1065
            dir = new File("coversfolder/G68cover/");
            
        } else if (cover.equals("G69")) {//george oct29,2025 .106-.1062
            dir = new File("coversfolder/G69cover/");
            
        } else if (cover.equals("G70")) {//george oct29,2025 .105-.106
            dir = new File("coversfolder/G70cover/");
            
        } else if (cover.equals("G71")) {//george oct29,2025 .103-.105
            dir = new File("coversfolder/G71cover/");
            
        } else if (cover.equals("G72")) {//george oct29,2025 .100-.103
            dir = new File("coversfolder/G72cover/");
            
        } else if (cover.equals("G73")) {//george oct29,2025 .095-.100
            dir = new File("coversfolder/G73cover/");
            
        } else if (cover.equals("G74")) {//george oct29,2025 .090-.095
            dir = new File("coversfolder/G74cover/");
            
        } else if (cover.equals("G75")) {//george oct29,2025 .08-.09
            dir = new File("coversfolder/G75cover/");
            
        } else if (cover.equals("G76")) {//george oct29,2025 .05-.08
            dir = new File("coversfolder/G76cover/");
            
        } else if (cover.equals("GT1")) {//george nov 10,2025 .50-diagonal
            dir = new File("coversfolder/GT1cover/");
            
        } else if (cover.equals("GT2")) {//george nov 10,2025 .425-.50
            dir = new File("coversfolder/GT2cover/");
            
        } else if (cover.equals("GT3")) {//george dec 21,2025 .390-.425
            dir = new File("coversfolder/GT3cover/");
            
        } else if (cover.equals("GT4")) {//george dec 21,2025 .355-.390
            dir = new File("coversfolder/GT4cover/");
            
        } else if (cover.equals("GT5")) {//george dec 21,2025 .330-.355
            dir = new File("coversfolder/GT5cover/");
            
        } else if (cover.equals("GT6")) {//george dec 21,2025 .305-.330
            dir = new File("coversfolder/GT6cover/");
            
        } else if (cover.equals("GT7")) {//george dec 21,2025 .290-.305
            dir = new File("coversfolder/GT7cover/");
            
        } else if (cover.equals("GT8")) {//george feb22,2026 .280-.290
            dir = new File("coversfolder/GT8cover/");
            
        } else if (cover.equals("GT9")) {//george feb22,2026 .275-.280
            dir = new File("coversfolder/GT9cover/");
            
        } else if (cover.equals("GT10")) {//george feb22,2026 .270-.275
            dir = new File("coversfolder/GT10cover/");
            
        } else if (cover.equals("GT11")) {//george feb22,2026 .265-.270
            dir = new File("coversfolder/GT11cover/");
            
        } else if (cover.equals("GT12")) {//george feb22,2026 .250-.265
            dir = new File("coversfolder/GT12cover/");
            
        } else if (cover.equals("GT13")) {//george feb22,2026 .240-.250
            dir = new File("coversfolder/GT13cover/");
            
        } else if (cover.equals("GT14")) {//george feb22,2026 .235-.240
            dir = new File("coversfolder/GT14cover/");
            
        } else if (cover.equals("GT15")) {//george feb22,2026 .230-.235
            dir = new File("coversfolder/GT15cover/");
            
        } else if (cover.equals("GT16")) {//george feb22,2026 .225-.230
            dir = new File("coversfolder/GT16cover/");
            
        } else if (cover.equals("GT17")) {//george feb22,2026 .220-.225
            dir = new File("coversfolder/GT17cover/");
            
        } else if (cover.equals("GT18")) {//george feb22,2026 .215-.220
            dir = new File("coversfolder/GT18cover/");
            
        } else if (cover.equals("GT19")) {//george feb22,2026 .210-.215
            dir = new File("coversfolder/GT19cover/");
            
        } else if (cover.equals("GT20")) {//george feb22,2026 .206-.210
            dir = new File("coversfolder/GT20cover/");
            
        } else if (cover.equals("GT21")) {//george feb22,2026 .203-.206
            dir = new File("coversfolder/GT21cover/");
            
        } else if (cover.equals("GT22")) {//george feb22,2026 .202-.203
            dir = new File("coversfolder/GT22cover/");
            
        } else if (cover.equals("GT23")) {//george feb22,2026 .201-.202
            dir = new File("coversfolder/GT23cover/");
            
        } else if (cover.equals("GT24")) {//george feb22,2026 .198-.201
            dir = new File("coversfolder/GT24cover/");
            
        } else if (cover.equals("GT25")) {//george feb22,2026 .196-.198
            dir = new File("coversfolder/GT25cover/");
            
        } else if (cover.equals("GT26")) {//george feb22,2026 .193-.196
            dir = new File("coversfolder/GT26cover/");
            
        } else if (cover.equals("GT27")) {//george feb22,2026 .191-.193
            dir = new File("coversfolder/GT27cover/");
            
        } else if (cover.equals("GT28")) {//george feb22,2026 .189-.191
            dir = new File("coversfolder/GT28cover/");
            
        } else if (cover.equals("GT29")) {//george feb22,2026 .187-.189
            dir = new File("coversfolder/GT29cover/");
            
        } else if (cover.equals("GT30")) {//george feb22,2026 .185-.187
            dir = new File("coversfolder/GT30cover/");
            
        } else if (cover.equals("GT31")) {//george feb22,2026 .182-.185
            dir = new File("coversfolder/GT31cover/");
            
        } else if (cover.equals("GT32")) {//george feb22,2026 .180-.182
            dir = new File("coversfolder/GT32cover/");
            
        } else if (cover.equals("GT33")) {//george feb22,2026 .178-.180
            dir = new File("coversfolder/GT33cover/");
            
        } else if (cover.equals("GT34")) {//george feb22,2026 .176-.178
            dir = new File("coversfolder/GT34cover/");
            
        } else if (cover.equals("GT35")) {//george feb22,2026 .174-.176
            dir = new File("coversfolder/GT35cover/");
            
        } else if (cover.equals("Geo1")) {//george oct30,2025
            dir = new File("coversfolder/ZZ-Geo1cover/");
            
        } else if (cover.equals("Mai1")) {//george oct30,2025
            dir = new File("coversfolder/ZZ-Mai1cover/");
            
        } else if (cover.equals("Mai2")) {//george oct30,2025
            dir = new File("coversfolder/ZZ-Mai2cover/");
            
        } else if (cover.equals("Sur1")) {//george oct30,2025
            dir = new File("coversfolder/ZZ-Sur1cover/");
            
        } else if (cover.equals("Sur2")) {//george oct30,2025
            dir = new File("coversfolder/ZZ-Sur2cover/");
            
        } else if (cover.equals("Sur3")) {//george oct30,2025
            dir = new File("coversfolder/ZZ-Sur3cover/");
            
        } else if (cover.equals("Tas1")) {//george oct30,2025
            dir = new File("coversfolder/ZZ-Tas1cover/");
            
        } else if (cover.equals("Tas2")) {//george oct30,2025
            dir = new File("coversfolder/ZZ-Tas2cover/");
            
        } else if (cover.equals("B10-10.2")) {//george 2022
            dir = new File("coversfolder/Z10-10-11cover/");

        } else {
            final Alert alert = new Alert(AlertType.ERROR);
            alert.setTitle("Load Cover");
            alert.setHeaderText("Error in Load");
            alert.setContentText("Cover folder or files might be missing");
            alert.showAndWait();
            return null;
        }
        return dir;
    }
    @SuppressWarnings("unchecked")
    private void loadCoverAction(File dir) {
            // Source precedence, smallest/fastest first:
            //   cover.pack  (single bundled file — text files can be deleted)
            //   cover.cbin  (compact binary cover, text files still present)
            //   cover.txt   (legacy plain text)
            ConvexPolygon polygon;
            List<CodePair> stables;
            List<TriplePair> triples;
            CoverData coverData;
            File pack = new File(dir, "cover.pack");
            File cbin = new File(dir, "cover.cbin");
            try {
                if (pack.exists()) {
                    CoverPack cp = CoverPack.readFile(pack.getPath());
                    polygon = Cover.parsePolygon(cp.text("polygon.txt").trim());
                    stables = Cover.parseStables(cp.text("stables.txt").trim());
                    triples = Cover.parseTriples(cp.text("triples.txt").trim());
                    coverData = CoverCodec.readBinary(
                        new ByteArrayInputStream(cp.coverCbin()), stables, triples);
                } else {
                    polygon = Cover.parsePolygon(Utils.readFromFile(dir + "/polygon.txt").trim());
                    stables = Cover.parseStables(Utils.readFromFile(dir + "/stables.txt").trim());
                    triples = Cover.parseTriples(Utils.readFromFile(dir + "/triples.txt").trim());
                    if (cbin.exists()) {
                        coverData = CoverCodec.readBinaryFile(cbin.getPath(), stables, triples);
                    } else {
                        coverData = loadFromText(new File(dir, "cover.txt"), stables, triples);
                    }
                }
            } catch (IOException ex) {
                throw new RuntimeException("failed to load cover from " + dir, ex);
            }

            // We can only load one cover at a time
            if (!regionsStackList.contains(coversBox.getValue())) {
                regionsStackList.add(coversBox.getValue());
                HashTriple tmp = new HashTriple(coverData);
                coverRectList.add(tmp);
                coverArea = Optional.of(polygon);
                renderRegions(guideLinesImageView);
            }
//        }
    }

    /**
     * Loads a cover from plain {@code cover.txt}. Reading the file into a String costs about five
     * times its size in heap and cannot work at all past 2 GB (Java arrays are int-indexed), so
     * anything large is first streamed into a scratch cbin and loaded from that instead. Same
     * CoverData either way; the scratch file is deleted before returning.
     */
    private static CoverData loadFromText(final File coverTxt, final List<CodePair> stables,
                                          final List<TriplePair> triples) throws IOException {
        if (coverTxt.length() <= (32L << 20)) {
            return CoverCodec.parseText(
                Utils.readFromFile(coverTxt.getPath()).trim(), stables, triples);
        }
        // Prefer the cover's own folder (same filesystem, guaranteed room for a file this size),
        // but fall back to the system temp dir when that folder is not writable -- covers opened
        // from a read-only location, e.g. inside an .app bundle.
        final File coverDir = coverTxt.getAbsoluteFile().getParentFile();
        File scratch;
        try {
            scratch = File.createTempFile("cover-load", ".cbin", coverDir);
        } catch (final IOException notWritable) {
            scratch = File.createTempFile("cover-load", ".cbin");
        }
        try {
            CoverStream.packTextToCbin(coverTxt, scratch, stables.size(), triples.size());
            return CoverCodec.readBinaryFile(scratch.getPath(), stables, triples);
        } finally {
            scratch.delete();
        }
    }

    private void zoomAction() {
        // so between 0 and pi, and min < max

        double xMin;
		double xMax;
		double yMin;
		double yMax;
		try {
			xMin = Math.toRadians(Double.parseDouble(xMinTextField.getText()));
			xMax = Math.toRadians(Double.parseDouble(xMaxTextField.getText()));
			yMin = Math.toRadians(Double.parseDouble(yMinTextField.getText()));
			yMax = Math.toRadians(Double.parseDouble(yMaxTextField.getText()));
		} catch (NumberFormatException e) {
			final Alert error = new Alert(AlertType.ERROR);
			error.setTitle("Error");
			error.setHeaderText("Number Format Error");
			error.setContentText("Please check the zoom fields' input");
			error.showAndWait();
			return;
		}

        if (0 < xMin && xMin < xMax && xMax < Math.PI
         && 0 < yMin && yMin < yMax && yMax < Math.PI) {

        	if ((xMin == xMax) && (yMin == yMax)) {
                final double size = map.pixelSize();
                map.setTranslateY(yMin - (SIZE / 2) * size);

            } else {
                final double width = xMax - xMin;
                final double height = yMax - yMin;

                final double largest = height > width ? height : width;

                // a scale of 1 gives us a width of pi
                final double scale = Math.PI / largest;

                map.setScale(scale);

                final double size = map.pixelSize();
                map.setTranslateX((xMax + xMin) / 2 - (SIZE / 2) * size);
                map.setTranslateY((yMax + yMin) / 2 - (SIZE / 2) * size);
            }
            viewRectangleBF.add(map.getViewRectangle());
            renderRegions(guideLinesImageView);
        }
    }

    private void pan(final double initX, final double initY, final double finX, final double finY) {
        if (Math.abs(finX - initX) > 5 || Math.abs(finY - initY) > 5) {
            map.translateXBy(map.radianX(initX) - map.radianX(finX));
            map.translateYBy(map.radianY(initY) - map.radianY(finY));

            viewRectangleBF.add(map.getViewRectangle());

            renderRegions(guideLinesImageView);

        } else {
            click(initX, initY);
        }
    }

    private void click(final double pixelX, final double pixelY) {
        starClicked = false;
        multiCodePairs.clear();

        final double oldRadianX = map.radianX(pixelX + 0.5);
        final double oldRadianY = map.radianY(pixelY + 0.5);

        final double oldDegreeX = Math.toDegrees(oldRadianX);
        final double oldDegreeY = Math.toDegrees(oldRadianY);

        textXLockField.setText(Double.toString(oldDegreeX));
        textYLockField.setText(Double.toString(oldDegreeY));

        if (selectRdoBtn.isSelected()) {

            // now we get the current point
            final double rx = map.radianX(pixelX + 0.5);
            final double ry = map.radianY(pixelY + 0.5);

            final double radianX = rx;
            final double radianY = ry;

            // iterate over the onScreenSequences, and check which ones are positive


            // for the point. We want the codes on the right to be sorted from smallest
            // to largest, since that makes life much easier
            // map and filter would be really nice right now
            selectedStorages = new TreeSet<>(); //Reset selected storages
            Color color = Color.TRANSPARENT;
            selectedRect = null;
            checkOneWrap.getChildren().clear();

            currentCodePair = null;
            currentStorages.clear();
            currentBounds.clear();
            currentColor = color;

            if (infPatternArea.location(rx, ry) == Location.INSIDE) {
                currentColor = Color.web(covRectsColorBox.getText().toLowerCase().replaceAll("\\s", ""), 0.5);
                int n = 1;
                while (!(0 < 3 * Math.PI / 2 - (n + 2) * rx - 2 * ry
                        && 3 * Math.PI / 2 - (n + 2) * rx - 2 * ry < Math.PI / 2)) {
                    n += 1;
                }

                final MutableIntList codeList = IntArrayList.newListWith(1, 1, 2 * n + 1, 1, 2, 1, 2 * n + 1, 1, 1, 4 * n + 2);
                final MutableIntList unstList1 = IntArrayList.newListWith(1, 2, 1, 2 * n);
                final MutableIntList unstList2 = IntArrayList.newListWith(1, 2, 1, 2 * n + 2);

                final CodeSequence stabCode = new CodeSequence(codeList);
                final CodeSequence unstCode1 = new CodeSequence(unstList1);
                final CodeSequence unstCode2 = new CodeSequence(unstList2);

                final Storage stable = loadStorage(new CodePair(stabCode, new InitialAngles(XYZ.Z, XYZ.Y))).get();
                final Storage unst1 = loadStorage(new CodePair(unstCode1, new InitialAngles(XYZ.Z, XYZ.Y))).get();
                final Storage unst2 = loadStorage(new CodePair(unstCode2, new InitialAngles(XYZ.Z, XYZ.Y))).get();

                selectedStorages.add(unst1);
                selectedStorages.add(stable);
                selectedStorages.add(unst2);

            } else if (infPatternArea2.location(rx, ry) == Location.INSIDE) {
                color = Color.web(covRectsColorBox.getText().toLowerCase().replaceAll("\\s", ""), 0.5);
                int n = 0;
                double third_equation_1 = -Math.sin(rx+2*ry)- Math.sin(3*rx+2*ry) + Math.sin(3*rx+4*ry)+ Math.sin(5*rx+4*ry) ;
                double positive_sum_1 = 0;
                double negative_sum_1 = 0;
                double third_equation_2 = - Math.sin(rx+2*ry)- Math.sin(3*rx+2*ry);
                double positive_sum_2 = 0;
                double negative_sum_2 = 0;
                while
                ((
                        n%2==0  &&
                                !(
                                        Math.cos((5+2*n))*(rx+ry) > 0
                                        && -Math.cos((2*n+4)*rx + (2*n+2)*ry) - Math.cos((2*n+4)*(rx+ry)) + Math.cos((2*n+6)*rx + (2*n+4)*ry) > 0
                                        && third_equation_1 - Math.sin((2*n+7)*rx+(2*n+6)*ry) > 0
                                )
                ) || (
                        n%2!=0 &&
                                !(
                                        -Math.cos((2*n+5)*(rx+ry)) > 0
                                        && Math.cos((2*n+4)*rx + (2*n+2)*ry) + Math.cos((2*n+4)*(rx+ry)) - Math.cos((2*n+6)*rx+(2*n+4)*ry) > 0
                                        && third_equation_2 + Math.sin((2*n+7)*rx+(2*n+6)*ry)>0
                                )
                )
                ){
                    n += 1;
                    if (n%2==0) {
                        positive_sum_1 = Math.sin((2*n + 3) * rx + (2 * n + 4) * ry) + Math.sin((2 * n + 5) * rx + (2 * n + 4) * ry);
                        negative_sum_1 = Math.sin((2*n + 1) * rx + (2 * n + 2) * ry) + Math.sin((2 * n + 3) * rx + (2 * n + 2) * ry);
                        third_equation_1 += positive_sum_1 - negative_sum_1;
                    }
                    else {
                        positive_sum_2 = Math.sin((2 * n + 1) * rx + (2 * n + 2) * ry) + Math.sin((2 * n + 3) * rx + (2 * n + 2) * ry);
                        negative_sum_2 = Math.sin((2 * n + 3) * rx + (2 * n + 4) * ry) + Math.sin((2 * n + 5) * rx + (2 * n + 4) * ry);
                        third_equation_2 += positive_sum_2 - negative_sum_2;
                    }
                }
                MutableIntList codeList1;
                if (n%2==0) {
                    codeList1 = IntArrayList.newListWith(1, 2, 1, 1, 1, 1, 4, 1, 1, 1, 1, 2, 1, 4);
                    int[] firstIterList = new int[]{1, 1, 2, 1, 1, 4};
                    int[] secondIterList = new int[]{1, 1, 4, 1, 1, 2};
                    for (int i = 1; i <= 2 * n + 2; i++) {
                        codeList1.addAllAtIndex(4, secondIterList);
                    }
                    for (int i = 1; i <= 2 * n + 1; i++) {
                        codeList1.addAllAtIndex(0, firstIterList);
                    }
                }
                else {
                    codeList1 = IntArrayList.newListWith(1, 1, 1, 1, 2, 1, 4, 1, 2, 1, 1, 1, 1, 4);
                    int[] firstIterList = new int[]{1, 1, 2, 1, 1, 4};
                    int[] secondIterList = new int[]{4, 1, 1, 2, 1, 1};
                    for (int i = 1; i <= n; i++) {
                        codeList1.addAllAtIndex(7, firstIterList);
                    }
                    for (int i = 1; i <= n; i++) {
                        codeList1.addAllAtIndex(6, secondIterList);
                    }
                    for (int i = 1; i <= 2*n + 1; i++) {
                        codeList1.addAllAtIndex(0, firstIterList);
                    }
                }

                final CodeSequence stabCode1 = new CodeSequence(codeList1);
                final Storage stable1 = loadStorage(new CodePair(stabCode1, new InitialAngles(XYZ.Z, XYZ.Y))).get();
                selectedStorages.add(stable1);
                if  (Math.cos(ry)+ Math.cos(2*rx+ry) > 0) {
                    double first_equation = - Math.cos(4*rx-ry)- Math.cos(6*rx-ry)+ Math.cos(6*rx+ry);
                    double second_equation = Math.cos(2*ry)- Math.cos(2*rx-2*ry)+Math.cos(2*rx)+Math.cos(4*rx)-
                            Math.cos(4*rx+2*ry)+Math.cos(6*rx-2*ry);
                    double third_equation = - Math.cos(rx-2*ry)+ Math.cos(3*rx)+Math.cos(5*rx-2*ry);
                    if (
                            first_equation> 0
                                    && second_equation -Math.cos(6*rx)> 0
                                    && third_equation > 0
                                    && -Math.sin(2*rx+2*ry)-Math.sin(4*rx)-Math.sin(4*rx+2*ry)-Math.sin(6*rx) > 0
                    ) {
                        final MutableIntList codeList2 = IntArrayList.newListWith(1,3,2,2,1,1,3,2,4,2,3,1,1,2,2,3,1,2);
                        final CodeSequence stabCode2 = new CodeSequence(codeList2);
                        final Storage stable2 = loadStorage(new CodePair(stabCode2, new InitialAngles(XYZ.Z, XYZ.Y))).get();

                        selectedStorages.add(stable2);
                    }
                    else {
                        n=1;
                        first_equation += Math.cos(8*rx+ry) - Math.cos(8*rx+3*ry);
                        second_equation += - Math.cos(6*rx+2*ry) - Math.cos(8*rx) ;
                        third_equation += Math.cos(5*rx) - Math.cos(7*rx);
                        if (
                            Math.pow(-1,n+1) * Math.sin((2*n+6)*rx+2*n*ry) > 0 &&
                                    first_equation > 0 &&
                                    Math.sin(rx+2*ry)+ Math.sin(3*rx+2*ry)+ Math.sin(5*rx)+ Math.sin(7*rx)- Math.sin(7*rx+2*ry) > 0 &&
                                    second_equation + Math.cos(8*rx + 2*ry) > 0 &&
                                    third_equation > 0
                        ) {
                            final MutableIntList codeList2 = IntArrayList.newListWith(1,3,2,3,1,1,1,1,2,1,3,2,4,2,3,1,2,1,1,1,1,3,2,3,1,2);
                            final CodeSequence stabCode2 = new CodeSequence(codeList2);
                            final Storage stable2 = loadStorage(new CodePair(stabCode2, new InitialAngles(XYZ.Z, XYZ.X))).get();
                            selectedStorages.add(stable2);

                        }
                        else {
                            n=2;
                            first_equation += -Math.cos(10*rx+3*ry)+Math.cos(10*rx+5*ry);
                            second_equation += Math.cos(8*rx+4*ry)+ Math.cos(10*rx+2*ry) ;
                            third_equation += -Math.cos(7*rx+2*ry)+ Math.cos(9*rx+2*ry);
                            double fourth_equation = Math.cos(ry)+ Math.cos(2*rx+ry)-Math.cos(2*rx+3*ry)+Math.cos(4*rx-ry)-
                                    Math.cos(4*rx+3*ry)+ Math.cos(6*rx-ry)-Math.cos(6*rx+ry)-Math.cos(8*rx+ry)+Math.cos(8*rx+3*ry);

                            if (
                                    Math.pow(-1,n+1) * Math.sin((2*n+6)*rx+2*n*ry) > 0 &&
                                            first_equation > 0 &&
                                            Math.sin(rx+2*ry)+ Math.sin(3*rx+2*ry)+ Math.sin(5*rx)+ Math.sin(7*rx)- Math.sin(7*rx+2*ry) > 0 &&
                                            second_equation - Math.cos(10*rx+4*ry) > 0 &&
                                            third_equation > 0 &&
                                            fourth_equation > 0
                            ) {
                                final MutableIntList codeList2 = IntArrayList.newListWith(1,3,2,3,1,2,1,1,1,1,2,1,2,1,3,2,4,2,3,1,2,1,2,1,1,1,1,2,1,3,2,3,1,2);
                                final CodeSequence stabCode2 = new CodeSequence(codeList2);
                                final Storage stable2 = loadStorage(new CodePair(stabCode2, new InitialAngles(XYZ.Z, XYZ.Y))).get();
                                selectedStorages.add(stable2);

                            }
                            else {
                                n=3;
                                first_equation += Math.cos(12*rx+5*ry)-Math.cos(12*rx+7*ry);
                                fourth_equation += Math.cos(10*rx+3*ry)- Math.cos(10*rx+5*ry);
                                while (
                                        !(Math.pow(-1,n+1) * Math.sin((2*n+6)*rx+2*n*ry) > 0 &&
                                                first_equation > 0 &&
                                                fourth_equation > 0
                                )) {
                                    n += 1;
                                    first_equation += Math.pow(-1,n+1) * Math.cos((2*n + 6) * rx + (2*n-1) *ry) +
                                            Math.pow(-1,n) * Math.cos((2*n + 6) * rx + (2*n+1) *ry);
                                    fourth_equation += Math.pow(-1,n+1) * Math.cos((2*n + 4) * rx + (2*n-3) *ry) +
                                            Math.pow(-1,n) * Math.cos((2*n + 4) * rx + (2*n-1) *ry);

                                }
                                final MutableIntList codeList2 = IntArrayList.newListWith(1,3,2,3,1,1,1,1,2,1,3,2,4,2,3,1,2,1,1,1,1,3,2,3,1,2);
                                int[] firstIterList = new int[]{1,2};
                                int[] secondIterList = new int[]{2,1};
                                for (int i=1; i<=n-1; i++) {
                                    codeList2.addAllAtIndex(21, secondIterList);
                                }
                                for (int i=1; i<=n-1; i++) {
                                    codeList2.addAllAtIndex(16, secondIterList);
                                }
                                for (int i=1; i<=n-1; i++) {
                                    codeList2.addAllAtIndex(9, firstIterList);
                                }
                                for (int i=1; i<=n-1; i++) {
                                    codeList2.addAllAtIndex(4, firstIterList);
                                }
                                final CodeSequence stabCode2 = new CodeSequence(codeList2);
                                if (n%2!=0)  {
                                    final Storage stable2 = loadStorage(new CodePair(stabCode2, new InitialAngles(XYZ.Z, XYZ.Y))).get();
                                    selectedStorages.add(stable2);
                                }
                                else {
                                    final Storage stable2 = loadStorage(new CodePair(stabCode2, new InitialAngles(XYZ.Z, XYZ.X))).get();
                                    selectedStorages.add(stable2);
                                }




                            }


                        }



                    }

                }
            } else if (infPatternArea3.location(rx, ry) == Location.INSIDE) {
                color = Color.web(covRectsColorBox.getText().toLowerCase().replaceAll("\\s", ""), 0.5);
                final MutableIntList codeList1 = IntArrayList.newListWith(1,1,1,1,2,1,3,2,3,1,2,1,1,1,1,3,2,3,1,1,2,2,2,1,1,3,2,3);
                final CodeSequence stabCode1 = new CodeSequence(codeList1);
                final Storage stable1 = loadStorage(new CodePair(stabCode1, new InitialAngles(XYZ.Z, XYZ.Y))).get();
                selectedStorages.add(stable1);

                final MutableIntList codeList2 = IntArrayList.newListWith(1,1,2,4,2,1,1,3,3,1,2,1,3,3);
                final CodeSequence stabCode2 = new CodeSequence(codeList2);
                final Storage stable2 = loadStorage(new CodePair(stabCode2, new InitialAngles(XYZ.Z, XYZ.X))).get();
                selectedStorages.add(stable2);

                final MutableIntList codeList3 = IntArrayList.newListWith(1,1,1);
                final CodeSequence stabCode3 = new CodeSequence(codeList3);
                final Storage stable3 = loadStorage(new CodePair(stabCode3, new InitialAngles(XYZ.Z, XYZ.Y))).get();
                selectedStorages.add(stable3);

            } else if (infPatternArea4.location(rx, ry) == Location.INSIDE) {
                color = Color.web(covRectsColorBox.getText().toLowerCase().replaceAll("\\s", ""), 0.5);
                final MutableIntList codeList1 = IntArrayList.newListWith(1,1,1);
                final CodeSequence stabCode1 = new CodeSequence(codeList1);
                final Storage stable1 = loadStorage(new CodePair(stabCode1, new InitialAngles(XYZ.Z, XYZ.X))).get();
                selectedStorages.add(stable1);
                final MutableIntList codeList2 = IntArrayList.newListWith(1,2,1,2);
                final CodeSequence stabCode2 = new CodeSequence(codeList2);
                final Storage stable2 = loadStorage(new CodePair(stabCode2, new InitialAngles(XYZ.Z, XYZ.X))).get();
                selectedStorages.add(stable2);
                final MutableIntList codeList3 = IntArrayList.newListWith(1,1,2,2,2,2,2,1,1,3,2,2,1,1,4,1,1,2,2,3);
                final CodeSequence stabCode3 = new CodeSequence(codeList3);
                final Storage stable3 = loadStorage(new CodePair(stabCode3, new InitialAngles(XYZ.Z, XYZ.Y))).get();
                selectedStorages.add(stable3);
                final MutableIntList codeList4 = IntArrayList.newListWith(1,2,1,2,1,2,1,3,4,3);
                final CodeSequence stabCode4 = new CodeSequence(codeList4);
                final Storage stable4 = loadStorage(new CodePair(stabCode4, new InitialAngles(XYZ.Z, XYZ.X))).get();
                selectedStorages.add(stable4);
                final MutableIntList codeList5 = IntArrayList.newListWith(1,1,2,2,2,2,2,2,2,2,2,1,1,3,2,2,2,2,1,1,4,1,1,2,2,2,2,3);
                final CodeSequence stabCode5 = new CodeSequence(codeList5);
                final Storage stable5 = loadStorage(new CodePair(stabCode5, new InitialAngles(XYZ.Z, XYZ.Y))).get();
                selectedStorages.add(stable5);
                final MutableIntList codeList6 = IntArrayList.newListWith(1,2,1,3,4,3);
                final CodeSequence stabCode6 = new CodeSequence(codeList6);
                final Storage stable6 = loadStorage(new CodePair(stabCode6, new InitialAngles(XYZ.Z, XYZ.X))).get();
                selectedStorages.add(stable6);
                final MutableIntList codeList7 = IntArrayList.newListWith(1,3,3);
                final CodeSequence stabCode7 = new CodeSequence(codeList7);
                final Storage stable7 = loadStorage(new CodePair(stabCode7, new InitialAngles(XYZ.Z, XYZ.X))).get();
                selectedStorages.add(stable7);


            } else if (starPatternArea.contains(rx, ry)){
                final MutableIntList codeList1 = IntArrayList.newListWith(1, 7, 12, 6, 12, 6, 14, 8, 16, 7, 1, 17, 8, 16, 8, 14, 6, 12, 6, 12, 7, 1, 15, 8, 16, 6, 8, 4, 8, 4, 10, 6, 12, 6, 14, 8, 16, 8, 17, 1, 7, 16, 8, 14, 6, 12, 6, 10, 4, 8, 4, 8, 6, 16, 8, 15);
                final MutableIntList codeList2 = IntArrayList.newListWith(1, 7, 12, 6, 12, 4, 4, 2, 6, 4, 8, 4, 10, 6, 12, 6, 14, 8, 16, 6, 8, 4, 10, 6, 12, 6, 14, 8, 16, 7, 1, 17, 8, 15, 1, 7, 12, 6, 13, 1, 8, 1, 15, 8, 17, 1, 7, 15, 1, 8, 1, 13, 6, 12, 7, 1, 15, 8, 17, 1, 7, 16, 8, 14, 6, 12, 6, 10, 4, 8, 6, 16, 8, 15, 1, 7, 12, 6, 13, 1, 8, 1, 15, 7, 1, 17, 8, 15, 1, 7, 12, 6, 12, 4, 4, 2, 6, 4, 8, 4, 10, 6, 12, 6, 14, 8, 16, 6, 8, 4, 10, 6, 12, 6, 14, 8, 16, 7, 1, 17, 8, 15, 1, 7, 12, 6, 13, 1, 8, 1, 15, 8, 17, 1, 7, 15, 1, 8, 1, 13, 6, 12, 7, 1, 15, 8, 17, 1, 7, 16, 8, 14, 6, 12, 6, 10, 4, 8, 6, 16, 8, 15, 1, 7, 12, 6, 13, 1, 8, 1, 15, 7, 1, 17, 8, 15, 1, 7, 12, 6, 12, 4, 4, 2, 6, 4, 8, 4, 10, 6, 12, 6, 14, 8, 16, 6, 8, 4, 10, 6, 12, 6, 14, 8, 16, 7, 1, 17, 8, 15, 1, 7, 12, 6, 13, 1, 8, 1, 15, 8, 17, 1, 7, 15, 1, 8, 1, 13, 6, 12, 7, 1, 15, 8, 17, 1, 7, 16, 8, 14, 6, 12, 6, 10, 4, 8, 6, 16, 8, 15, 1, 7, 12, 6, 13, 1, 8, 1, 15, 7, 1, 17, 8, 15, 1, 8, 1, 13, 6, 12, 7, 1, 15, 8, 17, 1, 7, 16, 8, 14, 6, 12, 6, 10, 4, 8, 6, 16, 8, 14, 6, 12, 6, 10, 4, 8, 4, 6, 2, 4, 4, 12, 6, 10, 4, 8, 4, 6, 2, 4, 2, 2, 2, 8, 4, 8, 6, 16, 8, 14, 6, 12, 6, 10, 4, 8, 6, 16, 8, 15, 1, 7, 12, 6, 13, 1, 8, 1, 15, 7, 1, 17, 8, 15);
                final MutableIntList codeList3 = IntArrayList.newListWith(1, 7, 12, 4, 4, 2, 6, 4, 10, 6, 12, 4, 6, 4, 8, 2, 2, 2, 6, 4, 10, 6, 12, 4, 4, 2, 6, 4, 10, 6, 13, 1, 8, 1, 15, 6, 8, 4, 10, 6, 14, 8, 17, 1, 7, 15, 1, 7, 12, 6, 14, 7, 1, 17, 8, 14, 6, 12, 7, 1, 15, 7, 1, 17, 8, 14, 6, 10, 4, 8, 6, 15, 1, 7, 12, 6, 14, 8, 17, 1, 7, 15, 1, 7, 12, 4, 4, 2, 6, 4, 10, 6, 13, 1, 8, 1, 15, 6, 8, 4, 10, 6, 14, 8, 17, 1, 7, 15, 1, 7, 12, 6, 14, 8, 17, 1, 7, 14, 6, 12, 7, 1, 15, 7, 1, 17, 8, 14, 6, 10, 4, 8, 6, 15, 1, 8, 1, 13, 6, 10, 4, 6, 2, 4, 4, 12, 6, 10, 4, 6, 2, 2, 2, 8, 6, 16, 8, 14, 6, 10, 4, 8, 6, 15, 1, 7, 12, 6, 14, 8, 17, 1, 7, 15, 1, 7, 12, 4, 4, 2, 6, 4, 10, 6, 13, 1, 8, 1, 15, 6, 8, 4, 10, 6, 14, 8, 17, 1, 7, 15, 1, 7, 12, 6, 14, 8, 17, 1, 7, 14, 6, 12, 7, 1, 15, 7, 1, 17, 8, 14, 6, 10, 4, 8, 6, 15, 1, 8, 1, 13, 6, 10, 4, 6, 2, 4, 4, 12, 6, 10, 4, 6, 2, 2, 2, 8, 6, 16, 8, 14, 6, 10, 4, 8, 6, 15, 1, 7, 12, 6, 14, 8, 17, 1, 7, 15, 1, 7, 12, 4, 4, 2, 6, 4, 10, 6, 13, 1, 8, 1, 15, 6, 8, 4, 10, 6, 14, 8, 17, 1, 7, 15, 1, 7, 12, 6, 14, 8, 17, 1, 7, 14, 6, 12, 7, 1, 15, 7, 1, 17, 8, 14, 6, 10, 4, 8, 6, 15, 1, 8, 1, 13, 6, 10, 4, 6, 2, 4, 4, 12, 6, 10, 4, 6, 2, 2, 2, 8, 6, 16, 8, 14, 6, 10, 4, 8, 6, 15, 1, 7, 12, 6, 14, 8, 17, 1, 7, 15);
                final MutableIntList codeList4 = IntArrayList.newListWith(1, 7, 12, 6, 12, 6, 14, 8, 16, 7, 1, 17, 8, 16, 8, 14, 6, 12, 6, 12, 7, 1, 15, 8, 16, 6, 8, 4, 8, 4, 10, 6, 12, 6, 13, 1, 8, 1, 15, 8, 17, 1, 7, 16, 8, 15, 1, 7, 12, 6, 12, 6, 14, 8, 16, 7, 1, 17, 8, 16, 8, 14, 6, 12, 6, 12, 7, 1, 15, 8, 16, 6, 8, 4, 8, 4, 10, 6, 12, 6, 13, 1, 8, 1, 15, 8, 17, 1, 7, 16, 8, 15, 1, 7, 12, 6, 12, 6, 14, 8, 16, 7, 1, 17, 8, 16, 8, 14, 6, 12, 6, 12, 7, 1, 15, 8, 16, 6, 8, 4, 8, 4, 10, 6, 12, 6, 13, 1, 8, 1, 15, 8, 17, 1, 7, 16, 8, 15, 1, 7, 12, 6, 12, 6, 14, 8, 16, 7, 1, 17, 8, 16, 8, 14, 6, 12, 6, 12, 7, 1, 15, 8, 16, 7, 1, 17, 8, 15, 1, 8, 1, 13, 6, 12, 6, 10, 4, 8, 4, 8, 6, 16, 8, 15, 1, 7, 12, 6, 12, 6, 14, 8, 16, 8, 17, 1, 7, 16, 8, 14, 6, 12, 6, 12, 7, 1, 15, 8, 16, 7, 1, 17, 8, 15, 1, 8, 1, 13, 6, 12, 6, 10, 4, 8, 4, 8, 6, 16, 8, 15, 1, 7, 12, 6, 12, 6, 14, 8, 16, 8, 17, 1, 7, 16, 8, 14, 6, 12, 6, 12, 7, 1, 15, 8, 16, 6, 8, 4, 8, 4, 10, 6, 12, 6, 13, 1, 8, 1, 15, 8, 17, 1, 7, 16, 8, 15, 1, 7, 12, 6, 12, 6, 14, 8, 16, 8, 17, 1, 7, 16, 8, 14, 6, 12, 6, 12, 7, 1, 15, 8, 16, 7, 1, 17, 8, 15, 1, 8, 1, 13, 6, 12, 6, 10, 4, 8, 4, 8, 6, 16, 8, 15, 1, 7, 12, 6, 12, 6, 14, 8, 16, 8, 17, 1, 7, 16, 8, 14, 6, 12, 6, 12, 7, 1, 15, 8, 16, 7, 1, 17, 8, 15, 1, 8, 1, 13, 6, 12, 6, 10, 4, 8, 4, 8, 6, 16, 8, 15);
                final MutableIntList codeList5 = IntArrayList.newListWith(1, 7, 12, 4, 6, 4, 10, 6, 14, 6, 10, 6, 14, 7, 1, 16, 1, 8, 1, 13, 7, 1, 16, 1, 7, 14, 6, 10, 6, 15, 1, 7, 14, 7, 1, 17, 8, 14, 7, 1, 15, 6, 10, 6, 14, 7, 1, 16, 1, 7, 13, 1, 8, 1, 16, 1, 7, 14, 6, 10, 6, 14, 6, 10, 4, 6, 4, 12, 7, 1, 16, 1, 7, 14, 6, 10, 6, 15, 1, 7, 14, 7, 1, 17, 8, 14, 7, 1, 15, 6, 10, 6, 14, 7, 1, 16, 1, 7, 13, 1, 8, 1, 16, 1, 7, 14, 6, 10, 6, 14, 6, 10, 4, 6, 4, 12, 7, 1, 16, 1, 7, 14, 6, 10, 6, 15, 1, 7, 14, 7, 1, 17, 8, 14, 7, 1, 15, 6, 10, 6, 14, 7, 1, 16, 1, 7, 13, 1, 8, 1, 16, 1, 7, 14, 6, 10, 6, 14, 6, 10, 4, 6, 4, 12, 7, 1, 16, 1, 7, 14, 6, 10, 6, 15, 1, 7, 14, 7, 1, 17, 8, 14, 7, 1, 15, 6, 10, 6, 14, 7, 1, 16, 1, 7, 12, 4, 6, 4, 10, 6, 14, 6, 10, 6, 14, 7, 1, 16, 1, 8, 1, 13, 7, 1, 16, 1, 7, 14, 6, 10, 6, 15, 1, 7, 14, 8, 17, 1, 7, 14, 7, 1, 15, 6, 10, 6, 14, 7, 1, 16, 1, 7, 12, 4, 6, 4, 10, 6, 14, 6, 10, 6, 14, 7, 1, 16, 1, 8, 1, 13, 7, 1, 16, 1, 7, 14, 6, 10, 6, 15, 1, 7, 14, 8, 17, 1, 7, 14, 7, 1, 15, 6, 10, 6, 14, 7, 1, 16, 1, 7, 13, 1, 8, 1, 16, 1, 7, 14, 6, 10, 6, 14, 6, 10, 4, 6, 4, 12, 7, 1, 16, 1, 7, 14, 6, 10, 6, 15, 1, 7, 14, 8, 17, 1, 7, 14, 7, 1, 15, 6, 10, 6, 14, 7, 1, 16, 1, 7, 12, 4, 6, 4, 10, 6, 14, 6, 10, 6, 14, 7, 1, 16, 1, 8, 1, 13, 7, 1, 16, 1, 7, 14, 6, 10, 6, 15, 1, 7, 14, 8, 17, 1, 7, 14, 7, 1, 15, 6, 10, 6, 14, 7, 1, 16);
                final MutableIntList unstList = IntArrayList.newListWith(2, 4);

                final CodeSequence codeSeq1 = new CodeSequence(codeList1);
                final CodeSequence codeSeq2 = new CodeSequence(codeList2);
                final CodeSequence codeSeq3 = new CodeSequence(codeList3);
                final CodeSequence codeSeq4 = new CodeSequence(codeList4);
                final CodeSequence codeSeq5 = new CodeSequence(codeList5);
                final CodeSequence unstableSeq = new CodeSequence(unstList);

                final CodePair codePair1 = new CodePair(codeSeq1, new InitialAngles(XYZ.Z, XYZ.Y));
                final CodePair codePair2 = new CodePair(codeSeq2, new InitialAngles(XYZ.Z, XYZ.Y));
                final CodePair codePair3 = new CodePair(codeSeq3, new InitialAngles(XYZ.Z, XYZ.Y));
                final CodePair codePair4 = new CodePair(codeSeq4, new InitialAngles(XYZ.Z, XYZ.Y));
                final CodePair codePair5 = new CodePair(codeSeq5, new InitialAngles(XYZ.Z, XYZ.Y));
                final CodePair unstable = new CodePair(unstableSeq, new InitialAngles(XYZ.Y, XYZ.X));

                multiCodePairs.addAll(Arrays.asList(codePair1, codePair2, codePair3, codePair4, codePair5, unstable));

                selectedRect = starPatternArea;
                starClicked = true;

            } else {
                for (HashTriple coverRects: coverRectList) {
                    for (final Rectangle rect : coverRects.stableEntrySet()) {
                        if (rect.contains(radianX, radianY)) {
                            final CodePair codeSeq = coverRects.getStable(rect);

                            if (coverColorCycle.isSelected() && !coverColor.equals(Color.TRANSPARENT)) {
                                coverColor = ColorPicker.next(coverColor);
                                covRectsColorBox.setText(Colors.colorMap.get(coverColor).get());
                            }

                            currentCodePair = codeSeq;
                            checkOneWrap.getChildren().add(checkOneBtn);
                            selectedRect = rect;

                            if (coverColor.equals(Color.TRANSPARENT)) {
                                color = coverRects.getColor(rect);
                            } else {
                                color = coverColor;
                            }
                            for (HashTriple coverRects2: coverRectList) {
                                for (final Rectangle rect2 : coverRects2.stableEntrySet()) {
                                    final CodePair codeSeq2 = coverRects2.getStable(rect2);
                                    final Color covColor;
                                    if (color.equals(Color.TRANSPARENT)) {
                                        covColor = coverRects2.getColor(rect2);
                                    } else {
                                        covColor = color;
                                    }
                                    if (codeSeq.equals(codeSeq2)) {
                                        coverRects2.put(rect2, covColor);
                                    }
                                }
                            }
                            break;
                        }
                    }
                }

                for (HashTriple coverRects: coverRectList) {
                    for (Rectangle rect : coverRects.tripleEntrySet()) {
                        if (rect.contains(radianX, radianY)) {
                            final TriplePair codeSeq = coverRects.getTriple(rect);

                            final CodePair stableNeg = codeSeq.stableNeg;
                            final CodePair unstable = codeSeq.unstable;
                            final CodePair stablePos = codeSeq.stablePos;

                            multiCodePairs.add(stableNeg);
                            multiCodePairs.add(unstable);
                            multiCodePairs.add(stablePos);

                            selectedRect = rect;
                            checkOneWrap.getChildren().add(checkOneBtn);

                            if (coverColorCycle.isSelected() && !coverColor.equals(Color.TRANSPARENT)) {
                                coverColor = ColorPicker.next(coverColor);
                                covRectsColorBox.setText(Colors.colorMap.get(coverColor).get());
                            }

                            if (coverColor.equals(Color.TRANSPARENT)) {
                                color = coverRects.getColor(rect);
                            } else {
                                color = coverColor;
                            }
                            for (HashTriple coverRects2 : coverRectList) {
                                for (final Rectangle rect2 : coverRects2.tripleEntrySet()) {
                                    final TriplePair codeSeq2 = coverRects2.getTriple(rect2);
                                    final Color covColor;
                                    if (color.equals(Color.TRANSPARENT)) {
                                        covColor = coverRects2.getColor(rect2);
                                    } else {
                                        covColor = color;
                                    }
                                    if (codeSeq.equals(codeSeq2)) {
                                        coverRects2.put(rect2, covColor);
                                    }
                                }
                            }
                            break;
                        }
                    }
                }
			}
			makeRightScrollPane(selectedStorages, color);
            renderRegions(guideLinesImageView);

        } else {
            final double zoom = Double.parseDouble(zoomScaleText.getText());

            if (magnifyRdoBtn.isSelected()) {
                map.scaleBy(zoom);
            } else if (demagnifyRdoBtn.isSelected()) {
                map.scaleBy(1 / zoom);
            } else if (centerBtn.isSelected()) {
                map.scaleBy(1);
            } else {
            	new ErrorAlert(new RuntimeException("Somehow, no click "
            			+ "setting was selected.")).showAndWait();
                return;
            }

            final double newRadianX = map.radianX(SIZE / 2 + 0.5);
            final double newRadianY = map.radianY(SIZE / 2 + 0.5);

            map.translateXBy(oldRadianX - newRadianX);
            map.translateYBy(oldRadianY - newRadianY);

            // we want map(mouse click) == zoomed map(center)

            viewRectangleBF.add(map.getViewRectangle());

            renderRegions(guideLinesImageView);
        }
    }

    private void makeRightScrollPane(final SortedSet<Storage> selectedStorages, final Color color) {
        // remove any listings already there
        codeSequencesGPane.getChildren().clear();
        int row = 0;
        // the for loops below were made in 2021, but for some reasons (maybe some errors), was taken out in 2022.
        for (final Storage storage : selectedStorages) {
            final int finalRow = row;
            final CheckBox drawCBox = new CheckBox();

            drawCBox.setText("Draw");
            drawCBox.setStyle(textBoxColor);
            drawCBox.setAllowIndeterminate(true);
            drawCBox.setIndeterminate(drawCBoxes[finalRow] == 0);
            drawCBox.setSelected(drawCBoxes[finalRow] == 1);

            if (drawCBox.isSelected() || drawCBox.isIndeterminate()) {
                currentStorages.add(storage);
                currentColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), 0.5);
                //currentColor = new Color(color.getRed(), color.getRed(), color.getRed(), 0.5);//george doesn't do anything
            }

            if (drawCBox.isSelected() && CodeType.isStable(storage.type)) {
                final Storage.Stable stable = (Storage.Stable) storage;
                currentBounds.add(stable.polygon);
            }

            final String codeString = storage.toString();
            final TextField lblCodeSequence = new TextField(codeString.split(",")[0]);
            final Label codeInfo = new Label();
            codeInfo.setText(storage.type + " (" + storage.classCodeSeq.length() + "," + storage.classCodeSeq.sum() + ")");
            codeInfo.setPadding(new Insets(5, 5, 5, 0));
            lblCodeSequence.setPrefWidth(100);
            lblCodeSequence.setEditable(false);

            drawCBox.setOnAction(event -> {
                currentStorages.remove(storage);
                if (CodeType.isStable(storage.type)) {
                    currentBounds.remove(((Storage.Stable) storage).polygon);
                }
                if (drawCBox.isSelected()) {
                    drawCBoxes[finalRow] = 1;
                    currentStorages.add(storage);
                    currentColor = Color.web(covRectsColorBox.getText().toLowerCase().replaceAll("\\s", ""), 0.5);//george change 0.5 to 1.0 and back
                    if (CodeType.isStable(storage.type)) {
                        final Storage.Stable stable = (Storage.Stable) storage;
                        currentBounds.add(stable.polygon);
                    }
                } else if (drawCBox.isIndeterminate()) {
                    drawCBoxes[finalRow] = 0;
                    currentStorages.add(storage);
                    currentColor = Color.web(covRectsColorBox.getText().toLowerCase().replaceAll("\\s", ""), 0.5);

                } else {
                    drawCBoxes[finalRow] = -1;
                }
                renderRegions(guideLinesImageView);
            });

            final HBox codeInfoHBox = new HBox(10);
            codeInfoHBox.getChildren().addAll(codeInfo, lblCodeSequence, drawCBox);
            codeInfoHBox.setAlignment(Pos.CENTER_LEFT);

            codeSequencesGPane.addRow(row, codeInfoHBox);
            row += 1;
        }

        if(multiCodePairs.size() == 0){ //Single stable was clicked
            singleStableClicked(color);
        } else { //Triple was clicked
            tripleStarClicked(color);
        }
        renderRegions(guideLinesImageView);
    }

    private void singleStableClicked(Color color){
        if (currentCodePair != null ){
            final int finalRow = 0;
            final CheckBox drawCBox = new CheckBox();

            drawCBox.setText("Draw");
            drawCBox.setStyle(textBoxColor);
            drawCBox.setAllowIndeterminate(true);
            drawCBox.setIndeterminate(false);
            drawCBox.setSelected(false);


            final String type = Wrapper.getCodeSequence(currentCodePair);
            final int length = getCodeLength(currentCodePair.sequence.toString());
            final int sum = getCodeSum(currentCodePair.sequence.toString());
            final TextField lblCodeSequence = new TextField(currentCodePair.sequence.toString());
            final Label codeInfo = new Label();
            codeInfo.setText(type + " (" + length + "," + sum + ")");
            codeInfo.setPadding(new Insets(5, 5, 5, 0));
            lblCodeSequence.setPrefWidth(100);
            lblCodeSequence.setEditable(false);

            drawCBox.setOnAction(event -> {
                currentStorages.clear();
                currentBounds.clear();
                if (drawCBox.isSelected()) {
                    final Alert alert = new Alert(AlertType.CONFIRMATION);
                    alert.setTitle("Drawing MRR Polygon");
                    alert.setHeaderText("Would you like to see the MRR Polygon");
                    Label content = new Label("Drawing the MRR Polygon requires all exact equations of the code sequence" +
                            "to be generated and may result in hours or even days of waiting for extremely large codes.  Do you wish " +
                            "to proceed?");
                    content.setFont(new Font("Times new roman", 15));
                    content.setWrapText(true);
                    alert.getDialogPane().setContent(content);
                    final Optional<ButtonType> response = alert.showAndWait();
                    if (response.isPresent() && response.get() == ButtonType.OK){
                        //Load Full storage
                        final Storage storage = loadStorage(currentCodePair).get();//Loading in this code sequence is what takes time

                        currentStorages.remove(storage);
                        if (CodeType.isStable(storage.type)) {
                            currentBounds.remove(((Storage.Stable) storage).polygon);
                        }

                        //Perform action needed
                        drawBoxSelectedAction(finalRow, storage, color);
                    }
                } else if (drawCBox.isIndeterminate()) {
                    //Load half storage
                    String polygonString = Wrapper.getBoundingPolygon(currentCodePair);
                    MutableList<Point> points = parsePolygonString(polygonString);

                    MutableList<Point> radPoints = new FastList<>();
                    for (Point point : points) {
                        radPoints.add(Point.create(Math.toRadians(point.x), Math.toRadians(point.y)));
                    }
                    ConvexPolygon polygon = ConvexPolygon.create(radPoints.toImmutable());

                    // Perform action needed
                    drawBoxIntermediateAction(finalRow, polygon, color);
                }
                renderRegions(guideLinesImageView);
            });

            final HBox codeInfoHBox = new HBox(10);
            codeInfoHBox.getChildren().addAll(codeInfo, lblCodeSequence, drawCBox);
            codeInfoHBox.setAlignment(Pos.CENTER_LEFT);

            codeSequencesGPane.addRow(0, codeInfoHBox);
        }
    }

    private void tripleStarClicked(Color color){
        int row = 0;
        for (final CodePair codePair : multiCodePairs) {
            final int finalRow = row;

            final String type = Wrapper.getCodeSequence(codePair);
            final int length = getCodeLength(codePair.sequence.toString());
            final int sum = getCodeSum(codePair.sequence.toString());
            final TextField lblCodeSequence = new TextField(codePair.sequence.toString());
            final Label codeInfo = new Label();
            codeInfo.setText(type + " (" + length + "," + sum + ")");
            codeInfo.setPadding(new Insets(5, 5, 5, 0));
            lblCodeSequence.setPrefWidth(100);
            lblCodeSequence.setEditable(false);
            final CheckBox drawCBox = new CheckBox();
            drawCBox.setText("Draw");
            drawCBox.setStyle(textBoxColor);
            drawCBox.setSelected(false);

            if (type.equals("OSNO") || type.equals("OSO") || type.equals("CS")){
                drawCBox.setAllowIndeterminate(true);
                drawCBox.setIndeterminate(false);
            }

            drawCBox.setOnAction(event -> {
                checkOneWrap.getChildren().clear();
                checkOneWrap.getChildren().add(checkOneBtn);
                currentCodePair = codePair;
                currentStorages.clear();
                currentBounds.clear();
                if (drawCBox.isSelected()) {
                    final Alert alert = new Alert(AlertType.CONFIRMATION);
                    alert.setTitle("Drawing MRR Polygon");
                    alert.setHeaderText("Would you like to see the MRR Polygon");
                    Label content = new Label("Drawing the MRR Polygon requires all exact equations of the code sequence" +
                            " to be generated and may result in hours or even days of waiting for extremely large codes.  Do you wish " +
                            "to proceed?");
                    content.setFont(new Font("Times new roman", 15));
                    content.setWrapText(true);
                    alert.getDialogPane().setContent(content);
                    final Optional<ButtonType> response = alert.showAndWait();
                    if (response.isPresent() && response.get() == ButtonType.OK){
                        //Load Full storage
                        currentCodePair = codePair;
                        final Storage storage = loadStorage(codePair).get();//Loading in this code sequence is what takes time

                        currentStorages.remove(storage);
                        if (CodeType.isStable(storage.type)) {
                            currentBounds.remove(((Storage.Stable) storage).polygon);
                        }

                        //Perform action needed
                        drawBoxSelectedAction(finalRow, storage, color); //Adds it to current storages
                        renderRegions(guideLinesImageView);
                    } else if (response.isPresent() && response.get() == ButtonType.CANCEL){
                        drawCBox.setSelected(false);
                        drawCBox.setIndeterminate(false);
                    }
                } else if (type.equals("OSNO") || type.equals("OSO") || type.equals("CS")){
                    if (drawCBox.isIndeterminate()){
                        currentCodePair = codePair;
                        //Getting only the bounding polygon for the code
                        String polygonString = Wrapper.getBoundingPolygon(codePair);
                        MutableList<Point> points = parsePolygonString(polygonString);

                        MutableList<Point> radPoints = new FastList<>();
                        for (Point point : points) {
                            radPoints.add(Point.create(Math.toRadians(point.x), Math.toRadians(point.y)));
                        }
                        ConvexPolygon polygon = ConvexPolygon.create(radPoints.toImmutable());

                        // Perform action needed
                        drawBoxIntermediateAction(finalRow, polygon, color);
                        renderRegions(guideLinesImageView);
                    }
                }
                renderRegions(guideLinesImageView);
            });

            final HBox codeInfoHBox = new HBox(10);
            codeInfoHBox.getChildren().addAll(codeInfo, lblCodeSequence);
            codeInfoHBox.setAlignment(Pos.CENTER_LEFT);

            codeInfoHBox.getChildren().add(drawCBox);

            codeSequencesGPane.addRow(row, codeInfoHBox);
            row += 1;
        }
    }

    private void drawBoxSelectedAction(int finalRow, Storage storage, Color color){
        currentStorages.add(storage);
        currentColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), 0.5);
        if (CodeType.isStable(storage.type)) {
            final Storage.Stable stable = (Storage.Stable) storage;
            currentBounds.add(stable.polygon);
        }
    }

    private void drawBoxIntermediateAction(int finalRow, ConvexPolygon polygon, Color color){
        currentColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), 0.5);
        currentBounds.add(polygon);
    }

    private static void setImageColor(final WritableImage image, final Color color) {
        final PixelWriter pixelWriter = image.getPixelWriter();

        for (int pixelX = 0; pixelX < SIZE; pixelX += 1) {
            for (int pixelY = 0; pixelY < SIZE; pixelY += 1) {
                pixelWriter.setColor(pixelX, pixelY, color);
            }
        }
    }

    private static ImageView renderColor(final Color color) {
        final WritableImage image = new WritableImage(SIZE, SIZE);

        setImageColor(image, color);

        final ImageView imageView = new ImageView(image);

        return imageView;
    }

    // NOTE: static functions don't rely on UI elements, since all the UI elements are non-static
    // the storages list already has newest first
    // Static functions are good, because they are more thread-safer
    private void renderRegions(final ImageView guideLinesImageView) {
        regionStack.getChildren().clear();
        final WritableImage guideLinesImage = renderGuideLines();
        guideLinesImageView.setImage(guideLinesImage);
        for (HashTriple region : coverRectList) {
            WritableImage regionImage = new WritableImage(SIZE, SIZE);
            for (final Rectangle rect : region.stableEntrySet()) {
                renderRect(rect, regionImage, region.getColor(rect), Color.FIREBRICK);
            }
            for (final Rectangle rect : region.tripleEntrySet()) {
                renderRect(rect, regionImage, region.getColor(rect), Color.FIREBRICK);
            }
            if (selectedRect != null) {
                if (!starClicked){
                    renderRect(selectedRect, regionImage, region.getColor(selectedRect), Color.WHITE);
                }
            }
            ImageView tmp = new ImageView();
            tmp.setImage(regionImage);
            regionStack.getChildren().add(tmp);
        }

        if (showCoverGuidelines.isSelected()) {
            final WritableImage boundsImage = new WritableImage(SIZE, SIZE);
            if (coverArea.isPresent()) {
                renderPolygon(coverArea.get(), boundsImage, coverAreaColor);
            }

            boundsImageView.setImage(boundsImage);
        }
        final WritableImage oboImage = new WritableImage(SIZE, SIZE);

        for (final Storage currentStorage : currentStorages) {
            renderRegion(currentStorage, oboImage, currentColor);
        }

        for (ConvexPolygon poly : currentBounds) {
            renderPolygonShell(poly, oboImage, currentColor.brighter());
        }

        regionIV.setImage(oboImage);
    }

    private void renderRect(final Rectangle rect, final WritableImage image, Color colorInside, Color colorBound) {
        final PixelWriter pixelWriter = image.getPixelWriter();
        final PixelReader pixelReader = image.getPixelReader();
        final int startPX = Math.max((int) map.pixelX(rect.intervalX.min), 0);
        final int startPY = Math.max((int) map.pixelY(rect.intervalY.min), 0);
        final int endPX = Math.min((int) map.pixelX(rect.intervalX.max), SIZE);
        final int endPY = Math.min((int) map.pixelY(rect.intervalY.max), SIZE);

        for (int i = startPX; i <= endPX; i++) {
            for (int j = startPY; j <= endPY; j++) {
                if (SIZE > i && i >= 0 && SIZE > j && j >= 0) {
                    double rx = map.radianX(i);
                    double ry = map.radianY(j);
                    if (coverArea.isPresent() && coverArea.get().location(rx, ry) == Location.OUTSIDE) {
                        //continue;
                    }
                    try {
                        if(pixelReader.getColor(i, j) != colorInside && pixelReader.getColor(i, j) != colorBound) {
                            if ((i == startPX || i == endPX || j == startPY || j == endPY)) {
                                if(colorBound == null){
                                    colorBound = Color.FIREBRICK;
                                }
                                pixelWriter.setColor(i, j, colorBound);
                            } else {
                                if(colorInside == null){
                                    colorInside = Color.BLACK;
                                }
                                pixelWriter.setColor(i, j, colorInside);
                            }
                        }
                    }
                    catch (final IndexOutOfBoundsException e) {
                        System.out.println("i: " + i + "  j: " + j);
                    }
                }
            }
        }
    }


    private void renderPolygonShell(final ConvexPolygon poly, final WritableImage image, final Color color) {
            final PixelWriter pixelWriter = image.getPixelWriter();

            final ImmutableList<Point> vertices = poly.vertices;
            final int size = vertices.size();

            for (int i = 0; i < size; ++i) {
                final Point a = vertices.get(i);
                final Point b = vertices.get((i + 1) % size);

                // horizontal
                if (a.y == b.y) {
                    drawHorizontalLine(
                    		a.y, Math.min(a.x, b.x), Math.max(a.x, b.x), pixelWriter, color, true);
                }
                // vertical
                else if (a.x == b.x) {
                    drawVerticalLine(
                    		a.x, Math.min(a.y, b.y), Math.max(a.y, b.y), pixelWriter, color, true);
                }
                // diagonal
                else {
                    final double slopeY = (b.y - a.y) / (b.x - a.x);
                    final DoubleUnaryOperator funcY = x -> slopeY * (x - a.x) + a.y; // y(x)

                    final double slopeX = (b.x - a.x) / (b.y - a.y);
                    final DoubleUnaryOperator funcX = y -> slopeX * (y - a.y) + a.x; // x(y)

                    drawObliqueLine(funcY, Math.min(a.x, b.x), Math.max(a.x, b.x), funcX,
                             Math.min(a.y, b.y), Math.max(a.y, b.y), pixelWriter, color, true);
                }
            }
        }


    private void drawHorizontalLine(final double y, final double x1, final double x2,
                 final PixelWriter pixelWriter, final Color color, final boolean thicken) {
        final int pixelY = (int) map.pixelY(y);

        if (0 <= pixelY && pixelY < SIZE) {
            for (int pixelX = 0; pixelX < SIZE; pixelX += 1) {
                final double radianX = map.radianX(pixelX + 0.5);
                if (x1 <= radianX && radianX <= x2) {
                    pixelWriter.setColor(pixelX, pixelY, color);
                    if ((pixelY + 1 < SIZE && pixelY - 1 >= 0) && thicken) {
                    	pixelWriter.setColor(pixelX, pixelY + 1, color);
                        pixelWriter.setColor(pixelX, pixelY - 1, color);
                    }
                }
            }
        }
    }

    // x, y1, y2 are in radians
    private void drawVerticalLine(final double x, final double y1, final double y2,
               final PixelWriter pixelWriter, final Color color, final boolean thicken) {
        final int pixelX = (int) map.pixelX(x);

        if (0 <= pixelX && pixelX < SIZE) {
            for (int pixelY = 0; pixelY < SIZE; pixelY += 1) {
                final double radianY = map.radianY(pixelY + 0.5);
                if (y1 <= radianY && radianY <= y2) {
                    pixelWriter.setColor(pixelX, pixelY, color);
                    if ((pixelX + 1 < SIZE && pixelX - 1 >= 0) && thicken) {
                    	pixelWriter.setColor(pixelX + 1, pixelY, color);
                        pixelWriter.setColor(pixelX - 1, pixelY, color);
                    }
                }
            }
        }
    }

    private void drawObliqueLine(final DoubleUnaryOperator y, final double x1, final double x2,
                                 final DoubleUnaryOperator x, final double y1, final double y2,
                                 final PixelWriter pixelWriter, final Color color, final boolean thicken) {

        // let's iterate across the x values
        for (int pixelX = 0; pixelX < SIZE; pixelX += 1) {
            final double radianX = map.radianX(pixelX);
            final double radianY = y.applyAsDouble(radianX);

            if (y1 <= radianY && radianY <= y2) {
                final int pixelY = (int) map.pixelY(radianY);

                if (0 <= pixelY && pixelY < SIZE) {
                    pixelWriter.setColor(pixelX, pixelY, color);
                    if (thicken && (1 <= pixelY && pixelY < SIZE - 1)) {
	                    pixelWriter.setColor(pixelX, pixelY + 1, color);
	                    pixelWriter.setColor(pixelX, pixelY - 1, color);
                    }
                }
            }
        }

        // now iterate over the rows
        for (int pixelY = 0; pixelY < SIZE; pixelY += 1) {
            final double radianY = map.radianY(pixelY);
            final double radianX = x.applyAsDouble(radianY);

            // is it part of the line segment?
            if (x1 <= radianX && radianX <= x2) {
                final int pixelX = (int) map.pixelX(radianX);

                // is it on screen?
                if (0 <= pixelX && pixelX < SIZE) {
                    pixelWriter.setColor(pixelX, pixelY, color);
                    if (thicken && (1 <= pixelX && pixelX < SIZE - 1)) {
	                    pixelWriter.setColor(pixelX + 1, pixelY, color);
	                    pixelWriter.setColor(pixelX - 1, pixelY, color);
                    }
                }
            }
        }
    }

    private WritableImage renderGuideLines() {
        // render the lines in the background image
        final WritableImage image = new WritableImage(SIZE, SIZE);
        final PixelWriter pixelWriter = image.getPixelWriter();

       //  we consider the infinite pattern area as part of the background
        for (int i = 0; i < SIZE; i++) {
        	for (int j = 0; j < SIZE; j++) {
        		final double rx = map.radianX(i);
        		final double ry = map.radianY(j);
        		if (infPatternArea.location(rx, ry) == Location.INSIDE) {
            		pixelWriter.setColor(i, j, Color.LIGHTGREY);//george may 20,2020 this gives the infinite corner
        		}
                if (infPatternArea2.location(rx, ry) == Location.INSIDE) {
                    pixelWriter.setColor(i, j, Color.LIGHTGREY);//george may 20,2020 this gives the infinite corner
                }
                if (infPatternArea3.location(rx, ry) == Location.INSIDE) {
                    pixelWriter.setColor(i, j, Color.LIGHTGREY);//george may 20,2020 this gives the infinite corner
                }
                if (infPatternArea4.location(rx, ry) == Location.INSIDE) {
                    pixelWriter.setColor(i, j, Color.LIGHTGREY);//george may 20,2020 this gives the infinite corner
                }
            	if (ry > 67.5 * Math.PI / 180 && (rx + ry) < Math.PI / 2 && rx > 0) {
            	}
            }
        }

        // we consider the infinite pattern area as part of the background
      /*  for (int i = 0; i < SIZE; i++) {
        	for (int j = 0; j < SIZE; j++) {
        		final double rx = map.radianX(i);
        		final double ry = map.radianY(j);
        		if (infPatternArea2.location(rx, ry) == Location.INSIDE) {
            		pixelWriter.setColor(i, j, Color.LIGHTGREY);//george may 20,2020 this gives the infinite corner
        		}
            	if (rx > 22.4998 * Math.PI / 180 && (rx + ry) < 45.0024 * Math.PI / 180 && ry > 22.499999 * Math.PI /180) {
            	}
            }
        }*/ //george may31,2020 this will grey out

     // we consider the infinite pattern area as part of the background
       /* for (int i = 0; i < SIZE; i++) {
        	for (int j = 0; j < SIZE; j++) {
        		final double rx = map.radianX(i);
        		final double ry = map.radianY(j);
        		if (infPatternArea.location(rx, ry) == Location.INSIDE) {
            		pixelWriter.setColor(i, j, Color.LIGHTGREY);//george may 20,2020 this gives the infinite corner
        		}
            	if (rx > 22.49 * Math.PI / 180 && (rx + ry) < 90 * Math.PI / 180 && ry > 22.49 * Math.PI /180) {
            	}
            }
        }*/

        // we have several horizontal lines, several vertical lines, and oblique ones
        drawHorizontalLine(0, 0, Math.PI, pixelWriter, lineColor, false);
        drawVerticalLine(0, 0, Math.PI, pixelWriter, lineColor, false);

        drawHorizontalLine(Math.PI / 2, 0, Math.PI / 2, pixelWriter, lineColor, false);
        drawVerticalLine(Math.PI / 2, 0, Math.PI / 2, pixelWriter, lineColor, false);


        // x + y = 90
        drawObliqueLine(x-> Math.PI / 2 - x,
                 0, Math.PI / 2, y -> Math.PI / 2 - y, 0, Math.PI / 2, pixelWriter, lineColor, false);

        // x + y = 180
        drawObliqueLine(
            x -> Math.PI - x, 0, Math.PI, y -> Math.PI - y, 0, Math.PI, pixelWriter, lineColor, false);
        // IMPORTANT: This is the line x = y
        drawObliqueLine(x -> x, 0, Math.PI / 4, y -> y, 0, Math.PI / 4, pixelWriter, lineColor, false);
        if (showAllGuidelines.isSelected()) {
            // IMPORTANT: This is the line x + y = 80
            drawObliqueLine(x
                            -> 4 * Math.PI / 9 - x,
                            0, 4 * Math.PI / 9,
                            y -> 4 * Math.PI / 9 - y, 0, 4 * Math.PI / 9, pixelWriter, lineColor, false);

            // IMPORTANT: This is the line x + y = 75
            drawObliqueLine(x
                            -> 15 * Math.PI / 36 - x,
                            0, 15 * Math.PI / 36,
                            y -> 15 * Math.PI / 36 - y, 0, 15 * Math.PI / 36, pixelWriter, lineColor, false);

            // IMPORTANT: This is the line x + y = 70
            drawObliqueLine(x
                            -> 7 * Math.PI / 18 - x,
                            0, 7 * Math.PI / 18,
                            y -> 7 * Math.PI / 18 - y, 0, 7 * Math.PI / 18, pixelWriter, lineColor, false);

            // IMPORTANT: This is the line x + y = 68
            drawObliqueLine(x
                            -> 17 * Math.PI / 45 - x,
                            0, 17 * Math.PI / 45,
                            y -> 17 * Math.PI / 45 - y, 0, 17 * Math.PI / 45, pixelWriter, lineColor, false);

            // IMPORTANT: This is the line x + y = 67.7 //george
            drawObliqueLine(x
                            -> 677 * Math.PI / 1800 - x,
                            0, 677 * Math.PI / 1800,
                            y -> 677 * Math.PI / 1800 - y, 0, 677 * Math.PI / 1800, pixelWriter, lineColor, false);


            // IMPORTANT: This is the line x + y = 67.6 //george
            drawObliqueLine(x
                            -> 169 * Math.PI / 450 - x,
                            0, 169 * Math.PI / 450,
                            y -> 169 * Math.PI / 450 - y, 0, 169 * Math.PI / 450, pixelWriter, lineColor, false);
            
         // IMPORTANT: This is the line x + y = 67.55 //george oct 28,2025
            drawObliqueLine(x
                            -> 1351 * Math.PI / 3600 - x,
                            0, 1351 * Math.PI / 3600,
                            y -> 1351 * Math.PI / 3600 - y, 0, 1351 * Math.PI / 3600, pixelWriter, lineColor, false);
            
         // IMPORTANT: This is the line x + y = 67.5 //george oct 28,2025
            drawObliqueLine(x
                            -> 3 * Math.PI / 8 - x,
                            0, 3 * Math.PI / 8,
                            y -> 3 * Math.PI / 8 - y, 0, 3 * Math.PI / 8, pixelWriter, lineColor, false);

            // IMPORTANT: This is the line x + y = 21.335 //george 2022
            drawObliqueLine(x
                            -> 21335 * Math.PI / 180000 - x,
                            0, 21335 * Math.PI / 180000,
                            y -> 21335 * Math.PI / 180000 - y, 0, 21335 * Math.PI / 180000, pixelWriter, lineColor, false);

            // IMPORTANT: This is the line x + y = 21.47 //george 2022
            drawObliqueLine(x
                            -> 2147 * Math.PI / 18000 - x,
                            0, 2147 * Math.PI / 18000,
                            y -> 2147 * Math.PI / 18000 - y, 0, 2147 * Math.PI / 18000, pixelWriter, lineColor, false);

            // IMPORTANT: This is the line x + y = 21.52 //george 2022
            drawObliqueLine(x
                            -> 2152 * Math.PI / 18000 - x,
                            0, 2152 * Math.PI / 18000,
                            y -> 2152 * Math.PI / 18000 - y, 0, 2152 * Math.PI / 18000, pixelWriter, lineColor, false);

            // IMPORTANT: This is the line x + y = 21.565 //george 2022
            drawObliqueLine(x
                            -> 21565 * Math.PI / 180000 - x,
                            0, 21565 * Math.PI / 180000,
                            y -> 21565 * Math.PI / 180000 - y, 0, 21565 * Math.PI / 180000, pixelWriter, lineColor, false);

            // IMPORTANT: This is the line x + y = 21.663 //george 2022
            drawObliqueLine(x
                            -> 21663 * Math.PI / 180000 - x,
                            0, 21663 * Math.PI / 180000,
                            y -> 21663 * Math.PI / 180000 - y, 0, 21663 * Math.PI / 180000, pixelWriter, lineColor, false);

            // IMPORTANT: This is the line x + y = 21.815 //george 2022
            drawObliqueLine(x
                            -> 21815 * Math.PI / 180000 - x,
                            0, 21815 * Math.PI / 180000,
                            y -> 21815 * Math.PI / 180000 - y, 0, 21815 * Math.PI / 180000, pixelWriter, lineColor, false);

            // IMPORTANT: This is the line x + y = 21.915 //george 2022
            drawObliqueLine(x
                            -> 21915 * Math.PI / 180000 - x,
                            0, 21915 * Math.PI / 180000,
                            y -> 21915 * Math.PI / 180000 - y, 0, 21915 * Math.PI / 180000, pixelWriter, lineColor, false);

            // IMPORTANT: This is the line x + y = 22.05 //george 2022
            drawObliqueLine(x
                            -> 2205 * Math.PI / 18000 - x,
                            0, 2205 * Math.PI / 18000,
                            y -> 2205 * Math.PI / 18000 - y, 0, 2205 * Math.PI / 18000, pixelWriter, lineColor, false);

            // IMPORTANT: This is the line x + y = 22.12 //george 2022
            drawObliqueLine(x
                            -> 2212 * Math.PI / 18000 - x,
                            0, 2212 * Math.PI / 18000,
                            y -> 2212 * Math.PI / 18000 - y, 0, 2212 * Math.PI / 18000, pixelWriter, lineColor, false);

            // IMPORTANT: This is the line x + y = 22.48 //george 2022
            drawObliqueLine(x
                            -> 2248 * Math.PI / 18000 - x,
                            0, 2248 * Math.PI / 18000,
                            y -> 2248 * Math.PI / 18000 - y, 0, 2248 * Math.PI / 18000, pixelWriter, lineColor, false);

            // IMPORTANT: This is the line x + y = 22.5 //george 2022
            drawObliqueLine(x
                            -> 225 * Math.PI / 1800 - x,
                            0, 225 * Math.PI / 1800,
                            y -> 225 * Math.PI / 1800 - y, 0, 225 * Math.PI / 1800, pixelWriter, lineColor, false);

            // IMPORTANT: This is the line x + y = 22.6 //george 2022
            drawObliqueLine(x
                            -> 226 * Math.PI / 1800 - x,
                            0, 226 * Math.PI / 1800,
                            y -> 226 * Math.PI / 1800 - y, 0, 226 * Math.PI / 1800, pixelWriter, lineColor, false);

            // IMPORTANT: This is the line x + y = 23.125 //george 2022
            drawObliqueLine(x
                            -> 23125 * Math.PI / 180000 - x,
                            0, 23125 * Math.PI / 180000,
                            y -> 23125 * Math.PI / 180000 - y, 0, 23125 * Math.PI / 180000, pixelWriter, lineColor, false);

            // IMPORTANT: This is the line x + y = 23.2 //george 2022
            drawObliqueLine(x
                            -> 232 * Math.PI / 1800 - x,
                            0, 232 * Math.PI / 1800,
                            y -> 232 * Math.PI / 1800 - y, 0, 232 * Math.PI / 1800, pixelWriter, lineColor, false);

            // IMPORTANT: This is the line x + y = 23.3 //george 2022
            drawObliqueLine(x
                            -> 233 * Math.PI / 1800 - x,
                            0, 233 * Math.PI / 1800,
                            y -> 233 * Math.PI / 1800 - y, 0, 233 * Math.PI / 1800, pixelWriter, lineColor, false);

            // IMPORTANT: This is the line x + y = 23.35 //george 2022
            drawObliqueLine(x
                            -> 2335 * Math.PI / 18000 - x,
                            0, 2335 * Math.PI / 18000,
                            y -> 2335 * Math.PI / 18000 - y, 0, 2335 * Math.PI / 18000, pixelWriter, lineColor, false);

            // IMPORTANT: This is the line x + y = 23.4 //george 2022
            drawObliqueLine(x
                            -> 234 * Math.PI / 1800 - x,
                            0, 234 * Math.PI / 1800,
                            y -> 234 * Math.PI / 1800 - y, 0, 234 * Math.PI / 1800, pixelWriter, lineColor, false);

            // IMPORTANT: This is the line x + y = 25.35 //george 2022
            drawObliqueLine(x
                            -> 2535 * Math.PI / 18000 - x,
                            0, 2535 * Math.PI / 18000,
                            y -> 2535 * Math.PI / 18000 - y, 0, 2535 * Math.PI / 18000, pixelWriter, lineColor, false);

            // IMPORTANT: This is the line x + y = 25.5 //george 2022
            drawObliqueLine(x
                            -> 255 * Math.PI / 1800 - x,
                            0, 255 * Math.PI / 1800,
                            y -> 255 * Math.PI / 1800 - y, 0, 255 * Math.PI / 1800, pixelWriter, lineColor, false);

            // IMPORTANT: This is the line x + y = 23.5 //george 2022
            drawObliqueLine(x
                            -> 235 * Math.PI / 1800 - x,
                            0, 235 * Math.PI / 1800,
                            y -> 235 * Math.PI / 1800 - y, 0, 235 * Math.PI / 1800, pixelWriter, lineColor, false);

            // IMPORTANT: This is the line x + y = 23.6 //george 2022
            drawObliqueLine(x
                            -> 236 * Math.PI / 1800 - x,
                            0, 236 * Math.PI / 1800,
                            y -> 236 * Math.PI / 1800 - y, 0, 236 * Math.PI / 1800, pixelWriter, lineColor, false);

            // IMPORTANT: This is the line x + y = 23.7 //george 2022
            drawObliqueLine(x
                            -> 237 * Math.PI / 1800 - x,
                            0, 237 * Math.PI / 1800,
                            y -> 237 * Math.PI / 1800 - y, 0, 237 * Math.PI / 1800, pixelWriter, lineColor, false);

            // IMPORTANT: This is the line x + y = 23.99 //george 2022
            drawObliqueLine(x
                            -> 2399 * Math.PI / 18000 - x,
                            0, 2399 * Math.PI / 18000,
                            y -> 2399 * Math.PI / 18000 - y, 0, 2399 * Math.PI / 18000, pixelWriter, lineColor, false);

            // IMPORTANT: This is the line x + y = 24.5 //george 2022
            drawObliqueLine(x
                            -> 245 * Math.PI / 1800 - x,
                            0, 245 * Math.PI / 1800,
                            y -> 245 * Math.PI / 1800 - y, 0, 245 * Math.PI / 1800, pixelWriter, lineColor, false);

            // IMPORTANT: This is the line x + y = 28.65 //george 2022
            drawObliqueLine(x
                            -> 2865 * Math.PI / 18000 - x,
                            0, 2865 * Math.PI / 18000,
                            y -> 2865 * Math.PI / 18000 - y, 0, 2865 * Math.PI / 18000, pixelWriter, lineColor, false);

            // IMPORTANT: This is the line x + y = 25.5 //george 2022
            drawObliqueLine(x
                            -> 255 * Math.PI / 1800 - x,
                            0, 255 * Math.PI / 1800,
                            y -> 255 * Math.PI / 1800 - y, 0, 255 * Math.PI / 1800, pixelWriter, lineColor, false);

            // IMPORTANT: This is the line x + y = 25.7 //george 2022
            drawObliqueLine(x
                            -> 257 * Math.PI / 1800 - x,
                            0, 257 * Math.PI / 1800,
                            y -> 257 * Math.PI / 1800 - y, 0, 257 * Math.PI / 1800, pixelWriter, lineColor, false);

            // IMPORTANT: This is the line x + y = 25.9 //george 2022
            drawObliqueLine(x
                            -> 259 * Math.PI / 1800 - x,
                            0, 259 * Math.PI / 1800,
                            y -> 259 * Math.PI / 1800 - y, 0, 259 * Math.PI / 1800, pixelWriter, lineColor, false);

            // IMPORTANT: This is the line x + y = 26.1 //george 2022
            drawObliqueLine(x
                            -> 261 * Math.PI / 1800 - x,
                            0, 261 * Math.PI / 1800,
                            y -> 261 * Math.PI / 1800 - y, 0, 261 * Math.PI / 1800, pixelWriter, lineColor, false);

         // IMPORTANT: This is the line x + y = 26.33 //george 2022
            drawObliqueLine(x
                            -> 2633 * Math.PI / 18000 - x,
                            0, 2633 * Math.PI / 18000,
                            y -> 2633 * Math.PI / 18000 - y, 0, 2633 * Math.PI / 18000, pixelWriter, lineColor, false);

            // IMPORTANT: This is the line x + y = 28.65 //george 2022
            drawObliqueLine(x
                            -> 2865 * Math.PI / 18000 - x,
                            0, 2865 * Math.PI / 18000,
                            y -> 2865 * Math.PI / 18000 - y, 0, 2865 * Math.PI / 18000, pixelWriter, lineColor, false);

            // IMPORTANT: This is the line x + y = 29.9 //george 2022
            drawObliqueLine(x
                            -> 299 * Math.PI / 1800 - x,
                            0, 299 * Math.PI / 1800,
                            y -> 299 * Math.PI / 1800 - y, 0, 299 * Math.PI / 1800, pixelWriter, lineColor, false);

            // IMPORTANT: This is the line x + y = 28 //george
            drawObliqueLine(x
                            -> 280 * Math.PI / 1800 - x,
                            0, 280 * Math.PI / 1800,
                            y -> 280 * Math.PI / 1800 - y, 0, 280 * Math.PI / 1800, pixelWriter, lineColor, false);

            // IMPORTANT: This is the line x + y = 30 //george
            drawObliqueLine(x
                            -> 300 * Math.PI / 1800 - x,
                            0, 300 * Math.PI / 1800,
                            y -> 300 * Math.PI / 1800 - y, 0, 300 * Math.PI / 1800, pixelWriter, lineColor, false);

            // IMPORTANT: This is the line x + y = 34 //george
            drawObliqueLine(x
                            -> 340 * Math.PI / 1800 - x,
                            0, 340 * Math.PI / 1800,
                            y -> 340 * Math.PI / 1800 - y, 0, 340 * Math.PI / 1800, pixelWriter, lineColor, false);

            // IMPORTANT: This is the line x + y = 35 //george dec 28,2020
            drawObliqueLine(x
                            -> 350 * Math.PI / 1800 - x,
                            0, 350 * Math.PI / 1800,
                            y -> 350 * Math.PI / 1800 - y, 0, 350 * Math.PI / 1800, pixelWriter, lineColor, false);

            // IMPORTANT: This is the line x + y = 27.225 //george dec 28,2020
            drawObliqueLine(x
                            -> 27225  * Math.PI / 180000 - x,
                            0, 27225 * Math.PI / 180000,
                            y -> 27225 * Math.PI / 180000 - y, 0, 27225 * Math.PI / 180000, pixelWriter, lineColor, false);

            // IMPORTANT: This is the line x + y = 24.75 //george dec 28,2020
            drawObliqueLine(x
                            -> 2475  * Math.PI / 18000 - x,
                            0, 2475 * Math.PI / 18000,
                            y -> 2475 * Math.PI / 18000 - y, 0, 2475 * Math.PI / 18000, pixelWriter, lineColor, false);

            // IMPORTANT: This is the line x + y = 24.5 //george dec 28,2020
            drawObliqueLine(x
                            -> 245  * Math.PI / 1800 - x,
                            0, 245 * Math.PI / 1800,
                            y -> 245 * Math.PI / 1800 - y, 0, 245 * Math.PI / 1800, pixelWriter, lineColor, false);


            // IMPORTANT: This is the line x + y = 25 //george dec 28,2020
            drawObliqueLine(x
                            -> 25  * Math.PI / 180 - x,
                            0, 25 * Math.PI / 180,
                            y -> 25 * Math.PI / 180 - y, 0, 25 * Math.PI / 180, pixelWriter, lineColor, false);

            // IMPORTANT: This is the line x + y = 44.9976 //george
            drawObliqueLine(x
                            -> 449976 * Math.PI / 1800000 - x,
                            0, 449976 * Math.PI / 1800000,
                            y -> 449976 * Math.PI / 1800000 - y, 0, 449976 * Math.PI / 1800000, pixelWriter, lineColor, false);

            // IMPORTANT: This is the line x + y = 45 //george
            drawObliqueLine(x
                            -> 450 * Math.PI / 1800 - x,
                            0, 450 * Math.PI / 1800,
                            y -> 450 * Math.PI / 1800 - y, 0, 450 * Math.PI / 1800, pixelWriter, lineColor, false);

            // IMPORTANT: This is the line x + y = 45.0024 //george
            drawObliqueLine(x
                            -> 450024 * Math.PI / 1800000 - x,
                            0, 450024 * Math.PI / 1800000,
                            y -> 450024 * Math.PI / 1800000 - y, 0, 450024 * Math.PI / 1800000, pixelWriter, lineColor, false);




            // y = 12 degrees George dec 29,2020
            drawHorizontalLine(12*Math.PI / 180, 0, 12*Math.PI / 180, pixelWriter, lineColor, false);

            // y = 11.3 degrees George 2022
            drawHorizontalLine(113*Math.PI / 1800, 0, 113*Math.PI / 1800, pixelWriter, lineColor, false);

            // y = 11.28 degrees George 2022
            drawHorizontalLine(1128*Math.PI / 18000, 0, 1128*Math.PI / 18000, pixelWriter, lineColor, false);

            // y = 11.26 degrees George 2022
            drawHorizontalLine(1126*Math.PI / 18000, 0, 1126*Math.PI / 18000, pixelWriter, lineColor, false);

            // y = 10.7 degrees George 2022
            drawHorizontalLine(107*Math.PI / 1800, 0, 107*Math.PI / 1800, pixelWriter, lineColor, false);

            // y = 10.65 degrees George 2022
            drawHorizontalLine(1065*Math.PI / 18000, 0, 1065*Math.PI / 18000, pixelWriter, lineColor, false);

            // y = 10.6 degrees George 2022
            drawHorizontalLine(106*Math.PI / 1800, 0, 106*Math.PI / 1800, pixelWriter, lineColor, false);

            // y = 10.5 degrees George 2022
            drawHorizontalLine(105*Math.PI / 1800, 0, 105*Math.PI / 1800, pixelWriter, lineColor, false);

            // y = 10.3 degrees George 2022
            drawHorizontalLine(103*Math.PI / 1800, 0, 103*Math.PI / 1800, pixelWriter, lineColor, false);

            // y = 10.2 degrees George 2022
            drawHorizontalLine(102*Math.PI / 1800, 0, 102*Math.PI / 1800, pixelWriter, lineColor, false);

            // y = 10 degrees George 2022
            drawHorizontalLine(10*Math.PI / 180, 0, 10*Math.PI / 180, pixelWriter, lineColor, false);

            // y = 11.335 degrees George 2022
            drawHorizontalLine(11335*Math.PI / 180000, 0, 11335*Math.PI / 180000, pixelWriter, lineColor, false);

            // y = 11.31 degrees George 2022
            drawHorizontalLine(1131*Math.PI / 18000, 0, 1131*Math.PI / 18000, pixelWriter, lineColor, false);

            // x = 10 degrees George dec 21,2020
            drawVerticalLine(1*Math.PI / 18, 1*Math.PI / 18, 576 * Math.PI / 1800, pixelWriter, lineColor, false);

            // x = 11 degrees George dec 21,2020
            drawVerticalLine(11*Math.PI / 180, 11*Math.PI / 180, 566 * Math.PI / 1800, pixelWriter, lineColor, false);

            // x = 12 degrees George dec 21,2020
            drawVerticalLine(Math.PI / 15, Math.PI / 15, 556 * Math.PI / 1800, pixelWriter, lineColor, false);

            // x = 12 degrees George may 25,2020 this one doesn't reach 112.4
            //drawVerticalLine(Math.PI / 15, Math.PI / 15, 37 * Math.PI / 120, pixelWriter, lineColor, false);

            // x = 1 degrees George may 25,2020
            drawVerticalLine(Math.PI / 180, Math.PI / 180, Math.PI / 2, pixelWriter, lineColor, false);

            // x = .5 degrees George may 25,2020
            drawVerticalLine(Math.PI / 360, Math.PI / 360, Math.PI / 2, pixelWriter, lineColor, false);

            // x = .425 degrees George may 25,2020
            drawVerticalLine(425*Math.PI / 180000, 425*Math.PI / 180000, Math.PI / 2, pixelWriter, lineColor, false);

            // x = .3625 degrees George may 25,2020
            drawVerticalLine(3625*Math.PI / 1800000, 3625*Math.PI / 1800000, Math.PI / 2, pixelWriter, lineColor, false);

            // x = .35 degrees George may 25,2020
            drawVerticalLine(35*Math.PI / 18000, 35*Math.PI / 18000, Math.PI / 2, pixelWriter, lineColor, false);

            // x = .325 degrees George may 25,2020
            drawVerticalLine(325*Math.PI / 180000, 325*Math.PI / 180000, Math.PI / 2, pixelWriter, lineColor, false);

            // x = .295 degrees George may 25,2020
            drawVerticalLine(295*Math.PI / 180000, 295*Math.PI / 180000, Math.PI / 2, pixelWriter, lineColor, false);

            // x = .26 degrees George may 25,2020
            drawVerticalLine(26*Math.PI / 18000, 26*Math.PI / 18000, Math.PI / 2, pixelWriter, lineColor, false);

            // x = .23 degrees George may 25,2020
            drawVerticalLine(23*Math.PI / 18000, 23*Math.PI / 18000, Math.PI / 2, pixelWriter, lineColor, false);

            // x = .18 degrees George may 25,2020
            drawVerticalLine(18*Math.PI / 18000, 18*Math.PI / 18000, Math.PI / 2, pixelWriter, lineColor, false);

            // x = .13 degrees George may 25,2020
            drawVerticalLine(13*Math.PI / 18000, 13*Math.PI / 18000, Math.PI / 2, pixelWriter, lineColor, false);
        }
        // Note: copy and paste the following to make another star square
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                final double rx = map.radianX(i);
                final double ry = map.radianY(j);
                if (starPatternArea.contains(rx, ry)) {
                    pixelWriter.setColor(i, j, Color.GOLD);//george may 20,2020 this gives the infinite corner
                }
            }
        }

        // 15, 30, 1 radian radius
//        for (int i = 0; i < SIZE; i++) {
//            for (int j = 0; j < SIZE; j++) {
//                final double rx = map.radianX(i);
//                final double ry = map.radianY(j);
//                if (starPatternArea2.contains(rx, ry)) {
//                    pixelWriter.setColor(i, j, Color.BLUE);//george may 20,2020 this gives the infinite corner
//                }
//            }
//        }

        return image;
    }

    private void renderPolygon(final ConvexPolygon poly, final WritableImage image, final Color color) {
        final PixelWriter pixelWriter = image.getPixelWriter();

        final ImmutableList<Point> vertices = poly.vertices;
        final int size = vertices.size();

        for (int i = 0; i < size; ++i) {
            final Point a = vertices.get(i);
            final Point b = vertices.get((i + 1) % size);

            // horizontal
            if (a.y == b.y) {
                drawHorizontalLine(
                		a.y, Math.min(a.x, b.x), Math.max(a.x, b.x), pixelWriter, color, false);
            }
            // vertical
            else if (a.x == b.x) {
                drawVerticalLine(
                		a.x, Math.min(a.y, b.y), Math.max(a.y, b.y), pixelWriter, color, false);
            }
            // diagonal
            else {
                final double slopeY = (b.y - a.y) / (b.x - a.x);
                final DoubleUnaryOperator funcY = x -> slopeY * (x - a.x) + a.y; // y(x)

                final double slopeX = (b.x - a.x) / (b.y - a.y);
                final DoubleUnaryOperator funcX = y -> slopeX * (y - a.y) + a.x; // x(y)

                drawObliqueLine(funcY, Math.min(a.x, b.x), Math.max(a.x, b.x), funcX,
                                Math.min(a.y, b.y), Math.max(a.y, b.y), pixelWriter, color, false);
            }
        }
    }


    private void renderUnstable(final Storage.Unstable unstable, final PixelWriter pixelWriter,
                                final Rectangle viewRectangle, final Color initColor) {
        final Color color;
//        // this is the standard color:
//        if (initColor.equals(new Color(
//                Color.WHITE.getRed(), Color.WHITE.getGreen(), Color.WHITE.getBlue(), 0.5))) {
//            color = Color.RED;
//        } else {
//            color = initColor;
//        }

        color = Color.BLACK;

        final List<Point> points = new ArrayList<>();

        final int xCoeff = unstable.constraint.coeff(XYEta.X);
        final int yCoeff = unstable.constraint.coeff(XYEta.Y);
        final int etaCoeff = unstable.constraint.coeff(XYEta.Eta);

        final int startPixelX = 0;
        final int startPixelY = 0;
        final int endPixelX = SIZE;
        final int endPixelY = SIZE;

        if (xCoeff == 0) {
            // horizontal line
            // solve for y
            final double y = -(double) etaCoeff / yCoeff * Math.PI / 2;

            if (viewRectangle.intervalY.contains(y)) {
                // now we iterate over the vertical lines
                for (int i = startPixelX; i < endPixelX; i += 1) {
                    final double radX = map.radianX(i);
                    final Point point = Point.create(radX, y);
                    points.add(point);
                }
            }
        } else if (yCoeff == 0) {
            // vertical line
            // solve for x
            final double x = -(double) etaCoeff / xCoeff * Math.PI / 2;

            if (viewRectangle.intervalX.contains(x)) {
                // now iterate over the horizontal lines
                for (int i = startPixelY; i < endPixelY; i += 1) {
                    final double radY = map.radianY(i);
                    final Point point = Point.create(x, radY);
                    points.add(point);
                }
            }
        } else {
            // oblique line
            // we can solve for x and y
            final DoubleUnaryOperator x = radY
                -> - (double) yCoeff / xCoeff * radY - (double) etaCoeff / (double) xCoeff * Math.PI / 2;

            final DoubleUnaryOperator y = radX
                -> - (double) xCoeff / yCoeff * radX - (double) etaCoeff / (double) yCoeff * Math.PI / 2;

            for (int i = startPixelX; i < endPixelX; i += 1) {
                final double radX = map.radianX(i);
                final double radY = y.applyAsDouble(radX);
                // need to make sure intersection is in the viewing box
                if (viewRectangle.intervalY.contains(radY)) {
                    final Point point = Point.create(radX, radY);
                    points.add(point);
                }
            }

            for (int i = startPixelY; i < endPixelY; i += 1) {
                final double radY = map.radianY(i);
                final double radX = x.applyAsDouble(radY);
                if (viewRectangle.intervalX.contains(radX)) {
                    final Point point = Point.create(radX, radY);
                    points.add(point);
                }
            }

            // now sort the points in lexicographical order
            final Comparator<Point> ordering = (a, b) -> {
                final double ax = a.x;
                final double ay = a.y;

                final double bx = b.x;
                final double by = b.y;

                if ((ax == bx) && (ay == by)) {
                    // a == b
                    return 0;
                } else if ((ax < bx) || (ax == bx && ay < by)) {
                    // a < b
                    return -1;
                } else {
                    // a > b
                    return 1;
                }
            };

            points.sort(ordering);
        }

        double startX = unstable.lineSegment.start.x;
        double endX = unstable.lineSegment.end.x;
        double startY = unstable.lineSegment.start.y;
        double endY = unstable.lineSegment.end.y;

        if (startX == endX) {
            startX = startX - 0.01;
            endX = endX + 0.01;
        } else if (startY == endY) {
            startY = startY - 0.01;
            endY = endY + 0.01;
        }

        final Rectangle boundingRectangle = Rectangle.create(startX, endX, startY, endY);

        for (int i = 0; i < points.size() - 1; i += 1) {
            final Point point = points.get(i);
            final Point nextPoint = points.get(i + 1);

            final Point midPoint = point.add(nextPoint).scale(0.5);
            final double rx = midPoint.x;
            final double ry = midPoint.y;

            if ((boundingRectangle.contains(rx, ry) && unstable.isPositive(rx, ry))) {
                final int px = (int) map.pixelX(rx);
                final int py = (int) map.pixelY(ry);
                try {
                    pixelWriter.setColor(px, py, color);
                    pixelWriter.setColor(px + 1, py, color);
                    pixelWriter.setColor(px - 1, py, color);
                    pixelWriter.setColor(px, py + 1, color);
                    pixelWriter.setColor(px, py - 1, color);
                } catch (final IndexOutOfBoundsException e) {
                }
            }
        }
    }

    private void renderRegion(final Storage region, final WritableImage image, final Color color) {
        final PixelReader pixelReader = image.getPixelReader();
        final PixelWriter pixelWriter = image.getPixelWriter();

        // now we create a rectangle that describes the current viewing screen
        final Rectangle viewRectangle = map.getViewRectangle();

        // ideally we would just iterate over all the points within the intersection
        // if they intersect, calculate the points we need to calculate
        if (region.intersects(viewRectangle)) {
            if (CodeType.isStable(region.type)) {
                final Storage.Stable stable = (Storage.Stable) region;

                // Determine the color of each pixel in a specified row
                for (int readY = 0; readY < SIZE; readY += 1) {
                    final double ry = map.radianY(readY + 0.5);
                    for (int readX = 0; readX < SIZE; readX += 1) {
                        final double rx = map.radianX(readX + 0.5);

                        final Color pixelColor = pixelReader.getColor(readX, readY);
                        // if it's not colored our color already, then let's see if we can color it
                        if (pixelColor != color && !color.equals(Color.TRANSPARENT)) {
                            // if the point is inside the bounding rectangle and is positive

                            final Location location = stable.polygon.location(rx, ry);
                            if (location == Location.INSIDE) {
                                if (stable.isPositive(rx, ry)) {
                                    pixelWriter.setColor(readX, readY, color);
                                }

                            }
                        }
                    }
                }

            } else {
                renderUnstable((Storage.Unstable) region, pixelWriter, viewRectangle, color.invert());
            }
        }
    }

    private static Optional<Line> smartLine(final double sX, final double sY, final double eX, final double eY) {
        final boolean[] problems = {sX > 0 && sX < SIZE && sY > 0 && sY < SIZE,
                                    eX > 0 && eX < SIZE && eY > 0 && eY < SIZE};

        if (problems[0] && problems[1]) {
            // the line is entirely inside
            return Optional.of(new Line(sX, sY, eX, eY));
        } else if (problems[0]) {
            // the start is inside
            final Point direct = Point.unit(Point.create(sX - eX, sY - eY));
            final Point start = Point.create(sX, sY);
            final Point lineEnd = onScreenLine(start, direct);

            return Optional.of(new Line(sX, sY, lineEnd.x, lineEnd.y));

        } else if (problems[1]) {
            // the end is inside
            final Point direct = Point.unit(Point.create(eX - sX, eY - sY));
            final Point start = Point.create(eX, eY);
            final Point lineStart = onScreenLine(start, direct);

            return Optional.of(new Line(lineStart.x, lineStart.y, eX, eY));
        } else {
            new ErrorAlert(new RuntimeException("Error when making the pan line")).showAndWait();
            return Optional.empty();
        }
    }

    // if you have a line which has one end on screen, you can use this to find the onscreen part
    // of that line
    private static Point onScreenLine(final Point start, final Point direct) {
        final Point end;
        final double OFFSET = 0.00000000005;

        final double angle = FastMath.atan2(direct.y, direct.x);

        if (Math.abs(angle - Math.PI) < OFFSET || Math.abs(angle + Math.PI) < OFFSET) {
            end = Point.create(SIZE, start.y);

        } else if (Math.abs(angle - Math.PI / 2) < OFFSET) {
            end = Point.create(start.x, 0);

        } else if (Math.abs(angle) < OFFSET) {
            end = Point.create(0, start.y);

        } else if (Math.abs(angle + Math.PI / 2) < OFFSET) {
            end = Point.create(start.x, SIZE);

        } else if (-Math.PI < angle && angle < -Math.PI / 2) {
            final double arg0 = (SIZE - start.y) / FastMath.sin(angle);
            final double arg1 = (SIZE - start.x) / FastMath.cos(angle);
            final double len = trueMin(arg0, arg1);
            end = start.add(direct.scale(len));

        } else if (-Math.PI / 2 < angle && angle < 0) {
            final double arg0 = (SIZE - start.y) / FastMath.sin(angle);
            final double arg1 = start.x / FastMath.cos(angle);
            final double len = -trueMin(Math.abs(arg0), Math.abs(arg1));
            end = start.add(direct.scale(len));

        } else if (0 < angle && angle < Math.PI / 2) {
            final double arg0 = start.y / FastMath.sin(angle);
            final double arg1 = start.x / FastMath.cos(angle);
            final double len = -trueMin(Math.abs(arg0), Math.abs(arg1));
            end = start.add(direct.scale(len));

        } else if (Math.PI / 2 < angle && angle < Math.PI) {
            final double arg0 = start.y / FastMath.sin(angle);
            final double arg1 = (SIZE - start.x) / FastMath.cos(angle);
            final double len = -trueMin(Math.abs(arg0), Math.abs(arg1));
            end = start.add(direct.scale(len));

        } else {
            end = start;
            new ErrorAlert(new RuntimeException("Something went wrong in"
            		+ " 'onScreenLine' method")).showAndWait();
        }

        return end;
    }

    private static double trueMin(final double a, final double b) {
        final double result;
        if (Math.abs(a) < Math.abs(b)) {
            result = a;
        } else {
            result = b;
        }
        return result;
    }

    public static Optional<Storage> loadStorage(final CodePair codePair) {

        final Optional<CodeInfo> opt = Wrapper.loadCodeInfo(codePair);

        if (!opt.isPresent()) {
            return Optional.empty();
        }

        final CodeInfo codeInfo = opt.get();

        final MutableList<Equation> eqs = new FastList<>();
        for (final SinEquation sin : codeInfo.sinEquations) {
            eqs.add(sin);
        }

        for (final CosEquation cos : codeInfo.cosEquations) {
            eqs.add(cos);
        }

        final CodeType type = codePair.sequence.type();

        final Storage storage;

        if (CodeType.isStable(type)) {

            final ConvexPolygon polygon = ConvexPolygon.create(codeInfo.points);
            storage = new Storage.Stable(codePair.sequence, codePair.angles, type, eqs.toImmutable(), polygon);

        } else {

            final LineSegment lineSegment = LineSegment.create(codeInfo.points);
            storage = new Storage.Unstable(codePair.sequence, codePair.angles, type, eqs.toImmutable(), lineSegment);
        }

        return Optional.of(storage);
    }

    public static int getCodeLength(String codeNumbers){
        return codeNumbers.split(" ").length;
    }

    public static int getCodeSum(String codeNumbers){
        int total = 0;
        for (String number : codeNumbers.split(" ")) {
            total += Integer.parseInt(number);
        }
        return total;
    }

    private void checkThisSquareAction(CodePair codePair){
        if(codePair == null){
            final Alert alert = new Alert(AlertType.CONFIRMATION);
            alert.setTitle("No Code Selected");
            alert.setHeaderText("There is no code selected from the triple to check the square with");
            Label content = new Label("To check a square with a triple you must first select a code in the draw menu.  " +
                    "The code that was last selected is the code that will be displayed in the check this square menu.");
            content.setFont(new Font("Times new roman", 15));
            content.setWrapText(true);
            alert.getDialogPane().setContent(content);
            final Optional<ButtonType> response = alert.showAndWait();
            if (response.isPresent()){
                return;
            }
        }

        if (selectedRect != null) {
            try {
                final CodeSequence codeSeq = codePair.sequence;
                final InitialAngles angles = codePair.angles;

                final String[] radiusCenter = selectedRect.getCenter();

                final StringBuilder builder = new StringBuilder();
                String string = "Code Sequence = " + codeSeq.toString() + "\nInitial Angles = " + angles.toString() +
                        "\nCenter = " + radiusCenter[1] + "\nRadius = " + radiusCenter[0] +
                        "\nRadius (radians) = " + selectedRect.center() +
                        "\nMagnifications = " + (selectedRect.getDenom() -1) +
                        "\nCovered = 1";
                builder.append(string);
                builder.append("\n\n");

                checkOneInfo.setText(builder.toString());

                checkOneWrap.getChildren().clear();
                checkOneWrap.getChildren().add(checkOneInfo);
                VBox.setVgrow(checkOneInfo, Priority.ALWAYS);

                final Alert alert = new Alert(AlertType.CONFIRMATION);
                alert.setTitle("Check This Square");
                alert.setHeaderText("Would you like to show the exact equations?");
                Label content = new Label("Depending on the code sequence being checked it may crash due to running out of memory because of the amount" +
                        " of exact equations there are.  Do you wish to proceed?");
                content.setFont(new Font("Times new roman", 15));
                content.setWrapText(true);
                alert.getDialogPane().setContent(content);
                final Optional<ButtonType> response = alert.showAndWait();
                if (response.isPresent() && response.get() == ButtonType.OK){
                    final Optional<CodeInfo> info = Wrapper.loadCodeInfo(new CodePair(codeSeq, angles));
                    builder.append("Equations of Exact Region\n");

                    for (final Equation equation : info.get().sinEquations) {
                        builder.append(equation);
                        builder.append("\n\n");
                    }

                    for (final Equation equation : info.get().cosEquations) {
                        builder.append(equation);
                        builder.append("\n\n");
                    }

                    checkOneInfo.clear();
                    checkOneInfo.setText(builder.toString());
                }

            } catch (Exception ex) {
                new ErrorAlert(ex).show();
            }

        } else {
            final Alert alert = new Alert(AlertType.INFORMATION);

            alert.setTitle("Cover");
            alert.setHeaderText("No square selected");
            alert.setContentText("Please select a square before pressing this.");

            alert.showAndWait();
        }
    }

    public static MutableList<Point> parsePolygonString(String polygonString){
        MutableList<Point> points = new FastList<>();
        double xCoord = 0;
        double yCoord = 0;
        for (String point : polygonString.split("~")) {
            if (!point.equals(" ")){
                String[] termsList = point.split(", ");
                String[] firstTerm = termsList[0].split("/");
                xCoord = Double.parseDouble(firstTerm[0]) / Double.parseDouble(firstTerm[1]);
                String[] secondTerm = termsList[1].split("/");
                yCoord = Double.parseDouble(secondTerm[0]) / Double.parseDouble(secondTerm[1]);

                points.add(Point.create(xCoord * 90, yCoord * 90));
            }
        }

        return points;
    }
}
