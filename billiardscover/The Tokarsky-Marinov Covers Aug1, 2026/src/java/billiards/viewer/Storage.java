package billiards.viewer;

import billiards.codeseq.CodeSequence;
import billiards.codeseq.CodeType;
import billiards.codeseq.InitialAngles;
import billiards.geometry.ConvexPolygon;
import billiards.geometry.LineSegment;
import billiards.geometry.Rectangle;
import billiards.math.Equation;
import billiards.math.LinCom;
import billiards.math.XYEta;

import org.apache.commons.math3.util.Precision;
import org.eclipse.collections.api.list.ImmutableList;

public abstract class Storage implements Comparable<Storage> {

    public final CodeSequence classCodeSeq;
    public final InitialAngles angles;
    public final CodeType type;
    private final ImmutableList<Equation> equations;

    // private constructor ensures we can only subclass from within the class
    private Storage(final CodeSequence classCodeSeq, final InitialAngles angles, final CodeType type, final ImmutableList<Equation> equations) {
        this.classCodeSeq = classCodeSeq;
        this.angles = angles;
        this.type = type;
        this.equations = equations;
    }

    public static final class Stable extends Storage {
        public final ConvexPolygon polygon;

        public Stable(final CodeSequence classCodeSeq,
                      final InitialAngles angles,
                      final CodeType type,
                      final ImmutableList<Equation> equations,
                      final ConvexPolygon polygon) {
            super(classCodeSeq, angles, type, equations);
            this.polygon = polygon;
        }

        @Override
        public boolean intersects(final Rectangle rect) {
            return this.polygon.intersects(rect);
        }

        @Override
        public boolean intersects(final ConvexPolygon rect) {
            return this.polygon.intersects(rect);
        }

        @Override
        public double getMinX() {
            return this.polygon.vertices.collectDouble(point -> point.x).min();
        }

        @Override
        public double getMaxX() {
            return this.polygon.vertices.collectDouble(point -> point.x).max();
        }

        @Override
        public double getMinY() {
            return this.polygon.vertices.collectDouble(point -> point.y).min();
        }

        @Override
        public double getMaxY() {
            return this.polygon.vertices.collectDouble(point -> point.y).max();
        }
    }

    public static final class Unstable extends Storage {

        public final LinCom<XYEta> constraint;
        public final LineSegment lineSegment;

        public Unstable(final CodeSequence classCodeSeq, final InitialAngles angles,
                        final CodeType type,
                        final ImmutableList<Equation> equations,
                        final LineSegment lineSegment) {
            super(classCodeSeq, angles, type, equations);
            this.constraint = classCodeSeq.constraint(angles.first, angles.second);
            this.lineSegment = lineSegment;
        }

        @Override
        public boolean intersects(final Rectangle rect) {
            return this.lineSegment.intersects(rect);
        }

        @Override
        public boolean intersects(final ConvexPolygon rect) {
            return this.lineSegment.intersects(rect);
        }

        @Override
        public double getMinX() {
            return Math.min(this.lineSegment.start.x, this.lineSegment.end.x);
        }

        @Override
        public double getMaxX() {
            return Math.max(this.lineSegment.start.x, this.lineSegment.end.x);
        }

        @Override
        public double getMinY() {
            return Math.min(this.lineSegment.start.y, this.lineSegment.end.y);
        }

        @Override
        public double getMaxY() {
            return Math.max(this.lineSegment.start.y, this.lineSegment.end.y);
        }
    }

    public abstract boolean intersects(final Rectangle rect);
    public abstract boolean intersects(final ConvexPolygon rect);
    public abstract double getMinX();
    public abstract double getMaxX();
    public abstract double getMinY();
    public abstract double getMaxY();

    public boolean isPositive(final double rx, final double ry) {
        for (final Equation equation : this.equations) {
            final double result = equation.evalf(rx, ry);
            if (Precision.compareTo(result, 0, 1e-14) < 0) {
                return false;
            }
        }

        return true;
    }

    // All of these methods are implemented using the code sequence
    // so they are the same for the two subclasses
    @Override
    public int compareTo(final Storage other) {
        return this.classCodeSeq.compareTo(other.classCodeSeq);
    }

    @Override
    public String toString() {
        return this.classCodeSeq + ", " + this.angles;
    }

    @Override
    public boolean equals(final Object obj) {
        final Storage other = (Storage) obj;
        return this.classCodeSeq.equals(other.classCodeSeq);
    }

    @Override
    public int hashCode() {
        return this.classCodeSeq.hashCode();
    }
}
