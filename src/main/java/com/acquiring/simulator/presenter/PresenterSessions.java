package com.acquiring.simulator.presenter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Opaque local relay sessions for the single configured demo subject.
 *
 * <p>The browser receives only a random opaque identifier in an {@code HttpOnly},
 * {@code SameSite=Strict} cookie. No JWT, refresh token, access code or key material is stored in
 * browser storage, the URL, the HTML or an event frame (channel contract C6/O4). Sessions live only
 * in relay memory, cannot outlive the configured lifetime and are invalidated on sign-out.</p>
 *
 * <p>This is the local test-only stand-in for the approved demo-user sign-in. It verifies a
 * configured demo access code (constant-time) and binds the session to the one configured subject;
 * it never issues tokens and never represents itself as the approved issuer.</p>
 */
public final class PresenterSessions {

    private static final int MAX_SESSIONS = 8;

    private final Clock clock;
    private final Duration lifetime;
    private final String demoSubject;
    private final byte[] accessCode;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public PresenterSessions(Clock clock, Duration lifetime, String demoSubject, String accessCode) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.lifetime = Objects.requireNonNull(lifetime, "lifetime");
        this.demoSubject = Objects.requireNonNull(demoSubject, "demoSubject");
        this.accessCode = accessCode.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Verify the local demo access code and issue one opaque session.
     *
     * @return the new session, or empty when the code is wrong (no diagnostic difference)
     */
    public Optional<Session> signIn(String suppliedCode) {
        byte[] supplied = suppliedCode == null
                ? new byte[0] : suppliedCode.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(accessCode, supplied)) {
            return Optional.empty();
        }
        prune();
        if (sessions.size() >= MAX_SESSIONS) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String id = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        Session session = new Session(id, demoSubject, now, now.plus(lifetime));
        sessions.put(id, session);
        return Optional.of(session);
    }

    /** Resolve a cookie value, removing it when expired. */
    public Optional<Session> resolve(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        Session session = sessions.get(sessionId);
        if (session == null) {
            return Optional.empty();
        }
        if (!clock.instant().isBefore(session.expiresAt())) {
            sessions.remove(sessionId);
            return Optional.empty();
        }
        return Optional.of(session);
    }

    public void signOut(String sessionId) {
        if (sessionId != null) {
            sessions.remove(sessionId);
        }
    }

    public int activeSessions() {
        prune();
        return sessions.size();
    }

    private void prune() {
        Instant now = clock.instant();
        sessions.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt()));
    }

    /** One in-memory session; never exposes the access code or any token. */
    public record Session(String id, String subject, Instant issuedAt, Instant expiresAt) {

        public Session {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(subject, "subject");
            Objects.requireNonNull(issuedAt, "issuedAt");
            Objects.requireNonNull(expiresAt, "expiresAt");
        }

        @Override
        public String toString() {
            return "Session[subject=" + subject + ", expiresAt=" + expiresAt + "]";
        }
    }
}
