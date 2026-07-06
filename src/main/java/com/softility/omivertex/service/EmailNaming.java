package com.softility.omivertex.service;

/**
 * Single source of truth for the company email-address convention. Imports use this
 * to resolve an existing associate from a display name, so the derivation must not
 * drift between callers.
 */
public final class EmailNaming {

    public static final String COMPANY_DOMAIN = "softility.com";

    private EmailNaming() {
    }

    /** "Priya Sharma" -&gt; "priya.sharma@softility.com". */
    public static String forName(String name) {
        String slug = name.toLowerCase().replaceAll("[^a-z0-9]+", ".").replaceAll("^\\.|\\.$", "");
        return slug + "@" + COMPANY_DOMAIN;
    }
}
