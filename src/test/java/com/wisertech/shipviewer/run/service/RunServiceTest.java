// aRunIsAssembledFromItsPassDetectionsAisAndPicture: checks a run joins pass, detections, AIS and raster.
// aPassWhoseDetectionsHaveNoTimeIsDatedByItsStart: checks a run whose detections lack times takes the pass start.
// anUnknownRunIsNull: checks that a run id with no stored pass gives null.
// runsAreListedNewestFirst: checks that run summaries are listed newest first.

package com.wisertech.shipviewer.run.service;

import static com.wisertech.shipviewer.Fixtures.PASS_START;
import static com.wisertech.shipviewer.Fixtures.detection;
import static com.wisertech.shipviewer.Fixtures.position;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wisertech.shipviewer.Fixtures;
import com.wisertech.shipviewer.ais.model.AisPosition;
import com.wisertech.shipviewer.detection.model.Detection;
import com.wisertech.shipviewer.match.service.TransponderMatcher;
import com.wisertech.shipviewer.raster.model.GeoBounds;
import com.wisertech.shipviewer.raster.model.RasterSource;
import com.wisertech.shipviewer.run.model.Run;
import com.wisertech.shipviewer.run.model.RunSummary;
import com.wisertech.shipviewer.run.repository.PassRepository;
import com.wisertech.shipviewer.run.repository.PassRepository.StoredPass;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class RunServiceTest {
    private final PassRepository passes = mock(PassRepository.class);
    private final RunService service =
            new RunService(passes, new TransponderMatcher(Fixtures.properties()), Fixtures.properties());

    private final StoredPass pass = new StoredPass(16, PASS_START, 9708, "bulgaria", 158);

    @Test
    void aRunIsAssembledFromItsPassDetectionsAisAndPicture() {
        Instant secondSlice = PASS_START.plusSeconds(25);
        Detection matched = detection("slice_2_target_030", 43.00, 29.00, secondSlice);
        Detection dark = detection("slice_1_target_001", 42.50, 28.50, PASS_START.plusSeconds(1));
        AisPosition stale = position("207000001", PASS_START.minusSeconds(800), 43.00, 29.00);
        AisPosition fresh = position("207000001", PASS_START.plusSeconds(10), 43.001, 29.00);
        RasterSource raster = new RasterSource("tiles", "run/{z}/{x}/{y}.webp",
                new GeoBounds(41, 28, 44, 32), 12, 16);

        when(passes.findByRunId("2026-10-02T155058Z_009708")).thenReturn(pass);
        when(passes.detectionsOf(pass)).thenReturn(List.of(matched, dark));
        when(passes.aisAround(PASS_START, Duration.ofMinutes(15))).thenReturn(List.of(stale, fresh));
        when(passes.rasterOf(pass)).thenReturn(raster);

        Run run = service.findRun("2026-10-02T155058Z_009708");

        assertThat(run.id()).isEqualTo("2026-10-02T155058Z_009708");
        assertThat(run.acquisitionTime()).isEqualTo(PASS_START.plusSeconds(1));
        assertThat(run.hasAis()).isTrue();
        assertThat(run.aisPositions()).containsExactly(fresh);
        assertThat(run.withTransponder()).isEqualTo(1);
        assertThat(run.claimedMmsis()).containsExactly("207000001");
        assertThat(run.log().cfarRawCount()).isEqualTo(158);
        assertThat(run.log().passStart()).isEqualTo(PASS_START);
        assertThat(run.timing().earliest()).isEqualTo(PASS_START.plusSeconds(10));
        assertThat(run.raster()).isSameAs(raster);
    }

    @Test
    void aPassWhoseDetectionsHaveNoTimeIsDatedByItsStart() {
        when(passes.findByRunId("2026-10-02T155058Z_009708")).thenReturn(pass);
        when(passes.detectionsOf(pass)).thenReturn(List.of(
                new Detection("x", 43, 29, null, null, "slice_1", null, "S1C", "009708", null)));
        when(passes.aisAround(any(), any())).thenReturn(List.of());

        Run run = service.findRun("2026-10-02T155058Z_009708");

        assertThat(run.acquisitionTime()).isEqualTo(PASS_START);
        assertThat(run.hasAis()).isFalse();
        assertThat(run.timing().gapMinutes()).isNull();
        assertThat(run.raster()).isNull();
    }

    @Test
    void anUnknownRunIsNull() {
        assertThat(service.findRun("2026-10-02T155058Z_009708")).isNull();
    }

    @Test
    void runsAreListedNewestFirst() {
        StoredPass older = new StoredPass(15, PASS_START.minusSeconds(86_400), 9693, "bulgaria", 10);
        when(passes.findAll()).thenReturn(List.of(older, pass));
        when(passes.aisAround(any(), any())).thenReturn(List.of());

        assertThat(service.listRuns()).extracting(RunSummary::id)
                .containsExactly("2026-10-02T155058Z_009708", "2026-10-01T155058Z_009693");
    }
}
