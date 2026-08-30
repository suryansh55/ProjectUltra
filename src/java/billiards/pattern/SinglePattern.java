package billiards.pattern;

import java.util.Arrays;

import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;

import billiards.codeseq.CodeSequence;
import billiards.codeseq.InvalidCodeSequence;
import javaslang.control.Either;
import billiards.codeseq.ClassifiedCodeSequence;
import patternfinder.PatUtils;

/**
 * <b>Jeff Khuu</b><br>
 * <b>May 06, 2026</b>
 * <p>
 * <i>SinglePattern</i> represents the pattern of a single code sequence. A
 * pattern can be thought of as a set of operations performed on legal codes to
 * find other legal codes. A single consists of only a single code sequence.
 * 
 * A pattern is a sequence of integers representing the index i, in a code
 * sequence to modify by two. If i is negative then two is subtracted and if i
 * is positive then two is added.
 * </p>
 */
public class SinglePattern {
	private final ImmutableIntList pattern; // Pattern in lowest terms
	private final ClassifiedCodeSequence base;
	private int factor; // Factor to transfer the pattern in lowest terms to full pattern

	private SinglePattern(ClassifiedCodeSequence base, ImmutableIntList pattern, int factor) {

		this.base = base;
		this.factor = factor;
		this.pattern = pattern;
	};

	/**
	 * Factory constructor for a SinglePattern which takes two code distinct code
	 * sequences.
	 * Creation fails when one of the following is true:
	 * (1) The given code sequences are not the same length
	 * (2) The given code sequences are exactly the same
	 * 
	 * @param code1 A legal code sequence in standard form
	 * @param code2 A legal code sequence in standard form
	 */
	public static Either<InvalidSinglePattern, SinglePattern> create(CodeSequence code1, CodeSequence code2) {
		if (code1.codeNumbers.size() != code2.codeNumbers.size()) {
			return Either.left(InvalidSinglePattern.MISMATCHED_CODE_LENGTHS);
		}
		if(code1.equals(code2)) {
			return Either.left(InvalidSinglePattern.NON_DISTINCT_CODES);
		}

		ImmutableIntList diff = PatUtils.diff(code2, code1).toImmutable();
		return SinglePattern.create(code1, diff);
	}

	/**
	 * Factory constructor for a SinglePattern which takes a code sequence
	 * Creation fails when one of the following is true:
	 * (1) An element in diff is not divisible by two
	 * 
	 * @param code A legal code sequence in standard form
	 * @param diff List of integers representing the difference between a code
	 *             sequence and the next code sequence in the pattern
	 */
	public static Either<InvalidSinglePattern, SinglePattern> create(CodeSequence code, ImmutableIntList diff) {
		// Check if every element in the diff is divisible by two
		for (int i = 0; i < diff.size(); ++i) {
			if (diff.get(i) % 2 != 0)
				return Either.left(InvalidSinglePattern.INVALID_DIFF);
		}
		int[] filtered = Arrays.stream(diff.toArray()).filter(n -> n != 0).map(n -> Math.abs(n) / 2).toArray();
		int gcd = PatUtils.gcd(filtered);

		MutableIntList pattern = new IntArrayList();
		diff.forEachWithIndex((n, j) -> {
			for (int i = 0; i < Math.abs(n / 2) / gcd; ++i) {
				int index = n > 0 ? j + 1 : -j - 1;
				pattern.add(index);
			}
		});

		MutableIntList reduced = new IntArrayList();
		diff.forEach(n -> reduced.add(n / 2));

		return Either.right(new SinglePattern(
				new ClassifiedCodeSequence(makeBase(reduced.toImmutable(), code)),
				pattern.toImmutable(), gcd));
	}

	/**
	 * <i>makeBase</i> takes a pattern and sequence code in standard form and
	 * returns the calculated base possibly not in standard form. A base is defined
	 * as the lexicographically least standard code sequence that is a part of the
	 * pattern.
	 * </p>
	 * 
	 * @precondition Code must be in standard form and pattern must correspond to
	 *               the code
	 * @param diff A pattern encoded as an int list where each index corresponds
	 *             to the index in the given code and represents the number of
	 *             multiples of two added/subtracted from the code for each
	 *             iteration of the pattern
	 * @param code A code sequence in standard form that is a member of the
	 *             pattern
	 * @return Returns an immutable int list representing the base in standard form
	 */
	private static CodeSequence makeBase(ImmutableIntList diff, CodeSequence code) {
		CodeSequence base = code;
		ImmutableIntList codeNumbers = code.codeNumbers;
		ImmutableIntList reducedDiff = PatUtils.reduce(diff.toArray());

		// Find all coefficients - a coefficient is the number of multiples of twos that
		// result in a non-zero and non-negative pattern
		MutableIntList coefficients = new IntArrayList();
		for (int i = 0; i < reducedDiff.size(); ++i) {
			if (reducedDiff.get(i) == 0)
				continue;
			int coefficient = codeNumbers.get(i) / (reducedDiff.get(i) * 2);
			coefficient = coefficient < 0 ? coefficient + 1 : coefficient - 1;
			coefficients.add(coefficient);
		}

		// Iterate through the coefficients, calculate the corresponding
		// base, check whether the code sequence it generates is valid and then compare
		// with our last known "good" base to see if its lexigraphically lesser
		for (int i = 0; i < coefficients.size(); ++i) {
			int k = coefficients.get(i);
			MutableIntList potentialBaseCodeNumbers = new IntArrayList();
			for (int j = 0; j < codeNumbers.size(); ++j) {
				potentialBaseCodeNumbers.add(codeNumbers.get(j) - (reducedDiff.get(j) * k * 2));
			}
			// NOTE: This currently discards bases not in sequential order, should they be considered part of the pattern?
			Either<InvalidCodeSequence, CodeSequence> potentialBase = CodeSequence.create(potentialBaseCodeNumbers);
			if (potentialBase.isLeft() || CodeSequence.compareIntList(potentialBaseCodeNumbers, base.codeNumbers) > 0)
				continue;

			if (potentialBase.get().compareTo(base) < 0)
				base = potentialBase.get();
		}
		return base;
	}

	public ClassifiedCodeSequence getBase() {
		return this.base;
	}

	@Override
	public String toString() {
		return pattern.makeString(" ");
	}

	public String toStringFull() {
		StringBuilder builder = new StringBuilder();

		for (int i = 0; i < pattern.size(); ++i) {
			for (int j = 0; j < factor; ++j) {
				builder.append(pattern.get(i)).append(" ");
			}
		}

		return builder.deleteCharAt(builder.length() - 1).toString();
	}
}
