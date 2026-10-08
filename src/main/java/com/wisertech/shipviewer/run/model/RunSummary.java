// RunSummary: one run's list line: id, time, AIS gap, detection counts and transponder split.

package com.wisertech.shipviewer.run.model;

import java.time.Instant;

public record RunSummary(
        String id,
        Instant acquisitionTime,
        Double aisGapMinutes,
        boolean hasAis,
        int detectionCount,
        Integer cfarRawCount,
        int withTransponder,
        int withoutTransponder) {
}
