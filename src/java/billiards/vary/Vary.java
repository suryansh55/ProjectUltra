package billiards.vary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

import org.apache.commons.math3.util.FastMath;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.api.set.sorted.MutableSortedSet;
import org.eclipse.collections.impl.list.mutable.FastList;
import org.eclipse.collections.impl.set.mutable.UnifiedSet;
import org.eclipse.collections.impl.set.sorted.mutable.TreeSortedSet;

import billiards.codeseq.ClassifiedCodeSequence;
import billiards.codeseq.CodeType;
import billiards.codeseq.CodeTypeSet;
import billiards.codeseq.CodeTypeCollection;
import billiards.geometry.Vector2;
import billiards.viewer.Utils;
import billiards.wrapper.Wrapper;

public class Vary {
	/*
	 * Jul,31 Marco Mai
	 * 1. the excuator will run each loop one by one
	 * 2. code type will transfer to the backend will computing, only transfer match
	 * one back to java here reduce memory usage
	 * 3. parallel computing most of the for loop
	 */
	public static MutableSet<ClassifiedCodeSequence> findCodes3(
			final double xCoord, final double yCoord, final int min, final int max, final double shots,
			final CodeTypeSet types, final ExecutorService executor) {

        Wrapper.backend_reset_cancel();
		final double xRad = FastMath.toRadians(xCoord);
		final double yRad = FastMath.toRadians(yCoord);

		final double base = Math.sin(xRad + yRad);

		final MutableSet<ClassifiedCodeSequence> codeSeqs = new UnifiedSet<>();

		final MutableList<ClassifiedCodeSequence> futures = new FastList<>();
		final MutableList<Future<MutableList<ClassifiedCodeSequence>>> futures2 = new FastList<>();

		final double increment = base / (shots + 1);

		// run the CS-specific code
		if (types.CS) {
			double xAngle = Double.valueOf(xRad);
			double yAngle = Double.valueOf(yRad);

			for (int i = 0; i < 3; i++) {

				final Double finX = xAngle;
				final Double finY = yAngle;

				final Future<MutableList<ClassifiedCodeSequence>> future = executor
						.submit(() -> VaryCS.fireAway(min, max, finX, finY, types.toString()));
				// final Future<MutableList<ClassifiedCodeSequence>> future =
				// executor.submit(() -> VaryCS.fireAway(min, max, finX, finY));

				try {
					MutableList<ClassifiedCodeSequence> result = future.get();
					futures.addAll(result);
				} catch (InterruptedException | ExecutionException e) {
					throw new RuntimeException(e); // or handle it however you need
				}

				double zAngle = Double.valueOf(Math.PI - xAngle - yAngle);
				xAngle = Double.valueOf(yAngle);
				yAngle = Double.valueOf(zAngle);
			}
		}
		// run the non-CS-specific code
		if (types.OSO || types.ONS || types.CNS || types.OSNO) {

			for (int count = 1; count <= shots; ++count) {
				final double pos = count * increment;
				final Future<MutableList<ClassifiedCodeSequence>> future = executor
						.submit(() -> Vary3.fireAway(min, max, xRad, yRad, pos, types.toString()));
				// executor.submit(() -> Vary3.fireAway(min, max, xRad, yRad, pos));

				futures2.add(future);
			}
			for (Future<MutableList<ClassifiedCodeSequence>> future : futures2) {
				try {
					MutableList<ClassifiedCodeSequence> partial = future.get(); // get the actual list
					futures.addAll(partial); // now addAll on MutableList, not Future
				} catch (InterruptedException | ExecutionException e) {
					e.printStackTrace(); // handle exceptions as needed
				}
			}
		}

		codeSeqs.addAll(futures);

		return codeSeqs;
	}


