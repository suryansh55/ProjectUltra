package billiards.pattern;

/**
 * <b>Jeff Khuu</b><br>
 * <b>May 08, 2026</b>
 * <p>
 * <i>InvalidTriple</i> represents the invalid states of a triple during initialization.
 * </p>
 */
public enum InvalidTriple {
	INCORRECT_CODES_LENGTH("Incorrect number of codes. The triple was not given exactly three codes."),
	MISPLACED_STABLE("Misplaced stable. Found a stable code sequence where an unstable code sequence was expected."),
	MISPLACED_UNSTABLE(
			"Misplaced unstable. Found an unstable code sequence where a stable code sequence was expected.");
	// NOTE: To be completely sure a triple is valid we should also check whether
	// the stables are boundaries to the unstable

	private String message;

	private InvalidTriple(String message) {
		this.message = message;
	}

	public String getErrorMessage() {
		return this.message;
	}
}
