package billiards.vary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.set.sorted.MutableSortedSet;
import org.eclipse.collections.impl.list.mutable.FastList;
import org.eclipse.collections.impl.set.sorted.mutable.TreeSortedSet;

import billiards.codeseq.ClassifiedCodeSequence;
import billiards.codeseq.CodeType;
import billiards.codeseq.Storage;
import billiards.codeseq.CodeTypeSet;
import billiards.codeseq.CodeTypeCollection;
import billiards.database.Database;
import billiards.geometry.Vector2;
import billiards.viewer.PriorityCallable;
import billiards.viewer.Utils;
import javaslang.Tuple;
import javaslang.Tuple2;
import javaslang.control.Either;

/**
 * <b>Jeff Khuu</b><br>
 * <b>May 22, 2026</b>
 * <p>
 * <code>VaryLAction</code> is a wrapper class around a single VaryL calculation
 * meant to parallel VaryLTask but operate within the main thread
 * </p>
 * 
 * @see VaryLTask
 */
public class VaryLAction {
	private final List<Storage> results = new ArrayList<>();
	private final VaryLInstance instance;
	private final int min, shots;
	private final Either<Integer, CodeTypeCollection<Integer>> max;
	private final CodeTypeCollection<Integer> maxLengths;
	private final int idx;
	private final CodeTypeSet types;

	public VaryLAction(VaryLInstance instance, int idx, int min, int shots,
			Either<Integer, CodeTypeCollection<Integer>> max, CodeTypeCollection<Integer> maxLengths,
			CodeTypeSet types) {
		this.instance = instance;
		this.idx = idx;
		this.min = min;
		this.shots = shots;
		this.max = max;
		this.maxLengths = maxLengths;
		this.types = types;
	}