	public static MutableSet<ClassifiedCodeSequence> findCodes3Parallel(
			final double xCoord, final double yCoord, final int min, final int max, final double shots,
			final CodeTypeSet types, final ExecutorService executor) {

		final double xRad = FastMath.toRadians(xCoord);
		final double yRad = FastMath.toRadians(yCoord);

		final double base = Math.sin(xRad + yRad);

		final MutableSet<ClassifiedCodeSequence> codeSeqs = new UnifiedSet<>();

		final MutableList<ClassifiedCodeSequence> futures = new FastList<>();
		final MutableList<Future<MutableList<ClassifiedCodeSequence>>> futures2 = new FastList<>();

		final double increment = base / (shots + 1);

		// run the CS-specific code
		if (types.CS) {
			double xAngle = Double.valueOf(xRad);
			double yAngle = Double.valueOf(yRad);

			for (int i = 0; i < 3; i++) {

				final Double finX = xAngle;
				final Double finY = yAngle;

				final Future<MutableList<ClassifiedCodeSequence>> future = executor
						.submit(() -> VaryCS.fireAway(min, max, finX, finY, types.toString()));
				// final Future<MutableList<ClassifiedCodeSequence>> future =
				// executor.submit(() -> VaryCS.fireAway(min, max, finX, finY));

				try {
					MutableList<ClassifiedCodeSequence> result = future.get();
					futures.addAll(result);
				} catch (InterruptedException | ExecutionException e) {
					throw new RuntimeException(e); // or handle it however you need
				}

				double zAngle = Double.valueOf(Math.PI - xAngle - yAngle);
				xAngle = Double.valueOf(yAngle);
				yAngle = Double.valueOf(zAngle);
			}
		}

		// run the non-CS-specific code
		if (types.OSO || types.ONS || types.CNS || types.OSNO) {

			for (int count = 1; count <= shots; ++count) {
				final double pos = count * increment;
				final Future<MutableList<ClassifiedCodeSequence>> future = executor
						.submit(() -> Vary3.fireAway(min, max, xRad, yRad, pos, types.toString()));
				// executor.submit(() -> Vary3.fireAway(min, max, xRad, yRad, pos));

				futures2.add(future);
			}
			for (Future<MutableList<ClassifiedCodeSequence>> future : futures2) {
				try {
					MutableList<ClassifiedCodeSequence> partial = future.get(); // get the actual list
					futures.addAll(partial); // now addAll on MutableList, not Future
				} catch (InterruptedException | ExecutionException e) {
					e.printStackTrace(); // handle exceptions as needed
				}
			}
		}

		codeSeqs.addAll(futures);

		return codeSeqs;
	}

	// boolean[] types should be in the order OSO, CS, CNS, ONS, OSNO
	public static MutableSet<ClassifiedCodeSequence> findCodes4(
			final double xCoord, final double yCoord, final int min, final int max, final double shots,
			final CodeTypeSet types) {

        Wrapper.backend_reset_cancel();
		final double xRad = FastMath.toRadians(xCoord);
		final double yRad = FastMath.toRadians(yCoord);

		final MutableSet<ClassifiedCodeSequence> codeSeqs = new UnifiedSet<>();

		final ExecutorService executor = Executors.newFixedThreadPool(Utils.numThreads);

		try{
		if (types.isCSOnly()) {
			// run the CS-specific code

			double xAngle = Double.valueOf(xRad);
			double yAngle = Double.valueOf(yRad);

			for (int i = 0; i < 3; i++) {

				final Double finX = xAngle;
				final Double finY = yAngle;

				final Future<MutableList<ClassifiedCodeSequence>> future = executor
						.submit(() -> VaryCS.fireAway(min, max, finX, finY, types.toString()));
				// final Future<MutableList<ClassifiedCodeSequence>> future =
				// executor.submit(() -> VaryCS.fireAway(min, max, finX, finY));

				try {
					MutableList<ClassifiedCodeSequence> result = future.get();
					codeSeqs.addAll(result);
				} catch (InterruptedException | ExecutionException e) {
					throw new RuntimeException(e); // or handle it however you need
				}

				double zAngle = Double.valueOf(Math.PI - xAngle - yAngle);
				xAngle = Double.valueOf(yAngle);
				yAngle = Double.valueOf(zAngle);

			}

		} else {
			final MutableList<ClassifiedCodeSequence> future = Vary4.fireAway(min, max, xRad, yRad, types.toString());
			codeSeqs.addAll(future);
		}
		} finally {
			executor.shutdownNow(); // Ensure the executor is shut down even if an exception occurs
		}
		return codeSeqs;
	}

