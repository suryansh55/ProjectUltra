package billiards.viewer;

import java.io.PrintWriter;
import java.io.StringWriter;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;

public class ErrorAlert extends javafx.scene.control.Alert {

	final Exception ex;

	Label label = new Label("The exception stacktrace was:");

	TextArea textArea = new TextArea();

	GridPane expContent = new GridPane();
	
	final Button copyBtn = new Button();
	
	public ErrorAlert(final Exception execpt) {
		super(AlertType.ERROR);
		
		ex = execpt;
		
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		ex.printStackTrace(pw);
		String exceptionText = sw.toString();
		
		textArea.setText(exceptionText);
		textArea.setEditable(false);
		textArea.setWrapText(false);
		
		copyBtn.setText("Copy");
		copyBtn.setOnAction(event -> {
			final Clipboard clipboard = Clipboard.getSystemClipboard();
	        final ClipboardContent content = new ClipboardContent();
	        content.putString(exceptionText);
	        clipboard.setContent(content);
		});
		copyBtn.setStyle("-fx-background-color: \n" + 
				"     #707070,\n" + 
				"     linear-gradient(#fcfcfc, #f3f3f3),\n" + 
				"     linear-gradient(#f2f2f2 0%, #ebebeb 49%, #dddddd 50%, #cfcfcf 100%);\n" + 
				"    -fx-background-insets: 0,1,2;\n" + 
				"    -fx-background-radius: 3,2,1;\n" + 
				"    -fx-padding: 3 30 3 30;\n" + 
				"    -fx-text-fill: black;\n" + 
				"    -fx-font-size: 11px;");
		Utils.colorButton(copyBtn, Color.LIGHTGRAY, Color.DARKGRAY);
		
		this.setTitle("Error");
		this.setHeaderText("We've Encountered a Problem!");
		this.setContentText("It would help us greatly if you emailed the\n"
				          + "stacktrace below to tokarsky@ualberta.ca :)");
		this.setResizable(true);
		
		textArea.setMaxWidth(Double.MAX_VALUE);
		textArea.setMaxHeight(Double.MAX_VALUE);
		GridPane.setVgrow(textArea, Priority.ALWAYS);
		GridPane.setHgrow(textArea, Priority.ALWAYS);
		
		final HBox labelBox = new HBox(10);
		labelBox.getChildren().addAll(label, copyBtn);
		labelBox.setAlignment(Pos.BASELINE_LEFT);
		
		expContent.setMaxWidth(Double.MAX_VALUE);
		expContent.add(labelBox, 0, 0);
		expContent.add(textArea, 0, 1);
		
		// Set expandable Exception into the dialog pane.
		this.getDialogPane().setExpandableContent(expContent);
	}
}
