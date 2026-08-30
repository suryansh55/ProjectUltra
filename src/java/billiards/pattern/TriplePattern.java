package billiards.pattern;

import java.util.Arrays;

import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;

import billiards.codeseq.ClassifiedCodeSequence;
import billiards.codeseq.InvalidCodeSequence;
import javaslang.control.Either;
import patternfinder.PatUtils;

/**
 * <b>Jeff Khuu</b><br>
 * <b>May 08, 2026</b>
 * <p>
 *     <i>TriplePattern</i> represents a valid pattern for a triple. A triple pattern can be made given two
 *     triples that are members of the pattern, or from a triple that is a member of the pattern and an indexed
 *     collection of the differences between the given triple and another member of the pattern. Patterns are
 *     guaranteed to be "in lowest terms".
 * </p>
 */
public final class TriplePattern {
    private final Triple base;
    private final ImmutableIntList[] patterns;
    private final int factor;

    private TriplePattern(final Triple base, final ImmutableIntList[] patterns, final int factor) {
        this.base = base;
        this.factor = factor;
        this.patterns = patterns;
    }

    /**
     * Factory constructor for a TriplePattern which takes two distinct triples.
     * Creation fails when one of the following is true:
     * (1) The length of a code sequence (negative stable, unstable, or positive stable) in triple1 does not
     * match its corresponding code sequence in triple2
     *
     * @param triple1 A valid triple.
     * @param triple2 A valid triple.
     */
    public static Either<InvalidTriplePattern, TriplePattern> create(final Triple triple1, final Triple triple2) {
        if (triple1.getNegativeStable().length() != triple2.getNegativeStable().length()
                || triple1.getUnstable().length() != triple2.getUnstable().length()
                || triple1.getPositiveStable().length() != triple2.getPositiveStable().length()) {
            return Either.left(InvalidTriplePattern.MISMATCHED_CODE_LENGTH);
        }

        final ImmutableIntList[] pattern = {
                PatUtils.diff(triple2.getNegativeStable().codeSequence,
                        triple1.getNegativeStable().codeSequence).toImmutable(),
                PatUtils.diff(triple2.getUnstable().codeSequence,
                        triple1.getUnstable().codeSequence).toImmutable(),
                PatUtils.diff(triple2.getPositiveStable().codeSequence,
                        triple1.getPositiveStable().codeSequence).toImmutable()
        };

        return TriplePattern.create(triple1, pattern);
    }

    /**
     * Factory constructor for a TriplePattern which takes a triple and a pattern.
     * Creation fails when one of the following is true:
     * (1) The given pattern is not of length 3
     * (2) An element of pattern[0], pattern[1] or pattern[2] is not divisible by 2
     *
     * @param triple A valid triple.
     * @param diffs  An array of three integer lists representing the pattern.
     */
    public static Either<InvalidTriplePattern, TriplePattern> create(final Triple triple,
            final ImmutableIntList[] diffs) {
        if (diffs.length != 3) {
            return Either.left(InvalidTriplePattern.INVALID_CODES_LENGTH);
        }
        for (final ImmutableIntList diff : diffs) {
            for (int i = 0; i < diff.size(); ++i) {
                if (diff.get(i) % 2 != 0) {
                    return Either.left(InvalidTriplePattern.INVALID_DIFF);
                }
            }
        }

        // Find the gcd to reduce the pattern to lowest terms. The gcd is taken across all three code
        // sequences, so the three parts of the pattern stay in step with one another.
        final MutableIntList allDiffs = new IntArrayList();

        for (final ImmutableIntList diff : diffs) {
            for (int i = 0; i < diff.size(); ++i) {
                if (diff.get(i) == 0) continue;
                allDiffs.add(Math.abs(diff.get(i)) / 2);
            }
        }
        final int gcd = PatUtils.gcd(allDiffs.toArray());

        final ImmutableIntList[] patterns = new ImmutableIntList[3];
        for (int i = 0; i < 3; ++i) {
            final ImmutableIntList diff = diffs[i];
            final MutableIntList pattern = new IntArrayList();
            for (int j = 0; j < diff.size(); ++j) {
                final int n = diff.get(j);

                for (int k = 0; k < Math.abs(n / 2) / gcd; ++k) {
                    // Add 1 since patterns are not zero-based indexed
                    final int index = n > 0 ? j + 1 : -j - 1;
                    pattern.add(index);
                }
            }
            patterns[i] = pattern.toImmutable();
        }

        return Either.right(new TriplePattern(makeBase(triple, diffs), patterns, gcd));
    }

