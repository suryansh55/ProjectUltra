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

		if (line.contains("-")) {
			if (line.indexOf("-") < line.indexOf("#"))
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
		} else {
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

	/**
	 * <b>Jeff Khuu</b><br>
	 * <b>May 04, 2026 (Date of Refactor)</b>
	 * <p>
	 * <i>listGCD</i> finds the greatest non-negative common divisor between an
	 * array of integers
	 * </p>
	 * 
	 * @param l Array of Integers
	 * @return Returns the greatest non-negative common divisor. Guaranteed to b
	 *         greater than or equal to 1.
	 */
	public static ImmutableIntList reduce(int[] l) {
		// Find the greatest common divisor between all non-zero values in the list
		// Find the non-zero elements of l
		int[] filtered = Arrays.stream(l).filter(n -> n != 0).map(n -> Math.abs(n)).toArray();

		int gcd = gcd(filtered);
		final MutableIntList result = new IntArrayList();

		for (int i = 0; i < l.length; ++i) {
			result.add(l[i] / gcd);
		}

		return result.toImmutable();
	}

	/**
	 * <b>Jeff Khuu</b><br>
	 * <b>May 04, 2026</b>
	 * <p>
	 * Calculates and returns the greatest common divisor between all integers in
	 * given list l
	 * </p>
	 * 
	 * @param l int[]
	 * @return int representing the greatest common divisor
	 */
	public static int gcd(int[] l) {
		if (l.length <= 1) {
			return l[0];
		}
		int gcd = l[0];

		for (int i = 1; i < l.length; ++i) {
			gcd = gcd(gcd, l[i]);
			if (gcd == 1)
				break;
		}
		return gcd;
	}

	/**
	 * <b>Jeff Khuu</b><br>
	 * <b>May 04, 2026</b>
	 * <p>
	 * Calculates and returns the greatest common divisor between two integers a and
	 * b using an iterative approach
	 * </p>
	 * 
	 * @param a int
	 * @param b int
	 * @return int representing the greatest common divisor
	 */
	private static int gcd(int a, int b) {
		int i = a < b ? a : b; // find the minimum of a and b

		// Iterate from the smaller number to 1
		for (; i > 1; i--) {
			// Check if i is a divisor
			if (a % i == 0 && b % i == 0)
				return i;
		}
		// Otherwise, return 1
		return 1;

	}

	/**
	 * <b>Jeff Khuu</b><br>
	 * <b>May 05, 2026</b>
	 * <p>
	 * <i>absMin</i> finds the minimum of the absolute value of the elements of a
	 * sequence. In other terms finds the value closest to zero.
	 * </p>
	 * 
	 * @param l MutableIntList
	 * @return The element from l with the least absolute value as its original
	 *         value
	 * 
	 * @example absMin([-5, -2, 1, 3]) -> 1
	 * @example absMin([-5, -2, -1, 3]) -> -1
	 */
	public static int absMin(final IntList l) {
		int min = l.get(0);
		for (int i = 1; i < l.size(); i++) {
			if (Math.abs(l.get(i)) < Math.abs(min))
				min = l.get(i);
		}
		return min;
	}

	/**
	 * <b>Jeff Khuu</b><br>
	 * <b>May 05, 2026</b>
	 * <p>
	 * <i>absMax</i> finds the maximum of the absolute value of the elements of a
	 * sequence. In other terms finds the value closest to zero.
	 * </p>
	 * 
	 * @param l MutableIntList
	 * @return The element from l with the greatest absolute value as its original
	 *         value
	 * 
	 * @example absMax([-5, -2, 1, 3]) -> -5
	 * @example absMax([-2, -1, 3]) -> 3
	 */
	public static int absMax(final IntList l) {
		int max = l.get(0);
		for (int i = 1; i < l.size(); i++) {
			if (Math.abs(l.get(i)) > Math.abs(max))
				max = l.get(i);
		}
		return max;
	}

	/**
	 * <b>Jeff Khuu</b><br>
	 * <b>May 06, 2026</b>
	 * <p>
	 * <i>findLeastBase</i> finds the lexigraphically lesser of two code sequences
	 * in the form they are given.
	 * </p>
	 * 
	 * @preconditions code1.size() == code2.size()
	 * @param code1 IntList representing a code sequence of n numbers
	 * @param code2 IntList representing a code sequence of n numbers
	 * @return Returns the lesser of the two given bases or the first base if
	 *         they're the same
	 * 
	 * @example findLeastCode([1, 2, 1, 4], [1, 8, 1, 16]) -> [1, 2, 1, 4]
	 * @example findLeastCode([1, 6, 1, 6], [1, 4, 1, 8]) -> [1, 4, 1, 8]
	 */
	public static IntList findLeastCode(final IntList code1, final IntList code2) {
		for (int i = 0; i < code1.size(); ++i) {
			if (code1.get(i) < code2.get(i))
				return code1;
			else if (code2.get(i) < code1.get(i))
				return code2;
		}
		return code1; // Same base
	}

	/**
	 * <b>Jeff Khuu</b><br>
	 * <b>May 06, 2026</b>
	 * <p>
	 * <i>diff</i> finds the difference between the code numbers of two given code
	 * sequences. The two code sequences MUST be the same length.
	 * </p>
	 * 
	 * @param left  Left hand code sequeunce
	 * @param right Right hand code sequence
	 * @return A list where each index corresponds to the index of the given code
	 *         sequences
	 */
	public static IntList diff(CodeSequence left, CodeSequence right) {
		IntArrayList diff = new IntArrayList();

		IntList leftSeq = left.codeNumbers;
		IntList rightSeq = right.codeNumbers;

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

	public static String printPat(ImmutableIntList pat) {
		String result = "";
		for (int j = 0; j < pat.size(); j++) {
			int patFactor = pat.get(j);
			result += repeat(" " + (patFactor < 0 ? -(j + 1) : (j + 1)), Math.abs(patFactor));
		}
		return result.trim();
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
			int value = muteCode[Math.abs(pat.get(i)) - 1];
			muteCode[Math.abs(pat.get(i)) - 1] = value + ((pat.get(i) < 0 ? -2 : 2) * times);
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
