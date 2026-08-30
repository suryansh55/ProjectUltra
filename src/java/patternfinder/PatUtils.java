package patternfinder;

import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.list.primitive.IntList;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Pattern;

import billiards.codeseq.ClassifiedCodeSequence;
import billiards.codeseq.CodeSequence;
import billiards.viewer.Utils;
import billiards.wrapper.ConnectionPool;
import billiards.wrapper.Wrapper;

public class PatUtils {

    public static final int numThreads = Runtime.getRuntime().availableProcessors();

    public static String trimCodeLine(String line) {

        // strip of comment lines
        line = line.split("//")[0];

        // Jeff Khuu, 2026. A signed pattern puts minus signs after the '#', and those must not be
        // mistaken for the "1 - CS(x, y)" prefix separator. Upstream guards with
        // `indexOf("-") < indexOf("#")`, which also stops splitting a line that has a dash and no '#'
        // at all; keep splitting those, since that is the case this strip was written for.
        final int dash = line.indexOf("-");
        final int hash = line.indexOf("#");
        if (dash >= 0 && (hash < 0 || dash < hash)) {
            line = line.split("-", 2)[1];
        }

        // Remove all the stuff from the other file format
        if (line.contains(")")) {
            line = line.split(Pattern.quote(")"))[1];
            line = line.split("O")[0];
            line = line.split("E")[0];
        }

        return line.trim();
    }

    public static String tripleTrimmer(String line) {
    	if (line.contains(",") && !line.contains(")")) {
    		return line.trim();
    	}
    	else {
    		return trimCodeLine(line);
    	}
    }

    public static String[] removeEmpty(String[] withEmpties) {
    	final ArrayList<String> resultList = new ArrayList<>();
    	for (int i = 0; i < withEmpties.length; i++) {
    		if (!withEmpties[i].replace(" ", "").isEmpty()) {
    			resultList.add(withEmpties[i]);
    		}
    	}
    	final String[] resultArray = new String[resultList.size()];
    	for (int i = 0; i < resultList.size(); i++) {
    		resultArray[i] = resultList.get(i);
    	}
    	return resultArray;
    }

    public static ImmutableIntList listGCD(int[] l) {
    	return listGCD(l, 1);
    }

    public static ImmutableIntList listGCD(int[] l, Integer coef) {
    	int gcd = l[0];

    	for (int i = 1; i < l.length; i++) {
    		if (gcd == 0) {
    			gcd = l[i];
    		} else {
    			gcd = GCD(gcd, l[i]);
    		}
    	}

    	final MutableIntList l2 = new IntArrayList();
    	for (int i = 0; i < l.length; i++) {
    		l2.add(coef * l[i]/gcd);
    	}
    	return l2.toImmutable();
    }

    public static int GCD(Integer a1, Integer a2) {
    	if (a1 == 0 || a2 == 0) {
    		return a1;
    	}
    	int n1 = a1;
    	int n2 = a2;
    	while (n1 != n2) {
    		if (n1 > n2) {
    			n1 -= n2;
    		} else {
    			n2 -= n1;
    		}
    	}
    	return n1;
    }

    /**
     * <b>Jeff Khuu</b><br>
     * <b>May 4, 2026</b>
     * <p>
     *     <i>reduce</i> divides a difference list through by the gcd of its non-zero entries, putting the
     *     pattern it encodes into lowest terms. Zeros are preserved, and signs are kept.
     * </p>
     * @param l A difference list.
     * @return The same list in lowest terms.
     * @example reduce([0, 4, 0, -8]) -> [0, 1, 0, -2]
     */
    public static ImmutableIntList reduce(int[] l) {
        // The gcd is taken over the magnitudes of the non-zero entries: a zero divides nothing, and a
        // sign must not change the size of the step.
        final int[] filtered = Arrays.stream(l).filter(n -> n != 0).map(Math::abs).toArray();

        final int gcd = gcd(filtered);
        final MutableIntList result = new IntArrayList();

        for (int i = 0; i < l.length; ++i) {
            result.add(l[i] / gcd);
        }

        return result.toImmutable();
    }

