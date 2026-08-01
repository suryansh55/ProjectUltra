package billiards.viewer;

import billiards.codeseq.CodePair;
import billiards.codeseq.CodeSequence;
import billiards.codeseq.InitialAngles;
import billiards.geometry.Point;
import billiards.math.Equation;
import billiards.math.LinCom;
import billiards.math.XYEta;
import billiards.wrapper.CodeInfo;
import billiards.wrapper.Wrapper;

import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import org.eclipse.collections.impl.list.mutable.FastList;

public final class InfoWindow {

    private final TextArea text = new TextArea();
    private final Button showButton = new Button();
    private final VBox root = new VBox();
    private final Stage stage = new Stage();
    private final Scene scene = new Scene(root);
    private final HBox inputHBox = new HBox();
    private final TextField codeNumbersTextField = new TextField();

    public InfoWindow(final String windowTitle) {
        stage.setScene(scene);
        stage.setTitle(windowTitle);
        stage.setOnCloseRequest(event -> stage.close());

        text.setPrefColumnCount(60);
        text.setPrefRowCount(20);
        text.setWrapText(true);
        text.setEditable(false);
        text.setFont(Font.font("Monaco", 16));

        // We want the text to expand as we make the window bigger
        VBox.setVgrow(text, Priority.ALWAYS);

        inputHBox.getChildren().addAll(codeNumbersTextField, showButton);
        inputHBox.setSpacing(10);

        codeNumbersTextField.setPromptText("Code Sequence");

        showButton.setText("Show");
        showButton.setOnAction(event -> showInfo());

        root.getChildren().addAll(inputHBox, text);
        root.setSpacing(10);
        root.setPadding(new Insets(10));
    }

    public void close() {
        stage.close();
    }

    public void show() {
        stage.show();
    }

    private void showInfo() {
        final String codePairString = codeNumbersTextField.getText();

        if (codePairString.isEmpty()) {
            final Alert alert = new Alert(AlertType.ERROR);

            alert.setTitle("Enter a Code Sequence");
            alert.setHeaderText("Enter a Code Sequence");
            alert.setContentText("Please enter a code sequence.");
            alert.showAndWait();
            return;
        }

        final String[] split = codePairString.split(",");
        final String codeNumsString = split[0].trim();
        
        // Dec 17, 2021 
        // I changed the initial angles from xy to zy;
        //final String initialAnglesString = "xy"; //split[1].trim();
        final String initialAnglesString = "zy"; //split[1].trim();

        final Optional<ImmutableIntList> optional = Utils.splitString(codeNumsString);
        final InitialAngles initialAngles = Cover.parseInitialAngles(initialAnglesString);

        if (!optional.isPresent()) {
            final Alert alert = new Alert(AlertType.ERROR);
            alert.setTitle("Invalid Input");
            alert.setHeaderText("Invalid Input");
            alert.setContentText(String.format("Input %s is invalid.", codeNumsString));

            alert.showAndWait();
            return;
        }

        final ImmutableIntList codeNums = optional.get();

/*
        final Either<InvalidCodeSequence, ClassifiedCodeSequence> either =
            ClassifiedCodeSequence.create(codeNums);
        if (either.isLeft()) {
            final InvalidCodeSequence errorCode = either.getLeft();
            final Alert alert = new Alert(AlertType.ERROR);
            alert.setTitle("Invalid Code Sequence");
            alert.setHeaderText("Invalid Code Sequence");
            alert.setContentText(InvalidCodeSequence.errorMessage(codeNums, errorCode));

            alert.showAndWait();
            return;
        }
        */

        final CodeSequence codeSeq = new CodeSequence(codeNums);

        final CodePair codePair = new CodePair(codeSeq, initialAngles);

        final String string = infoString(codePair);

        text.setText(string);
    }

    private static String infoString(final CodePair codePair) {
        final StringBuffer buff = new StringBuffer();

        buff.append(String.format("code sequence = %s%n", codePair.sequence.toString()));
        buff.append(String.format("code length = %d%n", codePair.sequence.length()));
        buff.append(String.format("code sum = %d%n", codePair.sequence.sum()));
        buff.append(String.format("code type = %s%n", codePair.sequence.type()));

        //buff.append('\n');
        //buff.append("initial angles = " + codePair.angles);
        buff.append("\n\n");

        final LinCom<XYEta> constraint = codePair.sequence.constraint(codePair.angles.first, codePair.angles.second);
        final String constraintStr = formatConstraint(constraint);
        //buff.append("constraint = " + formatConstraint(constraint));
        //buff.append("\n\n");
        if (!constraintStr.equals("0")) {
            buff.append("Line Equation: 0 = " + constraintStr);
            buff.append("\n\n\n");
        }

        buff.append("Bounding Polygon\n");

        String polygonString = Wrapper.getBoundingPolygon(codePair);
        MutableList<Point> points = Viewer.parsePolygonString(polygonString);
        for (Point point : points) {
            buff.append(point +"\n");
        }

        return buff.toString();
    }

    private static String toString(final XYEta symbol) {
        switch (symbol) {
        case X:
            return "x";
        case Y:
            return "y";
        case Eta:
            return "90";
        default:
            throw new RuntimeException("unknown symbol " + symbol);
        }
    }

    // Sadly, there is no generic way of implementing this in Java, so we will just hard code
    // it for this special case
    private static String formatConstraint(final LinCom<XYEta> constraint) {

        if (constraint.isZero()) {
            return "0";
        }

        final EnumMap<XYEta, Integer> coeffMap = new EnumMap<>(XYEta.class);
        coeffMap.put(XYEta.X, constraint.coeff(XYEta.X));
        coeffMap.put(XYEta.Y, constraint.coeff(XYEta.Y));
        coeffMap.put(XYEta.Eta, constraint.coeff(XYEta.Eta));

        boolean front = true;

        final StringBuilder builder = new StringBuilder();

        for (final Map.Entry<XYEta, Integer> entry : coeffMap.entrySet()) {

            final XYEta symbol = entry.getKey();
            final Integer coeff = entry.getValue();

            if ((coeff > 0) && !front) {
                builder.append('+');
            }

            if (coeff == -1) {
                builder.append('-');
            } else if ((coeff != 0) && (coeff != 1)) {
                builder.append(coeff);

                if (symbol == XYEta.Eta) {
                    builder.append('*');
                }
            }

            if (coeff != 0) {
                builder.append(toString(symbol));
                front = false;
            }
        }

        return builder.toString();
    }
}
