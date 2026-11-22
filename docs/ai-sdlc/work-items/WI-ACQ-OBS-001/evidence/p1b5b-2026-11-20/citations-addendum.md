# P1b5b addendum to the P1b5a citation record — attribution, not a rule conflict

The P1b5a files are preserved byte-identically; this addendum supersedes only the interpretation in
`p1b5a-2026-11-20/citations.md` §"Known citation conflict (recorded, not resolved)". No approved
specification, fixture or public code was edited.

## Verified existing domain mapping

In the legacy path `AuthorizationService` writes, for a pre-issuer network rejection:

```
209:                        DecisionSource.NETWORK.name(), "NETWORK_REJECTION", reply.availableAt());
210:                appendEvent(record, "NETWORK_REJECTION", null, DecisionSource.NETWORK,
577:                    DecisionSource.NETWORK.name(), "NETWORK_REJECTION", reply.availableAt());
578:            appendEvent(record, "NETWORK_REJECTION", null, DecisionSource.NETWORK,
```

and returns `Outcome.PROCESSOR_REJECTED` (line 580), with replay mapping
`NETWORK_REJECTED -> PROCESSOR_REJECTED` (line 618). So the state, provenance, internal category and
caller outcome already exist in domain code.

## Verified reason allowlists

- `domain.event.EventReason` (approved bounded event vocabulary): no `NETWORK_REJECTION` entry.
- `domain.model.RejectionReason` (internal processor-rejection reasons): no `NETWORK_REJECTION`
  entry either.

## Corrected attribution

1. `NETWORK_REJECTED` and `DecisionSource.NETWORK` are required by NET-01/OBS-03 and R05/R10/R22.
2. `NETWORK_REJECTION` is **not** a sourced rule code. It is existing internal domain-code
   vocabulary (the literal the legacy service already records). It travels on the business record
   only; the critical event uses `EventReason.VALID`, which the allowlist does contain.
3. Therefore the absence of the literal in NET-01/OBS-03 is **not** a business conflict, and nothing
   needs to be renamed, added to an allowlist or resolved by an owner decision.
4. No new public code was invented: the caller continues to see the existing
   `Outcome.PROCESSOR_REJECTED`, never an issuer decline.

`NetworkRejectedCriticalComposer`'s javadoc was corrected to state exactly this. Its executable
behaviour is unchanged from P1b5a.
