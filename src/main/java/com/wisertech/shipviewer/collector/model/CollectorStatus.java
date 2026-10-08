// CollectorStatus: what the page shows about the AIS collector: its state, process, newest position and log.

package com.wisertech.shipviewer.collector.model;

import java.time.Instant;
import java.util.List;

public record CollectorStatus(
        String state,
        Long pid,
        Instant startedAt,
        Instant lastPositionAt,
        Long positionsLastHour,
        List<String> log) {
    public static final String RUNNING = "running";
    public static final String STARTING = "starting";
    public static final String STOPPED = "stopped";
}
