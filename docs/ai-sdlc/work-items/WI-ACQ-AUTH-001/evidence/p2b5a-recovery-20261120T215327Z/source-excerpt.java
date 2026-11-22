    /**
     * Read the durable processor receipt for one evidence identity, if the normal verified result
     * was admitted atomically. Absence is explicit and is never inferred from a confirmed state.
     */
    public Optional<ProcessorEvidenceReceipt> readEvidenceReceipt(String environmentId,
            String attestingPrincipal, String evidenceId) {
        Map<String, AttributeValue> key = EvidenceReceiptCriticalComposer.receiptKey(
                environmentId, attestingPrincipal, evidenceId);
        return get(key.get("pk").s(), key.get("sk").s()).map(item ->
                new ProcessorEvidenceReceipt(
                        requiredString(item, "environment"),
                        requiredString(item, "attestingPrincipal"),
                        requiredString(item, "evidenceId"),
                        requiredString(item, "canonicalAttestationSha256"),
                        requiredString(item, "instructionSha256"),
                        requiredString(item, "responseSha256"),
                        requiredString(item, "acquirerRef"),
                        requiredString(item, "authorizationId"),
                        requiredString(item, "transactionId"),
                        requiredString(item, "keyId"),
                        requiredString(item, "attestationType"),
                        requiredString(item, "eventId"),
                        requiredNumber(item, "journalVersion"),
                        requiredString(item, "stateAfter"),
                        Instant.parse(requiredString(item, "evidenceAvailableAt"))));
    }
    /** Numeric attributes are read as numbers; a CAS-style counter is never parsed from a string. */
    private static long requiredNumber(Map<String, AttributeValue> item, String field) {
        AttributeValue value = item.get(field);
        if (value == null || value.n() == null) {
            throw new IllegalStateException("journal row is missing " + field);
        }
        return Long.parseLong(value.n());
    }
    private static String requiredString(Map<String, AttributeValue> item, String field) {
        AttributeValue value = item.get(field);
        if (value == null || value.s() == null) {
            throw new IllegalStateException("journal row is missing " + field);
        }
        return value.s();
