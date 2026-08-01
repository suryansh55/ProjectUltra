package billiards.math;

public enum XYZ {
    X,
    Y,
    Z;

    @Override
    public String toString() {
        switch (this) {
            case X:
                return "x";
            case Y:
                return "y";
            case Z:
                return "z";
        }

        throw new RuntimeException("unable to convert to string: " + this);
    }

    public static XYZ otherAngle(final XYZ angle1, final XYZ angle2) {
        if ((angle1 == X && angle2 == Y) || (angle1 == Y && angle2 == X)) {
            return Z;
        }

        if ((angle1 == X && angle2 == Z) || (angle1 == Z && angle2 == X)) {
            return Y;
        }

        if ((angle1 == Y && angle2 == Z) || (angle1 == Z && angle2 == Y)) {
            return X;
        }

        throw new RuntimeException("invalid angles " + angle1 + ' ' + angle2);
    }
}
