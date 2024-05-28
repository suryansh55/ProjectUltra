package billiards.viewer;

import billiards.codeseq.ClassifiedCodeSequence;
import billiards.codeseq.CodeType;
import billiards.codeseq.Storage;
import billiards.database.Database;
import billiards.geometry.Vector2;
import billiards.wrapper.ConnectionPool;

import javaslang.collection.Array;
import javaslang.control.Either;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;

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
    private final MutableSortedSet<ClassifiedCodeSequence> onScreenCodes;
    private final BoyanMenu boyanMenu;
    private final ConnectionPool pool;
    private final int CSmax;
    private final int OSOmax;
    private final int OSNOmax;
    private final int CSmaxSS;
    private final int OSOmaxSS;
    private final int OSNOmaxSS;
    private final boolean overrideSS;

    // Initialized on call()
    private Array<Callable<Either<String, Storage>>> tasks;

    // Calculates codeSequence set at a specific coordinate 
    private MutableSortedSet<ClassifiedCodeSequence> autoCodesFiltered(final Vector2 coords, final ExecutorService executor) {
        final MutableSortedSet<ClassifiedCodeSequence> codes = new TreeSortedSet<>();
        final MutableSortedSet<ClassifiedCodeSequence> boyanCodes = overrideSS ? boyanMenu.autoVary(coords, this.CSmaxSS, this.OSOmaxSS, this.OSNOmaxSS, executor) : boyanMenu.autoVary(coords, executor);
        // Generate the filtered list
        for (ClassifiedCodeSequence code : boyanCodes) {
            if (code.codeType.equals(CodeType.CS)) {
                if (code.codeLength <= this.CSmax) codes.add(code);
            } else if (code.codeType.equals(CodeType.OSO)) {
                if (code.codeLength <= this.OSOmax) codes.add(code);
            } else if (code.codeType.equals(CodeType.OSNO)) {
                if (code.codeLength <= this.OSNOmax) codes.add(code);
            }
        }
        return codes;
    }

    // Converts coordinates to correct format
    private Array<Vector2> toCoords(final MutableList<Double> points) {
        final MutableList<Vector2> out = new FastList<Vector2>();
        for(int i = 0; i < points.size(); i += 2) {
            final Vector2 coords = Vector2.create(Math.toDegrees(points.get(i)), Math.toDegrees(points.get(i+1)));
            out.add(coords);
        }
        return Array.ofAll(out);
    }


    // These expose partialResults to the FX application thread
    public final ObservableList<Storage> getPartials() {
        return this.partialResults.get();
    }
    public final ReadOnlyObjectProperty<ObservableList<Storage>> getPartialProperty() {
        return this.partialResults.getReadOnlyProperty();
    }
    // Constructor takes a list of points to vary at
    public PolyVaryTask(
        final MutableList<Double> points, final MutableSortedSet<ClassifiedCodeSequence> onScreenCodes, final BoyanMenu boyan, final Array<Integer> max, final ConnectionPool pool, final boolean override) {
        this.coordList = toCoords(points);
        this.onScreenCodes = onScreenCodes;
        this.boyanMenu = boyan;
        this.CSmax = max.get(0);
        this.OSOmax = max.get(1);
        this.OSNOmax = max.get(2);
        this.CSmaxSS = max.get(3);
        this.OSOmaxSS = max.get(4);
        this.OSNOmaxSS = max.get(5);
        this.overrideSS = override;
        this.pool = pool;
    }

    @Override
    protected ObservableList<Storage> call() {
        // We create two executors. storageExecutor handles the more expensive process of calculating code regions,
        // while shotExecutor handles the much faster calculation of finding the codes present at a given point
        final ExecutorService storageExecutor = Executors.newFixedThreadPool(Utils.numThreads); 
        final ExecutorService shotExecutor = Executors.newFixedThreadPool(Utils.numThreads); // Used specifically for finding codes

        final MutableSortedSet<ClassifiedCodeSequence> usedCodes = new TreeSortedSet<ClassifiedCodeSequence>();
        final MutableList<Future<Either<String, Storage>>> futures = new FastList<>();

        AtomicInteger progress = new AtomicInteger(); // Create an integer which supports non-locking concurrent operations
        final int todo = this.coordList.size();
        this.updateProgress(0, todo);

        // The meat and potatoes. Finds codes sequentially, and submits them to the executer as they are found.
        // This is the most efficient way to implement multithreaded polyvary since each code can be calculated as soon as it's found, without interfering with the process of finding more codes.
        this.coordList.forEach(coord -> {
            MutableSortedSet<ClassifiedCodeSequence> localCodes = autoCodesFiltered(coord, shotExecutor);
            // We want to know if we submitted a task that will update the progress for us.
            boolean noCodes = true;
            for(ClassifiedCodeSequence classCodeSeq: localCodes) {
                // Take the first code not already drawn
                if(this.onScreenCodes.contains(classCodeSeq) || usedCodes.contains(classCodeSeq)) continue;
                usedCodes.add(classCodeSeq); 
                noCodes = false;
                // Submit the runnable for this code
                futures.add(storageExecutor.submit(() -> {
                        // Load from database if code already exists. If not, calculate
                        final Optional<Storage> opt = Database.loadStorage(classCodeSeq, this.pool);
                        // Check to see if cancel was called
                        if(this.isCancelled()) {
                            return Either.left("");
                        }
                        this.updateProgress(progress.incrementAndGet(), todo); // updateProgress is thread safe
                        if (opt.isPresent()) {
                            final Storage storage = opt.get();
                            // Platform.runLater removes need for synchronization
                            Platform.runLater(() -> {
                                this.partialResults.get().add(storage);
                            });
                            return Either.right(storage);
                        } else {
                            return Either.left("//empty set " + classCodeSeq);
                        }
                    })
                );
                break;
            }
            if(noCodes) { // Still need to update progress even if nothing found
                this.updateProgress(progress.incrementAndGet(), todo);
            }
        });


        Optional<ExecutionException> except = Optional.empty();

        // If one of the futures throws an exception (like a failed to
        // calculate exception), we need to save it, cancel the rest of
        // the futures, and then throw that exception to bubble up the stack

        // This is where we do checking to see if we were cancelled
        for (final Future<Either<String, Storage>> future : futures) {
            if (this.isCancelled() || except.isPresent()) {
                // If the task was cancelled, or one of the futures threw an
                // exception, we need to cancel the rest of the futures

                // Each task checks cancellation on it's own, so we only have to cancel the rest
                future.cancel(false);
                storageExecutor.shutdown();
                shotExecutor.shutdown();
            } else {
                try {
                    final Either<String, Storage> either = future.get();
                    final String msg;
                    if (either.isLeft()) { // Print things like empty sets 
                        System.out.println(either.isLeft());
                    }
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

        storageExecutor.shutdown();
        shotExecutor.shutdown();

        // If there is an exception that happened, throw it now after shutting down the executor
        if (except.isPresent()) {
            throw new RuntimeException(except.get());
        }

        return this.partialResults.get();
    }
}