	/**
	 * <b>Jeff Khuu</b><br>
	 * <b>May 26, 2026</b>
	 * <p>
	 * Runs the calculation.
	 * </p>
	 * 
	 * @return A tuple containing the number of codes found and a list of storages
	 */
	public Tuple2<Integer, List<Storage>> run() {
		final MutableSortedSet<ClassifiedCodeSequence> usedCodes = new TreeSortedSet<ClassifiedCodeSequence>();
		final MutableList<Future<Either<String, Storage>>> futures = new FastList<>();

		// The meat and potatoes. Finds codes sequentially, and submits them to the
		// executer as they are found.
		// This is the most efficient way to implement varyL since each code can be
		// calculated as soon as it's found, without interfering with the process of
		// finding more codes.

		// Zhao Yu Li, Jun 27, 2025.
		// Removed for loop inside the task; we use a recursion of this task instead
		// (similar to AustinMaxVary, which
		// uses PolyVaryTask). This is to facilitate moving the screen from one point to
		// the next.
		Vector2 coord = this.instance.coords.get(idx);

		MutableSortedSet<ClassifiedCodeSequence> localCodes;
		System.out.println();
		System.out.println("//------------- working on point " + (idx + 1) + " -------------"); // george added // sept
																								// 27,2017
		// The BoyanCodes method vary3() called by varyTrianglesL() can throw
		// exceptions. We need to catch them
		try {
			localCodes = max.isRight()
					? Vary.varyTrianglesL(coord, this.min, this.max.get(), this.shots, this.types,
							this.instance.shotExecutor)
					: new TreeSortedSet<ClassifiedCodeSequence>(
							Vary.findCodes3(coord.x, coord.y, this.min, this.max.getLeft(), this.shots, this.types,
									this.instance.shotExecutor));

			localCodes.removeIf(code -> {
				return code.codeLength > this.maxLengths.get(code.codeType)
						|| this.instance.coverCodes.contains(code.toString());
			});
		} catch (RuntimeException e) {
			if (Thread.interrupted()) {
				return Tuple.of(0, results);
			}
			System.err.println("Terminating because of uncaught exception when finding codeSet");
			throw e;
		}
		// We draw the first i codes we found
		int i = this.instance.maxPrintNum == 0 ? localCodes.size() : this.instance.maxPrintNum;
		AtomicInteger codeNum = new AtomicInteger(1);

		// Take the first code not already drawn, and submit it to the storageExecutor
		// for processing
		if (!this.instance.printMid) {
			for (ClassifiedCodeSequence classCodeSeq : localCodes) {
				if (i <= 0)
					break;
				--i;

				System.out.println(Utils.standard(classCodeSeq, codeNum.getAndIncrement()));
				// Zhao Yu Li, Jun 24, 2025.
				// Replaced code block with function call
				loadStorage(usedCodes, futures, classCodeSeq);
			}
		} else {
			// Zhao Yu Li, May 06, 2025.
			// Prints only the middle code of each (code type, code length, and odd-even
			// pattern) group
			final CodeType[] codeTypes = { CodeType.CS, CodeType.OSO, CodeType.OSNO, CodeType.CNS, CodeType.ONS };

			long currentLength = -1;
			Map<CodeType, Map<String, ArrayList<ClassifiedCodeSequence>>> processedCodes = new HashMap<>();
			Map<CodeType, Map<String, Integer>> processedCodesLength = new HashMap<>();

			for (CodeType codeType : codeTypes) {
				processedCodes.put(codeType, new HashMap<>());
				processedCodesLength.put(codeType, new HashMap<>());
			}

			for (ClassifiedCodeSequence code : localCodes) {
				if (i <= 0)
					break;

				if (currentLength == -1) {
					currentLength = code.codeLength;
				}

				// Zhao Yu Li, Jun 24, 2025.
				// Replaced code block with function call
				if (code.codeLength == currentLength)
					addProcessedCode(processedCodes, processedCodesLength, code);
				else {
					for (CodeType codeType : codeTypes) {
						if (i <= 0)
							break;

						for (String oddEvenPattern : processedCodesLength.get(codeType).keySet()) {
							if (i <= 0)
								break;

							--i;
							printAndLoadStorage(
									processedCodes,
									processedCodesLength,
									codeType,
									oddEvenPattern,
									codeNum,
									usedCodes,
									futures);
						}

						// Clear and re-initialize the maps for the next iteration
						processedCodes.get(codeType).clear();
						processedCodesLength.get(codeType).clear();
					}

					currentLength = code.codeLength;
					processedCodes.get(code.codeType).put(code.oddEvenPattern, new ArrayList<>());
					processedCodes.get(code.codeType).get(code.oddEvenPattern).add(code);
					processedCodesLength.get(code.codeType).put(code.oddEvenPattern, 1);
				}
			}

			for (CodeType codeType : codeTypes) {
				if (i <= 0)
					break;

				// We reached the end of the iteration, add the middle of last (code type, code
				// length, odd-even) group
				for (String oddEvenPattern : processedCodesLength.get(codeType).keySet()) {
					if (i <= 0)
						break;

					// Zhao Yu Li, Jun 24, 2025.
					// Replaced code block with function call
					if (!processedCodes.get(codeType).get(oddEvenPattern).isEmpty()) {
						--i;
						printAndLoadStorage(
								processedCodes,
								processedCodesLength,
								codeType,
								oddEvenPattern,
								codeNum,
								usedCodes,
								futures);
					}
				}
			}
		}
		return Tuple.of(localCodes.size(), this.results);
	}

	// NOTE: Many of these below methods feel like they can be abstracted into
	// static methods

