package com.acquiring.simulator.console;

import java.util.Objects;

/**
 * The verified identity a console request is allowed to act as.
 *
 * <p>Only the authorizer seam produces this value. Nothing in the HTTP request body,
 * and no caller-supplied header, can create one. There is exactly one demo subject in
 * the approved exposure model, so this type is deliberately not a role or permission
 * model: it records which issuer, subject, client and audience class the token proved. The
 * issuer is carried so the history cursor can bind it (D-6) rather than trusting the cursor for
 * identity.</p>
 */
public record ConsolePrincipal(
        String issuer,
        String subject,
        String authorizedParty,
        String audience,
        java.util.Set<String> scopes) {

    public ConsolePrincipal {
        Objects.requireNonNull(issuer, "issuer");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(authorizedParty, "authorizedParty");
        Objects.requireNonNull(audience, "audience");
        scopes = scopes == null ? java.util.Set.of() : java.util.Set.copyOf(scopes);
    }

    /** Never renders credential material; this type holds no token. */
    @Override
    public String toString() {
        return "ConsolePrincipal[issuer=" + issuer
                + ", subject=" + subject
                + ", authorizedParty=" + authorizedParty
                + ", audience=" + audience
                + ", scopes=" + scopes + "]";
    }
}
