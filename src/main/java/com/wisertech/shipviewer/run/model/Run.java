// withTransponder: counts the detections matched to an AIS transponder.
// withoutTransponder: counts the detections with no matched transponder.
// claimedMmsis: returns the MMSIs that some detection has claimed, so the AIS layer can flag them.
// summary: condenses the run into its list line with counts, AIS gap and raw CFAR count.

package com.wisertech.shipviewer.run.model;

import com.wisertech.shipviewer.ais.model.AisPosition;
import com.wisertech.shipviewer.ais.model.AisTiming;
import com.wisertech.shipviewer.detection.model.Detection;
import com.wisertech.shipviewer.detection.model.MatchedDetection;
import com.wisertech.shipviewer.raster.model.RasterSource;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record Run(
        String id,
        Instant acquisitionTime,
        boolean hasAis,
        List<Detection> detections,
        List<AisPosition> aisPositions,
        List<MatchedDetection> matched,
        RunLog log,
        AisTiming timing,
        RasterSource raster) {
    public int withTransponder() {
        int count = 0;
        for (MatchedDetection candidate : matched) {
            if (candidate.matched()) {
                count++;
            }
        }
        return count;
    }

    public int withoutTransponder() {
        return detections.size() - withTransponder();
    }

    public Set<String> claimedMmsis() {
        Set<String> claimed = new HashSet<>();
        for (MatchedDetection candidate : matched) {
            if (candidate.matchedMmsi() != null) {
                claimed.add(candidate.matchedMmsi());
            }
        }
        return claimed;
    }

    public RunSummary summary() {
        return new RunSummary(
                id,
                acquisitionTime,
                timing.gapMinutes(),
                hasAis,
                detections.size(),
                log.cfarRawCount(),
                withTransponder(),
                withoutTransponder());
    }
}
