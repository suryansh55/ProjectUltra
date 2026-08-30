package billiards.pattern;

/**
 * <b>Jeff Khuu</b><br>
 * <b>May 08, 2026</b>
 * <p>
 * <i>InvalidSinglePattern</i> represents a single code sequence pattern that is
 * in an invalid state or that cannot be initialized.
 * </p>
 */
public enum InvalidSinglePattern {
	MISMATCHED_CODE_LENGTHS("Mismatched code sequence lengths. The length of the given patterns is not the same."),
	INVALID_DIFF("Invalid difference found. The difference between the two given codes is not a multiple of two."),
	NON_DISTINCT_CODES("Non distinct codes given. Both given codes are the same.");

	private String message; // NOTE: This pattern is very common across "invalid" types, abstracting this
							// difference into its own interface could be good.

	private InvalidSinglePattern(String message) {
		this.message = message;
	}

	public String getErrorMessage() {
		return this.message;
	}
}
