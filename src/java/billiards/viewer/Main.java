package billiards.viewer;

import billiards.database.Admin;
import billiards.wrapper.ConnectionPool;
import billiards.wrapper.Wrapper;

import java.io.File;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javafx.application.Application;
import javafx.stage.Stage;
import patternfinder.PatternFinder;

public final class Main extends Application {

    public static void main(final String[] args) {
        // Must run before anything touches the filesystem (incl. the JVM's nio
        // default directory). Suryansh Ankur, 2026
        redirectWorkingDirIfReadOnly();
        launch(args);
    }

    // When the app is double-clicked (vs. launched from a terminal), macOS starts
    // it with the working directory set to "/", which is read-only. The app and
    // the C++ backend both read/write many files by RELATIVE path
    // (iterationPoly.txt, cover/*.txt, tmp/*.txt, ...), so those operations fail
    // with "Read-only file system" and the app dies on startup. If the current
    // working directory isn't writable, relocate to a per-user data directory.
    // A terminal/dev launch from a writable dir is left untouched, so the project
    // workflow (cover/tmp outputs in the repo) is unchanged.
    private static void redirectWorkingDirIfReadOnly() {
        final String cwd = System.getProperty("user.dir");
        // java.io.File (not nio) so we don't initialize nio's cached default
        // directory before we've updated user.dir below.
        if (cwd != null && new File(cwd).canWrite()) {
            return;
        }
        final File base = new File(System.getProperty("user.home"), "BilliardsEverything");
        base.mkdirs();
        // Subdirectories the app/backend write into by relative path.
        new File(base, "cover").mkdirs();
        new File(base, "small_cover").mkdirs();
        new File(base, "tmp").mkdirs();

        final String basePath = base.getAbsolutePath();
        System.setProperty("user.dir", basePath);  // Java relative-path resolution
        Wrapper.changeWorkingDir(basePath);         // native (C++) relative-path resolution
        System.out.println("Working directory '" + cwd + "' not writable; using " + basePath);
    }

    // These are initialized first (like they are in a constructor)
    private final ExecutorService executor = Executors.newFixedThreadPool(Utils.numThreads);
    private ConnectionPool pool = null;

    // Suryansh Ankur, 2026
    private final String versionNumber = "10.0.17";

    // Order is constructor, init, start, stop
    // It would be a lot simpler if these methods didn't exist, and I just did
    // stuff myself. A lot less magic that way.

    // We currently don't use this
    @Override
    public void init() {
        Wrapper.errorLogging();
    }

    @Override
    public void start(final Stage mainWindow) {
        // In-app console: capture native + JVM stdout/stderr so the app no longer
        // needs to be launched from a terminal. Install first, before any output.
        // Suryansh Ankur, 2026
        ConsoleWindow.install();

        System.out.println("Threads available: " + Utils.numThreads);
        final DBGui dbGui = new DBGui();
        // Shows and waits until the window closes
        final Optional<String> databaseName = dbGui.getDbName();

        // IMPORTANT! Create a new garbage database if it does not already exist
        Admin.newJavaDB("garbage");

        // since we just have two programs for now, this is just a boolean telling if we're
        // using viewer or not.
        final boolean viewerSelected = dbGui.getProgram();

        databaseName.ifPresent(dbName -> {

            // 2024-06-06 Austin experimenting with thread and connection pool sizes
            pool = Admin.getConnectionPool(dbName, Utils.numThreads);
            if (viewerSelected) {
	            final Viewer viewer = new Viewer(mainWindow, versionNumber, executor, pool, dbName);
	            viewer.start(executor);
            } else {
            	final PatternFinder pFinder = new PatternFinder(mainWindow, versionNumber, pool, dbName);
                pFinder.start();
            }
        });
    }

    @Override
    public void stop() {

        if (pool != null) {
            pool.destroy();
        }

        executor.shutdown();
    }
}
