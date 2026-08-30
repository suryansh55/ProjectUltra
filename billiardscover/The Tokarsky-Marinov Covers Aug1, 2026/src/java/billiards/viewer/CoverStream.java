package billiards.viewer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import org.tukaani.xz.FinishableOutputStream;

/**
 * Constant-memory conversions between the legacy {@code cover.txt} token stream and the compact
 * binary {@code cbin} form.
 *
 * <p>{@link CoverCodec#parseText} and {@link CoverPack#readAllBytes} both materialize the whole
 * cover in the heap &mdash; as a {@code byte[]}, then as a Java&nbsp;8 {@code String} (two bytes
 * per character), then as the columnar arrays. That costs roughly five times the file size at
 * peak and, worse, is capped hard by Java's {@code int}-indexed arrays: no {@code cover.txt} at
 * or beyond 2&nbsp;GB can go through it at all.
 *
 * <p>Everything here streams instead. The text is scanned in a single forward pass through a
 * 64&nbsp;KB character buffer and the three columns are compressed straight to disk, so packing a
 * 10&nbsp;GB cover uses the same few megabytes as packing a 10&nbsp;MB one. Verification and
 * unpacking walk the cbin blocks the same way, never building a {@link CoverData}.
 *
 * <p>Deliberately limited to Java 1.8 APIs plus XZ for Java (see {@link Lzma}), matching the rest
 * of this source tree (the professor's toolchain is Oracle JDK 1.8.0_66).
 */
public final class CoverStream {

    /** Largest allocatable array; a few bytes under {@code Integer.MAX_VALUE} on common JVMs. */
    static final int MAX_ARRAY = Integer.MAX_VALUE - 8;

    /** Highest index cbin v1/v2 can hold: those store indices as two bytes. */
    static final int MAX_INDEX_V1 = 0xFFFF;

    /**
     * Highest number of entries a code table may have. The tables are {@code List<CodePair>}
     * and {@link CoverData} resolves a leaf with {@code stables.get(index)}, so an index can
     * never exceed what {@code List.get(int)} accepts &mdash; whatever width the file uses.
     * Escaping this needs the code tables themselves chunked, not a wider index.
     */
    static final long MAX_CODE_TABLE = MAX_ARRAY;

    /** Bytes per index in a {@code cbin}. v3 records which of these a file uses. */
    static final int WIDTH_16 = 2;
    static final int WIDTH_32 = 4;
    static final int WIDTH_64 = 8;

    private static final int BUF = 1 << 16;

    /**
     * Escape hatch: write the pre-LZMA2 Deflate formats (cbin v1/v3) instead of v4. Keeps the
     * legacy writers exercised, reproduces the byte-for-byte output of older packs, and is there
     * in case LZMA2 ever has to be taken out of the path in a hurry. Not set in normal use.
     */
    static final boolean LEGACY_DEFLATE = Boolean.getBoolean("billiards.cbin.deflate");

    /**
     * Test hook, same reasoning as {@link #LEGACY_DEFLATE}: force an index width (2, 4 or 8) rather
     * than the narrowest that fits. Reaching width 8 honestly would need a code table of more
     * than 4 billion entries, so this is the only practical way to exercise that path. 0 (the
     * default) means "choose from the code table sizes".
     */
    private static final int FORCE_WIDTH = Integer.getInteger("billiards.cbin.indexWidth", 0);

    private CoverStream() {
    }

    /** Node, stable, triple and token totals observed during a streaming pass. */
    public static final class Counts {

        public final long nodes;
        public final long stables;
        public final long triples;
        /** Whitespace-separated tokens in {@code cover.txt} (an {@code S n} leaf counts as two). */
        public final long tokens;
        /** Bytes per index in the emitted file: 2, 4 or 8. */
        public final int indexWidth;
        /** cbin version actually written: 4 normally, 1 or 3 under {@link #LEGACY_DEFLATE}. */
        public final int cbinVersion;

        Counts(final long nodes, final long stables, final long triples, final long tokens,
               final int indexWidth) {
            this(nodes, stables, triples, tokens, indexWidth, 0);
        }

        private Counts(final long nodes, final long stables, final long triples, final long tokens,
                       final int indexWidth, final int cbinVersion) {
            this.nodes = nodes;
            this.stables = stables;
            this.triples = triples;
            this.tokens = tokens;
            this.indexWidth = indexWidth;
            this.cbinVersion = cbinVersion;
        }

