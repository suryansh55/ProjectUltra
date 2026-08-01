package billiards.geometry;

import org.eclipse.collections.api.list.ImmutableList;

public final class ConvexPolygon implements Project {

    public final ImmutableList<Point> vertices;

    private ConvexPolygon(final ImmutableList<Point> vertices) {
        this.vertices = vertices;
    }

    public static ConvexPolygon create(final ImmutableList<Point> points) {
        if (points.size() < 3) {
            throw new RuntimeException("Error: not enough points for a convex polygon");
        }

        // TODO actually check this is a convex polygon though

        return new ConvexPolygon(points);
    }

    // algorithm taken from http://paulbourke.net/geometry/polygonmesh
    public Location location(final double x, final double y) {
        // in theory, it is possible to determine if a point is on the boundary
        // but with floating point math you have no guarantee
        Point p0 = this.vertices.get(0);
        Point p1 = this.vertices.get(1);

        final int firstSideSign = sign(p0, p1, x, y);
        if (firstSideSign == 0) {
            return Location.BOUNDARY;
        }

        final int size = this.vertices.size();
        for (int i = 1; i < size; ++i) {
            p0 = this.vertices.get(i);
            p1 = this.vertices.get((i + 1) % size);

            final int currentSideSign = sign(p0, p1, x, y);

            if (currentSideSign == 0) {
                return Location.BOUNDARY;
            } else if (currentSideSign != firstSideSign) {
                return Location.OUTSIDE;
            }
        }

        // if we have the same sign for everything, we are on the inside
        return Location.INSIDE;
    }

    private static int sign(final Point v0, final Point v1, final double x, final double y) {
        // final double value = (y - y0) * (x1 - x0) - (x - x0) * (y1 - y0);
        final double value = (y - v0.y) * (v1.x - v0.x) - (x - v0.x) * (v1.y - v0.y);

        if (value < 0) {
            return -1;
        } else if (value > 0) {
            return 1;
        } else if (value == 0) {
            return 0;
        } else {
            throw new RuntimeException("unorderable double in ConvexPolygon::sign");
        }
    }

    // Separating axis theorem
    // This is often called polygon collision in the gaming industry
    // http://www.dyn4j.org/2010/01/sat/

    // If we find a separating axis, they do not intersect. If we do not
    // find one, then they do intersect.

    // Return if we have found a separating axis by projecting the figure
    // and this along the axes of this
    public boolean separatingAxis(final Project figure) {
        // Now we get the axes from the sides of the polygon,
        // and project the rectangle and polygon along those
        // axes
        final int size = this.vertices.size();
        for (int i = 0; i < size; ++i) {
            final Point p0 = this.vertices.get(i);
            final Point p1 = this.vertices.get((i + 1) % size);

            // Vector from p0 to p1
            // The order and perpendicular vector are rather arbitrary
            // as long as they lie on the same line
            final Point edge = p1.sub(p0);
            final Point axis = Point.create(-edge.y, edge.x);

            final Interval figureProject = figure.project(axis);
            final Interval polyProject = this.project(axis);

            if (!Interval.intersects(figureProject, polyProject)) {
                return true;
            }
        }

        return false;
    }

    public boolean intersects(final ConvexPolygon poly) {
        // If we do not find a separating axis for the first one and we don't find a
        // separating axis for the second, then they do intersect.
        return !(poly.separatingAxis(this) || this.separatingAxis(poly));
    }

    public boolean intersects(final Rectangle rect) {
        // The axes for the rectangle are just the x and y
        // axes

        final Interval rectProjectX = rect.projectX();
        final Interval polyProjectX = this.projectX();

        if (!Interval.intersects(rectProjectX, polyProjectX)) {
            return false;
        }

        final Interval rectProjectY = rect.projectY();
        final Interval polyProjectY = this.projectY();

        if (!Interval.intersects(rectProjectY, polyProjectY)) {
            return false;
        }

        return !this.separatingAxis(rect);
    }

    // Project the polygon onto the x axis
    // so find the min and max x
    public Interval projectX() {
        final Point first = this.vertices.get(0);

        double min = first.x;
        double max = first.x;

        for (final Point point : this.vertices) {
            final double x = point.x;
            min = Math.min(min, x);
            max = Math.max(max, x);
        }

        return Interval.create(min, max);
    }

    // Ditto
    public Interval projectY() {
        final Point first = this.vertices.get(0);

        double min = first.y;
        double max = first.y;

        for (final Point point : this.vertices) {
            final double y = point.y;
            min = Math.min(min, y);
            max = Math.max(max, y);
        }

        return Interval.create(min, max);
    }

    @Override
    public Interval project(final Point axis) {
        final Point first = this.vertices.get(0);

        final double firstDot = Point.dot(first, axis);
        double min = firstDot;
        double max = firstDot;

        for (final Point point : this.vertices) {
            final double dot = Point.dot(point, axis);
            min = Math.min(min, dot);
            max = Math.max(max, dot);
        }

        return Interval.create(min, max);
    }

    @Override
    public int hashCode() {
        return this.vertices.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        final ConvexPolygon other = (ConvexPolygon) obj;
        return this.vertices.equals(other.vertices);
    }

    @Override
    public String toString() {
        return this.vertices.toString();
    }
}
