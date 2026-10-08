// me: returns the logged-in user's name and whether they hold the ROLE_admin authority.

package com.wisertech.shipviewer.web;

import com.wisertech.shipviewer.web.dto.Account;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AccountController {
    static final String ADMIN_AUTHORITY = "ROLE_admin";

    @GetMapping("/api/me")
    public Account me(Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> ADMIN_AUTHORITY.equals(authority.getAuthority()));
        return new Account(authentication.getName(), admin);
    }
}
