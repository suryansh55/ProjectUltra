package billiards.pattern;

/**
 * <b>Jeff Khuu</b><br>
 * <b>May 08, 2026</b>
 * <p>
 *     <i>InvalidTriplePattern</i> represents an invalid state for the initialization of a pattern for a
 *     triple.
 * </p>
 */
public enum InvalidTriplePattern {
    INVALID_CODES_LENGTH("Incorrect number of codes. The triple was not given exactly three codes."),
    MISMATCHED_CODE_LENGTH("Mismatched code sequence lengths. One of the stables/unstable does not have the same"
            + " length as its corresponding stable/unstable."),
    INVALID_DIFF("Invalid difference found. The difference between the two given patterns is not a multiple of two.");

    private final String message;

    private InvalidTriplePattern(final String message) {
        this.message = message;
    }

    public String getErrorMessage() {
        return this.message;
    }
}
