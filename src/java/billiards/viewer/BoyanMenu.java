package billiards.viewer;

import billiards.codeseq.ClassifiedCodeSequence;
import billiards.codeseq.CodeSequence;
import billiards.codeseq.CodeType;
import billiards.codeseq.CodeTypeCollection;
import billiards.geometry.Vector2;
import billiards.vary.Vary;
import billiards.vary.Vary3;
import billiards.vary.Vary4;
import billiards.vary.VaryCS;
import billiards.codeseq.CodeTypeSet;
import billiards.codeseq.CodeTypeSet.CodeTypeSetBuilder;
import billiards.database.Database;

import org.apache.commons.math3.util.FastMath;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.api.set.sorted.MutableSortedSet;
import org.eclipse.collections.impl.list.mutable.FastList;
import org.eclipse.collections.impl.set.mutable.UnifiedSet;
import org.eclipse.collections.impl.set.sorted.mutable.TreeSortedSet;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

public class BoyanMenu {

	// when using autovary3, if you want it to print all codes it finds, switch
	// false to true in
	// the next line:
	private static final boolean printAll = false;

	// setting this to true makes it so that when you Vary3, it prints out the codes
	// organized by
	// their type:
	private static final boolean splitUp = false;

	final VBox wrapper = new VBox();

	final Button middleVaryButton = new Button();
	final Button vary3Btn = new Button();
	final Button vary3BBtn = new Button();

	final Button vary4Btn = new Button();
	final Button autoVaryBtn = new Button();
	final Label sideSpacingLbl = new Label();
	final Label movesLbl = new Label();

	final TextField maxMovesText = new TextField();
	final TextField minMovesText = new TextField();
	final TextField shotsText = new TextField();

	final public TextField varyX1Text = new TextField();
	final TextField varyX2Text = new TextField();
	final TextField varyX3Text = new TextField();
	final public TextField varyY1Text = new TextField();
	final TextField varyY2Text = new TextField();
	final TextField varyY3Text = new TextField();
	final TextField line1CutText = new TextField();
	final TextField line2CutText = new TextField();

	final TextField autoCycleText = new TextField();
	final TextField cycleStepText = new TextField();
	final TextField autoIterText = new TextField();
	final TextField autoStepText = new TextField();

	final CheckBox allPixCheckBox = new CheckBox();

	final RadioButton line1RDB = new RadioButton();
	final RadioButton line2RDB = new RadioButton();
	final RadioButton line3RDB = new RadioButton();
	final ToggleGroup boyanGroup = new ToggleGroup();

	final CheckBox OSOcb = new CheckBox();
	final CheckBox OSNOcb = new CheckBox();
	final CheckBox ONScb = new CheckBox();
	final CheckBox CScb = new CheckBox();
	final CheckBox CNScb = new CheckBox();
	final CheckBox Triplescb = new CheckBox();

	// george june12,2019 start
	final CheckBox OSO2cb = new CheckBox();
	final CheckBox OSNO2cb = new CheckBox();
	final CheckBox ONS2cb = new CheckBox();
	final CheckBox CS2cb = new CheckBox();
	final CheckBox CNS2cb = new CheckBox();
	// george june12,2019 end

	final CheckBox varyOnePoint = new CheckBox();

	final Label hitsLabel = new Label();

	final CheckBox buildPolyCheckBox = new CheckBox();

	final TextField maxPrinting = new TextField();