	/**
	 * Loads the <code>Storage</code> for <code>codePrinted</code> and updates the
	 * <code>progress</code>.
	 * 
	 * @param usedCodes   The set of codes we have already loaded a
	 *                    <code>Storage</code> for.
	 * @param futures     Since loading <code>Storage</code> can take some time, we
	 *                    will launch each load as a task, and store its
	 *                    <code>Future</code> in this list.
	 * @param codePrinted The <code>ClassifiedCodeSequence</code> to load a
	 *                    <code>Storage</code> for.
	 */
	private void loadStorage(
			MutableSortedSet<ClassifiedCodeSequence> usedCodes,
			MutableList<Future<Either<String, Storage>>> futures,
			ClassifiedCodeSequence codePrinted) {
		usedCodes.add(codePrinted);
		// Submit the custom PriorityCallable for this code (Node that PriorityCallable
		// is a custom interface)
		futures.add(this.instance.storageExecutor.submit(new PriorityCallable<Either<String, Storage>>() {
			@Override
			public Either<String, Storage> call() {
				Either<String, Storage> result = loadStorage(codePrinted);
				return result;
			}

			@Override
			public int getPriority() {
				return codePrinted.length();
			}
		}));
	}

	// Find the storage associated to a codeSequence if it exists. Return the error
	// if not
	private Either<String, Storage> loadStorage(final ClassifiedCodeSequence classCodeSeq) {
		// Check to see if cancel was called
		if (Thread.interrupted()) {
			// Note that this method is intended to be submitted to an executor, hence this
			// interrupts the thread inside the threadpool
			Thread.currentThread().interrupt();
			System.out.println("//Cancel detected before loadStorage");
			return Either.left("");
		}
		// Load from database if code already exists. If not, calculate
		final Optional<Storage> opt = Database.loadStorage(classCodeSeq, this.instance.pool);
		// Check to see if cancel was called
		if (Thread.interrupted()) {
			Thread.currentThread().interrupt();
			System.out.println("//Cancel detected after loadStorage");
			return Either.left("");
		}
		if (opt.isPresent()) {
			final Storage storage = opt.get();
			this.results.add(storage);
			return Either.right(storage);
		} else {
			return Either.left("//empty set " + classCodeSeq);
		}
	}

	/**
	 * <code>printMidFirstLast</code> and <code>loadStorage</code> wrapped in one
	 * function.
	 * 
	 * @param processedCodes       Stores all (code type, odd-even pattern) groups.
	 *                             All codes are of the same code length.
	 * @param processedCodesLength Stores the size of each (code type, odd-even
	 *                             pattern group) in <code>processedCodes</code>.
	 * @param codeType             The type of <code>ClassifiedCodeSequence</code>
	 *                             we are printing.
	 * @param oddEvenPattern       The odd-even pattern of the codes we are
	 *                             printing.
	 * @param codeNum              Within the context of the whole task, we will
	 *                             print the <code>codeNum</code>'th code and
	 *                             onwards.
	 * @param usedCodes            The set of codes we have already loaded a
	 *                             <code>Storage</code> for.
	 * @param futures              Since loading <code>Storage</code> can take some
	 *                             time, we will launch each load as a task, and
	 *                             store its <code>Future</code> in this list.
	 */
	private void printAndLoadStorage(
			Map<CodeType, Map<String, ArrayList<ClassifiedCodeSequence>>> processedCodes,
			Map<CodeType, Map<String, Integer>> processedCodesLength,
			CodeType codeType,
			String oddEvenPattern,
			AtomicInteger codeNum,
			MutableSortedSet<ClassifiedCodeSequence> usedCodes,
			MutableList<Future<Either<String, Storage>>> futures

	) {
		ArrayList<ClassifiedCodeSequence> codesPrinted = printMidFirstLast(
				processedCodes,
				processedCodesLength,
				codeType,
				oddEvenPattern,
				codeNum);

		for (ClassifiedCodeSequence codePrinted : codesPrinted) {
			// Zhao Yu Li, Jul 31, 2025
			// We need to always load the storages because we will check if the next
			// coordinate is inside of any
			// of the polygons formed by these storages.
			loadStorage(usedCodes, futures, codePrinted);
		}
	}

