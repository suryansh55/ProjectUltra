package billiards.utils;

import billiards.geometry.ConvexPolygon;
import billiards.geometry.Vector2;
import com.google.common.base.Splitter;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.list.mutable.FastList;


public class Polygon {
    public static String cleanPolygon(String polygonString) {
        final Iterable<String> lines = Splitter.onPattern("\\R")
                .trimResults()
                .omitEmptyStrings()
                .split(polygonString);

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

    public static ConvexPolygon createConvexPolygon(String cleanedPolygon) {
        final String[] lines = cleanedPolygon.split("\n");
        final MutableList<Vector2> pointList = new FastList<>();

        for (final String line : lines) {
            // Tolerate blank/trailing lines instead of crashing on parseDouble("").
            if (line.trim().isEmpty()) {
                continue;
            }
            final String[] coords = line.trim().split("\\s+");
            if (coords.length < 2) {
                // Clear, typed error (a RuntimeException) so callers can show a
                // friendly alert instead of leaking an opaque parse trace.
                // Suryansh Ankur, 2026
                throw new IllegalArgumentException(
                    "Each polygon point must be two numbers 'x y'; got: \"" + line.trim() + "\"");
            }
            final double x = Math.toRadians(Double.parseDouble(coords[0]));
            final double y = Math.toRadians(Double.parseDouble(coords[1]));
            pointList.add(Vector2.create(x, y));
        }

        if (pointList.isEmpty()) {
            throw new IllegalArgumentException(
                "No polygon coordinates entered. Enter one 'x y' point per line.");
        }
        return ConvexPolygon.create(pointList.toImmutable());
    }
}
