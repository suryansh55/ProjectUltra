package billiards.viewer;

import java.io.IOException;

/**
 * Verifies that a {@link CoverData} regenerates exactly the original {@code cover.txt} token
 * stream. Streams both sequences token-by-token (O(1) extra memory) rather than materializing
 * either. Used by the converter's self-check.
 */
final class TokenCheck {

    private TokenCheck() {
    }

    static long verify(final String original, final CoverData data) throws IOException {
        final int n = original.length();
        int i = 0;
        int sIndex = 0;
        int tIndex = 0;
        long count = 0;

        for (int node = 0; node < data.nodeType.length; node++) {
            final byte type = data.nodeType[node];

            i = skipWhitespace(original, i, n);
            if (i >= n) {
                throw new IOException("original ended early at node " + node);
            }
            final char letter = original.charAt(i);
            i++;
            count++;

            if (type == CoverData.NODE_D) {
                expect(letter == 'D', node, "D", letter);
            } else if (type == CoverData.NODE_E) {
                expect(letter == 'E' || letter == 'H', node, "E/H", letter);
            } else if (type == CoverData.NODE_S) {
                expect(letter == 'S', node, "S", letter);
                i = skipWhitespace(original, i, n);
                final long[] parsed = parseInt(original, i, n);
                i = (int) parsed[1];
                count++;
                if ((int) parsed[0] != data.sCode[sIndex++]) {
                    throw new IOException("stable index mismatch at node " + node);
                }
            } else if (type == CoverData.NODE_T) {
                expect(letter == 'T', node, "T", letter);
                i = skipWhitespace(original, i, n);
                final long[] parsed = parseInt(original, i, n);
                i = (int) parsed[1];
                count++;
                if ((int) parsed[0] != data.tCode[tIndex++]) {
                    throw new IOException("triple index mismatch at node " + node);
                }
            }
        }

        i = skipWhitespace(original, i, n);
        if (i != n) {
            throw new IOException("original has trailing tokens after node stream");
        }
        return count;
    }

    private static void expect(final boolean ok, final int node, final String want, final char got)
            throws IOException {
        if (!ok) {
            throw new IOException("token mismatch at node " + node + ": expected " + want + " got '" + got + "'");
        }
    }

    private static int skipWhitespace(final String s, final int from, final int n) {
        int i = from;
        while (i < n) {
            final char c = s.charAt(i);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                i++;
            } else {
                break;
            }
        }
        return i;
    }

    // Returns {value, endIndex}.
    private static long[] parseInt(final String s, final int from, final int n) throws IOException {
        int i = from;
        long value = 0;
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
            throw new IOException("expected integer at offset " + from);
        }
        return new long[] {value, i};
    }
}
