// mapAuthorities: keeps the given authorities and adds a ROLE_ authority for each realm role in OIDC ID tokens.
// rolesFrom: turns the roles list of a realm_access claim into ROLE_-prefixed authorities, or none.

package com.wisertech.shipviewer.config;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

public class KeycloakRealmRoles implements GrantedAuthoritiesMapper {
    static final String REALM_ACCESS_CLAIM = "realm_access";
    static final String ROLES_KEY = "roles";
    static final String ROLE_PREFIX = "ROLE_";

    @Override
    public Collection<? extends GrantedAuthority> mapAuthorities(
            Collection<? extends GrantedAuthority> authorities) {
        Set<GrantedAuthority> mapped = new HashSet<>(authorities);
        for (GrantedAuthority authority : authorities) {
            if (authority instanceof OidcUserAuthority oidc) {
                mapped.addAll(rolesFrom(oidc.getIdToken().getClaimAsMap(REALM_ACCESS_CLAIM)));
            }
        }
        return mapped;
    }

    private static List<GrantedAuthority> rolesFrom(Map<String, Object> realmAccess) {
        if (realmAccess == null || !(realmAccess.get(ROLES_KEY) instanceof Collection<?> roles)) {
            return List.of();
        }
        return roles.stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(ROLE_PREFIX + role))
                .toList();
    }
}
