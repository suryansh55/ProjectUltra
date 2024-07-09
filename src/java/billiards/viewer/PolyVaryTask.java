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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.concurrent.Task;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;

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
    private final ExecutorService storageExecutor;
    private final ExecutorService shotExecutor;
    private final ImageView screenImage;
    private final PixelRadianMap screenMap; 

    // Calculates codeSequence set at a specific coordinate 
    private MutableSortedSet<ClassifiedCodeSequence> autoCodesFiltered(final Vector2 coords, final ExecutorService executor) {
        // autoVary requires coordinates to be in degree format
        final Vector2 degCoords = Vector2.create(Math.toDegrees(coords.x), Math.toDegrees(coords.y));
        final MutableSortedSet<ClassifiedCodeSequence> codes = new TreeSortedSet<>();
        final MutableSortedSet<ClassifiedCodeSequence> boyanCodes = overrideSS ? boyanMenu.autoVary(degCoords, this.CSmaxSS, this.OSOmaxSS, this.OSNOmaxSS, executor) : boyanMenu.autoVary(degCoords, executor);
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

    // Converts list of points into array of coordinate pairs 
    private Array<Vector2> toCoords(final MutableList<Double> points) {
        final MutableList<Vector2> out = new FastList<Vector2>();
        for(int i = 0; i < points.size(); i += 2) {
            final Vector2 coords = Vector2.create(points.get(i), points.get(i+1));
            out.add(coords);
        }
        return Array.ofAll(out);
    }

    // Runs a fast application thread task which determines the color of the pixel at a point
    private int pixelColor(final Vector2 point) {
        FutureTask<Integer> task = new FutureTask<Integer>(() -> {
            final Image image = this.screenImage.getImage();
            final PixelReader reader = image.getPixelReader();
            final int midX = (int) this.screenMap.pixelX(point.x);
            final int midY = (int) this.screenMap.pixelY(point.y);
            return reader.getArgb(midX, midY);
        });
        Platform.runLater(task);
        try {
            //System.err.println("//Found pixel color");
            return task.get();
        } catch(InterruptedException e) {
            System.err.println("//Interruption when finding pixel color");
            return -1;
        } catch(ExecutionException e) {
            System.err.println("//Failed to find pixel color");
            e.printStackTrace();
            return -1;
        }

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
    // Constructor takes a list of points to vary at
    public PolyVaryTask(
        final MutableList<Double> points, final MutableSortedSet<ClassifiedCodeSequence> onScreenCodes, final BoyanMenu boyan, final Array<Integer> max, final ConnectionPool pool, final boolean override, final ExecutorService eOne, final ExecutorService eTwo, final ImageView screen, final PixelRadianMap map) {
        this.coordList = toCoords(points);
        this.onScreenCodes = onScreenCodes;
        this.boyanMenu = boyan;
        this.CSmax = max.get(0);
        this.OSOmax = max.get(1);
        this.OSNOmax = max.get(2);
        this.CSmaxSS = max.get(3);
        this.OSOmaxSS = max.get(4);
        this.OSNOmaxSS = max.get(5);
        this.pool = pool;
        this.overrideSS = override;
        this.storageExecutor = eOne;
        this.shotExecutor = eTwo;
        this.screenImage = screen;
        this.screenMap = map;
    }

    @Override
    protected ObservableList<Storage> call() {
        // storageExecutor handles the more expensive process of calculating code regions,
        // while shotExecutor handles the much faster calculation of finding the codes present at a given point

        final MutableSortedSet<ClassifiedCodeSequence> usedCodes = new TreeSortedSet<ClassifiedCodeSequence>();
        final MutableList<Future<Either<String, Storage>>> futures = new FastList<>();

        AtomicInteger progress = new AtomicInteger(); // Create an integer which supports non-locking concurrent operations
        final int todo = this.coordList.size();
        this.updateProgress(0, todo);

        // The meat and potatoes. Finds codes sequentially, and submits them to the executer as they are found.
        // This is the most efficient way to implement multithreaded polyvary since each code can be calculated as soon as it's found, without interfering with the process of finding more codes.
        for(Vector2 coord: this.coordList) {
            MutableSortedSet<ClassifiedCodeSequence> localCodes;
            // The BoyanCodes method vary3() called by autoVary() can throw exceptions. We need to catch them
            try {
                localCodes = autoCodesFiltered(coord, shotExecutor);
            } catch(RuntimeException e) {
                //System.out.println("Caught interrupt");
                // Break out of for loop to cancel gracefully
                if(this.isCancelled() || Thread.interrupted()) {
                    break;
                } else {
                    System.err.println("Terminating because of uncaught exception when finding codeSet");
                    throw e;
                }
            }
            // We want to know if we submitted a task that will update the progress for us.
            boolean noCodes = true;

            // Take the first code not already drawn, and submit it to the storageExecutor for processing 
            for(ClassifiedCodeSequence classCodeSeq: localCodes) {
                if(this.onScreenCodes.contains(classCodeSeq) || usedCodes.contains(classCodeSeq)) continue;
                usedCodes.add(classCodeSeq); 
                noCodes = false;
                // Submit the runnable for this code
                futures.add(storageExecutor.submit(() -> {
                        int color = pixelColor(coord);
                        if(color == -1) {
                            return Either.left("");
                        }
                        if(color != 0) {
                            //System.out.println("//Pixel already filled, skipping");
                            this.updateProgress(progress.incrementAndGet(), todo); // updateProgress is thread safe
                            return Either.left("");
                        }
                        Either<String, Storage> result = loadStorage(classCodeSeq);
                        this.updateProgress(progress.incrementAndGet(), todo); // updateProgress is thread safe
                        return result;
                    })
                );
                break;
            }
            if(noCodes) { // Still need to update progress even if nothing found
                this.updateProgress(progress.incrementAndGet(), todo);
            }
        }


        Optional<ExecutionException> except = Optional.empty();

        // If one of the futures throws an exception (like a failed to
        // calculate exception), we need to save it, cancel the rest of
        // the futures, and then throw that exception to bubble up the stack

        //System.out.println("+------------Waiting for futures------------+");
        // This is where we do checking to see if we were cancelled
        for (final Future<Either<String, Storage>> future : futures) {
            if (this.isCancelled() || except.isPresent()) {
                // If the task was cancelled, or one of the futures threw an
                // exception, we need to cancel the rest of the futures
                System.out.println("//Cancelling submitted future");
                future.cancel(true);
            } else {
                try {
                    final Either<String, Storage> either = future.get();
                    if (either.isLeft()) { // Print things like empty sets 
                        if(!either.left().get().equals("")) System.out.println(either.left().get());
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

        if (except.isPresent()) {
            throw new RuntimeException(except.get());
        }

        return this.partialResults.get();
    }
}
