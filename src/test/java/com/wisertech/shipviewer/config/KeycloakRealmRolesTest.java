// realmRolesBecomePrefixedAuthorities: checks realm roles become ROLE_ authorities beside the original login.
// aTokenWithoutRolesGivesNoRoles: checks that an ID token without realm_access adds no ROLE_ authorities.
// authoritiesThatAreNotALoginPassThroughUnchanged: checks that a non-OIDC authority is passed through alone.
// loginWith: builds an OIDC login authority whose ID token carries the given extra claims.
// names: lists the authority names of a collection of granted authorities.

package com.wisertech.shipviewer.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

class KeycloakRealmRolesTest {
    private final KeycloakRealmRoles mapper = new KeycloakRealmRoles();

    @Test
    void realmRolesBecomePrefixedAuthorities() {
        OidcUserAuthority login = loginWith(Map.of("realm_access", Map.of("roles", List.of("user", "admin"))));

        assertThat(names(mapper.mapAuthorities(List.of(login))))
                .contains("ROLE_user", "ROLE_admin")
                .contains(login.getAuthority());
    }

    @Test
    void aTokenWithoutRolesGivesNoRoles() {
        OidcUserAuthority login = loginWith(Map.of());

        assertThat(names(mapper.mapAuthorities(List.of(login))))
                .noneMatch(name -> name.startsWith("ROLE_"));
    }

    @Test
    void authoritiesThatAreNotALoginPassThroughUnchanged() {
        GrantedAuthority scope = new SimpleGrantedAuthority("SCOPE_openid");

        assertThat(names(mapper.mapAuthorities(List.of(scope)))).containsExactly("SCOPE_openid");
    }

    private static OidcUserAuthority loginWith(Map<String, Object> extraClaims) {
        OidcIdToken token = OidcIdToken.withTokenValue("token")
                .subject("someone")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claims(claims -> claims.putAll(extraClaims))
                .build();
        return new OidcUserAuthority(token);
    }

    private static List<String> names(Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream().map(GrantedAuthority::getAuthority).toList();
    }
}