        Counts withCbinVersion(final int version) {
            return new Counts(nodes, stables, triples, tokens, indexWidth, version);
        }

        /** True when the cover blocks are LZMA2 rather than the older Deflate. */
        public boolean isLzma2() {
            return cbinVersion == CoverCodec.VERSION_4;
        }

        /**
         * Bytes the columnar {@link CoverData} arrays will occupy once this cover is loaded.
         * Indices live in {@code int[]} in memory whatever width the file uses, so this does
         * not vary with {@link #indexWidth}.
         */
        public long viewerHeapBytes() {
            return nodes + (stables * 4L) + (triples * 4L);
        }
    }

    /**
     * Narrowest index width that can address {@code entries} code-table entries. Chosen from
     * the table sizes rather than from the indices actually seen, so the width is known before
     * the single forward pass over {@code cover.txt} begins.
     */
    static int widthFor(final long entries) {
        if (FORCE_WIDTH == WIDTH_16 || FORCE_WIDTH == WIDTH_32 || FORCE_WIDTH == WIDTH_64) {
            return FORCE_WIDTH;
        }
        if (entries <= MAX_INDEX_V1 + 1L) {
            return WIDTH_16;
        }
        if (entries <= 0xFFFFFFFFL) {
            return WIDTH_32;
        }
        return WIDTH_64;
    }

    // ---------------------------------------------------------------- pack: text -> cbin

    /**
     * Streams {@code coverTxt} into a compact {@code cbin} at {@code outCbin}, using memory
     * independent of the cover's size.
     *
     * <p>{@code stableCodes} and {@code tripleCodes} are the entry counts of {@code stables.txt}
     * and {@code triples.txt}. They fix the index width up front &mdash; so this stays one
     * forward pass &mdash; and every index in the cover is checked against them, which catches a
     * leaf pointing past the end of its code table before the viewer trips over it.
     *
     * <p>Emits cbin v4: 64-bit counts, an explicit index width of 2, 4 or 8 bytes, and LZMA2
     * blocks. Under {@link #LEGACY_DEFLATE} it emits the older Deflate forms instead &mdash; v1
     * when the cover fits v1's {@code int} block lengths and 16-bit indices (so such a cover
     * re-packs to byte-identical output), otherwise v3.
     */
    public static Counts packTextToCbin(final File coverTxt, final File outCbin,
                                        final long stableCodes, final long tripleCodes)
            throws IOException {
        checkTable(stableCodes, "stables.txt");
        checkTable(tripleCodes, "triples.txt");
        final int width = Math.max(widthFor(stableCodes), widthFor(tripleCodes));
        // cover.txt's length bounds every column: the node column is a byte per token, and an
        // index costs at least as many characters in the text as it does bytes in the column.
        final int dictBits = Lzma.dictBitsFor(coverTxt.length());

        final File dir = tempDirFor(outCbin);
        final File nz = File.createTempFile("cover-node", ".z", dir);
        final File sz = File.createTempFile("cover-stable", ".z", dir);
        final File tz = File.createTempFile("cover-triple", ".z", dir);
        try {
            final Counts counts =
                scanColumns(coverTxt, nz, sz, tz, width, stableCodes, tripleCodes, dictBits);
            return counts.withCbinVersion(writeCbin(outCbin, counts, nz, sz, tz, dictBits));
        } finally {
            nz.delete();
            sz.delete();
            tz.delete();
        }
    }

    private static void checkTable(final long entries, final String what) throws IOException {
        if (entries < 0 || entries > MAX_CODE_TABLE) {
            throw new IOException(what + " has " + entries + " entries, more than the viewer can "
                + "address (" + MAX_CODE_TABLE + "): the code tables are List<CodePair> and a "
                + "leaf resolves through List.get(int). Widening the stored index does not help; "
                + "the code tables themselves would have to be chunked.");
        }
    }

