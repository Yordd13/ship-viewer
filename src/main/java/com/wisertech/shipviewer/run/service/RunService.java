// listRuns: loads every stored pass as a run and returns their summaries, newest first.
// findRun: loads the run with the given id, or returns null when no pass has it.
// newestFirst: orders summaries by acquisition time descending with nulls last, then by id descending.
// load: assembles a run from a pass's detections, nearest AIS per vessel, matches, timing and raster.
// aisWindow: returns the configured AIS window either side of a pass as a duration.
// earliestAcquisition: returns the earliest acquisition time among the detections, or null.

package com.wisertech.shipviewer.run.service;

import com.wisertech.shipviewer.ais.model.AisPosition;
import com.wisertech.shipviewer.ais.model.AisTiming;
import com.wisertech.shipviewer.ais.service.PassAis;
import com.wisertech.shipviewer.config.ViewerProperties;
import com.wisertech.shipviewer.detection.model.Detection;
import com.wisertech.shipviewer.detection.model.MatchedDetection;
import com.wisertech.shipviewer.match.service.TransponderMatcher;
import com.wisertech.shipviewer.run.model.Run;
import com.wisertech.shipviewer.run.model.RunLog;
import com.wisertech.shipviewer.run.model.RunSummary;
import com.wisertech.shipviewer.run.repository.PassRepository;
import com.wisertech.shipviewer.run.repository.PassRepository.StoredPass;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RunService {
    private final PassRepository passes;
    private final TransponderMatcher matcher;
    private final ViewerProperties properties;

    public RunService(PassRepository passes, TransponderMatcher matcher, ViewerProperties properties) {
        this.passes = passes;
        this.matcher = matcher;
        this.properties = properties;
    }

    public List<RunSummary> listRuns() {
        List<RunSummary> summaries = new ArrayList<>();
        for (StoredPass pass : passes.findAll()) {
            summaries.add(load(pass).summary());
        }
        summaries.sort(newestFirst());
        return summaries;
    }

    public Run findRun(String id) {
        StoredPass pass = passes.findByRunId(id);
        return pass == null ? null : load(pass);
    }

    private static Comparator<RunSummary> newestFirst() {
        Comparator<RunSummary> byTime = Comparator.comparing(
                RunSummary::acquisitionTime, Comparator.nullsLast(Comparator.reverseOrder()));
        return byTime.thenComparing(RunSummary::id, Comparator.reverseOrder());
    }

    private Run load(StoredPass pass) {
        List<Detection> detections = passes.detectionsOf(pass);
        List<AisPosition> positions = PassAis.nearestPerVessel(
                passes.aisAround(pass.start(), aisWindow()), pass.start());

        Instant acquisitionTime = earliestAcquisition(detections);
        if (acquisitionTime == null) {
            acquisitionTime = pass.start();
        }
        List<MatchedDetection> matched = matcher.match(detections, positions);

        return new Run(
                pass.runId(),
                acquisitionTime,
                !positions.isEmpty(),
                detections,
                positions,
                matched,
                new RunLog(pass.start(), pass.cfarRawCount()),
                AisTiming.of(positions, acquisitionTime),
                passes.rasterOf(pass));
    }

    private Duration aisWindow() {
        return Duration.ofSeconds(Math.round(properties.aisWindowMinutes() * 60));
    }

    private static Instant earliestAcquisition(List<Detection> detections) {
        Instant earliest = null;
        for (Detection detection : detections) {
            Instant candidate = detection.acquisitionTime();
            if (candidate != null && (earliest == null || candidate.isBefore(earliest))) {
                earliest = candidate;
            }
        }
        return earliest;
    }
}
