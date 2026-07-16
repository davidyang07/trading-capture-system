package com.tradingcapture.account.security;

import com.tradingcapture.account.domain.service.AccessContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Derives the domain {@link AccessContext} from the verified JWT authentication.
 *
 * <p>Owner subject resolution: {@code preferred_username} when present — it is deterministic
 * across realm re-imports and matches the dev-realm usernames and the seed data — otherwise
 * the token's {@code sub}. Nothing here ever reads identity from request content.</p>
 */
@Component
public class AccessContextFactory {

    public AccessContext from(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            throw new IllegalStateException(
                    "Expected a JWT authentication but got: " + authentication.getClass().getName());
        }
        Jwt jwt = jwtAuthentication.getToken();
        String preferredUsername = jwt.getClaimAsString("preferred_username");
        String subject = StringUtils.hasText(preferredUsername) ? preferredUsername : jwt.getSubject();

        boolean admin = false;
        for (GrantedAuthority authority : jwtAuthentication.getAuthorities()) {
            if ("ROLE_ADMIN".equals(authority.getAuthority())) {
                admin = true;
            }
        }
        return new AccessContext(subject, admin);
    }
}
