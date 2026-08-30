package billiards.viewer;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

/**
 * Bundles a cover directory into a single {@code cover.pack} (and back).
 *
 * <p>Usage:
 * <pre>
 *   java billiards.viewer.CoverConverter &lt;coverDir&gt; [&lt;coverDir&gt; ...]            # pack
 *   java billiards.viewer.CoverConverter --unpack &lt;coverDir&gt; [&lt;coverDir&gt; ...]   # restore text
 * </pre>
 *
 * <p>Packing verifies, before reporting success, that the pack round-trips to identical text
 * files and that the cover round-trips to the exact original token stream. After a successful
 * pack you can delete every {@code *.txt} in the folder: the viewer loads directly from
 * {@code cover.pack}, and {@code --unpack} regenerates the text files whenever the external
 * C++ verifier (which reads them) needs to run.
 */
public final class CoverConverter {

    private CoverConverter() {
    }

    public static void main(final String[] args) throws IOException {
        if (args.length == 0) {
            System.err.println("usage: CoverConverter [--unpack] <coverDir> [<coverDir> ...]");
            System.exit(2);
            return;
        }
        final boolean unpack = "--unpack".equals(args[0]);
        final int start = unpack ? 1 : 0;
        int failures = 0;
        for (int i = start; i < args.length; i++) {
            final File dir = new File(args[i]);
            try {
                if (unpack) {
                    unpack(dir);
                } else {
                    pack(dir);
                }
            } catch (final RuntimeException | IOException e) {
                failures++;
                System.err.println("FAILED " + args[i] + ": " + e.getMessage());
            }
        }
        if (failures > 0) {
            System.exit(1);
        }
    }

    private static void pack(final File dir) throws IOException {
        final File out = new File(dir, "cover.pack");
        final CoverStream.Counts counts = CoverPack.packDirectory(dir, out);

        // Verify the small bundled text files round-trip byte-for-byte. The cover entry is
        // skipped here so a huge cover is never pulled into the heap.
        final CoverPack pack = CoverPack.readBlobs(out);
        long textTotal = 0;
        for (final String name : CoverPack.TEXT_FILES) {
            final File f = new File(dir, name);
            if (!f.exists()) {
                continue;
            }
            textTotal += f.length();
            final byte[] original = CoverPack.readAllBytes(f);
            if (!Arrays.equals(original, pack.blob(name))) {
                throw new IOException("round-trip mismatch for " + name);
            }
        }

        // Verify the cover itself round-trips to the exact original token stream, streaming both
        // sides out of the pack we just wrote.
        final File coverTxt = new File(dir, "cover.txt");
        textTotal += coverTxt.length();
        final File tmpCbin = File.createTempFile("cover-verify", ".cbin", dir);
        final long tokens;
        try {
            CoverPack.extractCoverTo(out, tmpCbin);
            tokens = CoverStream.verifyCbinAgainstText(tmpCbin, coverTxt);
        } finally {
            tmpCbin.delete();
        }
        if (tokens != counts.tokens) {
            throw new IOException("token count disagreement: packer saw " + counts.tokens
                + ", verifier saw " + tokens);
        }

        final long packSize = out.length();
        System.out.printf(
            "%s: %,d cover tokens  |  all text %,d B -> cover.pack %,d B  (%.1fx smaller)%n",
            dir.getName(), tokens, textTotal, packSize, (double) textTotal / (double) packSize);
        System.out.printf("  %,d nodes  |  %,d stable leaves  |  %,d triple leaves%n",
            counts.nodes, counts.stables, counts.triples);
        System.out.printf("  index width %d bytes  |  cbin v%d, %s%n", counts.indexWidth,
            counts.cbinVersion, counts.isLzma2() ? "LZMA2" : "Deflate");
        System.out.println("  bundled: " + pack.names() + " + cover");
        System.out.printf("  viewing this cover needs about %,d MB of heap for the cover arrays "
            + "alone -- run the viewer with -Xmx%dg or more%n",
            counts.viewerHeapBytes() / (1024 * 1024), suggestedViewerGigs(counts));
        System.out.println("  -> safe to delete the .txt files; run with --unpack to restore them");
    }

    /** Cover arrays plus room for the rest of the viewer, rounded up to whole gigabytes. */
    private static long suggestedViewerGigs(final CoverStream.Counts counts) {
        final long gib = 1024L * 1024L * 1024L;
        return Math.max(2L, ((counts.viewerHeapBytes() * 3L / 2L) + gib - 1) / gib);
    }

    private static void unpack(final File dir) throws IOException {
        final File pack = new File(dir, "cover.pack");
        if (!pack.exists()) {
            throw new IOException("no cover.pack in " + dir);
        }
        CoverPack.unpack(pack, dir);
        System.out.println(dir.getName() + ": restored text files from cover.pack");
    }
}
