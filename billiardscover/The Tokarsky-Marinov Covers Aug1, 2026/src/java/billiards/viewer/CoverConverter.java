package billiards.viewer;

import billiards.codeseq.CodePair;
import billiards.codeseq.TriplePair;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

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
        CoverPack.packDirectory(dir, out);

        // Verify: every bundled text file round-trips byte-for-byte, and the cover round-trips
        // to the exact original token stream.
        final CoverPack pack = CoverPack.readFile(out.getPath());
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
        final File coverTxt = new File(dir, "cover.txt");
        textTotal += coverTxt.length();
        final List<CodePair> stables = Cover.parseStables(pack.text("stables.txt").trim());
        final List<TriplePair> triples = Cover.parseTriples(pack.text("triples.txt").trim());
        final CoverData cover = CoverCodec.readBinary(
            new ByteArrayInputStream(pack.coverCbin()), stables, triples);
        final long tokens = TokenCheck.verify(new String(
            CoverPack.readAllBytes(coverTxt), java.nio.charset.Charset.defaultCharset()).trim(), cover);

        final long packSize = out.length();
        System.out.printf(
            "%s: %,d cover tokens  |  all text %,d B -> cover.pack %,d B  (%.1fx smaller)%n",
            dir.getName(), tokens, textTotal, packSize, (double) textTotal / (double) packSize);
        System.out.println("  bundled: " + pack.names() + " + cover");
        System.out.println("  -> safe to delete the .txt files; run with --unpack to restore them");
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
