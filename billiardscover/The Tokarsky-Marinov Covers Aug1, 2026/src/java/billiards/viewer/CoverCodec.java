package billiards.viewer;

import billiards.codeseq.CodePair;
import billiards.codeseq.TriplePair;

import org.eclipse.collections.impl.list.mutable.primitive.ByteArrayList;
import org.eclipse.collections.impl.list.mutable.primitive.CharArrayList;

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

/**
 * Reads and writes covers in two forms:
 * <ul>
 *   <li>the legacy space-separated text stream ({@code cover.txt}), and</li>
 *   <li>the compact binary {@code cover.cbin} &mdash; three JDK-Deflate blocks (node-type
 *       stream, uint16 stable indices, uint16 triple indices) behind a small header.</li>
 * </ul>
 * Both produce an identical {@link CoverData}. The binary form is ~60x smaller than the text
 * and parses without any string tokenization or {@code Integer.parseInt}.
 *
 * <p>Deliberately limited to {@code java.util.zip} (Deflate) and other Java 1.8 APIs so it
 * builds on the professor's Oracle JDK 1.8.0_66 toolchain with no added dependency.
 */
public final class CoverCodec {

    private static final byte[] MAGIC = {'C', 'B', 'I', 'N'};
    private static final byte VERSION = 1;

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
        final CharArrayList sCode = new CharArrayList();
        final CharArrayList tCode = new CharArrayList();

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
                sCode.add((char) value);
                nodeType.add(CoverData.NODE_S);
            } else if (c == 'T') {
                i++;
                final int value = readInt(cover, i, n);
                i = intEnd;
                tCode.add((char) value);
                nodeType.add(CoverData.NODE_T);
            } else {
                throw new RuntimeException("unknown cover token '" + c + "' at offset " + i);
            }
        }

        return new CoverData(nodeType.toArray(), sCode.toArray(), tCode.toArray(), stables, triples);
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
        int value = 0;
        boolean any = false;
        while (i < n) {
            final char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                value = value * 10 + (c - '0');
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
        return value;
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

    public static void writeBinary(final CoverData data, final OutputStream out) throws IOException {
        final DataOutputStream dos = new DataOutputStream(out);
        dos.write(MAGIC);
        dos.writeByte(VERSION);
        dos.writeInt(data.nodeType.length);
        dos.writeInt(data.sCode.length);
        dos.writeInt(data.tCode.length);
        writeBlock(dos, data.nodeType);
        writeBlock(dos, charsToBytes(data.sCode));
        writeBlock(dos, charsToBytes(data.tCode));
        dos.flush();
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
        if (version != VERSION) {
            throw new IOException("unsupported cover.cbin version: " + version);
        }
        final int nodeCount = dis.readInt();
        final int stableCount = dis.readInt();
        final int tripleCount = dis.readInt();

        final byte[] nodeTypeBytes = readBlock(dis, nodeCount);
        final char[] sCode = bytesToChars(readBlock(dis, stableCount * 2), stableCount);
        final char[] tCode = bytesToChars(readBlock(dis, tripleCount * 2), tripleCount);

        return new CoverData(nodeTypeBytes, sCode, tCode, stables, triples);
    }

    // Each block: [int rawLen][int compLen][compLen deflated bytes].
    private static void writeBlock(final DataOutputStream dos, final byte[] raw) throws IOException {
        final byte[] comp = deflate(raw);
        dos.writeInt(raw.length);
        dos.writeInt(comp.length);
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
                sb.append('S').append(' ').append((int) data.sCode[si++]);
            } else {
                sb.append('T').append(' ').append((int) data.tCode[ti++]);
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

    private static byte[] charsToBytes(final char[] a) {
        final byte[] b = new byte[a.length * 2];
        for (int i = 0; i < a.length; i++) {
            final char v = a[i];
            b[2 * i] = (byte) (v & 0xff);
            b[2 * i + 1] = (byte) ((v >> 8) & 0xff);
        }
        return b;
    }

    private static char[] bytesToChars(final byte[] b, final int count) {
        final char[] a = new char[count];
        for (int i = 0; i < count; i++) {
            final int lo = b[2 * i] & 0xff;
            final int hi = b[2 * i + 1] & 0xff;
            a[i] = (char) (lo | (hi << 8));
        }
        return a;
    }
}
