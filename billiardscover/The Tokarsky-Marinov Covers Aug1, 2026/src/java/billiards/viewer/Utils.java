package billiards.viewer;

import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.list.primitive.IntList;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import javafx.scene.Node;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.util.Duration;

public final class Utils {

    public static final int numThreads = Runtime.getRuntime().availableProcessors();

    public static Optional<ImmutableIntList> splitString(final String textCodeSeq) {
        // split on whitespace
        final String[] textCodeNumbers = textCodeSeq.trim().split("\\s+");

        final MutableIntList list = new IntArrayList();

        for (final String textCodeNumber : textCodeNumbers) {
            if (!textCodeNumber.isEmpty()) {
                try {
                    final int codeNumber = Integer.parseInt(textCodeNumber);
                    list.add(codeNumber);
                } catch (final NumberFormatException e) {
                    return Optional.empty();
                }
            }
        }

        return Optional.of(list.toImmutable());
    }

    public static String readFromFile(final String string) {

        try {

            final Path path = Paths.get(string);
            final byte[] bytes = Files.readAllBytes(path);

            return new String(bytes, Charset.defaultCharset());

        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    // Copy each element of source into dest, overriding the current values
    public static void copyInto(final MutableIntList dest, final IntList source) {
        for (int i = 0; i < dest.size(); ++i) {
            final int elem = source.get(i);
            dest.set(i, elem);
        }
    }

    public static String hex(final Color color) {
        final long rd = Math.round(color.getRed() * 255);
        final long gr = Math.round(color.getGreen() * 255);
        final long bl = Math.round(color.getBlue() * 255);

        final String hex = String.format("%02x%02x%02x", rd, gr, bl);

        return "-fx-base: #" + hex;
    }

    public static void colorButton(final Node button, final Color color, final Color clicked) {
        button.setStyle(hex(color));
        button.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> button.setStyle(hex(clicked)));
        button.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> button.setStyle(hex(color)));
    }

    public static Tooltip toolTip(final String text) {
        final Tooltip tip = new Tooltip(text);
        tip.setPrefWidth(300);
        tip.setWrapText(true);

        return tip;
    }

    // we break into a the private field of tool tip behaviour and change it around
    // based on a post on https://coderanch.com/t/622070/java/control-Tooltip-visible-time-duration
    public static void setupCustomTooltipBehavior(final int openDelayInMillis, final int visibleDurationInMillis,
                                                  final int closeDelayInMillis) {
        try {

            Class<?> TTBehaviourClass = null;
            final Class<?>[] declaredClasses = Tooltip.class.getDeclaredClasses();
            for (final Class<?> c : declaredClasses) {
                if (c.getCanonicalName().equals("javafx.scene.control.Tooltip.TooltipBehavior")) {
                    TTBehaviourClass = c;
                    break;
                }
            }

            if (TTBehaviourClass == null) {
                return;
            }
            final Constructor<?> constructor = TTBehaviourClass.getDeclaredConstructor(
                Duration.class, Duration.class, Duration.class, boolean.class);
            if (constructor == null) {
                return;
            }
            constructor.setAccessible(true);
            final Object newTTBehaviour = constructor.newInstance(
                new Duration(openDelayInMillis), new Duration(visibleDurationInMillis),
                new Duration(closeDelayInMillis), false);
            if (newTTBehaviour == null) {
                return;
            }
            final Field ttbehaviourField = Tooltip.class.getDeclaredField("BEHAVIOR");
            if (ttbehaviourField == null) {
                return;
            }

            ttbehaviourField.setAccessible(true);
            ttbehaviourField.set(Tooltip.class, newTTBehaviour);

        } catch (final Exception e) {
            // This reaches into a private JavaFX internal (Tooltip.TooltipBehavior) whose shape
            // varies between JavaFX 8 builds. If it is not present or has a different signature,
            // fall back to JavaFX's default tooltip timing rather than crashing the whole app.
            System.err.println("custom tooltip behavior unavailable on this JavaFX build; "
                + "using defaults (" + e.getClass().getSimpleName() + ")");
        }
    }
}
