// idle: returns the snapshot of a runner that has not run any job, with an empty log.

package com.wisertech.shipviewer.job.model;

import java.time.Instant;
import java.util.List;

public record JobSnapshot(
        String kind,
        String state,
        Instant startedAt,
        Instant finishedAt,
        Integer exitCode,
        String failure,
        List<String> log,
        String partialLine) {
    public static JobSnapshot idle() {
        return new JobSnapshot(null, "idle", null, null, null, null, List.of(), "");
    }
}
