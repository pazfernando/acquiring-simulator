# MANIFEST — t3-fullsuite-20261122

Evidence directory: `docs/ai-sdlc/work-items/WI-ACQ-OBS-001/evidence/t3-fullsuite-20261122/`
Slice: one javadoc correction plus one full offline Maven suite run.
Binding: branch `feat/wi-acq-auth-001`, HEAD `5bbd4eb5d49be036e695e5f25a8c83c4f7d5ac14` (unchanged).
Toolchain: OpenJDK 25.0.5, Apache Maven 3.10.1, offline `-o`.
Aggregate: 978 tests / 67 classes / 0 failures / 0 errors / 0 skipped / exit 0 / BUILD SUCCESS.

| File | Bytes | SHA-256 |
| --- | --- | --- |
| `RESULTS.md` | 6347 | `7859c8b7b1a0382400cd921980bf59e3f6674df33536c0528225bcac90610ebe` |
| `branch.txt` | 21 | `963e5dbe5b0462f5e36d3e0fbd31f8baff49a40bac39e44863c2cda9672493ad` |
| `comment-fix.diff` | 1304 | `eb5c3319afb7982ad7763ed6aef2fe6fdb48b194c8f3bf532bd4f79eff94d880` |
| `head.txt` | 41 | `a837fbe88ca98c8aca28f36ef161c6800474df5937302b9c502a0fbb01d9818b` |
| `freeze-pre.txt` | 41635 | `fa2a1d0bd8a5a94b792c539247ec96400a2e6812c70fbb0260072e2235d11708` |
| `freeze-post.txt` | 41635 | `fa2a1d0bd8a5a94b792c539247ec96400a2e6812c70fbb0260072e2235d11708` |
| `freeze-diff.txt` | 0 | `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855` |
| `fullsuite-exit.txt` | 2 | `9a271f2a916b0b6ee6cecb2426f0b3206ef074578be55d9bc94f6f3fe3ab86aa` |
| `fullsuite-stdout.txt` | 39303 | `d6eb0bd3b6fcc4ab40e646d64ab61111c11bb43f2c25bd0db938cc91f22737b4` |
| `fullsuite-stderr.txt` | 1355 | `b1a3c73e5dbaa389594006c7458dbf6a3e3def465b427f4cc4f184d9688d84b5` |
| `surefire-summary.txt` | 212 | `6e3462b6f8140d8c266710ba749129622d4559d515c5097a465539a6dc7040c0` |

`freeze-pre.txt` and `freeze-post.txt` are identical byte-for-byte (same SHA-256, empty
`freeze-diff.txt`), covering 292 inputs: 211 `src/main` Java, 72 `src/test` Java, 8
`src/main/resources` files and `pom.xml`.

Limitations: the freeze baseline is the working tree at slice start (post-javadoc-fix, with
pre-existing uncommitted owner/coordinator edits), not the committed tree at `5bbd4eb`. The
first shell invocation in the slice failed with exit 127 (`mvn: command not found`) and executed no
test; its outputs were replaced by the real run's files listed above. The suite was executed once.
