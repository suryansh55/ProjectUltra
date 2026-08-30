package billiards.viewer;

import org.tukaani.xz.FinishableOutputStream;
import org.tukaani.xz.FinishableWrapperOutputStream;
import org.tukaani.xz.LZMA2InputStream;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.UnsupportedOptionsException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * The LZMA2 settings the cover format is written with, in one place.
 *
 * <p>Deflate &mdash; what {@code cbin} v1&ndash;v3 used &mdash; matches only within a 32&nbsp;KB
 * window. A cover is a serialized tree whose redundancy is overwhelmingly <em>long range</em>:
 * whole subtrees recur megabytes apart, so most of what Deflate could exploit is simply out of
 * its reach. LZMA2 with a 64&nbsp;MB dictionary sees those repeats and is about 40% smaller on
 * the same bytes, with no change to the column layout.
 *
 * <p>Measured on a 130M-node cover (raw column bytes &rarr; stored bytes):
 * <pre>
 *   node column    130,482,089  Deflate  4,138,697   LZMA2  2,234,015   -46%
 *   stable column  386,120,672  Deflate 11,226,812   LZMA2  7,635,138   -32%
 * </pre>
 *
 * <p>Decoding stays cheap &mdash; about 600&nbsp;MB/s of raw output, against Deflate's
 * 1,200&nbsp;MB/s &mdash; so a cover that used to inflate in seconds still does. Encoding is the
 * side that pays: roughly 3&nbsp;MB/s against Deflate's 20, which is why
 * {@link CoverStream} runs the three columns' encoders on their own threads.
 *
 * <p>Backed by XZ for Java (public domain, {@code libs/xz-1.9.jar}), which targets Java&nbsp;7
 * and so runs on the Oracle JDK 1.8.0_66 toolchain this source tree is built with.
 */
final class Lzma {

    /**
     * log2 of the dictionary the writer uses: 2^26 = 64&nbsp;MiB. Decoding allocates exactly
     * this much; encoding needs roughly ten times it for the match finder.
     */
    static final int DICT_BITS = 26;

    /** Smallest and largest dictionary a file may declare, guarding a corrupt header. */
    private static final int MIN_DICT_BITS = 12;
    private static final int MAX_DICT_BITS = 30;

    private Lzma() {
    }

    /**
     * The dictionary to write a stream of at most {@code rawUpperBound} bytes with: never larger
     * than {@link #DICT_BITS}, and never larger than the data itself, since a dictionary can only
     * hold what has already gone past. Matters because the encoder's match finder wants roughly
     * ten times the dictionary in heap whatever the input size &mdash; without this a one-megabyte
     * test cover would demand the same 700&nbsp;MB per column as a ten-gigabyte one.
     *
     * <p>A decoder may always use a dictionary at least as large as the encoder did, so a header
     * declaring this value stays correct even when the bound was a loose one.
     */
    static int dictBitsFor(final long rawUpperBound) {
        int bits = MIN_DICT_BITS;
        while (bits < DICT_BITS && (1L << bits) < rawUpperBound) {
            bits++;
        }
        return bits;
    }

    static int dictSize(final int dictBits) throws IOException {
        if (dictBits < MIN_DICT_BITS || dictBits > MAX_DICT_BITS) {
            throw new IOException("this cover declares an LZMA2 dictionary of 2^" + dictBits
                + " bytes; only 2^" + MIN_DICT_BITS + " to 2^" + MAX_DICT_BITS + " are supported");
        }
        return 1 << dictBits;
    }

    /**
     * Position bits for a column of fixed-width records: an index column striding 4 bytes wants
     * {@code pb=2} so the coder keeps a separate probability context per byte of the record.
     * Worth about 1% on the stable column, and the node column (stride 1, {@code pb=0}) needs it
     * to not spread its contexts across four slots that all mean the same thing.
     */
    static int posBitsForStride(final int stride) {
        int bits = 0;
        for (int s = stride; s > 1; s >>= 1) {
            bits++;
        }
        return bits;
    }

    /**
     * Preset 9 with the "extreme" search parameters. Those are what buy most of the win over
     * Deflate: preset 9 on its own leaves the node column at 3,370,929 bytes rather than
     * 2,234,015, for a third of the encoding time.
     */
    private static LZMA2Options options(final int posBits, final int dictBits) throws IOException {
        try {
            final LZMA2Options o = new LZMA2Options(9);
            o.setDictSize(dictSize(dictBits));
            o.setLcLp(3, 0);
            o.setPb(posBits);
            o.setMode(LZMA2Options.MODE_NORMAL);
            o.setNiceLen(273);
            o.setDepthLimit(512);
            o.setMatchFinder(LZMA2Options.MF_BT4);
            return o;
        } catch (final UnsupportedOptionsException e) {
            throw new IOException("LZMA2 rejected the cover codec settings (pb=" + posBits + ")", e);
        }
    }

    /**
     * A compressing sink over {@code out}. The caller must call {@code finish()} on the result
     * (not just {@code close()}) to flush the final LZMA2 chunk.
     */
    static FinishableOutputStream compressor(final OutputStream out, final int posBits,
                                             final int dictBits) throws IOException {
        return options(posBits, dictBits).getOutputStream(new FinishableWrapperOutputStream(out));
    }

    static InputStream decompressor(final InputStream in, final int dictBits) throws IOException {
        return new LZMA2InputStream(in, dictSize(dictBits));
    }

    static byte[] compress(final byte[] raw, final int posBits, final int dictBits)
            throws IOException {
        final ByteArrayOutputStream bytes =
            new ByteArrayOutputStream(Math.max(64, raw.length / 16));
        final FinishableOutputStream out = compressor(bytes, posBits, dictBits);
        out.write(raw);
        out.finish();
        return bytes.toByteArray();
    }

    static byte[] decompress(final byte[] comp, final int rawLen, final int dictBits)
            throws IOException {
        final byte[] out = new byte[rawLen];
        final InputStream in = decompressor(new ByteArrayInputStream(comp), dictBits);
        try {
            int off = 0;
            while (off < rawLen) {
                final int n = in.read(out, off, rawLen - off);
                if (n < 0) {
                    throw new IOException("LZMA2 block ended after " + off + " of " + rawLen
                        + " bytes");
                }
                off += n;
            }
        } finally {
            in.close();
        }
        return out;
    }
}
