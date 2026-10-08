// tilesDir: writes one tile into a temp folder and points shipviewer.tiles-dir at it.
// anApiCallWithoutALoginGets401RatherThanARedirect: checks an API call without a login gets 401.
// anApiCallAcceptingAnythingStillGets401: checks an API call accepting any type still gets 401, not a redirect.
// openingThePageWithoutALoginIsSentToKeycloak: checks the page without a login redirects to Keycloak.
// theCsrfCookieIsHandedOutOnAnOrdinaryRequestAndAcceptedBackAsAHeader: checks the XSRF cookie works as a header.
// aHeaderThatDoesNotMatchTheCookieIsRefused: checks a CSRF header differing from the cookie gets 403.
// theLogoutFormsRawCookieValueIsAccepted: checks logout accepts the raw cookie value as its _csrf field.
// loggingOutEndsTheKeycloakSessionToo: checks logout redirects to Keycloak's end-session URL with a token hint.
// tilesAreCachedPrivatelyNowThatTheyNeedALogin: checks that a tile is served with private, immutable caching.
// theHeaderLearnsWhoIsLoggedInAndWhetherTheyAreAnAdmin: checks /api/me returns the name and admin flag.

package com.wisertech.shipviewer.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wisertech.shipviewer.config.SecurityConfig;
import com.wisertech.shipviewer.config.TestKeycloak;
import com.wisertech.shipviewer.job.service.JobRunner;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({JobController.class, AccountController.class})
@Import({SecurityConfig.class, TestKeycloak.class})
class LoginBehaviourTest {
    private static final SimpleGrantedAuthority USER = new SimpleGrantedAuthority("ROLE_user");
    private static final SimpleGrantedAuthority ADMIN = new SimpleGrantedAuthority("ROLE_admin");
    private static final String TILE = "/tiles/run/7/1/1.webp";

    @TempDir
    static Path tiles;

    @DynamicPropertySource
    static void tilesDir(DynamicPropertyRegistry registry) throws IOException {
        Path tile = tiles.resolve("run/7/1/1.webp");
        Files.createDirectories(tile.getParent());
        Files.write(tile, new byte[] {1, 2, 3});
        registry.add("shipviewer.tiles-dir", () -> tiles.toString());
    }

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JobRunner runner;

    @Test
    void anApiCallWithoutALoginGets401RatherThanARedirect() throws Exception {
        mvc.perform(get("/api/runs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anApiCallAcceptingAnythingStillGets401() throws Exception {
        mvc.perform(get("/api/runs").header("Accept", "*/*"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void openingThePageWithoutALoginIsSentToKeycloak() throws Exception {
        mvc.perform(get("/").accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/oauth2/authorization/keycloak"));
    }

    @Test
    void theCsrfCookieIsHandedOutOnAnOrdinaryRequestAndAcceptedBackAsAHeader() throws Exception {
        Cookie token = mvc.perform(get("/api/jobs").with(oidcLogin().authorities(USER, ADMIN)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("XSRF-TOKEN");

        mvc.perform(post("/api/jobs/search")
                        .with(oidcLogin().authorities(USER, ADMIN))
                        .cookie(token)
                        .header("X-XSRF-TOKEN", token.getValue()))
                .andExpect(status().isAccepted());
        verify(runner).start(any(), any());
    }

    @Test
    void aHeaderThatDoesNotMatchTheCookieIsRefused() throws Exception {
        Cookie token = mvc.perform(get("/api/jobs").with(oidcLogin().authorities(USER, ADMIN)))
                .andReturn().getResponse().getCookie("XSRF-TOKEN");

        mvc.perform(post("/api/jobs/search")
                        .with(oidcLogin().authorities(USER, ADMIN))
                        .cookie(token)
                        .header("X-XSRF-TOKEN", "not-the-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void theLogoutFormsRawCookieValueIsAccepted() throws Exception {
        Cookie token = mvc.perform(get("/api/jobs").with(oidcLogin().authorities(USER)))
                .andReturn().getResponse().getCookie("XSRF-TOKEN");

        mvc.perform(post("/logout")
                        .with(oidcLogin().clientRegistration(TestKeycloak.REGISTRATION).authorities(USER))
                        .cookie(token)
                        .param("_csrf", token.getValue()))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void loggingOutEndsTheKeycloakSessionToo() throws Exception {
        mvc.perform(post("/logout")
                        .with(csrf())
                        .with(oidcLogin().clientRegistration(TestKeycloak.REGISTRATION).authorities(USER)))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", startsWith(TestKeycloak.END_SESSION + "?")))
                .andExpect(header().string("Location", containsString("id_token_hint=")))
                .andExpect(header().string("Location", containsString("post_logout_redirect_uri=http://localhost/")));
    }

    @Test
    void tilesAreCachedPrivatelyNowThatTheyNeedALogin() throws Exception {
        mvc.perform(get(TILE).with(oidcLogin().authorities(USER)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("private")))
                .andExpect(header().string("Cache-Control", containsString("immutable")));
    }

    @Test
    void theHeaderLearnsWhoIsLoggedInAndWhetherTheyAreAnAdmin() throws Exception {
        mvc.perform(get("/api/me").with(oidcLogin()
                        .idToken(token -> token.subject("admin"))
                        .authorities(USER, ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("admin"))
                .andExpect(jsonPath("$.admin").value(true));

        mvc.perform(get("/api/me").with(oidcLogin()
                        .idToken(token -> token.subject("user"))
                        .authorities(USER)))
                .andExpect(jsonPath("$.name").value("user"))
                .andExpect(jsonPath("$.admin").value(false));
    }
}
