package billiards.vary;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import billiards.codeseq.*;
import billiards.database.Database;
import billiards.geometry.Location;
import billiards.geometry.Vector2;
import billiards.utils.PrintMid;
import billiards.viewer.PriorityCallable;
import billiards.viewer.Utils;
import billiards.viewer.VaryLTask;
import javaslang.Tuple;
import javaslang.Tuple2;
import javaslang.collection.Array;
import javaslang.control.Either;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.set.sorted.MutableSortedSet;
import org.eclipse.collections.impl.list.mutable.FastList;

import org.eclipse.collections.impl.set.sorted.mutable.TreeSortedSet;

import static billiards.vary.VaryLAction.addProcessedCode;

/**
 * AutoPolyVary
 */
public class AutoPolyVaryAction {
	private final List<Vector2> coords;
	private final int min;
	private final Either<Integer, CodeTypeCollection<Integer>> max;
	private final int shots;
	private final CodeTypeSet types;
	private final CodeTypeCollection<Integer> maxCodeLength;
	private final AutoPolyVaryInstance instance;

	public AutoPolyVaryAction(List<Vector2> coords, int min, Either<Integer, CodeTypeCollection<Integer>> max,
							  CodeTypeCollection<Integer> maxCodeLength, CodeTypeSet types, int shots,
							  AutoPolyVaryInstance instance) {
        this.coords = coords;
		this.min = min;
        this.max = max;
		this.types = types;
		this.shots = shots;
		this.maxCodeLength = maxCodeLength;
        this.instance = instance;
    }

	public List<Storage> run() {
		int empty = 0;
		int emptyMax = 8;

		final MutableSortedSet<ClassifiedCodeSequence> usedCodes = new TreeSortedSet<>();
		final MutableList<Future<Either<String, Storage>>> futures = new FastList<>();
		ArrayList<Storage> storages = new ArrayList<>();

		COORD_LOOP:
		for (Vector2 coord : this.coords) {
			for (Storage storage : storages) {
				Storage.Stable stable = (Storage.Stable) storage;
				final Location location = stable.polygon.location(coord.x, coord.y);

				if (location == Location.INSIDE) {
					continue COORD_LOOP;
				}
			}
			storages.clear();

			MutableSortedSet<ClassifiedCodeSequence> localCodes;

			try {
				localCodes = autoCodesFiltered(coord);
			} catch (RuntimeException e) {
				if (Thread.interrupted()) {
					break;
				} else {
					System.err.println("Terminating because of uncaught exception when finding codeSet");
					throw e;
				}
			}

			if (localCodes.isEmpty()) {
				++empty;
				if (empty >= emptyMax) {
					System.out.println("Finish Vary due to too many empty pixels");
					break;
				}
			}
			ArrayList<ClassifiedCodeSequence> printedCodes;
			int i = this.instance.maxNumPrint == 0 ? localCodes.size() : this.instance.maxNumPrint;
			switch(this.instance.printMode){
				case REGULAR:
					AtomicInteger codeNum = new AtomicInteger(1);
					for (ClassifiedCodeSequence classCodeSeq : localCodes) {
						if (usedCodes.contains(classCodeSeq)) continue;
						loadStorageFromDB(classCodeSeq, usedCodes, futures);
						System.out.println(Utils.standard(classCodeSeq, codeNum.getAndIncrement()));
						break;
					}
					break;
				case MIDDLE:
					printedCodes = printMid(localCodes, i);
					loadPrintedCodesStorage(usedCodes, futures, printedCodes);
					break;
				case FIRSTMIDLAST:
					printedCodes = printMidFirstLast(localCodes, i);
					loadPrintedCodesStorage(usedCodes, futures, printedCodes);
					break;
			}


			for (final Future<Either<String, Storage>> future : futures) {
				Either<String, Storage> either = checkStatus(future);

				if (either != null) {
					if (either.isLeft()) { // Print things like empty sets
						if (!either.left().get().isEmpty())
							System.out.println(either.left().get());
					} else {
						storages.add(either.right().get());
					}
				}
			}
		}

		return storages;
	}
	private ArrayList<ClassifiedCodeSequence> printMid(Collection<ClassifiedCodeSequence> codes, final int numToPrint) {
		return printCode(codes, numToPrint, false, false);
	}

