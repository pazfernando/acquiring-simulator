# Technical preflight — WI-ACQ-OBS-001 (partial, evidence-finalized)

Date: 2026-11-19 America/Bogota; DynamoDB Local section updated 2026-11-20. Status: **PARTIAL / RECOVERED EVIDENCE; DE42 CORRECTED PROBES REPRODUCIBLE, HISTORICAL ROOT CAUSE UNPROVEN; DYNAMODB LOCAL CHECKS NOW CAPTURED (AWS BEHAVIOR STILL UNVERIFIED)**. This report finalizes the evidence that exists after interrupted preflight attempts. It is not a completed preflight, not a QA/Security verdict, and not construction authorization. Any check without captured output is marked **UNVERIFIED**; no result is inferred from intended behavior.

Evidence preservation (2026-11-19, follow-up turn): all reproducible probe artifacts were copied out of `/tmp/opencode` into `evidence/preflight-2026-11-19/` (manifest with hashes in that folder). A second bounded turn then re-executed the same Java probe with the explicit JDK 25 toolchain and captured stdout/stderr/exit codes in `technical-preflight-evidence/` (manifest there). The rerun reproduced the preserved outputs byte-for-byte; no DE42 mismatch is present. The profile and vectors were not changed. See §2.

Correction (same follow-up): §3 previously reported two defects in the DynamoDB probe script. Both were wrong and are **withdrawn**: DynamoDB evaluates `attribute_not_exists(pk)` against the pre-existing item (so a first insert succeeds), and the probe's `ClientRequestToken` arguments are single-quoted, so the table name is never expanded. Neither is a product defect; neither was ever a captured failure. DynamoDB results remain UNVERIFIED because no per-check output exists.

## Authority and environment

- Authority: `owner-decisions.md`, "Architecture approval and technical-preflight commission" (2026-11-19). Scope: technical preflight before implementation; safe local/offline checks or existing read-only inspections only; no provision, deploy, send, production edits or new publication.
- Working directory: `/home/pazfernando/projects/acquiring-simulator`
- Branch: `feat/wi-acq-auth-001`
- HEAD: `cfc456f4f469d9558a78fda8ff9b770b00817315` (`cfc456f`, 2026-11-19 09:25:03 -0500, "Document observability console specification and owner decisions")
- Working tree at report time: 34 changed/untracked entries, including the OBS-001 specification set and diagram assets. No production source was edited by this preflight.
- Model for this recovery turn: provider `nan`, model `deepseek-v4-flash` (DeepSeek V4 Flash), configured default, no override. The model actually used by the earlier aborted preflight runs was not recorded and is **UNVERIFIED**.
- Toolchain observed in this shell: JDK 25 at `/home/pazfernando/.local/opt/jdk-25`; Maven 3.10.1 at `/home/pazfernando/.local/opt/maven/bin/mvn` (neither on `PATH`). `terraform`, `tflint`, `checkov` and `cfn-lint` are **ABSENT** from `PATH` in this environment. Node/npm, Python 3 and Docker are present.
- Baseline build: `mvn -o -q -DskipTests compile` and `mvn -o -q test-compile` with `JAVA_HOME=/home/pazfernando/.local/opt/jdk-25` both exited 0 (offline, no network). Tests were not executed in this turn.
- No Git write, commit, push, PR, deployment, Drive upload or external publication was performed for this report. Evidence copies were written under this work item only.

## Pinned source identity — VERIFIED

All ten SHA-256 identities pinned in the commission were recomputed on the current files and match exactly:

| Artifact | SHA-256 | Result |
|---|---|---|
| `requirements.md` | `49c5d412…e2b459df` | MATCH |
| `acceptance-scenarios.md` | `15868984…aa7733` | MATCH |
| `iso8583-profile.md` | `de02c533…0e1fe481` | MATCH |
| `channel-contract.md` | `495e1172…38ea3bca` | MATCH |
| `observation-contract.md` | `ca53e384…842a205a` | MATCH |
| `decision-proposal.md` | `becbabcd…fe219166` | MATCH |
| `activity-flow.md` | `7f09731a…fb3c67bd` | MATCH |
| `activity-flow.mmd` | `e2fbb1aa…388393ce` | MATCH |
| `domain-review.md` | `73adba20…ee0ca2` | MATCH |
| `vectors/review-manifest.json` | `dac6fc14…eb5082dc` | MATCH |

Full digests are reproducible with `sha256sum` on those paths. This is document-integrity evidence only; it does not validate the content's technical claims.

## Check results

### 1. Java toolchain — OBSERVED

