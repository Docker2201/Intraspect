package com.sergey.pisarev.util;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Startup log: why the program did not open on this computer.
 *
 * <p>On some shop-floor machines the window simply never appeared and there was
 * nothing to look at. This log is written BEFORE the user interface is built, so it
 * captures failures of the FXML load, of JavaFX itself and of the native libraries.
 *
 * <p>The file lives next to the program ({@code intraspect-start.log}). If that folder
 * cannot be written — a read-only stick, a locked-down policy — the log falls back to
 * the data folder and then to the system temp directory, so it is never lost silently.
 *
 * <p>Only the last {@value #MAX_RUNS} runs are kept: the file stays readable on a
 * machine where the program is started every day.
 *
 * <p>Deliberately English-only. These lines are read by whoever supports the machines,
 * pasted into tickets and compared between computers; one fixed language keeps them
 * greppable and independent of the interface language.
 *
 * <p>No method throws. A log that breaks startup would be worse than no log at all.
 */
public final class StartupLog {
    private static final String FILE_NAME = "intraspect-start.log";
    /** How many past runs are kept in the file. */
    private static final int MAX_RUNS = 100;
    private static final String RUN_MARKER = "=== run ";
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static volatile Path logPath;
    private static volatile boolean resolved;

    private StartupLog() {
    }

    /** Where the log is written; null when no location was writable. */
    public static Path path() {
        return logPath;
    }

    /**
     * Start of a run: the machine's environment in one header.
     *
     * <p>Call this as the first statement of {@code main}, before touching JavaFX.
     */
    public static void begin(String appVersion) {
        try {
            keepLastRuns();
            StringBuilder header = new StringBuilder();
            String eol = System.lineSeparator();
            header.append(eol)
                    .append(RUN_MARKER).append(LocalDateTime.now().format(STAMP))
                    .append(" ===").append(eol);
            header.append("  version    : ").append(appVersion == null ? "?" : appVersion).append(eol);
            header.append("  os         : ")
                    .append(property("os.name")).append(' ').append(property("os.version"))
                    .append(' ').append(property("os.arch")).append(eol);
            header.append("  java       : ").append(property("java.version"))
                    .append("  (").append(property("java.home")).append(')').append(eol);
            header.append("  locale     : ").append(java.util.Locale.getDefault().toLanguageTag()).append(eol);
            header.append("  user       : ").append(property("user.name")).append(eol);
            header.append("  working dir: ").append(property("user.dir")).append(eol);
            header.append("  launch path: ").append(property("jpackage.app-path")).append(eol);
            write(header.toString());
        } catch (RuntimeException ignored) {
            // The log must never get in the way of starting up.
        }
    }

    /** An ordinary milestone of the startup sequence. */
    public static void step(String message) {
        try {
            write("  " + LocalDateTime.now().format(STAMP) + "  " + message + System.lineSeparator());
        } catch (RuntimeException ignored) {
            // see begin()
        }
    }

    /** Where the program keeps its data — a common cause of both "does not save" and "does not start". */
    public static void storage(String description) {
        step("data: " + description);
    }

    /** Why startup failed, with the full stack and every nested cause. */
    public static void failure(String what, Throwable error) {
        try {
            StringWriter text = new StringWriter();
            PrintWriter out = new PrintWriter(text);
            out.println("  !!! " + LocalDateTime.now().format(STAMP) + "  " + what);
            for (Throwable current = error; current != null; current = current.getCause()) {
                out.println("      " + current.getClass().getName() + ": " + current.getMessage());
                for (StackTraceElement frame : current.getStackTrace()) {
                    out.println("        at " + frame);
                }
                if (current.getCause() == current) {
                    break;
                }
                if (current.getCause() != null) {
                    out.println("      caused by:");
                }
            }
            out.flush();
            write(text.toString());
        } catch (RuntimeException ignored) {
            // see begin()
        }
    }

    /** Installs a handler that records any uncaught failure of any thread. */
    public static void installGlobalHandler() {
        try {
            Thread.setDefaultUncaughtExceptionHandler(
                    (thread, error) -> failure("uncaught error in thread " + thread.getName(), error));
        } catch (RuntimeException ignored) {
            // see begin()
        }
    }

    private static String property(String key) {
        String value = System.getProperty(key);
        return value == null || value.isBlank() ? "-" : value;
    }

    /**
     * Drops the oldest runs so that at most {@value #MAX_RUNS} - 1 remain before the
     * new one is appended. Trimming by run rather than by byte count keeps whole
     * records intact: half a stack trace is useless to whoever reads this.
     */
    private static synchronized void keepLastRuns() {
        Path target = target();
        if (target == null) {
            return;
        }
        try {
            if (!Files.isRegularFile(target)) {
                return;
            }
            List<String> lines = Files.readAllLines(target, StandardCharsets.UTF_8);
            List<Integer> starts = new ArrayList<>();
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).startsWith(RUN_MARKER)) {
                    starts.add(i);
                }
            }
            if (starts.size() < MAX_RUNS) {
                return;
            }
            int from = starts.get(starts.size() - (MAX_RUNS - 1));
            Files.write(target, lines.subList(from, lines.size()), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException | RuntimeException ignored) {
            // A log we cannot tidy is still a log worth appending to.
        }
    }

    private static synchronized void write(String text) {
        Path target = target();
        if (target == null) {
            return;
        }
        try {
            Files.writeString(target, text, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException ignored) {
            // The file may have gone away mid-run — the stick was pulled. Stay quiet.
        }
    }

    private static synchronized Path target() {
        if (resolved) {
            return logPath;
        }
        resolved = true;
        for (Path candidate : candidates()) {
            if (candidate == null) {
                continue;
            }
            try {
                Path parent = candidate.getParent();
                if (parent != null && !Files.isDirectory(parent)) {
                    Files.createDirectories(parent);
                }
                Files.writeString(candidate, "", StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                logPath = candidate;
                return logPath;
            } catch (IOException | RuntimeException ignored) {
                // try the next location
            }
        }
        return null;
    }

    /** Next to the program, then the data folder, then the system temp directory. */
    private static Path[] candidates() {
        Path appDir = null;
        String appPath = System.getProperty("jpackage.app-path");
        if (appPath != null && !appPath.isBlank()) {
            try {
                appDir = Path.of(appPath).toAbsolutePath().normalize().getParent();
            } catch (RuntimeException ignored) {
                appDir = null;
            }
        }
        if (appDir == null) {
            try {
                appDir = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
            } catch (RuntimeException ignored) {
                appDir = null;
            }
        }
        Path dataDir = null;
        try {
            dataDir = PortableStorage.dataDir();
        } catch (RuntimeException ignored) {
            dataDir = null;
        }
        Path tempDir = null;
        try {
            String temp = System.getProperty("java.io.tmpdir");
            if (temp != null && !temp.isBlank()) {
                tempDir = Path.of(temp).resolve(FILE_NAME);
            }
        } catch (RuntimeException ignored) {
            tempDir = null;
        }
        return new Path[]{
                appDir == null ? null : appDir.resolve(FILE_NAME),
                dataDir == null ? null : dataDir.resolve(FILE_NAME),
                tempDir
        };
    }
}
