# P2c4 — single full offline suite verification: actual results

Verification-only slice. No source, test, resource, `pom.xml`, specification or vector file was
edited; no test was rerun; no artifact from `p2b8-20261120T224049Z` was recreated.

- UTC window: start `2026-11-20T22:46:32Z`, Maven start `22:46:46Z`, finish `22:48:32Z`.
- Model: DeepSeek V4 Flash (provider `nan`), configured default, no override.
- Binding: `/home/pazfernando/projects/acquiring-simulator`, branch `feat/wi-acq-auth-001`,
  HEAD `cfc456f4f469d9558a78fda8ff9b770b00817315`, origin
  `https://github.com/pazfernando/acquiring-simulator.git`.
- Toolchain: Temurin JDK 25.0.5 (`/home/pazfernando/.local/opt/jdk-25`), Apache Maven 3.10.1
  (`/home/pazfernando/.local/opt/maven/bin/mvn`), offline (`-o`).
- Runtime inactive: no HTTP route, no `AwsRuntime` start, no deployment, no AWS/cloud call, no
  credential flow, no Git write, no publication, no deletion.

## Command (executed exactly once)

```
JAVA_HOME=/home/pazfernando/.local/opt/jdk-25 \
  /home/pazfernando/.local/opt/maven/bin/mvn -o -B test
```

Captured immediately to `test-stdout.txt`, `test-stderr.txt`, `test-exit.txt`
(`test-command.txt` holds the exact command).

## Actual aggregate

| Field | Observed |
| --- | --- |
| Exit code | **0** (`test-exit.txt`) |
| Build result | `BUILD SUCCESS` |
| Maven total time | `01:40 min` |
| Test classes executed | **63** (`Running com.acquiring…` count = 63; 63 Surefire XML) |
| Tests run | **959** |
| Failures | **0** |
| Errors | **0** |
| Skipped | **0** |
| Surefire`flakes` attribute | 0 for all 63 XML |
| Surefire aggregate line | `Tests run: 959, Failures: 0, Errors: 0, Skipped: 0` |

Per-package rollup (`surefire-by-package.tsv`):

| Package | Tests | Classes |
| --- | --- | --- |
| `com.acquiring.simulator` | 48 | 2 |
| `com.acquiring.simulator.aws` | 152 | 20 |
| `com.acquiring.simulator.channel` | 165 | 14 |
| `com.acquiring.simulator.domain` | 10 | 2 |
| `com.acquiring.simulator.domain.event` | 61 | 10 |
| `com.acquiring.simulator.protocol.iso8583` | 523 | 15 |
| **Total** | **959** | **63** |

Per-class results are in `surefire-summary.tsv`; copied Surefire XML/TXT are in this directory.
`grep -c` for `<failure` / `<error` elements across the 63 copied XMLs is 0.

## Freeze and correspondence

- `freeze-filelist.txt` — 282 inputs: every file under `src/main` (194) and `src/test` (68),
  `pom.xml`, all 13 files in `docs/ai-sdlc/work-items/WI-ACQ-OBS-001/vectors/`, and the six
  approved contract inputs (`requirements.md`, `acceptance-scenarios.md`, `iso8583-profile.md`,
  `channel-contract.md`, `observation-contract.md`, `decision-proposal.md`).
- `hashes-before.sha256` (282) and `hashes-after.sha256` (282) are **byte-identical**:
  `hashes.diff` is empty (`diff` exit 0). `hashes-verify.txt` re-checks the pre-run list:
  282/282 `OK`, `sha256sum -c` exit 0.
- Therefore the green run exercised exactly the frozen source, test, resource, `pom.xml` and
  approved oracle bytes.
- `head-before.txt` == `head-after.txt` (`cfc456f4…`), never rewritten.
- `git-status-before.txt` == `git-status-after.txt` (95 lines each, `git-status.diff` empty).
  The pre-existing modified/untracked work was preserved, not staged or reverted.
- `git-status-final.txt` adds two untracked files created **after** this run by the
  AI-SDLC Coordinator at `2026-11-20T22:49:47Z` (`delivery-status.md`,
  `operation-a-completion-plan.md`). They are outside this slice: not written, read as
  instruction, staged or modified here, and not in the 282-input freeze list. The only files
  this slice wrote are this evidence directory and the §54 record in
  `implementation-progress.md`.
- `surefire-freshness.txt`: all 63 XML mtimes fall inside the actual run window
  (0 outside); 0 copied-XML hash mismatches, so the copied reports are the reports this run
  produced. Pre-existing `target/surefire-reports` content was listed in
  `surefire-before-list.txt` and was not deleted.

## Interpretation limits

- This is local construction verification against SQLite, PostgreSQL/Testcontainers
  (`postgres:17-alpine`), DynamoDB Local (`amazon/dynamodb-local:2.6.1`) and in-process
  fixtures. It is **not** independent QA, Security review, human acceptance, business
  admission, runtime activation or deployment.
- A green suite does not prove AWS service behavior, production capacity/throttling, deployed
  IAM, key custody/rotation, TLS/mTLS or IAM/SigV4 principal binding, API Gateway wire behavior,
  JWT issuer/WebSocket behavior, ADOT compatibility or the end-to-end application.
- Offline mode means every dependency and container image was already present locally; network
  resolution was not exercised.

## C2 principal mapping — recorded open decision, not resolved here

The transport adapter (`C4HttpCarriageBridge`) still accepts a
`C4AuthenticatedTransportContext` supplied by the platform boundary. The exact approved clause
(`channel-contract.md` C2) is:

> "Require authenticated service principal with route-specific permission and server context/
> fixture resolution; no trust in X headers for identity. Mutual TLS or IAM-controlled ingress
> realization remains human architecture/Security gate; candidate recommendation uses private
> API Gateway IAM-authorized HTTPS with SigV4 service identity."

Owner decision context (`owner-decisions.md`): "single demo JWT principal and separate service
IAM"; the JWT authorizer is the console C1 path only. The open architecture/Security decision —
mutual TLS versus IAM-authorized SigV4 ingress — is **not** decided by this slice. No principal
allowlist, unsigned fallback or authentication behavior was added, and console JWT was not
treated as service identity. This verification proceeded independently of that gate.

## Remaining blockers

P1 G6 (atomic critical-path durability) and C2 principal realization remain open; Security
review, independent QA and end-to-end application remain open.
