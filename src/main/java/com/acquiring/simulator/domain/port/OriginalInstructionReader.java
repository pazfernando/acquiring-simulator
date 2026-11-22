package com.acquiring.simulator.domain.port;

import java.util.Optional;

/**
 * Strong-read seam for the exact persisted original Operation A wire instruction ({@code 0100}).
 *
 * <p>The signed P5/P6 binding is only meaningful against the instruction the processor actually
 * accepted, so a caller must not supply the bytes at admission time. An implementation reads the
 * immutable original projection by durable operation reference with a strongly consistent read and
 * returns exactly the persisted octets; it never re-encodes, normalizes or reconstructs them.</p>
 *
 * <p>Absence is explicit: an empty result means no original instruction was persisted for that
 * operation. An unreadable row or missing byte attribute is an {@link IllegalStateException}, which
 * an admission caller must treat as unknown rather than as proof that no instruction exists.</p>
 *
 * <p>This port is a read seam only. Persisting the original belongs to the claim/ACL path and is not
 * wired by this slice; key custody, TLS/IAM principal mapping and production storage policy remain
 * open.</p>
 */
public interface OriginalInstructionReader {

    /**
     * @param authorizationId durable operation reference
     * @return the exact persisted original {@code 0100} octets, or empty when none is persisted
     */
    Optional<byte[]> readPersistedOriginal0100(String authorizationId);
}
