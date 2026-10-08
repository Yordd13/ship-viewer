// of: finds the earliest and latest report times and the minutes from the median report to the pass.

package com.wisertech.shipviewer.ais.model;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public record AisTiming(Instant earliest, Instant latest, Double gapMinutes) {
    public static final double MEANINGFUL_GAP_MINUTES = 30.0;

    public static AisTiming of(List<AisPosition> positions, Instant acquisitionTime) {
        if (positions.isEmpty()) {
            return new AisTiming(null, null, null);
        }

        List<Instant> timestamps = new ArrayList<>(positions.size());
        for (AisPosition position : positions) {
            timestamps.add(position.timestamp());
        }
        timestamps.sort(Comparator.naturalOrder());

        Instant median = timestamps.get(timestamps.size() / 2);

        Double gapMinutes = null;
        if (acquisitionTime != null) {
            long seconds = Math.abs(Duration.between(acquisitionTime, median).getSeconds());
            gapMinutes = Math.round(seconds / 60.0 * 10.0) / 10.0;
        }
        return new AisTiming(timestamps.get(0), timestamps.get(timestamps.size() - 1),
                gapMinutes);
    }
}
