package com.softility.omivertex.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Refuses to start the production profile with the well-known default credentials,
 * so a misconfigured deploy fails loudly instead of shipping guessable logins.
 */
@Component
@Profile("prod")
public class ProductionSafetyCheck {

    public ProductionSafetyCheck(
            @Value("${omivertex.auth.admin-password}") String adminPassword,
            @Value("${omivertex.auth.viewer-password}") String viewerPassword) {
        if ("Admin@123".equals(adminPassword) || "Viewer@123".equals(viewerPassword)) {
            throw new IllegalStateException(
                    "Refusing to start in prod with default credentials. Set "
                    + "OMIVERTEX_ADMIN_PASSWORD and OMIVERTEX_VIEWER_PASSWORD to strong values.");
        }
    }
}