    /** Single forward pass over the text, compressing each column straight into a temp file. */
    private static Counts scanColumns(final File coverTxt, final File nz, final File sz,
                                      final File tz, final int width, final long stableCodes,
                                      final long tripleCodes, final int dictBits)
            throws IOException {
        OutputStream nOut = null;
        OutputStream sOut = null;
        OutputStream tOut = null;
        Reader reader = null;
        try {
            // The node column is a byte per node, the index columns are `width`-byte records;
            // telling the coder that stride is worth about 1% on the index columns.
            nOut = column(nz, 1, dictBits);
            sOut = column(sz, width, dictBits);
            tOut = column(tz, width, dictBits);
            reader = new InputStreamReader(new FileInputStream(coverTxt), Charset.defaultCharset());

            final Scanner sc = new Scanner(reader);
            long nodes = 0;
            long stables = 0;
            long triples = 0;
            long tokens = 0;

            for (;;) {
                final int c = sc.nextNonSpace();
                if (c < 0) {
                    break;
                }
                tokens++;
                if (c == 'D') {
                    nOut.write(CoverData.NODE_D);
                } else if (c == 'E' || c == 'H') {
                    nOut.write(CoverData.NODE_E);
                } else if (c == 'S') {
                    writeIndex(sOut, sc.readIndex("stable", stableCodes), width);
                    stables++;
                    tokens++;
                    nOut.write(CoverData.NODE_S);
                } else if (c == 'T') {
                    writeIndex(tOut, sc.readIndex("triple", tripleCodes), width);
                    triples++;
                    tokens++;
                    nOut.write(CoverData.NODE_T);
                } else {
                    throw new IOException("unknown cover token '" + (char) c + "' at offset "
                        + sc.offset() + " of " + coverTxt.getName());
                }
                nodes++;
            }

            // Closing finishes each deflate stream; the temp file length is then the block size.
            nOut.close();
            nOut = null;
            sOut.close();
            sOut = null;
            tOut.close();
            tOut = null;

            return new Counts(nodes, stables, triples, tokens, width);
        } finally {
            closeQuietly(nOut);
            closeQuietly(sOut);
            closeQuietly(tOut);
            closeQuietly(reader);
        }
    }

    /**
     * A compressing sink over {@code f} for a column of {@code stride}-byte records.
     *
     * <p>Under {@link #LEGACY_DEFLATE} this is a plain {@code DeflaterOutputStream}, whose 64&nbsp;KB
     * front buffer batches per-node writes into the same input chunks {@link CoverCodec#deflate}
     * would produce &mdash; which is what keeps legacy v1 output byte-identical to the old
     * in-memory packer. Otherwise it is an {@link AsyncColumn}: LZMA2 encodes at a few MB/s, so
     * running the three columns' encoders one after another would add half an hour to a
     * multi-gigabyte cover. Each column gets its own thread and the scan just feeds them.
     */
    private static OutputStream column(final File f, final int stride, final int dictBits)
            throws IOException {
        if (LEGACY_DEFLATE) {
            final OutputStream file = new BufferedOutputStream(new FileOutputStream(f), BUF);
            final Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
            return new BufferedOutputStream(
                new DeflaterOutputStream(file, deflater, BUF) {
                    public void close() throws IOException {
                        try {
                            super.close();
                        } finally {
                            deflater.end();
                        }
                    }
                }, BUF);
        }
        return new BufferedOutputStream(
            new AsyncColumn(f, Lzma.posBitsForStride(stride), dictBits), BUF);
    }

    /**
     * An LZMA2-compressing sink whose encoder runs on its own thread, fed 1&nbsp;MB blocks over a
     * short bounded queue. The queue bounds memory (at most four blocks in flight) and makes the
     * handoff self-throttling: if the scan outruns the encoder it simply blocks on {@code put}.
     */
    private static final class AsyncColumn extends OutputStream {

        /** Sentinel telling the worker there is no more input. */
        private static final byte[] END = new byte[0];
        private static final int HANDOFF = 1 << 20;

        private final ArrayBlockingQueue<byte[]> queue = new ArrayBlockingQueue<byte[]>(3);
        private final Thread worker;
        private final File file;
        /** Set by the worker; read by the scan thread after every handoff and at close. */
        private volatile IOException failure;
        private byte[] pending = new byte[HANDOFF];
        private int pendingLen;
        private boolean closed;

        AsyncColumn(final File f, final int posBits, final int dictBits) {
            this.file = f;
            this.worker = new Thread(new Encoder(f, posBits, dictBits, queue, this),
                "cover-lzma2-" + f.getName());
            this.worker.setDaemon(true);
            this.worker.start();
        }

        void fail(final IOException e) {
            failure = e;
        }

        public void write(final int b) throws IOException {
            if (pendingLen == pending.length) {
                handOff();
            }
            pending[pendingLen++] = (byte) b;
        }

        public void write(final byte[] b, final int off, final int len) throws IOException {
            int at = off;
            int left = len;
            while (left > 0) {
                if (pendingLen == pending.length) {
                    handOff();
                }
                final int n = Math.min(left, pending.length - pendingLen);
                System.arraycopy(b, at, pending, pendingLen, n);
                pendingLen += n;
                at += n;
                left -= n;
            }
        }

