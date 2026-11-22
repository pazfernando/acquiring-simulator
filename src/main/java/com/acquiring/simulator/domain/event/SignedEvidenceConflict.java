package com.acquiring.simulator.domain.event;

import java.util.Objects;

/**
 * Durable quarantine metadata for one signed financial-evidence conflict.
 *
 * <p>Approved wording: ISO profile P5/P4 — {@code iso8583-profile.md:91} ("evidenceId is immutable
 * in (environmentId,attestingPrincipal,evidenceId); enforce one canonical content hash per identity.
 * A reused ID with different data quarantines, no overwrite") and {@code :94} ("contradictory signed
 * decision quarantines and preserves established confirmation"). This record is that quarantine
 * marker.
 *
 * <p>It is normalised metadata only: the conflicting identity triple, the candidate canonical
 * attestation SHA-256, an optional reference to the established canonical hash, the established
 * business reference and a safe reason. It never carries the signed proof, the signature, a second
 * payload, raw ISO octets or any secret, and it is not business evidence, a payment result, a
 * resend, a reversal or a resolution.
 *
 * <p>The row is written at most once per candidate identity. A repeated conflict keeps the first
 * conflicting witness and never advances the business version, source sequence, critical slot or
 * the original receipt. Deleting the marker or failing to persist it cannot delete or change the
 * established outcome ({@code observation-contract.md:70}).
 *
 * @param type                                     why the candidate is quarantined
 * @param environmentId                            immutable evidence environment
 * @param attestingPrincipal                       principal that signed the candidate evidence
 * @param evidenceId                               reused/different evidence identity
 * @param canonicalAttestationSha256               candidate canonical attestation SHA-256
 * @param establishedCanonicalAttestationSha256    established receipt hash, or {@code null} when no
 *                                                 receipt identity was read for the business record
 * @param establishedAuthorizationId               established business reference
 * @param establishedAcquirerRef                   established acquirer reference
 * @param reason                                   safe, non-payload diagnostic
 */
public record SignedEvidenceConflict(
        Type type,
        String environmentId,
        String attestingPrincipal,
        String evidenceId,
        String canonicalAttestationSha256,
        String establishedCanonicalAttestationSha256,
        String establishedAuthorizationId,
        String establishedAcquirerRef,
        String reason) {

    /** Journal partition prefix for quarantine rows; distinct from the receipt {@code EVID#}. */
    public static final String PARTITION_PREFIX = "EVIDQ#";
    /** Journal sort key for the one quarantine marker of an evidence identity. */
    public static final String SORT_KEY = "QUARANTINE";
    /** Maximum profile identifier width (environment, principal, evidence, authorization). */
    public static final int MAX_IDENTIFIER_LENGTH = 64;
    /** Maximum business reference width (acquirer reference). */
    public static final int MAX_REFERENCE_LENGTH = 128;
    /** Maximum safe reason width. */
    public static final int MAX_REASON_LENGTH = 128;

    /** The two approved conflict classes; no other conflict invented here. */
    public enum Type {
        /** The same evidence identity arrived with a different canonical content hash. */
        REUSED_IDENTITY_DIFFERENT_CONTENT,
        /** A different evidence identity asserts an outcome contradicting a determined record. */
        CONTRADICTORY_DETERMINED_OUTCOME
    }

    public SignedEvidenceConflict {
        Objects.requireNonNull(type, "type");
        identifier("environmentId", environmentId, MAX_IDENTIFIER_LENGTH);
        identifier("attestingPrincipal", attestingPrincipal, MAX_IDENTIFIER_LENGTH);
        identifier("evidenceId", evidenceId, MAX_IDENTIFIER_LENGTH);
        hex64("canonicalAttestationSha256", canonicalAttestationSha256);
        if (establishedCanonicalAttestationSha256 != null) {
            hex64("establishedCanonicalAttestationSha256",
                    establishedCanonicalAttestationSha256);
        }
        identifier("establishedAuthorizationId", establishedAuthorizationId,
                MAX_IDENTIFIER_LENGTH);
        identifier("establishedAcquirerRef", establishedAcquirerRef, MAX_REFERENCE_LENGTH);
        text("reason", reason, MAX_REASON_LENGTH);
    }

    /**
     * Journal partition of this candidate identity. The three identity components cannot contain
     * {@code #} because the profile identifier alphabet excludes it, so the layout is unambiguous.
     */
    public String identityKey() {
        return PARTITION_PREFIX + environmentId + "#" + attestingPrincipal + "#" + evidenceId;
    }

    /** Never renders the full hashes, signed proof material or a second payload. */
    @Override
    public String toString() {
        return "SignedEvidenceConflict[type=" + type + ", identity=" + identityKey()
                + ", establishedAuthorizationId=" + establishedAuthorizationId + "]";
    }

    private static void identifier(String field, String value, int max) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw new IllegalArgumentException(field + " must be 1-" + max + " characters");
        }
        EventSanitizer.requireSafe(field, value);
    }

    private static void text(String field, String value, int max) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw new IllegalArgumentException(field + " must be 1-" + max + " characters");
        }
        EventSanitizer.requireSafe(field, value);
    }

    private static void hex64(String field, String value) {
        if (value == null || value.length() != 64 || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + " must be lowercase 64-character hex");
        }
    }
}
