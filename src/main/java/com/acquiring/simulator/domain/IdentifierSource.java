package com.acquiring.simulator.domain;

import java.util.UUID;

/**
 * Seam for internally generated identifier candidates. The production source is random; tests
 * substitute a controllable source to prove that generated identifiers stay safe for the event
 * sanitizer for a known dangerous input.
 */
@FunctionalInterface
public interface IdentifierSource {

    /** Supplies at least 24 hexadecimal characters; normalization keeps the first 24. */
    String nextCandidate();

    /** Production source: 128 random bits rendered as 32 lowercase hexadecimal characters. */
    static IdentifierSource randomHex() {
        return () -> UUID.randomUUID().toString().replace("-", "");
    }
}
