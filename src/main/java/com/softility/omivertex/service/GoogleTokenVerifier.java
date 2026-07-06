package com.softility.omivertex.service;

import java.util.Optional;

/**
 * Verifies a Google Sign-In ID token and returns the authenticated identity.
 * Abstracted so the login flow depends on the contract, not the Google SDK — and so
 * tests can supply a stub instead of reaching out to Google.
 */
public interface GoogleTokenVerifier {

    Optional<GoogleIdentity> verify(String idToken);

    record GoogleIdentity(String email, String name) {}
}
