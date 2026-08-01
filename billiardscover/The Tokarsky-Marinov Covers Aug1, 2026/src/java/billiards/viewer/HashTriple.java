package billiards.viewer;

import billiards.codeseq.CodePair;
import billiards.codeseq.TriplePair;
import billiards.geometry.Rectangle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import javafx.scene.paint.Color;

/**
 * A loaded cover, as consumed by the viewer.
 *
 * <p>Previously this held three {@code Map<Rectangle, ...>} keyed by materialized rectangles
 * (stable codes, triple codes, colors) &mdash; on the order of a gigabyte for a large cover,
 * which caused out-of-memory failures. It now wraps a {@link CoverData} (structure and codes,
 * stored columnar) and keeps only per-leaf color as a palette index in two {@code char[]}
 * arrays. Rectangles are reconstructed lazily while walking the tree, and each carries its
 * leaf ordinal (see {@link Rectangle#getCoverOrdinal()}), so the lookups below stay O(1)
 * without any hash map. The public API is unchanged, so the viewer's render and selection
 * code is untouched.
 */
public final class HashTriple {

    private final CoverData data;
    private final char[] stableColor; // palette index per stable leaf; 0 = base color
    private final char[] tripleColor; // palette index per triple leaf; 0 = base color

    private final ArrayList<Color> palette = new ArrayList<Color>();
    private final HashMap<Color, Character> paletteIndex = new HashMap<Color, Character>();

    public HashTriple(final CoverData data) {
        this.data = data;
        this.stableColor = new char[data.stableCount()];
        this.tripleColor = new char[data.tripleCount()];
        internColor(Color.BLACK); // palette[0] is the default/base color
    }

    private char internColor(final Color color) {
        final Character existing = paletteIndex.get(color);
        if (existing != null) {
            return existing.charValue();
        }
        final char index = (char) palette.size();
        palette.add(color);
        paletteIndex.put(color, Character.valueOf(index));
        return index;
    }

    public Iterable<Rectangle> stableEntrySet() {
        return data.stableRects();
    }

    public Iterable<Rectangle> tripleEntrySet() {
        return data.tripleRects();
    }

    public CodePair getStable(final Rectangle rect) {
        final int ordinal = rect.getCoverOrdinal();
        return ordinal >= 0 ? data.stableAt(ordinal) : null;
    }

    public TriplePair getTriple(final Rectangle rect) {
        final int ordinal = rect.getCoverOrdinal();
        return ordinal >= 0 ? data.tripleAt(ordinal) : null;
    }

    public Color getColor(final Rectangle rect) {
        final int ordinal = rect.getCoverOrdinal();
        if (ordinal < 0) {
            return palette.get(0);
        }
        final byte kind = rect.getCoverKind();
        if (kind == Rectangle.COVER_KIND_STABLE && ordinal < stableColor.length) {
            return palette.get(stableColor[ordinal]);
        }
        if (kind == Rectangle.COVER_KIND_TRIPLE && ordinal < tripleColor.length) {
            return palette.get(tripleColor[ordinal]);
        }
        return palette.get(0);
    }

    public void put(final Rectangle rect, final Color color) {
        final int ordinal = rect.getCoverOrdinal();
        if (ordinal < 0) {
            return;
        }
        final char index = internColor(color);
        final byte kind = rect.getCoverKind();
        if (kind == Rectangle.COVER_KIND_STABLE && ordinal < stableColor.length) {
            stableColor[ordinal] = index;
        } else if (kind == Rectangle.COVER_KIND_TRIPLE && ordinal < tripleColor.length) {
            tripleColor[ordinal] = index;
        }
    }

    public void clear() {
        Arrays.fill(stableColor, (char) 0);
        Arrays.fill(tripleColor, (char) 0);
    }
}
