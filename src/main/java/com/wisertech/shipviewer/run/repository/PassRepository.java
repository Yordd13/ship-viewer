// StoredPass.runId: builds the pass's run id from its start stamp and its orbit padded to six digits.
// findAll: reads every stored pass, newest start first.
// findByRunId: looks up the pass a run id names by its start time and orbit, or returns null.
// detectionsOf: reads a pass's detections with their slice, acquisition time, mission and the pass orbit.
// aisAround: reads the AIS positions stored within the window either side of a moment, by time then MMSI.
// rasterOf: reads the raster layer stored for a pass, or returns null when it has no picture.
// storedPass: maps a pass_run result row to a StoredPass.
// fromColumn: reads a DATETIME column as a UTC instant, or null.
// toColumn: converts an instant to the UTC LocalDateTime the database columns hold.
// orbitOf: parses the orbit number after the first underscore of a run id, or returns null.
// doubleOrNull(BigDecimal): converts a decimal to a double, keeping null as null.
// doubleOrNull(Long): converts a long to a double, keeping null as null.

package com.wisertech.shipviewer.run.repository;

import com.wisertech.shipviewer.ais.model.AisPosition;
import com.wisertech.shipviewer.detection.model.Detection;
import com.wisertech.shipviewer.raster.model.GeoBounds;
import com.wisertech.shipviewer.raster.model.RasterSource;
import com.wisertech.shipviewer.run.model.FilenameStamp;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PassRepository {
    private final JdbcTemplate jdbc;

    public PassRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record StoredPass(long id, Instant start, int orbit, String area, Integer cfarRawCount) {
        public String runId() {
            return FilenameStamp.format(start) + "_" + String.format("%06d", orbit);
        }
    }

    public List<StoredPass> findAll() {
        return jdbc.query("""
                SELECT id, pass_start, absolute_orbit, area, cfar_detections_raw
                  FROM pass_run
                 ORDER BY pass_start DESC
                """, (row, n) -> storedPass(row));
    }

    public StoredPass findByRunId(String runId) {
        Instant start = FilenameStamp.parseOrNull(runId);
        Integer orbit = orbitOf(runId);
        if (start == null || orbit == null) {
            return null;
        }
        List<StoredPass> found = jdbc.query("""
                SELECT id, pass_start, absolute_orbit, area, cfar_detections_raw
                  FROM pass_run
                 WHERE pass_start = ? AND absolute_orbit = ?
                """, (row, n) -> storedPass(row), toColumn(start), orbit);
        return found.isEmpty() ? null : found.get(0);
    }

    public List<Detection> detectionsOf(StoredPass pass) {
        String orbit = String.format("%06d", pass.orbit());
        return jdbc.query("""
                SELECT d.detection_id, d.latitude, d.longitude, d.width_m, d.length_m,
                       d.distance_to_shore_m, s.slice_index, s.acquisition_start, s.mission
                  FROM detection d
                  JOIN pass_slice s ON s.id = d.pass_slice_id
                 WHERE s.pass_run_id = ?
                 ORDER BY s.slice_index, d.detection_id
                """, (row, n) -> new Detection(
                row.getString("detection_id"),
                row.getDouble("latitude"),
                row.getDouble("longitude"),
                doubleOrNull(row.getBigDecimal("width_m")),
                doubleOrNull(row.getBigDecimal("length_m")),
                "slice_" + row.getInt("slice_index"),
                fromColumn(row, "acquisition_start"),
                row.getString("mission"),
                orbit,
                doubleOrNull(row.getObject("distance_to_shore_m", Long.class))), pass.id());
    }

    public List<AisPosition> aisAround(Instant moment, Duration window) {
        return jdbc.query("""
                SELECT mmsi, ts, latitude, longitude, sog, cog, name
                  FROM ais_position
                 WHERE ts BETWEEN ? AND ?
                 ORDER BY ts, mmsi
                """, (row, n) -> {
            Instant reported = fromColumn(row, "ts");
            return new AisPosition(
                    row.getString("mmsi"),
                    reported,
                    reported.getNano() != 0,
                    row.getDouble("latitude"),
                    row.getDouble("longitude"),
                    doubleOrNull(row.getBigDecimal("sog")),
                    doubleOrNull(row.getBigDecimal("cog")),
                    row.getString("name"));
        }, toColumn(moment.minus(window)), toColumn(moment.plus(window)));
    }

    public RasterSource rasterOf(StoredPass pass) {
        List<RasterSource> found = jdbc.query("""
                SELECT type, file, bound_south, bound_west, bound_north, bound_east,
                       max_native_zoom, max_zoom
                  FROM raster_layer
                 WHERE pass_run_id = ?
                """, (row, n) -> new RasterSource(
                row.getString("type"),
                row.getString("file"),
                new GeoBounds(row.getDouble("bound_south"), row.getDouble("bound_west"),
                        row.getDouble("bound_north"), row.getDouble("bound_east")),
                row.getObject("max_native_zoom", Integer.class),
                row.getObject("max_zoom", Integer.class)), pass.id());
        return found.isEmpty() ? null : found.get(0);
    }

    private static StoredPass storedPass(ResultSet row) throws SQLException {
        return new StoredPass(
                row.getLong("id"),
                fromColumn(row, "pass_start"),
                row.getInt("absolute_orbit"),
                row.getString("area"),
                row.getObject("cfar_detections_raw", Integer.class));
    }

    private static Instant fromColumn(ResultSet row, String column) throws SQLException {
        LocalDateTime value = row.getObject(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private static LocalDateTime toColumn(Instant moment) {
        return LocalDateTime.ofInstant(moment, ZoneOffset.UTC);
    }

    private static Integer orbitOf(String runId) {
        int separator = runId.indexOf('_');
        if (separator < 0) {
            return null;
        }
        try {
            return Integer.valueOf(runId.substring(separator + 1));
        } catch (NumberFormatException notAnOrbit) {
            return null;
        }
    }

    private static Double doubleOrNull(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    private static Double doubleOrNull(Long value) {
        return value == null ? null : value.doubleValue();
    }
}
