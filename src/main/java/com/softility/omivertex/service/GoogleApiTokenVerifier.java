package com.softility.omivertex.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Optional;

/**
 * Real Google verification: checks the ID token's signature against Google's public
 * keys and that it was issued for our OAuth client (audience). If no client id is
 * configured (e.g. local dev), verification always fails closed — the endpoint then
 * refuses Google sign-in rather than trusting anything, so there is no spoofable path.
 */
@Component
public class GoogleApiTokenVerifier implements GoogleTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(GoogleApiTokenVerifier.class);

    private final GoogleIdTokenVerifier verifier;

    public GoogleApiTokenVerifier(@Value("${omivertex.auth.google.client-id:}") String clientId) {
        if (clientId == null || clientId.isBlank()) {
            this.verifier = null;
            log.warn("omivertex.auth.google.client-id is not set — Google sign-in is disabled "
                    + "(the endpoint will reject all tokens). Set it to enable Google login.");
        } else {
            GoogleIdTokenVerifier built = null;
            try {
                built = new GoogleIdTokenVerifier.Builder(
                        GoogleNetHttpTransport.newTrustedTransport(), GsonFactory.getDefaultInstance())
                        .setAudience(Collections.singletonList(clientId))
                        .build();
            } catch (Exception e) {
                log.error("Failed to initialise Google token verifier", e);
            }
            this.verifier = built;
        }
    }

    @Override
    public Optional<GoogleIdentity> verify(String idToken) {
        if (verifier == null || idToken == null || idToken.isBlank()) {
            return Optional.empty();
        }
        try {
            GoogleIdToken token = verifier.verify(idToken);
            if (token == null) {
                return Optional.empty();
            }
            GoogleIdToken.Payload payload = token.getPayload();
            if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
                return Optional.empty();
            }
            Object name = payload.get("name");
            return Optional.of(new GoogleIdentity(payload.getEmail(), name == null ? "" : name.toString()));
        } catch (Exception e) {
            log.debug("Google token verification failed", e);
            return Optional.empty();
        }
    }
}
