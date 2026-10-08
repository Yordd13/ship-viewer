// theRunIdIsTheStampAndTheZeroPaddedOrbit: checks the run id is the start stamp plus the six-digit orbit.
// findAllReadsTheColumnsAsUtc: checks that pass rows map to stored passes with UTC starts and nullable counts.
// findByRunIdLooksUpTheStartAndOrbit: checks a run id is looked up by its start column and orbit.
// anUnknownRunIdIsNull: checks that a run id matching no row gives null.
// aRunIdThatCannotBeParsedIsNullWithoutAsking: checks unparseable run ids give null without any query.
// detectionsCarryTheirSliceAndThePassOrbit: checks detection rows map with slice name, orbit and null sizes kept.
// aisIsReadFromEitherSideOfTheMoment: checks AIS is queried either side of the moment, flagging sub-seconds.
// passRow: builds a pass_run row with the given id and raw CFAR count at the test pass start.
// detectionRow: builds a detection row joined with its slice, with fixed position, size and shore distance.
// aisRow: builds an ais_position row for an MMSI at the given time, with fixed position, speed and name.
// ScriptedJdbc.answer: registers the rows to answer queries whose SQL contains the given FROM clause.
// ScriptedJdbc.lastArguments: returns the arguments of the most recent query.
// ScriptedJdbc.query(String, RowMapper): answers an argument-free query through the scripted rows.
// ScriptedJdbc.query(String, RowMapper, Object...): records the arguments and maps the rows scripted for the SQL.
// ScriptedJdbc.resultSet: mocks a ResultSet over one row, answering the getters PassRepository uses.

package com.wisertech.shipviewer.run.repository;