    private static Triple makeBase(final Triple triple, final ImmutableIntList[] diffs) {
        Triple base = triple;
        // "Reduce" the diffs to find the total number of 2s added/subtracted per iteration of the pattern
        final ImmutableIntList[] reducedDiffs = Arrays.stream(diffs)
                .map(diff -> PatUtils.reduce(diff.toArray())).toArray(ImmutableIntList[]::new);

        final ClassifiedCodeSequence[] sequences = triple.getCodeSequences();
        // Define coefficients to be the maximum number of times two can be subtracted from an element in the
        // code sequence where the difference is > 0
        final MutableIntList coefficients = new IntArrayList();

        // Find the coefficients from each code sequence in the triple
        for (int i = 0; i < 3; ++i) {
            final ImmutableIntList codeNumbers = sequences[i].codeSequence.codeNumbers;
            final ImmutableIntList diff = reducedDiffs[i];

            diff.forEachWithIndex((n, index) -> {
                if (n == 0) return;

                int coefficient = codeNumbers.get(index) / (2 * n);
                coefficient = coefficient < 0 ? coefficient + 1 : coefficient - 1;
                coefficients.add(coefficient);
            });
        }

        // Find the coefficient that results in the lowest valid code sequence.
        //
        // NOTE: Instead of an IntList of coefficients we could consider using a set instead, or taking the
        // .distinct() elements of the int list.
        coefficientLoop:
        for (int i = 0; i < coefficients.size(); ++i) {
            final int coeff = coefficients.get(i);
            final MutableIntList[] potentialCodes = new MutableIntList[3];
            for (int j = 0; j < 3; ++j) {
                final ImmutableIntList codeNumbers = sequences[j].codeSequence.codeNumbers;
                final MutableIntList potentialCode = new IntArrayList();
                for (int k = 0; k < codeNumbers.size(); ++k) {
                    potentialCode.add(codeNumbers.get(k) - 2 * coeff * reducedDiffs[j].get(k));
                }
                potentialCodes[j] = potentialCode;
            }

            final ClassifiedCodeSequence[] newTripleSequence = new ClassifiedCodeSequence[3];
            for (int j = 0; j < 3; ++j) {
                final Either<InvalidCodeSequence, ClassifiedCodeSequence> sequence =
                        ClassifiedCodeSequence.create(potentialCodes[j]);
                if (sequence.isLeft()) continue coefficientLoop;
                newTripleSequence[j] = sequence.get();
            }

            // A candidate whose stables/unstable no longer sit in the right slots is not a triple at all, so
            // it cannot be this pattern's base. The drop calls .get() straight away and dies on it.
            final Either<InvalidTriple, Triple> newTriple = Triple.create(newTripleSequence);
            if (newTriple.isLeft()) continue;

            if (newTriple.get().compareTo(base) < 0) {
                base = newTriple.get();
            }
        }

        return base;
    }

    public Triple getBase() {
        return this.base;
    }

    /**
     * Produces the string using the reduced pattern.
     */
    @Override
    public String toString() {
        final String[] parts = new String[3];
        for (int i = 0; i < 3; ++i) {
            parts[i] = this.patterns[i].makeString(" ");
        }

        return String.join(", ", parts);
    }

    /**
     * Produces the string using the full pattern, i.e. each reduced part repeated by the factor.
     */
    public String toStringFull() {
        final String[] parts = new String[3];

        for (int i = 0; i < 3; ++i) {
            final StringBuilder part = new StringBuilder();
            for (int j = 0; j < this.patterns[i].size(); ++j) {
                for (int k = 0; k < factor; ++k) {
                    part.append(this.patterns[i].get(j)).append(" ");
                }
            }
            if (part.length() > 0) part.deleteCharAt(part.length() - 1);
            parts[i] = part.toString();
        }

        return String.join(", ", parts);
    }
}
