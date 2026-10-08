// detectionsBecomeGeoJsonPointsLongitudeFirst: checks detections become longitude-first points with match fields.
// aisPositionsKnowWhetherTheirVesselWasClaimed: checks AIS features flag vessels a detection claimed.
// theSummaryAndAisTimingAreCarriedOver: checks the detail carries the summary, AIS times and no raster.
// run: builds a test run with one matched and one dark detection, two AIS positions and the given raster.
// features: returns the feature list of a GeoJSON FeatureCollection.
// geometry: returns the geometry map of a GeoJSON feature.
// properties: returns the properties map of a GeoJSON feature.

package com.wisertech.shipviewer.web.mapper;

import static com.wisertech.shipviewer.Fixtures.PASS_START;
import static com.wisertech.shipviewer.Fixtures.detection;
import static com.wisertech.shipviewer.Fixtures.position;
import static org.assertj.core.api.Assertions.assertThat;

import com.wisertech.shipviewer.ais.model.AisPosition;
import com.wisertech.shipviewer.ais.model.AisTiming;
import com.wisertech.shipviewer.detection.model.Detection;
import com.wisertech.shipviewer.detection.model.MatchedDetection;
import com.wisertech.shipviewer.raster.model.RasterSource;
import com.wisertech.shipviewer.run.model.Run;
import com.wisertech.shipviewer.run.model.RunLog;
import com.wisertech.shipviewer.web.dto.RunDetail;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RunPayloadsTest {
    private static final String RUN_ID = "2026-10-02T155058Z_009708";

    private final Detection ship = detection("slice_1_target_001", 43.25, 29.75, PASS_START);
    private final Detection dark = detection("slice_1_target_002", 42.10, 28.90, PASS_START);
    private final AisPosition claimed = position("207000001", PASS_START, 43.25, 29.75);
    private final AisPosition passing = position("207000002", PASS_START, 42.80, 28.10);

    @Test
    void detectionsBecomeGeoJsonPointsLongitudeFirst() {
        RunDetail detail = RunPayloads.detailOf(run(null));

        assertThat(detail.detections()).containsEntry("type", "FeatureCollection");
        Map<String, Object> first = features(detail.detections()).get(0);
        assertThat(first).containsEntry("type", "Feature");
        assertThat(geometry(first)).containsEntry("type", "Point")
                .containsEntry("coordinates", List.of(29.75, 43.25));
        assertThat(properties(first))
                .containsEntry("detectionId", "slice_1_target_001")
                .containsEntry("matched", true)
                .containsEntry("matchedMmsi", "207000001")
                .containsEntry("distanceMeters", 3.0)
                .containsEntry("distanceToShoreM", 5000.0);
        assertThat(properties(features(detail.detections()).get(1)))
                .containsEntry("matched", false)
                .containsEntry("matchedMmsi", null);
    }

    @Test
    void aisPositionsKnowWhetherTheirVesselWasClaimed() {
        RunDetail detail = RunPayloads.detailOf(run(null));

        List<Map<String, Object>> ais = features(detail.ais());
        assertThat(ais).hasSize(2);
        assertThat(properties(ais.get(0))).containsEntry("mmsi", "207000001")
                .containsEntry("matched", true).containsEntry("vesselName", "VESSEL 207000001");
        assertThat(properties(ais.get(1))).containsEntry("mmsi", "207000002")
                .containsEntry("matched", false);
        assertThat(geometry(ais.get(1))).containsEntry("coordinates", List.of(28.10, 42.80));
    }

    @Test
    void theSummaryAndAisTimingAreCarriedOver() {
        RunDetail detail = RunPayloads.detailOf(run(null));

        assertThat(detail.summary().id()).isEqualTo(RUN_ID);
        assertThat(detail.summary().withTransponder()).isEqualTo(1);
        assertThat(detail.aisEarliestTime()).isEqualTo(PASS_START.minusSeconds(30));
        assertThat(detail.aisLatestTime()).isEqualTo(PASS_START.plusSeconds(30));
        assertThat(detail.raster()).isNull();
    }

    private Run run(RasterSource raster) {
        return new Run(RUN_ID, PASS_START, true,
                List.of(ship, dark), List.of(claimed, passing),
                List.of(new MatchedDetection(ship, "207000001", 3.0), new MatchedDetection(dark, null, null)),
                new RunLog(PASS_START, 158),
                new AisTiming(PASS_START.minusSeconds(30), PASS_START.plusSeconds(30), 0.5),
                raster);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> features(Map<String, Object> collection) {
        return (List<Map<String, Object>>) collection.get("features");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> geometry(Map<String, Object> feature) {
        return (Map<String, Object>) feature.get("geometry");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> properties(Map<String, Object> feature) {
        return (Map<String, Object>) feature.get("properties");
    }
}
