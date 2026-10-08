// securityFilterChain: requires the user role, logs in via Keycloak, gives /api 401s, and sets CSRF and logout.
// plainTokenOnEveryRequest: makes a CSRF handler taking the plain cookie token, resolved on every request.
// keycloakLogout: makes a logout handler that also ends the Keycloak session and returns to the site root.

package com.wisertech.shipviewer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    static final String POST_LOGOUT_REDIRECT = "{baseUrl}/";

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http, ClientRegistrationRepository registrations) throws Exception {
        http
                .authorizeHttpRequests(requests -> requests.anyRequest().hasRole("user"))
                .oauth2Login(login -> login
                        .userInfoEndpoint(userInfo -> userInfo
                                .userAuthoritiesMapper(new KeycloakRealmRoles())))
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                PathPatternRequestMatcher.withDefaults().matcher("/api/**")))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(plainTokenOnEveryRequest()))
                .logout(logout -> logout
                        .logoutSuccessHandler(keycloakLogout(registrations)));

        return http.build();
    }

    private static CsrfTokenRequestAttributeHandler plainTokenOnEveryRequest() {
        CsrfTokenRequestAttributeHandler handler = new CsrfTokenRequestAttributeHandler();
        handler.setCsrfRequestAttributeName(null);
        return handler;
    }

    private static OidcClientInitiatedLogoutSuccessHandler keycloakLogout(
            ClientRegistrationRepository registrations) {
        OidcClientInitiatedLogoutSuccessHandler handler =
                new OidcClientInitiatedLogoutSuccessHandler(registrations);
        handler.setPostLogoutRedirectUri(POST_LOGOUT_REDIRECT);
        return handler;
    }
}
