package billiards.viewer;

import billiards.codeseq.ClassifiedCodeSequence;
import billiards.codeseq.CodeType;
import billiards.codeseq.Storage;
import billiards.database.Database;
import billiards.geometry.Vector2;
import billiards.wrapper.ConnectionPool;

import javaslang.collection.Array;
import javaslang.control.Either;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.concurrent.Task;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.set.sorted.MutableSortedSet;
import org.eclipse.collections.impl.list.mutable.FastList;
import org.eclipse.collections.impl.set.sorted.mutable.TreeSortedSet;
/*
PolyVaryTask encompasses the process of finding codes and calculating corresponding code regions.
Both these processes are multithreaded. Because of this, the task does not perform ui updates directly. Instead, you should access partialResults using getPartials() or getPartialProperty() and set up a change listener from the javafx application thread if you wish to provide ui updates during execution.

If only the final result is required, you can just call get() on this task after it finishes.
 * */
public final class VaryLTask extends Task<ObservableList<Storage>> {
    // Expose task property representing partial results
    private ReadOnlyObjectWrapper<ObservableList<Storage>> partialResults =
            new ReadOnlyObjectWrapper<>(
                    this, 
                    "partialResults",
                    FXCollections.observableArrayList(
                            new ArrayList<Storage>()
                    )
            );
    private final Array<Vector2> coordList;
    private final MutableSortedSet<String> coverCodes = new TreeSortedSet<>();
    private final BoyanMenu boyanMenu;
    private final ConnectionPool pool;
    private final int CSmax;
    private final int OSOmax;
    private final int OSNOmax;
    private final int CSmaxSS;
    private final int OSOmaxSS;
    private final int OSNOmaxSS;
    private final boolean overrideSS;
    private final boolean draw;
    private final int maxPrint;
    private final ExecutorService storageExecutor;
    private final ExecutorService shotExecutor;
    private final boolean printMid;
    //private final ImageView screenImage;
    //private final PixelRadianMap screenMap; 

    // Constructor takes a list of points to vary at
    public VaryLTask(
        final Array<Vector2> points, List<String> coverCodes, final BoyanMenu boyan, 
        final Array<Integer> max, final ConnectionPool pool, final boolean override, final boolean draw,
        final Integer maxPrint, final ExecutorService eOne, final ExecutorService eTwo, final boolean printMid) {
        this.coordList = points; // Points are in degrees
        this.coverCodes.addAll(coverCodes);
        this.boyanMenu = boyan;
        this.CSmax = max.get(0);
        this.OSOmax = max.get(1);
        this.OSNOmax = max.get(2);
        this.CSmaxSS = max.get(3);
        this.OSOmaxSS = max.get(4);
        this.OSNOmaxSS = max.get(5);
        this.pool = pool;
        this.overrideSS = override;
        this.draw = draw;
        this.maxPrint = maxPrint;
        this.storageExecutor = eOne;
        this.shotExecutor = eTwo;
        this.printMid = printMid;
    }

