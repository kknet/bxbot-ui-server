/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 Stephan Zerhusen
 * Copyright (c) 2017 Gareth Jon Lynch
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.gazbert.bxbot.ui.server.rest.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Util class for validating and accessing JSON Web Tokens.
 * <p>
 * Properties are loaded from the resources/application.yml file.
 * <p>
 * Code originated from the excellent JWT and Spring Boot example by Stephan Zerhusen:
 * https://github.com/szerhusenBC/jwt-spring-security-demo
 *
 * @author gazbert
 */
@Component
public class JwtTokenUtils {

    private static final Logger LOG = LogManager.getLogger();

    static final String AUDIENCE_BXBOT_UI = "bxbot-ui";

    private static final String CLAIM_KEY_USERNAME = "sub";
    private static final String CLAIM_KEY_AUDIENCE = "audience";
    private static final String CLAIM_KEY_CREATED = "created";
    private static final String CLAIM_KEY_LAST_PASSWORD_CHANGE_DATE = "lastPasswordChangeDate";
    private static final String CLAIM_KEY_ROLES = "roles";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    @Value("${jwt.allowed_clock_skew}")
    private long allowedClockSkewInSecs;


    /**
     * For simple validation it is enough to just check the token integrity by decrypting it with our private key.
     * We don't have to call the database for an additional User lookup/check for every request.
     *
     * @param token the JWT.
     * @return true if JWT is valid, false otherwise.
     */
    public boolean validateToken(String token) {

        boolean isValid = false;

        try {
            final Date created = getCreatedDateFromToken(token);
            isValid = !isCreatedBeforeLastPasswordReset(created, getLastPasswordResetDateFromToken(token));
        } catch (Exception e) {
            LOG.error("Invalid token! Details: " + e.getMessage(), e);
        }
        return isValid;
    }

    /**
     * Validates the JWT and cross-checks the JWT User details with the repository User details.
     *
     * @param token       the JWT.
     * @param userDetails the user details looked up from the repository.
     * @return true if JWT is valid, false otherwise.
     */
    public boolean validateToken(String token, UserDetails userDetails) throws JwtAuthenticationException {

        boolean isValid = false;

        try {
            final JwtUser user = (JwtUser) userDetails;
            final String username = getUsernameFromToken(token);
            final Date created = getCreatedDateFromToken(token);

            isValid = (username.equals(user.getUsername()) // no need for this as we put it in there!
                    && !isCreatedBeforeLastPasswordReset(created, new Date(user.getLastPasswordResetDate())));
        } catch (Exception e) {
            LOG.error("Invalid token! Details: " + e.getMessage(), e);
        }
        return isValid;
    }

