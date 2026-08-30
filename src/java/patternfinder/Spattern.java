package patternfinder;

import java.util.Optional;

import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;
import billiards.codeseq.CodeSequence;
import billiards.codeseq.InvalidCodeSequence;

public class Spattern {
	final private ImmutableIntList pat;
	final private ImmutableIntList base;
	final private CodeSequence baseCode;

	public Spattern(ImmutableIntList pat, ImmutableIntList ex) {
		// if (pat.max() == 0) {
		// throw new RuntimeException("Bad pattern");
		// }
		this.pat = pat;
		this.base = makeBase(pat, ex);
		this.baseCode = CodeSequence.create(this.base).get();
	}

	/**
	 * <b>Jeff Khuu</b><br>
	 * <b>May 03, 2026 (Refactored)</b>
	 * <p>
	 * <i>makeBase</i> takes a pattern and sequence code in standard form and
	 * returns the calculated base possibly not in standard form. A base is defined
	 * as the lexicographically least standard code sequence that is a part of the
	 * pattern.
	 * </p>
	 * 
	 * @precondition Code must be in standard form and pattern must correspond to
	 *               the code
	 * @param pattern A pattern encoded as an int list where each index corresponds
	 *                to the index in the given code and represents the number of
	 *                multiples of two added/subtracted from the code for each
	 *                iteration of the pattern
	 * @param code    A code sequence in standard form that is a member of the
	 *                pattern
	 * @return Returns an immutable int list representing the base in standard form
	 */
	private static ImmutableIntList makeBase(ImmutableIntList pattern, ImmutableIntList code) {
		MutableIntList base = new IntArrayList();

		// Find all coefficients - a coefficient is the number of multiples of twos that
		// result in a non-zero and non-negative pattern
		MutableIntList coefficients = new IntArrayList();
		for (int i = 0; i < pattern.size(); ++i) {
			if (pattern.get(i) == 0)
				continue;
			int coefficient = code.get(i) / (pattern.get(i) * 2);
			coefficient = coefficient < 0 ? coefficient + 1 : coefficient - 1;
			coefficients.add(coefficient);
		}

		// Iterate through the coefficients, calculate the corresponding
		// base, check whether the code sequence it generates is valid and then compare
		// with our last known "good" base to see if its lexigraphically lesser
		for (int i = 0; i < coefficients.size(); ++i) {
			int k = coefficients.get(i);
			MutableIntList potentialBase = new IntArrayList();
			for (int j = 0; j < code.size(); ++j) {
				potentialBase.add(code.get(j) - pattern.get(j) * k * 2);
			}

			Optional<InvalidCodeSequence> invalid = CodeSequence.validate(potentialBase);
			if (invalid.isPresent()) {
				continue; // If the code sequeunce is invalid (i.e present in the option)
			}

			if (base.size() == 0) {
				base = potentialBase;
				continue;
			}
			base = PatUtils.findLeastCode(base, potentialBase).toList();
		}

		return base.toImmutable();
	}

	public static boolean same(Spattern p1, Spattern p2) {
		return (p1.getPat().equals(p2.getPat()) && p1.getBase().equals(p2.getBase()));
	}

	public int size() {
		return pat.size();
	}

	public int getPat(int i) {
		return pat.get(i);
	}

	public ImmutableIntList getPat() {
		return pat;
	}

	public ImmutableIntList getBase() {
		return base;
	}

	public CodeSequence getBaseCode() {
		return baseCode;
	}

	public int getN(final Single trip) {
		int n = 0;
		for (int i = 0; i < pat.size(); i++) {
			if (pat.get(i) != 0) {
				n = (trip.getCode().get(i) - base.get(i)) / pat.get(i);
				return Math.abs(n / 2);
			}
		}
		return 0;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null) {
			return false;
		}
		if (!Spattern.class.isAssignableFrom(obj.getClass())) {
			return false;
		}
		final Spattern t2 = (Spattern) obj;
		return pat.equals(t2.getPat()) && base.equals(t2.getBase());
	}

	@Override
	public int hashCode() {
		int hash = 3;
		hash = 53 * hash + (this.pat != null ? this.pat.hashCode() : 0);
		hash = 53 * hash + this.base.hashCode();
		return hash;
	}

	@Override
	public String toString() {
		return "// pat: " + PatUtils.printPat(pat) + "\n// base: " + PatUtils.printImm(base);
	}
}
