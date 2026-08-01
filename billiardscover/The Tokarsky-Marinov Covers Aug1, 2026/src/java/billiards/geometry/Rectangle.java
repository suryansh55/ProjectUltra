package billiards.geometry;

import java.util.Objects;

// Note: Accurate radii can only be obtained for rectangle that are create using intial() method or are result of subdivision.
// If you create a new Rectangle using create() method (as is done for the hard coded rectangles such as starpattern in viewer)
// you should assign the numerX, numerY and denom using the setter methods.
public final class Rectangle implements Project {

    public final Interval intervalX;
    public final Interval intervalY;
    private long numerX;
    private long numerY;
    private byte denom;

    // Transient tag used by the cover viewer's columnar store (CoverData / HashTriple).
    // A rectangle yielded while walking a cover's quadtree carries the kind of leaf it
    // represents (COVER_KIND_STABLE / COVER_KIND_TRIPLE) and its ordinal position among
    // leaves of that kind, so per-leaf lookups (code, color) are O(1) array indexing
    // instead of hashing. Not part of geometric identity; ignored by equals()/hashCode().
    public static final byte COVER_KIND_NONE = 0;
    public static final byte COVER_KIND_STABLE = 1;
    public static final byte COVER_KIND_TRIPLE = 2;
    private byte coverKind = COVER_KIND_NONE;
    private int coverOrdinal = -1;

    private Rectangle(final Interval intervalX, final Interval intervalY) {
        this.intervalX = intervalX;
        this.intervalY = intervalY;
    }

    public void setCoverTag(final byte kind, final int ordinal) {
        this.coverKind = kind;
        this.coverOrdinal = ordinal;
    }

    public byte getCoverKind() {
        return this.coverKind;
    }

    public int getCoverOrdinal() {
        return this.coverOrdinal;
    }

    // Not necessary that x0 <= x1 and y0 <= y1. It gets sorted automatically
    public static Rectangle create(
        final double x0, final double x1, final double y0, final double y1) {
        final Interval intervalX = Interval.create(x0, x1);
        final Interval intervalY = Interval.create(y0, y1);

        return new Rectangle(intervalX, intervalY);
    }


    public static Rectangle initial() {
        Rectangle rect = Rectangle.create(0, Math.PI/2, 0, Math.PI/2);
        rect.setNumerX(1);
        rect.setNumerY(1);
        rect.setDenom((byte) 1);

        return rect;
    }

    public void setNumerX(long val) {
        this.numerX = val;
    }

    public void setNumerY(long val) {
        this.numerY = val;
    }

    public void setDenom(byte expo) {
        this.denom = expo;
    }
    public long getNumerX() {
        return this.numerX;
    }

    public long getNumerY() {
        return this.numerY;
    }

    public byte getDenom() {
        return this.denom;
    }

    public Vector2 center() {
        return Vector2.create(intervalX.center(), intervalY.center());
    }

    public boolean contains(final double x, final double y) {
        return this.intervalX.contains(x) && this.intervalY.contains(y);
    }

    public boolean contains(final Rectangle a) {
        return this.intervalX.contains(a.intervalX) && this.intervalY.contains(a.intervalY);
    }
    public boolean hasOnPlot(final double x, final double y) {
        return this.intervalX.hasOnPlot(x) && this.intervalY.hasOnPlot(y);
    }
    public double radius() {
        return 1.0 / (double) ((long) 1 << denom);
    }


    public String[] getCenter() {
        long radiusTemp = ((long) 1 << denom);
        String radius = "1/" + radiusTemp;
        String xCoord = "";
        String yCoord = "";

        xCoord += numerX + "/" + ((long) 1 << denom);
        yCoord += numerY + "/" + ((long) 1 << denom);
        String center = "(" + xCoord + ", " + yCoord + ")";

        String[] items = {radius, center};
        return items;
    }

    // http://stackoverflow.com/questions/306316/determine-if-two-rectangles-overlap-each-other
    public static boolean intersects(final Rectangle a, final Rectangle b) {
        return a.intervalX.min <= b.intervalX.max && a.intervalX.max >= b.intervalX.min && a.intervalY.min <= b.intervalY.max && a.intervalY.max >= b.intervalY.min;
    }

