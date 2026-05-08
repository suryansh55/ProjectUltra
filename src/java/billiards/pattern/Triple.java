package billiards.pattern;

import javaslang.control.Either;

import billiards.codeseq.ClassifiedCodeSequence;

/**
 * <b>Jeff Khuu</b><br>
 * <b>May 06, 2026</b>
 * <p>
 * <i>Triple</i> represents a Triple of CodeSequences. A triple must always be
 * of the of the form stable, unstable, stable.
 * where the negative and positive stables are
 * </p>
 */
public class Triple implements Comparable<Triple> {
	private final ClassifiedCodeSequence negativeStable, unstable, positiveStable;

	private Triple(final ClassifiedCodeSequence negative, final ClassifiedCodeSequence unstable,
			final ClassifiedCodeSequence positive) {
		this.negativeStable = negative;
		this.unstable = unstable;
		this.positiveStable = positive;
	}

	/**
	 * Factory constructor for a Triple which takes a list of code sequences
	 * Creation fails when one of the following is true:
	 * (1) codes.length != 3
	 * (2) codes[0] and codes[2] are not stable
	 * (3) codes[1] is not unstable
	 * 
	 * @param codes Array of three classified code sequences
	 */
	public static Either<InvalidTriple, Triple> create(ClassifiedCodeSequence[] codes) {
		if (codes.length != 3)
			return Either.left(InvalidTriple.INCORRECT_CODES_LENGTH);
		else if (!(codes[0].stable && codes[2].stable))
			return Either.left(InvalidTriple.MISPLACED_STABLE);
		else if (codes[1].stable)
			return Either.left(InvalidTriple.MISPLACED_UNSTABLE);

		// TODO: To prevent an invalid triple from being created it should be tested
		// whether both stables actually intersect along a common boundary unstable code
		// line and whether that unstable code line is the given unstable

		return Either.right(new Triple(codes[0], codes[1], codes[2]));
	}

	public ClassifiedCodeSequence getNegativeStable() {
		return negativeStable;
	}

	public ClassifiedCodeSequence getUnstable() {
		return unstable;
	}

	public ClassifiedCodeSequence getPositiveStable() {
		return positiveStable;
	}

	public ClassifiedCodeSequence[] getCodeSequences() {
		return new ClassifiedCodeSequence[] { negativeStable, unstable, positiveStable };
	}

	@Override
	public String toString() {
		return String.format("%s, %s, %s", negativeStable, unstable, positiveStable);
	}

	@Override
	public int compareTo(Triple o) {
		return this.unstable.compareTo(o.unstable);
	}

	@Override
	public int hashCode() {
		return super.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		final Triple other = (Triple) obj;

		return (other.negativeStable.equals(this.negativeStable))
				&& (other.unstable.equals(this.unstable))
				&& (other.positiveStable.equals(this.positiveStable));
	}

}