    /**
     * <b>Jeff Khuu</b><br>
     * <b>May 4, 2026</b>
     * <p>
     *     The greatest common divisor of every integer in <code>l</code>.
     * </p>
     * @param l Array of integers. May be empty.
     * @return The gcd, or 1 for an empty array so callers can divide by it unconditionally.
     */
    public static int gcd(int[] l) {
        if (l.length == 0) return 1;

        int gcd = l[0];

        for (int i = 1; i < l.length; ++i) {
            gcd = gcd(gcd, l[i]);
            if (gcd == 1) break;
        }

        return gcd == 0 ? 1 : gcd;
    }

    /**
     * <b>Jeff Khuu</b><br>
     * <b>May 4, 2026</b>
     * <p>
     *     The greatest common divisor of <code>a</code> and <code>b</code>, by Euclid. Upstream trial-divides
     *     down from min(a, b), which agrees on the positive inputs the pattern code feeds it but is O(min)
     *     and returns 1 rather than the divisor once either argument is zero or negative.
     * </p>
     */
    private static int gcd(int a, int b) {
        int x = Math.abs(a);
        int y = Math.abs(b);

        while (y != 0) {
            final int t = y;
            y = x % y;
            x = t;
        }

        return x;
    }

    /**
     * <b>Jeff Khuu</b><br>
     * <b>May 6, 2026</b>
     * <p>
     *     <i>diff</i> is the element-wise difference between the code numbers of two code sequences of equal
     *     length. This is the raw form of a pattern: entry i says how much code number i moves by.
     * </p>
     * @param left  Left hand code sequence.
     * @param right Right hand code sequence.
     * @return left - right, index by index.
     */
    public static IntList diff(final CodeSequence left, final CodeSequence right) {
        final IntArrayList diff = new IntArrayList();

        final IntList leftSeq = left.codeNumbers;
        final IntList rightSeq = right.codeNumbers;

        assert leftSeq.size() == rightSeq.size();

        for (int i = 0; i < leftSeq.size(); ++i) {
            diff.add(leftSeq.get(i) - rightSeq.get(i));
        }

        return diff;
    }

    public static String printAndTestTrip(final Triple trip, final ConnectionPool pool) {
    	String result = "";
    	for (int i = 0; i < 3; i++) {
			if (!PatUtils.emptyVerify(trip.getCode(i), pool)) {
				result += "empty " + PatUtils.printImm(trip.getCode(i));
			} else {
				result += printImm(trip.getCode(i));
			}
			if (i < 2) {
				result += ", ";
			}

    	}
    	return result;
    }

    public static String repeat(String str, int times) {
        return new String(new char[times]).replace("\0", str);
    }

    public static String printImm(ImmutableIntList imm) {
    	String result = "";
		for (int j = 0; j < imm.size(); j++) {
			result += " " + imm.get(j);
		}
    	return result.trim();
    }

    /**
     * Renders a position-indexed pattern as a list of one-based, signed indices. A negative entry means
     * two is subtracted at that position, and prints as a negative index.
     *
     * @example printPat([0, 2, 0, -1]) -> "2 2 -4"
     */
    public static String printPat(ImmutableIntList pat) {
    	String result = "";
		for (int j = 0; j < pat.size(); j++) {
			// Jeff Khuu, 2026. repeat() builds a char[times] array, so a negative entry used to throw
			// NegativeArraySizeException instead of printing a negative index.
			final int patFactor = pat.get(j);
			result += repeat(" " + (patFactor < 0 ? -(j + 1) : (j + 1)), Math.abs(patFactor));
		}
    	return result.trim();
    }

    /**
     * <b>Jeff Khuu</b><br>
     * <b>May 5, 2026</b>
     * <p>
     *     <i>absMin</i> returns the element of <code>l</code> closest to zero, with its original sign.
     * </p>
     * @example absMin([-5, -2, 1, 3]) -> 1
     * @example absMin([-5, -2, -1, 3]) -> -1
     */
    public static int absMin(final IntList l) {
        int min = l.get(0);
        for (int i = 1; i < l.size(); i++) {
            if (Math.abs(l.get(i)) < Math.abs(min)) min = l.get(i);
        }
        return min;
    }