	public BoyanMenu(final Button cycleVaryButton, final Button middleVaryLButton, final Button polyAutoBtn,
			final Button varyLBtn,
			final Button autoPolyVaryBtn, final TextField lineStartField, final TextField lineStepField,
			final TextField lineEndField, final Button findPointsBtn,
			final Button superPolyVaryBtn, final CheckBox superAutoCb, final double TipOpenDelay,
			final double TipCloseDelay) {

		Utils.setupCustomTooltipBehavior((int) (TipOpenDelay * 1000), (int) (TipCloseDelay * 1000), 200);

		sideSpacingLbl.setText("Shots");

		movesLbl.setText("SideSum");

		maxMovesText.setPrefWidth(60);
		maxMovesText.setTooltip(Utils.toolTip("The maximum side sum to search for"));
		maxMovesText.setText("200");
		minMovesText.setPrefWidth(60);
		minMovesText.setTooltip(Utils.toolTip("The minimum side sum to search for"));
		minMovesText.setText("0");
		shotsText.setPrefWidth(35);// george set 40 instead of 60
		shotsText.setText("4");// george set 4 instead of 8
		shotsText.setTooltip(Utils.toolTip("How many places along the base it should shoot"));

		varyX1Text.setPrefWidth(155);
		varyX1Text.setText("30");
		varyX1Text.setTooltip(Utils.toolTip("The first angle in your triangle"));
		varyY1Text.setPrefWidth(155);
		varyY1Text.setText("40");
		varyY1Text.setTooltip(Utils.toolTip("The second angle in your triangle"));
		// varyX2Text.setPrefWidth(155);
		// varyY2Text.setPrefWidth(155);
		// varyX2Text.setText("0");
		// varyY2Text.setText("0");

		final TextField[] fields = { varyX2Text, varyY2Text, varyX3Text, varyY3Text };
		for (final TextField field : fields) {
			field.setPrefWidth(155);
			field.setText("0");
		}

		line1CutText.setPrefWidth(40);
		line1CutText.setText("0");
		line1CutText.setStyle(Utils.hex(Color.LIGHTGREEN));
		line2CutText.setPrefWidth(40);
		line2CutText.setText("0");
		line2CutText.setStyle(Utils.hex(Color.LIGHTGREEN));
		line1RDB.setToggleGroup(boyanGroup);
		line1RDB.setSelected(true);
		line2RDB.setToggleGroup(boyanGroup);
		line3RDB.setToggleGroup(boyanGroup);

		hitsLabel.setText("" + 1);

		OSOcb.setText("OSO");
		OSOcb.setSelected(true);
		OSOcb.setTooltip(Utils.toolTip(
				"The Vary3 functions will look for codes only of chosen types"));
		OSNOcb.setText("OSNO");
		OSNOcb.setSelected(true);
		OSNOcb.setTooltip(Utils.toolTip(
				"The Vary3 and Load Directory functions will look for codes only of chosen types"));
		CScb.setText("CS");
		CScb.setSelected(true);
		CScb.setTooltip(Utils.toolTip(
				"The Vary3 and Load Directory functions will look for codes only of chosen types"));
		CNScb.setText("CNS");
		CNScb.setTooltip(Utils.toolTip(
				"The Vary3 and Load Directory functions will look for codes only of chosen types"));
		ONScb.setText("ONS");
		ONScb.setTooltip(Utils.toolTip(
				"The Vary3 and Load Directory functions will look for codes only of chosen types"));
		Triplescb.setText("Triples");
		Triplescb.setTooltip(Utils.toolTip(
				"The Load Directory function will look for codes only of chosen types"));
		// george june12,2019 start
		OSO2cb.setText("OSO2");
		OSO2cb.setSelected(true);
		OSO2cb.setTooltip(Utils.toolTip(
				"The Vary3 functions will look for codes only of chosen types"));
		OSNO2cb.setText("OSNO2");
		OSNO2cb.setSelected(true);
		OSNO2cb.setTooltip(Utils.toolTip(
				"The Vary3 functions will look for codes only of chosen types"));
		CS2cb.setText("CS2");
		CS2cb.setSelected(true);
		CS2cb.setTooltip(Utils.toolTip(
				"The Vary3 functions will look for codes only of chosen types"));
		CNS2cb.setText("CNS2");
		CNS2cb.setTooltip(Utils.toolTip(
				"The Vary3 functions will look for codes only of chosen types"));
		ONS2cb.setText("ONS2");
		ONS2cb.setTooltip(Utils.toolTip(
				"The Vary3 functions will look for codes only of chosen types"));
		// george june12,2019 end
		varyOnePoint.setText("Vary1Pt");
		varyOnePoint.setTooltip(Utils.toolTip("When using varyL, if this is "
				+ "selected, it will shoot at that point and draw/write to the "
				+ "vary one point file the top X specified codes."));

		autoCycleText.setText("3");
		autoCycleText.setTooltip(Utils.toolTip("For PolyVary, the number of subdivisions done"));
		autoCycleText.setPrefWidth(40);

		cycleStepText.setText("1");
		cycleStepText.setTooltip(Utils.toolTip("For SuperPolyVary, the change in subdivisions each iteration"));
		cycleStepText.setPrefWidth(40);

		autoIterText.setText("0");
		autoIterText.setPrefWidth(40);
		autoIterText.setTooltip(Utils.toolTip("For autoVary3, the number of side sum iterations done"));
		autoStepText.setText("50");
		autoStepText.setPrefWidth(60);
		autoStepText.setTooltip(Utils.toolTip("For autoVary3, the step when doing side sum iterations"));

		allPixCheckBox.setText("All Pixels");
		allPixCheckBox.setTooltip(Utils.toolTip("For autoVary3, if this is on, no subdivisions are done."
				+ " Instead, each individual pixel is considered"));

		vary3Btn.setText("Vary");
		vary3Btn.setTooltip(Utils.toolTip("Search for codes at points specified above. See Instructions"
				+ " for how to use this"));
		Utils.colorButton(vary3Btn, Color.SKYBLUE, Color.GOLD);
		vary3Btn.setOnAction(event -> varyAction("Vary", "vary3.txt", false));

		// Zhao Yu Li, May 05, 2025.
		// A new button that performs the same computations as Vary, but only prints the
		// middle code of each
		// (code type, code length) group.
		middleVaryButton.setText("LiMV");
		middleVaryButton
				.setTooltip(Utils.toolTip("Middle Vary. Search for codes at points specified above. But for codes of " +
						"the same type (i.e. CS, OSO, OSNO, etc.) and the same code length, only print the middle one."));
		Utils.colorButton(middleVaryButton, Color.SKYBLUE, Color.GOLD);
		middleVaryButton.setOnAction(event -> varyAction("Middle Vary", "middleVary3.txt", true));

		vary3BBtn.setText("Vary3B");
		vary3BBtn.setTooltip(Utils.toolTip("Search for codes at points specified above. See Instructions"
				+ " for how to use this"));
		Utils.colorButton(vary3BBtn, Color.SKYBLUE, Color.GOLD);
		vary3BBtn.setOnAction(event -> {

			final long startTime = System.currentTimeMillis();// george june 12,2019 added in !CS2cb.isSelected() &&
																// !CNS2cb.isSelected() && !ONS2cb.isSelected() &&
																// !OSNO2cb.isSelected() && !OSO2cb.isSelected()
			if (!CS2cb.isSelected() && !CNS2cb.isSelected() && !ONS2cb.isSelected() && !OSNO2cb.isSelected()
					&& !OSO2cb.isSelected()) {
				final Alert alert = new Alert(AlertType.ERROR);

				alert.setTitle("Vary");
				alert.setHeaderText("No CodeTypes");
				alert.setContentText("Please select at least one codetype.");
				alert.showAndWait();
			} else {
				final int shots = Integer.parseInt(shotsText.getText());
				final int max = Integer.parseInt(maxMovesText.getText());
				final int min = Integer.parseInt(minMovesText.getText());

				final ExecutorService executor = Executors.newFixedThreadPool(Utils.numThreads);

				System.out.println(String.format(
						"//------------------------- Vary3B %d shots at %d to %d moves -------------------------//",
						shots, min, max));

				final Task<MutableSortedSet<ClassifiedCodeSequence>> varyTask = new Task<MutableSortedSet<ClassifiedCodeSequence>>() {

					@Override
					protected MutableSortedSet<ClassifiedCodeSequence> call() throws Exception {
						return varyTriangles(2, executor);
					}

				};

				final Progress progress = new Progress(varyTask);
				final Thread varyThread = new Thread(varyTask);

				varyTask.setOnSucceeded(success -> {
					try {
						MutableSortedSet<ClassifiedCodeSequence> allCodes = varyTask.get();
						allCodes.forEach(seq -> Database.saveToDatabase(seq, "garbage"));
						hitsLabel.setText("" + allCodes.size());
						printCodes(allCodes, "vary3B.txt", true, true, allCodes.size());
					} catch (InterruptedException | ExecutionException e) {
						throw new RuntimeException(e);
					}
					final long endTime = System.currentTimeMillis();
					System.out.println("Time: " + (endTime - startTime));
					System.out.println("Time: " + Utils.timeConvert(endTime - startTime));
					executor.shutdown();
					progress.close();
				});

				varyTask.setOnCancelled(cancelled -> {
					System.out.println("// Vary3B Cancelled");
					varyThread.interrupt();
					executor.shutdownNow();
					progress.close();
				});
				varyTask.setOnFailed(fail -> {
					System.out.println("// Vary3B failed");
					executor.shutdown();
					progress.close();
				});

				varyThread.start();

				progress.show();
			}

		});

		vary4Btn.setText("V4");
		Utils.colorButton(vary4Btn, Color.SKYBLUE, Color.GOLD);
		vary4Btn.setTooltip(Utils.toolTip("Search for all codes at points specified above. See Instructions"
				+ " for how to use this"));
		vary4Btn.setOnAction(event -> {

			final long startTime = System.currentTimeMillis();
			if (!CScb.isSelected() && !CNScb.isSelected() && !ONScb.isSelected() && !OSNOcb.isSelected()
					&& !OSOcb.isSelected()) {
				final Alert alert = new Alert(AlertType.ERROR);

				alert.setTitle("Vary");
				alert.setHeaderText("No CodeTypes");
				alert.setContentText("Please select at least one codetype.");
				alert.showAndWait();
			} else {
				final int shots = Integer.parseInt(shotsText.getText());
				final int max = Integer.parseInt(maxMovesText.getText());
				final int min = Integer.parseInt(minMovesText.getText());

				if (max > 250) {
					final Alert alert = new Alert(AlertType.CONFIRMATION);

					alert.setTitle("Vary4");
					alert.setHeaderText("Vary4");
					alert.setContentText(
							"Vary4 is slow at this number of moves.\nDo you want to continue?");

					final Optional<ButtonType> response = alert.showAndWait();

					if (!response.isPresent() || response.get() != ButtonType.OK) {
						return;
					}
				}

				System.out.println(String.format(
						"//------------------------- Vary4 %d shots at %d to %d moves -------------------------//",
						shots, min, max));
				final ExecutorService executor = Executors.newFixedThreadPool(Utils.numThreads);

				final MutableSortedSet<ClassifiedCodeSequence> allCodes = varyTriangles(4, executor);
				printCodes(allCodes, "garbage.txt", true, false, allCodes.size());
			}
			final long endTime = System.currentTimeMillis();
			System.out.println("Time: " + (endTime - startTime));
			System.out.println("Time: " + Utils.timeConvert(endTime - startTime));

		});

		buildPolyCheckBox.setText("Make Poly");
		buildPolyCheckBox.setStyle(Utils.hex(Color.MISTYROSE));
		buildPolyCheckBox.setTooltip(Utils.toolTip("Use the console to construct a polygon. See "
				+ "instructions for how to use this"));
		buildPolyCheckBox.setOnAction(event -> {
			if (buildPolyCheckBox.isSelected()) {
				System.out.println("");
				System.out.println("+---------- make a polygon ----------+");

			} else {
				System.out.println("+------------------------------------+");
				System.out.println("");
			}
		});

		maxPrinting.setMaxWidth(50);
		maxPrinting.setText("1");
		maxPrinting.setTooltip(Utils.toolTip("Set the max number of codes to be printed from a set"
				+ " found by varyL or PolyVary"));

		final HBox varyLine1HBox = new HBox(10, line1RDB, varyX1Text, varyY1Text);
		varyLine1HBox.setPadding(new Insets(0, 10, 10, 0));
		varyLine1HBox.setAlignment(Pos.CENTER);

		final HBox varyLine2HBox =
				// new HBox(10, line2RDB, varyX2Text, varyY2Text, line1CutText);
				new HBox(10, line2RDB, varyX2Text, varyY2Text);

		varyLine2HBox.setPadding(new Insets(0, 10, 10, 0));
		varyLine2HBox.setAlignment(Pos.CENTER);

		final HBox varyLine3HBox = new HBox(10, line3RDB, varyX3Text, varyY3Text, line2CutText);
		varyLine3HBox.setPadding(new Insets(0, 10, 10, 0));
		varyLine3HBox.setAlignment(Pos.CENTER);

		// George May 17,2023 switch the positions of the bounds and AutoPolyVary
		// final HBox varyInfoHBox =
		// new HBox(10, minMovesText, maxMovesText, movesLbl, shotsText,
		// sideSpacingLbl);
		// varyInfoHBox.setPadding(new Insets(0, 10, 10, 0));
		// varyInfoHBox.setAlignment(Pos.CENTER);
		final HBox autoPolyVaryHBox = new HBox(10, autoPolyVaryBtn, lineStartField, lineStepField, lineEndField, findPointsBtn);
		autoPolyVaryHBox.setPadding(new Insets(0, 10, 10, 0));
		autoPolyVaryHBox.setAlignment(Pos.CENTER);

		final HBox codeTypesHBox = new HBox(10, CNScb, CScb, ONScb, OSNOcb, OSOcb); // , Triplescb);//george june
																					// 18,2019 replaced vary4Btn with
																					// vary3Btn
		codeTypesHBox.setPadding(new Insets(0, 10, 10, 0));
		codeTypesHBox.setAlignment(Pos.CENTER);

		// george june 12,2019 start
		// final HBox codeTypes2HBox =
		// new HBox(10, vary3BBtn, CNS2cb, CS2cb, ONS2cb, OSNO2cb, OSO2cb);
		final HBox codeTypes2HBox = new HBox();
		codeTypes2HBox.setPadding(new Insets(0, 10, 10, 0));
		codeTypes2HBox.setAlignment(Pos.CENTER);
		// george june 12,2019 end

		/*
		 * final HBox vary3HBox =
		 * new HBox(10, vary3Btn, varyLBtn, hitsLabel, autoVaryButton, autoCycleText,
		 * minPrinting,
		 * maxPrinting);
		 */ // , autoIterText, autoStepText //added minPrinting george june6,2019

		// Zhao Yu Li, May 06, 2025.
		// Moved buildPolyCheckBox from to Viewr.java.
		// Added new MiddleVaryL button.
		final HBox vary3HBox = new HBox(10, middleVaryLButton, varyLBtn, maxPrinting, polyAutoBtn, autoCycleText);
		// new HBox(10, vary4Btn, varyLBtn, hitsLabel, autoVaryButton, autoCycleText,
		// //george june 18,2019 replaced vary3Btn with vary4Btn
		// maxPrinting); //, autoIterText, autoStepText

		// new HBox(10, varyLBtn, hitsLabel, autoVaryButton, autoCycleText, //george
		// june 18,2019 replaced vary3Btn with vary4Btn
		// maxPrinting); //, autoIterText, autoStepText
		// new HBox(10, buildPolyCheckBox, varyLBtn, hitsLabel, autoCycleText, //george
		// june 18,2019 replaced vary3Btn with vary4Btn
		// maxPrinting); //, autoIterText, autoStepText

		final HBox newHBox = new HBox(3, middleVaryButton, vary3Btn, hitsLabel, minMovesText, maxMovesText, movesLbl,
				shotsText, sideSpacingLbl);
		newHBox.setPadding(new Insets(0, 10, 10, 0));
		newHBox.setAlignment(Pos.CENTER);

		// Zhao Yu Li, Aug 18, 2025.
		// Put "LiCycle" by "SuperLiLuVary".
		final HBox superHBox = new HBox(10, cycleVaryButton, superPolyVaryBtn, cycleStepText, superAutoCb);
		superHBox.setPadding(new Insets(0, 10, 10, 0));
		superHBox.setAlignment(Pos.CENTER);

		vary3HBox.setPadding(new Insets(0, 10, 10, 0));
		vary3HBox.setAlignment(Pos.CENTER);
		// note vary4Btn says V4 on the button george June 18,2019

		// final HBox autoHBox = new HBox(10, buildPolyCheckBox, varyOnePoint,
		// allPixCheckBox, polyAutoBtn);
		// final HBox autoHBox = new HBox(10, buildPolyCheckBox, polyAutoBtn);
		// final HBox autoHBox = new HBox(10, polyAutoBtn);
		// autoHBox.setPadding(new Insets(0, 10, 10, 0));
		// autoHBox.setAlignment(Pos.CENTER);

		wrapper.setSpacing(8);
		// wrapper.getChildren().addAll(varyLine1HBox, varyLine2HBox, varyLine3HBox,
		// varyInfoHBox, codeTypesHBox, codeTypes2HBox, vary3HBox, autoHBox);

		// wrapper.getChildren().addAll(varyLine1HBox, varyLine2HBox, varyLine3HBox,
		// varyInfoHBox, codeTypesHBox, codeTypes2HBox, vary3HBox);
		wrapper.getChildren().addAll(varyLine1HBox, varyLine2HBox, superHBox, autoPolyVaryHBox, vary3HBox,
				codeTypesHBox, newHBox);
		// wrapper.getChildren().addAll(varyLine1HBox, varyLine2HBox, varyLine3HBox,
		// varyInfoHBox, codeTypes2HBox, vary3HBox, autoHBox);
	}// george june 12,2019 changed codeTypesHBox to codeTypes2HBox

