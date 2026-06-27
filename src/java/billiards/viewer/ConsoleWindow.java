package billiards.viewer;

import billiards.wrapper.Wrapper;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

import javafx.animation.AnimationTimer;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToolBar;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.text.Font;
import javafx.stage.Screen;
import javafx.stage.Stage;

/**
 * In-app console (Suryansh Ankur, 2026).
 *
 * <p>Replaces the need to launch from a terminal. {@link Wrapper#setupConsoleCapture()}
 * redirects the process stdout/stderr (fd 1 and 2) into a pipe; this class drains that
 * pipe and shows everything the native backend AND the JVM print in a window, and tees
 * it to a log file so failures are diagnosable even with no terminal attached.
 *
 * <p>Threading model — deliberately mirrors the project's hard-won FX-thread rules:
 * <ul>
 *   <li>A dedicated <b>daemon reader thread</b> performs blocking {@code consoleRead}s and
 *       <b>always</b> drains the kernel pipe. This is critical: if the pipe ever filled,
 *       native {@code std::cout} writes would block the compute threads and freeze the app.</li>
 *   <li>An {@link AnimationTimer} flushes accumulated text to the {@link TextArea} on the FX
 *       thread at most once per frame, in batches — never once per line.</li>
 * </ul>
 */
public final class ConsoleWindow {

    // Cap the on-screen buffer; a TextArea degrades badly past a few hundred KB.
    private static final int MAX_CHARS = 200_000;

    // Cap pending (not-yet-rendered) text so a stalled FX thread can't grow memory
    // without bound. The reader keeps draining the pipe regardless.
    private static final int MAX_PENDING_CHARS = 1_000_000;

    // High-contrast dark theme (Suryansh Ankur, 2026): the default light TextArea
    // is glary and low-contrast, which is hard to read for some users. Near-black
    // background with bright off-white text maximizes legibility. Tweak here.
    private static final String BG_COLOR = "#1b1b1b";       // console background
    private static final String TEXT_COLOR = "#f5f5f5";     // console text
    private static final String TOOLBAR_COLOR = "#2a2a2a";  // toolbar strip

    // Persistent log, so crashes are diagnosable with no terminal attached.
    private static final File LOG_FILE =
        new File(System.getProperty("user.home"), "Library/Logs/BilliardsEverything/console.log");

    private final Stage stage = new Stage();
    private final TextArea textArea = new TextArea();
    private final CheckBox autoScroll = new CheckBox("Auto-scroll");

    // Guarded by itself. Reader thread appends; FX timer drains.
    private final StringBuilder pending = new StringBuilder();

    // Guarded by itself. Written by the reader thread and the uncaught-exception
    // handler; flushed on every write so a crash leaves a complete log.
    private final Writer logWriter;

    private volatile boolean installed = false;

    private ConsoleWindow() {
        this.logWriter = openLog();
        buildUi();
    }

    /**
     * Install capture and show the console. Safe to call once, early in startup
     * (before other output is produced). Returns the window, or {@code null} if
     * native capture could not be installed (output then stays on stdout/stderr).
     */
    public static ConsoleWindow install() {
        final ConsoleWindow console = new ConsoleWindow();
        console.installed = Wrapper.setupConsoleCapture();
        if (!console.installed) {
            System.err.println("ConsoleWindow: native capture unavailable; "
                + "output will remain on the terminal.");
            return null;
        }
        console.installCrashLogging();
        console.startReaderThread();
        console.startFlushTimer();
        console.stage.show();
        console.logLine("=== Billiards Everything console started; log: " + LOG_FILE + " ===");
        return console;
    }