    /**
     * <b>Jeff Khuu</b><br>
     * <b>May 6, 2026</b>
     * <p>
     *     <i>findLeastCode</i> returns the lexicographically lesser of two code sequences, in the form they
     *     are given. Ties return the first.
     * </p>
     * @preconditions code1.size() == code2.size()
     * @example findLeastCode([1, 2, 1, 4], [1, 8, 1, 16]) -> [1, 2, 1, 4]
     * @example findLeastCode([1, 6, 1, 6], [1, 4, 1, 8]) -> [1, 4, 1, 8]
     */
    public static IntList findLeastCode(final IntList code1, final IntList code2) {
        for (int i = 0; i < code1.size(); ++i) {
            if (code1.get(i) < code2.get(i)) return code1;
            else if (code2.get(i) < code1.get(i)) return code2;
        }
        return code1; // Same base
    }

    public static int intListCompare(final ImmutableIntList l1, final ImmutableIntList l2) {
    	for (int i = 0; i < l1.size(); i++) {
    		if (l1.get(i) > l2.get(i)) {
    			return 1;
    		} else if (l1.get(i) < l2.get(i)) {
    			return -1;
    		}
    	}
    	return 0;
    }

	// this checks if the element is an empty set
	public static boolean emptyVerify(final ImmutableIntList pat, final ConnectionPool pool) {
		return Wrapper.saveToDatabase(pat.toArray(), pool);
	}

	public static boolean emptyVerifyLR(final ImmutableIntList b,
	        final ImmutableIntList c, final ConnectionPool pool) {
	    ClassifiedCodeSequence base;
        ClassifiedCodeSequence code;
        try {
            base = ClassifiedCodeSequence.create(b).get();
            code = ClassifiedCodeSequence.create(c).get();
        } catch (Exception e) {
            throw new RuntimeException("Couldn't make a classified code sequence in supercheckLR");
        }

	    return Wrapper.loadPictureLR(base, code, pool, "empty").isPresent();
	}

    // add a pattern to a code, 'times' times.
    public static ImmutableIntList addImm(ImmutableIntList code, ImmutableIntList pat, int times) {

    	final MutableIntList result = new IntArrayList();
    	

    	final int[] muteCode = code.toArray();
    	for (int i = 0; i < pat.size(); i++) {
    		// Jeff Khuu, 2026. `pat` here is a list of signed one-based indices: the magnitude picks the
    		// position and the sign says whether two is added or subtracted there.
    		final int index = Math.abs(pat.get(i)) - 1;
    		final int step = pat.get(i) < 0 ? -2 : 2;
    		muteCode[index] = muteCode[index] + (step * times);
    	}

    	for (int i = 0; i < code.size(); i++) {
    		result.add(muteCode[i]);
    	}
    	return result.toImmutable();
    }

    // check if a line with extend is valid
    public static boolean xtndValidate(final String line) {
    	if (!line.contains("#")) {
    		return false;
    	}

		final String codeStr;
		final String patStr;
		try {
			codeStr = line.replace("+", "").split("#")[0].trim();
			patStr = line.replace("+", "").split("#")[1].trim();
		} catch (IndexOutOfBoundsException e) {
			return false;
		}

		final String[] codeStrs = Utils.tripleTrimmer(codeStr).split(",");
		final String[] patStrs = patStr.split(",");

		if ((codeStrs.length == 1 && patStrs.length == 1) ||
			(codeStrs.length == 3 && patStrs.length == 3)) {

			for (int i = 0; i < codeStrs.length; i++) {
				final Optional<ImmutableIntList> code = splitString(codeStrs[i]);
				if (!code.isPresent()) {
					return false;
				}
				final Optional<ImmutableIntList> pat = splitString(patStrs[i]);
				if (!pat.isPresent()) {
					return false;
				}
				final int max = pat.get().max();
				if (max > code.get().size()) {
					return false;
				}
			}

			return true;
		}

		return false;
    }


    public static Optional<ImmutableIntList> splitString(final String textCodeSeq) {
        // split on whitespace
    	final String tcsTrim = textCodeSeq.trim();
    	if (tcsTrim.isEmpty()) {
    		return Optional.empty();
    	}
        final String[] textCodeNumbers = tcsTrim.split("\\s+");

        final MutableIntList list = new IntArrayList();

        for (final String textCodeNumber : textCodeNumbers) {
            if (!textCodeNumber.isEmpty()) {
                try {
                    final int codeNumber = Integer.parseInt(textCodeNumber);
                    list.add(codeNumber);
                } catch (final NumberFormatException e) {
                    return Optional.empty();
                }
            }
        }

        return Optional.of(list.toImmutable());
    }
}
