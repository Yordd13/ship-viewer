// nearestPerVessel: keeps each vessel's report nearest the pass start, ordered by time then MMSI.
// closer: tells whether a report is nearer the pass start than another, the later one winning a tie.

package com.wisertech.shipviewer.ais.service;

import com.wisertech.shipviewer.ais.model.AisPosition;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PassAis {
    private PassAis() {
    }

    public static List<AisPosition> nearestPerVessel(List<AisPosition> positions, Instant passStart) {
        Map<String, AisPosition> nearest = new LinkedHashMap<>();
        for (AisPosition position : positions) {
            AisPosition current = nearest.get(position.mmsi());
            if (current == null || closer(position, current, passStart)) {
                nearest.put(position.mmsi(), position);
            }
        }

        List<AisPosition> chosen = new ArrayList<>(nearest.values());
        chosen.sort(Comparator.comparing(AisPosition::timestamp).thenComparing(AisPosition::mmsi));
        return chosen;
    }

    private static boolean closer(AisPosition candidate, AisPosition current, Instant passStart) {
        Duration candidateGap = Duration.between(passStart, candidate.timestamp()).abs();
        Duration currentGap = Duration.between(passStart, current.timestamp()).abs();
        int comparison = candidateGap.compareTo(currentGap);
        return comparison < 0 || (comparison == 0 && candidate.timestamp().isAfter(current.timestamp()));
    }
}