	// boolean[] types should be in the order OSO, CS, CNS, ONS, OSNO
	public static MutableSet<ClassifiedCodeSequence> findCodes2(
			final double xCoord, final double yCoord, final int min, final int max, final double shots,
			final CodeTypeSet types, final ExecutorService executor) {

		Wrapper.backend_reset_cancel();
		final double xRad = FastMath.toRadians(xCoord);
		final double yRad = FastMath.toRadians(yCoord);

		final double base = Math.sin(xRad + yRad);

		final MutableSet<ClassifiedCodeSequence> codeSeqs = new UnifiedSet<>();

		final MutableList<Future<MutableList<ClassifiedCodeSequence>>> futures = new FastList<>();

		final double increment = base / (shots + 1);

		if (types.isCSOnly()) {
			// run the CS-specific code

			double xAngle = Double.valueOf(xRad);
			double yAngle = Double.valueOf(yRad);

			for (int i = 0; i < 3; i++) {

				final Double finX = xAngle;
				final Double finY = yAngle;

				final Future<MutableList<ClassifiedCodeSequence>> future = executor
						.submit(() -> VaryCS.fireAway(min, max, finX, finY, types.toString()));

				futures.add(future);

				double zAngle = Double.valueOf(Math.PI - xAngle - yAngle);
				xAngle = Double.valueOf(yAngle);
				yAngle = Double.valueOf(zAngle);
			}
		} else {
			// run the non-CS-specific code
			for (int count = 1; count <= shots; ++count) {

				final double pos = count * increment;

				final Future<MutableList<ClassifiedCodeSequence>> future = executor
						.submit(() -> Vary3.fireAway(min, max, xRad, yRad, pos, types.toString()));

				futures.add(future);
			}
		}

		for (final Future<MutableList<ClassifiedCodeSequence>> future : futures) {
			try {
				for (final ClassifiedCodeSequence codeSeq : future.get()) {
					final CodeType type = codeSeq.codeType;

					/*
					 * if ((types[0] && type.equals(CodeType.OSO)) ||
					 * (types[1] && type.equals(CodeType.CS)) ||
					 * (types[2] && type.equals(CodeType.CNS)) ||
					 * (types[3] && type.equals(CodeType.ONS)) ||
					 * (types[4] && type.equals(CodeType.OSNO))) {
					 */ // george june12,2019 this is the original

					if (types.hasEnabled(type)) {
						if (codeSeq.codeSum >= min) {
							codeSeqs.add(codeSeq);
						}
					}
				}
			} catch (InterruptedException | ExecutionException e) {
				throw new RuntimeException(e);
			}
		}

		return codeSeqs;
	}