        private void handOff() throws IOException {
            if (pendingLen == 0) {
                return;
            }
            final byte[] block = pendingLen == pending.length
                ? pending
                : java.util.Arrays.copyOf(pending, pendingLen);
            enqueue(block);
            pending = new byte[HANDOFF];   // the worker owns the block we just handed over
            pendingLen = 0;
        }

        /**
         * Offers with a timeout rather than blocking outright: if the worker has died the queue
         * will never drain again, and an untimed {@code put} would hang the conversion instead of
         * surfacing the encoder's error.
         */
        private void enqueue(final byte[] block) throws IOException {
            for (;;) {
                checkFailure();
                try {
                    if (queue.offer(block, 1, TimeUnit.SECONDS)) {
                        return;
                    }
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted while packing " + file.getName(), e);
                }
                if (!worker.isAlive()) {
                    checkFailure();
                    throw new IOException("the compressor for " + file.getName()
                        + " stopped without reporting why");
                }
            }
        }

        private void checkFailure() throws IOException {
            final IOException e = failure;
            if (e != null) {
                throw e;
            }
        }

        public void close() throws IOException {
            if (closed) {
                return;
            }
            closed = true;
            handOff();
            enqueue(END);
            try {
                worker.join();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted while finishing " + file.getName(), e);
            }
            checkFailure();
        }
    }

    /** The worker half of {@link AsyncColumn}: drains the queue into an LZMA2 stream. */
    private static final class Encoder implements Runnable {

        private final File file;
        private final int posBits;
        private final int dictBits;
        private final ArrayBlockingQueue<byte[]> queue;
        private final AsyncColumn owner;

        Encoder(final File file, final int posBits, final int dictBits,
                final ArrayBlockingQueue<byte[]> queue, final AsyncColumn owner) {
            this.file = file;
            this.posBits = posBits;
            this.dictBits = dictBits;
            this.queue = queue;
            this.owner = owner;
        }

        public void run() {
            OutputStream out = null;
            try {
                out = new BufferedOutputStream(new FileOutputStream(file), BUF);
                final FinishableOutputStream z = Lzma.compressor(out, posBits, dictBits);
                for (;;) {
                    final byte[] block = queue.take();
                    if (block.length == 0) {
                        break;
                    }
                    z.write(block, 0, block.length);
                }
                z.finish();
                out.flush();
                out.close();
                out = null;
            } catch (final IOException e) {
                owner.fail(new IOException("failed compressing " + file.getName() + ": "
                    + e.getMessage(), e));
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                owner.fail(new IOException("interrupted while compressing " + file.getName()));
            } catch (final RuntimeException e) {
                // OutOfMemoryError aside, anything thrown here would otherwise vanish into a
                // dead thread and hang the scan on a queue nobody drains.
                owner.fail(new IOException("failed compressing " + file.getName() + ": " + e, e));
            } finally {
                closeQuietly(out);
            }
        }
    }

    /** Little-endian, {@code width} bytes, matching the 16-bit layout v1 has always used. */
    private static void writeIndex(final OutputStream out, final long v, final int width)
            throws IOException {
        for (int i = 0; i < width; i++) {
            out.write((int) ((v >>> (8 * i)) & 0xFF));
        }
    }

