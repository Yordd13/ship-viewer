// writeTalker: writes a one-file Java program that prints lines, sleeps and exits with a given code.
// nothingHasRunYet: checks that a new runner is not running and reports the idle snapshot.
// aJobThatExitsCleanlyIsDoneWithItsOutputKept: checks a zero exit is done with every output line kept.
// aNonZeroExitIsAFailure: checks that a non-zero exit code marks the job failed with no failure message.
// onlyTheLast500LinesAreKept: checks that a 600-line job keeps only its last 500 log lines.
// aSecondJobIsRefusedWhileTheFirstRuns: checks that starting a job while one runs is refused.
// talk: builds the arguments that make the talker print lines, sleep and exit with a code.
// awaitFinish: waits up to 60 seconds for the job to finish and returns its snapshot.

package com.wisertech.shipviewer.job.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wisertech.shipviewer.Fixtures;
import com.wisertech.shipviewer.exception.JobAlreadyRunningException;
import com.wisertech.shipviewer.job.model.JobSnapshot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JobRunnerTest {
    private static final String JAVA =
            ProcessHandle.current().info().command().orElseThrow();

    @TempDir
    static Path workDir;

    private static Path talker;

    @BeforeAll
    static void writeTalker() throws IOException {
        talker = workDir.resolve("Talker.java");
        Files.writeString(talker, """
                public class Talker {
                    public static void main(String[] args) throws Exception {
                        int lines = Integer.parseInt(args[0]);
                        for (int i = 0; i < lines; i++) {
                            System.out.print("line " + i + (i % 2 == 0 ? "\\n" : "\\r\\n"));
                        }
                        System.out.print("no newline at the end");
                        System.out.flush();
                        Thread.sleep(Long.parseLong(args[1]));
                        System.exit(Integer.parseInt(args[2]));
                    }
                }
                """);
    }

    private final JobRunner runner = new JobRunner(Fixtures.properties(workDir, JAVA));

    @Test
    void nothingHasRunYet() {
        assertThat(runner.isRunning()).isFalse();
        assertThat(runner.snapshot()).isEqualTo(JobSnapshot.idle());
    }

    @Test
    void aJobThatExitsCleanlyIsDoneWithItsOutputKept() throws Exception {
        runner.start("search", talk(3, 0, 0));
        JobSnapshot finished = awaitFinish();

        assertThat(finished.kind()).isEqualTo("search");
        assertThat(finished.state()).isEqualTo("done");
        assertThat(finished.exitCode()).isZero();
        assertThat(finished.failure()).isNull();
        assertThat(finished.startedAt()).isBeforeOrEqualTo(finished.finishedAt());
        assertThat(finished.log().get(0)).startsWith("$ " + JAVA).endsWith("3 0 0");
        assertThat(finished.log()).containsSequence("line 0", "line 1", "line 2", "no newline at the end");
        assertThat(finished.partialLine()).isEmpty();
    }

    @Test
    void aNonZeroExitIsAFailure() throws Exception {
        runner.start("pipeline", talk(0, 0, 3));
        JobSnapshot finished = awaitFinish();

        assertThat(finished.state()).isEqualTo("failed");
        assertThat(finished.exitCode()).isEqualTo(3);
        assertThat(finished.failure()).isNull();
    }

    @Test
    void onlyTheLast500LinesAreKept() throws Exception {
        runner.start("pipeline", talk(600, 0, 0));
        List<String> log = awaitFinish().log();

        assertThat(log).hasSize(500);
        assertThat(log.get(0)).isEqualTo("line 101");
        assertThat(log.get(499)).isEqualTo("no newline at the end");
    }

    @Test
    void aSecondJobIsRefusedWhileTheFirstRuns() throws Exception {
        runner.start("pipeline", talk(0, 5_000, 0));

        assertThat(runner.isRunning()).isTrue();
        assertThatThrownBy(() -> runner.start("search", talk(0, 0, 0)))
                .isInstanceOf(JobAlreadyRunningException.class)
                .hasMessageContaining("A pipeline job is already running");
        awaitFinish();
    }

    private static List<String> talk(int lines, long sleepMillis, int exitCode) {
        return List.of(talker.toString(), String.valueOf(lines), String.valueOf(sleepMillis),
                String.valueOf(exitCode));
    }

    private JobSnapshot awaitFinish() throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(60));
        while (runner.isRunning()) {
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("The job did not finish: " + runner.snapshot());
            }
            Thread.sleep(50);
        }
        return runner.snapshot();
    }
}
