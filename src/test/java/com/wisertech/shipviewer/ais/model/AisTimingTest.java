// noPositionsMeanNoTiming: checks that no positions give a timing with every field null.
// theGapIsMeasuredToTheMedianReportInTenthsOfAMinute: checks earliest, latest and a median gap rounded to 0.1 min.
// withoutAnAcquisitionTimeThereIsNoGap: checks that a missing acquisition time leaves the gap null.

package com.wisertech.shipviewer.ais.model;

import static com.wisertech.shipviewer.Fixtures.PASS_START;
import static com.wisertech.shipviewer.Fixtures.position;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class AisTimingTest {
    @Test
    void noPositionsMeanNoTiming() {
        assertThat(AisTiming.of(List.of(), PASS_START)).isEqualTo(new AisTiming(null, null, null));
    }

    @Test
    void theGapIsMeasuredToTheMedianReportInTenthsOfAMinute() {
        AisTiming timing = AisTiming.of(List.of(
                position("3", PASS_START.plusSeconds(3600), 43, 29),
                position("1", PASS_START.minusSeconds(60), 43, 29),
                position("2", PASS_START.plusSeconds(100), 43, 29)), PASS_START);

        assertThat(timing.earliest()).isEqualTo(PASS_START.minusSeconds(60));
        assertThat(timing.latest()).isEqualTo(PASS_START.plusSeconds(3600));
        assertThat(timing.gapMinutes()).isEqualTo(1.7);
    }

    @Test
    void withoutAnAcquisitionTimeThereIsNoGap() {
        AisTiming timing = AisTiming.of(List.of(position("1", PASS_START, 43, 29)), null);

        assertThat(timing.earliest()).isEqualTo(PASS_START);
        assertThat(timing.gapMinutes()).isNull();
    }
}
