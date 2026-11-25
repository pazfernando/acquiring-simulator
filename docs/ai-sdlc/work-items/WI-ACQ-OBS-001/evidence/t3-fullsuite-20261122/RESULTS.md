# T3 full offline suite — 2026-11-22

**Status: developer verification evidence on one working tree. Not independent QA, not Security
review, not human acceptance, not deployment.**

This slice did two bounded things: it corrected one stale javadoc sentence in
`AuthorizationService` and it ran the complete offline Maven suite once. It added no feature, no
production behaviour, no assertion change, and it skipped no test.

## 1. Binding and runtime

| Item | Value |
| --- | --- |
| Repository | `/home/pazfernando/projects/acquiring-simulator` |
| Branch | `feat/wi-acq-auth-001` (`branch.txt`) |
| HEAD | `5bbd4eb5d49be036e695e5f25a8c83c4f7d5ac14` (owner commit, unchanged before and after; `head.txt`) |
| Runtime | local JVM, `JAVA_HOME=/home/pazfernando/.local/opt/jdk-25` |
| Java | OpenJDK 25.0.5 2026-10-20 LTS |
| Maven | Apache Maven 3.10.1 (`3afd8084b5629f80b97750906a719879ed4cd283`), offline `-o` |
| Test infrastructure | Testcontainers 2.0.5, `amazon/dynamodb-local:2.6.1`, SQLite for the local-profile core |
| Cloud | No AWS call, no credentials, no deployment, no publication, no Git write |
| Working tree | Already dirty at slice start (owner/coordinator edits); this slice added the javadoc fix and this evidence directory |

`mvn`/`java` are not on the default `PATH` in this session; the toolchain is installed under
`/home/pazfernando/.local/opt`. Every command below therefore exported `JAVA_HOME` and prepended
`$JAVA_HOME/bin` and the Maven `bin` explicitly.

## 2. Item 1 — stale comment corrected

`src/main/java/com/acquiring/simulator/domain/AuthorizationService.java`, javadoc of the
late-evidence constructor only (`comment-fix.diff`). It used to claim:

> This constructor is not Spring-wired: the `@Autowired` constructor above remains the runtime
> binding and `AwsRuntime` is unchanged.

That is false at these bytes: `AwsRuntime.atomicService(...)` now calls exactly this constructor and
passes `LateEvidenceAdmissionGateway.over(...)` alongside the claim, dispatch-intent, result and
network-result gateways. It now says:

> This is the constructor `AwsRuntime#atomicService` binds for the Lambda runtime, passing the
> late-evidence gateway alongside the other atomic gateways; the `@Autowired` constructor above
> remains only the Spring/JDBC local harness binding.

`comment-fix.diff` contains three changed lines, all inside `/** ... */`; no code, signature,
annotation, import or string literal changed. The `@Autowired` four-argument constructor is still
the Spring/JDBC harness binding, which is what the corrected text says.

## 3. Item 2 — full offline suite, one invocation

Command (once, after the comment fix):

```
mvn -o -B test
```

| Item | Value |
| --- | --- |
| Exit | `0` (`fullsuite-exit.txt`) |
| Result | `BUILD SUCCESS` |
| Aggregate | **Tests run: 978, Failures: 0, Errors: 0, Skipped: 0** |
| Test classes | **67** |
| Total time | `01:48 min` |
| Artifacts | `fullsuite-stdout.txt`, `fullsuite-stderr.txt`, `surefire-summary.txt` |

The aggregate was cross-checked two ways: the Surefire console line in `fullsuite-stdout.txt`, and
an independent sum of the `tests/failures/errors/skipped` attributes across the 67
`target/surefire-reports/TEST-*.xml` files. Both give 978 / 0 / 0 / 0.

Stderr holds only non-fatal JVM notices (Mockito self-attach/byte-buddy agent, SQLite native
access); nothing there indicates a failed test.

**Invocation-disclosure.** The first shell attempt in this slice ran `mvn -o -B test` with the
default `PATH` and returned **exit 127, `mvn: command not found`**. It started no Maven process and
executed no test; its empty stdout and the 127 message were overwritten by the real run's outputs.
The suite itself ran **exactly once**, with the explicit toolchain paths above.

## 4. Freeze

292 inputs were hashed before the run: 211 `src/main` Java files, 72 `src/test` Java files, 8
`src/main/resources` files and `pom.xml`.

| Check | Result |
| --- | --- |
| `freeze-pre.txt` SHA-256 | `fa2a1d0bd8a5a94b792c539247ec96400a2e6812c70fbb0260072e2235d11708` |
| `freeze-post.txt` SHA-256 | `fa2a1d0bd8a5a94b792c539247ec96400a2e6812c70fbb0260072e2235d11708` |
| `diff -u freeze-pre.txt freeze-post.txt` | empty, exit 0 (`freeze-diff.txt`, 0 bytes) |
| HEAD before/after | identical, `5bbd4eb` |

The freeze held: the suite mutated no main source, test source, resource or `pom.xml`. **Limitation
disclosed:** the baseline is the working tree as it stood at the start of this slice, i.e. it
includes the javadoc fix and the pre-existing uncommitted owner/coordinator changes; it proves the
run mutated nothing, not that these are the owner-committed bytes at `5bbd4eb`.

## 5. Did the comment fix change behaviour?

No. The diff touches javadoc text only; the compiled artifact is unchanged in behaviour, and the
same full suite that covers `AuthorizationService` (including
`AwsRuntimeAtomicWiringIntegrationTest`, 10 tests, and `LateEvidenceServiceWiringTest`, 11 tests)
is green at 978 / 0 / 0 / 0. The run is evidence of no regression, not a proof of equivalence beyond
the byte-level fact that no executable line changed.

## 6. What this proves and what it does not

**Proves.** At these bytes, with this toolchain, the complete offline suite compiles and passes:
978 tests across 67 classes, no failure, error or skip. The late-evidence constructor is the one the
Lambda runtime binds, and its javadoc now says so.

**Does not prove.** No integrated payment. Every green case is in-process (DynamoDB Local,
PostgreSQL/Testcontainers, SQLite, stub issuer). Absent: the real authenticated boundary (API
Gateway, mTLS, OAuth `private_key_jwt`), real Lambda invocation, real issuer/network round trip,
mounted internal ISO route, deadline persistence, timeout-observation vocabulary,
history/query/cursor/expiry, Streams/notifier/WebSocket, console, ADOT, Terraform, CI, and
independent QA/Security/acceptance.

## 7. Non-claims

No Git write, stage, commit, push, PR, merge, deploy, release, publication, AWS call, credential
flow, provider mutation, approved-specification edit or capacity default occurred. No onward
delegation, no model override. `delivery-status.md` was not edited. Local implementation evidence is
not independent QA, Security review or human acceptance; every traceability row stays
`independent verification: PENDING`.
