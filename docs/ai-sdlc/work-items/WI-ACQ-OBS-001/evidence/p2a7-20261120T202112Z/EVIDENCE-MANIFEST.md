# P2a7 evidence manifest — pure NEW-exchange 826-byte admission budget

- Work item: WI-ACQ-OBS-001 (protocol slice).
- Phase: P2a7 — one pure validator for the approved 826-byte NEW-request
  admission budget, plus actual packed request/response byte-fixture tests.
  Not financial admission, trust or registry.
- Run start: `2026-11-20T20:21:12Z`; evidence folder
  `p2a7-20261120T202112Z`.
- Repo: `/home/pazfernando/projects/acquiring-simulator`.
- Branch: `feat/wi-acq-auth-001`.
- HEAD: `cfc456f4f469d9558a78fda8ff9b770b00817315` (unchanged; no Git write).
- Origin: `https://github.com/pazfernando/acquiring-simulator.git`.
- Model: provider `nan`, model `deepseek-v4-flash`; `model_override=none`.
- Toolchain: Temurin JDK 25.0.5 and Apache Maven 3.10.1 at
  `~/.local/opt/jdk-25` / `~/.local/opt/maven` (`toolchain-version.txt`).
  Runtime inactive. No service start, AWS call, HTTP, storage change,
  credential flow, publication, deletion or specification/vector edit.

## Approved rule applied

Exact source (`iso8583-profile.md`, P2a): **“Structural parser accepts DE48 up
to 999 bytes. Exchange admission before business claim/dispatch requires request
DE48 <=826 bytes. Reserve 173 bytes for the longest supported suffix:
;actor=PROCESSOR;stage=BEFORE_NETWORK;result=PROCESSOR_REJECTED;
reason=DISALLOWED;evidence= plus 64 ASCII evidence-ID bytes plus ;at= plus 14
digits. Every P4 alternative fits. … 827–999 structurally valid requests fail
ADMISSION_RESPONSE_CAPACITY, no claim or dispatch. Never discover response
overflow after a permitted send, omit echoed keys, shorten refs or truncate
evidence.”**

The reserve is derived in production, not copied as a magic capacity:

| Component | Source | Bytes |
| --- | --- | --- |
| six response tail keys | `Iso8583De48ResponseParser.RESPONSE_KEYS` (`;`+key+`=`) | 44 |
| worst actor/stage/result/reason | accepted combinations probed through `Iso8583P4MatrixValidator` | 51 |
| `;evidence=` value | approved 64-byte maximum | 64 |
| `;at=` value | fixed 14 digits | 14 |
| **reserve** | 44 + 51 + 64 + 14 | **173** |
| **admission bound** | 999 − 173 | **826** |

The 51-byte metadata maximum is `PROCESSOR` (9) + `BEFORE_NETWORK` (14) +
`PROCESSOR_REJECTED` (18) + `DISALLOWED` (10). Combining per-key maxima instead
would invent an unapproved 181-byte reserve, so only combinations the P4 matrix
actually accepts contribute. Structural packing/scanning keep 999. A 999-byte
response remains representable: `CAP-response-3` is exactly the 826-byte
request prefix plus the 173-byte worst suffix.

## Delivered files (3 new; no pre-existing input edited)

| File | SHA-256 |
| --- | --- |
| `Iso8583ExchangeBudgetValidator.java` | `981c858cd523a73c2a76883dacc27a144934312ba60bb4585732e6d7aae3850b` |
| `Iso8583ExchangeBudgetDecision.java` | `9a0e329331c8ee68deaf6f4bf8a9b38a31cb2abd42485732beca757e1d057049` |
| `Iso8583ExchangeBudgetValidatorTest.java` | `f003ef3cbd9193522996b0adc9493576f54435f6677e83c01f7fd77d13239d8d` |

No existing source, profile, vector, fixture, resource or test was modified.
Freshness, domain, P4, binding, registry/mandate/route resolution, response
packing, C4, HTTP and runtime remain untouched.

Behavior:

- `NEW_FIRST_DISPATCH`: `1..826` → `ACCEPTED`; `827..999` → `REJECTED` with
  first-failure label `ADMISSION_RESPONSE_CAPACITY`; `<1` or `>999` fails closed
  as a structurally impossible direct call.
- `AUTHORIZED_HISTORICAL_REPLAY` / `LATE_EVIDENCE` → `NOT_APPLICABLE` without
  reading a fresh length; the persisted original is not re-encoded or sent
  again.
- The mode is a trusted server-side enum, never decoded from the wire.
- The API takes only a byte count, so it cannot drop `prior`, unknown keys or an
  authorization reference, mint a fresh alias or truncate to fit.

## Deterministic tests (actual)

`Iso8583ExchangeBudgetValidatorTest`: **7/7**, failures 0, errors 0, skipped 0,
exit `0`, stderr empty.

Exercised approved fixtures (11 distinct vectors):

- `B-DE48-exact-999` — actual packed hex, DE48 999, structurally packs
  byte-exact, NEW budget `REJECTED`.
- `CAP-request-826` — actual packed hex, DE48 826, `ACCEPTED`.
- `CAP-request-827` — actual packed hex, DE48 827, `REJECTED`.
- `CAP-over-request-1..7` — seven actual packed 827-byte requests; each parses
  with all 17 extension keys and `prior` intact, then rejects.
- `CAP-response-3` — actual 999-byte response = 826 prefix + 173 suffix; parses
  as canonical response and re-packs byte-exact.

Focused cases also pin the derivation (44/51/173/826), the exact 826/827 edge,
replay/late non-application (827, 999 and `-1` ignored), trusted-mode
requirement and structural 1..999 input guard.

Protocol regression `Iso8583*Test`: **419/419**, failures 0, errors 0, skipped
0, exit `0`, stderr empty. That is 412 prior (P2a5's 407 + P2a6's 5) plus these
7; P2a7 changed no existing protocol test.

## Hash and status correspondence

- `hashes-before.sha256` (pre-edit, 181 inputs) → `hashes-frozen-pre-run.sha256`
  (post-edit/pre-test, 184 inputs) differs only by the **three added files**.
- `hashes-frozen-pre-run.sha256` and `hashes-after.sha256` are byte-identical
  (`5e0fff7c473fac654032b0637ce90e238a43aab9c94896ca3abadee5e77f272a`);
  `hashes-frozen-vs-after.diff` is empty, exit `0`.
- `hashes-existing-vs-before.diff` is empty, exit `0`: every pre-existing input
  is byte-identical, including `Iso8583P4MatrixValidator.java`
  (`2aa986da571753f4de21c1b3dab00849f8cbe11d1bc092747de3400eb8d1a771`),
  `Iso8583Packer.java`
  (`1a6eb030ee51a6b126d51c65c0a1b239dea9bff15002ab53624354f4950b99d0`),
  `Iso8583De48ResponseParser.java`
  (`27ce332e7a6840830f4a0092ecd581579a9aa2abaf5aee425feb63e45e48f92e`) and
  `Iso8583AdmissionMode.java`
  (`498621629ab1bb107162493893cdff810011c0bb683d38f2204376581dccefcd`).
- `git-status-frozen-pre-run.txt` = `git-status-after.txt`; HEAD after equals
  the binding above. `targeted-stdout.txt` records main recompilation of 123
  sources, test recompilation of 51 and the 7/7 run.
- A first invocation without the toolchain on `PATH` produced exit `127` before
  any code ran; its stdout/stderr/exit are preserved as
  `targeted-*-attempt1-path-missing.txt`. No test failure occurred.
- The P2a6 manifest referenced `mvn-version.txt` / `java-version.txt`; those
  files were absent when this run began, consistent with the toolchain being
  off `PATH`. This manifest does not alter or recreate P2a6 evidence.

## Boundary

Local JUnit/byte-fixture evidence only. This is **not** authentication,
attestation/crypto verification, registry/fixture/mandate/route resolution,
domain re-validation, P4/binding admission, duplicate/conflict resolution,
response construction beyond structural packing, C4 carriage, HTTP/runtime
behaviour, independent QA, Security review or human acceptance. P1 and G6
remain open. No Git write or publication was performed.