	public static ArrayList<ClassifiedCodeSequence> filterCodes(ArrayList<ClassifiedCodeSequence> codes) {
		final ArrayList<ClassifiedCodeSequence> organizedCodes = new ArrayList<>();
		final CodeType[] codeTypes = { CodeType.CS, CodeType.OSO, CodeType.OSNO, CodeType.CNS, CodeType.ONS };

		// Zhao Yu Li, May 05, 2025.
		// Prints only the middle code of the list of codes with the same type (i.e. CS,
		// OSNO, OSO, etc.)
		// and code length.
		// Zhao Yu Li, May 06, 2025.
		// Groups are distinguished by (code type, code length, and odd-even pattern)
		for (final CodeType type : codeTypes) {
			long currentLength = -1;
			Map<String, ArrayList<ClassifiedCodeSequence>> processedCodes = new HashMap<>();
			Map<String, Integer> processedCodesLength = new HashMap<>();

			for (final ClassifiedCodeSequence code : codes) {
				if (code.codeType.equals(type)) {
					if (currentLength == -1) {
						currentLength = code.codeLength;
					}

					if (code.codeLength == currentLength) {
						processedCodesLength.compute(code.oddEvenPattern,
								(k, lengthCount) -> (lengthCount == null) ? 1 : lengthCount + 1);

						if (!processedCodes.containsKey(code.oddEvenPattern)) {
							processedCodes.put(code.oddEvenPattern, new ArrayList<>());
						}
						processedCodes.get(code.oddEvenPattern).add(code);
					} else {
						for (String oddEvenPattern : processedCodesLength.keySet()) {
							// Only add the middle one
							// Updated Jun 20, 2025.
							// Also adds the first and the last codes.
							addFirstMidLast(organizedCodes, processedCodes, processedCodesLength, oddEvenPattern);
						}

						// Clear and re-initialize for the next iteration
						processedCodes.clear();
						processedCodes.put(code.oddEvenPattern, new ArrayList<>());
						processedCodes.get(code.oddEvenPattern).add(code);
						currentLength = code.codeLength;
						processedCodesLength.clear();
						processedCodesLength.put(code.oddEvenPattern, 1);
					}
				}
			}

			// We reached the end of the iteration, add the middle of last (code type, code
			// length, odd-even) group
			// Updated Jun 20, 2025.
			// Also prints the first and the last codes.
			for (String oddEvenPattern : processedCodesLength.keySet()) {
				if (!processedCodes.get(oddEvenPattern).isEmpty()) {
					addFirstMidLast(organizedCodes, processedCodes, processedCodesLength, oddEvenPattern);
				}
			}
		}
		return organizedCodes;
	}

	private static void addFirstMidLast(
			ArrayList<ClassifiedCodeSequence> organizedCodes,
			Map<String, ArrayList<ClassifiedCodeSequence>> processedCodes,
			Map<String, Integer> processedCodesLength,
			String oddEvenPattern) {
		if (processedCodesLength.get(oddEvenPattern) >= 2)
			organizedCodes.add(processedCodes.get(oddEvenPattern).get(0));

		organizedCodes.add(processedCodes.get(oddEvenPattern)
				.get(processedCodesLength.get(oddEvenPattern) / 2));

		if (processedCodesLength.get(oddEvenPattern) >= 3)
			organizedCodes.add(processedCodes.get(oddEvenPattern).get(processedCodesLength.get(oddEvenPattern) - 1));
	}

	/**
	 * <b>Jeff Khuu</b><br>
	 * <b>May 22, 2026</b>
	 * <p>
	 * <code>varyTrianglesL</code> is a wrapper around findCodes3 which separates
	 * finding codes between CS and non-CS code types with filtering for OSO and
	 * OSNO code types based on side sum and code length.
	 * </p>
	 * 
	 * @param point    Point representing the triangle to perform varyL on
	 * @param min      Minimum code length to consider
	 * @param max      Maximum side sum for each code type
	 * @param shots    Number of shots to perform
	 * @param executor ExecutorService to run computations on
	 */
	public static MutableSortedSet<ClassifiedCodeSequence> varyTrianglesL(final Vector2 point, final int min,
			final CodeTypeCollection<Integer> max, final int shots, final CodeTypeSet types,
			final ExecutorService executor) {
		// Split types into two CodeTypeSets
		final CodeTypeSet noCS = CodeTypeSet.builder()
				.setOSO(types.OSO)
				.setCNS(types.CNS)
				.setONS(types.ONS)
				.setOSNO(types.OSNO)
				.build();
		final CodeTypeSet onlyCS = CodeTypeSet.builder().setCS(types.CS).build();

		final MutableSortedSet<ClassifiedCodeSequence> codesFound = new TreeSortedSet<>();
		codesFound
				.addAll(Vary.findCodes3(point.x, point.y, min, max.CS, shots, onlyCS, executor));
		codesFound
				.addAll(Vary.findCodes3(point.x, point.y, min,
						Math.max(max.OSO, max.OSNO), shots, noCS, executor));
		codesFound.removeIf(code -> code.codeSum > max.get(code.codeType));
		return codesFound;

	}
}