    /** Writes the cbin and returns the version it chose. */
    private static int writeCbin(final File out, final Counts c, final File nz, final File sz,
                                 final File tz, final int dictBits) throws IOException {
        final long nComp = nz.length();
        final long sComp = sz.length();
        final long tComp = tz.length();
        final long sRaw = c.stables * c.indexWidth;
        final long tRaw = c.triples * c.indexWidth;
        // Legacy v1 only describes 16-bit indices and int-sized blocks; anything else needs v3.
        final boolean v1 = LEGACY_DEFLATE
            && c.indexWidth == WIDTH_16
            && c.nodes <= MAX_ARRAY
            && sRaw <= MAX_ARRAY
            && tRaw <= MAX_ARRAY
            && nComp <= MAX_ARRAY && sComp <= MAX_ARRAY && tComp <= MAX_ARRAY;

        final int version = v1 ? CoverCodec.VERSION_1
            : (LEGACY_DEFLATE ? CoverCodec.VERSION_3 : CoverCodec.VERSION_4);
        final FileOutputStream fos = new FileOutputStream(out);
        try {
            final DataOutputStream dos =
                new DataOutputStream(new BufferedOutputStream(fos, BUF));
            dos.write(CoverCodec.MAGIC);
            if (v1) {
                dos.writeByte(CoverCodec.VERSION_1);
                dos.writeInt((int) c.nodes);
                dos.writeInt((int) c.stables);
                dos.writeInt((int) c.triples);
                copyBlock(dos, false, c.nodes, nComp, nz);
                copyBlock(dos, false, sRaw, sComp, sz);
                copyBlock(dos, false, tRaw, tComp, tz);
            } else {
                dos.writeByte(version);
                dos.writeByte(c.indexWidth);
                if (!LEGACY_DEFLATE) {
                    dos.writeByte(dictBits);
                }
                dos.writeLong(c.nodes);
                dos.writeLong(c.stables);
                dos.writeLong(c.triples);
                copyBlock(dos, true, c.nodes, nComp, nz);
                copyBlock(dos, true, sRaw, sComp, sz);
                copyBlock(dos, true, tRaw, tComp, tz);
            }
            dos.flush();
        } finally {
            fos.close();
        }
        return version;
    }

    private static void copyBlock(final DataOutputStream dos, final boolean wide, final long rawLen,
                                  final long compLen, final File comp) throws IOException {
        if (wide) {
            dos.writeLong(rawLen);
            dos.writeLong(compLen);
        } else {
            dos.writeInt((int) rawLen);
            dos.writeInt((int) compLen);
        }
        final FileInputStream in = new FileInputStream(comp);
        try {
            final byte[] buf = new byte[BUF];
            for (;;) {
                final int n = in.read(buf);
                if (n < 0) {
                    break;
                }
                dos.write(buf, 0, n);
            }
        } finally {
            in.close();
        }
    }

    // ---------------------------------------------------------------- verify: cbin vs text

    /**
     * Confirms that {@code cbin} regenerates exactly the token stream in {@code coverTxt},
     * comparing them token by token with both sides streamed. Returns the token count.
     */
    public static long verifyCbinAgainstText(final File cbin, final File coverTxt) throws IOException {
        final CbinHeader h = CbinHeader.read(cbin);
        Block nodes = null;
        Block stables = null;
        Block triples = null;
        Reader reader = null;
        try {
            nodes = h.openNodes(cbin);
            stables = h.openStables(cbin);
            triples = h.openTriples(cbin);
            reader = new InputStreamReader(new FileInputStream(coverTxt), Charset.defaultCharset());
            final Scanner sc = new Scanner(reader);

            long tokens = 0;
            long sSeen = 0;
            long tSeen = 0;

            for (long node = 0; node < h.nodes; node++) {
                final int type = nodes.in.read();
                if (type < 0) {
                    throw new IOException("cbin node stream ended early at node " + node);
                }
                final int letter = sc.nextNonSpace();
                if (letter < 0) {
                    throw new IOException("cover.txt ended early at node " + node);
                }
                tokens++;

                if (type == CoverData.NODE_D) {
                    expect(letter == 'D', node, "D", letter);
                } else if (type == CoverData.NODE_E) {
                    expect(letter == 'E' || letter == 'H', node, "E/H", letter);
                } else if (type == CoverData.NODE_S) {
                    expect(letter == 'S', node, "S", letter);
                    tokens++;
                    compareIndex(sc.readIndex("stable", MAX_CODE_TABLE), stables.in, h.indexWidth,
                        node, "stable");
                    sSeen++;
                } else if (type == CoverData.NODE_T) {
                    expect(letter == 'T', node, "T", letter);
                    tokens++;
                    compareIndex(sc.readIndex("triple", MAX_CODE_TABLE), triples.in, h.indexWidth,
                        node, "triple");
                    tSeen++;
                } else {
                    throw new IOException("unknown cbin node type " + type + " at node " + node);
                }
            }

            if (sc.nextNonSpace() >= 0) {
                throw new IOException("cover.txt has trailing tokens after node " + h.nodes);
            }
            if (nodes.in.read() >= 0) {
                throw new IOException("cbin node stream is longer than its header count " + h.nodes);
            }
            if (sSeen != h.stables) {
                throw new IOException("stable count mismatch: header " + h.stables + " vs " + sSeen);
            }
            if (tSeen != h.triples) {
                throw new IOException("triple count mismatch: header " + h.triples + " vs " + tSeen);
            }
            return tokens;
        } finally {
            closeQuietly(nodes);
            closeQuietly(stables);
            closeQuietly(triples);
            closeQuietly(reader);
        }
    }