	/**
	 * Adds <code>code</code> to the appropriate (code type, odd-even pattern) group
	 * in <code>processedCodes</code>
	 * and increments the size of that group.
	 * 
	 * @param processedCodes       Stores all (code type, odd-even pattern) groups.
	 *                             All codes are of the same code length.
	 * @param processedCodesLength Stores the size of each (code type, odd-even
	 *                             pattern group) in <code>processedCodes</code>.
	 * @param code                 The <code>ClassifiedCodeSequence</code> to add.
	 */
	public static void addProcessedCode(
			Map<CodeType, Map<String, ArrayList<ClassifiedCodeSequence>>> processedCodes,
			Map<CodeType, Map<String, Integer>> processedCodesLength,
			ClassifiedCodeSequence code) {
		processedCodesLength.get(code.codeType).compute(code.oddEvenPattern,
				(k, lengthCount) -> (lengthCount == null) ? 1 : lengthCount + 1);

		if (!processedCodes.get(code.codeType).containsKey(code.oddEvenPattern)) {
			processedCodes.get(code.codeType).put(code.oddEvenPattern, new ArrayList<>());
		}
		processedCodes.get(code.codeType).get(code.oddEvenPattern).add(code);
	}

	/**
	 * <p>
	 * Prints the middle code of the (<code>codeType</code>, code length,
	 * <code>oddEvenPattern</code>) group to the
	 * terminal. Optionally also prints the first and last of each group.
	 * </p>
	 * <p>
	 * <b>NOTE</b>: Assumes all <code>ClassifiedCodeSequence</code> in
	 * <code>processedCodes</code> are of the same
	 * code length.
	 * </p>
	 * 
	 * @param processedCodes       Stores all (code type, odd-even pattern) groups.
	 *                             All codes are of the same code length.
	 * @param processedCodesLength Stores the size of each (code type, odd-even
	 *                             pattern group) in <code>processedCodes</code>.
	 * @param codeType             The type of <code>ClassifiedCodeSequence</code>
	 *                             we are printing.
	 * @param oddEvenPattern       The odd-even pattern of the codes we are
	 *                             printing.
	 * @param codeNum              Within the context of the whole task, we will
	 *                             print the <code>codeNum</code>'th code and
	 *                             onwards.
	 * @return The array of codes we printed, in the order that we printed them.
	 */
	private ArrayList<ClassifiedCodeSequence> printMidFirstLast(
			Map<CodeType, Map<String, ArrayList<ClassifiedCodeSequence>>> processedCodes,
			Map<CodeType, Map<String, Integer>> processedCodesLength,
			CodeType codeType,
			String oddEvenPattern,
			AtomicInteger codeNum) {
		ArrayList<ClassifiedCodeSequence> codesPrinted = new ArrayList<>();

		final ClassifiedCodeSequence codeToPrint = processedCodes.get(codeType)
				.get(oddEvenPattern)
				.get(processedCodesLength.get(codeType).get(oddEvenPattern) / 2);

		if (this.instance.printFirstLast) {
			if (processedCodesLength.get(codeType).get(oddEvenPattern) >= 2) {
				final ClassifiedCodeSequence firstCode = processedCodes
						.get(codeType)
						.get(oddEvenPattern)
						.get(0);
				System.out.println(Utils.standard(firstCode, codeNum.getAndIncrement()));
				codesPrinted.add(firstCode);
			}
		}

		System.out.println(Utils.standard(codeToPrint, codeNum.getAndIncrement()));
		codesPrinted.add(codeToPrint);

		if (this.instance.printFirstLast) {
			if (processedCodesLength.get(codeType).get(oddEvenPattern) >= 3) {
				final ClassifiedCodeSequence lastCode = processedCodes
						.get(codeType)
						.get(oddEvenPattern)
						.get(processedCodesLength
								.get(codeType)
								.get(oddEvenPattern) - 1);
				System.out.println(Utils.standard(lastCode, codeNum.getAndIncrement()));
				codesPrinted.add(lastCode);
			}
		}

		return codesPrinted;
	}
}