	// Zhao Yu Li, May 05, 2025.
	// Function that performs the Vary computations. This function is only used for
	// Vary and MiddleVary because they
	// carry out the same computations, but just prints the results differently.
	private void varyAction(String title, String outFile, boolean printMid) {
		final long startTime = System.currentTimeMillis();// george june 12,2019 added in !CS2cb.isSelected() &&
															// !CNS2cb.isSelected() && !ONS2cb.isSelected() &&
															// !OSNO2cb.isSelected() && !OSO2cb.isSelected()
		if (!CScb.isSelected() && !CNScb.isSelected() && !ONScb.isSelected() && !OSNOcb.isSelected()
				&& !OSOcb.isSelected()) {
			final Alert alert = new Alert(AlertType.ERROR);

			alert.setTitle(title);
			alert.setHeaderText("No CodeTypes");
			alert.setContentText("Please select at least one codetype.");
			alert.showAndWait();
		} else {
			final int shots = Integer.parseInt(shotsText.getText());
			final int max = Integer.parseInt(maxMovesText.getText());
			final int min = Integer.parseInt(minMovesText.getText());

			final ExecutorService executor = Executors.newFixedThreadPool(Utils.numThreads);

			System.out.printf(
					"//------------------------- " + (printMid ? "Middle " : "")
							+ "Vary %d shots at %d to %d moves -------------------------//%n",
					shots, min, max);

			final Task<MutableSortedSet<ClassifiedCodeSequence>> varyTask = new Task<MutableSortedSet<ClassifiedCodeSequence>>() {

				@Override
				protected MutableSortedSet<ClassifiedCodeSequence> call() throws Exception {
					return varyTriangles(3, executor);
				}

			};

			// final Progress progress = new Progress(varyTask);
			final ProgressWithStatus progress = new ProgressWithStatus(varyTask, "Calling findCodes3 (no status)", 0);
			final Thread varyThread = new Thread(varyTask);

			varyTask.setOnSucceeded(success -> {
				try {
					MutableSortedSet<ClassifiedCodeSequence> allCodes = varyTask.get();
					allCodes.forEach(seq -> Database.saveToDatabase(seq, "garbage"));
					hitsLabel.setText("" + allCodes.size());
					printCodes(allCodes, outFile, true, true, allCodes.size(), printMid);
				} catch (InterruptedException | ExecutionException e) {
					throw new RuntimeException(e);
				}
				final long endTime = System.currentTimeMillis();
				System.out.println("Time: " + (endTime - startTime));
				System.out.println("Time: " + Utils.timeConvert(endTime - startTime));
				executor.shutdown();
				progress.close();
			});

			varyTask.setOnCancelled(cancelled -> {
				System.out.println("// " + title + " Cancelled");
				varyThread.interrupt();
				executor.shutdownNow();
				progress.close();
			});
			varyTask.setOnFailed(fail -> {
				System.out.println("// " + title + " failed");
				executor.shutdown();
				progress.close();
			});

			varyThread.start();

			progress.show();
		}
	}

