// aUserCanSeeTheCurrentJob: checks that a user may read the current job.
// aUserCannotStartASearch: checks that a user's search request is refused with 403 and nothing starts.
// aUserCannotStartAPipelineRun: checks that a user's pipeline request is refused with 403 and nothing starts.
// anAdminCanStartASearch: checks that an admin's search request is accepted and starts a job.
// anAdminCanStartAPipelineRun: checks that an admin's pipeline request is accepted and starts a job.
// someoneLoggedInWithoutTheUserRoleSeesNothing: checks a login without the user role gets 403 on the jobs list.
// anAdminWithoutACsrfTokenCannotStartAnything: checks an admin POST without a CSRF token is refused with 403.

package com.wisertech.shipviewer.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wisertech.shipviewer.config.SecurityConfig;
import com.wisertech.shipviewer.config.TestKeycloak;
import com.wisertech.shipviewer.job.service.JobRunner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(JobController.class)
@Import({SecurityConfig.class, TestKeycloak.class})
class JobControllerSecurityTest {
    private static final SimpleGrantedAuthority USER = new SimpleGrantedAuthority("ROLE_user");
    private static final SimpleGrantedAuthority ADMIN = new SimpleGrantedAuthority("ROLE_admin");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JobRunner runner;

    @Test
    void aUserCanSeeTheCurrentJob() throws Exception {
        mvc.perform(get("/api/jobs").with(oidcLogin().authorities(USER)))
                .andExpect(status().isOk());
    }

    @Test
    void aUserCannotStartASearch() throws Exception {
        mvc.perform(post("/api/jobs/search").with(csrf()).with(oidcLogin().authorities(USER)))
                .andExpect(status().isForbidden());
        verify(runner, never()).start(any(), any());
    }

    @Test
    void aUserCannotStartAPipelineRun() throws Exception {
        mvc.perform(post("/api/jobs/pipeline").with(csrf()).with(oidcLogin().authorities(USER)))
                .andExpect(status().isForbidden());
        verify(runner, never()).start(any(), any());
    }

    @Test
    void anAdminCanStartASearch() throws Exception {
        mvc.perform(post("/api/jobs/search").with(csrf()).with(oidcLogin().authorities(USER, ADMIN)))
                .andExpect(status().isAccepted());
        verify(runner).start(any(), any());
    }

    @Test
    void anAdminCanStartAPipelineRun() throws Exception {
        mvc.perform(post("/api/jobs/pipeline").with(csrf()).with(oidcLogin().authorities(USER, ADMIN)))
                .andExpect(status().isAccepted());
        verify(runner).start(any(), any());
    }

    @Test
    void someoneLoggedInWithoutTheUserRoleSeesNothing() throws Exception {
        mvc.perform(get("/api/jobs").with(oidcLogin()))
                .andExpect(status().isForbidden());
    }

    @Test
    void anAdminWithoutACsrfTokenCannotStartAnything() throws Exception {
        mvc.perform(post("/api/jobs/pipeline").with(oidcLogin().authorities(USER, ADMIN)))
                .andExpect(status().isForbidden());
        verify(runner, never()).start(any(), any());
    }
}
