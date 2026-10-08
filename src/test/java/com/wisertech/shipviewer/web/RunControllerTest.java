// listsTheRuns: checks that the run list is served as JSON with id, counts and gap.
// showsOneRunAsGeoJson: checks one run is served with GeoJSON collections and no raster field when it has none.
// anUnknownRunIs404WithAMessage: checks that an unknown run id gets 404 with a "No run called" message.

package com.wisertech.shipviewer.web;

import static com.wisertech.shipviewer.Fixtures.PASS_START;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wisertech.shipviewer.ais.model.AisTiming;
import com.wisertech.shipviewer.config.SecurityConfig;
import com.wisertech.shipviewer.config.TestKeycloak;
import com.wisertech.shipviewer.run.model.Run;
import com.wisertech.shipviewer.run.model.RunLog;
import com.wisertech.shipviewer.run.model.RunSummary;
import com.wisertech.shipviewer.run.service.RunService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RunController.class)
@Import({SecurityConfig.class, TestKeycloak.class})
class RunControllerTest {
    private static final SimpleGrantedAuthority USER = new SimpleGrantedAuthority("ROLE_user");
    private static final String RUN_ID = "2026-10-02T155058Z_009708";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RunService runs;

    @Test
    void listsTheRuns() throws Exception {
        when(runs.listRuns()).thenReturn(List.of(
                new RunSummary(RUN_ID, PASS_START, null, false, 3, 158, 0, 3)));

        mvc.perform(get("/api/runs").with(oidcLogin().authorities(USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(RUN_ID))
                .andExpect(jsonPath("$[0].detectionCount").value(3))
                .andExpect(jsonPath("$[0].cfarRawCount").value(158))
                .andExpect(jsonPath("$[0].aisGapMinutes").isEmpty());
    }

    @Test
    void showsOneRunAsGeoJson() throws Exception {
        when(runs.findRun(RUN_ID)).thenReturn(new Run(RUN_ID, PASS_START, false,
                List.of(), List.of(), List.of(), new RunLog(PASS_START, 0),
                new AisTiming(null, null, null), null));

        mvc.perform(get("/api/runs/" + RUN_ID).with(oidcLogin().authorities(USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.id").value(RUN_ID))
                .andExpect(jsonPath("$.detections.type").value("FeatureCollection"))
                .andExpect(jsonPath("$.ais.features").isEmpty())
                .andExpect(jsonPath("$.raster").doesNotExist());
    }

    @Test
    void anUnknownRunIs404WithAMessage() throws Exception {
        mvc.perform(get("/api/runs/2020-01-01T000000Z_000001").with(oidcLogin().authorities(USER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No run called 2020-01-01T000000Z_000001."));
    }
}