    public static Rectangle[] subdivide(Rectangle rect) {
        final Vector2 center = rect.center();
        final long oldNumerX = rect.getNumerX();
        final long oldNumerY = rect.getNumerY();
        final byte oldDenom = rect.getDenom();

        final Rectangle upperLeft = Rectangle.create(rect.intervalX.min, center.x, center.y, rect.intervalY.max);

        final Rectangle upperRight = Rectangle.create(center.x, rect.intervalX.max, center.y, rect.intervalY.max);

        final Rectangle lowerLeft = Rectangle.create(rect.intervalX.min, center.x, rect.intervalY.min, center.y);

        final Rectangle lowerRight = Rectangle.create(center.x, rect.intervalX.max, rect.intervalY.min, center.y);

        long newLeft = (2 * oldNumerX) - 1;
        long newRight = (2 * oldNumerX) + 1;
        long newTop = (2 * oldNumerY) + 1;
        long newBottom = (2 * oldNumerY) - 1;
        byte newDenom = (byte) (oldDenom + 1);

        upperLeft.setNumerX(newLeft);
        upperLeft.setNumerY(newTop);
        upperLeft.setDenom(newDenom);

        upperRight.setNumerX(newRight);
        upperRight.setNumerY(newTop);
        upperRight.setDenom(newDenom);

        lowerLeft.setNumerX(newLeft);
        lowerLeft.setNumerY(newBottom);
        lowerLeft.setDenom(newDenom);

        lowerRight.setNumerX(newRight);
        lowerRight.setNumerY(newBottom);
        lowerRight.setDenom(newDenom);

        return new Rectangle[] {upperLeft, upperRight, lowerLeft, lowerRight};
    }

    public static Rectangle createInsideArbitrary(double x, double y, double radius, int extraMag) {
        // x, y, radius are all in radians
        Rectangle outer = Rectangle.create(x-radius, x+radius, y-radius, y+radius);
        Rectangle square = Rectangle.initial();
        while (!outer.contains(square)) {
            Rectangle[] subdivides = subdivide(square);
            for (Rectangle subdivide : subdivides) {
                if (subdivide.hasOnPlot(x, y)) {
                    square = subdivide;
                }
            }
        }

        while (extraMag != 0) {
            Rectangle[] subdivides = subdivide(square);
            for (Rectangle subdivide : subdivides) {
                if (subdivide.hasOnPlot(x, y)) {
                    square = subdivide;
                }
            }
            extraMag--;
        }
        return square;
    }

    // In C++, where we have value types, I wouldn't worry about this, because
    // the optimizer would take care of the copies. But in Java, we have to
    // allocate memory
    @Override
    public Interval project(final Point axis) {
        final Point a = Point.create(this.intervalX.min, this.intervalY.min);

        final double aDot = Point.dot(a, axis);
        double min = aDot;
        double max = aDot;

        final Point b = Point.create(this.intervalX.min, this.intervalY.max);

        final double bDot = Point.dot(b, axis);
        min = Math.min(min, bDot);
        max = Math.max(max, bDot);

        final Point c = Point.create(this.intervalX.max, this.intervalY.max);

        final double cDot = Point.dot(c, axis);
        min = Math.min(min, cDot);
        max = Math.max(max, cDot);

        final Point d = Point.create(this.intervalX.max, this.intervalY.min);

        final double dDot = Point.dot(d, axis);

        min = Math.min(min, dDot);
        max = Math.max(max, dDot);

        return Interval.create(min, max);
    }

    public Interval projectX() {
        return this.intervalX;
    }

    public Interval projectY() {
        return this.intervalY;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.intervalX, this.intervalY);
    }

    @Override
    public boolean equals(final Object obj) {
        final Rectangle other = (Rectangle) obj;
        return this.intervalX.equals(other.intervalX) && this.intervalY.equals(other.intervalY);
    }

    @Override
    public String toString() {
        return this.intervalX + ", " + this.intervalY;
    }
}
