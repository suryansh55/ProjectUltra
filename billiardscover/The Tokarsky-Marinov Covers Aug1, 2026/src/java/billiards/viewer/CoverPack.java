package billiards.viewer;

import org.tukaani.xz.FinishableOutputStream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A single self-contained cover file ({@code cover.pack}) bundling an entire cover directory:
 * <ul>
 *   <li>the small text files ({@code polygon.txt}, {@code square.txt}, {@code precision.txt},
 *       {@code stables.txt}, {@code triples.txt}, {@code info.txt}) stored verbatim, so they
 *       round-trip byte-for-byte, and</li>
 *   <li>the big {@code cover.txt} stored as the compact binary cover ({@link CoverCodec}
 *       {@code cbin} bytes).</li>
 * </ul>
 *
 * <p>With this, a cover folder can be reduced to just {@code cover.pack}: the viewer loads
 * everything it needs directly from it, and {@link #unpack} regenerates the original text
 * files on demand (e.g. for the external C++ cover verifier, which reads them as text).
 *
 * <p><b>Version 2</b> compresses the text files as one <em>solid</em> LZMA2 stream instead of a
 * Deflate stream each. {@code info.txt} is largely a restatement of {@code stables.txt}, so
 * compressing them together lets the second one match against the first: on a 130M-node cover
 * that pair goes from 5,146,505 bytes (Deflate, separate) to 1,962,556 (LZMA2, solid) &mdash;
 * about a third of which is the solidity rather than the codec. Version 1 packs stay readable.
 */
public final class CoverPack {

    private static final byte[] MAGIC = {'C', 'P', 'A', 'K'};
    /** Deflate per file, every entry tagged with a kind. Still read; no longer written. */
    private static final byte VERSION_1 = 1;
    /** One solid LZMA2 stream over all the text files, then the cover. */
    private static final byte VERSION_2 = 2;
    private static final byte VERSION = VERSION_2;
    private static final byte KIND_BLOB = 0;   // v1 only: Deflate of a file's exact bytes
    private static final byte KIND_COVER = 1;  // v1 only: embedded CoverCodec cbin bytes

    /** Text files bundled verbatim, in the order the app expects to find them. */
    public static final String[] TEXT_FILES = {
        "polygon.txt", "square.txt", "precision.txt", "stables.txt", "triples.txt", "info.txt"
    };

    private final LinkedHashMap<String, byte[]> blobs; // name -> inflated raw bytes
    private final byte[] coverCbin;                     // embedded cbin bytes, or null

    private CoverPack(final LinkedHashMap<String, byte[]> blobs, final byte[] coverCbin) {
        this.blobs = blobs;
        this.coverCbin = coverCbin;
    }

    public byte[] blob(final String name) {
        return blobs.get(name);
    }

    public String text(final String name) {
        final byte[] b = blobs.get(name);
        return b == null ? null : new String(b, Charset.defaultCharset());
    }

    public boolean hasCover() {
        return coverCbin != null;
    }

    public byte[] coverCbin() {
        return coverCbin;
    }

    public Set<String> names() {
        return blobs.keySet();
    }

    // ---------------------------------------------------------------- write

    /**
     * Builds a {@code cover.pack} from an existing cover directory. Bundles whichever of
     * {@link #TEXT_FILES} are present verbatim, and stores {@code cover.txt} as compact cbin.
     * Requires {@code cover.txt}, {@code stables.txt} and {@code triples.txt}.
     *
     * <p>{@code cover.txt} is streamed through {@link CoverStream#packTextToCbin} into a scratch
     * file beside the output, so peak memory does not scale with the cover's size and there is
     * no 2&nbsp;GB ceiling on the input.
     */
    public static CoverStream.Counts packDirectory(final File dir, final File out) throws IOException {
        final File coverTxt = new File(dir, "cover.txt");
        if (!coverTxt.exists()) {
            throw new IOException("no cover.txt in " + dir);
        }
        final LinkedHashMap<String, byte[]> raw = new LinkedHashMap<String, byte[]>();
        for (final String name : TEXT_FILES) {
            final File f = new File(dir, name);
            if (f.exists()) {
                raw.put(name, readAllBytes(f));
            }
        }
        if (!raw.containsKey("stables.txt") || !raw.containsKey("triples.txt")) {
            throw new IOException("missing stables.txt/triples.txt in " + dir);
        }

        final File tmpCbin = File.createTempFile("cover-pack", ".cbin", dir);
        try {
            final CoverStream.Counts counts = CoverStream.packTextToCbin(coverTxt, tmpCbin,
                codeTableSize(raw.get("stables.txt")), codeTableSize(raw.get("triples.txt")));
            write(out, raw, tmpCbin);
            return counts;
        } finally {
            tmpCbin.delete();
        }
    }

    /**
     * Number of entries in a {@code stables.txt}/{@code triples.txt}, counted the way
     * {@link Cover#parseStables} does: the file is split on {@code '\n'} with empty pieces
     * dropped, and each surviving line is one entry whose index is its position. Counting the
     * bytes directly avoids building a {@code String} copy of the table.
     */
    static long codeTableSize(final byte[] table) {
        long entries = 0;
        boolean inLine = false;
        for (int i = 0; i < table.length; i++) {
            final byte b = table[i];
            if (b == '\n') {
                inLine = false;
            } else if (!inLine && b != ' ' && b != '\r' && b != '\t') {
                inLine = true;
                entries++;
            }
        }
        return entries;
    }

    public static void write(final File out, final LinkedHashMap<String, byte[]> rawBlobs,
                             final byte[] coverCbin) throws IOException {
        final FileOutputStream fos = new FileOutputStream(out);
        try {
            final BufferedOutputStream bos = new BufferedOutputStream(fos, 1 << 16);
            write((OutputStream) bos, rawBlobs, coverCbin);
            bos.flush();
        } finally {
            fos.close();
        }
    }

    /**
     * Same as {@link #write(File, LinkedHashMap, byte[])} but copies the cover entry straight
     * from an existing cbin file, so a large cover never has to sit in the heap as a
     * {@code byte[]}.
     */
    public static void write(final File out, final LinkedHashMap<String, byte[]> rawBlobs,
                             final File coverCbinFile) throws IOException {
        final long cbinLen = coverCbinFile.length();
        if (cbinLen > CoverStream.MAX_ARRAY) {
            throw new IOException("packed cover is " + cbinLen
                + " bytes, too large for a cover.pack entry (limit " + CoverStream.MAX_ARRAY + ")");
        }
        final FileOutputStream fos = new FileOutputStream(out);
        try {
            final DataOutputStream dos =
                new DataOutputStream(new BufferedOutputStream(fos, 1 << 16));
            writeHeaderAndBlobs(dos, rawBlobs, true);
            dos.writeInt((int) cbinLen);
            final FileInputStream in = new FileInputStream(coverCbinFile);
            try {
                final byte[] buf = new byte[1 << 16];
                long copied = 0;
                for (;;) {
                    final int n = in.read(buf);
                    if (n < 0) {
                        break;
                    }
                    dos.write(buf, 0, n);
                    copied += n;
                }
                if (copied != cbinLen) {
                    throw new IOException("cover cbin changed size while packing: expected "
                        + cbinLen + ", copied " + copied);
                }
            } finally {
                in.close();
            }
            dos.flush();
        } finally {
            fos.close();
        }
    }

    public static void write(final OutputStream out, final LinkedHashMap<String, byte[]> rawBlobs,
                             final byte[] coverCbin) throws IOException {
        final DataOutputStream dos = new DataOutputStream(out);
        writeHeaderAndBlobs(dos, rawBlobs, coverCbin != null);
        if (coverCbin != null) {
            dos.writeInt(coverCbin.length);
            dos.write(coverCbin);
        }
        dos.flush();
    }

    /**
     * Writes the v2 header: the file directory (name and exact length of each text file), then
     * every one of them concatenated into a single LZMA2 stream. Solid, so a file can match
     * against everything bundled before it &mdash; which is most of what makes {@code info.txt}
     * nearly free once {@code stables.txt} is in the stream.
     */
    private static void writeHeaderAndBlobs(final DataOutputStream dos,
                                            final LinkedHashMap<String, byte[]> rawBlobs,
                                            final boolean hasCover) throws IOException {
        dos.write(MAGIC);
        dos.writeByte(VERSION);
        dos.writeInt(rawBlobs.size());
        long rawTotal = 0;
        for (final Map.Entry<String, byte[]> e : rawBlobs.entrySet()) {
            writeName(dos, e.getKey());
            dos.writeInt(e.getValue().length);
            rawTotal += e.getValue().length;
        }
        final int dictBits = Lzma.dictBitsFor(rawTotal);

        // Compressed into memory rather than a temp file: the text files are the small half of a
        // cover, and this way the blobs are never concatenated into one giant array either.
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream(1 << 20);
        final FinishableOutputStream solid = Lzma.compressor(bytes, 0, dictBits);
        for (final Map.Entry<String, byte[]> e : rawBlobs.entrySet()) {
            solid.write(e.getValue());
        }
        solid.finish();
        final byte[] comp = bytes.toByteArray();

        dos.writeByte(dictBits);
        dos.writeLong(rawTotal);
        dos.writeLong(comp.length);
        dos.write(comp);
        dos.writeByte(hasCover ? 1 : 0);
    }

    // ---------------------------------------------------------------- read

    public static CoverPack readFile(final String path) throws IOException {
        final FileInputStream fis = new FileInputStream(path);
        try {
            return read(new BufferedInputStream(fis, 1 << 16));
        } finally {
            fis.close();
        }
    }

    public static CoverPack read(final InputStream in) throws IOException {
        final DataInputStream dis = new DataInputStream(in);
        final int version = readMagicAndVersion(dis, "cover.pack");
        if (version == VERSION_1) {
            final int count = dis.readInt();
            final LinkedHashMap<String, byte[]> blobs = new LinkedHashMap<String, byte[]>();
            byte[] cover = null;
            for (int i = 0; i < count; i++) {
                final String name = readName(dis);
                final byte kind = dis.readByte();
                if (kind == KIND_BLOB) {
                    blobs.put(name, readBlobEntry(dis));
                } else if (kind == KIND_COVER) {
                    final int len = dis.readInt();
                    cover = new byte[len];
                    dis.readFully(cover);
                } else {
                    throw new IOException("unknown cover.pack entry kind: " + kind);
                }
            }
            return new CoverPack(blobs, cover);
        }
        final Solid solid = readSolid(dis, true);
        byte[] cover = null;
        if (solid.hasCover) {
            cover = new byte[dis.readInt()];
            dis.readFully(cover);
        }
        return new CoverPack(solid.blobs, cover);
    }

    /**
     * The v2 text-file section: a directory of names and exact lengths, then all of them in one
     * LZMA2 stream. Leaves the stream positioned on the cover entry.
     *
     * @param wantBlobs false to skip past the payload without decompressing it
     */
    private static Solid readSolid(final DataInputStream dis, final boolean wantBlobs)
            throws IOException {
        final int count = dis.readInt();
        if (count < 0 || count > 1024) {
            throw new IOException("cover.pack claims " + count + " bundled text files");
        }
        final String[] names = new String[count];
        final int[] rawLens = new int[count];
        for (int i = 0; i < count; i++) {
            names[i] = readName(dis);
            rawLens[i] = dis.readInt();
            if (rawLens[i] < 0) {
                throw new IOException("cover.pack gives " + names[i] + " a negative length");
            }
        }
        final int dictBits = dis.readUnsignedByte();
        final long rawTotal = dis.readLong();
        final long compLen = dis.readLong();
        if (compLen < 0 || compLen > MAX_SLURP_BYTES) {
            throw new IOException("cover.pack's text section is " + compLen
                + " compressed bytes, more than this reader can hold");
        }

        final LinkedHashMap<String, byte[]> blobs = new LinkedHashMap<String, byte[]>();
        if (!wantBlobs) {
            CoverStream.skipFully(dis, compLen);
        } else {
            final byte[] comp = new byte[(int) compLen];
            dis.readFully(comp);
            final InputStream in =
                Lzma.decompressor(new ByteArrayInputStream(comp), dictBits);
            try {
                long seen = 0;
                for (int i = 0; i < count; i++) {
                    final byte[] blob = new byte[rawLens[i]];
                    int off = 0;
                    while (off < blob.length) {
                        final int n = in.read(blob, off, blob.length - off);
                        if (n < 0) {
                            throw new IOException("cover.pack's text section ended " + off
                                + " bytes into " + names[i] + ", which should be " + blob.length);
                        }
                        off += n;
                    }
                    seen += blob.length;
                    blobs.put(names[i], blob);
                }
                if (seen != rawTotal) {
                    throw new IOException("cover.pack's text section holds " + seen
                        + " bytes but its header says " + rawTotal);
                }
            } finally {
                in.close();
            }
        }
        return new Solid(blobs, dis.readUnsignedByte() != 0);
    }

    /** What {@link #readSolid} recovered: the text files (if asked for) and whether a cover follows. */
    private static final class Solid {

        private final LinkedHashMap<String, byte[]> blobs;
        private final boolean hasCover;

        Solid(final LinkedHashMap<String, byte[]> blobs, final boolean hasCover) {
            this.blobs = blobs;
            this.hasCover = hasCover;
        }
    }

    // ---------------------------------------------------------------- unpack

    /**
     * Regenerates every original text file (including {@code cover.txt}) from a pack into
     * {@code destDir}. Lets the external C++ verifier &mdash; or anything else expecting the
     * loose text files &mdash; run from just a {@code cover.pack}.
     */
    public static void unpack(final File packFile, final File destDir) throws IOException {
        if (!destDir.exists() && !destDir.mkdirs()) {
            throw new IOException("cannot create " + destDir);
        }
        final File tmpCbin = File.createTempFile("cover-unpack", ".cbin", destDir);
        boolean haveCover = false;
        try {
            final FileInputStream fis = new FileInputStream(packFile);
            try {
                final DataInputStream dis =
                    new DataInputStream(new BufferedInputStream(fis, 1 << 16));
                final int version = readMagicAndVersion(dis, packFile.getName());
                if (version == VERSION_1) {
                    final int count = dis.readInt();
                    for (int i = 0; i < count; i++) {
                        final String name = readName(dis);
                        final byte kind = dis.readByte();
                        if (kind == KIND_BLOB) {
                            writeAllBytes(new File(destDir, name), readBlobEntry(dis));
                        } else if (kind == KIND_COVER) {
                            copyCoverEntry(dis, tmpCbin);
                            haveCover = true;
                        } else {
                            throw new IOException("unknown cover.pack entry kind: " + kind);
                        }
                    }
                } else {
                    final Solid solid = readSolid(dis, true);
                    for (final Map.Entry<String, byte[]> e : solid.blobs.entrySet()) {
                        writeAllBytes(new File(destDir, e.getKey()), e.getValue());
                    }
                    if (solid.hasCover) {
                        copyCoverEntry(dis, tmpCbin);
                        haveCover = true;
                    }
                }
            } finally {
                fis.close();
            }
            if (haveCover) {
                // Streamed cbin -> text: never builds a CoverData, so the regenerated cover.txt
                // can be far larger than the heap. Byte-identical to CoverCodec.writeCoverText.
                CoverStream.writeTextFromCbin(tmpCbin, new File(destDir, "cover.txt"));
            }
        } finally {
            tmpCbin.delete();
        }
    }

    /**
     * Streams just the embedded cbin out of a pack into {@code outCbin}. Lets the converter's
     * self-check and {@link #unpack} work on the cover as a file rather than a heap array.
     */
    public static void extractCoverTo(final File packFile, final File outCbin) throws IOException {
        final FileInputStream fis = new FileInputStream(packFile);
        try {
            final DataInputStream dis = new DataInputStream(new BufferedInputStream(fis, 1 << 16));
            final int version = readMagicAndVersion(dis, packFile.getName());
            if (version == VERSION_1) {
                final int count = dis.readInt();
                for (int i = 0; i < count; i++) {
                    readName(dis);
                    final byte kind = dis.readByte();
                    if (kind == KIND_BLOB) {
                        dis.readInt();                            // raw length, not needed here
                        CoverStream.skipFully(dis, dis.readInt()); // skip the deflated payload
                    } else if (kind == KIND_COVER) {
                        copyCoverEntry(dis, outCbin);
                        return;
                    } else {
                        throw new IOException("unknown cover.pack entry kind: " + kind);
                    }
                }
            } else if (readSolid(dis, false).hasCover) {
                copyCoverEntry(dis, outCbin);
                return;
            }
            throw new IOException("no embedded cover in " + packFile);
        } finally {
            fis.close();
        }
    }

    /**
     * Reads only the bundled text files, skipping the (potentially huge) cover entry. Used by
     * the converter to verify the small blobs without decoding the cover.
     */
    public static CoverPack readBlobs(final File packFile) throws IOException {
        final FileInputStream fis = new FileInputStream(packFile);
        try {
            final DataInputStream dis = new DataInputStream(new BufferedInputStream(fis, 1 << 16));
            final int version = readMagicAndVersion(dis, packFile.getName());
            if (version != VERSION_1) {
                return new CoverPack(readSolid(dis, true).blobs, null);
            }
            final int count = dis.readInt();
            final LinkedHashMap<String, byte[]> blobs = new LinkedHashMap<String, byte[]>();
            for (int i = 0; i < count; i++) {
                final String name = readName(dis);
                final byte kind = dis.readByte();
                if (kind == KIND_BLOB) {
                    blobs.put(name, readBlobEntry(dis));
                } else if (kind == KIND_COVER) {
                    CoverStream.skipFully(dis, dis.readInt());
                } else {
                    throw new IOException("unknown cover.pack entry kind: " + kind);
                }
            }
            return new CoverPack(blobs, null);
        } finally {
            fis.close();
        }
    }

    private static int readMagicAndVersion(final DataInputStream dis, final String what)
            throws IOException {
        final byte[] magic = new byte[MAGIC.length];
        dis.readFully(magic);
        for (int i = 0; i < MAGIC.length; i++) {
            if (magic[i] != MAGIC[i]) {
                throw new IOException(what + " is not a cover.pack (bad magic)");
            }
        }
        final int version = dis.readUnsignedByte();
        if (version != VERSION_1 && version != VERSION_2) {
            throw new IOException("unsupported cover.pack version: " + version
                + " (this build reads 1 and 2)");
        }
        return version;
    }

    private static byte[] readBlobEntry(final DataInputStream dis) throws IOException {
        final int rawLen = dis.readInt();
        final int compLen = dis.readInt();
        final byte[] comp = new byte[compLen];
        dis.readFully(comp);
        return CoverCodec.inflate(comp, rawLen);
    }

    private static void copyCoverEntry(final DataInputStream dis, final File out) throws IOException {
        final int len = dis.readInt();
        final FileOutputStream fos = new FileOutputStream(out);
        try {
            final BufferedOutputStream bos = new BufferedOutputStream(fos, 1 << 16);
            final byte[] buf = new byte[1 << 16];
            int left = len;
            while (left > 0) {
                final int n = dis.read(buf, 0, Math.min(buf.length, left));
                if (n < 0) {
                    throw new IOException("cover.pack truncated: " + left + " cover bytes missing");
                }
                bos.write(buf, 0, n);
                left -= n;
            }
            bos.flush();
        } finally {
            fos.close();
        }
    }

    // ---------------------------------------------------------------- helpers

    private static void writeName(final DataOutputStream dos, final String name) throws IOException {
        final byte[] n = name.getBytes("UTF-8");
        dos.writeShort(n.length);
        dos.write(n);
    }

    private static String readName(final DataInputStream dis) throws IOException {
        final int len = dis.readUnsignedShort();
        final byte[] n = new byte[len];
        dis.readFully(n);
        return new String(n, "UTF-8");
    }

    /** Largest file this can slurp. A Java array is int-indexed, so anything at or beyond
     *  {@code Integer.MAX_VALUE} would truncate silently via the {@code (int)} cast below. */
    static final long MAX_SLURP_BYTES = Integer.MAX_VALUE - 8L;

    static byte[] readAllBytes(final File f) throws IOException {
        final long len = f.length();
        if (len > MAX_SLURP_BYTES) {
            throw new IOException(f.getName() + " is " + len + " bytes; this packer cannot read a "
                + "file larger than " + MAX_SLURP_BYTES + " bytes (Java's array size limit). "
                + "Raising -Xmx will NOT help -- reading it would truncate silently.");
        }
        final FileInputStream fis = new FileInputStream(f);
        try {
            final byte[] buf = new byte[(int) len];
            final DataInputStream dis = new DataInputStream(fis);
            dis.readFully(buf);
            return buf;
        } finally {
            fis.close();
        }
    }

    private static void writeAllBytes(final File f, final byte[] bytes) throws IOException {
        final FileOutputStream fos = new FileOutputStream(f);
        try {
            fos.write(bytes);
        } finally {
            fos.close();
        }
    }
}
