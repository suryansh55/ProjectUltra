package billiards.viewer;

import billiards.codeseq.CodePair;
import billiards.codeseq.TriplePair;
import billiards.geometry.Rectangle;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Compact, columnar in-memory representation of a cover.
 *
 * <p>A cover is a full quadtree over the initial square, serialized in preorder. Every
 * node is one of:
 * <ul>
 *   <li>{@code D} &mdash; internal node, subdivided into 4 quarters (UL, UR, LL, LR);</li>
 *   <li>{@code E} &mdash; empty leaf (legacy {@code H} is treated identically);</li>
 *   <li>{@code S n} &mdash; leaf covered by stable index {@code n};</li>
 *   <li>{@code T n} &mdash; leaf covered by triple index {@code n}.</li>
 * </ul>
 *
 * <p>The geometry of each leaf (its {@link Rectangle}) is fully determined by its position
 * in the tree, so it is <em>not</em> stored: it is reconstructed on demand while walking the
 * preorder {@link #nodeType} stream. This replaces the old representation &mdash; two
 * {@code Map<Rectangle, ...>} holding ~7.2M materialized {@code Rectangle} objects, which cost
 * on the order of a gigabyte and caused out-of-memory failures &mdash; with three primitive
 * arrays totaling a few tens of megabytes.
 */
public final class CoverData {

    public static final byte NODE_D = 0;
    public static final byte NODE_E = 1;
    public static final byte NODE_S = 2;
    public static final byte NODE_T = 3;

    /** Preorder node-type stream, one byte per node. */
    final byte[] nodeType;
    /**
     * Stable index for each {@code S} leaf, in preorder-of-stable-leaves.
     *
     * <p>{@code int}, not {@code char}: covers exist whose {@code stables.txt} holds more than
     * 65536 entries, and the old {@code char} silently wrapped those indices. {@code int} is
     * also the widest that can be used here &mdash; {@link #stableAt} resolves through
     * {@code List.get(int)} &mdash; even though the file format can store 8-byte indices.
     */
    final int[] sCode;
    /** Triple index for each {@code T} leaf, in preorder-of-triple-leaves. */
    final int[] tCode;

    private final List<CodePair> stables;
    private final List<TriplePair> triples;

    public CoverData(final byte[] nodeType, final int[] sCode, final int[] tCode,
                     final List<CodePair> stables, final List<TriplePair> triples) {
        this.nodeType = nodeType;
        this.sCode = sCode;
        this.tCode = tCode;
        this.stables = stables;
        this.triples = triples;
    }

    public int stableCount() {
        return sCode.length;
    }

    public int tripleCount() {
        return tCode.length;
    }

    public CodePair stableAt(final int ordinal) {
        return stables.get(sCode[ordinal]);
    }

    public TriplePair tripleAt(final int ordinal) {
        return triples.get(tCode[ordinal]);
    }

    /** Lazily yields a {@link Rectangle} (tagged {@code COVER_KIND_STABLE}) for every {@code S} leaf. */
    public Iterable<Rectangle> stableRects() {
        return new Iterable<Rectangle>() {
            public Iterator<Rectangle> iterator() {
                return new LeafIterator(NODE_S, Rectangle.COVER_KIND_STABLE);
            }
        };
    }

    /** Lazily yields a {@link Rectangle} (tagged {@code COVER_KIND_TRIPLE}) for every {@code T} leaf. */
    public Iterable<Rectangle> tripleRects() {
        return new Iterable<Rectangle>() {
            public Iterator<Rectangle> iterator() {
                return new LeafIterator(NODE_T, Rectangle.COVER_KIND_TRIPLE);
            }
        };
    }

    /**
     * Preorder walk of the quadtree that reconstructs each cell's geometry from its parent's,
     * using exactly the arithmetic in {@link Rectangle#subdivide} so the intervals are
     * bit-identical to the old materialized rectangles. Only leaves of {@code targetType}
     * are yielded; each is tagged with its kind and ordinal among leaves of that type.
     */
    private final class LeafIterator implements Iterator<Rectangle> {

        private final byte targetType;
        private final byte tagKind;

        // Explicit DFS stack of pending nodes (columnar, to avoid per-node allocation).
        private double[] xMin = new double[256];
        private double[] xMax = new double[256];
        private double[] yMin = new double[256];
        private double[] yMax = new double[256];
        private long[] numX = new long[256];
        private long[] numY = new long[256];
        private byte[] den = new byte[256];
        private int sp = 0;

        private int nodeIndex = 0;   // preorder position of the next node to pop
        private int sSeen = 0;       // number of S leaves visited so far
        private int tSeen = 0;       // number of T leaves visited so far
        private Rectangle next;      // prefetched next matching leaf, or null

        LeafIterator(final byte targetType, final byte tagKind) {
            this.targetType = targetType;
            this.tagKind = tagKind;
            // Root square: [0, pi/2] x [0, pi/2], numerX = numerY = 1, denom = 1.
            push(0.0, Math.PI / 2.0, 0.0, Math.PI / 2.0, 1L, 1L, (byte) 1);
            advance();
        }

        public boolean hasNext() {
            return next != null;
        }

        public Rectangle next() {
            if (next == null) {
                throw new NoSuchElementException();
            }
            final Rectangle result = next;
            advance();
            return result;
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }

        private void advance() {
            while (sp > 0) {
                final int i = --sp;
                final double x0 = xMin[i];
                final double x1 = xMax[i];
                final double y0 = yMin[i];
                final double y1 = yMax[i];
                final long nx = numX[i];
                final long ny = numY[i];
                final byte d = den[i];

                final byte type = nodeType[nodeIndex++];

                if (type == NODE_D) {
                    final double cx = (x0 + x1) / 2.0;
                    final double cy = (y0 + y1) / 2.0;
                    final byte nd = (byte) (d + 1);
                    final long lx = (2 * nx) - 1;
                    final long rx = (2 * nx) + 1;
                    final long ty = (2 * ny) + 1;
                    final long by = (2 * ny) - 1;
                    // Push in reverse of the subdivide() order so children pop as UL, UR, LL, LR.
                    push(cx, x1, y0, cy, rx, by, nd); // lower-right
                    push(x0, cx, y0, cy, lx, by, nd); // lower-left
                    push(cx, x1, cy, y1, rx, ty, nd); // upper-right
                    push(x0, cx, cy, y1, lx, ty, nd); // upper-left
                    continue;
                }

                final int ordinal;
                if (type == NODE_S) {
                    ordinal = sSeen++;
                } else if (type == NODE_T) {
                    ordinal = tSeen++;
                } else {
                    ordinal = -1; // empty leaf
                }

                if (type == targetType) {
                    final Rectangle rect = Rectangle.create(x0, x1, y0, y1);
                    rect.setNumerX(nx);
                    rect.setNumerY(ny);
                    rect.setDenom(d);
                    rect.setCoverTag(tagKind, ordinal);
                    next = rect;
                    return;
                }
            }
            next = null;
        }

        private void push(final double x0, final double x1, final double y0, final double y1,
                          final long nx, final long ny, final byte d) {
            if (sp == xMin.length) {
                grow();
            }
            xMin[sp] = x0;
            xMax[sp] = x1;
            yMin[sp] = y0;
            yMax[sp] = y1;
            numX[sp] = nx;
            numY[sp] = ny;
            den[sp] = d;
            sp++;
        }

        private void grow() {
            final int n = xMin.length * 2;
            xMin = copy(xMin, n);
            xMax = copy(xMax, n);
            yMin = copy(yMin, n);
            yMax = copy(yMax, n);
            numX = copyL(numX, n);
            numY = copyL(numY, n);
            den = copyB(den, n);
        }

        private double[] copy(final double[] a, final int n) {
            final double[] b = new double[n];
            System.arraycopy(a, 0, b, 0, a.length);
            return b;
        }

        private long[] copyL(final long[] a, final int n) {
            final long[] b = new long[n];
            System.arraycopy(a, 0, b, 0, a.length);
            return b;
        }

        private byte[] copyB(final byte[] a, final int n) {
            final byte[] b = new byte[n];
            System.arraycopy(a, 0, b, 0, a.length);
            return b;
        }
    }
}
