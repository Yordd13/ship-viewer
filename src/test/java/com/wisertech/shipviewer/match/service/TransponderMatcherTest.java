// aVesselInsideTheRadiusIsTheDetectionsTransponder: checks a vessel 1112 m away is matched with its MMSI.
// aVesselBeyondTheRadiusLeavesTheDetectionDark: checks a vessel beyond 2000 m leaves the detection unmatched.
// aVesselIsClaimedByOnlyTheNearestDetection: checks that only the nearer of two detections gets the vessel.
// aDetectionWhoseNearestVesselIsTakenFallsBackToTheNextFreeOne: checks a detection takes the next free vessel.
// severalReportsOfOneVesselAreStillOneVessel: checks that two reports of one MMSI match only one detection.
// noAisMeansEveryDetectionIsDark: checks that with no AIS positions no detection is matched.

package com.wisertech.shipviewer.match.service;

import static com.wisertech.shipviewer.Fixtures.PASS_START;
import static com.wisertech.shipviewer.Fixtures.detection;
import static com.wisertech.shipviewer.Fixtures.position;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.wisertech.shipviewer.Fixtures;
import com.wisertech.shipviewer.detection.model.Detection;
import com.wisertech.shipviewer.detection.model.MatchedDetection;
import java.util.List;
import org.junit.jupiter.api.Test;

class TransponderMatcherTest {
    private final TransponderMatcher matcher = new TransponderMatcher(Fixtures.properties());

    @Test
    void aVesselInsideTheRadiusIsTheDetectionsTransponder() {
        Detection ship = detection("slice_1_target_001", 43.00, 29.00, PASS_START);

        List<MatchedDetection> matched =
                matcher.match(List.of(ship), List.of(position("207000001", PASS_START, 43.01, 29.00)));

        assertThat(matched).singleElement().satisfies(candidate -> {
            assertThat(candidate.detection()).isSameAs(ship);
            assertThat(candidate.matched()).isTrue();
            assertThat(candidate.matchedMmsi()).isEqualTo("207000001");
            assertThat(candidate.distanceMeters()).isCloseTo(1112.0, within(1.0));
        });
    }

    @Test
    void aVesselBeyondTheRadiusLeavesTheDetectionDark() {
        List<MatchedDetection> matched = matcher.match(
                List.of(detection("slice_1_target_001", 43.00, 29.00, PASS_START)),
                List.of(position("207000001", PASS_START, 43.02, 29.00)));

        assertThat(matched).singleElement().satisfies(candidate -> {
            assertThat(candidate.matched()).isFalse();
            assertThat(candidate.matchedMmsi()).isNull();
            assertThat(candidate.distanceMeters()).isNull();
        });
    }

    @Test
    void aVesselIsClaimedByOnlyTheNearestDetection() {
        List<MatchedDetection> matched = matcher.match(
                List.of(detection("far", 43.010, 29.00, PASS_START),
                        detection("near", 43.001, 29.00, PASS_START)),
                List.of(position("207000001", PASS_START, 43.00, 29.00)));

        assertThat(matched).extracting(MatchedDetection::matchedMmsi).containsExactly(null, "207000001");
    }

    @Test
    void aDetectionWhoseNearestVesselIsTakenFallsBackToTheNextFreeOne() {
        List<MatchedDetection> matched = matcher.match(
                List.of(detection("first", 43.000, 29.00, PASS_START),
                        detection("second", 43.002, 29.00, PASS_START)),
                List.of(position("207000001", PASS_START, 43.0001, 29.00),
                        position("207000002", PASS_START, 43.010, 29.00)));

        assertThat(matched).extracting(MatchedDetection::matchedMmsi)
                .containsExactly("207000001", "207000002");
    }

    @Test
    void severalReportsOfOneVesselAreStillOneVessel() {
        List<MatchedDetection> matched = matcher.match(
                List.of(detection("first", 43.000, 29.00, PASS_START),
                        detection("second", 43.005, 29.00, PASS_START)),
                List.of(position("207000001", PASS_START, 43.000, 29.00),
                        position("207000001", PASS_START.plusSeconds(60), 43.005, 29.00)));

        assertThat(matched).extracting(MatchedDetection::matchedMmsi).containsOnlyOnce("207000001");
        assertThat(matched).filteredOn(MatchedDetection::matched).hasSize(1);
    }

    @Test
    void noAisMeansEveryDetectionIsDark() {
        List<MatchedDetection> matched = matcher.match(
                List.of(detection("a", 43.0, 29.0, PASS_START), detection("b", 43.1, 29.1, PASS_START)),
                List.of());

        assertThat(matched).hasSize(2).noneMatch(MatchedDetection::matched);
    }
}
