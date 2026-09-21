package com.ziyadsamhaoui.messagingapigateway.support;

import java.time.Duration;
import java.time.Instant;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;

import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Mints the RS256 access tokens the Auth Service would issue, together with the JWKS document served by
 * the auth upstream stub. Only the public key ever leaves this class.
 */
public final class JwtTestTokens {

    /** Must match {@code badrlink.gateway.security.jwt.issuer}. */
    public static final String ISSUER = "http://auth-service:8081";

    public static final String JWKS_PATH = "/oauth2/jwks";

    private static final String KEY_ID = "badrlink-test-key";

    private static final String UNKNOWN_KEY_ID = "key-not-published-in-the-jwks";

    /** Spring Security tolerates 60s of clock skew, so an expired token must be older than that. */
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(60);

    private static final RSAKey SIGNING_KEY = generateKey(KEY_ID);

    private static final RSAKey UNKNOWN_KEY = generateKey(UNKNOWN_KEY_ID);

    private static final JwtEncoder SIGNING_ENCODER = encoderFor(SIGNING_KEY);

    private static final JwtEncoder UNKNOWN_KEY_ENCODER = encoderFor(UNKNOWN_KEY);

    private JwtTestTokens() {
    }

    /** The JWKS document the gateway fetches to verify signatures. */
    public static String jwksJson() {
        return new JWKSet(SIGNING_KEY.toPublicJWK()).toString();
    }

    public static String validToken(String subject) {
        Instant now = Instant.now();
        return SIGNING_ENCODER.encode(JwtEncoderParameters.from(
                claims(subject, ISSUER, now.minusSeconds(5), now.plus(Duration.ofMinutes(5))))).getTokenValue();
    }

    /** Expired well beyond the allowed clock skew. */
    public static String expiredToken(String subject) {
        Instant now = Instant.now();
        Instant expiresAt = now.minus(CLOCK_SKEW).minusSeconds(30);
        return SIGNING_ENCODER.encode(JwtEncoderParameters.from(
                claims(subject, ISSUER, now.minus(Duration.ofMinutes(10)), expiresAt))).getTokenValue();
    }

    public static String tokenWithWrongIssuer(String subject) {
        Instant now = Instant.now();
        return SIGNING_ENCODER.encode(JwtEncoderParameters.from(
                claims(subject, "http://evil.example.com", now.minusSeconds(5), now.plus(Duration.ofMinutes(5)))))
                .getTokenValue();
    }

    /** Valid claims, but signed with a key that is absent from the JWKS document. */
    public static String tokenSignedByUnknownKey(String subject) {
        Instant now = Instant.now();
        return UNKNOWN_KEY_ENCODER.encode(JwtEncoderParameters.from(
                claims(subject, ISSUER, now.minusSeconds(5), now.plus(Duration.ofMinutes(5))))).getTokenValue();
    }

    /** Structurally invalid: not even three dot-separated segments. */
    public static String malformedToken() {
        return "not-a-json-web-token";
    }

    private static JwtClaimsSet claims(String subject, String issuer, Instant issuedAt, Instant expiresAt) {
        return JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(subject)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("scope", "openid")
                .build();
    }

    private static JwtEncoder encoderFor(RSAKey key) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
    }

    private static RSAKey generateKey(String keyId) {
        try {
            return new RSAKeyGenerator(2048)
                    .keyID(keyId)
                    .algorithm(JWSAlgorithm.RS256)
                    .generate();
        }
        catch (JOSEException ex) {
            throw new IllegalStateException("Cannot generate the test RSA key", ex);
        }
    }
}
