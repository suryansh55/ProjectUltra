package billiards.viewer;

import billiards.codeseq.ClassifiedCodeSequence;
import billiards.codeseq.CodeType;
import billiards.codeseq.Storage;
import billiards.database.Database;
import billiards.wrapper.ConnectionPool;

import javaslang.collection.Array;
import javaslang.control.Either;

import java.util.ArrayList;
import java.util.Optional;
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
                            new ArrayList<>()
                    )
            );


    private final MutableList<Double> points;
    private final MutableSortedSet<ClassifiedCodeSequence> onScreenCodes;
    private final int CSmax;
    private final int OSOmax;
    private final int OSNOmax;
    private final int CSmaxSS;
    private final int OSOmaxSS;
    private final int OSNOmaxSS;
    private final boolean overrideSS;
    private final boolean print;
    private final boolean detailed;

    // Initialized on call()
    private Array<Callable<Either<String, Storage>>> tasks;

    // Calculates codeSequence at a specific coordinate 
    private MutableSortedSet<ClassifiedCodeSequence> autoCodesFiltered(final double rx, final double ry, final ExecutorService executor) {
        final MutableSortedSet<ClassifiedCodeSequence> codes = new TreeSortedSet<>();
        final Vector2 coords = Vector2.create(Math.toDegrees(rx), Math.toDegrees(ry));

        final MutableSortedSet<ClassifiedCodeSequence> boyanCodes = overrideSS ? boyanMenu.autoVary(coords, CSmaxSS, OSOmaxSS, OSNOmaxSS, executor) : boyanMenu.autoVary(coords, executor);
        // Generate the filtered list
        for (ClassifiedCodeSequence code : boyanCodes) {
            if (code.codeType.equals(CodeType.CS)) {
                if (code.codeLength <= this.CSmax) {
                    codes.add(code);
                }
            } else if (code.codeType.equals(CodeType.OSO)) {
                if (code.codeLength <= this.OSOmax) {
                    codes.add(code);
                }
            } else if (code.codeType.equals(CodeType.OSNO)) {
                if (code.codeLength <= this.OSNOmax) {
                    codes.add(code);
                }
            }
        }
        return codes;
    }

    private Array<ClassifiedCodeSequence> findCodes(final ExecutorService executor) {
        final int todo = points.size() / 2;
        final MutableList<ClassifiedCodeSequence> codes = new FastList();
        updateProgress(0, todo);
        for (int i = 0; i < todo; i++) {
            if (isCancelled()) {
                return codes;
            }
            final int place = i * 2;
            MutableSortedSet<ClassifiedCodeSequence> localCodes = autoCodesFiltered(points.get(place), points.get(place + 1), maxList, overrideSS, executor);
            if(!localCodes.isEmpty()) {
                // Add the first code that is not already onscreen
                for(ClassifiedCodeSequence code: localCodes) {
                    if(!(this.onScreenCodes.contains(code))) {
                        codes.add(code);
                        break;
                    }
                }
            }
            updateProgress(i + 1, todo);
        }
        return Array.ofAll(codes);

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
        final MutableList<Double> points, MutableSortedSet<ClassifiedCodeSequence> onScreenCodes, final Array<int> max, final boolean override, final ConnectionPool pool, boolean print, boolean detailed) {
        this.points = points;
        this.onScreenCodes = onScreenCodes;
        this.CSmax = max.get(0);
        this.OSOmax = max.get(1);
        this.OSNOmax = max.get(2);
        this.CSmaxSS = max.get(3);
        this.OSOmaxSS = max.get(4);
        this.OSNOmaxSS = max.get(5);
        this.overrideSS = override;
        this.print = print;
        this.detailed = detailed;
    }

    @Override
    protected ObservableList<Storage> call() {
        final ExecutorService executor = Executors.newFixedThreadPool(Utils.numThreads);


        Array<ClassifiedCodeSequence> classCodeSeqs = findCodes(executor);

        this.tasks = classCodeSeqs.map(classCodeSeq -> () -> {
            // We could check here for Thread.interrupted() to see if we should
            // cancel the task, but this operation is one-shot,
            // so there's not much point

            // Load from database if code already exists. If not, calculate
            final Optional<Storage> opt = Database.loadStorage(classCodeSeq, pool);

            if (opt.isPresent()) {
                final Storage storage = opt.get();
                return Either.right(storage);
            } else {
                return Either.left("//empty set " + classCodeSeq);
            }
        });

        final Array<Future<Either<String, Storage>>> futures =
            this.tasks.map(task -> executor.submit(task));

        int progress = 0;
        final int todo = futures.size();

        this.updateProgress(progress, todo);

        Optional<ExecutionException> except = Optional.empty();

        // If one of the futures throws an exception (like a failed to
        // calculate exception), we need to save it, cancel the rest of
        // the futures, and then throw that exception to bubble up the stack

        // This is where we do checking to see if we were cancelled
        for (final Future<Either<String, Storage>> future : futures) {
            if (this.isCancelled() || except.isPresent()) {
                // If the task was cancelled, or one of the futures threw an
                // exception, we need to cancel the rest of the futures

                // There is no point in interrupting the thread, since we can't
                // cancel the future while it is running
                future.cancel(false);
            } else {
                try {
                    final Either<String, Storage> either = future.get();

                    final String msg;
                    if (either.isLeft()) {
                        msg = either.getLeft();
                    } else {
                        final Storage storage = either.get();
                        // Add a new item to the partial results
                        Platform.runLater(() -> {
                            this.partialResults.get().add(storage);
                        });
                        if(detailed) {
                            // print the code, whether it covered the pixel or not
                            final CodeType type = storage.codeType();

                            String codeStr = "" + type;
                            // String codeStr = "xxx " + type; //george july 26 2017 -
                            // type whatever you want between the quotes in the line above
                            // make sure to add a space after the xxx
                            if (codeStr.equals("CS")) {
                                codeStr += "  ";
                            } else if (!codeStr.equals("OSNO")) {
                                codeStr += " ";
                            }
                            msg = codeStr + " (" + storage.codeLength() + ", " + storage.codeSum() + ") " + storage.toString();
                        } else {
                            msg = storage.toString();
                        }
                    }
                    
                    if (print) {
                    	System.out.println(msg);
                    }
                    // this.updateMessage(msg);

                    progress += 1;
                    this.updateProgress(progress, todo);
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

        // TODO This does not cancel futures that are currently running
        // (like when we hit cancel or a future throws an exception). Should we wait for them to
        // finish?
        executor.shutdown();

        // If there is an exception that happened, throw it now after shutting down the executor
        if (except.isPresent()) {
            throw new RuntimeException(except.get());
        }

        return this.partialResults.get();
    }
}