- JDK 25 present but not on `PATH`: `/home/pazfernando/.local/opt/jdk-25`
  - `java version "25.0.5" 2026-10-20 LTS` (Temurin-25.0.5+8), `javac 25.0.5`.
- A second runtime exists: `/usr/lib/jvm/java-17-amazon-corretto.x86_64` (Corretto 17), used by the local DynamoDB emulator process.
- Consequence: earlier work that assumed a default `java`/`javac` on `PATH` fails in this shell; the JDK 25 path must be explicit.

### 2. ISO 8583 codec feasibility — STRUCTURALLY ALIGNED; PRODUCTION CODEC STILL UNPROVEN

- Candidate probe only. It is a disposable structural check of the proposed private profile, not the production codec and not scheme conformance.
- Fixtures: `85` packed vectors extracted from `vectors/packed-vectors.json`. All probe artifacts are preserved under `evidence/preflight-2026-11-19/` with SHA-256 in that folder's README.
- **DE42 alignment: CORRECTED PROBES REPRODUCIBLE; HISTORICAL CAUSE UNPROVEN (2026-11-19 follow-up).** The earlier turn attributed the mismatch to `IsoProbe.java`, not the profile or vectors: DE32 was described as declared fixed `an` width 11 while `iso8583-profile.md` P1 declares `LL n1–11`; the 2-byte misread was said to shift DE37/DE41/DE42 and raise `PARSER_ALPHABET DE42 not uppercase AN`. A second latent defect (`parsed.fields.get(48)` using an Integer key against String keys) was reached only after that fix. Both were corrected in the disposable probe; approved documents and fixtures are byte-unchanged. The pre-correction source was not preserved, so this is a record of that turn and **not a proven root cause**; only the corrected result is independently reproducible.
- Captured result after correction (`java-probe-stdout.txt`): `fixtures=85`, `ACCEPT parsed+decoded: 31/31`, `structural-layer negatives detected: 24/24`, `non-structural negatives deferred to business layers: 30`, `problems=0`.
- **Independent rerun (2026-11-19, this turn):** recompiled `IsoProbe.java` (`d782b052…6d6615`) with `/home/pazfernando/.local/opt/jdk-25/bin/javac` 25.0.5 and ran it against the same `packed-fixtures.tsv` (`88528eca…ac7cc3d`). Compile exit `0`, run exit `0`, stdout `4cdcc829…e9da3df` — **byte-identical** to the preserved `java-probe-stdout.txt`; stderr empty. Python rerun also exit `0` with stdout `e5172023…508056a`, byte-identical to the preserved `python-probe-stdout.txt`. Outputs were written to `technical-preflight-evidence/` before analysis. **No DE42 mismatch occurred**; the structural count matches. Python's per-vector list still prints nine `FAIL` labels for `ADMISSION_*` cases (`B-DE48-exact-999`, `CAP-over-request-1..7`, `D-stale`) that its own summary excludes as business/admission-layer and that neither probe implements; its summary remains `problems=0`. That is a probe display inconsistency, not a profile contradiction.
- Captured Python cross-check (`python-probe-stdout.txt`) agrees on all 31 ACCEPT parses and all parser-class negatives. It does not evaluate `ADMISSION_RESPONSE_CAPACITY` for ids named `CAP-over-request-*`; the Java classifier defers some capacity cases. Capacity/freshness belong to admission/business layers that neither probe implements.
- Still open: fixed-length padding rules are asserted only by fixtures; production codec selection (library such as jPOS vs custom) and offline dependency availability were not executed. Structural alignment of two throwaway probes is not codec implementation, QA, cryptographic or interoperability evidence.

### 3. DynamoDB atomic append / cursor / expiry / limits — LOCAL CAPTURED (§3a); AWS BEHAVIOR UNVERIFIED; earlier "script defect" claims withdrawn

