package billiards.math;

public abstract class Equation {

    // The coefficients are all ints, but the calculations are faster
    // if we save them as doubles. In Java, doing an int -> double conversion
    // is safe, since a 64-bit double can always store a 32-bit int
    // (but long -> double is not safe!)
    protected final double[] coeffs;

    protected Equation(final int[] intCoeffs) {
        this.coeffs = convertDouble(intCoeffs);
    }

    private static double[] convertDouble(final int[] intCoeffs) {
        final double[] doubleCoeffs = new double[intCoeffs.length];
        for (int i = 0; i < intCoeffs.length; ++i) {
            doubleCoeffs[i] = intCoeffs[i];
        }
        return doubleCoeffs;
    }

    protected String formatSum(final String trig) {

        final StringBuilder builder = new StringBuilder();

        if (this.coeffs.length == 0) {
            builder.append('0');
            return builder.toString();
        }

        // Set to false once we add the first coefficient
        boolean front = true;

        for (int i = 0; i < this.coeffs.length; i += 3) {
            final int trigCoeff = (int) this.coeffs[i];
            final int xCoeff = (int) this.coeffs[i + 1];
            final int yCoeff = (int) this.coeffs[i + 2];

            // add a positive sign if it is positive and not at the front
            if ((trigCoeff > 0) && !front) {
                builder.append('+');
            }

            if (trigCoeff == 1) {
                // do nothing
            } else if (trigCoeff == -1) {
                builder.append('-');
            } else if (trigCoeff != 0) {
                builder.append(trigCoeff);
            } else {
                throw new RuntimeException("zero trig coefficient in equation");
            }

            builder.append(trig).append('(');
            formatArg(builder, xCoeff, yCoeff);
            builder.append(')');

            front = false;
        }

        return builder.toString();
    }

    private static void formatArg(final StringBuilder builder, final int x, final int y) {

        if (x == 0 && y == 0) {
            builder.append('0');
            return;
        }

        // Set to false once we add the first coefficient
        boolean front = true;

        // add a positive sign if it is positive and not at the front
        if ((x > 0) && !front) {
            builder.append('+');
        }

        if (x == -1) {
            builder.append('-');
        } else if ((x != 0) && (x != 1)) {
            builder.append(x);
        }

        // if coeff == 0, we ignore it
        // otherwise, we append it and set front to false
        if (x != 0) {
            builder.append('x');
            front = false;
        }

        // Now do y
        if ((y > 0) && !front) {
            builder.append('+');
        }

        if (y == -1) {
            builder.append('-');
        } else if ((y != 0) && (y != 1)) {
            builder.append(y);
        }

        // if coeff == 0, we ignore it
        // otherwise, we append it and set front to false
        if (y != 0) {
            builder.append('y');
            front = false;
        }
    }

    public abstract double evalf(final double x, final double y);
}
