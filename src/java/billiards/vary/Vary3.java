package billiards.vary;

import java.util.Optional;

import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.list.mutable.FastList;

import billiards.codeseq.ClassifiedCodeSequence;
import billiards.wrapper.Wrapper;

public final class Vary3 {
	/*
	 * jul,31,2025 Marco Mai
	 * move vary3 to backend, rest of the code here being disable
	 * passing codetype to the backend
	 */
	public static MutableList<ClassifiedCodeSequence> fireAway(final int movesMin, final int movesMax,
			final double xAngle, final double yAngle, final double pos, final String reqTypes) {

		Optional<MutableList<ClassifiedCodeSequence>> values = Wrapper.vary3Cpp(movesMin, movesMax, pos, xAngle, yAngle,
				reqTypes);
		if (!values.isPresent()) {
			final MutableList<ClassifiedCodeSequence> codes = new FastList<>();
			return codes;
		} else {
			MutableList<ClassifiedCodeSequence> codes = values.get();
			return codes;
		}

	}

	/**
	 * <b>Jeff Khuu</b><br>
	 * <b>June 09, 2026</b>
	 * <p>
	 * <code>fireAwayParallel</code> calls a parallelized-version of Vary3, this
	 * version parallelizes the search AND verification of code sequences.
	 * @see src/backend/cpp/vary3.cpp
	 * </p>
	 */
	public static MutableList<ClassifiedCodeSequence> fireAwayParallel(final int movesMin, final int movesMax,
			final double xAngle, final double yAngle, final double pos, final String reqTypes) {

		Optional<MutableList<ClassifiedCodeSequence>> values = Wrapper.vary3Cpp(movesMin, movesMax, pos, xAngle,
				yAngle, reqTypes);
		if (!values.isPresent()) {
			final MutableList<ClassifiedCodeSequence> codes = new FastList<>();
			return codes;
		} else {
			MutableList<ClassifiedCodeSequence> codes = values.get();
			return codes;
		}

	}

}
