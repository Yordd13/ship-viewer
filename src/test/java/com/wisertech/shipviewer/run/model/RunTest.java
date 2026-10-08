// countsDetectionsWithAndWithoutATransponder: checks the counts of matched and unmatched detections.
// knowsWhichVesselsWereClaimed: checks that the claimed MMSIs are exactly those of matched detections.
// summarisesItself: checks that the summary carries the id, time, gap, counts and raw CFAR count.

package com.wisertech.shipviewer.run.model;

import static com.wisertech.shipviewer.Fixtures.PASS_START;
import static com.wisertech.shipviewer.Fixtures.detection;
import static org.assertj.core.api.Assertions.assertThat;

import com.wisertech.shipviewer.ais.model.AisTiming;
import com.wisertech.shipviewer.detection.model.Detection;
import com.wisertech.shipviewer.detection.model.MatchedDetection;
import java.util.List;
import org.junit.jupiter.api.Test;

class RunTest {
    private final Detection a = detection("a", 43.0, 29.0, PASS_START);
    private final Detection b = detection("b", 43.1, 29.1, PASS_START);
    private final Detection c = detection("c", 43.2, 29.2, PASS_START);

    private final Run run = new Run("2026-10-02T155058Z_009708", PASS_START, true,
            List.of(a, b, c), List.of(),
            List.of(new MatchedDetection(a, "207000001", 12.5),
                    new MatchedDetection(b, null, null),
                    new MatchedDetection(c, "207000002", 800.0)),
            new RunLog(PASS_START, 158), new AisTiming(PASS_START, PASS_START, 0.5), null);

    @Test
    void countsDetectionsWithAndWithoutATransponder() {
        assertThat(run.withTransponder()).isEqualTo(2);
        assertThat(run.withoutTransponder()).isEqualTo(1);
    }

    @Test
    void knowsWhichVesselsWereClaimed() {
        assertThat(run.claimedMmsis()).containsExactlyInAnyOrder("207000001", "207000002");
    }

    @Test
    void summarisesItself() {
        assertThat(run.summary()).isEqualTo(new RunSummary(
                "2026-10-02T155058Z_009708", PASS_START, 0.5, true, 3, 158, 2, 1));
    }
}
