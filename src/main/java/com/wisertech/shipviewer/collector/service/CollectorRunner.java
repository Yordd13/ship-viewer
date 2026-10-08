// status: reports whether the collector is running, starting or stopped, with its database activity and log.
// start: launches the AIS collector detached with its output logged, refusing if one already runs.
// launchCommand: builds the go run command, wrapped in cmd /c start /b on Windows so it outlives the viewer.
// findCollector: finds a live operating-system process whose executable is the AIS collector.
// isCollector: tells whether a process's executable is named aiscollect or aiscollect.exe.
// endedWithError: tells whether the log shows the collector exited with an error or failed exit status.
// readLogTail: reads up to the last 200 non-blank lines of the collector's log file.

package com.wisertech.shipviewer.collector.service;

import com.wisertech.shipviewer.collector.model.CollectorStatus;
import com.wisertech.shipviewer.collector.repository.AisActivityRepository;
import com.wisertech.shipviewer.config.ViewerProperties;
import com.wisertech.shipviewer.exception.CollectorAlreadyRunningException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.ProcessBuilder.Redirect;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
public class CollectorRunner {
    static final String EXECUTABLE = "aiscollect";
    static final String PACKAGE = "./cmd/" + EXECUTABLE;
    static final Path LOG_FILE = Path.of("logs", "aiscollect.log");

    private static final Duration STARTING_GRACE = Duration.ofMinutes(2);
    private static final int LOG_LINES_SHOWN = 200;
    private static final int LOG_BYTES_READ = 64 * 1024;

    private final ViewerProperties properties;
    private final AisActivityRepository activity;
    private final Supplier<Stream<ProcessHandle>> processes;
    private final Clock clock;
    private final boolean windows;

    private Instant launchedAt;

    @Autowired
    public CollectorRunner(ViewerProperties properties, AisActivityRepository activity) {
        this(properties, activity, ProcessHandle::allProcesses, Clock.systemUTC(),
                System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows"));
    }

    CollectorRunner(ViewerProperties properties, AisActivityRepository activity,
            Supplier<Stream<ProcessHandle>> processes, Clock clock, boolean windows) {
        this.properties = properties;
        this.activity = activity;
        this.processes = processes;
        this.clock = clock;
        this.windows = windows;
    }

    public synchronized CollectorStatus status() {
        Optional<ProcessHandle> running = findCollector();
        List<String> log = readLogTail();

        String state;
        if (running.isPresent()) {
            state = CollectorStatus.RUNNING;
        } else if (launchedAt != null
                && clock.instant().isBefore(launchedAt.plus(STARTING_GRACE))
                && !endedWithError(log)) {
            state = CollectorStatus.STARTING;
        } else {
            state = CollectorStatus.STOPPED;
        }

        Instant lastPosition = null;
        Long lastHour = null;
        try {
            lastPosition = activity.lastPositionAt();
            lastHour = activity.positionsSince(clock.instant().minus(Duration.ofHours(1)));
        } catch (DataAccessException databaseAway) {
        }

        return new CollectorStatus(
                state,
                running.map(ProcessHandle::pid).orElse(null),
                running.flatMap(process -> process.info().startInstant()).orElse(null),
                lastPosition,
                lastHour,
                log);
    }

    public synchronized CollectorStatus start() throws IOException {
        CollectorStatus current = status();
        if (!CollectorStatus.STOPPED.equals(current.state())) {
            throw new CollectorAlreadyRunningException(current.pid() == null
                    ? "The AIS collector is already starting."
                    : "The AIS collector is already running (process " + current.pid()
                            + "). Only one may run: a second would hold a second subscription "
                            + "and store every position twice.");
        }
        Path pipelineDir = properties.pipelineDir();
        if (pipelineDir == null || !Files.isDirectory(pipelineDir)) {
            throw new IOException("shipviewer.pipeline-dir does not point at a folder: " + pipelineDir);
        }

        Path log = pipelineDir.resolve(LOG_FILE);
        Files.createDirectories(log.getParent());
        Files.writeString(log, "$ " + properties.goCommand() + " run " + PACKAGE
                + System.lineSeparator(), StandardCharsets.UTF_8);

        new ProcessBuilder(launchCommand())
                .directory(pipelineDir.toFile())
                .redirectErrorStream(true)
                .redirectOutput(Redirect.appendTo(log.toFile()))
                .start();
        launchedAt = clock.instant();
        return status();
    }

    List<String> launchCommand() {
        List<String> command = new ArrayList<>();
        if (windows) {
            command.addAll(List.of("cmd", "/c", "start", "\"\"", "/b"));
        }
        command.addAll(List.of(properties.goCommand(), "run", PACKAGE));
        return command;
    }

    private Optional<ProcessHandle> findCollector() {
        try (Stream<ProcessHandle> all = processes.get()) {
            return all.filter(process -> process.isAlive() && isCollector(process)).findFirst();
        }
    }

    private static boolean isCollector(ProcessHandle process) {
        return process.info().command()
                .map(command -> Path.of(command).getFileName().toString())
                .map(name -> name.equalsIgnoreCase(EXECUTABLE) || name.equalsIgnoreCase(EXECUTABLE + ".exe"))
                .orElse(false);
    }

    private static boolean endedWithError(List<String> log) {
        return log.stream().anyMatch(line -> line.startsWith("Error:") || line.startsWith("exit status"));
    }

    private List<String> readLogTail() {
        Path pipelineDir = properties.pipelineDir();
        if (pipelineDir == null) {
            return List.of();
        }
        Path log = pipelineDir.resolve(LOG_FILE);
        if (!Files.isRegularFile(log)) {
            return List.of();
        }
        try (RandomAccessFile file = new RandomAccessFile(log.toFile(), "r")) {
            long start = Math.max(0, file.length() - LOG_BYTES_READ);
            byte[] bytes = new byte[(int) (file.length() - start)];
            file.seek(start);
            file.readFully(bytes);
            List<String> lines = new ArrayList<>(Arrays.asList(
                    new String(bytes, StandardCharsets.UTF_8).split("\\r?\\n")));
            if (start > 0) {
                lines.remove(0);
            }
            lines.removeIf(String::isBlank);
            return List.copyOf(lines.subList(Math.max(0, lines.size() - LOG_LINES_SHOWN), lines.size()));
        } catch (IOException unreadable) {
            return List.of("Could not read " + log + ": " + unreadable.getMessage());
        }
    }
}
