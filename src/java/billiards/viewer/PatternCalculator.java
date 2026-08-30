package billiards.viewer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Optional;

import billiards.codeseq.CodeSequence;
import billiards.pattern.InvalidSinglePattern;
import billiards.pattern.InvalidTriplePattern;
import billiards.pattern.SinglePattern;
import billiards.pattern.Triple;
import billiards.pattern.TriplePattern;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javaslang.control.Either;
import patternfinder.PatUtils;

/**
 * <b>Zhao Yu Li</b><br>
 * <b>Jul 07, 2025</b>
 * <p>
 * Calculates the iteration pattern between two code sequences.
 * </p>
 */
public class PatternCalculator {
	public PatternCalculator() {
		final Stage stage = new Stage();
		stage.setTitle("Code Sequence Pattern Calculator");

		final VBox root = new VBox(10);
		final Scene scene = new Scene(root, 1000, 600);
		root.setAlignment(Pos.CENTER);

		stage.setScene(scene);

		// Create a multi-line text area with top-aligned cursor
		TextArea textArea = new TextArea();
		textArea.setWrapText(true);
		textArea.setPrefWidth(980);
		textArea.setPrefHeight(580);
		textArea.setFont(new Font("monospace", 16));

		// Put the text area inside a scroll pane
		ScrollPane scrollPane = new ScrollPane(textArea);
		scrollPane.setFitToHeight(true);
		scrollPane.setFitToWidth(true);

		// Create button
		Button button = new Button("Calculate Pattern");

		root.setPadding(new Insets(10));
		root.getChildren().addAll(scrollPane, button);

		// Action listener
		button.setOnAction(e -> calcCodePattern(textArea));

		stage.show();
	}

	private static String getSubstringAfterPattern(String string, String pattern) {
		int index = string.indexOf(") ");
		if (index != -1) {
			return string.substring(index + pattern.length());
		} else {
			return string;
		}
	}

	private static void calcCodePattern(TextArea textArea) {
		final String[] codes = textArea.getText().trim().split("\n");

		if (codes.length != 2) {
			showAlert("Please enter exactly two code sequences separated by a new line.");
			return;
		}

		final String pattern = ") ";
		final String string1 = getSubstringAfterPattern(codes[0], pattern);
		final String string2 = getSubstringAfterPattern(codes[1], pattern);

		if (string1.isEmpty()) {
			showAlert(
					"The first code sequence does not contain the pattern '" + pattern +
							"'\nPlease enter a valid code sequence, e.g. starting with '1 - CS(x, y)'.");
			return;
		}

		if (string2.isEmpty()) {
			showAlert(
					"The second code sequence does not contain the pattern '" + pattern +
							"'\nPlease enter a valid code sequence, e.g. starting with '1 - CS(x, y)'.");
			return;
		}

		final String[] subSequence1 = string1.split(",");
		final String[] subSequence2 = string2.split(",");

		if (subSequence1.length != subSequence2.length) {
			showAlert("The number of subsequences do not match.");
			return;
		}

		if (subSequence1.length != 1 && subSequence1.length != 3) {
			System.out.println(
					"Unorthodox number of subsequences. i.e. The code sequences are neither a single nor a triple.");
			return;
		}

		if (subSequence1.length == 1) { // Case if the pattern is a single
			Optional<CodeSequence> code1 = Utils.strToCodeSequence(subSequence1[0]);
			Optional<CodeSequence> code2 = Utils.strToCodeSequence(subSequence2[0]);
			if (!code1.isPresent() || !code2.isPresent()) {
				showAlert("Failed to parse a code sequence from the given patterns.");
				return;
			}

			Either<InvalidSinglePattern, SinglePattern> singlePattern = SinglePattern.create(code1.get(), code2.get());
			if (singlePattern.isLeft()) {
				showAlert("Error creating pattern: " + singlePattern.getLeft().getErrorMessage());
				return;
			}
			System.out.printf("The full pattern is:\n%s\n", singlePattern.get().toStringFull());
			System.out.printf("The reduced pattern is:\n%s\n", singlePattern.get());
		} else { // Case if the pattern is a triple
			Optional<Triple> triple1 = Utils.strToTriple(subSequence1);
			Optional<Triple> triple2 = Utils.strToTriple(subSequence2);

			if(!triple1.isPresent() || !triple2.isPresent()) {
				showAlert("Failed to parse a triple from the given patterns");
				return;
			}

			Either<InvalidTriplePattern, TriplePattern> triplePattern = TriplePattern.create(triple1.get(), triple2.get());
			if(triplePattern.isLeft()) {
				showAlert("Error creating pattern: " + triplePattern.getLeft().getErrorMessage());
				return;
			}
			System.out.printf("The full pattern is:\n%s\n", triplePattern.get().toStringFull());
			System.out.printf("The reduced pattern is:\n%s\n", triplePattern.get());
		}
	}

	private static void showAlert(String content) {
		Text text = new Text(content);
		text.setWrappingWidth(350);

		Alert alert = new Alert(Alert.AlertType.INFORMATION);
		alert.setTitle("Pattern Calculator");
		alert.getDialogPane().setContent(text);
		alert.getDialogPane().setPrefWidth(400);
		alert.getDialogPane().setPadding(new Insets(10));
		alert.showAndWait();
	}
}
