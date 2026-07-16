package billiards.viewer;

import billiards.codeseq.ClassifiedCodeSequence;
import billiards.codeseq.CodeType;
import billiards.codeseq.CodeTypeCollection;
import billiards.codeseq.Storage;
import billiards.database.Database;
import billiards.geometry.Location;
import billiards.geometry.Vector2;
import billiards.utils.PrintMid;
import billiards.wrapper.ConnectionPool;
// Suryansh Ankur, 2026
import billiards.wrapper.Wrapper;

import javaslang.collection.Array;
import javaslang.control.Either;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
// Suryansh Ankur, 2026
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.concurrent.Task;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;

//import jdk.jshell.execution.Util;
import org.apache.commons.lang3.NotImplementedException;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.set.sorted.MutableSortedSet;
import org.eclipse.collections.impl.list.mutable.FastList;
import org.eclipse.collections.impl.set.sorted.mutable.TreeSortedSet;

/*
PolyVaryTask encompasses the process of finding codes and calculating corresponding code regions.
Both these processes are multithreaded. Because of this, the task does not perform ui updates directly. Instead, you should access partialResults using getPartials() or getPartialProperty() and set up a change listener from the javafx application thread if you wish to provide ui updates during execution.

If only the final result is required, you can just call get() on this task after it finishes.
 * */
public final class PolyVaryTask extends Task<ObservableList<Storage>> {
    // Suryansh Ankur, 2026
    // A region calculation for a long code sequence allocates a large amount of
    // multiprecision data in the C++ backend. The storageExecutor runs several
    // concurrently, so on memory-constrained machines several big calculations at
    // once drove peak memory to ~13 GB on 8 GB of RAM and the process was
    // OOM-killed. Codes longer than this threshold are gated below so only a
    // limited number run at once. Tunable: lower the threshold / permit count for
    // less RAM, raise them for more. Static so the limit is global across runs.
    private static final int LARGE_CODE_THRESHOLD = 150;
    // 2 permits: with the unfolding calc path now fully on TBB (one shared,
    // bounded work-stealing arena), two concurrent large calcs no longer
    // oversubscribe the CPU the way nested boost::asio pools did. Overlapping a
    // second calc fills cores that would otherwise idle during the serial
    // points_and_stuff / polygon-reduction phases. Verified peak RSS ~2 GB with
    // 1 permit, so ~2x that still sits well under an 8 GB budget. Lower to 1 if
    // memory pressure returns; raise further only with headroom to spare.
    private static final Semaphore LARGE_CALC_GATE = new Semaphore(2);

	// Expose task property representing partial results
	private ReadOnlyObjectWrapper<ObservableList<Storage>> partialResults = new ReadOnlyObjectWrapper<>(
			this,
			"partialResults",
			FXCollections.observableArrayList(
					new ArrayList<Storage>()));
	private final Array<Vector2> coordList;
	private final MutableSortedSet<ClassifiedCodeSequence> onScreenCodes;
	private volatile PixelReader pixelReader;
	private final ConnectionPool pool;
	private final CodeTypeCollection<Integer> max;
	private final Optional<CodeTypeCollection<Integer>> overrideSum;
	private final ExecutorService storageExecutor;
	private final ExecutorService shotExecutor;
	private final ImageView screenImage;
	private final BoyanMenu boyanMenu;
	private final PixelRadianMap screenMap;
	private final int mode;
	private final int numGroupToPrint;
    private volatile double imgWidth;
    private volatile double imgHeight;

	// Constructor takes a list of points to vary at
	public PolyVaryTask(
			final MutableList<Double> points, final MutableSortedSet<ClassifiedCodeSequence> onScreenCodes,
			final BoyanMenu boyan,
			final CodeTypeCollection<Integer> max,
			final Optional<CodeTypeCollection<Integer>> override,
			final ConnectionPool pool, final ExecutorService eOne,
			final ExecutorService eTwo, final ImageView screen, final PixelRadianMap map, final int mode,
			final int numGroupToPrint) {
		this.coordList = toCoords(points);
		this.onScreenCodes = onScreenCodes;
		this.boyanMenu = boyan;
		this.pool = pool;
		this.max = max;
		this.overrideSum = override;
		this.storageExecutor = eOne;
		this.shotExecutor = eTwo;
		this.screenImage = screen;
		this.screenMap = map;
		this.mode = mode;
		this.numGroupToPrint = numGroupToPrint;
	}