	public void click(final double xDeg, final double yDeg) {

		if (buildPolyCheckBox.isSelected()) {
			System.out.println(xDeg + " " + yDeg);
		}

		if (line3RDB.isSelected()) {
			varyX3Text.setText(Double.toString(xDeg));
			varyY3Text.setText(Double.toString(yDeg));
		} else if (line2RDB.isSelected()) {
			varyX2Text.setText(Double.toString(xDeg));
			varyY2Text.setText(Double.toString(yDeg));
		} else {
			varyX1Text.setText(Double.toString(xDeg));
			varyY1Text.setText(Double.toString(yDeg));
		}
	}

	private MutableSortedSet<ClassifiedCodeSequence> varyTriangles(
			final int version, final ExecutorService exe) {
		return varyTriangles(Double.parseDouble(varyX1Text.getText()),
				Double.parseDouble(varyY1Text.getText()),
				Double.parseDouble(varyX2Text.getText()),
				Double.parseDouble(varyY2Text.getText()),
				Double.parseDouble(varyX3Text.getText()),
				Double.parseDouble(varyY3Text.getText()),
				Double.parseDouble(line1CutText.getText()),
				Double.parseDouble(line2CutText.getText()),
				version, exe);
	}

	public MutableSortedSet<ClassifiedCodeSequence> varyTriangles(
			final double aX1, final double aY1, final double aX2, final double aY2,
			final double aX3, final double aY3, final double aCut1, final double aCut2,
			final int version, final ExecutorService exe) {

		final MutableSortedSet<ClassifiedCodeSequence> bareCodesFound = new TreeSortedSet<>();

		for (double cut2 = 0; cut2 <= aCut2; ++cut2) {
			for (double cut1 = 0; cut1 <= aCut1; ++cut1) {

				double a;
				double b;

				if (aCut2 > 0 && aCut1 > 0) {
					a = aX1 + (aX2 - aX1) * cut1 / aCut1 + (aX3 - aX1) * cut2 / aCut2;
					b = aY1 + (aY2 - aY1) * cut1 / aCut1 + (aY3 - aY1) * cut2 / aCut2;
				} else if (aCut1 > 0) {
					a = aX1 + (aX2 - aX1) * cut1 / aCut1;
					b = aY1 + (aY2 - aY1) * cut1 / aCut1;
				} else if (aCut2 > 0) {
					a = aX1 + (aX3 - aX1) * cut2 / aCut2;
					b = aY1 + (aY3 - aY1) * cut2 / aCut2;
				} else {
					a = aX1;
					b = aY1;
				}

				final MutableSortedSet<ClassifiedCodeSequence> pointCodes = findCodes(a, b, version, exe);
				bareCodesFound.addAll(pointCodes);
			}
		}
		return bareCodesFound;
	}

