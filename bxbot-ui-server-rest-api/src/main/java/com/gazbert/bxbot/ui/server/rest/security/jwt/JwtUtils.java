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
public class JwtUtils {

    private static final Logger LOG = LogManager.getLogger();

    static final String CLAIM_KEY_LAST_PASSWORD_CHANGE_DATE = "bxbot:lastPasswordChangeDate";
    static final String CLAIM_KEY_ROLES = "bxbot:roles";

    private static final String CLAIM_KEY_USERNAME = "sub";
    private static final String CLAIM_KEY_ISSUER = "iss";
    private static final String CLAIM_KEY_ISSUED_AT = "iat";
    private static final String CLAIM_KEY_AUDIENCE = "aud";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expirationInSecs;

    @Value("${jwt.allowed_clock_skew}")
    private long allowedClockSkewInSecs;

    @Value("${jwt.issuer}")
    private String issuer;

    @Value("${jwt.audience}")
    private String audience;


    /**
     * For simple validation it is enough to just check the token integrity by decrypting it with our private key.
     * We don't have to call the database for an additional User lookup/check for every request.
     *
     * @param token the JWT.
     * @return the token claims if the JWT was valid.
     * @throws JwtAuthenticationException if the JWT was invalid.
     */
    public Claims validateTokenAndGetClaims(String token) {

        try {
            final Claims claims = getClaimsFromToken(token);
            final Date created = getIssuedAtDateFromTokenClaims(claims);
            final Date lastPasswordResetDate = getLastPasswordResetDateFromTokenClaims(claims);
            if (isCreatedBeforeLastPasswordReset(created, lastPasswordResetDate)) {
                final String errorMsg = "Invalid token! Created date claim is before last password reset date." +
                        " Created date: " + created + " Password reset date: " + lastPasswordResetDate;
                LOG.error(errorMsg);
                throw new JwtAuthenticationException(errorMsg);
            }
            return claims;
        } catch (Exception e) {
            final String errorMsg = "Invalid token! Details: " + e.getMessage();
            LOG.error(errorMsg, e);
            throw new JwtAuthenticationException(errorMsg, e);
        }
    }

    public String generateToken(JwtUser userDetails) {
        final Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_KEY_ISSUER, issuer);
        claims.put(CLAIM_KEY_ISSUED_AT, new Date());
        claims.put(CLAIM_KEY_AUDIENCE, audience);
        claims.put(CLAIM_KEY_USERNAME, userDetails.getUsername());
        claims.put(CLAIM_KEY_ROLES, mapRolesFromGrantedAuthorities(userDetails.getAuthorities()));
        claims.put(CLAIM_KEY_LAST_PASSWORD_CHANGE_DATE, userDetails.getLastPasswordResetDate());
        return generateToken(claims);
    }

    public boolean canTokenBeRefreshed(Claims claims, Date lastPasswordReset) {
        final Date created = getIssuedAtDateFromTokenClaims(claims);
        return !isCreatedBeforeLastPasswordReset(created, lastPasswordReset);
    }

    public String refreshToken(String token) throws JwtAuthenticationException {
        try {
            final Claims claims = getClaimsFromToken(token);
            claims.put(CLAIM_KEY_ISSUED_AT, new Date());
            return generateToken(claims);
        } catch (Exception e) {
            final String errorMsg = "Failed to refresh token!";
            LOG.error(errorMsg, e);
            throw new JwtAuthenticationException(errorMsg, e);
        }
    }

    public String getUsernameFromTokenClaims(Claims claims) {
        try {
            final String username = claims.getSubject();
            if (username == null) {
                final String errorMsg = "Failed to extract username claim from token!";
                LOG.error(errorMsg);
                throw new JwtAuthenticationException(errorMsg);
            }
            return username;
        } catch (Exception e) {
            final String errorMsg = "Failed to extract username claim from token!";
            LOG.error(errorMsg);
            throw new JwtAuthenticationException(errorMsg, e);
        }
    }

    public List<GrantedAuthority> getRolesFromTokenClaims(Claims claims) throws JwtAuthenticationException {
        final List<GrantedAuthority> roles = new ArrayList<>();
        try {
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

    Date getIssuedAtDateFromTokenClaims(Claims claims) throws JwtAuthenticationException {
        try {
            return claims.getIssuedAt();
        } catch (Exception e) {
            final String errorMsg = "Failed to extract iat claim from token!";
            LOG.error(errorMsg, e);
            throw new JwtAuthenticationException(errorMsg, e);
        }
    }

    Date getExpirationDateFromTokenClaims(Claims claims) throws JwtAuthenticationException {
        try {
            return claims.getExpiration();
        } catch (Exception e) {
            final String errorMsg = "Failed to extract expiration claim from token!";
            LOG.error(errorMsg, e);
            throw new JwtAuthenticationException(errorMsg, e);
        }
    }

    Date getLastPasswordResetDateFromTokenClaims(Claims claims) {
        Date lastPasswordResetDate;
        try {
            lastPasswordResetDate = new Date((Long) claims.get(CLAIM_KEY_LAST_PASSWORD_CHANGE_DATE));
        } catch (Exception e) {
            LOG.error("Failed to extract lastPasswordResetDate claim from token!", e);
            lastPasswordResetDate = null;
        }
        return lastPasswordResetDate;
    }

    // ------------------------------------------------------------------------
    // Private utils
    // ------------------------------------------------------------------------

    private String generateToken(Map<String, Object> claims) {

        final Date issuedAtDate = (Date) claims.get(CLAIM_KEY_ISSUED_AT);
        final Date expirationDate = new Date(issuedAtDate.getTime() + (expirationInSecs * 1000));

        return Jwts.builder()
                .setClaims(claims)
                .setExpiration(expirationDate)
                .setIssuedAt(issuedAtDate) // TODO Possible bug in jjwt - if this not set to override claims value, date is WAY in the future...
                .signWith(SignatureAlgorithm.HS512, secret)
                .compact();
    }

    private Claims getClaimsFromToken(String token) {
        return Jwts.parser()
                .setAllowedClockSkewSeconds(allowedClockSkewInSecs)
                .setSigningKey(secret)
                .requireIssuer(issuer)
                .requireAudience(audience)
                .parseClaimsJws(token)
                .getBody();
    }

    private boolean isCreatedBeforeLastPasswordReset(Date created, Date lastPasswordReset) {
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