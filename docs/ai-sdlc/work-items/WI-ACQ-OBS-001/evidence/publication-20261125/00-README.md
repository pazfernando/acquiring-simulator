# Publication record — WI-ACQ-OBS-001, 2026-11-25

Owner instruction: "commit and push everything up to here". Authorized scope:
publication on `feat/wi-acq-auth-001` only. No force, amend, rebase, tag, PR, merge
or deploy.

This record covers the second publication round, made on top of the previous
round recorded in `../publication-20261124/`.

## Outcome

**PUBLISHED**, first attempt, non-force, no rejection this time.

- Commit `4bd0aff227cd97a979f55bcd82019ad104398ff7` — seven files, 210 insertions.
- Push `88fe42d..4bd0aff` on `feat/wi-acq-auth-001`, exit code `0`, fast-forward.
- Remote `refs/heads/feat/wi-acq-auth-001` moved
  `88fe42d46167ef459aa0228817cb3618556eb2d3` → `4bd0aff227cd97a979f55bcd82019ad104398ff7`,
  confirmed with `git ls-remote` and equal to local HEAD.
- Nothing was forced, amended, rebased or tagged. No PR, merge, deploy or release.
- Unlike round 1, this round touches no `.github/workflows/` path, so the
  workflow-scope restriction did not apply and no credential change was needed.

## Binding at start

| Item | Value |
| --- | --- |
| realpath / toplevel | `/home/pazfernando/projects/acquiring-simulator` |
| branch | `feat/wi-acq-auth-001` |
| origin (fetch/push) | `https://github.com/pazfernando/acquiring-simulator.git` |
| origin credential content | none — plain HTTPS URL, no user or token embedded |
| HEAD | `88fe42d46167ef459aa0228817cb3618556eb2d3` |
| `origin/feat/wi-acq-auth-001` (tracking ref) | `88fe42d46167ef459aa0228817cb3618556eb2d3` |
| `refs/heads/feat/wi-acq-auth-001` (ls-remote) | `88fe42d46167ef459aa0228817cb3618556eb2d3` |

Local HEAD, the tracking ref and the actual remote ref all agreed before any work
started, so this round is a clean fast-forward with no divergence.

## What this round contains

Two documents were expected. The working tree held three more files than the
Coordinator's message described; they are listed below rather than silently
included or silently dropped.

| Path | State | Origin |
| --- | --- | --- |
| `docs/ai-sdlc/PROJECT.md` | modified (+4) | Coordinator: publication record for the 2026-11-24 round |
| `docs/ai-sdlc/work-items/WI-ACQ-OBS-001/WORK_ITEM.md` | modified (+10) | Coordinator: publication record for the 2026-11-24 round |
| `revisions/pending-dispatch-20261122.md` | untracked, 43 lines | unexpected — coordinator dispatch note |
| `revisions/pending-dispatch-continuation-not-sent-20261123.md` | untracked, 17 lines | unexpected — coordinator dispatch note |
| `revisions/pending-dispatch-continuation-sent-20261123.md` | untracked, 3 lines | unexpected — coordinator dispatch note |
| `evidence/publication-20261125/` | new | this record |

### Why the three unexpected files were included

They are coordinator dispatch notes under `revisions/`, which per the repository
instructions are historical evidence rather than live specification. They belong to
the same artifact family as `revisions/pending-dispatch-20261120.md`, which is
already tracked (added by commit `5bbd4eb`). The 2026-11-22 and 2026-11-23 notes were
written after that archive commit, so they were never picked up.

The owner's instruction was "everything up to here", and leaving them untracked
would have kept the tree dirty, contradicting that instruction. They were
*inspected* before being staged: they contain dispatch instructions, report paths,
SHA-256 digests, file names and one internal ACP session identifier. They contain no
credential, no key and no secret. Internal `acp:` session identifiers already appear
in published tracked files (`WI-ACQ-AUTH-001/WORK_ITEM.md`,
`revisions/work-item-opencode-commission-2026-11-18.md`,
`handoffs/continuation-20261123/rev2-review-dispatch.md`), so this adds no new class
of exposure.

If the owner does not want them published, they can be removed by a normal
follow-up commit; nothing here rewrites history.

## Safety gate

Checked before staging, explicitly:

- `.terraform` directories on disk: **0**.
- `*.tfstate`, `*.tfstate.*`, `*.tfplan`, `*.tfvars`, `*.tfvars.json` on disk: **0**.
- No candidate path matches `.terraform/`, state, plan, tfvars, key, PEM or `.env`.
- A secret-pattern scan over every candidate file (`BEGIN ... PRIVATE KEY`, AWS
  `AKIA`/`ASIA` ids, `ghp_`/`github_pat_` tokens, `xox` Slack tokens,
  `x-amz-security-token`, `client_secret=`/`password=` assignments,
  `aws_secret_access_key`) returned **no matches**.
- Nothing under `.github/workflows/` is touched this round, so the workflow-scope
  rejection that blocked the previous round does not apply.

No `.terraform`, `*.tfstate*`, `*.tfplan`, `*.tfvars*`, key material or secret
literal is part of this publication.

## Files in this directory

| File | Content |
| --- | --- |
| `00-README.md` | This record, including the outcome above. |
| `01-status.txt` | Binding, full `git status -uall`, area grouping, totals. |
| `02-commit.txt` | Commit SHA, file count, stat, name-status, verbatim message. |
| `03-push.txt` | Verbatim push output, exit code, refs before/after. |

`00-README.md` and `01-status.txt` were written before the commit, so they are inside
`4bd0aff`. `02-commit.txt` and `03-push.txt` describe that commit and its push and
therefore cannot be; they are carried by a small follow-up commit, as in the previous
round.