	public MutableSortedSet<ClassifiedCodeSequence> varyTrianglesL(
			final Vector2 point, final ExecutorService executor) { // Got rid of findCodes since it was unecessary
																	// complexity
		return findCodes(point.x, point.y, 3, executor);
	}

	// Overloading for seperate maximums
	public MutableSortedSet<ClassifiedCodeSequence> varyTrianglesL(
			final Vector2 point, final int CSmaxSS, final int OSOmaxSS, final int OSNOmaxSS,
			final ExecutorService executor) { // Got rid of findCodes since it was unecessary complexity
		final int min = Integer.parseInt(minMovesText.getText());
		final double shots = Integer.parseInt(shotsText.getText());

		final CodeTypeSet noCS = CodeTypeSet.builder()
				.setOSO(OSOmaxSS > 0)
				.setCNS(CNScb.isSelected())
				.setONS(ONScb.isSelected())
				.setOSNO(OSNOmaxSS > 0)
				.build();
		final CodeTypeSet onlyCS = CodeTypeSet.builder().setCS(true).build();

		final MutableSortedSet<ClassifiedCodeSequence> unfilteredCodesFound = new TreeSortedSet<>();
		final MutableSortedSet<ClassifiedCodeSequence> codesFound = new TreeSortedSet<>();
		unfilteredCodesFound.addAll(Vary.findCodes3(point.x, point.y, min, CSmaxSS, shots, onlyCS, executor));
		unfilteredCodesFound
				.addAll(Vary.findCodes3(point.x, point.y, min, Math.max(OSOmaxSS, OSNOmaxSS), shots, noCS, executor));
		for (final ClassifiedCodeSequence code : unfilteredCodesFound) { // Filter out overly large OSO/OSNO
			final CodeType type = code.codeType;
			if (type.equals(CodeType.OSO) && code.codeSum >= OSOmaxSS) {
				continue;
			}
			if (type.equals(CodeType.OSNO) && code.codeSum >= OSNOmaxSS) {
				continue;
			}
			codesFound.add(code);
		}
		return codesFound;
	}

