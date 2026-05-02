package billiards.viewer;

import java.util.HashMap;

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

		StringBuilder codePattern = new StringBuilder();

		final String[] subSequence1 = string1.split(",");
		final String[] subSequence2 = string2.split(",");

		if (subSequence1.length != subSequence2.length) {
			showAlert("The number of subsequences do not match.");
			return;
		}

		if (subSequence1.length != 1 && subSequence1.length != 3) {
			System.out.println(
					"Unorthodox number of subsequences. i.e. The code sequences are neither a single nor a triple.");
		}

		for (int i = 0; i < subSequence1.length; i++) {
			final String subPattern = calcSequencePattern(subSequence1[i].trim(), subSequence2[i].trim());
			codePattern.append(subPattern).append(", ");
		}

		// Take a substring to exclude the final comma
		final String patternStr = codePattern.toString().trim().substring(0, codePattern.length() - 2);
		final String reducedPattern = reduceToLowestTerms(patternStr);

		System.out.println("The full pattern is: ");
		System.out.println(patternStr);

		System.out.println("The pattern in lowest terms is:");
		System.out.println(reducedPattern);
	}

	private static String calcSequencePattern(String string1, String string2) {
		final String[] code1 = string1.split(" ");
		final String[] code2 = string2.split(" ");

		if (code1.length != code2.length)
			showAlert("The length of the code sequences do not match.");

		StringBuilder codePattern = new StringBuilder();

		for (int i = 0; i < code1.length; i++) {
			int code1Val;
			int code2Val;
			try {
				code1Val = Integer.parseInt(code1[i]);
				code2Val = Integer.parseInt(code2[i]);
			} catch (Exception e) {
				showAlert("An exception occurred while converting code sequence at index " + (i + 1)
						+ " into integers: " + e);
				return "";
			}

			final int difference = code2Val - code1Val;

			if (difference % 2 != 0) {
				showAlert("Difference between the code value at index " + (i + 1) + " is not a multiple of 2.");
				return "";
			}

			if (difference > 0) {
				for (int k = 0; k < difference / 2; k++) {
					codePattern.append((i + 1)).append(" ");
				}
			} else if (difference < 0) {
				for (int k = 0; k < -difference / 2; k++) {
					codePattern.append(-(i + 1)).append(" ");
				}
			}
		}

		return codePattern.toString().trim();
	}

	/**
	 * <b>Jeff Khuu</b><br>
	 * <b>May 01, 2026</b>
	 * <p>
	 * Reduces a given code pattern into "lowest terms". A code pattern is in lowest
	 * terms if the occurrences of each addition of two have no common factors
	 * </p>
	 * 
	 * @param codePattern String representing a valid code pattern
	 * @return String representing the given code pattern in "lowest terms"
	 */
	private static String reduceToLowestTerms(String codePattern) {
		// Build a counter of the occurrences of each number in the code pattern
		HashMap<Integer, Integer> patternCounter = new HashMap<>();
		for (String patternStrElem : codePattern.split(" ")) {
			int patternElem = Integer.parseInt(patternStrElem);
			if (patternCounter.containsKey(patternElem)) {
				patternCounter.put(patternElem, patternCounter.get(patternElem) + 1);
			} else {
				patternCounter.put(patternElem, 1);
			}
		}

		// Find the greastest common divisor of all the elements in the sequence
		int[] patternOccurences = patternCounter.values().stream().mapToInt(i -> i).toArray();
		int gcd = patternOccurences[0];

		if (patternOccurences.length >= 2) {
			for (int i = 1; i < patternOccurences.length; ++i) {
				gcd = gcd(gcd, patternOccurences[i]);
				if (gcd == 1)
					break;
			}
		}

		// Build the string with the reduced number of pattern elements
		StringBuilder reducedPatternBuilder = new StringBuilder();
		for (int key : patternCounter.keySet().stream().sorted().mapToInt(i -> i).toArray()) {
			for (int i = 0; i < patternCounter.get(key) / gcd; ++i) {
				reducedPatternBuilder.append(key).append(" ");
			}
		}

		return reducedPatternBuilder.toString();
	}

	/**
	 * <b>Jeff Khuu</b><br>
	 * <b>May 01, 2026</b>
	 * <p>
	 * Calculates and returns the greatest common divisor between two integers a and
	 * b
	 * using an iterative approach
	 * </p>
	 * 
	 * @param a int
	 * @param b int
	 * @return int representing the greatest common divisor
	 */
	private static int gcd(int a, int b) {
		int i = a < b ? a : b; // find the minimum of a and b

		// Iterate from the smaller number to 1
		for (; i > 1; i--) {
			// Check if i is a divisor
			if (a % i == 0 && b % i == 0)
				return i;
		}
		// Otherwise, return 1
		return 1;

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
