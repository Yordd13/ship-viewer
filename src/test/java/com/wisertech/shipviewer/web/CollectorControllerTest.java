// aUserCanSeeTheCollector: checks that a user gets the collector status as JSON.
// aUserCannotStartIt: checks that a user's start request is refused with 403 and nothing starts.
// anAdminCanStartIt: checks that an admin's start request is accepted with 202 and the status.
// aSecondCollectorIs409: checks that starting while one runs answers 409 with the exception's message.

package com.wisertech.shipviewer.web;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wisertech.shipviewer.collector.model.CollectorStatus;
import com.wisertech.shipviewer.collector.service.CollectorRunner;
import com.wisertech.shipviewer.config.SecurityConfig;
import com.wisertech.shipviewer.config.TestKeycloak;
import com.wisertech.shipviewer.exception.CollectorAlreadyRunningException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CollectorController.class)
@Import({SecurityConfig.class, TestKeycloak.class})
class CollectorControllerTest {
    private static final SimpleGrantedAuthority USER = new SimpleGrantedAuthority("ROLE_user");
    private static final SimpleGrantedAuthority ADMIN = new SimpleGrantedAuthority("ROLE_admin");

    private static final CollectorStatus RUNNING = new CollectorStatus(CollectorStatus.RUNNING, 4242L,
            Instant.parse("2026-10-06T10:00:00Z"), Instant.parse("2026-10-06T11:59:00Z"), 1234L,
            List.of("$ go run ./cmd/aiscollect"));

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CollectorRunner collector;

    @Test
    void aUserCanSeeTheCollector() throws Exception {
        when(collector.status()).thenReturn(RUNNING);

        mvc.perform(get("/api/collector").with(oidcLogin().authorities(USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("running"))
                .andExpect(jsonPath("$.pid").value(4242))
                .andExpect(jsonPath("$.positionsLastHour").value(1234))
                .andExpect(jsonPath("$.log[0]").value("$ go run ./cmd/aiscollect"));
    }

    @Test
    void aUserCannotStartIt() throws Exception {
        mvc.perform(post("/api/collector/start").with(csrf()).with(oidcLogin().authorities(USER)))
                .andExpect(status().isForbidden());
        verify(collector, never()).start();
    }

    @Test
    void anAdminCanStartIt() throws Exception {
        when(collector.start()).thenReturn(RUNNING);

        mvc.perform(post("/api/collector/start").with(csrf()).with(oidcLogin().authorities(USER, ADMIN)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.state").value("running"));
    }

    @Test
    void aSecondCollectorIs409() throws Exception {
        when(collector.start()).thenThrow(new CollectorAlreadyRunningException("already running"));

        mvc.perform(post("/api/collector/start").with(csrf()).with(oidcLogin().authorities(USER, ADMIN)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("already running"));
    }
}
