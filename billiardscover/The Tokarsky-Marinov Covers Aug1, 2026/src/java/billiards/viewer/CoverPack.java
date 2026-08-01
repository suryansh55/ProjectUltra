package billiards.viewer;

import billiards.codeseq.CodePair;
import billiards.codeseq.TriplePair;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A single self-contained cover file ({@code cover.pack}) bundling an entire cover directory:
 * <ul>
 *   <li>the small text files ({@code polygon.txt}, {@code square.txt}, {@code precision.txt},
 *       {@code stables.txt}, {@code triples.txt}, {@code info.txt}) stored verbatim (Deflate),
 *       so they round-trip byte-for-byte, and</li>
 *   <li>the big {@code cover.txt} stored as the compact binary cover ({@link CoverCodec}
 *       {@code cbin} bytes).</li>
 * </ul>
 *
 * <p>With this, a cover folder can be reduced to just {@code cover.pack}: the viewer loads
 * everything it needs directly from it, and {@link #unpack} regenerates the original text
 * files on demand (e.g. for the external C++ cover verifier, which reads them as text).
 */
public final class CoverPack {

    private static final byte[] MAGIC = {'C', 'P', 'A', 'K'};
    private static final byte VERSION = 1;
    private static final byte KIND_BLOB = 0;   // Deflate of a file's exact bytes
    private static final byte KIND_COVER = 1;  // embedded CoverCodec cbin bytes

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
     */
    public static void packDirectory(final File dir, final File out) throws IOException {
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
        final List<CodePair> stables = Cover.parseStables(trim(raw.get("stables.txt")));
        final List<TriplePair> triples = Cover.parseTriples(trim(raw.get("triples.txt")));
        final CoverData cover = CoverCodec.parseText(trim(readAllBytes(coverTxt)), stables, triples);
        final byte[] cbin = toCbinBytes(cover);

        write(out, raw, cbin);
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

    public static void write(final OutputStream out, final LinkedHashMap<String, byte[]> rawBlobs,
                             final byte[] coverCbin) throws IOException {
        final DataOutputStream dos = new DataOutputStream(out);
        dos.write(MAGIC);
        dos.writeByte(VERSION);
        dos.writeInt(rawBlobs.size() + (coverCbin != null ? 1 : 0));
        for (final Map.Entry<String, byte[]> e : rawBlobs.entrySet()) {
            writeName(dos, e.getKey());
            dos.writeByte(KIND_BLOB);
            final byte[] comp = CoverCodec.deflate(e.getValue());
            dos.writeInt(e.getValue().length);
            dos.writeInt(comp.length);
            dos.write(comp);
        }
        if (coverCbin != null) {
            writeName(dos, "cover");
            dos.writeByte(KIND_COVER);
            dos.writeInt(coverCbin.length);
            dos.write(coverCbin);
        }
        dos.flush();
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
        final byte[] magic = new byte[MAGIC.length];
        dis.readFully(magic);
        for (int i = 0; i < MAGIC.length; i++) {
            if (magic[i] != MAGIC[i]) {
                throw new IOException("not a cover.pack file (bad magic)");
            }
        }
        final int version = dis.readUnsignedByte();
        if (version != VERSION) {
            throw new IOException("unsupported cover.pack version: " + version);
        }
        final int count = dis.readInt();
        final LinkedHashMap<String, byte[]> blobs = new LinkedHashMap<String, byte[]>();
        byte[] cover = null;
        for (int i = 0; i < count; i++) {
            final String name = readName(dis);
            final byte kind = dis.readByte();
            if (kind == KIND_BLOB) {
                final int rawLen = dis.readInt();
                final int compLen = dis.readInt();
                final byte[] comp = new byte[compLen];
                dis.readFully(comp);
                blobs.put(name, CoverCodec.inflate(comp, rawLen));
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

    // ---------------------------------------------------------------- unpack

    /**
     * Regenerates every original text file (including {@code cover.txt}) from a pack into
     * {@code destDir}. Lets the external C++ verifier &mdash; or anything else expecting the
     * loose text files &mdash; run from just a {@code cover.pack}.
     */
    public static void unpack(final File packFile, final File destDir) throws IOException {
        final CoverPack pack = readFile(packFile.getPath());
        if (!destDir.exists() && !destDir.mkdirs()) {
            throw new IOException("cannot create " + destDir);
        }
        for (final String name : pack.names()) {
            writeAllBytes(new File(destDir, name), pack.blob(name));
        }
        if (pack.hasCover()) {
            final List<CodePair> stables = Cover.parseStables(pack.text("stables.txt").trim());
            final List<TriplePair> triples = Cover.parseTriples(pack.text("triples.txt").trim());
            final CoverData cover = CoverCodec.readBinary(
                new ByteArrayInputStream(pack.coverCbin()), stables, triples);
            final FileOutputStream fos = new FileOutputStream(new File(destDir, "cover.txt"));
            try {
                final Writer w = new BufferedWriter(
                    new OutputStreamWriter(fos, Charset.defaultCharset()), 1 << 16);
                CoverCodec.writeCoverText(cover, w);
                w.flush();
            } finally {
                fos.close();
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    static byte[] toCbinBytes(final CoverData cover) throws IOException {
        final java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream(1 << 20);
        CoverCodec.writeBinary(cover, baos);
        return baos.toByteArray();
    }

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

    private static String trim(final byte[] bytes) {
        return new String(bytes, Charset.defaultCharset()).trim();
    }

    static byte[] readAllBytes(final File f) throws IOException {
        final FileInputStream fis = new FileInputStream(f);
        try {
            final byte[] buf = new byte[(int) f.length()];
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
