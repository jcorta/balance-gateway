package io.github.jcorta.balancegateway.api;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;

/** Builds JWTs shaped like the ones the authorization server issues, signed with a key generated for the tests. */
final class TestTokens {

    static final String ISSUER = "http://auth-server.test";
    private static final String KEY_ID = "test-key";
    private static final RSAKey SIGNING_KEY = generateKey(KEY_ID);
    private static final RSAKey UNKNOWN_KEY = generateKey(KEY_ID);

    private TestTokens() {
    }

    /** The public key set that the authorization server would publish. */
    static String jwks() {
        return new JWKSet(SIGNING_KEY.toPublicJWK()).toString();
    }

    static String valid(String scope) {
        return validForClient("ivr-channel", scope);
    }

    static String validForClient(String clientId, String scope) {
        return sign(SIGNING_KEY, ISSUER, clientId, scope, Instant.now().plusSeconds(300));
    }

    static String expired(String scope) {
        return sign(SIGNING_KEY, ISSUER, "ivr-channel", scope, Instant.now().minusSeconds(3_600));
    }

    static String fromAnotherIssuer(String scope) {
        return sign(SIGNING_KEY, "http://another-issuer.test", "ivr-channel", scope, Instant.now().plusSeconds(300));
    }

    /** Same key id as the published key, but different key material, so only the signature check can reject it. */
    static String signedWithAnUnknownKey(String scope) {
        return sign(UNKNOWN_KEY, ISSUER, "ivr-channel", scope, Instant.now().plusSeconds(300));
    }

    /** A correctly signed token whose payload was edited afterwards, so the signature no longer matches. */
    static String withScopeEditedAfterSigning(String signedScope, String forgedScope) {
        String[] parts = validForClient("reporting", signedScope).split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                .replace(signedScope, forgedScope);
        String forged = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return parts[0] + "." + forged + "." + parts[2];
    }

    /** A token with alg=none: the claims are acceptable but nothing vouches for them. */
    static String unsigned(String scope) {
        Instant expiresAt = Instant.now().plusSeconds(300);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject("ivr-channel")
                .claim("scope", List.of(scope))
                .expirationTime(Date.from(expiresAt))
                .build();
        return new PlainJWT(claims).serialize();
    }

    private static String sign(RSAKey key, String issuer, String subject, String scope, Instant expiresAt) {
        Instant issuedAt = expiresAt.minusSeconds(300);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(subject)
                .claim("scope", List.of(scope))
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiresAt))
                .jwtID(UUID.randomUUID().toString())
                .build();
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Cannot sign test token", e);
        }
    }

    private static RSAKey generateKey(String keyId) {
        try {
            return new RSAKeyGenerator(2048).keyID(keyId).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException("Cannot generate test key", e);
        }
    }
}
