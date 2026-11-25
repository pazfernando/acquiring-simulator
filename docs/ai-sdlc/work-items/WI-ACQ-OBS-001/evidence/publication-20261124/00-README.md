# Publication record — WI-ACQ-OBS-001, 2026-11-24

Owner instruction: commit everything produced so far and push to the task branch
("haz commit y push hasta acá"). Authorized scope: publication of current work on
`feat/wi-acq-auth-001` only. No PR, no merge, no deploy, no tag, no force-push.

## Outcome

**PUBLISHED.** The branch is on the remote at `3a2bb58`.

- Commit created locally: `3a2bb5894179e30cbeab6ec37223486c46734293` (591 files).
- First push attempt: **REJECTED** by GitHub. The credential was a Personal Access
  Token without the `workflow` scope, so it could not create `.github/workflows/*`.
  A permission rejection, not a non-fast-forward.
- The owner then granted the missing permission. The push was retried unchanged
  (same commit, same branch, no force) and **succeeded** on the second attempt.
- Remote ref `origin/feat/wi-acq-auth-001`: `781dc0519a2643501b32b4f8ec3bbd58a891d4aa`
  before, `3a2bb5894179e30cbeab6ec37223486c46734293` after. Fast-forward.
- Nothing was amended, rebased, tagged or forced at any point.
- A small follow-up commit carries this record plus `02-commit.txt` and
  `03-push.txt`, which the publication action itself produced and therefore could
  not be inside `3a2bb58`.

## Binding at start

| Item | Value |
| --- | --- |
| realpath / toplevel | `/home/pazfernando/projects/acquiring-simulator` |
| branch | `feat/wi-acq-auth-001` |
| origin (fetch/push) | `https://github.com/pazfernando/acquiring-simulator.git` |
| HEAD before | `5bbd4eb5d49be036e695e5f25a8c83c4f7d5ac14` |

## What would have been published

The remote-tracking ref `origin/feat/wi-acq-auth-001` was at `781dc05`, so the push
was a fast-forward of **two** commits, not one:

1. `5bbd4eb` — "Archive remaining simulator artifacts from the agent workspace"
   (pre-existing local commit, authored before this session, never pushed).
2. `3a2bb58` — this session's work.

Both were published by the successful retry. The operator should know that resolving
the token scope published both, not just this session's commit. The older commit was
already in the branch history and was left untouched.

## Files in this directory

| File | Content |
| --- | --- |
| `01-precommit-status.txt` | Binding, full `git status -uall`, totals, area grouping, safety gate. Committed in `3a2bb58`. |
| `02-commit.txt` | Commit SHA, file count, stat, name-status summary, verbatim message. |
| `03-push.txt` | Verbatim output of the **first (rejected)** push, exit code, interpretation, local vs remote refs. |
| `00-README.md` | This record, including the successful retry below. |

`02-commit.txt` and `03-push.txt` are produced by the publication action itself, so
they could not exist inside `3a2bb58`. They are carried by the small follow-up commit
that also contains this file.

## Safety gate

- On-disk `.terraform` directories: 0. On-disk `*.tfstate`, `*.tfstate.*`, `*.tfplan`,
  `*.tfvars`, `*.tfvars.json`: 0.
- No staged path matches `.terraform/`, state, plan, tfvars, key, PEM, `.env`.
- No added line contains a private-key block or a secret literal. The three
  "BEGIN ... PRIVATE KEY" grep hits are the detector regex and leak-scan transcripts,
  not key material.
- `infra/terraform/.gitignore` covered `.terraform/`, `.terraform.tfstate.lock.info`,
  `*.tfstate`, `*.tfstate.*`, `*.tfplan` and crash logs. It did not cover `*.tfvars`,
  so `*.tfvars` and `*.tfvars.json` were added to it in this session to close that gap.
  No such files existed anyway.

## Retry — successful push

Owner reported the permission was granted. The retry followed the same rules: no
force, no amend, no rebase, no tag, no PR, no merge, no other changes.

Preconditions re-verified immediately before pushing:

| Item | Value |
| --- | --- |
| HEAD | `3a2bb5894179e30cbeab6ec37223486c46734293` |
| branch | `feat/wi-acq-auth-001` |
| `origin/feat/wi-acq-auth-001` (local tracking ref) | `781dc0519a2643501b32b4f8ec3bbd58a891d4aa` |
| uncommitted | only `00-README.md`, `02-commit.txt`, `03-push.txt` (this directory) |

Command and output, verbatim:

```
$ git push origin feat/wi-acq-auth-001
To https://github.com/pazfernando/acquiring-simulator.git
   781dc05..3a2bb58  feat/wi-acq-auth-001 -> feat/wi-acq-auth-001
EXIT_CODE=0
```

Result: **ACCEPTED**, fast-forward, exit code `0`.

Post-push agreement between the local tracking ref and the actual remote ref
(`git ls-remote origin refs/heads/feat/wi-acq-auth-001`):

```
3a2bb5894179e30cbeab6ec37223486c46734293	refs/heads/feat/wi-acq-auth-001
```

The earlier rejection was a credential-scope block only. It was resolved by the
owner granting the permission; nothing about the commit or the ref changed.

## Final state

- Remote `feat/wi-acq-auth-001`: `3a2bb58` (`5bbd4eb` + `3a2bb58` published).
- Local branch: `3a2bb58` plus the follow-up record commit.
- No PR, merge, tag, deploy or release was created. Nothing was force-pushed.