    private static void compareIndex(final long fromText, final InputStream column, final int width,
                                     final long node, final String what) throws IOException {
        final long fromCbin = readIndex(column, width, node, what);
        if (fromText != fromCbin) {
            throw new IOException(what + " index mismatch at node " + node + ": cover.txt has "
                + fromText + ", cbin has " + fromCbin);
        }
    }

    private static void expect(final boolean ok, final long node, final String want, final int got)
            throws IOException {
        if (!ok) {
            throw new IOException("token mismatch at node " + node + ": expected " + want
                + " got '" + (char) got + "'");
        }
    }

    // ---------------------------------------------------------------- unpack: cbin -> text

    /**
     * Regenerates the legacy {@code cover.txt} token stream from {@code cbin} without building a
     * {@link CoverData}. Byte-for-byte identical to {@link CoverCodec#writeCoverText}: tokens
     * separated by single spaces, no trailing newline.
     */
    public static long writeTextFromCbin(final File cbin, final File outTxt) throws IOException {
        final CbinHeader h = CbinHeader.read(cbin);
        Block nodes = null;
        Block stables = null;
        Block triples = null;
        FileOutputStream fos = null;
        try {
            nodes = h.openNodes(cbin);
            stables = h.openStables(cbin);
            triples = h.openTriples(cbin);
            fos = new FileOutputStream(outTxt);
            final Writer w = new BufferedWriter(
                new OutputStreamWriter(fos, Charset.defaultCharset()), BUF);

            final StringBuilder sb = new StringBuilder(BUF + 32);
            long tokens = 0;
            for (long k = 0; k < h.nodes; k++) {
                if (k > 0) {
                    sb.append(' ');
                }
                final int type = nodes.in.read();
                if (type < 0) {
                    throw new IOException("cbin node stream ended early at node " + k);
                }
                tokens++;
                if (type == CoverData.NODE_D) {
                    sb.append('D');
                } else if (type == CoverData.NODE_E) {
                    sb.append('E');
                } else if (type == CoverData.NODE_S) {
                    sb.append('S').append(' ')
                        .append(readIndex(stables.in, h.indexWidth, k, "stable"));
                    tokens++;
                } else if (type == CoverData.NODE_T) {
                    sb.append('T').append(' ')
                        .append(readIndex(triples.in, h.indexWidth, k, "triple"));
                    tokens++;
                } else {
                    throw new IOException("unknown cbin node type " + type + " at node " + k);
                }
                if (sb.length() >= BUF) {
                    w.write(sb.toString());
                    sb.setLength(0);
                }
            }
            if (sb.length() > 0) {
                w.write(sb.toString());
            }
            w.flush();
            return tokens;
        } finally {
            closeQuietly(nodes);
            closeQuietly(stables);
            closeQuietly(triples);
            closeQuietly(fos);
        }
    }

    private static long readIndex(final InputStream column, final int width, final long node,
                                  final String what) throws IOException {
        long value = 0;
        for (int i = 0; i < width; i++) {
            final int b = column.read();
            if (b < 0) {
                throw new IOException("cbin " + what + " stream ended early at node " + node);
            }
            value |= ((long) b) << (8 * i);
        }
        return value;
    }

    // ---------------------------------------------------------------- cbin header

    /** Parsed cbin header: counts plus the file offset and length of each deflated block. */
    static final class CbinHeader {

        final int version;
        /** Bytes per index in this file: 2 for v1/v2, whatever v3/v4's header declares. */
        final int indexWidth;
        /** log2 of the LZMA2 dictionary, or 0 in the Deflate versions v1&ndash;v3. */
        final int dictBits;
        final long nodes;
        final long stables;
        final long triples;
        private final long nodeOff;
        private final long nodeComp;
        private final long stableOff;
        private final long stableComp;
        private final long tripleOff;
        private final long tripleComp;

        private CbinHeader(final int version, final int indexWidth, final int dictBits,
                           final long nodes, final long stables, final long triples,
                           final long nodeOff, final long nodeComp, final long stableOff,
                           final long stableComp, final long tripleOff, final long tripleComp) {
            this.version = version;
            this.indexWidth = indexWidth;
            this.dictBits = dictBits;
            this.nodes = nodes;
            this.stables = stables;
            this.triples = triples;
            this.nodeOff = nodeOff;
            this.nodeComp = nodeComp;
            this.stableOff = stableOff;
            this.stableComp = stableComp;
            this.tripleOff = tripleOff;
            this.tripleComp = tripleComp;
        }

