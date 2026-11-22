package com.acquiring.simulator.domain.event;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Durable processor receipt for one admitted normal verified issuer result (observation contract
 * O3/P5; profile P5 step (c)).
 *
 * <p>The evidence identity is exactly {@code (environmentId, attestingPrincipal, evidenceId)}. One
 * canonical content hash — the signed attestation canonical SHA-256 — is retained per identity; a
 * different content hash for the same identity must never overwrite the first receipt. The
 * attesting principal is the principal that signed the evidence (the inner issuer for a RELAY), not
 * a carriage relay principal. {@link #identitySha256()} is a storage-local digest of the identity
 * triple and is not an evidence hash or a signature.</p>
 *
 * <p>The receipt carries normalized references and hashes only: no raw ISO bytes, signed
 * attestation JSON, signature, public key or body. It records the business committed journal
 * version the receipt was committed with, so a read can tie the receipt to the committed record
 * rather than infer it.</p>
 *
 * <p>This type is a read representation. Construction does not create the row; only the atomic
 * receipt composition does, in the same {@code TransactWriteItems} as the business record and the
 * critical event.</p>
 */
public record ProcessorEvidenceReceipt(
        String environmentId,
        String attestingPrincipal,
        String evidenceId,
        String canonicalAttestationSha256,
        String instructionSha256,
        String responseSha256,
        String acquirerRef,
        String authorizationId,
        String transactionId,
        String keyId,
        String attestationType,
        String eventId,
        long journalVersion,
        String stateAfter,
        Instant evidenceAvailableAt) {

    /** Maximum profile identifier width (environment, principal, evidence, key, authorization). */
    public static final int MAX_IDENTIFIER_LENGTH = 64;
    /** Maximum business reference width (acquirer reference, event identity). */
    public static final int MAX_REFERENCE_LENGTH = 128;

    private static final Pattern HEX_64 = Pattern.compile("[0-9a-f]{64}");

    public ProcessorEvidenceReceipt {
        identifier("environmentId", environmentId, MAX_IDENTIFIER_LENGTH);
        identifier("attestingPrincipal", attestingPrincipal, MAX_IDENTIFIER_LENGTH);
        identifier("evidenceId", evidenceId, MAX_IDENTIFIER_LENGTH);
        hex64("canonicalAttestationSha256", canonicalAttestationSha256);
        hex64("instructionSha256", instructionSha256);
        hex64("responseSha256", responseSha256);
        identifier("acquirerRef", acquirerRef, MAX_REFERENCE_LENGTH);
        identifier("authorizationId", authorizationId, MAX_IDENTIFIER_LENGTH);
        identifier("transactionId", transactionId, MAX_IDENTIFIER_LENGTH);
        identifier("keyId", keyId, MAX_IDENTIFIER_LENGTH);
        identifier("attestationType", attestationType, MAX_IDENTIFIER_LENGTH);
        identifier("eventId", eventId, MAX_REFERENCE_LENGTH);
        if (journalVersion < 1) {
            throw new IllegalArgumentException("journalVersion must be positive");
        }
        if (!"APPROVED".equals(stateAfter) && !"DECLINED".equals(stateAfter)) {
            throw new IllegalArgumentException(
                    "stateAfter must be the confirmed APPROVED or DECLINED state");
        }
        Objects.requireNonNull(evidenceAvailableAt, "evidenceAvailableAt");
    }

    /**
     * Storage identity of this receipt. The three components cannot contain {@code #} because the
     * profile identifier alphabet excludes it, so the layout is unambiguous.
     */
    public String identityKey() {
        return "EVID#" + environmentId + "#" + attestingPrincipal + "#" + evidenceId;
    }

    /**
     * Stable digest of {@link #identityKey()}. This is a lookup/identity digest, deliberately
     * distinct from {@code canonicalAttestationSha256}; it proves no evidence content.
     */
    public String identitySha256() {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(identityKey().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    /** Never renders the full hashes or signed proof material. */
    @Override
    public String toString() {
        return "ProcessorEvidenceReceipt[identity=" + identityKey()
                + ", authorizationId=" + authorizationId
                + ", stateAfter=" + stateAfter
                + ", journalVersion=" + journalVersion + "]";
    }

    private static void identifier(String field, String value, int max) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw new IllegalArgumentException(field + " must be 1-" + max + " characters");
        }
        EventSanitizer.requireSafe(field, value);
    }

    private static void hex64(String field, String value) {
        if (value == null || !HEX_64.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be lowercase 64-character hex");
        }
    }
}