- Environment: DynamoDB Local (`java -jar DynamoDBLocal.jar -inMemory`) was run with dummy static credentials against `127.0.0.1:8000`; no AWS account or credentials were used. The process was still alive at report time.
- Probe script: `/tmp/opencode/ddb-probe.sh` (12 KB), covering create-table, 4-item business/event/dedupe/head transaction, cancellation atomicity, duplicate-item rejection, `ClientRequestToken` idempotency, HEAD CAS increment, consistent reads, pagination, expiry metadata, TTL, item-size limits, 40- vs 101-action transaction limits, and a GSI read path.
- Execution evidence: timestamped table name `acq-preflight-1795124996-2796042` appears in generated payloads, and `ddb-tx40.json` / `ddb-tx101.json` were produced at 16:50, so the script ran at least to the P4-10 transaction-limit section. `ddb-big.json` (16:45) targets a different table name (`acq-preflight`), so it came from an earlier ad-hoc attempt.
- **No PASS/FAIL summary or per-check output was captured.** Therefore every individual DynamoDB check result is UNVERIFIED; the artifacts prove execution and inputs, not outcomes.
- **Correction (2026-11-19 follow-up) — two previously reported script defects were incorrect and are withdrawn.** Neither is a product defect, and no captured failure ever supported either claim:
  1. **`attribute_not_exists(pk)` in P4-2 is valid, not impossible.** A DynamoDB `Put` condition is evaluated against the **pre-existing item**, not against the item image being written. When no item exists for that key, `attribute_not_exists(pk)` is true and the conditional insert commits. P4-2 is the first write to `BIZ#k1/META`, `TX#t1/E#000000000001`, `DEDUPE#EV-1/META` and `BIZ#k1/HEAD` (P4-1 only creates the table), so the transaction is structurally achievable. The earlier claim that the transaction was guaranteed to cancel was wrong.
  2. **The `ClientRequestToken` is 16 characters, not 46.** The script passes `--client-request-token tok-preflight-'$T'`; the single quotes prevent expansion, so the literal token is `tok-preflight-$T` (16 chars) and `tok-dup-$T` (10 chars) for P4-4, both inside the 36-character limit. Reproduced in `technical-preflight-evidence/token-quoting-check.txt`. The 46-character figure would apply only to an expanded form (14 + 32); it is not what the script sends. Recorded observation, not a defect and not a blocker: the literal `$T` means the token is not table-specific despite the apparent intent. Treated as run isolation in §3a (tokens derived from the fresh table name), not as a fix to a defect; the check as executed in this historical run was valid.
- Two genuine observations remain, neither a product defect: the probe is a scripted harness with no captured result, and its checks run against DynamoDB Local, which does not prove AWS service behavior (capacity, throttling, Streams, IAM, encryption, TTL deletion timing). Those remain UNVERIFIED. A rerun needs captured per-check output, not script changes.

#### 3a. Captured local run — 2026-11-20 (supersedes "no per-check output" only)

The rerun requested above was executed under the resumed commission. Evidence is in `technical-preflight-evidence/ddb-run-2026-11-20/` (README + `MANIFEST.sha256`); stdout/stderr/exit/metadata were saved before interpretation.

- Binding: `/home/pazfernando/projects/acquiring-simulator`, `feat/wi-acq-auth-001`, HEAD `cfc456f4f469d9558a78fda8ff9b770b00817315`, origin `https://github.com/pazfernando/acquiring-simulator.git`, model provider `nan` / `deepseek-v4-flash` (configured default, no override).
- Endpoint: `http://127.0.0.1:8000`, the **pre-existing** in-memory `DynamoDBLocal.jar` process (PID 2787567, started 2026-11-19, cwd `/home/dynamodblocal`), dummy `local`/`local` credentials, no AWS account. No install, no deletion of existing tables, no Git write.
- Probe: pinned `ddb-probe.sh` (`c40ad7b2…5fb2066`) unmodified; run copy `1955043a…ebb0d2c` differs **only** in deriving the two `ClientRequestToken` values from the fresh table name, because the same long-lived emulator already cached the older literal tokens and replay would have misreported idempotency. This is run isolation, not a defect fix; the §3 withdrawal stands.
- Fresh table `acq-preflight-1795189977-2996627`; exit `0`; stderr empty; elapsed 14 s.
- Result: `SUMMARY: pass=21 fail=0`, grouped by request family:

| Family | Evidence in `probe-stdout.txt` | Outcome |
| --- | --- | --- |
| Atomic append | P4-2 four-item transaction `PASS`; post-commit strongly consistent count = 4 | **PASS** (local) |
| Cancellation | P4-3 whole-transaction cancel + no partial rows; P4-3b duplicate-item rejection | **PASS** (local) |
| Idempotency | P4-4 identical token+params no-op; different params → `IdempotentParameterMismatch` | **PASS** (local) |
| CAS | P4-5 conditional increment, stale CAS rejected, `lastSequence=1` | **PASS** (local) |
| Query cursor | P4-6 `Limit`+`LastEvaluatedKey` round-trip 2/2; `ConsistentRead` accepted | **PASS** (local) |
| Limits | P4-9 4096-byte accepted / >400KB rejected; P4-10 40-action accepted / 101 rejected | **PASS** (local) |
| Ancillary | P4-1, P4-7, P4-8, P4-11 | **PASS** (local) |

