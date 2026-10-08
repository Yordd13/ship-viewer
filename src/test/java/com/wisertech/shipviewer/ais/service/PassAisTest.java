// eachVesselKeepsOnlyItsReportNearestThePass: checks that only a vessel's report nearest the pass start is kept.
// onATieTheLaterReportWins: checks that of two equally near reports the later wins, in either input order.
// theResultIsOrderedByTimeThenMmsi: checks that the chosen reports are sorted by time, then by MMSI.
// noPositionsGiveNoPositions: checks that an empty input gives an empty result.

package com.wisertech.shipviewer.ais.service;

import static com.wisertech.shipviewer.Fixtures.PASS_START;
import static com.wisertech.shipviewer.Fixtures.position;
import static org.assertj.core.api.Assertions.assertThat;

import com.wisertech.shipviewer.ais.model.AisPosition;
import java.util.List;
import org.junit.jupiter.api.Test;

class PassAisTest {
    @Test
    void eachVesselKeepsOnlyItsReportNearestThePass() {
        AisPosition early = position("207000001", PASS_START.minusSeconds(600), 43.0, 29.0);
        AisPosition close = position("207000001", PASS_START.plusSeconds(30), 43.1, 29.1);
        AisPosition late = position("207000001", PASS_START.plusSeconds(400), 43.2, 29.2);

        assertThat(PassAis.nearestPerVessel(List.of(early, close, late), PASS_START))
                .containsExactly(close);
    }

    @Test
    void onATieTheLaterReportWins() {
        AisPosition before = position("207000001", PASS_START.minusSeconds(60), 43.0, 29.0);
        AisPosition after = position("207000001", PASS_START.plusSeconds(60), 43.1, 29.1);

        assertThat(PassAis.nearestPerVessel(List.of(after, before), PASS_START)).containsExactly(after);
        assertThat(PassAis.nearestPerVessel(List.of(before, after), PASS_START)).containsExactly(after);
    }

    @Test
    void theResultIsOrderedByTimeThenMmsi() {
        AisPosition b = position("207000002", PASS_START, 43.0, 29.0);
        AisPosition a = position("207000001", PASS_START, 43.0, 29.0);
        AisPosition first = position("207000009", PASS_START.minusSeconds(5), 43.0, 29.0);

        assertThat(PassAis.nearestPerVessel(List.of(b, a, first), PASS_START))
                .containsExactly(first, a, b);
    }

    @Test
    void noPositionsGiveNoPositions() {
        assertThat(PassAis.nearestPerVessel(List.of(), PASS_START)).isEmpty();
    }
}
