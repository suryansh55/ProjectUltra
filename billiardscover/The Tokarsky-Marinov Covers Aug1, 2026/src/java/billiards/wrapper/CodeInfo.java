package billiards.wrapper;

import billiards.geometry.Point;
import billiards.math.CosEquation;
import billiards.math.SinEquation;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.list.mutable.FastList;

public final class CodeInfo {

    public final ImmutableList<Point> points;
    public final ImmutableList<SinEquation> sinEquations;
    public final ImmutableList<CosEquation> cosEquations;

    public CodeInfo(final String points, final String sinEquations, final String cosEquations) {
        this.points = parsePoints(points);
        this.sinEquations = parseSinEquations(sinEquations);
        this.cosEquations = parseCosEquations(cosEquations);
    }

    private static ImmutableList<SinEquation> parseSinEquations(final String string) {

        final String[] strings = StringUtils.split(string, '\n');

        final MutableList<SinEquation> equations = new FastList<>();

        for (final String str : strings) {

            final String[] stringCoeffs = StringUtils.split(str, ' ');

            final int[] coeffs = new int[stringCoeffs.length];
            for (int i = 0; i < stringCoeffs.length; ++i) {
                coeffs[i] = Integer.parseInt(stringCoeffs[i]);
            }

            final SinEquation eq = new SinEquation(coeffs);

            equations.add(eq);
        }

        return equations.toImmutable();
    }

    private static ImmutableList<CosEquation> parseCosEquations(final String string) {

        final String[] strings = StringUtils.split(string, '\n');

        final MutableList<CosEquation> equations = new FastList<>();

        for (final String str : strings) {

            final String[] stringCoeffs = StringUtils.split(str, ' ');

            final int[] coeffs = new int[stringCoeffs.length];
            for (int i = 0; i < stringCoeffs.length; ++i) {
                coeffs[i] = Integer.parseInt(stringCoeffs[i]);
            }

            final CosEquation eq = new CosEquation(coeffs);

            equations.add(eq);
        }

        return equations.toImmutable();
    }

    private static ImmutableList<Point> parsePoints(final String string) {

        final String[] strings = StringUtils.split(string, '\n');

        final MutableList<Point> points = new FastList<>();

        for (final String str : strings) {
            final String[] split = StringUtils.split(str, ' ');

            final double x = rationalToRadians(split[0]);
            final double y = rationalToRadians(split[1]);

            final Point point = Point.create(x, y);
            points.add(point);
        }

        return points.toImmutable();
    }

    public static double rationalToRadians(final String rat) {
        if (rat.indexOf('/') == -1) {
            // Does not contain /, so an integer
            final double num = Integer.parseInt(rat);
            return num * Math.PI / 2.0;
        } else {
            final String[] nums = StringUtils.split(rat, '/');
            final double numer = Integer.parseInt(nums[0]);
            final double denom = Integer.parseInt(nums[1]);

            return numer / denom * Math.PI / 2.0;
        }
    }

}
