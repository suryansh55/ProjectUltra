package billiards.geometry;

import org.apache.commons.math3.util.FastMath;

import java.util.Objects;

// This class is immutable
public final class Point {

    public final double x;
    public final double y;

    private Point(final double x, final double y) {
        this.x = x;
        this.y = y;
    }

    // this - v
    public Point sub(final Point v) {
        final double newX = this.x - v.x;
        final double newY = this.y - v.y;
        return Point.create(newX, newY);
    }

    // this + v
    public Point add(final Point v) {
        final double newX = this.x + v.x;
        final double newY = this.y + v.y;
        return Point.create(newX, newY);
    }

    // scale * this
    public Point scale(final double scale) {
        final double newX = scale * this.x;
        final double newY = scale * this.y;
        return Point.create(newX, newY);
    }

    public double norm() {
        return FastMath.hypot(this.x, this.y);
    }

    public static Point unit(final Point v) {
        return v.scale(1.0 / v.norm());
    }

    public static Point create(final double x, final double y) {
        return new Point(x, y);
    }

    public static double dot(final Point v, final Point w) {
        return v.x * w.x + v.y * w.y;
    }

    // Return the z compenent of v x w, where v and w are zero-extended to three dimensions
    public static double cross(final Point v, final Point w) {
        return v.x * w.y - v.y * w.x;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.x, this.y);
    }

    @Override
    public boolean equals(final Object obj) {
        final Point other = (Point) obj;
        return this.x == other.x && this.y == other.y;
    }

    @Override
    public String toString() {
        return "(" + this.x + ", " + this.y + ")";
    }
}
