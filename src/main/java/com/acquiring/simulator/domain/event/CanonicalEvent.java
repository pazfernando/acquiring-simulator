package com.acquiring.simulator.domain.event;

/**
 * Immutable functional-event payload (observation contract O1a/O2a). The field set is closed:
 * collector/transport fields are not part of it, and optional values are null so the RFC 8785
 * content hash is stable. Business rules and capacity figures are not encoded here.
 */
public record CanonicalEvent(
        int schemaVersion,
        String eventId,
        String producerId,
        String instanceId,
        EventActor actor,
        String capability,
        String version,
        String manifestVersion,
        String acquirerRef,
        String merchantRef,
        String terminalRef,
        String transactionId,
        String operationId,
        String attemptId,
        String correlationId,
        String taskExecutionId,
        EventTask taskName,
        EventStatus status,
        String occurredAt,
        long producerSequence,
        EventProvenance provenance,
        EventReason reason,
        String parentTaskId,
        String parentEventId,
        String retryOfAttemptId,
        String originalOperationId,
        String startedAt,
        String endedAt,
        Long elapsedMicros,
        EventClockQuality clockQuality,
        String evidenceRef,
        EventGapCode gapCode) {

    public static Builder builder() {
        return new Builder();
    }

    /** Mutable assembly helper; all contract enforcement stays in {@link EventValidator}. */
    public static final class Builder {
        private int schemaVersion = 1;
        private String eventId;
        private String producerId;
        private String instanceId;
        private EventActor actor;
        private String capability;
        private String version;
        private String manifestVersion;
        private String acquirerRef;
        private String merchantRef;
        private String terminalRef;
        private String transactionId;
        private String operationId;
        private String attemptId;
        private String correlationId;
        private String taskExecutionId;
        private EventTask taskName;
        private EventStatus status;
        private String occurredAt;
        private Long producerSequence;
        private EventProvenance provenance;
        private EventReason reason;
        private String parentTaskId;
        private String parentEventId;
        private String retryOfAttemptId;
        private String originalOperationId;
        private String startedAt;
        private String endedAt;
        private Long elapsedMicros;
        private EventClockQuality clockQuality;
        private String evidenceRef;
        private EventGapCode gapCode;

        public Builder schemaVersion(int value) { this.schemaVersion = value; return this; }
        public Builder eventId(String value) { this.eventId = value; return this; }
        public Builder producerId(String value) { this.producerId = value; return this; }
        public Builder instanceId(String value) { this.instanceId = value; return this; }
        public Builder actor(EventActor value) { this.actor = value; return this; }
        public Builder capability(String value) { this.capability = value; return this; }
        public Builder version(String value) { this.version = value; return this; }
        public Builder manifestVersion(String value) { this.manifestVersion = value; return this; }
        public Builder acquirerRef(String value) { this.acquirerRef = value; return this; }
        public Builder merchantRef(String value) { this.merchantRef = value; return this; }
        public Builder terminalRef(String value) { this.terminalRef = value; return this; }
        public Builder transactionId(String value) { this.transactionId = value; return this; }
        public Builder operationId(String value) { this.operationId = value; return this; }
        public Builder attemptId(String value) { this.attemptId = value; return this; }
        public Builder correlationId(String value) { this.correlationId = value; return this; }
        public Builder taskExecutionId(String value) { this.taskExecutionId = value; return this; }
        public Builder taskName(EventTask value) { this.taskName = value; return this; }
        public Builder status(EventStatus value) { this.status = value; return this; }
        public Builder occurredAt(String value) { this.occurredAt = value; return this; }
        public Builder producerSequence(long value) { this.producerSequence = value; return this; }
        public Builder provenance(EventProvenance value) { this.provenance = value; return this; }
        public Builder reason(EventReason value) { this.reason = value; return this; }
        public Builder parentTaskId(String value) { this.parentTaskId = value; return this; }
        public Builder parentEventId(String value) { this.parentEventId = value; return this; }
        public Builder retryOfAttemptId(String value) { this.retryOfAttemptId = value; return this; }
        public Builder originalOperationId(String value) { this.originalOperationId = value; return this; }
        public Builder startedAt(String value) { this.startedAt = value; return this; }
        public Builder endedAt(String value) { this.endedAt = value; return this; }
        public Builder elapsedMicros(Long value) { this.elapsedMicros = value; return this; }
        public Builder clockQuality(EventClockQuality value) { this.clockQuality = value; return this; }
        public Builder evidenceRef(String value) { this.evidenceRef = value; return this; }
        public Builder gapCode(EventGapCode value) { this.gapCode = value; return this; }

        public CanonicalEvent build() {
            if (producerSequence == null) {
                throw new EventValidationException(EventViolation.MISSING_REQUIRED, "producerSequence", null);
            }
            return new CanonicalEvent(schemaVersion, eventId, producerId, instanceId, actor,
                    capability, version, manifestVersion, acquirerRef, merchantRef, terminalRef,
                    transactionId, operationId, attemptId, correlationId, taskExecutionId,
                    taskName, status, occurredAt, producerSequence, provenance, reason,
                    parentTaskId, parentEventId, retryOfAttemptId, originalOperationId,
                    startedAt, endedAt, elapsedMicros, clockQuality, evidenceRef, gapCode);
        }
    }
}