    @Override
    protected ObservableList<Storage> call() {
        // storageExecutor handles the more expensive process of calculating code regions,
        // while shotExecutor handles the much faster calculation of finding the codes present at a given point

        final MutableSortedSet<ClassifiedCodeSequence> usedCodes = new TreeSortedSet<ClassifiedCodeSequence>();
        final MutableList<Future<Either<String, Storage>>> futures = new FastList<>();

        AtomicInteger progress = new AtomicInteger(); // Create an integer which supports non-locking concurrent operations
        final int todo = this.coordList.size() * maxPrint;
        this.updateProgress(0, todo);

        // The meat and potatoes. Finds codes sequentially, and submits them to the executer as they are found.
        // This is the most efficient way to implement varyL since each code can be calculated as soon as it's found, without interfering with the process of finding more codes.
        int count = 1;
        int totalCodes = 0;

        for(Vector2 coord: this.coordList) {
            MutableSortedSet<ClassifiedCodeSequence> localCodes;
			System.out.println();
			System.out.println("//------------- working on point " + count++ + " -------------"); // george added // sept 27,2017
            // The BoyanCodes method vary3() called by varyTrianglesL() can throw exceptions. We need to catch them
            try {
                localCodes = autoCodesFiltered(coord, shotExecutor);
            } catch(RuntimeException e) {
                if(this.isCancelled() || Thread.interrupted()) {
                    break;
                } else {
                    System.err.println("Terminating because of uncaught exception when finding codeSet");
                    throw e;
                }
            }
            // We draw the first i codes we found
			int i = this.maxPrint == 0 ? localCodes.size() : this.maxPrint;
            int codeNum = 1;
            // Take the first code not already drawn, and submit it to the storageExecutor for processing 
            totalCodes += localCodes.size();
            if (!printMid) {
                for(ClassifiedCodeSequence classCodeSeq: localCodes) {
                    if(i <= 0) break;
                    --i;
                    System.out.println(Utils.standard(classCodeSeq, codeNum++));
                    if(usedCodes.contains(classCodeSeq) || !this.draw) { // Update in the case of not drawing this code
                        this.updateProgress(progress.incrementAndGet(), todo);
                        continue;
                    }
                    usedCodes.add(classCodeSeq);
                    // Submit the custom PriorityCallable for this code (Node that PriorityCallable is a custom interface)
                    futures.add(storageExecutor.submit(new PriorityCallable<Either<String, Storage>>() {
                                @Override
                                public Either<String, Storage> call() {
                                    Either<String, Storage> result = loadStorage(classCodeSeq);
                                    VaryLTask.this.updateProgress(progress.incrementAndGet(), todo);
                                    return result;
                                }

                                @Override
                                public int getPriority() {
                                    return classCodeSeq.length();
                                }
                            })
                    );
                }
            } else {
                // Zhao Yu Li, May 06, 2025.
                // Prints only the middle code of each (code type, code length, and odd-even pattern) group
                final CodeType[] codeTypes = {CodeType.CS, CodeType.OSO, CodeType.OSNO, CodeType.CNS, CodeType.ONS};

                long currentLength = -1;
                Map<CodeType, Map<String, ArrayList<ClassifiedCodeSequence>>> processedCodes = new HashMap<>();
                Map<CodeType, Map<String, Integer>> processedCodesLength = new HashMap<>();

                for (CodeType codeType : codeTypes) {
                    processedCodes.put(codeType, new HashMap<>());
                    processedCodesLength.put(codeType, new HashMap<>());
                }

                for(ClassifiedCodeSequence code: localCodes) {
                    if (i <= 0) break;

                    if (currentLength == -1) {
                        currentLength = code.codeLength;
                    }

                    if (code.codeLength == currentLength) {
                        processedCodesLength.get(code.codeType).compute(code.oddEvenPattern,
                                (k, lengthCount) -> (lengthCount == null) ? 1 : lengthCount + 1);

                        if (!processedCodes.get(code.codeType).containsKey(code.oddEvenPattern)) {
                            processedCodes.get(code.codeType).put(code.oddEvenPattern, new ArrayList<>());
                        }
                        processedCodes.get(code.codeType).get(code.oddEvenPattern).add(code);
                    } else {
                        for (CodeType codeType : codeTypes) {
                            if (i <= 0) break;

                            for (String oddEvenPattern : processedCodesLength.get(codeType).keySet()) {
                                if (i <= 0) break;

                                // Only print the middle one
                                final ClassifiedCodeSequence classCodeSeq = processedCodes.get(codeType)
                                        .get(oddEvenPattern)
                                        .get(processedCodesLength.get(codeType).get(oddEvenPattern) / 2);

                                --i;
                                System.out.println(Utils.standard(classCodeSeq, codeNum++));

                                if(usedCodes.contains(classCodeSeq) || !this.draw) { // Update in the case of not drawing this code
                                    this.updateProgress(progress.incrementAndGet(), todo);
                                    continue;
                                }
                                usedCodes.add(classCodeSeq);
                                // Submit the custom PriorityCallable for this code (Node that PriorityCallable is a custom interface)
                                futures.add(storageExecutor.submit(new PriorityCallable<Either<String, Storage>>() {
                                            @Override
                                            public Either<String, Storage> call() {
                                                Either<String, Storage> result = loadStorage(classCodeSeq);
                                                VaryLTask.this.updateProgress(progress.incrementAndGet(), todo);
                                                return result;
                                            }

                                            @Override
                                            public int getPriority() {
                                                return classCodeSeq.length();
                                            }
                                        })
                                );

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
                            ClassifiedCodeSequence codeToPrint = processedCodes.get(codeType)
                                    .get(oddEvenPattern)
                                    .get(processedCodesLength.get(codeType).get(oddEvenPattern) / 2);
                            --i;
                            System.out.println(Utils.standard(codeToPrint, codeNum++));
                        }
                    }
                }
            }

            for(int p = 0; p < i; ++p) { // Update in the case of < i codes
                this.updateProgress(progress.incrementAndGet(), todo);
            }
        }

		System.out.println("//~~~~~~~~~~~~~~~~~~~~~~~~~~~ " + totalCodes
		+ " codes found total ~~~~~~~~~~~~~~~~~~~~~~~~~~~");//added // george sept27,2017
        // The shot executor is no longer needed
        shotExecutor.shutdown();

        Optional<ExecutionException> except = Optional.empty();

        // If one of the futures throws an exception (like a failed to
        // calculate exception), we need to save it, cancel the rest of
        // the futures, and then throw that exception to bubble up the stack
        for (final Future<Either<String, Storage>> future : futures) {
            checkStatus(future, except);
        }

        if (except.isPresent()) {
            throw new RuntimeException(except.get());
        }

        return this.partialResults.get();
    }

    // Cancel or detect execution errors; This is where we do checking to see if we were cancelled
    private void checkStatus(final Future<Either<String, Storage>> future, Optional<ExecutionException> except) {
        if (this.isCancelled() || except.isPresent()) {
            // If the task was cancelled, or one of the futures threw an
            // exception, we need to cancel the rest of the futures
            //System.out.println("//Cancelling submitted future");
            future.cancel(true);
        } else {
            try {
                future.get();
                /*
                final Either<String, Storage> either = future.get();
                if (either.isLeft()) { // Print things like empty sets 
                    if(!either.left().get().equals("")) System.out.println(either.left().get());
                }
                */
            } catch (final ExecutionException e) {
                // One of the futures threw an exception during its calculation,
                // so we need to cancel the rest of the futures
                except = Optional.of(e);
            } catch (final InterruptedException e) {
                if (!this.isCancelled()) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    // Calculates codeSequence set at a specific coordinate 
    private MutableSortedSet<ClassifiedCodeSequence> autoCodesFiltered(final Vector2 coords, final ExecutorService executor) {
        // autoVary requires coordinates to be in degree format
        final MutableSortedSet<ClassifiedCodeSequence> codes = new TreeSortedSet<>();
        final MutableSortedSet<ClassifiedCodeSequence> boyanCodes = overrideSS ? boyanMenu.varyTrianglesL(coords, this.CSmaxSS, this.OSOmaxSS, this.OSNOmaxSS, executor) : boyanMenu.varyTrianglesL(coords, executor);
        // Generate the filtered list
        for (ClassifiedCodeSequence code : boyanCodes) {
            if (code.codeType.equals(CodeType.OSO) && code.codeLength > OSOmax) {
                continue;
            } else if (code.codeType.equals(CodeType.OSNO) && code.codeLength > OSNOmax) {
                continue;
            } else if (code.codeType.equals(CodeType.CS) && code.codeLength > CSmax) {
                continue;
            }
            if(!this.coverCodes.contains(code.codeSequence.toString())) {
                codes.add(code);
            }
        }
        return codes;
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

    
}
