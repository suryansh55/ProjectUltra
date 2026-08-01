package billiards.viewer;

import billiards.codeseq.CodePair;
import billiards.wrapper.CodeInfo;
import billiards.codeseq.CodeSequence;
import billiards.codeseq.InitialAngles;
import billiards.codeseq.TriplePair;
import billiards.geometry.ConvexPolygon;
import billiards.geometry.Rectangle;
import billiards.geometry.Point;
import billiards.math.XYZ;

import com.google.common.base.Splitter;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.list.primitive.IntList;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.impl.list.mutable.FastList;
import org.eclipse.collections.impl.map.mutable.UnifiedMap;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class Cover {

    // TODO change this
    public static InitialAngles parseInitialAngles(final String str) {
        switch (str) {
            case "xy":
                return new InitialAngles(XYZ.X, XYZ.Y);
            case "xz":
                return new InitialAngles(XYZ.X, XYZ.Z);
            case "yx":
                return new InitialAngles(XYZ.Y, XYZ.X);
            case "yz":
                return new InitialAngles(XYZ.Y, XYZ.Z);
            case "zx":
                return new InitialAngles(XYZ.Z, XYZ.X);
            case "zy":
                return new InitialAngles(XYZ.Z, XYZ.Y);
            default:
                throw new RuntimeException("unable to parse initial angles: " + str);
        }
    }

    public static ConvexPolygon parsePolygon(final String string) {

        final MutableList<Point> vertices = new FastList<>();

        final String[] lines = StringUtils.split(string, '\n');

        for (final String line : lines) {
            final String[] coords = StringUtils.split(line, ' ');

            final double x = CodeInfo.rationalToRadians(coords[0]);
            final double y = CodeInfo.rationalToRadians(coords[1]);

            final Point vertex = Point.create(x, y);
            vertices.add(vertex);
        }

        return ConvexPolygon.create(vertices.toImmutable());
    }

//    public static Rectangle parseRectangle(final String string) {
//
//        final String[] coords = StringUtils.split(string, ' ');
//
//        final double xMin = CodeInfo.rationalToRadians(coords[0]);
//        final double xMax = CodeInfo.rationalToRadians(coords[1]);
//
//        final double yMin = CodeInfo.rationalToRadians(coords[2]);
//        final double yMax = CodeInfo.rationalToRadians(coords[3]);
//
//        return Rectangle.create(xMin, xMax, yMin, yMax);
//    }

    public static List<CodePair> parseStables(final String string) {

        final MutableList<CodePair> stablePairs = new FastList<>();

        final String[] lines = StringUtils.split(string, '\n');

        for (int i = 0; i < lines.length; ++i) {

            final String line = lines[i].replace(":", ",");

            final String[] comps = StringUtils.split(line, ',');

            final int fileIndex = Integer.parseInt(comps[0].trim());

            if (fileIndex != i) {
                throw new RuntimeException("mismatched indices: " + fileIndex + ", " + i);
            }

            final String codeString = comps[1].trim();

            final IntList codeList = Utils.splitString(codeString).get();

            final CodeSequence codeSeq = new CodeSequence(codeList);

            final String initialAnglesString = comps[2].trim();

            final InitialAngles initialAngles = parseInitialAngles(initialAnglesString);

            final CodePair stablePair = new CodePair(codeSeq, initialAngles);

            stablePairs.add(stablePair);
        }

        return stablePairs;
    }

    public static List<TriplePair> parseTriples(final String string) {

        final MutableList<TriplePair> triplePairs = new FastList<>();

        final String[] lines = StringUtils.split(string, '\n');

        for (int i = 0; i < lines.length; ++i) {

            final String line = lines[i].replace(":", ",").replace(";", ",");

            final String[] comps = StringUtils.split(line, ',');

            final int fileIndex = Integer.parseInt(comps[0].trim());

            if (fileIndex != i) {
                throw new RuntimeException("mismatched indices: " + fileIndex + ", " + i);
            }

            final String stableNegString = comps[1].trim();
            final String stableNegAnglesString = comps[2].trim();

            final String unstableString = comps[3].trim();
            final String unstableAnglesString = comps[4].trim();

            final String stablePosString = comps[5].trim();
            final String stablePosAnglesString = comps[6].trim();

            final IntList stableNegList = Utils.splitString(stableNegString).get();
            final IntList unstableList = Utils.splitString(unstableString).get();
            final IntList stablePosList = Utils.splitString(stablePosString).get();

            final CodeSequence stableNeg = new CodeSequence(stableNegList);
            final CodeSequence unstable = new CodeSequence(unstableList);
            final CodeSequence stablePos = new CodeSequence(stablePosList);

            final InitialAngles stableNegAngles = parseInitialAngles(stableNegAnglesString);
            final InitialAngles unstableAngles = parseInitialAngles(unstableAnglesString);
            final InitialAngles stablePosAngles = parseInitialAngles(stablePosAnglesString);

            final CodePair stableNegPair = new CodePair(stableNeg, stableNegAngles);
            final CodePair unstablePair = new CodePair(unstable, unstableAngles);
            final CodePair stablePosPair = new CodePair(stablePos, stablePosAngles);

            final TriplePair triplePair = new TriplePair(stableNegPair, unstablePair, stablePosPair);

            triplePairs.add(triplePair);
        }

        return triplePairs;
    }

    public static List<Object> parseCover(final String coverString, final Rectangle square, final List<CodePair> stables, final List<TriplePair> triples) {
        List<Object> list = new ArrayList<Object>();
        final Iterator<String> tokens = Splitter.on(' ').split(coverString).iterator();

        final MutableMap<Rectangle, CodePair> stableCover = new UnifiedMap<>();
        final MutableMap<Rectangle, TriplePair> tripleCover = new UnifiedMap<>();

        parseCover(tokens, square, stables, triples,stableCover, tripleCover);

        if (tokens.hasNext()) {
            throw new RuntimeException("unused tokens when parsing a cover");
        }
        list.add(stableCover);
        list.add(tripleCover);

        return list;
    }

    private static void parseCover(final Iterator<String> tokens, final Rectangle square, final List<CodePair> stables, final List<TriplePair> triples, final Map<Rectangle, CodePair> stableCover, final Map<Rectangle, TriplePair> tripleCover) {

        final String token = tokens.next();

        if (token.equals("E")) {
            // Nothing to do
        } else if (token.equals("H")) {
            // Also do nothing
        } else if (token.equals("S")) {
            final String str = tokens.next();
            final int index = Integer.parseInt(str);

            final CodePair stable = stables.get(index);
            stableCover.put(square, stable);
        } else if (token.equals("T")) {
            final String str = tokens.next();
            final int index = Integer.parseInt(str);

            final TriplePair triple = triples.get(index);
            tripleCover.put(square, triple);

        } else if (token.equals("D")) {

            final Rectangle[] quarters = Rectangle.subdivide(square);

            for (final Rectangle quarter : quarters) {
                parseCover(tokens, quarter, stables, triples, stableCover, tripleCover);
            }

        } else {
            throw new RuntimeException("unknown cover token: " + token);
        }
    }
}
