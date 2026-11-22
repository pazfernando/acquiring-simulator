# P2c3 test iterations

The first two iterations happened before log capture; this note preserves their exact
symptom and fix. The captured run is the third iteration.

1. `NullPointerException: Cannot invoke "java.time.Instant.toString()" because "e.asOf" is null`
   at `C3ResponseEnvelope.canonicalJson` called from the compact constructor.
   Cause: a compact record constructor cannot read its own fields before assignment.
   Fix: serialize the constructor parameters explicitly instead of `this`.

2. `H17 canonical body` mismatch: expected `"disposition":"COMPLETED"`, built `"REPLAY"`.
   Cause: the test fact table inferred replay from the sample `ATT-002` identifiers.
   The `given` text defines H17/H18 as initial durable results, and H20/H24 as initial
   committed results despite reused sample IDs. Fix: map those four cases to
   `Origin.SUBMISSION`. This was a test-fact correction, not a builder change.

3. Captured run: 9 tests, 26/26 oracle cases, 0 failures.
