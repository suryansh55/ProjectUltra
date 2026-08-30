package billiards.codeseq;

/**
 * <b>Jeff Khuu</b><br>
 * <b>May 22, 2026</b>
 * <p>
 * <code>CodeTypeSet</code> encodes a set containing at most the five possible
 * code types. When a code type is a member of the set its member field will be
 * true. Useful for encoding the types performed on by Vary algorithms.
 * Once a CodeTypeSet is constructed its member fields guaranteed to be final.
 * </p>
 */
public class CodeTypeSet {
	public final boolean OSO;
	public final boolean CS;
	public final boolean CNS;
	public final boolean ONS;
	public final boolean OSNO;

	/**
	 * <b>Jeff Khuu</b><br>
	 * <b>May 22, 2026</b>
	 * <p>
	 * <code>builder</code> returns a new instance of a CodeTypeSetBuilder. Building
	 * a default builder will produce false for all code types.
	 * </p>
	 */
	public static CodeTypeSetBuilder builder() {
		return new CodeTypeSetBuilder();
	}

	/**
	 * <b>Jeff Khuu</b><br>
	 * <b>May 22, 2026</b>
	 * <p>
	 * <code>getDefault()</code> returns a default configuration of code types.
	 * </p>
	 */
	public static CodeTypeSet getDefault() {
		return builder()
				.setOSO(true)
				.setCS(true)
				.setOSNO(true)
				.build();
	}

	private CodeTypeSet(CodeTypeSetBuilder builder) {
		this.OSO = builder.OSO;
		this.CS = builder.CS;
		this.CNS = builder.CNS;
		this.ONS = builder.ONS;
		this.OSNO = builder.OSNO;

	}

	public static class CodeTypeSetBuilder {
		private boolean OSO = false;
		private boolean CS = false;
		private boolean CNS = false;
		private boolean ONS = false;
		private boolean OSNO = false;

		public CodeTypeSetBuilder setOSO(boolean OSO) {
			this.OSO = OSO;
			return this;
		}

		public CodeTypeSetBuilder setCS(boolean CS) {
			this.CS = CS;
			return this;
		}

		public CodeTypeSetBuilder setCNS(boolean CNS) {
			this.CNS = CNS;
			return this;
		}

		public CodeTypeSetBuilder setONS(boolean ONS) {
			this.ONS = ONS;
			return this;
		}

		public CodeTypeSetBuilder setOSNO(boolean OSNO) {
			this.OSNO = OSNO;
			return this;
		}

		public CodeTypeSet build() {
			return new CodeTypeSet(this);
		}

	}

	/**
	 * <b>Jeff Khuu</b><br>
	 * <b>May 22, 2026</b>
	 * <p>
	 * <code>isCSOnly</code> returns true if and only if CS is true, and all others
	 * are false. It is common to want to check if CS is the only code type
	 * selected.
	 * </p>
	 */
	public boolean isCSOnly() {
		return CS && !OSO && !CNS && !ONS && !OSNO;
	}

	/**
	 * <b>Jeff Khuu</b><br>
	 * <b>May 22, 2026</b>
	 * <p>
	 * <code>isEnabled</code> returns true if and only if the given CodeType is true
	 * within this CodeTypeSet
	 * </p>
	 */
	public boolean hasEnabled(CodeType type) {
		switch (type) {
			case OSO:
				return this.OSO;
			case CS:
				return this.CS;
			case CNS:
				return this.CNS;
			case ONS:
				return this.ONS;
			case OSNO:
				return this.OSNO;
		}
		return false;
	}

	@Override
	public String toString() {
		StringBuilder selectedTypes = new StringBuilder();

		// transfer this to backend checking if right type
		if (this.OSO)
			selectedTypes.append("OSO ");
		if (this.CS)
			selectedTypes.append("CS ");
		if (this.CNS)
			selectedTypes.append("CNS ");
		if (this.ONS)
			selectedTypes.append("ONS ");
		if (this.OSNO)
			selectedTypes.append("OSNO ");

		String reqTypes = selectedTypes.toString().trim();
		return reqTypes;
	}

}
