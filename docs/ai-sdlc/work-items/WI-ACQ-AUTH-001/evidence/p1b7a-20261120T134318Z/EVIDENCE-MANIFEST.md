# p1b7a evidence manifest — recovery compile + targeted integrity tests

Work item: WI-ACQ-AUTH-001
Phase: p1b7a (single short recovery turn)
Date (UTC): 2026-11-20

## Binding

- Repository: `/home/pazfernando/projects/acquiring-simulator`
- Branch: `feat/wi-acq-auth-001`
- HEAD at start: `cfc456f4f469d9558a78fda8ff9b770b00817315` (expected and matched)
- Model: provider `nan`, model `deepseek-v4-flash` (no override)
- JDK: `/home/pazfernando/.local/opt/jdk-25` (JDK 25, explicit `JAVA_HOME`)
- Maven: `/home/pazfernando/.local/opt/maven/bin/mvn`
- Runtime: inactive. No AWS calls, no service start, no containers.
- Git: read-only. No writes, no publication.

## Scope

1. Compile the existing (already authored) observation-integrity changes.
2. Run only the targeted integrity tests if they already exist.
3. Author nothing new if the three required integrity tests are already present.

Existing integrity tests found before any edit (no authoring needed):

- `changedPayloadForTheSameIdentityIsAConflictWithoutOverwrite` — persisted conflict marker.
- `duplicateConflictRepeatsTheSameMarkerAndConsumesNoSequenceOrSlot` — idempotent replay, no extra sequence/slot.
- `unauthorizedContextCannotPoisonTheConflictMarker` — auth/context before marker/dedupe.

## Artifacts

| File | Purpose |
| --- | --- |
| `BINDING.txt` | Binding, toolchain, dirty/untracked counts at start |
| `git-status-before.txt` | Full porcelain status before any command |
| `integrity-files-before.sha256` | SHA-256 of the integrity source and test files before build |
| `javac-version.txt`, `mvn-version.txt` | Toolchain versions actually used |
| `compile-stdout.txt`, `compile-stderr.txt`, `compile-exit.txt` | `mvn -o -DskipTests compile` capture |
| `test-compile-stdout.txt`, `test-compile-stderr.txt`, `test-compile-exit.txt` | `mvn -o test-compile` capture |
| `test-stdout.txt`, `test-stderr.txt`, `test-exit.txt` | Targeted integrity test run capture |
| `integrity-files-after.sha256` | Same hashes after build (frozen check) |
| `git-status-after.txt` | Porcelain status after build |
| `RESULTS.md` | Outcomes, exact commands, next command |

## Rules honoured

- Original event, hash, context and business outcome immutable.
- No raw conflicting payload copied into evidence (only hashes).
- No extra sequence or slot consumed.
- Auth/context checked before marker/dedupe.
- No specification edits, no new features, no capacity defaults.