import static com.wisertech.shipviewer.Fixtures.PASS_START;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.wisertech.shipviewer.ais.model.AisPosition;
import com.wisertech.shipviewer.detection.model.Detection;
import com.wisertech.shipviewer.run.repository.PassRepository.StoredPass;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class PassRepositoryTest {
    private static final LocalDateTime START_COLUMN = LocalDateTime.of(2026, 10, 2, 15, 50, 58);

    private final ScriptedJdbc jdbc = new ScriptedJdbc();
    private final PassRepository repository = new PassRepository(jdbc);

    private final StoredPass pass = new StoredPass(16, PASS_START, 9708, "bulgaria", 158);

    @Test
    void theRunIdIsTheStampAndTheZeroPaddedOrbit() {
        assertThat(pass.runId()).isEqualTo("2026-10-02T155058Z_009708");
    }

    @Test
    void findAllReadsTheColumnsAsUtc() {
        jdbc.answer("FROM pass_run", passRow(16, 158), passRow(15, null));

        assertThat(repository.findAll()).containsExactly(
                pass, new StoredPass(15, PASS_START, 9708, "bulgaria", null));
    }

    @Test
    void findByRunIdLooksUpTheStartAndOrbit() {
        jdbc.answer("FROM pass_run", passRow(16, 158));

        assertThat(repository.findByRunId("2026-10-02T155058Z_009708")).isEqualTo(pass);
        assertThat(jdbc.lastArguments()).containsExactly(START_COLUMN, 9708);
    }

    @Test
    void anUnknownRunIdIsNull() {
        jdbc.answer("FROM pass_run");

        assertThat(repository.findByRunId("2026-10-02T155058Z_009708")).isNull();
    }

    @Test
    void aRunIdThatCannotBeParsedIsNullWithoutAsking() {
        assertThat(repository.findByRunId("nonsense")).isNull();
        assertThat(repository.findByRunId("2026-10-02T155058Z")).isNull();
        assertThat(repository.findByRunId("2026-10-02T155058Z_abc")).isNull();
        assertThat(jdbc.queries).isEmpty();
    }

    @Test
    void detectionsCarryTheirSliceAndThePassOrbit() {
        Map<String, Object> measured = detectionRow("slice_2_target_030");
        Map<String, Object> unmeasured = detectionRow("slice_2_target_031");
        unmeasured.put("distance_to_shore_m", null);
        unmeasured.put("width_m", null);
        jdbc.answer("FROM detection", measured, unmeasured);

        List<Detection> detections = repository.detectionsOf(pass);

        assertThat(detections).containsExactly(
                new Detection("slice_2_target_030", 43.17755, 29.559759, 60.0, 70.0, "slice_2",
                        PASS_START.plusSeconds(25), "S1C", "009708", 86253.0),
                new Detection("slice_2_target_031", 43.17755, 29.559759, null, 70.0, "slice_2",
                        PASS_START.plusSeconds(25), "S1C", "009708", null));
        assertThat(jdbc.lastArguments()).containsExactly(16L);
    }

    @Test
    void aisIsReadFromEitherSideOfTheMoment() {
        Map<String, Object> whole = aisRow("207000001", START_COLUMN);
        Map<String, Object> fraction = aisRow("207000002", START_COLUMN.plusNanos(250_000_000));
        fraction.put("sog", null);
        fraction.put("cog", null);
        fraction.put("name", null);
        jdbc.answer("FROM ais_position", whole, fraction);

        List<AisPosition> positions = repository.aisAround(PASS_START, Duration.ofMinutes(15));

        assertThat(positions).containsExactly(
                new AisPosition("207000001", PASS_START, false, 43.1, 29.2, 11.5, 270.0, "ODESSA STAR"),
                new AisPosition("207000002", PASS_START.plusMillis(250), true, 43.1, 29.2, null, null, null));
        assertThat(jdbc.lastArguments())
                .containsExactly(START_COLUMN.minusMinutes(15), START_COLUMN.plusMinutes(15));
    }

    private static Map<String, Object> passRow(long id, Integer raw) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", id);
        row.put("pass_start", START_COLUMN);
        row.put("absolute_orbit", 9708);
        row.put("area", "bulgaria");
        row.put("cfar_detections_raw", raw);
        return row;
    }

    private static Map<String, Object> detectionRow(String id) {
        Map<String, Object> row = new HashMap<>();
        row.put("detection_id", id);
        row.put("latitude", 43.17755);
        row.put("longitude", 29.559759);
        row.put("width_m", new BigDecimal("60.0"));
        row.put("length_m", new BigDecimal("70.0"));
        row.put("distance_to_shore_m", 86253L);
        row.put("slice_index", 2);
        row.put("acquisition_start", START_COLUMN.plusSeconds(25));
        row.put("mission", "S1C");
        return row;
    }

    private static Map<String, Object> aisRow(String mmsi, LocalDateTime ts) {
        Map<String, Object> row = new HashMap<>();
        row.put("mmsi", mmsi);
        row.put("ts", ts);
        row.put("latitude", 43.1);
        row.put("longitude", 29.2);
        row.put("sog", new BigDecimal("11.5"));
        row.put("cog", new BigDecimal("270.0"));
        row.put("name", "ODESSA STAR");
        return row;
    }

    private static final class ScriptedJdbc extends JdbcTemplate {
        private final Map<String, List<Map<String, Object>>> rowsByTable = new LinkedHashMap<>();
        private final List<Object[]> queries = new ArrayList<>();

        @SafeVarargs
        final void answer(String fromClause, Map<String, Object>... rows) {
            rowsByTable.put(fromClause, List.of(rows));
        }

        Object[] lastArguments() {
            return queries.get(queries.size() - 1);
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> mapper) {
            return query(sql, mapper, new Object[0]);
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> mapper, Object... arguments) {
            queries.add(arguments);
            List<Map<String, Object>> rows = rowsByTable.entrySet().stream()
                    .filter(entry -> sql.contains(entry.getKey()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Unexpected query: " + sql))
                    .getValue();
            List<T> mapped = new ArrayList<>();
            try {
                for (int index = 0; index < rows.size(); index++) {
                    mapped.add(mapper.mapRow(resultSet(rows.get(index)), index));
                }
            } catch (SQLException impossible) {
                throw new AssertionError(impossible);
            }
            return mapped;
        }

        private static ResultSet resultSet(Map<String, Object> row) {
            return mock(ResultSet.class, invocation -> {
                String column = invocation.getArgument(0);
                if (!row.containsKey(column)) {
                    throw new AssertionError("No column " + column);
                }
                Object value = row.get(column);
                return switch (invocation.getMethod().getName()) {
                    case "getString" -> (String) value;
                    case "getDouble" -> ((Number) value).doubleValue();
                    case "getInt" -> ((Number) value).intValue();
                    case "getLong" -> ((Number) value).longValue();
                    case "getBigDecimal" -> (BigDecimal) value;
                    case "getObject" -> value;
                    default -> throw new AssertionError("Unexpected " + invocation.getMethod());
                };
            });
        }
    }
}