    private static Writer openLog() {
        try {
            Files.createDirectories(LOG_FILE.getParentFile().toPath());
            // Append so the prior crash's tail survives across runs.
            return Files.newBufferedWriter(LOG_FILE.toPath(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (final IOException e) {
            // Non-fatal: fall back to no file logging (window still works).
            return null;
        }
    }

    /**
     * Guarantee the fatal error reaches the log file directly, not only via the
     * pipe — the daemon reader might not drain the last bytes before the JVM exits.
     */
    private void installCrashLogging() {
        final Thread.UncaughtExceptionHandler prior = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            final StringWriter sw = new StringWriter();
            error.printStackTrace(new PrintWriter(sw));
            logLine("!!! Uncaught exception in thread \"" + thread.getName() + "\":\n" + sw);
            System.err.println("Uncaught exception in \"" + thread.getName() + "\": " + error);
            error.printStackTrace();
            if (prior != null) {
                prior.uncaughtException(thread, error);
            }
        });
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (logWriter != null) {
                synchronized (logWriter) {
                    try {
                        logWriter.flush();
                        logWriter.close();
                    } catch (final IOException ignored) {
                        // shutting down anyway
                    }
                }
            }
        }, "console-log-flush"));
    }

    private void logLine(final String line) {
        writeLog(line.endsWith("\n") ? line : line + "\n");
    }

    private void writeLog(final String text) {
        if (logWriter == null) {
            return;
        }
        synchronized (logWriter) {
            try {
                logWriter.write(text);
                logWriter.flush();
            } catch (final IOException ignored) {
                // Don't let logging failures cascade.
            }
        }
    }

    private void buildUi() {
        textArea.setEditable(false);
        textArea.setWrapText(false);
        // -fx-control-inner-background colours the content area; -fx-text-fill the
        // text; the highlight colours keep selected text readable on the dark bg.
        textArea.setFont(Font.font("Monospaced", 13));
        textArea.setStyle(
            "-fx-control-inner-background: " + BG_COLOR + ";"
            + "-fx-text-fill: " + TEXT_COLOR + ";"
            + "-fx-highlight-fill: #3a5a8c;"
            + "-fx-highlight-text-fill: #ffffff;"
            + "-fx-font-family: 'Monospaced'; -fx-font-size: 13px;");

        autoScroll.setSelected(true);
        autoScroll.setStyle("-fx-text-fill: " + TEXT_COLOR + ";");

        final Button clear = new Button("Clear");
        clear.setOnAction(e -> {
            textArea.clear();
            synchronized (pending) {
                pending.setLength(0);
            }
        });

        final Button copy = new Button("Copy all");
        copy.setOnAction(e -> {
            final ClipboardContent content = new ClipboardContent();
            content.putString(textArea.getText());
            Clipboard.getSystemClipboard().setContent(content);
        });

        final ToolBar toolBar = new ToolBar(clear, copy, autoScroll);
        toolBar.setStyle("-fx-background-color: " + TOOLBAR_COLOR + ";");

        final BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: " + BG_COLOR + ";");
        root.setTop(toolBar);
        root.setCenter(textArea);

        stage.setTitle("Billiards Everything — Console");
        stage.setScene(new Scene(root, 760, 460));
        // Keep the console usable and out of the way: enforce a minimum size and
        // open it toward the top-right of the screen so it doesn't land on top of
        // the main window. Suryansh Ankur, 2026
        stage.setMinWidth(480);
        stage.setMinHeight(280);
        final Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        stage.setX(Math.max(screen.getMinX() + 20, screen.getMaxX() - 760 - 20));
        stage.setY(screen.getMinY() + 40);

        // Closing (the red X on macOS) must NOT destroy the console — there is no
        // way to reopen it while the app runs, so a stray click would lose all
        // further output. Intercept the close request and iconify (minimize)
        // instead: the window is tucked into the dock and can always be restored.
        // The minimize/zoom buttons keep working normally. Suryansh Ankur, 2026
        stage.setOnCloseRequest(event -> {
            event.consume();
            stage.setIconified(true);
        });
    }

    private void startReaderThread() {
        final Thread reader = new Thread(this::readLoop, "console-reader");
        reader.setDaemon(true);
        reader.start();
    }

    /** Always drains the pipe so it can never fill and block native writers. */
    private void readLoop() {
        final byte[] readBuf = new byte[8192];
        final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE);
        // Carries bytes of an incomplete multibyte char across reads.
        ByteBuffer leftover = ByteBuffer.allocate(0);

        while (true) {
            final int n = Wrapper.consoleRead(readBuf, readBuf.length);
            if (n <= 0) {
                // -1 = not installed / error, 0 = EOF. Nothing more will come.
                break;
            }

            final ByteBuffer in = ByteBuffer.allocate(leftover.remaining() + n);
            in.put(leftover);
            in.put(readBuf, 0, n);
            in.flip();

            final CharBuffer out = CharBuffer.allocate(in.remaining() + 1);
            decoder.reset();
            decoder.decode(in, out, false);  // endOfInput=false: keep trailing partial bytes
            out.flip();

            // Whatever the decoder didn't consume is an incomplete trailing char.
            leftover = ByteBuffer.allocate(in.remaining());
            leftover.put(in);
            leftover.flip();

            if (out.length() > 0) {
                final String text = out.toString();
                enqueue(text);
                writeLog(text);
            }
        }
    }

    private void enqueue(final String text) {
        synchronized (pending) {
            pending.append(text);
            final int overflow = pending.length() - MAX_PENDING_CHARS;
            if (overflow > 0) {
                pending.delete(0, overflow);  // drop oldest if GUI is hopelessly behind
            }
        }
    }

    private void startFlushTimer() {
        new AnimationTimer() {
            @Override
            public void handle(final long now) {
                final String chunk;
                synchronized (pending) {
                    if (pending.length() == 0) {
                        return;
                    }
                    chunk = pending.toString();
                    pending.setLength(0);
                }
                textArea.appendText(chunk);

                final int len = textArea.getLength();
                if (len > MAX_CHARS) {
                    textArea.deleteText(0, len - MAX_CHARS);
                }
                if (autoScroll.isSelected()) {
                    textArea.positionCaret(textArea.getLength());
                }
            }
        }.start();
    }
}
