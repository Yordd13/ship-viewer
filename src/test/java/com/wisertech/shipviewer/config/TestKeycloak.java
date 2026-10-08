// clientRegistrationRepository: provides an in-memory repository holding the hand-written Keycloak registration.

package com.wisertech.shipviewer.config;

import java.util.Map;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

@TestConfiguration
public class TestKeycloak {
    public static final String END_SESSION = "http://keycloak.test/logout";

    public static final ClientRegistration REGISTRATION = ClientRegistration
            .withRegistrationId("keycloak")
            .clientId("ship-viewer")
            .clientSecret("test-secret")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .scope("openid", "profile", "email")
            .authorizationUri("http://keycloak.test/auth")
            .tokenUri("http://keycloak.test/token")
            .jwkSetUri("http://keycloak.test/certs")
            .userNameAttributeName("preferred_username")
            .providerConfigurationMetadata(Map.of("end_session_endpoint", END_SESSION))
            .build();

    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
        return new InMemoryClientRegistrationRepository(REGISTRATION);
    }
}
