package billiards.pattern;

import java.util.Arrays;

import billiards.codeseq.ClassifiedCodeSequence;
import javaslang.control.Either;

/**
 * <b>Jeff Khuu</b><br>
 * <b>May 06, 2026</b>
 * <p>
 *     <i>Triple</i> represents a triple of code sequences. A triple must always be of the form
 *     stable, unstable, stable, where the two stables are the negative and positive sides of the unstable.
 * </p>
 */
public final class Triple implements Comparable<Triple> {
    private final ClassifiedCodeSequence negativeStable, unstable, positiveStable;

    private Triple(final ClassifiedCodeSequence negative, final ClassifiedCodeSequence unstable,
            final ClassifiedCodeSequence positive) {
        this.negativeStable = negative;
        this.unstable = unstable;
        this.positiveStable = positive;
    }

    /**
     * Factory constructor for a Triple which takes a list of code sequences.
     * Creation fails when one of the following is true:
     * (1) codes.length != 3
     * (2) codes[0] and codes[2] are not stable
     * (3) codes[1] is not unstable
     *
     * @param codes Array of three classified code sequences.
     */
    public static Either<InvalidTriple, Triple> create(final ClassifiedCodeSequence[] codes) {
        if (codes.length != 3) {
            return Either.left(InvalidTriple.INCORRECT_CODES_LENGTH);
        } else if (!(codes[0].stable && codes[2].stable)) {
            // An unstable turned up in a slot that must hold a stable. Note the drop returns the other
            // constant here, which contradicts the message text attached to it.
            return Either.left(InvalidTriple.MISPLACED_UNSTABLE);
        } else if (codes[1].stable) {
            return Either.left(InvalidTriple.MISPLACED_STABLE);
        }

        // TODO: To prevent an invalid triple from being created it should be tested whether both stables
        // actually intersect along a common boundary unstable code line, and whether that unstable code
        // line is the given unstable.

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
    public int compareTo(final Triple o) {
        return this.unstable.compareTo(o.unstable);
    }

    @Override
    public int hashCode() {
        // Must agree with equals: the drop returns super.hashCode() here, so two equal triples get
        // different hash codes and a HashSet/HashMap of triples silently keeps duplicates.
        return Arrays.hashCode(new Object[] { negativeStable, unstable, positiveStable });
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Triple)) return false;

        final Triple other = (Triple) obj;

        return other.negativeStable.equals(this.negativeStable)
                && other.unstable.equals(this.unstable)
                && other.positiveStable.equals(this.positiveStable);
    }
}
