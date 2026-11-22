# Failure — P1b3a current-tree verification (2026-11-20)

Single failing test of the 154-test suite. Exit code 1 (`BUILD FAILURE`).

## Result summary

| Metric | Value |
| --- | --- |
| Tests run | 154 |
| Failures | 0 |
| Errors | 1 |
| Skipped | 0 |
| Test classes | 18 |
| Result | **BUILD FAILURE**, exit 1, total time 33.253 s |

## Error

```
com.acquiring.simulator.domain.event.EventValidationException: PROHIBITED_CONTENT at correlationId:
credential or payment-data shape is not admitted
  at EventSanitizer.requireSafe(EventSanitizer.java:38)
  at EventValidator.text(EventValidator.java:160)
  at EventValidator.requiredText(EventValidator.java:150)
  at EventValidator.validate(EventValidator.java:27)
  at DynamoEventStore.appendInternal(DynamoEventStore.java:80)
  at DynamoEventStore.append(DynamoEventStore.java:74)
  at AtomicClaimGateway.commit(AtomicClaimGateway.java:101)
  at DynamoClaimGateway.claim(DynamoClaimGateway.java:79)
  at AuthorizationService.authorizeAtomic(AuthorizationService.java:283)
  at AuthorizationService.authorizeUnlinked(AuthorizationService.java:254)
  at AuthorizationService.authorize(AuthorizationService.java:105)
  at AtomicClaimServiceWiringIntegrationTest
     .atomicClaimPathCommitsOnceDispatchesOnceAndReportsPersistedCommittedVersion(
      AtomicClaimServiceWiringIntegrationTest.java:154)
```

## Which path, and why it is not the slice under review

The throw is a **sanitizer rejection of a generated correlation ID**, not a claim/composer logic
defect. `AuthorizationService.java:282` builds `correlationId = "CORR-" + randomId()`, and
`randomId()` (`AuthorizationService.java:585-587`) returns

```java
UUID.randomUUID().toString().replace("-", "").substring(0, 24)
```

— 24 hexadecimal characters. `EventSanitizer.PAN_SHAPE` is `(?<!\d)\d{13,19}(?!\d)`, so a run of
13-19 consecutive **decimal** characters inside that hex string is rejected as a PAN shape even
though it is a random identifier.

Measured collision rate for exactly that generator (200,000 samples, `PanShapeCheck`, same JDK):

```
collisions=3104 / 200000 = 1.5520%
```

So roughly **1 in 65** authorization attempts throws. That is a real, low-rate flake, not a
deterministic defect of the current tree.

## Evidence that this is non-determinism, not a regression

- Attempt A (`full-test.log`, composer `089d76ca…`) ran the same 154 tests with **0 failures and 0
  errors**, including `AtomicClaimServiceWiringIntegrationTest` 6/6.
- The only source delta between Attempt A and the current tree is
  `PossibleSendCriticalComposer.java` (`089d76ca…` → `b9a2579f…`).
- This test constructs its store with `CriticalWriteComposer.NONE`
  (`AtomicClaimServiceWiringIntegrationTest` setup), so the composer is **not on the failing path**.
- Therefore the failing code path is byte-identical between the passing and failing snapshots. A
  deterministic regression is excluded by that alone; a random-ID/sanitizer shape collision is the
  only remaining explanation consistent with the evidence.

## Next fix (smallest, not applied this turn)

1. **Confirm the hypothesis** with one targeted rerun of just this class
   (`mvn -o test -Dtest=AtomicClaimServiceWiringIntegrationTest`). Passing on rerun confirms flake;
   failing again with the same field points at a deterministic input instead.
2. **Then fix the generator, not the sanitizer.** `randomId()` should emit a shape that cannot be
   read as a PAN — e.g. a non-digit separator inside the hex (mirroring the `CORR-` prefix) or a
   lowercase-alpha-first grouping. The sanitizer is a security control (R20) and must not be
   relaxed to accommodate identifier generation.
3. Add a regression test over the ID generator asserting `EventSanitizer` accepts its output across
   many samples, so the 1.55% class of collisions cannot return.

Not done this turn: no source edit, no rerun beyond the single authorized suite run, no Git write.