- Unchanged limits: DynamoDB Local does not prove AWS capacity, throttling, Streams, IAM, encryption, TTL deletion timing, GSI propagation or concurrent-conflict behavior. The eventual read returned 4 in-process, so the probe's "stale eventual read" branch did not fire; `ConsistentRead=true` on catch-up remains a contract requirement, not something measured here. P4-10's 101-action rejection is asserted only by non-zero exit, not by error code.


### 4. JWT / identity provider — actual issuer UNVERIFIED

- No issuer URL, JWKS, audience, subject registry entry, token lifetime or signing key was acquired or inspected. No login flow was started, consistent with the commission's prohibition on initiating credentials.
- Only **synthetic** claim fixtures exist (numeric `iat`/`exp` per the L1 correction). Those are document fixtures, not issuer validation.
- The specification's "existing JWT issuer" and single demo subject therefore remain an unverified external dependency. Relay/API Gateway/WebSocket authentication behavior is unproven.

### 5. AWS, ADOT and WebSocket behavior — DOCUMENTATION-ONLY

Findings are from AWS documentation and Context7, summarized in `adot-observability-research.md`; none were runtime-tested here:

- ADOT/OTel exports telemetry asynchronously; it is not a durable journal and does not prove task completeness. Lambda Telemetry API delivery can drop and has lifecycle/buffer limits.
- CloudWatch Logs subscriptions are at-least-once with duplicates, normally sub-three-minute but not SLO-bounded; non-retryable failures can disable forwarding.
- DynamoDB Streams is finite (24-hour retention), at-least-once, and requires idempotent consumers; a durable outbox must outlive the stream window.
- `HTTP API` JWT authorizer semantics must not be assumed to carry over to WebSocket `$connect`; header preservation, principal mapping and callback revalidation are unproven.
- **Java 25 + ADOT layer/runtime compatibility is unproven.** No layer ARN, architecture match or flush behavior was verified.

### 6. Capacity, cost and reconnect behavior — NOT COMPUTED

- No load test, quota check, pricing calculation or cost ceiling was produced. Proposed numeric capacities/SLOs remain unapproved and unvalidated.
- Reconnect/no-financial-replay logic was reviewed only as specification text; no runtime transport existed to test subscription-before-catch-up, epoch rotation, heartbeat or tombstone behavior.

## Exact evidence available

Artifacts are preserved in-repo at `evidence/preflight-2026-11-19/` (see its README for the full manifest). SHA-256 below are of the preserved copies.

| Path (in evidence folder unless noted) | SHA-256 |
|---|---|
| `ddb-probe.sh` | `c40ad7b2…5fb2066` |
| `IsoProbe.java` (corrected) | `d782b052…6d6615` |
| `java-probe-stdout.txt` | `4cdcc829…e9da3df` |
| `iso_probe.py` | `a895a6a3…ca16eaf5` |
| `python-probe-stdout.txt` | `e5172023…508056a` |
| `gen-fixtures.py` | `0be6f4a1…b703ff2` |
| `packed-fixtures.tsv` | `88528eca…ac7cc3d` |
| `ddb-tx40.json` | `faad75e9…ced26710` |
| `ddb-tx101.json` | `c688d5fd…058e5e53` |
| `ddb-big.json` (not copied; `/tmp/opencode`) | `697475b7…35b0a137` |

Re-execution evidence (2026-11-19, this turn) is in `technical-preflight-evidence/`; its README carries the full manifest. The two stdout files reproduce the preserved outputs exactly:

| Path (in `technical-preflight-evidence/`) | SHA-256 | Result |
|---|---|---|
| `java-rerun-stdout.txt` | `4cdcc829…e9da3df` | same bytes as `java-probe-stdout.txt`; 31/31 ACCEPT, 24/24, 30 deferred, `problems=0` |
| `java-rerun-stderr.txt` | empty (SHA-256 of empty file) | — |
| `java-rerun-exit.txt` | `0` | run exit 0 |
| `java-compile-exit.txt` | `0` | `javac` 25.0.5, exit 0 |
| `python-rerun-stdout.txt` | `e5172023…508056a` | same bytes as `python-probe-stdout.txt` |
| `python-rerun-exit.txt` | `0` | run exit 0 |
| `token-quoting-check.txt` | `9c649ba2…beabb1` | P4-2 token 16 chars, P4-4 token 10 chars |
| `run-metadata.txt` | `8100faa6…1c71c3` | UTC time, cwd, branch/HEAD binding, classpath |
| `input-sha256.txt` | `4dd7528b…260188` | hashes of probe sources and fixtures used |