    public String generateToken(JwtUser userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_KEY_USERNAME, userDetails.getUsername());
        claims.put(CLAIM_KEY_AUDIENCE, AUDIENCE_BXBOT_UI);
        claims.put(CLAIM_KEY_CREATED, new Date());
        claims.put(CLAIM_KEY_ROLES, mapRolesFromGrantedAuthorities(userDetails.getAuthorities()));
        claims.put(CLAIM_KEY_LAST_PASSWORD_CHANGE_DATE, userDetails.getLastPasswordResetDate());
        return generateToken(claims);
    }

    public Boolean canTokenBeRefreshed(String token, Date lastPasswordReset) {
        final Date created = getCreatedDateFromToken(token);
        return !isCreatedBeforeLastPasswordReset(created, lastPasswordReset);
    }

    public String refreshToken(String token) throws JwtAuthenticationException {
        try {
            final Claims claims = getClaimsFromToken(token);
            claims.put(CLAIM_KEY_CREATED, new Date());
            return generateToken(claims);
        } catch (Exception e) {
            final String errorMsg = "Failed to refresh token!";
            LOG.error(errorMsg, e);
            throw new JwtAuthenticationException(errorMsg, e);
        }
    }

    public String getUsernameFromToken(String token) {

        String username = null;

        // token can be validly null if user is logging in for first time to ask for a token
        if (token != null) {
            try {
                final Claims claims = getClaimsFromToken(token);
                if (claims != null) {
                    username = claims.getSubject();
                }
            } catch (Exception e) {
                LOG.warn("Failed to extract username claim from token!", e);
            }
        }
        return username;
    }

    public Date getCreatedDateFromToken(String token) throws JwtAuthenticationException {
        try {
            final Claims claims = getClaimsFromToken(token);
            return new Date((Long) claims.get(CLAIM_KEY_CREATED));
        } catch (Exception e) {
            final String errorMsg = "Failed to extract created date claim from token!";
            LOG.error(errorMsg, e);
            throw new JwtAuthenticationException(errorMsg, e);
        }
    }

    public Date getExpirationDateFromToken(String token) throws JwtAuthenticationException {
        try {
            final Claims claims = getClaimsFromToken(token);
            return claims.getExpiration();
        } catch (Exception e) {
            final String errorMsg = "Failed to extract expiration claim from token!";
            LOG.error(errorMsg, e);
            throw new JwtAuthenticationException(errorMsg, e);
        }
    }

    public Date getLastPasswordResetDateFromToken(String token) {
        Date lastPasswordResetDate;
        try {
            final Claims claims = getClaimsFromToken(token);
            lastPasswordResetDate = new Date((Long) claims.get(CLAIM_KEY_LAST_PASSWORD_CHANGE_DATE));
        } catch (Exception e) {
            LOG.error("Failed to extract lastPasswordResetDate claim from token!", e);
            lastPasswordResetDate = null;
        }
        return lastPasswordResetDate;
    }

    public String getAudienceFromToken(String token) throws JwtAuthenticationException {
        try {
            final Claims claims = getClaimsFromToken(token);
            return (String) claims.get(CLAIM_KEY_AUDIENCE);
        } catch (Exception e) {
            final String errorMsg = "Failed to extract audience claim from token!";
            LOG.error(errorMsg, e);
            throw new JwtAuthenticationException(errorMsg, e);
        }
    }

    public List<GrantedAuthority> getRolesFromToken(String token) throws JwtAuthenticationException {
        final List<GrantedAuthority> roles = new ArrayList<>();
        try {
            final Claims claims = getClaimsFromToken(token);

            @SuppressWarnings("unchecked") final List<String> rolesFromClaim = (List<String>) claims.get(CLAIM_KEY_ROLES);

            for (final String roleFromClaim : rolesFromClaim) {
                roles.add(new SimpleGrantedAuthority(roleFromClaim));
            }
            return roles;
        } catch (Exception e) {
            final String errorMsg = "Failed to extract roles claim from token!";
            LOG.error(errorMsg, e);
            throw new JwtAuthenticationException(errorMsg, e);
        }
    }

    // ------------------------------------------------------------------------
    // Private utils
    // ------------------------------------------------------------------------

    private String generateToken(Map<String, Object> claims) {
        return Jwts.builder()
                .setClaims(claims)
                .setExpiration(generateExpirationDate())
                .signWith(SignatureAlgorithm.HS512, secret)
                .compact();
    }

    private Claims getClaimsFromToken(String token) {
        return Jwts.parser()
                .setAllowedClockSkewSeconds(allowedClockSkewInSecs * 1000)
                .setSigningKey(secret)
                .parseClaimsJws(token)
                .getBody();
    }

    private Date generateExpirationDate() {
        return new Date(System.currentTimeMillis() + expiration * 1000);
    }


    private Boolean isCreatedBeforeLastPasswordReset(Date created, Date lastPasswordReset) {
        return (lastPasswordReset != null && created.before(lastPasswordReset));
    }

    private List<String> mapRolesFromGrantedAuthorities(Collection<? extends GrantedAuthority> grantedAuthorities) {
        final List<String> roles = new ArrayList<>();
        for (final GrantedAuthority grantedAuthority : grantedAuthorities) {
            roles.add(grantedAuthority.getAuthority());
        }
        return roles;
    }
}