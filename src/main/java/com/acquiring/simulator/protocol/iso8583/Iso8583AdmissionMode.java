package com.acquiring.simulator.protocol.iso8583;

/**
 * Trusted server-side mode of one admission evaluation.
 *
 * <p>This enum is deliberately internal: the calling component supplies it from
 * its own control flow. It is never decoded from DE48, a header or any client
 * field. A caller therefore cannot turn a stale {@code NEW_FIRST_DISPATCH} into
 * a replay, or a replay into a fresh dispatch.</p>
 */
public enum Iso8583AdmissionMode {

    /** New unlinked request that may be claimed and dispatched once. */
    NEW_FIRST_DISPATCH,

    /** Authorized historical replay of the persisted original request. */
    AUTHORIZED_HISTORICAL_REPLAY,

    /** Late authenticated evidence for an already persisted operation. */
    LATE_EVIDENCE
}
