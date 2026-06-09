package billiards.codeseq;

/**
 * <b>Jeff Khuu</b><br>
 * <b>May 25, 2026</b>
 * <p>
 * <code>CodeTypeCollection</code> encodes a collection of five elements of type
 * T corresponding to each code type. All values must be set and are guaranteed
 * to be final.
 * </p>
 */
public class CodeTypeCollection<T> {
	public final T OSO;
	public final T CS;
	public final T CNS;
	public final T ONS;
	public final T OSNO;

	public CodeTypeCollection(T oso, T cs, T cns, T ons, T osno) {
		this.OSO = oso;
		this.CS = cs;
		this.CNS = cns;
		this.ONS = ons;
		this.OSNO = osno;
	}

	/**
	 * <b>Jeff Khuu</b><br>
	 * <b>May 25, 2026</b>
	 * <p>
	 * <code>get</code> returns the value for a given code type, this is NOT
	 * O(1)/random-access, getting a code type using the CodeType enum is O(n) where
	 * n is the number of possible code types
	 * </p>
	 */
	public T get(CodeType type) {
		// Assert that the type is one of the five expected so that we don't get
		// unexpected behaviour if CodeType changes caused by the default branch in the
		// switch statement
		assert type == CodeType.OSO
				|| type == CodeType.CS
				|| type == CodeType.CNS
				|| type == CodeType.ONS
				|| type == CodeType.OSNO
				: "An unknown CodeType was given. Cannot be matched to a value";

		// NOTE: Perhaps there is a better way to get values from the CodeType enum so
		// that we keep random access, perhaps with a HashMap?
		switch (type) {
			case OSO:
				return OSO;
			case CS:
				return CS;
			case CNS:
				return CNS;
			case ONS:
				return ONS;
			default:
			case OSNO:
				return OSNO;
		}
	}

	@Override
	public String toString() {
		return String.format("OSO: %s CS: %s CNS: %s ONS: %s OSNO: %s", OSO, CS, CNS, ONS, OSNO);
	}
}
