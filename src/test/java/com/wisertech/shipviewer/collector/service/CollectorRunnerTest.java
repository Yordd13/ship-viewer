// someAisIsStored: stubs the activity repository with a recent last position and an hourly count.
// aProcessCalledAiscollectIsTheRunningCollector: checks an aiscollect.exe process is reported as running.
// withoutSuchAProcessItIsStopped: checks the state is stopped when no process is named aiscollect.
// aSecondCollectorIsRefused: checks that starting while a collector process runs throws naming its process id.
// startingWritesTheLogAndAFailedStartEndsAsStopped: checks the log is written and an Error ends it stopped, then waits for the child to exit.
// onWindowsItIsLaunchedDetachedThroughStart: checks the launch command uses cmd /c start /b only on Windows.
// theStatusSurvivesTheDatabaseBeingAway: checks status still answers, with null counts, when the database fails.
// runner: builds a collector runner over the given processes with a fixed clock and the temp pipeline folder.
// process: mocks a live process handle with the given pid and command, started an hour ago.

package com.wisertech.shipviewer.collector.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wisertech.shipviewer.Fixtures;
import com.wisertech.shipviewer.collector.model.CollectorStatus;
import com.wisertech.shipviewer.collector.repository.AisActivityRepository;
import com.wisertech.shipviewer.exception.CollectorAlreadyRunningException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataAccessResourceFailureException;

class CollectorRunnerTest {
    private static final Instant NOW = Instant.parse("2026-10-06T12:00:00Z");
    private static final String JAVA = ProcessHandle.current().info().command().orElseThrow();

    @TempDir
    Path pipelineDir;

    private final AisActivityRepository activity = mock(AisActivityRepository.class);

    @BeforeEach
    void someAisIsStored() {
        when(activity.lastPositionAt()).thenReturn(NOW.minusSeconds(90));
        when(activity.positionsSince(NOW.minus(Duration.ofHours(1)))).thenReturn(1234L);
    }

    @Test
    void aProcessCalledAiscollectIsTheRunningCollector() {
        ProcessHandle collector = process(4242, "C:\\Temp\\go-build1\\exe\\aiscollect.exe");
        CollectorRunner runner = runner(List.of(process(1, "C:\\Go\\bin\\go.exe"), collector), false);

        CollectorStatus status = runner.status();

        assertThat(status.state()).isEqualTo(CollectorStatus.RUNNING);
        assertThat(status.pid()).isEqualTo(4242L);
        assertThat(status.startedAt()).isEqualTo(NOW.minusSeconds(3600));
        assertThat(status.lastPositionAt()).isEqualTo(NOW.minusSeconds(90));
        assertThat(status.positionsLastHour()).isEqualTo(1234L);
        assertThat(status.log()).isEmpty();
    }

    @Test
    void withoutSuchAProcessItIsStopped() {
        ProcessHandle nameless = mock(ProcessHandle.class);
        ProcessHandle.Info noCommand = mock(ProcessHandle.Info.class);
        when(nameless.isAlive()).thenReturn(true);
        when(nameless.info()).thenReturn(noCommand);
        when(noCommand.command()).thenReturn(Optional.empty());

        CollectorStatus status = runner(List.of(process(1, "/usr/bin/go"), nameless), false).status();

        assertThat(status.state()).isEqualTo(CollectorStatus.STOPPED);
        assertThat(status.pid()).isNull();
        assertThat(status.startedAt()).isNull();
    }

    @Test
    void aSecondCollectorIsRefused() {
        CollectorRunner runner = runner(List.of(process(4242, "/opt/aiscollect")), false);

        assertThatThrownBy(runner::start)
                .isInstanceOf(CollectorAlreadyRunningException.class)
                .hasMessageContaining("process 4242");
    }

    @Test
    void startingWritesTheLogAndAFailedStartEndsAsStopped() throws Exception {
        CollectorRunner runner = runner(List.of(), false);

        CollectorStatus starting = runner.start();

        assertThat(starting.state()).isEqualTo(CollectorStatus.STARTING);
        assertThatThrownBy(runner::start).isInstanceOf(CollectorAlreadyRunningException.class)
                .hasMessage("The AIS collector is already starting.");

        Instant deadline = Instant.now().plusSeconds(30);
        while (runner.status().state().equals(CollectorStatus.STARTING)) {
            assertThat(Instant.now()).as("the failed start was noticed").isBefore(deadline);
            Thread.sleep(100);
        }
        for (ProcessHandle child : ProcessHandle.current().children().toList()) {
            child.onExit().get(30, TimeUnit.SECONDS);
        }
        List<String> log = runner.status().log();
        assertThat(log.get(0)).isEqualTo("$ " + JAVA + " run ./aiscollect");
        assertThat(log).anyMatch(line -> line.startsWith("Error:"));
        assertThat(Files.exists(pipelineDir.resolve("logs").resolve("aiscollect.log"))).isTrue();
    }

    @Test
    void onWindowsItIsLaunchedDetachedThroughStart() {
        assertThat(runner(List.of(), true).launchCommand())
                .containsExactly("cmd", "/c", "start", "\"\"", "/b", JAVA, "run", "./aiscollect");
        assertThat(runner(List.of(), false).launchCommand())
                .containsExactly(JAVA, "run", "./aiscollect");
    }

    @Test
    void theStatusSurvivesTheDatabaseBeingAway() {
        when(activity.lastPositionAt()).thenThrow(new DataAccessResourceFailureException("down"));

        CollectorStatus status = runner(List.of(), false).status();

        assertThat(status.state()).isEqualTo(CollectorStatus.STOPPED);
        assertThat(status.lastPositionAt()).isNull();
        assertThat(status.positionsLastHour()).isNull();
    }

    private CollectorRunner runner(List<ProcessHandle> processes, boolean windows) {
        return new CollectorRunner(Fixtures.properties(pipelineDir, JAVA), activity,
                processes::stream, Clock.fixed(NOW, ZoneOffset.UTC), windows);
    }

    private static ProcessHandle process(long pid, String command) {
        ProcessHandle process = mock(ProcessHandle.class);
        ProcessHandle.Info info = mock(ProcessHandle.Info.class);
        when(process.isAlive()).thenReturn(true);
        when(process.pid()).thenReturn(pid);
        when(process.info()).thenReturn(info);
        when(info.command()).thenReturn(Optional.of(command));
        when(info.startInstant()).thenReturn(Optional.of(NOW.minusSeconds(3600)));
        return process;
    }
}