	/**
	 * <code>autoVary</code> runs the autoVary algorithm, essentially vary3 repeated
	 * for a number of iterations, incrementing the maximum side sum each iteration
	 *
	 * @param point Point to run autoVary on
	 * @param exe   ExecutorService to run the algorithm on
	 */
	public MutableSortedSet<ClassifiedCodeSequence> autoVary(
			final Vector2 point, final ExecutorService exe) {
		// Grab necessary variables from global state
		int max = Integer.parseInt(maxMovesText.getText());
		int min = Integer.parseInt(minMovesText.getText());
		final int iterate = Integer.parseInt(autoIterText.getText());
		final int step = Integer.parseInt(autoStepText.getText());
		final int shots = Integer.parseInt(shotsText.getText());
		// TODO: Abstract autoVary to different
		final CodeTypeSet types = CodeTypeSet.builder()
				.setOSO(OSOcb.isSelected())
				.setCS(CScb.isSelected())
				.setCNS(CNScb.isSelected())
				.setONS(ONScb.isSelected())
				.setOSNO(OSNOcb.isSelected())
				.build();
		// george june 12,2019 added , OSO2cb.isSelected(), CS2cb.isSelected(),
		// CNS2cb.isSelected(), ONS2cb.isSelected(), OSNO2cb.isSelected()


		for (int i = 0; i < iterate + 1; i++) {
			final MutableSortedSet<ClassifiedCodeSequence> codesFound = new TreeSortedSet<>();
			codesFound.addAll(Vary.findCodes3(point.x, point.y, min, max, shots, types, exe));
			printCodes(codesFound, "garbage.txt", printAll, true, Integer.parseInt(maxPrinting.getText()));

			if (codesFound.isEmpty()) {
				min = Integer.valueOf(max);
				max += step;

			} else {
				return codesFound;
			}
		}
		return new TreeSortedSet<>();
	}