        static CbinHeader read(final File cbin) throws IOException {
            final FileInputStream fis = new FileInputStream(cbin);
            try {
                final DataInputStream dis = new DataInputStream(new BufferedInputStream(fis, BUF));
                final byte[] magic = new byte[CoverCodec.MAGIC.length];
                dis.readFully(magic);
                for (int i = 0; i < magic.length; i++) {
                    if (magic[i] != CoverCodec.MAGIC[i]) {
                        throw new IOException(cbin.getName() + " is not a cover.cbin (bad magic)");
                    }
                }
                final int version = dis.readUnsignedByte();
                final boolean wide;
                int width = WIDTH_16;
                int dictBits = 0;
                long off = CoverCodec.MAGIC.length + 1;
                if (version == CoverCodec.VERSION_1) {
                    wide = false;
                } else if (version == CoverCodec.VERSION_2) {
                    wide = true;
                } else if (version == CoverCodec.VERSION_3 || version == CoverCodec.VERSION_4) {
                    wide = true;
                    width = dis.readUnsignedByte();
                    off += 1;
                    if (width != WIDTH_16 && width != WIDTH_32 && width != WIDTH_64) {
                        throw new IOException("cover.cbin declares an index width of " + width
                            + " bytes; only 2, 4 and 8 are defined");
                    }
                    if (version == CoverCodec.VERSION_4) {
                        dictBits = dis.readUnsignedByte();
                        off += 1;
                        Lzma.dictSize(dictBits);   // reject a corrupt dictionary size up front
                    }
                } else {
                    throw new IOException("unsupported cover.cbin version: " + version);
                }

                final long nodes;
                final long stables;
                final long triples;
                if (wide) {
                    nodes = dis.readLong();
                    stables = dis.readLong();
                    triples = dis.readLong();
                    off += 24;
                } else {
                    nodes = dis.readInt();
                    stables = dis.readInt();
                    triples = dis.readInt();
                    off += 12;
                }

                final long[] node = readBlockHeader(dis, wide, off, nodes, "node");
                off = node[2];
                final long[] stable = readBlockHeader(dis, wide, off, stables * width, "stable");
                off = stable[2];
                final long[] triple = readBlockHeader(dis, wide, off, triples * width, "triple");

                return new CbinHeader(version, width, dictBits, nodes, stables, triples,
                    node[0], node[1], stable[0], stable[1], triple[0], triple[1]);
            } finally {
                fis.close();
            }
        }

        /** Returns {dataOffset, compLen, offsetAfterBlock} and skips the block payload. */
        private static long[] readBlockHeader(final DataInputStream dis, final boolean wide,
                                              final long off, final long expectedRaw,
                                              final String what) throws IOException {
            final long rawLen;
            final long compLen;
            final long headerLen;
            if (wide) {
                rawLen = dis.readLong();
                compLen = dis.readLong();
                headerLen = 16;
            } else {
                rawLen = dis.readInt();
                compLen = dis.readInt();
                headerLen = 8;
            }
            if (rawLen != expectedRaw) {
                throw new IOException(what + " block length mismatch: header implies " + expectedRaw
                    + " but block says " + rawLen);
            }
            final long dataOff = off + headerLen;
            skipFully(dis, compLen);
            return new long[] {dataOff, compLen, dataOff + compLen};
        }

        Block openNodes(final File cbin) throws IOException {
            return new Block(cbin, nodeOff, nodeComp, dictBits);
        }

        Block openStables(final File cbin) throws IOException {
            return new Block(cbin, stableOff, stableComp, dictBits);
        }

        Block openTriples(final File cbin) throws IOException {
            return new Block(cbin, tripleOff, tripleComp, dictBits);
        }
    }

    /**
     * One decompressed column, read straight out of the cbin file at its own offset. The three
     * columns are interleaved during a walk, so each needs an independent file handle.
     *
     * <p>{@code dictBits} of 0 means a Deflate block (cbin v1&ndash;v3); anything else is LZMA2
     * (v4) with that dictionary size.
     */
    static final class Block implements java.io.Closeable {

        private final FileInputStream file;
        /** Non-null only for a Deflate block, which owns a native inflater to release. */
        private final Inflater inflater;
        final InputStream in;

