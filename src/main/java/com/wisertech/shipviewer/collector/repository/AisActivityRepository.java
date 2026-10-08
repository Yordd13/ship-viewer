// lastPositionAt: returns the newest stored AIS position time as UTC, or null when none is stored.
// positionsSince: counts the AIS positions stored with a time at or after the given moment.

package com.wisertech.shipviewer.collector.repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AisActivityRepository {
    private final JdbcTemplate jdbc;

    public AisActivityRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Instant lastPositionAt() {
        LocalDateTime newest = jdbc.queryForObject(
                "SELECT MAX(ts) FROM ais_position", LocalDateTime.class);
        return newest == null ? null : newest.toInstant(ZoneOffset.UTC);
    }

    public long positionsSince(Instant moment) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ais_position WHERE ts >= ?", Long.class,
                LocalDateTime.ofInstant(moment, ZoneOffset.UTC));
        return count == null ? 0 : count;
    }
}