	// Overloading of autoVary for seperate maximum side sums when given override
	/**
	 * <code>autoVary</code> runs the autoVary algorithm filtering the results by
	 * the given separated maximum side sums.
	 * @see #autoVary(Vector2, ExecutorService)
	 *
	 * @param point      Point to run autoVary on
	 * @param maxSideSum Maximum side sums for each code type
	 * @param exe        ExecutorService to run the algorithm on
	 */
	public MutableSortedSet<ClassifiedCodeSequence> autoVary(
			final Vector2 point, final CodeTypeCollection<Integer> maxSideSum,
			final ExecutorService exe) {
		int CSmin = Integer.parseInt(minMovesText.getText());
		int CSstep = 0;
		int OSmin = Integer.parseInt(minMovesText.getText());
		int OSstep = 0;
		final int iterate = Integer.parseInt(autoIterText.getText());
		final int step = Integer.parseInt(autoStepText.getText());
		final int shots = Integer.parseInt(shotsText.getText());

		final CodeTypeSet noCS = CodeTypeSet.builder()
				.setOSO(maxSideSum.OSO > 0)
				.setCNS(CNScb.isSelected())
				.setONS(ONScb.isSelected())
				.setOSNO(maxSideSum.OSNO > 0)
				.build();
		final CodeTypeSet onlyCS = CodeTypeSet.builder().setCS(true).build();

		for (int i = 0; i < iterate + 1; i++) {
			final MutableSortedSet<ClassifiedCodeSequence> codesFound = new TreeSortedSet<>();
			// Handle CS separately from other code types
			if (maxSideSum.CS > 0) {
				codesFound.addAll(Vary.findCodes3(point.x, point.y, CSmin, maxSideSum.CS + CSstep, shots, onlyCS, exe));
			}
			codesFound.addAll(
					Vary.findCodes3(point.x, point.y, OSmin, Math.max(maxSideSum.OSO, maxSideSum.OSNO) + OSstep, shots,
							noCS, exe));

			// Filter codes if their maxSideSum is non-zero
			codesFound.removeIf(code -> {
				int max = maxSideSum.get(code.codeType);
				return max != 0 && code.codeSum >= max; });
			printCodes(codesFound, "garbage.txt", printAll, true, Integer.parseInt(maxPrinting.getText()));

			if (codesFound.isEmpty()) {
				CSmin = maxSideSum.CS;
				CSstep += step;
				OSmin = Math.max(maxSideSum.OSO, maxSideSum.OSNO);
				OSstep += OSstep;
			} else {
				return codesFound;
			}
		}
		return new TreeSortedSet<>();
	}

	private MutableSortedSet<ClassifiedCodeSequence> findCodes(
			final double xCoord, final double yCoord, final int version, final ExecutorService exe) {
		final MutableSortedSet<ClassifiedCodeSequence> out = new TreeSortedSet<>();
		final int max = Integer.parseInt(maxMovesText.getText());
		final int min = Integer.parseInt(minMovesText.getText());
		final double shots = Integer.parseInt(shotsText.getText());

		final CodeTypeSet types = CodeTypeSet.builder()
				.setOSO(OSOcb.isSelected())
				.setCS(CScb.isSelected())
				.setCNS(CNScb.isSelected())
				.setONS(ONScb.isSelected())
				.setOSNO(OSNOcb.isSelected())
				.build();

		final CodeTypeSet types2 = CodeTypeSet.builder()
				.setOSO(OSO2cb.isSelected())
				.setCS(CS2cb.isSelected())
				.setCNS(CNS2cb.isSelected())
				.setONS(ONS2cb.isSelected())
				.setOSNO(OSNO2cb.isSelected())
				.build();

		// george june12,2019 added , OSO2cb.isSelected(), CS2cb.isSelected(),
		// CNS2cb.isSelected(), ONS2cb.isSelected(), OSNO2cb.isSelected()
		if (version == 4) {
			out.addAll(Vary.findCodes4(xCoord, yCoord, min, max, shots, types));
		} else if (version == 3) {
			out.addAll(Vary.findCodes3(xCoord, yCoord, min, max, shots, types, exe));
		} else if (version == 2) {
			out.addAll(Vary.findCodes2(xCoord, yCoord, min, max, shots, types2, exe));
		} else {
			throw new RuntimeException("Version for varyTriangles must be 3 or 4");
		}
		return out;
	}

	// Objects for comparison functionality
	static ArrayList<ArrayList<String>> cList = new ArrayList<>();
	static ArrayList<String> savePairs = new ArrayList<>();
	static ArrayList<String> varySeq = new ArrayList<>();
	// setting this to true enables the compare functionality for code sequences.
	static boolean compare = false;
	boolean dragIntend = false;

