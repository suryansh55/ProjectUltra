package billiards.viewer;

import billiards.codeseq.CodePair;
import billiards.codeseq.TriplePair;

import org.eclipse.collections.impl.list.mutable.primitive.ByteArrayList;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * Reads and writes covers in two forms:
 * <ul>
 *   <li>the legacy space-separated text stream ({@code cover.txt}), and</li>
 *   <li>the compact binary {@code cover.cbin} &mdash; three compressed columns (node-type
 *       stream, stable indices, triple indices) behind a small header.</li>
 * </ul>
 * Both produce an identical {@link CoverData}. The binary form is ~130x smaller than the text
 * and parses without any string tokenization or {@code Integer.parseInt}.
 *
 * <p>The columns are LZMA2 as of {@link #VERSION_4} and were Deflate before it; every version is
 * still readable. See {@link Lzma} for why the codec changed and what it cost.
 *
 * <p>Deliberately limited to Java 1.8 APIs (plus XZ for Java, which targets Java 7) so it builds
 * on the professor's Oracle JDK 1.8.0_66 toolchain.
 */
public final class CoverCodec {

    static final byte[] MAGIC = {'C', 'B', 'I', 'N'};

    /** Original layout: 32-bit counts and block lengths, indices always 16-bit. */
    static final byte VERSION_1 = 1;
    /**
     * Same layout with 64-bit counts and block lengths, indices still 16-bit. Superseded by
     * {@link #VERSION_3}; still read so that any file already written stays loadable.
     */
    static final byte VERSION_2 = 2;
    /**
     * 64-bit counts and block lengths, plus an explicit index width (2, 4 or 8 bytes) in the
     * header. Needed above 2^30 leaves, where v1's {@code int} raw-length field overflows, and
     * above 65536 code-table entries, where a 16-bit index cannot name every entry. Written only
     * when a cover actually exceeds v1's range, so existing covers keep producing byte-identical
     * v1 output.
     */
    static final byte VERSION_3 = 3;
    /**
     * v3's layout with the three blocks stored as LZMA2 rather than Deflate, and a byte in the
     * header naming the dictionary size ({@link Lzma#DICT_BITS}). About 40% smaller than v3 on
     * the same columns &mdash; see {@link Lzma} for why and for what it costs. This is what the
     * packer writes; v1&ndash;v3 stay readable so no existing pack has to be rebuilt.
     */
    static final byte VERSION_4 = 4;

    private CoverCodec() {
    }

    // ---------------------------------------------------------------- text

    /**
     * Parses the legacy {@code cover.txt} token stream into a {@link CoverData}. Scans the
     * character sequence directly instead of splitting it, avoiding ~16.7M transient substrings
     * and {@code parseInt} calls for a large cover.
     */
    public static CoverData parseText(final String cover, final List<CodePair> stables,
                                      final List<TriplePair> triples) {
        final ByteArrayList nodeType = new ByteArrayList();
        final IntArrayList sCode = new IntArrayList();
        final IntArrayList tCode = new IntArrayList();

        final int n = cover.length();
        int i = 0;
        while (i < n) {
            final char c = cover.charAt(i);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                i++;
                continue;
            }
            if (c == 'D') {
                nodeType.add(CoverData.NODE_D);
                i++;
            } else if (c == 'E' || c == 'H') {
                nodeType.add(CoverData.NODE_E);
                i++;
            } else if (c == 'S') {
                i++;
                final int value = readInt(cover, i, n);
                i = intEnd;
                sCode.add(checkIndex(value, stables.size(), "stable", i));
                nodeType.add(CoverData.NODE_S);
            } else if (c == 'T') {
                i++;
                final int value = readInt(cover, i, n);
                i = intEnd;
                tCode.add(checkIndex(value, triples.size(), "triple", i));
                nodeType.add(CoverData.NODE_T);
            } else {
                throw new RuntimeException("unknown cover token '" + c + "' at offset " + i);
            }
        }

        return new CoverData(nodeType.toArray(), sCode.toArray(), tCode.toArray(), stables, triples);
    }

    // An index must name an entry of its code table; anything else would surface later as an
    // IndexOutOfBoundsException inside the viewer's render loop.
    private static int checkIndex(final int value, final int tableSize, final String what,
                                  final int offset) {
        if (value < 0 || value >= tableSize) {
            throw new RuntimeException(what + " index " + value + " near offset " + offset
                + " has no entry in " + what + "s.txt, which holds " + tableSize + " entries");
        }
        return value;
    }

    // readInt scans a non-negative integer starting at or after `from`, skipping leading
    // whitespace, and reports where it stopped via the intEnd field (avoids allocating a
    // wrapper just to return two values).
    private static int intEnd;

    private static int readInt(final String s, final int from, final int n) {
        int i = from;
        while (i < n) {
            final char c = s.charAt(i);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                i++;
            } else {
                break;
            }
        }
        long value = 0;
        boolean any = false;
        while (i < n) {
            final char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                value = value * 10 + (c - '0');
                if (value > Integer.MAX_VALUE) {
                    throw new RuntimeException("index at offset " + from
                        + " is impossibly large (over " + Integer.MAX_VALUE + ")");
                }
                any = true;
                i++;
            } else {
                break;
            }
        }
        if (!any) {
            throw new RuntimeException("expected integer at offset " + from);
        }
        intEnd = i;
        return (int) value;
    }

    // ---------------------------------------------------------------- binary

    public static void writeBinaryFile(final CoverData data, final String path) throws IOException {
        final FileOutputStream fos = new FileOutputStream(path);
        try {
            final BufferedOutputStream bos = new BufferedOutputStream(fos, 1 << 16);
            writeBinary(data, bos);
            bos.flush();
        } finally {
            fos.close();
        }
    }

    /**
     * Writes an in-memory {@link CoverData} as cbin v4, byte-for-byte what
     * {@link CoverStream#packTextToCbin} would produce for the same cover. Kept for small covers
     * and tests; the converter uses the streaming packer instead, which never builds a
     * {@code CoverData} and so is not bounded by the heap. Under
     * {@link CoverStream#LEGACY_DEFLATE} this emits the older Deflate forms (v1 or v3).
     */
    public static void writeBinary(final CoverData data, final OutputStream out) throws IOException {
        // Width is chosen from the indices actually present: this writer has the whole cover in
        // memory, so unlike the streaming packer it does not need the code table sizes to decide.
        final int width = Math.max(widthFor(data.sCode), widthFor(data.tCode));
        final boolean v1 = width == CoverStream.WIDTH_16;
        if (data.sCode.length > CoverStream.MAX_ARRAY / width
            || data.tCode.length > CoverStream.MAX_ARRAY / width) {
            throw new IOException("cover is too large for the in-memory writer ("
                + data.sCode.length + " stable / " + data.tCode.length
                + " triple leaves); use CoverStream.packTextToCbin");
        }
        final DataOutputStream dos = new DataOutputStream(out);
        dos.write(MAGIC);
        if (v1 && CoverStream.LEGACY_DEFLATE) {
            dos.writeByte(VERSION_1);
            dos.writeInt(data.nodeType.length);
            dos.writeInt(data.sCode.length);
            dos.writeInt(data.tCode.length);
            writeBlock(dos, data.nodeType);
            writeBlock(dos, indicesToBytes(data.sCode, width));
            writeBlock(dos, indicesToBytes(data.tCode, width));
        } else if (CoverStream.LEGACY_DEFLATE) {
            dos.writeByte(VERSION_3);
            dos.writeByte(width);
            dos.writeLong(data.nodeType.length);
            dos.writeLong(data.sCode.length);
            dos.writeLong(data.tCode.length);
            writeWideBlock(dos, data.nodeType);
            writeWideBlock(dos, indicesToBytes(data.sCode, width));
            writeWideBlock(dos, indicesToBytes(data.tCode, width));
        } else {
            final int dictBits = Lzma.dictBitsFor(
                Math.max(data.nodeType.length, data.sCode.length * (long) width));
            dos.writeByte(VERSION_4);
            dos.writeByte(width);
            dos.writeByte(dictBits);
            dos.writeLong(data.nodeType.length);
            dos.writeLong(data.sCode.length);
            dos.writeLong(data.tCode.length);
            // Same stride-aware settings the streaming packer uses, so a cover written either
            // way comes out the same size.
            writeLzmaBlock(dos, data.nodeType, 1, dictBits);
            writeLzmaBlock(dos, indicesToBytes(data.sCode, width), width, dictBits);
            writeLzmaBlock(dos, indicesToBytes(data.tCode, width), width, dictBits);
        }
        dos.flush();
    }

    private static int widthFor(final int[] indices) {
        int max = 0;
        for (int i = 0; i < indices.length; i++) {
            if (indices[i] > max) {
                max = indices[i];
            }
        }
        return CoverStream.widthFor(max + 1L);
    }

    public static CoverData readBinaryFile(final String path, final List<CodePair> stables,
                                           final List<TriplePair> triples) throws IOException {
        final FileInputStream fis = new FileInputStream(path);
        try {
            final BufferedInputStream bis = new BufferedInputStream(fis, 1 << 16);
            return readBinary(bis, stables, triples);
        } finally {
            fis.close();
        }
    }

    public static CoverData readBinary(final InputStream in, final List<CodePair> stables,
                                       final List<TriplePair> triples) throws IOException {
        final DataInputStream dis = new DataInputStream(in);
        final byte[] magic = new byte[MAGIC.length];
        dis.readFully(magic);
        for (int i = 0; i < MAGIC.length; i++) {
            if (magic[i] != MAGIC[i]) {
                throw new IOException("not a cover.cbin file (bad magic)");
            }
        }
        final int version = dis.readUnsignedByte();
        if (version == VERSION_1) {
            final int nodeCount = dis.readInt();
            final int stableCount = dis.readInt();
            final int tripleCount = dis.readInt();

            final byte[] nodeTypeBytes = readBlock(dis, nodeCount);
            final int[] sCode = bytesToIndices(
                readBlock(dis, stableCount * 2), stableCount, CoverStream.WIDTH_16);
            final int[] tCode = bytesToIndices(
                readBlock(dis, tripleCount * 2), tripleCount, CoverStream.WIDTH_16);

            return new CoverData(nodeTypeBytes, sCode, tCode, stables, triples);
        }
        if (version == VERSION_2) {
            return readBinaryWide(dis, CoverStream.WIDTH_16, 0, stables, triples);
        }
        if (version == VERSION_3 || version == VERSION_4) {
            final int width = dis.readUnsignedByte();
            if (width != CoverStream.WIDTH_16 && width != CoverStream.WIDTH_32
                && width != CoverStream.WIDTH_64) {
                throw new IOException("cover.cbin declares an index width of " + width
                    + " bytes; only 2, 4 and 8 are defined");
            }
            final int dictBits = version == VERSION_4 ? dis.readUnsignedByte() : 0;
            if (dictBits != 0) {
                Lzma.dictSize(dictBits);   // reject a corrupt dictionary size up front
            }
            return readBinaryWide(dis, width, dictBits, stables, triples);
        }
        throw new IOException("unsupported cover.cbin version: " + version);
    }

    /**
     * v2/v3/v4 read path. The raw stable column is {@code count * width} bytes, which for a very
     * large cover exceeds what a single {@code byte[]} can hold, so each block is decompressed
     * incrementally into its destination array through a small staging buffer rather than
     * being materialized first. {@code dictBits} of 0 selects Deflate (v2/v3), anything else
     * LZMA2 (v4).
     */
    private static CoverData readBinaryWide(final DataInputStream dis, final int width,
                                            final int dictBits, final List<CodePair> stables,
                                            final List<TriplePair> triples) throws IOException {
        final int nodeCount = checkFitsArray(dis.readLong(), "node");
        final int stableCount = checkFitsArray(dis.readLong(), "stable");
        final int tripleCount = checkFitsArray(dis.readLong(), "triple");

        final byte[] nodeTypeBytes = new byte[nodeCount];
        inflateBlockInto(dis, nodeCount, nodeTypeBytes, dictBits);
        final int[] sCode = new int[stableCount];
        inflateBlockIntoIndices(dis, sCode, width, dictBits);
        final int[] tCode = new int[tripleCount];
        inflateBlockIntoIndices(dis, tCode, width, dictBits);

        return new CoverData(nodeTypeBytes, sCode, tCode, stables, triples);
    }

    private static int checkFitsArray(final long count, final String what) throws IOException {
        if (count < 0 || count > CoverStream.MAX_ARRAY) {
            throw new IOException("this cover has " + count + " " + what
                + " entries, more than a Java array can hold (" + CoverStream.MAX_ARRAY
                + "); loading it needs CoverData reworked into chunked segments");
        }
        return (int) count;
    }

    // Reads a wide [long rawLen][long compLen][data] block and expands it straight into dest.
    private static void inflateBlockInto(final DataInputStream dis, final long expectedRawLen,
                                         final byte[] dest, final int dictBits) throws IOException {
        final WideBlock in = openWideBlock(dis, expectedRawLen, dictBits);
        int off = 0;
        while (off < dest.length) {
            final int n = in.read(dest, off, dest.length - off);
            if (n < 0) {
                throw new IOException("decompressed " + off + " bytes, expected " + dest.length);
            }
            off += n;
        }
        in.finish();
    }

    /**
     * Expands one index column of {@code width}-byte little-endian values into {@code dest}.
     * An index is accumulated a byte at a time so that a value may straddle any number of
     * staging-buffer refills, which keeps the three widths on one code path.
     */
    private static void inflateBlockIntoIndices(final DataInputStream dis, final int[] dest,
                                                final int width, final int dictBits)
            throws IOException {
        final WideBlock in = openWideBlock(dis, dest.length * (long) width, dictBits);
        final byte[] staging = new byte[1 << 16];
        int outIndex = 0;
        long acc = 0;
        int have = 0;
        while (outIndex < dest.length) {
            final int n = in.read(staging, 0, staging.length);
            if (n < 0) {
                throw new IOException("index block ended after " + outIndex + " of " + dest.length);
            }
            for (int i = 0; i < n; i++) {
                acc |= ((long) (staging[i] & 0xff)) << (8 * have);
                have++;
                if (have == width) {
                    if (acc < 0 || acc > Integer.MAX_VALUE) {
                        throw new IOException("index " + acc + " at position " + outIndex
                            + " exceeds what the viewer can address: the code tables are "
                            + "List<CodePair> and a leaf resolves through List.get(int)");
                    }
                    dest[outIndex++] = (int) acc;
                    acc = 0;
                    have = 0;
                    if (outIndex == dest.length) {
                        break;
                    }
                }
            }
        }
        in.finish();
    }

    /** One wide block being read: the decompressing stream, and the bounded source under it. */
    private static final class WideBlock {

        private final InputStream in;
        private final BoundedStream source;

        WideBlock(final InputStream in, final BoundedStream source) {
            this.in = in;
            this.source = source;
        }

        int read(final byte[] b, final int off, final int len) throws IOException {
            return in.read(b, off, len);
        }

        /**
         * Drains the codec's trailer and then anything the codec chose not to read, so the shared
         * stream is left exactly at the start of the next block whatever the decoder does at the
         * end of its input.
         */
        void finish() throws IOException {
            final byte[] scratch = new byte[64];
            while (in.read(scratch, 0, scratch.length) > 0) {
                continue;
            }
            source.drain();
        }
    }

    private static WideBlock openWideBlock(final DataInputStream dis, final long expectedRawLen,
                                           final int dictBits) throws IOException {
        final long rawLen = dis.readLong();
        final long compLen = dis.readLong();
        if (rawLen != expectedRawLen) {
            throw new IOException("block length mismatch: header " + expectedRawLen
                + " vs block " + rawLen);
        }
        final BoundedStream bounded = new BoundedStream(dis, compLen);
        final InputStream in = dictBits == 0
            ? new InflaterInputStream(bounded, new Inflater(), 1 << 16)
            : Lzma.decompressor(bounded, dictBits);
        return new WideBlock(in, bounded);
    }

    /** Caps a shared sequential stream at one block's compressed length. */
    private static final class BoundedStream extends InputStream {

        private final InputStream in;
        private long remaining;

        BoundedStream(final InputStream in, final long remaining) {
            this.in = in;
            this.remaining = remaining;
        }

        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            final int b = in.read();
            if (b >= 0) {
                remaining--;
            }
            return b;
        }

        public int read(final byte[] b, final int off, final int len) throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            final int n = in.read(b, off, (int) Math.min(len, remaining));
            if (n > 0) {
                remaining -= n;
            }
            return n;
        }

        /** Consumes whatever the decoder left behind, so the shared stream ends up block-aligned. */
        void drain() throws IOException {
            if (remaining > 0) {
                CoverStream.skipFully(in, remaining);
                remaining = 0;
            }
        }
    }

    // Each block: [int rawLen][int compLen][compLen deflated bytes].
    private static void writeBlock(final DataOutputStream dos, final byte[] raw) throws IOException {
        final byte[] comp = deflate(raw);
        dos.writeInt(raw.length);
        dos.writeInt(comp.length);
        dos.write(comp);
    }

    // Same, with the 64-bit lengths v2/v3/v4 use.
    private static void writeWideBlock(final DataOutputStream dos, final byte[] raw)
            throws IOException {
        final byte[] comp = deflate(raw);
        dos.writeLong(raw.length);
        dos.writeLong(comp.length);
        dos.write(comp);
    }

    private static void writeLzmaBlock(final DataOutputStream dos, final byte[] raw,
                                       final int stride, final int dictBits) throws IOException {
        final byte[] comp = Lzma.compress(raw, Lzma.posBitsForStride(stride), dictBits);
        dos.writeLong(raw.length);
        dos.writeLong(comp.length);
        dos.write(comp);
    }

    private static byte[] readBlock(final DataInputStream dis, final int expectedRawLen) throws IOException {
        final int rawLen = dis.readInt();
        final int compLen = dis.readInt();
        if (rawLen != expectedRawLen) {
            throw new IOException("block length mismatch: header " + expectedRawLen + " vs block " + rawLen);
        }
        final byte[] comp = new byte[compLen];
        dis.readFully(comp);
        return inflate(comp, rawLen);
    }

    /**
     * Regenerates the legacy {@code cover.txt} token stream from a {@link CoverData}, writing
     * it directly to {@code out} (no giant intermediate string). Whitespace layout is
     * normalized to single spaces; the tokens themselves are identical, which is all any
     * whitespace-delimited reader (the Java parser and the C++ verifier) depends on.
     */
    public static void writeCoverText(final CoverData data, final Writer out) throws IOException {
        int si = 0;
        int ti = 0;
        final StringBuilder sb = new StringBuilder(1 << 16);
        for (int k = 0; k < data.nodeType.length; k++) {
            if (k > 0) {
                sb.append(' ');
            }
            final byte t = data.nodeType[k];
            if (t == CoverData.NODE_D) {
                sb.append('D');
            } else if (t == CoverData.NODE_E) {
                sb.append('E');
            } else if (t == CoverData.NODE_S) {
                sb.append('S').append(' ').append(data.sCode[si++]);
            } else {
                sb.append('T').append(' ').append(data.tCode[ti++]);
            }
            if (sb.length() >= (1 << 16)) {
                out.write(sb.toString());
                sb.setLength(0);
            }
        }
        if (sb.length() > 0) {
            out.write(sb.toString());
        }
        out.flush();
    }

    static byte[] deflate(final byte[] raw) throws IOException {
        final Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        final ByteArrayOutputStream baos = new ByteArrayOutputStream(Math.max(64, raw.length / 8));
        final DeflaterOutputStream dos = new DeflaterOutputStream(baos, deflater, 1 << 16);
        try {
            dos.write(raw);
            dos.finish();
        } finally {
            dos.close();
            deflater.end();
        }
        return baos.toByteArray();
    }

    static byte[] inflate(final byte[] comp, final int rawLen) throws IOException {
        final Inflater inflater = new Inflater();
        inflater.setInput(comp);
        final byte[] out = new byte[rawLen];
        try {
            int off = 0;
            while (off < rawLen) {
                final int got = inflater.inflate(out, off, rawLen - off);
                if (got == 0) {
                    if (inflater.finished() || inflater.needsDictionary()) {
                        break;
                    }
                    if (inflater.needsInput()) {
                        throw new IOException("truncated deflate block");
                    }
                }
                off += got;
            }
            if (off != rawLen) {
                throw new IOException("inflated " + off + " bytes, expected " + rawLen);
            }
        } catch (final java.util.zip.DataFormatException e) {
            throw new IOException("corrupt deflate block", e);
        } finally {
            inflater.end();
        }
        return out;
    }

    private static byte[] indicesToBytes(final int[] a, final int width) {
        final byte[] b = new byte[a.length * width];
        for (int i = 0; i < a.length; i++) {
            // Widened to long so an 8-byte width does not shift an int past 31 bits.
            final long v = a[i] & 0xFFFFFFFFL;
            for (int k = 0; k < width; k++) {
                b[(i * width) + k] = (byte) ((v >>> (8 * k)) & 0xff);
            }
        }
        return b;
    }

    private static int[] bytesToIndices(final byte[] b, final int count, final int width)
            throws IOException {
        final int[] a = new int[count];
        for (int i = 0; i < count; i++) {
            long v = 0;
            for (int k = 0; k < width; k++) {
                v |= ((long) (b[(i * width) + k] & 0xff)) << (8 * k);
            }
            if (v < 0 || v > Integer.MAX_VALUE) {
                throw new IOException("index " + v + " at position " + i
                    + " exceeds what the viewer can address (List.get(int))");
            }
            a[i] = (int) v;
        }
        return a;
    }
}
