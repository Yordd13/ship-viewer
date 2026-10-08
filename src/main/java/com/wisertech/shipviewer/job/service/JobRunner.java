// isRunning: tells whether a job is currently running.
// snapshot: returns an immutable copy of the job's state, log and unfinished output line.
// start: launches a Go command in the pipeline folder and pumps its output, refusing if a job already runs.
// pump: reads the process output character by character into log lines, then records the exit code.
// commitPartial: moves the unfinished output line into the log, if there is one.
// append: adds a line to the log under the lock.
// appendLocked: adds a line to the log, dropping the oldest beyond 500 lines.
// finish: records the exit code or failure and marks the job done or failed.

package com.wisertech.shipviewer.job.service;

import com.wisertech.shipviewer.config.ViewerProperties;
import com.wisertech.shipviewer.exception.JobAlreadyRunningException;
import com.wisertech.shipviewer.job.model.JobSnapshot;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class JobRunner {
    private static final int LOG_LINES_KEPT = 500;

    private final ViewerProperties properties;

    private final Deque<String> log = new ArrayDeque<>();
    private final StringBuilder partial = new StringBuilder();
    private String kind;
    private String state = "idle";
    private Instant startedAt;
    private Instant finishedAt;
    private Integer exitCode;
    private String failure;

    public JobRunner(ViewerProperties properties) {
        this.properties = properties;
    }

    public synchronized boolean isRunning() {
        return "running".equals(state);
    }

    public synchronized JobSnapshot snapshot() {
        return new JobSnapshot(kind, state, startedAt, finishedAt, exitCode, failure,
                List.copyOf(log), partial.toString());
    }

    public void start(String jobKind, List<String> arguments) throws IOException {
        synchronized (this) {
            if (isRunning()) {
                throw new JobAlreadyRunningException(
                        "A " + kind + " job is already running. Only one at a time: the "
                                + "processing job alone asks Docker for 11 GB.");
            }
            if (properties.pipelineDir() == null
                    || !Files.isDirectory(properties.pipelineDir())) {
                throw new IOException("shipviewer.pipeline-dir does not point at a folder: "
                        + properties.pipelineDir());
            }
            kind = jobKind;
            state = "running";
            startedAt = Instant.now();
            finishedAt = null;
            exitCode = null;
            failure = null;
            log.clear();
            partial.setLength(0);
        }

        List<String> command = new ArrayList<>();
        command.add(properties.goCommand());
        command.addAll(arguments);

        append("$ " + String.join(" ", command));

        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(properties.pipelineDir().toFile())
                .redirectErrorStream(true);

        Process process;
        try {
            process = builder.start();
        } catch (IOException cannotStart) {
            finish(null, "Could not start " + properties.goCommand() + ": "
                    + cannotStart.getMessage());
            throw cannotStart;
        }

        Thread.ofVirtual().name("job-" + jobKind).start(() -> pump(process));
    }

    private void pump(Process process) {
        try (Reader reader = new InputStreamReader(process.getInputStream(),
                StandardCharsets.UTF_8)) {
            int character;
            while ((character = reader.read()) != -1) {
                if (character == '\n' || character == '\r') {
                    commitPartial();
                } else {
                    synchronized (this) {
                        partial.append((char) character);
                    }
                }
            }
            commitPartial();
            finish(process.waitFor(), null);
        } catch (IOException readFailed) {
            finish(null, "Lost the job's output: " + readFailed.getMessage());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            finish(null, "Stopped waiting for the job.");
        }
    }

    private synchronized void commitPartial() {
        if (partial.isEmpty()) {
            return;
        }
        appendLocked(partial.toString());
        partial.setLength(0);
    }

    private synchronized void append(String line) {
        appendLocked(line);
    }

    private void appendLocked(String line) {
        log.addLast(line);
        while (log.size() > LOG_LINES_KEPT) {
            log.removeFirst();
        }
    }

    private synchronized void finish(Integer code, String problem) {
        exitCode = code;
        failure = problem;
        finishedAt = Instant.now();
        state = problem == null && code != null && code == 0 ? "done" : "failed";
    }
}