	// a method for printing a set of codes. Can set print to false, which makes
	// this function just write
	// to the file.
	public static void printCodes(final MutableSortedSet<ClassifiedCodeSequence> allCodes, final String file,
			final boolean print, final boolean erase, final int Number, final boolean printMid) {

		final Path path = Paths.get(file);

		if (!Files.exists(path)) {
			try {
				Files.createFile(path);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		if (erase) {
			// Suryansh Ankur, 2026
            // Truncate the file by opening it in non-append mode. This is a scratch
            // dump file, so a failure here (e.g. the process is temporarily out of
            // file descriptors) must not abort the whole computation - log the real
            // cause and carry on.
            try {
                // opening in non-append mode truncates the file
                new PrintWriter(file).close();
            } catch (final FileNotFoundException e) {
                System.err.println("//Warning: couldn't truncate " + file + ": " + e.getMessage());
            }
		}

		final ArrayList<ClassifiedCodeSequence> splitCodes;
		final CodeType[] codeTypes = { CodeType.CS, CodeType.OSO, CodeType.OSNO, CodeType.CNS, CodeType.ONS };
		if (splitUp) {
			for (final CodeType type : codeTypes) {
				for (final ClassifiedCodeSequence code : allCodes) {
					if (code.codeType.equals(type)) {
						splitCodes.add(code);
					}
				}
			}
		} else {
			splitCodes = new ArrayList<>(allCodes);
		}

		ArrayList<ClassifiedCodeSequence> organizedCodes = new ArrayList<>(splitCodes);

		// Zhao Yu Li, May 05, 2025.
		// Prints only the middle code of the list of codes with the same type (i.e. CS,
		// OSNO, OSO, etc.)
		// and code length.
		// Zhao Yu Li, May 06, 2025.
		// Groups are distinguished by (code type, code length, and odd-even pattern)
		if (printMid) {
			organizedCodes = Vary.filterCodes(splitCodes);
		}

		int count = 0;
		ArrayList<String> codes = new ArrayList<>();
		// Suryansh Ankur, 2026
        // Open the dump file once (append mode) instead of reopening it for every
        // code, which churns file descriptors. Writing here is best-effort: the
        // codes are also returned via the in-memory structures (varySeq) below, so
        // if the file can't be opened (e.g. low on file descriptors) we log the
        // real cause and skip the dump rather than aborting the computation.
        PrintStream output = null;
        try {
            output = new PrintStream(new FileOutputStream(file, true));
        } catch (final FileNotFoundException e) {
            System.err.println("//Warning: couldn't open " + file + " for writing: " + e.getMessage());
        }
        try {
            for (final ClassifiedCodeSequence code : organizedCodes) {
                count += 1;
                final String codeString = Utils.standard(code, count);
                if (count <= Number && print) {
                    System.out.println(codeString);
                    //codes.add(codeString.substring(5));
                    codes.add(codeString.substring(codeString.indexOf("-") + 2));
                }
                if (output != null) {
                    output.println(codeString + " " + CodeSequence.evenOddSequence(code.codeSequence.codeNumbers));
                }
            }
        } finally {
            if (output != null) output.close();
        }
        varySeq.clear();
		varySeq.addAll(codes);

		if (compare) {
			// Adding the Vary3 codes to 2D ArrayList of ArrayList for comparison.
			if (cList.size() >= 2) {
				cList.remove(0);
				cList.add(1, codes);
			} else if (cList.size() == 0) {
				cList.add(0, codes);
			} else {
				cList.add(1, codes);
			}

			ArrayList<String> matching = Utils.compare(cList);
			if (cList.size() == 2) {
				System.out
						.println("//--------------------------- Matching Code Sequences ---------------------------//");
				if (matching.isEmpty()) {
					System.out.println("None matching...");
					cList.remove(0);
				} else {
					savePairs.clear();
					for (String code : matching) {
						System.out.println(code);
						savePairs.add(code);
					}
					cList.add(matching);

				}
				if (cList.size() >= 2) {
					cList.remove(0);
				}
			}

		}
	}

	private static void addFirstMidLast(
			ArrayList<ClassifiedCodeSequence> organizedCodes,
			Map<String, ArrayList<ClassifiedCodeSequence>> processedCodes,
			Map<String, Integer> processedCodesLength,
			String oddEvenPattern) {
		if (processedCodesLength.get(oddEvenPattern) >= 2)
			organizedCodes.add(processedCodes.get(oddEvenPattern).get(0));

		organizedCodes.add(processedCodes.get(oddEvenPattern)
				.get(processedCodesLength.get(oddEvenPattern) / 2));

		if (processedCodesLength.get(oddEvenPattern) >= 3)
			organizedCodes.add(processedCodes.get(oddEvenPattern).get(processedCodesLength.get(oddEvenPattern) - 1));
	}

	// a method for printing a set of codes. Can set print to false, which makes
	// this function just write
	// to the file.
	public static void printCodes(final MutableSortedSet<ClassifiedCodeSequence> allCodes, final String file,
			final boolean print, final boolean erase, final int Number) {
		printCodes(allCodes, file, print, erase, Number, false);
	}

	public String typeString() {
		String types = "";
		if (OSNOcb.isSelected()) {
			types += "OSNO ";
		}
		if (OSOcb.isSelected()) {
			types += "OSO ";
		}
		if (CScb.isSelected()) {
			types += "CS ";
		}
		if (CNScb.isSelected()) {
			types += "CNS ";
		}
		if (ONScb.isSelected()) {
			types += "ONS ";
		} /*
			 * //george june 12,2019 start
			 * if (OSNO2cb.isSelected()) {
			 * types += "OSNO ";
			 * }
			 * if (OSO2cb.isSelected()) {
			 * types += "OSO ";
			 * }
			 * if (CS2cb.isSelected()) {
			 * types += "CS ";
			 * }
			 * if (CNS2cb.isSelected()) {
			 * types += "CNS ";
			 * }
			 * if (ONS2cb.isSelected()) {
			 * types += "ONS ";
			 * }//george june 12,2019 end
			 */
		return types;
	}

	public ArrayList<Double> getRadianCoord() {
		ArrayList<Double> result = new ArrayList<>();
		double x = Math.toRadians(Double.parseDouble(varyX1Text.getText()));
		double y = Math.toRadians(Double.parseDouble(varyY1Text.getText()));
		result.add(x);
		result.add(y);
		return result;
	}
}