Commands that produced the evidence in this report and the follow-up:

```
git rev-parse HEAD && git status --porcelain
sha256sum <the ten pinned artifacts>
/home/pazfernando/.local/opt/jdk-25/bin/java -version
/home/pazfernando/.local/opt/jdk-25/bin/javac -version
/home/pazfernando/.local/opt/jdk-25/bin/javac -d /tmp/opencode/javaprobe2 IsoProbe.java
/home/pazfernando/.local/opt/jdk-25/bin/java -cp /tmp/opencode/javaprobe2 IsoProbe packed-fixtures.tsv
python3 iso_probe.py packed-fixtures.tsv
JAVA_HOME=/home/pazfernando/.local/opt/jdk-25 /home/pazfernando/.local/opt/maven/bin/mvn -o -q -DskipTests compile
readlink -f /proc/<dynamodb-local-pid>/exe
# 2026-11-19 rerun (technical-preflight-evidence/)
/home/pazfernando/.local/opt/jdk-25/bin/javac -version && /home/pazfernando/.local/opt/jdk-25/bin/java -version
/home/pazfernando/.local/opt/jdk-25/bin/javac -d <tmp> ../evidence/preflight-2026-11-19/IsoProbe.java
/home/pazfernando/.local/opt/jdk-25/bin/java -cp <tmp> IsoProbe ../evidence/preflight-2026-11-19/packed-fixtures.tsv
python3 ../evidence/preflight-2026-11-19/iso_probe.py ../evidence/preflight-2026-11-19/packed-fixtures.tsv
bash /tmp/opencode/token-quoting-check.sh
```

## Unresolved items (must not be read as PASS)

1. ~~Real-Java vs Python ISO probe divergence around DE42~~ — corrected probes reproduced this turn (Java stdout byte-identical to the preserved run, stderr empty, exit 0, no DE42 mismatch); the **historical root cause remains unproven** because the pre-correction probe bytes were not preserved. The candidate explanation (DE32 declared fixed instead of `LL n1–11`) rests on the earlier turn's record only. Python's parser spec is not a strict mirror (DE32 kind `an` vs Java `n`, canonical charset check uses `isalnum`), and the two probes agree on these fixtures' outputs, not by construction. Capacity/freshness layers remain unimplemented by both.
2. ~~Whether either codec probe accepts all 85 vectors~~ — captured: 31/31 ACCEPT, 24/24 structural negatives, 30 deferred business-layer cases; `problems=0`.
3. Production codec selection (library vs custom) and offline dependency availability.
4. **DynamoDB — local checks now captured (2026-11-20); AWS behavior still UNVERIFIED.** The 2026-11-20 bounded run produced `pass=21 fail=0` with per-check output for atomic append, cancellation/no-partial-write, idempotency, CAS, cursor pagination and size/transaction limits against loopback DynamoDB Local (§3a). Real-service capacity, throttling, Streams, IAM, encryption, TTL deletion timing and concurrent-conflict behavior are not proven by an emulator and remain unverified. The two earlier "script defects" (46-character token, impossible P4-2 condition) remain **withdrawn** — they are not product defects and no captured failure ever supported them.
5. Real JWT issuer, audience, JWKS, lifetime, subject binding.
6. API Gateway WebSocket authentication, header preservation, callback/epoch behavior.
7. Java 25 ADOT layer/runtime compatibility and asynchronous delivery behavior.
8. Capacity, cost ceiling, latency and retention behavior under load.
9. Whether documentation-only AWS findings hold in the target account/Region.

## Decisions still required

- **QA:** installed and delegable (`ai-sdlc-qa` is on the shared active roster), currently available. Independent verification is a **downstream gate after implementation** under the shared workflow; it does not replace implementation-owned technical preflight, and this report makes no QA claim.
- **Security:** **unavailable** — not a callable agent. Security review remains required for the changed trust boundaries (JWT issuer/audience, WebSocket `$connect`, IAM separation, origin/loopback, redaction) when that gate is reached.
- **Owner:** owns any numeric capacity/SLO/cost ceiling and any residual risk acceptance. Architecture approval is valid; construction is not authorized in this turn.

## Explicit non-claims

This report does not claim: completed preflight; codec, cryptographic, HTTP, WebSocket or DynamoDB success on AWS; QA or Security approval; production readiness; deployment; or that the architecture approval covers implementation. It records what was observed, what was not, and where the evidence lives so the work can resume without repeating the interrupted run.