	private ArrayList<ClassifiedCodeSequence> printMidFirstLast(Collection<ClassifiedCodeSequence> codes, final int numToPrint) {
		return printCode(codes, numToPrint, true, true);
	}
	private ArrayList<ClassifiedCodeSequence> printCode(Collection<ClassifiedCodeSequence> codes, final int numToPrint,
														// These two flags are a disgusting code smell, but I'm not sure
														// how to otherwise approach the responsibilities of this function
														boolean printFirst, boolean printLast ) {
		final CodeType[] codeTypes = {CodeType.CS, CodeType.OSO, CodeType.OSNO, CodeType.CNS, CodeType.ONS};

		long currentLength = -1;
		Map<CodeType, Map<String, ArrayList<ClassifiedCodeSequence>>> processedCodes = new HashMap<>();
		Map<CodeType, Map<String, Integer>> processedCodesLength = new HashMap<>();

		for (CodeType codeType : codeTypes) {
			processedCodes.put(codeType, new HashMap<>());
			processedCodesLength.put(codeType, new HashMap<>());
		}
		int i = numToPrint;
		int codeNum = 1;
		ArrayList<ClassifiedCodeSequence> codesPrinted = new ArrayList<>();
		for(ClassifiedCodeSequence code: codes) {
			if (i <= 0) break;

			if (currentLength == -1) {
				currentLength = code.codeLength;
			}

			if (code.codeLength == currentLength) addProcessedCode(processedCodes, processedCodesLength, code);
			else {
				for (CodeType codeType : codeTypes) {
					if (i <= 0) break;

					for (String oddEvenPattern : processedCodesLength.get(codeType).keySet()) {
						if (i <= 0) break;

						if(printFirst) codeNum = printFirstOfGroup(processedCodes, processedCodesLength, codeNum, codesPrinted, codeType, oddEvenPattern);

						// Only print the middle one
						final ClassifiedCodeSequence codeToPrint = processedCodes.get(codeType)
								.get(oddEvenPattern)
								.get(processedCodesLength.get(codeType).get(oddEvenPattern) / 2);

						--i;
						System.out.println(Utils.standard(codeToPrint, codeNum++));
						codesPrinted.add(codeToPrint);

						if(printLast) codeNum = printLastOfGroup(processedCodes, processedCodesLength, codeNum, codesPrinted, codeType, oddEvenPattern);
					}

					// Clear and re-initialize for the next iteration
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
			if (i <= 0) break;

			// We reached the end of the iteration, add the middle of last (code type, code length, odd-even) group
			for (String oddEvenPattern : processedCodesLength.get(codeType).keySet()) {
				if (i <= 0) break;

				if (!processedCodes.get(codeType).get(oddEvenPattern).isEmpty()) {
					codeNum = printFirstOfGroup(processedCodes, processedCodesLength, codeNum, codesPrinted, codeType, oddEvenPattern);

					ClassifiedCodeSequence codeToPrint = processedCodes.get(codeType)
							.get(oddEvenPattern)
							.get(processedCodesLength.get(codeType).get(oddEvenPattern) / 2);
					--i;
					System.out.println(Utils.standard(codeToPrint, codeNum++));
					codesPrinted.add(codeToPrint);

					codeNum = printLastOfGroup(processedCodes, processedCodesLength, codeNum, codesPrinted, codeType, oddEvenPattern);
				}
			}
		}

		return codesPrinted;
	}

	private static int printFirstOfGroup(Map<CodeType, Map<String, ArrayList<ClassifiedCodeSequence>>> processedCodes, Map<CodeType, Map<String, Integer>> processedCodesLength, int codeNum, ArrayList<ClassifiedCodeSequence> codesPrinted, CodeType codeType, String oddEvenPattern) {
		if (processedCodesLength.get(codeType).get(oddEvenPattern) >= 2) {
			final ClassifiedCodeSequence firstCode = processedCodes
					.get(codeType)
					.get(oddEvenPattern)
					.get(0);
			System.out.println(Utils.standard(firstCode, codeNum++));
			codesPrinted.add(firstCode);
		}
		return codeNum;
	}

	private static int printLastOfGroup(Map<CodeType, Map<String, ArrayList<ClassifiedCodeSequence>>> processedCodes, Map<CodeType, Map<String, Integer>> processedCodesLength, int codeNum, ArrayList<ClassifiedCodeSequence> codesPrinted, CodeType codeType, String oddEvenPattern) {
		if (processedCodesLength.get(codeType).get(oddEvenPattern) >= 3) {
			final ClassifiedCodeSequence lastCode = processedCodes
					.get(codeType)
					.get(oddEvenPattern)
					.get(processedCodesLength
							.get(codeType)
							.get(oddEvenPattern) - 1);
			System.out.println(Utils.standard(lastCode, codeNum++));
			codesPrinted.add(lastCode);
		}

		return codeNum;
	}

	private Either<String, Storage> checkStatus(final Future<Either<String, Storage>> future){
		try {
			return future.get();
		} catch (final ExecutionException e) {
			// One of the futures threw an exception during its calculation,
			// so we need to cancel the rest of the futures
			throw new RuntimeException(e);
		} catch (final InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	// Calculates codeSequence set at a specific coordinate
	private MutableSortedSet<ClassifiedCodeSequence> autoCodesFiltered(final Vector2 coords ) {
		// autoVary requires coordinates to be in degree format
		final Vector2 degCoords = Vector2.create(Math.toDegrees(coords.x), Math.toDegrees(coords.y));

        final MutableSortedSet<ClassifiedCodeSequence> codes = this.max.isRight()
				? AutoVary.autoVary(degCoords, this.min, this.max.get(), this.shots, this.types, this.instance)
                : AutoVary.autoVary(degCoords, this.min, this.max.getLeft(), this.shots, this.types, this.instance);
		// Generate the filtered list
		codes.removeIf(code -> {
			int max = this.maxCodeLength.get(code.codeType);
			return max != 0 && code.codeLength > max;
		});
		return codes;
	}

	private boolean loadStorageFromDB(ClassifiedCodeSequence classCodeSeq,
	                                  MutableSortedSet<ClassifiedCodeSequence> usedCodes,
	                                  MutableList<Future<Either<String, Storage>>> futures) {
		usedCodes.add(classCodeSeq);
		// Submit the runnable for this code
		futures.add(this.instance.storageExecutor.submit(new PriorityCallable<Either<String, Storage>>() {
			@Override
			public Either<String, Storage> call() {
                return loadStorage(classCodeSeq);
			}

			@Override
			public int getPriority() {
				return classCodeSeq.length();
			}
		}));

		return false;
	}

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
			// Update partialResults on the application thread in order to enforce thread
			// safety
			return Either.right(storage);
		} else {
			return Either.left("//empty set " + classCodeSeq);
		}
	}

	private void loadPrintedCodesStorage(MutableSortedSet<ClassifiedCodeSequence> usedCodes,
										 MutableList<Future<Either<String, Storage>>> futures,
										 ArrayList<ClassifiedCodeSequence> printedCodes) {
		for (ClassifiedCodeSequence classCodeSeq : printedCodes) {
			boolean skipped = !loadStorageFromDB(classCodeSeq, usedCodes, futures);
		}
	}
}