	@Override
	protected ObservableList<Storage> call() {
		// Suryansh Ankur, 2026
        // Clear any stale cancel from a previous run before launching new backend work.
        // The backend cancel flag is process-wide, so it must be reset exactly once here,
        // at the start of the run, rather than inside the concurrent vary calls themselves.
        Wrapper.backend_reset_cancel();

        // Benchmark: wall-clock + peak process RSS for this run (see logBenchmark below).
        final long benchStartNanos = System.nanoTime();

		try {
            final FutureTask<PixelReader> initReader = new FutureTask<>(() -> this.screenImage.getImage().getPixelReader());
            Platform.runLater(initReader);
            this.pixelReader = initReader.get();
            final FutureTask<Image> initImg = new FutureTask<>(() -> this.screenImage.getImage());
            Platform.runLater(initImg);
            final Image img = initImg.get();
            this.imgWidth = img.getWidth();
            this.imgHeight = img.getHeight();
        } catch (final InterruptedException | ExecutionException e) {
            this.pixelReader = null;
        }

		// storageExecutor handles the more expensive process of calculating code
		// regions,
		// while shotExecutor handles the much faster calculation of finding the codes
		// present at a given point

		final MutableSortedSet<ClassifiedCodeSequence> usedCodes = new TreeSortedSet<ClassifiedCodeSequence>();
		final MutableList<Future<Either<String, Storage>>> futures = new FastList<>();
		ArrayList<Storage> storages = new ArrayList<>();

		AtomicInteger progress = new AtomicInteger(); // Create an integer which supports non-locking concurrent
														// operations
		final int todo = this.coordList.size();
		this.updateProgress(0, todo);

		AtomicInteger codeNum = new AtomicInteger(1);
		int emptyMax = 8; // Max number of empty pixels. Hardcoded for now//george jan3,2025 you can
							// change the 8 to whatever
		int empty = 0; // Number of empty pixels
		// The meat and potatoes. Finds codes sequentially, and submits them to the
		// executer as they are found.
		// This is the most efficient way to implement multithreaded polyvary since each
		// code can be calculated as soon as it's found, without interfering with the
		// process of finding more codes.
		// Zhao Yu Li, Aug 6, 2025.
		// To save time, we check if the current coordinate is inside any of the
		// polygons formed the codes found from
		// the previous coordinate. If yes, then we don't need to run Vary for this
		// coordinate because a code from the
		// last coordinate fills the square.
		COORD_LOOP:
		for (Vector2 coord : this.coordList) {
			long tPixel = 0;
            long tFind = 0;
            long tStorage = 0;
            long iterStart = System.nanoTime();
			this.updateProgress(progress.incrementAndGet(), todo);

			for (Storage storage : storages) {
				Storage.Stable stable = (Storage.Stable) storage;
				final Location location = stable.polygon.location(coord.x, coord.y);

				if (location == Location.INSIDE) {
					// System.out.println("Skipped because a code sequence from previous coordinate
					// covers this coordinate.");
					// System.out.println(Utils.standard(storage.classCodeSeq, 1));

					continue COORD_LOOP;
				}
			}

			storages.clear();

			MutableSortedSet<ClassifiedCodeSequence> localCodes;
			// The BoyanCodes method vary3() called by autoVary() can throw exceptions. We
			// need to catch them
			// By taking a second to check the pixel color, we can potentially avoid all
			// other work for this coord.
			// Nick Shan, July, 2026
			// Use cached PixelReader directly (non-blocking, avoids JavaFX thread bottleneck)
            long t0 = System.nanoTime();
            int color = 0;
            if (this.pixelReader != null) {
                final int midX = (int) this.screenMap.pixelX(coord.x);
                final int midY = (int) this.screenMap.pixelY(coord.y);
                if (midX >= 0 && midY >= 0 && midX < this.imgWidth && midY < this.imgHeight) {
                    color = this.pixelReader.getArgb(midX, midY);
                }
            }
            tPixel = System.nanoTime() - t0;
            this.updateProgress(progress.incrementAndGet(), todo);
            if(color != 0) {
                //System.out.printf("  [coord %d] pixelColor=%s  (filled)%n", coordIdx, Profiler.fmt(tPixel));
                continue;
            }
            t0 = System.nanoTime();
            try {
                localCodes = autoCodesFiltered(coord, shotExecutor);
            } catch(RuntimeException e) {
                if(this.isCancelled() || Thread.interrupted()) {
                    break;
                } else {
                    //System.err.println("Terminating because of uncaught exception when finding codeSet");
					shotExecutor.shutdownNow();
                    throw e;
                }
            }
			tFind = System.nanoTime() - t0;
            // We want to know if we submitted a task that will update the progress for us.
            if(localCodes.isEmpty()) {
                ++empty;
                //if(empty >= emptyMax) {
                //    System.out.println("Finish Vary due to too many empty pixels");
                //    break;
                //}
                if(empty >= emptyMax) {
                    break;
                }
                this.updateProgress(progress.incrementAndGet(), todo);
                final long tOther = (System.nanoTime() - iterStart) - tPixel - tFind;
                //System.out.printf("  [coord %d] pixelColor=%s  findCodes=%s  other=%s  total=%s%n",
                //    coordIdx, Profiler.fmt(tPixel), Profiler.fmt(tFind), Profiler.fmt(tOther), Profiler.fmt(System.nanoTime() - iterStart));
                continue;
            }
            empty = 0;

            // Zhao Yu Li, Jul 03, 2025.
            // Do not invalidate all results just because only one code was used previously
            // Check if any of the codes found were previously used
//            boolean used = false;
//            for(ClassifiedCodeSequence code: usedCodes) {
//                used = used || localCodes.contains(code);
//            }
//            for(ClassifiedCodeSequence code: this.onScreenCodes) {
//                used = used || localCodes.contains(code);
//            }
//            if(used) {
//                this.updateProgress(progress.incrementAndGet(), todo);
//                continue;
//            }
            t0 = System.nanoTime();
			if (mode == 0) {
				boolean noCodes = true;

				// Take the first code not already drawn, and submit it to the storageExecutor
				// for processing
				for (ClassifiedCodeSequence classCodeSeq : localCodes) {
					if (this.onScreenCodes.contains(classCodeSeq) || usedCodes.contains(classCodeSeq))
						continue;

					noCodes = loadStorageFromDB(classCodeSeq, usedCodes, futures, progress, todo);
					System.out.println(Utils.standard(classCodeSeq, codeNum.getAndIncrement()));
					break;
				}

				if (noCodes) { // Still need to update progress even if nothing found
					this.updateProgress(progress.incrementAndGet(), todo);
				}
			} else if (mode == 1) {
				ArrayList<ClassifiedCodeSequence> printedCodes = PrintMid.printMid(localCodes, numGroupToPrint);
				loadPrintedCodesStorage(usedCodes, futures, progress, todo, printedCodes);
			} else if (mode == 2) {
				ArrayList<ClassifiedCodeSequence> printedCodes = PrintMid.printFirstMidLast(localCodes, numGroupToPrint,
						true);
				loadPrintedCodesStorage(usedCodes, futures, progress, todo, printedCodes);
			} else {
				throw new NotImplementedException("Invalid mode value for PolyVaryTask");
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

			futures.clear();
		}

        // Suryansh Ankur, 2026
        //logBenchmark(benchStartNanos);
		return this.partialResults.get();
	}

    // Suryansh Ankur, 2026
    // Logs wall-clock time and peak process RSS (JVM + native) for the run, for
    // before/after comparison when optimizing the vary threading/memory model.
    private void logBenchmark(final long startNanos) {
        final double seconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;
        final long peakBytes = Wrapper.backend_peak_rss_bytes();
        final String peak = peakBytes < 0
                ? "unavailable"
                : String.format("%.1f MB", peakBytes / (1024.0 * 1024.0));
        System.out.printf("[Benchmark] LiLuMaxVary: %.2f s, peak RSS %s%n", seconds, peak);
    }

	// Cancel or detect execution errors; This is where we do checking to see if we
	// were cancelled
	private Either<String, Storage> checkStatus(final Future<Either<String, Storage>> future) {
		if (this.isCancelled()) {
			// If the task was cancelled, or one of the futures threw an
			// exception, we need to cancel the rest of the futures
			// System.out.println("//Cancelling submitted future");
			future.cancel(true);
			return null;
		} else {
			try {
				return future.get();
			} catch (final ExecutionException e) {
				// One of the futures threw an exception during its calculation,
				// so we need to cancel the rest of the futures
				throw new RuntimeException(e);
			} catch (final InterruptedException e) {
				if (!this.isCancelled()) {
					throw new RuntimeException(e);
				}
			}
		}

		return null;
	}

	// Calculates codeSequence set at a specific coordinate
	private MutableSortedSet<ClassifiedCodeSequence> autoCodesFiltered(final Vector2 coords,
			final ExecutorService executor) {
		// autoVary requires coordinates to be in degree format
		final Vector2 degCoords = Vector2.create(Math.toDegrees(coords.x), Math.toDegrees(coords.y));
		final MutableSortedSet<ClassifiedCodeSequence> codes = this.overrideSum.isPresent()
				? boyanMenu.autoVary(degCoords, this.overrideSum.get(), executor)
				: boyanMenu.autoVary(degCoords, executor);
		// Generate the filtered list
		codes.removeIf(code -> {
			int max = this.max.get(code.codeType);
			return max != 0 && code.codeLength > max;
		});
		return codes;
	}

	// Converts list of points into array of coordinate pairs
	public static Array<Vector2> toCoords(final MutableList<Double> points) {
		final MutableList<Vector2> out = new FastList<Vector2>();
		for (int i = 0; i < points.size(); i += 2) {
			final Vector2 coords = Vector2.create(points.get(i), points.get(i + 1));
			out.add(coords);
		}
		Collections.shuffle(out); // Randomize as an optimization
		return Array.ofAll(out);
	}


    // Find the storage associated to a codeSequence if it exists. Return the error if not
    private Either<String, Storage> loadStorage(final ClassifiedCodeSequence classCodeSeq) {
        // Check to see if cancel was called
        if(this.isCancelled() || Thread.interrupted()) {
            // Note that this method is intended to be submitted to an executor, hence this interrupts the thread inside the threadpool
            Thread.currentThread().interrupt();
            System.out.println("//Cancel detected before loadStorage");
            return Either.left("");
        }
        // Load from database if code already exists. If not, calculate
        final Optional<Storage> opt = Database.loadStorage(classCodeSeq, this.pool);
        // Check to see if cancel was called
        if(this.isCancelled() || Thread.interrupted()) {
            Thread.currentThread().interrupt();
            System.out.println("//Cancel detected after loadStorage");
            return Either.left("");
        }
        if (opt.isPresent()) {
            final Storage storage = opt.get();
            // Update partialResults on the application thread in order to enforce thread safety
            Platform.runLater(() -> this.partialResults.get().add(storage));
            return Either.right(storage);
        } else {
            return Either.left("//empty set " + classCodeSeq);
        }
    }

	// These expose partialResults to the FX application thread
	public final ObservableList<Storage> getPartials() {
		return this.partialResults.get();
	}

	public final ReadOnlyObjectProperty<ObservableList<Storage>> getPartialProperty() {
		return this.partialResults.getReadOnlyProperty();
	}

	private boolean loadStorageFromDB(ClassifiedCodeSequence classCodeSeq,
			MutableSortedSet<ClassifiedCodeSequence> usedCodes,
			MutableList<Future<Either<String, Storage>>> futures, AtomicInteger progress,
			int todo) {
		usedCodes.add(classCodeSeq);
		// Submit the runnable for this code
		futures.add(storageExecutor.submit(new PriorityCallable<Either<String, Storage>>() {
			@Override
			public Either<String, Storage> call() {
				Either<String, Storage> result = loadStorage(classCodeSeq);
				if (!PolyVaryTask.this.isCancelled())
					PolyVaryTask.this.updateProgress(progress.incrementAndGet(), todo); // updateProgress is thread safe
				return result;
			}

			@Override
			public int getPriority() {
				return classCodeSeq.length();
			}
		}));

		return false;
	}

	private void loadPrintedCodesStorage(MutableSortedSet<ClassifiedCodeSequence> usedCodes,
			MutableList<Future<Either<String, Storage>>> futures, AtomicInteger progress, int todo,
			ArrayList<ClassifiedCodeSequence> printedCodes) {
		boolean atLeastOneCode = false;

		for (ClassifiedCodeSequence classCodeSeq : printedCodes) {
			boolean skipped = !loadStorageFromDB(classCodeSeq, usedCodes, futures, progress, todo);
			atLeastOneCode = atLeastOneCode || skipped;
		}

		if (!atLeastOneCode) { // Still need to update progress even if nothing found
			this.updateProgress(progress.incrementAndGet(), todo);
		}
	}
}