        Block(final File cbin, final long off, final long compLen, final int dictBits)
                throws IOException {
            this.file = new FileInputStream(cbin);
            boolean ok = false;
            try {
                skipFully(file, off);
                final InputStream bounded = new BoundedInputStream(file, compLen);
                if (dictBits == 0) {
                    this.inflater = new Inflater();
                    this.in = new BufferedInputStream(
                        new InflaterInputStream(bounded, inflater, BUF), BUF);
                } else {
                    this.inflater = null;
                    this.in = new BufferedInputStream(Lzma.decompressor(bounded, dictBits), BUF);
                }
                ok = true;
            } finally {
                if (!ok) {
                    file.close();
                }
            }
        }

        public void close() throws IOException {
            try {
                file.close();
            } finally {
                if (inflater != null) {
                    inflater.end();
                }
            }
        }
    }

    /** Caps a stream at {@code remaining} bytes so one block cannot read into the next. */
    private static final class BoundedInputStream extends InputStream {

        private final InputStream in;
        private long remaining;

        BoundedInputStream(final InputStream in, final long remaining) {
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
    }

    // ---------------------------------------------------------------- text scanner

    /**
     * Forward-only tokenizer over {@code cover.txt}. Scans through a fixed character buffer, so
     * the text is never materialized and the file may be arbitrarily large.
     */
    private static final class Scanner {

        private final Reader in;
        private final char[] buf = new char[BUF];
        private int pos;
        private int lim;
        private boolean eof;
        private long offset;

        Scanner(final Reader in) {
            this.in = in;
        }

        long offset() {
            return offset;
        }

        private int peek() throws IOException {
            if (pos < lim) {
                return buf[pos];
            }
            if (eof) {
                return -1;
            }
            for (;;) {
                final int n = in.read(buf, 0, buf.length);
                if (n < 0) {
                    eof = true;
                    pos = 0;
                    lim = 0;
                    return -1;
                }
                if (n > 0) {
                    pos = 0;
                    lim = n;
                    return buf[0];
                }
            }
        }

        private void advance() {
            pos++;
            offset++;
        }

        /** Consumes and returns the next non-whitespace character, or -1 at end of file. */
        int nextNonSpace() throws IOException {
            for (;;) {
                final int c = peek();
                if (c < 0) {
                    return -1;
                }
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                    advance();
                    continue;
                }
                advance();
                return c;
            }
        }

        /**
         * Reads the index following an {@code S}/{@code T} token and checks it against the size
         * of the code table it points into. The old packer cast this to {@code char}, silently
         * wrapping anything past 65535 (so index 70000 became 4464); the index is now as wide as
         * the file's declared width, and an index with no entry behind it is a hard error here
         * rather than an {@code IndexOutOfBoundsException} inside the viewer.
         */
        long readIndex(final String what, final long tableSize) throws IOException {
            int c = peek();
            while (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                advance();
                c = peek();
            }
            long value = 0;
            boolean any = false;
            while (c >= '0' && c <= '9') {
                value = (value * 10) + (c - '0');
                if (value < 0 || value > MAX_CODE_TABLE) {
                    throw new IOException(what + " index at offset " + offset
                        + " is impossibly large (over " + MAX_CODE_TABLE + ")");
                }
                any = true;
                advance();
                c = peek();
            }
            if (!any) {
                throw new IOException("expected an integer after the " + what
                    + " token at offset " + offset);
            }
            if (value >= tableSize) {
                throw new IOException(what + " index " + value + " at offset " + offset
                    + " has no entry in " + what + "s.txt, which holds " + tableSize
                    + " entries (valid indices are 0.." + (tableSize - 1) + ")");
            }
            return value;
        }
    }

    // ---------------------------------------------------------------- helpers

    static void skipFully(final InputStream in, final long n) throws IOException {
        long left = n;
        while (left > 0) {
            final long got = in.skip(left);
            if (got > 0) {
                left -= got;
                continue;
            }
            if (in.read() < 0) {
                throw new IOException("unexpected end of file while skipping " + n + " bytes");
            }
            left--;
        }
    }

    /** A directory on the same filesystem as {@code target}, for scratch files. */
    private static File tempDirFor(final File target) {
        final File parent = target.getAbsoluteFile().getParentFile();
        return parent != null && parent.isDirectory() ? parent : null;
    }

    private static void closeQuietly(final java.io.Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (final IOException ignored) {
                // Closing a stream we are already abandoning; the original failure matters more.
            }
        }
    }
}
