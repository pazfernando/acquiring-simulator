# DynamoDB Local probe — captured run — 2026-11-20

Status: this directory holds the **first captured per-check output** for the DynamoDB probe that `technical-preflight.md` §3 previously recorded as UNVERIFIED. Stdout, stderr, exit code and metadata were written here **before** interpretation. These are diagnostic and local-only: **not** product code, **not** AWS service behavior, **not** QA/Security evidence.

Authority: `../../owner-decisions.md`, "Architecture approval and technical-preflight commission" (2026-11-19), resumed under the coordinator instruction of 2026-11-20 ("continúa con lo pendiente"). Restricted to loopback DynamoDB Local with dummy credentials. No AWS calls, no real credentials, no provisioning, no deletion of existing tables, no global installs, no Git writes.

## Binding observed

| Field | Value |
| --- | --- |
| Repository | `/home/pazfernando/projects/acquiring-simulator` |
| Branch | `feat/wi-acq-auth-001` |
| HEAD | `cfc456f4f469d9558a78fda8ff9b770b00817315` |
| Origin | `https://github.com/pazfernando/acquiring-simulator.git` (credential-free URL) |
| Model | provider `nan`, `deepseek-v4-flash` — configured default, no override |
| Endpoint | `http://127.0.0.1:8000` (loopback only) |
| Emulator | pre-existing process PID `2787567`, `java -jar DynamoDBLocal.jar -inMemory`, cwd `/home/dynamodblocal`, started 2026-11-19 |
| Credentials | dummy static `local`/`local`, region `us-east-1`; no AWS account |
| AWS CLI | `aws-cli/1.44.49` (Python 3.12.3, botocore 1.42.59) |
| Run | `2026-11-20T15:52:53Z`, elapsed 14 s, exit `0`, stderr empty |

## Probe identity and the one deliberate difference

The pinned probe source is `../../evidence/preflight-2026-11-19/ddb-probe.sh`, SHA-256 `c40ad7b296bf452ee2d1006327aa89126b53f20fdc7e5e02f6af0b99f5fb2066`. It was **not** modified.

The run used a copy at `/tmp/opencode/ddb-probe-scoped-20261120.sh` (SHA-256 `1955043a8e317e0827f39e14497fffa1074c2cf04ca44300e6ed250feebb0d2c`) whose **only** difference is the two `ClientRequestToken` arguments, which are derived from the fresh table name instead of being a fixed literal (`scoped-token-diff.txt` is the exact diff, `scoped-token-diff-exit.txt` = `1`).

Why: the emulator is the **same long-lived in-memory process** used by the interrupted 2026-11-19 runs, so its request-token cache already holds the previously used literal tokens. Replaying a literal token against a different table would return `IdempotentParameterMismatch` on the first call and misreport the idempotency check. Scoping the token to the run is a **run-isolation requirement, not a defect fix**; no check body was altered and the earlier withdrawal of the token-length and `attribute_not_exists(pk)` claims stands unchanged.

Fresh table: `acq-preflight-1795189977-2996627`. Tokens: `tok-preflight-1795189977-2996627` (31 chars) and `tok-dup-1795189977-2996627` (23 chars), both inside the 36-character limit.

## Files

| File | SHA-256 | Role |
| --- | --- | --- |
| `commands.txt` | `ed31350d…e6f61f` | Exact commands run. |
| `run-metadata.txt` | `8ec72faa…a3562c` | Time, binding, endpoint, emulator, CLI, auth mode. |
| `probe-stdout.txt` | `077142a6…d626bc` | Captured probe stdout (36 lines). |
| `probe-stderr.txt` | `e3b0c442…b855` (empty) | Captured stderr. |
| `probe-exit.txt` | `9a271f2a…ab86aa` | `0`. |
| `elapsed.txt` | `6f00a609…8842b` | Wall time in seconds. |
| `assertions.txt` | `8ddcb768…15897` | Per-check PASS/FAIL grouped by check family. |
| `scoped-token-diff.txt` | `6d9dd111…e26e93` | Only difference between pinned probe and run copy. |
| `probe-sha256.txt` | `31b1393c…e1e7e0` | Hashes of pinned source and run copy. |
| `MANIFEST.sha256` | — | Hashes of the files above (excludes itself). |

## Result

`SUMMARY: pass=21 fail=0`, exit `0`, stderr empty.

| Family | Checks | Outcome |
| --- | --- | --- |
| Atomic append | P4-2 four-item transaction commits; all 4 rows visible to a strongly consistent read | PASS |
| Cancellation | P4-3 condition failure cancels the transaction with no partial rows; P4-3b duplicate item in one transaction rejected | PASS |
| Idempotency | P4-4 identical token+params is a no-op success; same token + different params rejected (`IdempotentParameterMismatch`) | PASS |
| CAS | P4-5 conditional HEAD increment succeeds; stale CAS rejected; `lastSequence=1` (single allocation) | PASS |
| Query cursor | P4-6 page1=2, page2=2 via `LastEvaluatedKey`; `ConsistentRead` accepted on base-table Query | PASS |
| Limits | P4-9 4096-byte item accepted, >400KB rejected; P4-10 40-action transaction accepted, 101-action rejected | PASS |
| Ancillary | P4-1 create-table; P4-7 expiry revision metadata; P4-8 TTL enabled on `expiresAt`; P4-11 GSI query path | PASS |

## Limits of this result (do not read as AWS proof)

- DynamoDB Local is an emulator. It does not prove real-service capacity, throttling, adaptive capacity, Streams delivery, IAM enforcement, encryption, TTL deletion timing, GSI propagation delay, or transaction conflict semantics under concurrency.
- The post-commit eventual read returned `4` in the same process (`default(eventual)=4 consistent=4`); DynamoDB Local does not exhibit the cross-replica staleness the probe's optional stale-read branch looks for. The consistency requirement for catch-up remains a contract property (always set `ConsistentRead=true` on catch-up), not something this run disproves.
- P4-10's 101-action rejection is the documented 100-action transaction cap as emulated locally; the exact error code was not separately asserted beyond non-zero exit.
- The probe asserts behavior of specific expressions and limits. It is not an implementation, not a data-model review, and not evidence that the approved contract is complete.
